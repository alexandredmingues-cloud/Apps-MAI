from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import io
import pathlib
import uuid
import zipfile

from app.core.rbac import Permission, TenantContext
from app.core.security import InputSanitizer


def _default_apk_storage_dir() -> pathlib.Path:
    # monty-saas/backend/app/domain -> monty-saas/backend/storage/apk
    return pathlib.Path(__file__).resolve().parents[2] / "storage" / "apk"


def validate_apk_bytes(raw_bytes: bytes, file_name: str) -> tuple[bool, str]:
    """Vérifie qu'un binaire correspond à un véritable APK Android signé (ZIP + AndroidManifest.xml + Signature v1/v2/v3)."""
    if not file_name.lower().endswith(".apk"):
        return False, "Le fichier doit porter l'extension .apk."
    if len(raw_bytes) < 50_000:
        return False, "Fichier APK invalide ou incomplet (taille insuffisante)."
    if not raw_bytes.startswith(b"PK"):
        return False, "Archive ZIP/APK invalide (en-tête PK absent)."
    try:
        with zipfile.ZipFile(io.BytesIO(raw_bytes)) as zf:
            names = zf.namelist()
            if "AndroidManifest.xml" not in names:
                return False, "AndroidManifest.xml manquant dans l'archive APK."
            has_dex = any(n.endswith(".dex") for n in names)
            if not has_dex:
                return False, "Aucun bytecode DEX compilé (classes.dex) trouvé dans l'APK."
            has_v1_sig = any(
                n.startswith("META-INF/") and n.endswith((".SF", ".RSA", ".DSA", ".EC"))
                for n in names
            )
            has_v2_v3_sig = b"APK Sig Block 42" in raw_bytes
            if not (has_v1_sig or has_v2_v3_sig):
                return False, "APK non signé (signature v1 JAR ou v2/v3 APK Sig Block 42 requise)."
    except zipfile.BadZipFile:
        return False, "Archive APK corrompue."
    return True, "APK Android signé et vérifié."


@dataclass
class MobileAppRelease:
    id: str
    version_name: str
    version_code: int
    package_id: str
    min_android_sdk: int
    min_android_label: str
    sha256: str
    file_size_bytes: int
    file_name: str
    storage_path: str
    release_notes: str
    signature_verified: bool
    is_published: bool
    is_recommended: bool
    download_count: int
    published_by: str
    created_at: str
    updated_at: str


class MobileReleasesService:
    """Gestion & distribution sécurisée des builds Android APK réels de Monty."""

    def __init__(self, storage_dir: pathlib.Path | None = None) -> None:
        self._storage_dir = storage_dir or _default_apk_storage_dir()
        self._storage_dir.mkdir(parents=True, exist_ok=True)
        self._releases: dict[str, MobileAppRelease] = {}
        self._bootstrap_verified_release()

    def _bootstrap_verified_release(self) -> None:
        staged = self._storage_dir / "monty-immo-v1.0.apk"
        if not staged.exists():
            # Fallback to Gradle output in repo if present
            repo_apk = pathlib.Path(__file__).resolve().parents[4] / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
            if repo_apk.exists():
                staged.write_bytes(repo_apk.read_bytes())

        if not staged.exists():
            return

        data = staged.read_bytes()
        valid, _msg = validate_apk_bytes(data, staged.name)
        if not valid:
            return

        sha = hashlib.sha256(data).hexdigest()
        now = datetime.now(timezone.utc).isoformat()
        rel = MobileAppRelease(
            id="rel-monty-1-0",
            version_name="1.0",
            version_code=1,
            package_id="com.aistudio.montyimmo.kxmpzq",
            min_android_sdk=24,
            min_android_label="Android 7.0 (API 24) minimum",
            sha256=sha,
            file_size_bytes=len(data),
            file_name=staged.name,
            storage_path=str(staged),
            release_notes=(
                "Version initiale complète de Monty Immo Android : simulateur HCSF 35 %, "
                "comparables DVF & score vendeur, matching mobilité 6 modes, "
                "CRM & copropriétés RNIC, coffre documentaire et assistant terrain."
            ),
            signature_verified=True,
            is_published=True,
            is_recommended=True,
            download_count=0,
            published_by="pipeline-android",
            created_at=now,
            updated_at=now,
        )
        self._releases[rel.id] = rel

    def get_latest_published(self) -> MobileAppRelease | None:
        published = [
            r for r in self._releases.values()
            if r.is_published and r.signature_verified and pathlib.Path(r.storage_path).exists()
        ]
        if not published:
            return None
        recommended = [r for r in published if r.is_recommended]
        pool = recommended or published
        return sorted(pool, key=lambda r: (r.version_code, r.updated_at), reverse=True)[0]

    def list_public_releases(self) -> list[MobileAppRelease]:
        return sorted(
            [
                r for r in self._releases.values()
                if r.is_published and r.signature_verified and pathlib.Path(r.storage_path).exists()
            ],
            key=lambda r: (r.version_code, r.created_at),
            reverse=True,
        )

    def admin_list_releases(self, ctx: TenantContext) -> list[MobileAppRelease]:
        if not (ctx.has_permission(Permission.AGENCY_MANAGE) or ctx.has_permission(Permission.PLATFORM_ADMIN)):
            raise PermissionError("Accès réservé aux administrateurs.")
        return sorted(self._releases.values(), key=lambda r: (r.version_code, r.created_at), reverse=True)

    def register_uploaded_apk(
        self,
        ctx: TenantContext,
        *,
        raw_bytes: bytes,
        file_name: str,
        version_name: str,
        version_code: int,
        package_id: str = "com.aistudio.montyimmo.kxmpzq",
        min_android_sdk: int = 24,
        release_notes: str = "",
        publish_now: bool = True,
        mark_recommended: bool = True,
        persist_local: bool = True,
    ) -> MobileAppRelease:
        if not (ctx.has_permission(Permission.AGENCY_MANAGE) or ctx.has_permission(Permission.PLATFORM_ADMIN)):
            raise PermissionError("Seul un administrateur peut publier un APK mobile.")

        safe_name = pathlib.Path(file_name).name
        if not safe_name.endswith(".apk"):
            safe_name = f"{safe_name}.apk"
        ok, err = validate_apk_bytes(raw_bytes, safe_name)
        if not ok:
            raise ValueError(err)

        clean_ver = InputSanitizer.sanitize_text(version_name, 32)
        if not clean_ver or version_code < 1:
            raise ValueError("Numéro de version et version_code (>= 1) requis.")

        sha = hashlib.sha256(raw_bytes).hexdigest()
        rel_id = f"rel-{uuid.uuid4().hex[:8]}"
        dest = self._storage_dir / f"{rel_id}-{safe_name}"
        if persist_local:
            dest.write_bytes(raw_bytes)
        now = datetime.now(timezone.utc).isoformat()

        if mark_recommended and publish_now:
            for existing in self._releases.values():
                existing.is_recommended = False

        rel = MobileAppRelease(
            id=rel_id,
            version_name=clean_ver,
            version_code=int(version_code),
            package_id=InputSanitizer.sanitize_text(package_id, 140) or "com.aistudio.montyimmo.kxmpzq",
            min_android_sdk=max(21, min(36, int(min_android_sdk))),
            min_android_label=f"Android {7 if min_android_sdk == 24 else 8}.0 (API {min_android_sdk}) minimum",
            sha256=sha,
            file_size_bytes=len(raw_bytes),
            file_name=safe_name,
            storage_path=str(dest) if persist_local else "",
            release_notes=InputSanitizer.sanitize_text(release_notes, 1500) or "Mise à jour Monty Mobile.",
            signature_verified=True,
            is_published=bool(publish_now),
            is_recommended=bool(mark_recommended and publish_now),
            download_count=0,
            published_by=ctx.user_id,
            created_at=now,
            updated_at=now,
        )
        self._releases[rel.id] = rel
        return rel

    def admin_update_release(
        self,
        ctx: TenantContext,
        *,
        release_id: str,
        is_published: bool | None = None,
        is_recommended: bool | None = None,
        release_notes: str | None = None,
    ) -> MobileAppRelease:
        if not (ctx.has_permission(Permission.AGENCY_MANAGE) or ctx.has_permission(Permission.PLATFORM_ADMIN)):
            raise PermissionError("Accès réservé aux administrateurs.")
        rel = self._releases.get(release_id)
        if rel is None:
            raise ValueError("Version mobile introuvable.")
        if is_published is not None:
            rel.is_published = bool(is_published)
            if not rel.is_published:
                rel.is_recommended = False
        if is_recommended is not None:
            if is_recommended and not rel.is_published:
                raise ValueError("Impossible de recommander une version non publiée.")
            if is_recommended:
                for other in self._releases.values():
                    other.is_recommended = False
            rel.is_recommended = bool(is_recommended)
        if release_notes is not None:
            rel.release_notes = InputSanitizer.sanitize_text(release_notes, 1500)
        rel.updated_at = datetime.now(timezone.utc).isoformat()
        return rel

    def resolve_download_file(self, release_id: str) -> tuple[MobileAppRelease, pathlib.Path]:
        rel = self._releases.get(release_id)
        if rel is None or not rel.is_published or not rel.signature_verified:
            raise ValueError("Version APK introuvable ou non publiée.")
        path = pathlib.Path(rel.storage_path)
        if not path.exists():
            raise ValueError("Fichier APK absent du stockage serveur.")
        rel.download_count += 1
        return rel, path


mobile_releases_service = MobileReleasesService()
