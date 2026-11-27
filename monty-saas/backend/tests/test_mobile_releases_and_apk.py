from __future__ import annotations

import io
import pathlib
import sys
import tempfile
import unittest
import zipfile

BACKEND_DIR = pathlib.Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

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


def _make_structurally_signed_test_apk() -> bytes:
    """Synthetic ZIP fixture: signature entries exercise structure checks only."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_STORED) as zf:
        zf.writestr("AndroidManifest.xml", b"<?xml version='1.0'?><manifest package='com.test'/>")
        zf.writestr("classes.dex", b"dex\n035\x00" + b"\x00" * 60_000)
        zf.writestr("resources.arsc", b"\x02\x00\x0c\x00" + b"A" * 55_000)
        zf.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n")
        zf.writestr("META-INF/TEST.SF", b"Signature-Version: 1.0\n")
        zf.writestr("META-INF/TEST.RSA", b"synthetic-test-signature-block")
    return buf.getvalue()


class MobileReleasesAndApkTests(unittest.TestCase):
    def test_no_production_release_is_published_without_explicit_signed_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            svc = MobileReleasesService(storage_dir=pathlib.Path(tmp))
            self.assertIsNone(svc.get_latest_published())
            self.assertEqual(svc.list_public_releases(), [])
            self.assertEqual(list(pathlib.Path(tmp).iterdir()), [])

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

    def test_admin_upload_publish_recommend_and_download_counter(self) -> None:
        real_apk_bytes = _make_structurally_signed_test_apk()

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


if __name__ == "__main__":
    unittest.main()
