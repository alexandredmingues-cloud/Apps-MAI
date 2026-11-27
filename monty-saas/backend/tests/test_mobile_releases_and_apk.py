from __future__ import annotations

import hashlib
import os
import io
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import AsyncMock, Mock, patch
from types import SimpleNamespace
import zipfile

BACKEND_DIR = pathlib.Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from fastapi import HTTPException  # noqa: E402
from app.core.config import load_settings  # noqa: E402
from app.api.v1.mobile_releases_router import _persist_uploaded_mobile_release, _storage_for_environment  # noqa: E402
from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.domain.mobile_releases_service import (  # noqa: E402
    MobileReleasesService,
    validate_apk_bytes,
)


def _make_unsigned_apk_zip(*, include_manifest: bool = True, include_dex: bool = True) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_STORED) as zf:
        if include_manifest:
            zf.writestr("AndroidManifest.xml", b"<?xml version='1.0'?><manifest package='com.test'/>")
        if include_dex:
            zf.writestr("classes.dex", b"dex\n035\x00" + b"\x00" * 60_000)
        zf.writestr("resources.arsc", b"\x02\x00\x0c\x00" + b"A" * 55_000)
    return buf.getvalue()


class MobileReleasesAndApkTests(unittest.TestCase):
    @unittest.skipUnless(
        (BACKEND_DIR / "storage" / "apk" / "monty-immo-v1.0.apk").is_file(),
        "APK de production signé absent du dépôt : vérification reportée au pipeline de publication mobile.",
    )
    def test_staged_production_apk_is_present_signed_and_verifiable(self) -> None:
        svc = MobileReleasesService()
        latest = svc.get_latest_published()
        self.assertIsNotNone(latest)
        assert latest is not None
        self.assertTrue(latest.signature_verified)
        self.assertTrue(latest.is_published)
        self.assertTrue(latest.is_recommended)
        self.assertEqual(len(latest.sha256), 64)
        self.assertGreater(latest.file_size_bytes, 1_000_000)
        self.assertTrue(pathlib.Path(latest.storage_path).exists())

        disk_sha = hashlib.sha256(pathlib.Path(latest.storage_path).read_bytes()).hexdigest()
        self.assertEqual(latest.sha256, disk_sha)


    def test_production_apk_storage_requires_remote_backend(self) -> None:
        clean = {
            "ENVIRONMENT": "production",
            "SUPABASE_URL": "",
            "SUPABASE_SERVICE_ROLE_KEY": "",
            "S3_ENDPOINT": "",
            "S3_BUCKET": "",
            "S3_ACCESS_KEY": "",
            "S3_SECRET_KEY": "",
        }
        with patch.dict(os.environ, clean, clear=False):
            settings = load_settings()
        with self.assertRaises(HTTPException) as raised:
            _storage_for_environment(settings)
        self.assertEqual(raised.exception.status_code, 503)

    def test_development_apk_storage_can_use_local_backend(self) -> None:
        clean = {
            "ENVIRONMENT": "development",
            "SUPABASE_URL": "",
            "SUPABASE_SERVICE_ROLE_KEY": "",
            "S3_ENDPOINT": "",
            "S3_BUCKET": "",
            "S3_ACCESS_KEY": "",
            "S3_SECRET_KEY": "",
        }
        with patch.dict(os.environ, clean, clear=False):
            settings = load_settings()
        storage = _storage_for_environment(settings)
        self.assertEqual(storage.active_backend, "local")

    def test_local_object_storage_delete_is_idempotent(self) -> None:
        clean = {
            "ENVIRONMENT": "development",
            "SUPABASE_URL": "",
            "SUPABASE_SERVICE_ROLE_KEY": "",
            "S3_ENDPOINT": "",
            "S3_BUCKET": "",
            "S3_ACCESS_KEY": "",
            "S3_SECRET_KEY": "",
        }
        with patch.dict(os.environ, clean, clear=False):
            settings = load_settings()
        with tempfile.TemporaryDirectory() as tmp:
            storage = ObjectStorageAdapter(settings, local_root=pathlib.Path(tmp) / "uploads")
            stored = storage.put_bytes(object_key="apk/test-release.apk", raw_bytes=b"test")
            target = pathlib.Path(stored.local_path)
            self.assertTrue(target.exists())
            storage.delete_object(object_key="apk/test-release.apk")
            storage.delete_object(object_key="apk/test-release.apk")
            self.assertFalse(target.exists())

    def test_validate_apk_bytes_rejects_fake_corrupt_and_unsigned_files(self) -> None:
        ok, _ = validate_apk_bytes(b"not-an-apk" * 8000, "monty.txt")
        self.assertFalse(ok)

        ok, _ = validate_apk_bytes(b"PK\x03\x04tiny", "monty.apk")
        self.assertFalse(ok)

        ok, _ = validate_apk_bytes(b"NOT_A_ZIP_" * 7000, "monty.apk")
        self.assertFalse(ok)

        no_manifest = _make_unsigned_apk_zip(include_manifest=False, include_dex=True)
        ok, err = validate_apk_bytes(no_manifest, "nomanifest.apk")
        self.assertFalse(ok)
        self.assertIn("AndroidManifest.xml", err)

        no_dex = _make_unsigned_apk_zip(include_manifest=True, include_dex=False)
        ok, err = validate_apk_bytes(no_dex, "nodex.apk")
        self.assertFalse(ok)
        self.assertIn("DEX", err)

        unsigned = _make_unsigned_apk_zip(include_manifest=True, include_dex=True)
        ok, err = validate_apk_bytes(unsigned, "unsigned.apk")
        self.assertFalse(ok)
        self.assertIn("non signé", err)

    @unittest.skipUnless(
        (BACKEND_DIR / "storage" / "apk" / "monty-immo-v1.0.apk").is_file(),
        "APK de production signé absent du dépôt : vérification reportée au pipeline de publication mobile.",
    )
    def test_admin_upload_publish_recommend_and_download_counter(self) -> None:
        staged_apk = BACKEND_DIR / "storage" / "apk" / "monty-immo-v1.0.apk"
        self.assertTrue(staged_apk.exists())
        real_apk_bytes = staged_apk.read_bytes()

        admin_ctx = TenantContext(
            user_id="usr-admin",
            agency_id="agency-france-reseau",
            role=MontyRole.DIRECTOR,
        )
        agent_ctx = TenantContext(
            user_id="usr-agent",
            agency_id="agency-france-reseau",
            role=MontyRole.AGENT,
        )

        with tempfile.TemporaryDirectory() as tmp:
            svc = MobileReleasesService(storage_dir=pathlib.Path(tmp))
            with self.assertRaises(PermissionError):
                svc.register_uploaded_apk(
                    agent_ctx,
                    raw_bytes=real_apk_bytes,
                    file_name="monty-v1.2.apk",
                    version_name="1.2.0",
                    version_code=2,
                )

            rel = svc.register_uploaded_apk(
                admin_ctx,
                raw_bytes=real_apk_bytes,
                file_name="monty-v1.2.apk",
                version_name="1.2.0",
                version_code=2,
                release_notes="Ajout Monty Price Index et synchronisation terrain.",
                publish_now=True,
                mark_recommended=True,
            )
            self.assertEqual(rel.version_name, "1.2.0")
            self.assertEqual(rel.version_code, 2)
            self.assertTrue(rel.signature_verified)
            self.assertTrue(rel.is_recommended)

            latest = svc.get_latest_published()
            self.assertIsNotNone(latest)
            assert latest is not None
            self.assertEqual(latest.id, rel.id)

            dl_rel, dl_path = svc.resolve_download_file(rel.id)
            self.assertTrue(dl_path.exists())
            self.assertEqual(dl_rel.download_count, 1)

            # Unpublish removes from public recommended and disallows recommendation
            svc.admin_update_release(admin_ctx, release_id=rel.id, is_published=False)
            with self.assertRaises(ValueError):
                svc.admin_update_release(admin_ctx, release_id=rel.id, is_recommended=True)
            with self.assertRaises(ValueError):
                svc.resolve_download_file(rel.id)

            public_list = svc.list_public_releases()
            self.assertTrue(all(r.id != rel.id for r in public_list))




class MobileReleasePublicationCleanupTests(unittest.IsolatedAsyncioTestCase):
    async def test_database_failure_triggers_remote_apk_cleanup(self) -> None:
        from unittest.mock import patch as local_patch

        fake_storage = SimpleNamespace(
            active_backend="supabase",
            put_bytes=lambda **kwargs: SimpleNamespace(object_key=kwargs["object_key"]),
            delete_object=Mock(),
        )
        fake_repo = SimpleNamespace(upsert_mobile_release=AsyncMock(side_effect=RuntimeError("database unavailable")))
        release = SimpleNamespace(id="rel-test1234", file_name="release.apk", storage_path="")
        with local_patch(
            "app.api.v1.mobile_releases_router.PostgresV3OrchestratorRepository",
            return_value=fake_repo,
        ):
            with self.assertRaises(HTTPException) as raised:
                await _persist_uploaded_mobile_release(
                    release,
                    raw_bytes=b"apk-bytes",
                    storage=fake_storage,
                    session_factory=object(),
                )
        self.assertEqual(raised.exception.status_code, 503)
        fake_storage.delete_object.assert_called_once_with(object_key="apk/rel-test1234-release.apk")


if __name__ == "__main__":
    unittest.main()
