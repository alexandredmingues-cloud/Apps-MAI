from __future__ import annotations

import os
import secrets
from dataclasses import dataclass

# Secret éphémère stable au niveau du processus worker (évite l'invalidation des JWT entre requêtes en dev)
_PROCESS_EPHEMERAL_DEV_SECRET: str = secrets.token_urlsafe(32)


def _parse_bool(raw: str | None, default: bool = False) -> bool:
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    environment: str
    database_url: str
    redis_url: str
    jwt_secret: str
    jwt_ttl_minutes: int
    allowed_origins: list[str]
    openai_api_key: str
    openai_model_fast: str
    openai_model_complex: str
    stripe_secret_key: str
    stripe_webhook_secret: str
    s3_endpoint: str
    s3_bucket: str
    s3_access_key: str
    s3_secret_key: str
    enable_dev_auth: bool = False

    @property
    def effective_jwt_secret(self) -> str:
        if self.jwt_secret:
            if self.is_production and len(self.jwt_secret) < 32:
                raise RuntimeError("JWT_SECRET must be at least 32 characters in production.")
            return self.jwt_secret
        if self.is_production:
            raise RuntimeError("JWT_SECRET must be configured in production.")
        return _PROCESS_EPHEMERAL_DEV_SECRET

    @property
    def is_production(self) -> bool:
        return self.environment.lower() == "production"

    @property
    def can_issue_dev_session(self) -> bool:
        """Fail-closed : dev-session est désactivé sauf opt-in explicite hors production."""
        return (not self.is_production) and self.enable_dev_auth


def load_settings() -> Settings:
    env = os.environ.get("ENVIRONMENT", "development").strip() or "development"
    raw_origins = os.environ.get("ALLOWED_ORIGINS", "http://localhost:3000")
    origins = [o.strip() for o in raw_origins.split(",") if o.strip()]
    return Settings(
        environment=env,
        database_url=os.environ.get("DATABASE_URL", "").strip(),
        redis_url=os.environ.get("REDIS_URL", "").strip(),
        jwt_secret=os.environ.get("JWT_SECRET", "").strip(),
        jwt_ttl_minutes=int(os.environ.get("JWT_TTL_MINUTES", "60")),
        allowed_origins=origins,
        openai_api_key=os.environ.get("OPENAI_API_KEY", "").strip(),
        openai_model_fast=os.environ.get("OPENAI_MODEL_FAST", "gpt-4.1-mini").strip(),
        openai_model_complex=os.environ.get("OPENAI_MODEL_COMPLEX", "gpt-4.1").strip(),
        stripe_secret_key=os.environ.get("STRIPE_SECRET_KEY", "").strip(),
        stripe_webhook_secret=os.environ.get("STRIPE_WEBHOOK_SECRET", "").strip(),
        s3_endpoint=os.environ.get("S3_ENDPOINT", "").strip(),
        s3_bucket=os.environ.get("S3_BUCKET", "").strip(),
        s3_access_key=os.environ.get("S3_ACCESS_KEY", "").strip(),
        s3_secret_key=os.environ.get("S3_SECRET_KEY", "").strip(),
        enable_dev_auth=_parse_bool(os.environ.get("ENABLE_DEV_AUTH"), default=False),
    )
