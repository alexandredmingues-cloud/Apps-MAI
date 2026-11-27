from __future__ import annotations

import base64
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import FileResponse, RedirectResponse
from pydantic import BaseModel, Field

from app.api.v1.deps import ensure_production_persistence, get_db_session_factory, get_settings, require_permission
from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.core.storage import ObjectStorageAdapter
from app.db.postgres_v3_orchestrator_repository import PostgresV3OrchestratorRepository
from app.domain.mobile_releases_service import MobileAppRelease, mobile_releases_service

mobile_releases_router = APIRouter(tags=["Phase 28 — Distribution APK Mobile"])


def _storage_for_environment(settings: Settings) -> ObjectStorageAdapter:
    storage = ObjectStorageAdapter(settings)
    if settings.is_production and storage.active_backend == "local":
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Stockage objet distant non configuré pour la distribution mobile.",
            headers={"Retry-After": "60"},
        )
    return storage


def _release_dto(rel: MobileAppRelease, *, storage: ObjectStorageAdapter | None = None) -> dict[str, Any]:
    dto: dict[str, Any] = {
        "id": rel.id,
        "version_name": rel.version_name,
        "version_code": rel.version_code,
        "package_id": rel.package_id,
        "min_android_sdk": rel.min_android_sdk,
        "min_android_label": rel.min_android_label,
        "sha256": rel.sha256,
        "file_size_bytes": rel.file_size_bytes,
        "file_size_mb": round(rel.file_size_bytes / (1024 * 1024), 2),
        "file_name": rel.file_name,
        "release_notes": rel.release_notes,
        "signature_verified": rel.signature_verified,
        "is_published": rel.is_published,
        "is_recommended": rel.is_recommended,
        "download_count": rel.download_count,
        "published_by": rel.published_by,
        "created_at": rel.created_at,
        "updated_at": rel.updated_at,
        "download_path": f"/api/v1/mobile/releases/{rel.id}/download",
    }
    # Signed URLs are generated only by the download endpoint, never exposed in metadata.
    dto["storage_backend"] = storage.active_backend if storage is not None else "unknown"
    return dto


@mobile_releases_router.get("/mobile/releases/latest")
async def get_latest_mobile_release(
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    ensure_production_persistence(sf, is_production=settings.is_production, service_name="distribution mobile")
    storage = _storage_for_environment(settings)
    if sf is not None:
        pg_items = await PostgresV3OrchestratorRepository(sf).list_mobile_releases(published_only=True)
        if not pg_items:
            return {"available": False, "message": "Aucune version Android signée n'est actuellement publiée.", "release": None}
        recommended = [r for r in pg_items if r.is_recommended]
        rel = (recommended or pg_items)[0]
        return {
            "available": True,
            "message": "APK Android signé et vérifié disponible au téléchargement.",
            "release": _release_dto(rel, storage=storage),
        }
    rel = mobile_releases_service.get_latest_published()
    if rel is None:
        return {
            "available": False,
            "message": "Aucune version Android signée n'est actuellement publiée.",
            "release": None,
        }
    return {
        "available": True,
        "message": "APK Android signé et vérifié disponible au téléchargement.",
        "release": _release_dto(rel, storage=storage),
    }


@mobile_releases_router.get("/mobile/releases")
async def list_public_mobile_releases(
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    ensure_production_persistence(sf, is_production=settings.is_production, service_name="distribution mobile")
    storage = _storage_for_environment(settings)
    items = (
        await PostgresV3OrchestratorRepository(sf).list_mobile_releases(published_only=True)
        if sf is not None
        else mobile_releases_service.list_public_releases()
    )
    return {"count": len(items), "releases": [_release_dto(r, storage=storage) for r in items]}


@mobile_releases_router.get("/mobile/releases/{release_id}/download")
async def download_mobile_apk(
    release_id: str,
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
):
    ensure_production_persistence(sf, is_production=settings.is_production, service_name="distribution mobile")
    storage = _storage_for_environment(settings)
    if sf is not None:
        repo = PostgresV3OrchestratorRepository(sf)
        items = await repo.list_mobile_releases(published_only=True)
        rel = next((item for item in items if item.id == release_id), None)
        if rel is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Version APK introuvable ou non publiée.")
        if storage.active_backend != "local":
            object_key = rel.storage_path or f"apk/{rel.id}-{rel.file_name}"
            try:
                info = storage.issue_signed_url(object_key=object_key, method="GET", expires_in_seconds=300)
            except (RuntimeError, ValueError) as exc:
                raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Téléchargement temporairement indisponible.") from exc
            await repo.increment_mobile_download(release_id)
            return RedirectResponse(
                url=info.signed_url,
                status_code=status.HTTP_307_TEMPORARY_REDIRECT,
                headers={"Cache-Control": "no-store", "X-Checksum-SHA256": rel.sha256},
            )
    try:
        rel, path = mobile_releases_service.resolve_download_file(release_id)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return FileResponse(
        path=str(path),
        media_type="application/vnd.android.package-archive",
        filename=rel.file_name,
        headers={
            "X-Checksum-SHA256": rel.sha256,
            "X-App-Version": rel.version_name,
            "Cache-Control": "no-store",
        },
    )


@mobile_releases_router.get("/admin/mobile/releases")
async def admin_list_mobile_releases(
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    ensure_production_persistence(sf, is_production=settings.is_production, service_name="administration des versions mobiles")
    storage = _storage_for_environment(settings)
    items = (
        await PostgresV3OrchestratorRepository(sf).list_mobile_releases(published_only=False)
        if sf is not None
        else mobile_releases_service.admin_list_releases(ctx)
    )
    return {
        "count": len(items),
        "total_downloads": sum(r.download_count for r in items),
        "releases": [_release_dto(r, storage=storage) for r in items],
    }


class UploadApkBase64Request(BaseModel):
    file_name: str = Field(min_length=5, max_length=180)
    content_base64: str = Field(min_length=64)
    version_name: str = Field(min_length=1, max_length=32)
    version_code: int = Field(ge=1, le=999999)
    package_id: str = Field(default="com.aistudio.montyimmo.kxmpzq", max_length=140)
    min_android_sdk: int = Field(default=24, ge=21, le=36)
    release_notes: str = Field(default="", max_length=1500)
    publish_now: bool = True
    mark_recommended: bool = True


@mobile_releases_router.post("/admin/mobile/releases/upload", status_code=status.HTTP_201_CREATED)
async def admin_upload_mobile_apk(
    body: UploadApkBase64Request,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        raw_bytes = base64.b64decode(body.content_base64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Encodage base64 APK invalide.") from exc

    ensure_production_persistence(sf, is_production=settings.is_production, service_name="publication APK")
    storage = _storage_for_environment(settings)
    try:
        rel = mobile_releases_service.register_uploaded_apk(
            ctx,
            raw_bytes=raw_bytes,
            persist_local=(storage.active_backend == "local"),
            file_name=body.file_name,
            version_name=body.version_name,
            version_code=body.version_code,
            package_id=body.package_id,
            min_android_sdk=body.min_android_sdk,
            release_notes=body.release_notes,
            publish_now=body.publish_now,
            mark_recommended=body.mark_recommended,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    if storage.active_backend != "local":
        try:
            stored = storage.put_bytes(
                object_key=f"apk/{rel.id}-{rel.file_name}",
                raw_bytes=raw_bytes,
                content_type="application/vnd.android.package-archive",
            )
            rel.storage_path = stored.object_key
        except (RuntimeError, ValueError) as exc:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Stockage objet indisponible ; version non publiée.") from exc
    if sf is not None:
        rel = await PostgresV3OrchestratorRepository(sf).upsert_mobile_release(rel)
    return _release_dto(rel, storage=storage)


@mobile_releases_router.post("/admin/mobile/releases/upload-binary", status_code=status.HTTP_201_CREATED)
async def admin_upload_mobile_apk_binary(
    request: Request,
    file_name: str,
    version_name: str,
    version_code: int,
    min_android_sdk: int = 24,
    release_notes: str = "",
    publish_now: bool = True,
    mark_recommended: bool = True,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    raw_bytes = await request.body()
    ensure_production_persistence(sf, is_production=settings.is_production, service_name="publication APK")
    storage = _storage_for_environment(settings)
    try:
        rel = mobile_releases_service.register_uploaded_apk(
            ctx,
            raw_bytes=raw_bytes,
            persist_local=(storage.active_backend == "local"),
            file_name=file_name,
            version_name=version_name,
            version_code=version_code,
            min_android_sdk=min_android_sdk,
            release_notes=release_notes,
            publish_now=publish_now,
            mark_recommended=mark_recommended,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    if storage.active_backend != "local":
        try:
            stored = storage.put_bytes(
                object_key=f"apk/{rel.id}-{rel.file_name}",
                raw_bytes=raw_bytes,
                content_type="application/vnd.android.package-archive",
            )
            rel.storage_path = stored.object_key
        except (RuntimeError, ValueError) as exc:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Stockage objet indisponible ; version non publiée.") from exc
    if sf is not None:
        rel = await PostgresV3OrchestratorRepository(sf).upsert_mobile_release(rel)
    return _release_dto(rel, storage=storage)


class UpdateMobileReleaseRequest(BaseModel):
    is_published: bool | None = None
    is_recommended: bool | None = None
    release_notes: str | None = Field(default=None, max_length=1500)


@mobile_releases_router.patch("/admin/mobile/releases/{release_id}")
async def admin_update_mobile_release(
    release_id: str,
    body: UpdateMobileReleaseRequest,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    ensure_production_persistence(sf, is_production=settings.is_production, service_name="administration des versions mobiles")
    if sf is not None:
        if not (ctx.has_permission(Permission.AGENCY_MANAGE) or ctx.has_permission(Permission.PLATFORM_ADMIN)):
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Accès réservé aux administrateurs.")
        repo = PostgresV3OrchestratorRepository(sf)
        releases = await repo.list_mobile_releases(published_only=False)
        rel = next((item for item in releases if item.id == release_id), None)
        if rel is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Version mobile introuvable.")
        if body.is_published is not None:
            rel.is_published = bool(body.is_published)
            if not rel.is_published:
                rel.is_recommended = False
        if body.is_recommended is not None:
            if body.is_recommended and not rel.is_published:
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Impossible de recommander une version non publiée.")
            rel.is_recommended = bool(body.is_recommended)
        if body.release_notes is not None:
            rel.release_notes = body.release_notes[:1500]
        if rel.is_recommended:
            for other in releases:
                if other.id != rel.id:
                    other.is_recommended = False
                    await repo.upsert_mobile_release(other)
        rel = await repo.upsert_mobile_release(rel)
    else:
        try:
            rel = mobile_releases_service.admin_update_release(
                ctx,
                release_id=release_id,
                is_published=body.is_published,
                is_recommended=body.is_recommended,
                release_notes=body.release_notes,
            )
        except PermissionError as exc:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
        except ValueError as exc:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _release_dto(rel, storage=_storage_for_environment(settings))
