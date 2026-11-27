from __future__ import annotations

import base64
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from app.api.v1.deps import require_permission
from app.core.rbac import Permission, TenantContext
from app.domain.mobile_releases_service import MobileAppRelease, mobile_releases_service

mobile_releases_router = APIRouter(tags=["Phase 28 — Distribution APK Mobile"])


def _release_dto(rel: MobileAppRelease) -> dict[str, Any]:
    return {
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


@mobile_releases_router.get("/mobile/releases/latest")
async def get_latest_mobile_release() -> dict[str, Any]:
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
        "release": _release_dto(rel),
    }


@mobile_releases_router.get("/mobile/releases")
async def list_public_mobile_releases() -> dict[str, Any]:
    items = mobile_releases_service.list_public_releases()
    return {"count": len(items), "releases": [_release_dto(r) for r in items]}


@mobile_releases_router.get("/mobile/releases/{release_id}/download")
async def download_mobile_apk(release_id: str) -> FileResponse:
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
) -> dict[str, Any]:
    items = mobile_releases_service.admin_list_releases(ctx)
    return {
        "count": len(items),
        "total_downloads": sum(r.download_count for r in items),
        "releases": [_release_dto(r) for r in items],
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
) -> dict[str, Any]:
    try:
        raw_bytes = base64.b64decode(body.content_base64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Encodage base64 APK invalide.") from exc

    try:
        rel = mobile_releases_service.register_uploaded_apk(
            ctx,
            raw_bytes=raw_bytes,
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
    return _release_dto(rel)


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
) -> dict[str, Any]:
    raw_bytes = await request.body()
    try:
        rel = mobile_releases_service.register_uploaded_apk(
            ctx,
            raw_bytes=raw_bytes,
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
    return _release_dto(rel)


class UpdateMobileReleaseRequest(BaseModel):
    is_published: bool | None = None
    is_recommended: bool | None = None
    release_notes: str | None = Field(default=None, max_length=1500)


@mobile_releases_router.patch("/admin/mobile/releases/{release_id}")
async def admin_update_mobile_release(
    release_id: str,
    body: UpdateMobileReleaseRequest,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
) -> dict[str, Any]:
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
    return _release_dto(rel)
