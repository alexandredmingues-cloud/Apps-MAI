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
    sentry_dsn: str = ""
    anthropic_api_key: str = ""
    anthropic_model_sonnet: str = "claude-sonnet-4-6"
    anthropic_model_haiku: str = "claude-haiku-4-5"
    whatsapp_app_secret: str = ""
    whatsapp_verify_token: str = ""
    ai_daily_budget_euro: float = 15.0
    anthropic_api_keys: tuple[str, ...] = ()
    anthropic_max_tokens: int = 900
    anthropic_timeout_seconds: float = 18.0
    whatsapp_phone_number_id: str = ""
    whatsapp_access_token: str = ""
    dvf_source_url: str = "https://api.cquest.org/dvf"
    rnic_resource_id: str = ""
    rnic_particulier_price_euro: int = 19
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    smtp_from_email: str = ""
    resend_api_key: str = ""
    local_llm_base_url: str = ""
    local_llm_model: str = "qwen2.5:7b-instruct"
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.5-flash"
    mistral_api_key: str = ""
    deepseek_api_key: str = ""
    monty_ai_confidential_default: bool = False
    supabase_url: str = ""
    supabase_service_role_key: str = ""
    supabase_storage_bucket: str = "monty-vault"
    s3_region: str = "eu-west-3"
    seller_pack_price_euro: int = 29
    firebase_project_id: str = ""
    platform_admin_user_ids: tuple[str, ...] = ()
    local_llm_allow_private_hosts: bool = False
    turnstile_secret_key: str = ""
    turnstile_allowed_hostnames: tuple[str, ...] = ()

    @property
    def effective_anthropic_keys(self) -> list[str]:
        keys = [k.strip() for k in self.anthropic_api_keys if k.strip()]
        if self.anthropic_api_key and self.anthropic_api_key not in keys:
            keys.insert(0, self.anthropic_api_key)
        return keys

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
    origins = [o.strip().rstrip("/") for o in raw_origins.split(",") if o.strip()]
    if env.lower() == "production":
        origins = [o for o in origins if o != "*"]
    raw_anthropic_keys = os.environ.get("ANTHROPIC_API_KEYS", "")
    anthropic_keys = tuple(k.strip() for k in raw_anthropic_keys.split(",") if k.strip())
    raw_platform_admins = os.environ.get("PLATFORM_ADMIN_USER_IDS", "")
    platform_admins = tuple(u.strip() for u in raw_platform_admins.split(",") if u.strip())
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
        sentry_dsn=os.environ.get("SENTRY_DSN", "").strip(),
        anthropic_api_key=os.environ.get("ANTHROPIC_API_KEY", "").strip(),
        anthropic_model_sonnet=os.environ.get("ANTHROPIC_MODEL_SONNET", "claude-sonnet-4-6").strip(),
        anthropic_model_haiku=os.environ.get("ANTHROPIC_MODEL_HAIKU", "claude-haiku-4-5").strip(),
        whatsapp_app_secret=os.environ.get("WHATSAPP_APP_SECRET", "").strip(),
        whatsapp_verify_token=os.environ.get("WHATSAPP_VERIFY_TOKEN", "").strip(),
        ai_daily_budget_euro=float(os.environ.get("AI_DAILY_BUDGET_EURO", "15.0")),
        anthropic_api_keys=anthropic_keys,
        anthropic_max_tokens=int(os.environ.get("ANTHROPIC_MAX_TOKENS", "900")),
        anthropic_timeout_seconds=float(os.environ.get("ANTHROPIC_TIMEOUT_SECONDS", "18.0")),
        whatsapp_phone_number_id=os.environ.get("WHATSAPP_PHONE_NUMBER_ID", "").strip(),
        whatsapp_access_token=os.environ.get("WHATSAPP_ACCESS_TOKEN", "").strip(),
        dvf_source_url=os.environ.get("DVF_SOURCE_URL", "https://api.cquest.org/dvf").strip(),
        rnic_resource_id=os.environ.get("RNIC_DATA_GOUV_RESOURCE_ID", "").strip(),
        rnic_particulier_price_euro=int(os.environ.get("RNIC_PARTICULIER_PRICE_EURO", "19")),
        smtp_host=os.environ.get("SMTP_HOST", "").strip(),
        smtp_port=int(os.environ.get("SMTP_PORT", "587")),
        smtp_user=os.environ.get("SMTP_USER", "").strip(),
        smtp_password=os.environ.get("SMTP_PASSWORD", "").strip(),
        smtp_from_email=os.environ.get("SMTP_FROM_EMAIL", "").strip(),
        resend_api_key=os.environ.get("RESEND_API_KEY", "").strip(),
        local_llm_base_url=(os.environ.get("OLLAMA_BASE_URL") or os.environ.get("LOCAL_LLM_BASE_URL") or "").strip(),
        local_llm_model=(os.environ.get("LOCAL_LLM_MODEL") or "qwen2.5:7b-instruct").strip(),
        gemini_api_key=os.environ.get("GEMINI_API_KEY", "").strip(),
        gemini_model=os.environ.get("GEMINI_MODEL", "gemini-2.5-flash").strip(),
        mistral_api_key=os.environ.get("MISTRAL_API_KEY", "").strip(),
        deepseek_api_key=os.environ.get("DEEPSEEK_API_KEY", "").strip(),
        monty_ai_confidential_default=_parse_bool(os.environ.get("MONTY_AI_CONFIDENTIAL_DEFAULT"), default=False),
        supabase_url=os.environ.get("SUPABASE_URL", "").strip().rstrip("/"),
        supabase_service_role_key=os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "").strip(),
        supabase_storage_bucket=os.environ.get("SUPABASE_STORAGE_BUCKET", "monty-vault").strip() or "monty-vault",
        s3_region=os.environ.get("S3_REGION", "eu-west-3").strip() or "eu-west-3",
        seller_pack_price_euro=int(os.environ.get("SELLER_PACK_PRICE_EURO", "29")),
        firebase_project_id=os.environ.get("FIREBASE_PROJECT_ID", "").strip(),
        platform_admin_user_ids=platform_admins,
        local_llm_allow_private_hosts=_parse_bool(os.environ.get("LOCAL_LLM_ALLOW_PRIVATE_HOSTS"), default=False),
        turnstile_secret_key=os.environ.get("TURNSTILE_SECRET_KEY", "").strip(),
        turnstile_allowed_hostnames=tuple(h.strip().lower() for h in os.environ.get("TURNSTILE_ALLOWED_HOSTNAMES", "").split(",") if h.strip()),
    )
