from __future__ import annotations

from fastapi import Depends, Header, HTTPException, status

from app.core.config import Settings, load_settings
from app.core.rbac import MontyRole, Permission, TenantContext
from app.core.security import SlidingWindowRateLimiter, decode_access_token
from app.db.session import get_session_factory

_rate_limiter = SlidingWindowRateLimiter(redis_url=load_settings().redis_url)


def get_settings() -> Settings:
    return load_settings()


def get_db_session_factory(settings: Settings = Depends(get_settings)):
    return get_session_factory(settings.database_url)


def get_current_tenant(
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> TenantContext:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentification Bearer requise.",
        )
    token = authorization.removeprefix("Bearer ").strip()
    try:
        payload = decode_access_token(token, settings.effective_jwt_secret)
        role = MontyRole(payload["role"])
        ctx = TenantContext(
            user_id=str(payload["sub"]),
            agency_id=str(payload["aid"]),
            role=role,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Session invalide ou expirée : {exc}",
        ) from exc

    rl = _rate_limiter.check_and_consume(f"api:{ctx.agency_id}:{ctx.user_id}", max_requests=120, window_seconds=60)
    if not rl.allowed:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Trop de requêtes. Réessayez dans {rl.retry_after_seconds}s.",
        )
    return ctx


def check_ai_rate_limit(ctx: TenantContext) -> None:
    rl = _rate_limiter.check_and_consume(f"ai:{ctx.agency_id}:{ctx.user_id}", max_requests=20, window_seconds=60)
    if not rl.allowed:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Quota horaire assistant IA dépassé. Réessayez dans {rl.retry_after_seconds}s.",
        )


def require_permission(permission: Permission):
    def _dep(ctx: TenantContext = Depends(get_current_tenant)) -> TenantContext:
        try:
            ctx.require_permission(permission)
        except PermissionError as exc:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
        return ctx

    return _dep
