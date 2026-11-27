from __future__ import annotations

from dataclasses import dataclass
import datetime as dt
import hashlib
import hmac
import json
import pathlib
from typing import Any
import urllib.error
import urllib.parse
import urllib.request

from app.core.config import Settings


@dataclass(frozen=True)
class StoredObjectInfo:
    backend: str  # "supabase" | "s3" | "local"
    object_key: str
    signed_url: str
    local_path: str = ""
    expires_in_seconds: int = 900


def _hmac_sha256(key: bytes, msg: str) -> bytes:
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()


def _build_s3_presigned_url(
    *,
    endpoint: str,
    bucket: str,
    access_key: str,
    secret_key: str,
    region: str,
    object_key: str,
    method: str = "GET",
    expires_in_seconds: int = 900,
) -> str:
    """Construit une URL pré-signée AWS Signature V4 (compatible S3 / Cloudflare R2 / MinIO)."""
    parsed = urllib.parse.urlparse(endpoint.rstrip("/"))
    host = parsed.netloc or parsed.path
    scheme = parsed.scheme or "https"
    now = dt.datetime.now(dt.timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    scope = f"{date_stamp}/{region}/s3/aws4_request"
    clean_key = object_key.lstrip("/")
    canonical_uri = f"/{bucket}/{urllib.parse.quote(clean_key, safe='/~_.')}"
    query_params = {
        "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
        "X-Amz-Credential": f"{access_key}/{scope}",
        "X-Amz-Date": amz_date,
        "X-Amz-Expires": str(expires_in_seconds),
        "X-Amz-SignedHeaders": "host",
    }
    canonical_query = "&".join(
        f"{urllib.parse.quote(k, safe='~')}={urllib.parse.quote(v, safe='~')}"
        for k, v in sorted(query_params.items())
    )
    canonical_headers = f"host:{host}\n"
    canonical_request = f"{method}\n{canonical_uri}\n{canonical_query}\n{canonical_headers}\nhost\nUNSIGNED-PAYLOAD"
    string_to_sign = (
        f"AWS4-HMAC-SHA256\n{amz_date}\n{scope}\n"
        + hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()
    )
    k_date = _hmac_sha256(f"AWS4{secret_key}".encode("utf-8"), date_stamp)
    k_region = _hmac_sha256(k_date, region)
    k_service = _hmac_sha256(k_region, "s3")
    k_signing = _hmac_sha256(k_service, "aws4_request")
    signature = hmac.new(k_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
    return f"{scheme}://{host}{canonical_uri}?{canonical_query}&X-Amz-Signature={signature}"


class ObjectStorageAdapter:
    """
    Adaptateur de stockage objet sécurisé pour documents acquéreurs/vendeurs et APK :
    1. Supabase Storage REST (`SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY`)
    2. S3 / Cloudflare R2 / MinIO SigV4 (`S3_ENDPOINT` + `S3_BUCKET` + clés)
    3. Fallback disque local (`monty-saas/backend/storage/uploads`) hors cloud objet.
    """

    def __init__(self, settings: Settings, local_root: pathlib.Path | None = None) -> None:
        self._settings = settings
        self._local_root = local_root or (
            pathlib.Path(__file__).resolve().parents[2] / "storage" / "uploads"
        )
        self._local_root.mkdir(parents=True, exist_ok=True)

    @property
    def active_backend(self) -> str:
        if self._settings.supabase_url and self._settings.supabase_service_role_key:
            return "supabase"
        if (
            self._settings.s3_endpoint
            and self._settings.s3_bucket
            and self._settings.s3_access_key
            and self._settings.s3_secret_key
        ):
            return "s3"
        return "local"

    def issue_signed_url(
        self,
        *,
        object_key: str,
        method: str = "GET",
        expires_in_seconds: int = 900,
    ) -> StoredObjectInfo:
        clean_key = object_key.lstrip("/")
        backend = self.active_backend
        if backend == "supabase":
            bucket = self._settings.supabase_storage_bucket
            endpoint = f"{self._settings.supabase_url}/storage/v1/object/sign/{bucket}/{clean_key}"
            try:
                req = urllib.request.Request(
                    endpoint,
                    data=json.dumps({"expiresIn": expires_in_seconds}).encode("utf-8"),
                    headers={
                        "Authorization": f"Bearer {self._settings.supabase_service_role_key}",
                        "apikey": self._settings.supabase_service_role_key,
                        "Content-Type": "application/json",
                    },
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=8.0) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                signed_rel = str(payload.get("signedURL") or payload.get("signedUrl") or "")
                if signed_rel:
                    full_url = (
                        signed_rel
                        if signed_rel.startswith("http")
                        else f"{self._settings.supabase_url}/storage/v1{signed_rel}"
                    )
                    return StoredObjectInfo(
                        backend="supabase",
                        object_key=clean_key,
                        signed_url=full_url,
                        expires_in_seconds=expires_in_seconds,
                    )
            except Exception:
                pass
            return StoredObjectInfo(
                backend="supabase",
                object_key=clean_key,
                signed_url=f"{self._settings.supabase_url}/storage/v1/object/authenticated/{bucket}/{clean_key}",
                expires_in_seconds=expires_in_seconds,
            )

        if backend == "s3":
            url = _build_s3_presigned_url(
                endpoint=self._settings.s3_endpoint,
                bucket=self._settings.s3_bucket,
                access_key=self._settings.s3_access_key,
                secret_key=self._settings.s3_secret_key,
                region=self._settings.s3_region,
                object_key=clean_key,
                method=method.upper(),
                expires_in_seconds=expires_in_seconds,
            )
            return StoredObjectInfo(
                backend="s3",
                object_key=clean_key,
                signed_url=url,
                expires_in_seconds=expires_in_seconds,
            )

        local_target = (self._local_root / clean_key).resolve()
        if not str(local_target).startswith(str(self._local_root.resolve())):
            raise ValueError("Chemin objet invalide.")
        return StoredObjectInfo(
            backend="local",
            object_key=clean_key,
            signed_url="",
            local_path=str(local_target),
            expires_in_seconds=expires_in_seconds,
        )

    def put_bytes(
        self,
        *,
        object_key: str,
        raw_bytes: bytes,
        content_type: str = "application/octet-stream",
    ) -> StoredObjectInfo:
        clean_key = object_key.lstrip("/")
        backend = self.active_backend
        if backend == "supabase":
            bucket = self._settings.supabase_storage_bucket
            url = f"{self._settings.supabase_url}/storage/v1/object/{bucket}/{clean_key}"
            try:
                req = urllib.request.Request(
                    url,
                    data=raw_bytes,
                    headers={
                        "Authorization": f"Bearer {self._settings.supabase_service_role_key}",
                        "apikey": self._settings.supabase_service_role_key,
                        "Content-Type": content_type,
                        "x-upsert": "true",
                    },
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=20.0):
                    pass
                return self.issue_signed_url(object_key=clean_key, method="GET")
            except (urllib.error.URLError, TimeoutError, OSError):
                pass
        elif backend == "s3":
            put_url = _build_s3_presigned_url(
                endpoint=self._settings.s3_endpoint,
                bucket=self._settings.s3_bucket,
                access_key=self._settings.s3_access_key,
                secret_key=self._settings.s3_secret_key,
                region=self._settings.s3_region,
                object_key=clean_key,
                method="PUT",
                expires_in_seconds=300,
            )
            try:
                req = urllib.request.Request(
                    put_url,
                    data=raw_bytes,
                    headers={"Content-Type": content_type},
                    method="PUT",
                )
                with urllib.request.urlopen(req, timeout=20.0):
                    pass
                return self.issue_signed_url(object_key=clean_key, method="GET")
            except (urllib.error.URLError, TimeoutError, OSError):
                pass

        # Fallback disque local sécurisé
        local_target = (self._local_root / clean_key).resolve()
        if not str(local_target).startswith(str(self._local_root.resolve())):
            raise ValueError("Chemin objet invalide.")
        local_target.parent.mkdir(parents=True, exist_ok=True)
        local_target.write_bytes(raw_bytes)
        return StoredObjectInfo(
            backend="local",
            object_key=clean_key,
            signed_url="",
            local_path=str(local_target),
            expires_in_seconds=900,
        )
