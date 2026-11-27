from __future__ import annotations

import uuid

from fastapi import Depends, Header, HTTPException, status
from sqlalchemy import text

from app.core.config import Settings, load_settings
from app.core.rbac import MontyRole, Permission, TenantContext
from app.core.security import SlidingWindowRateLimiter, decode_access_token
from app.db.session import get_session_factory

_rate_limiter = SlidingWindowRateLimiter(
    redis_url=load_settings().redis_url,
    require_redis=load_settings().is_production,
)


def get_settings() -> Settings:
    return load_settings()


def get_db_session_factory_unchecked(settings: Settings = Depends(get_settings)):
    """Raw factory reserved for health/diagnostic endpoints that must report unsafe DB state."""
    return get_session_factory(settings.database_url)


async def get_db_session_factory(settings: Settings = Depends(get_settings)):
    session_factory = get_session_factory(settings.database_url)
    if session_factory is None or not settings.is_production:
        return session_factory
    try:
        async with session_factory() as session:
            role_state = (
                await session.execute(
                    text("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user")
                )
            ).mappings().first()
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Impossible de vérifier les privilèges du rôle PostgreSQL.",
        ) from exc
    if not role_state or bool(role_state["rolsuper"]) or bool(role_state["rolbypassrls"]):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Rôle PostgreSQL refusé : utiliser un rôle applicatif non-superutilisateur sans BYPASSRLS.",
        )
    return session_factory


async def get_current_tenant(
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
    session_factory=Depends(get_db_session_factory),
) -> TenantContext:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentification Bearer requise.")

    token = authorization.removeprefix("Bearer ").strip()
    try:
        payload = decode_access_token(token, settings.effective_jwt_secret)
        user_id = str(payload["sub"])
        agency_id = str(payload["aid"])
        role = MontyRole(payload["role"])
        uuid.UUID(user_id)
        uuid.UUID(agency_id)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session invalide ou expirée.") from exc

    if session_factory is None:
        if settings.is_production:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Le service d'authentification persistant est indisponible.",
            )
        # Compatibility limitée au développement local pendant la migration Firebase.
    else:
        try:
            async with session_factory() as session:
                async with session.begin():
                    role_state = (
                        await session.execute(
                            text("""
                                SELECT rolsuper, rolbypassrls
                                FROM pg_roles
                                WHERE rolname = current_user
                            """)
                        )
                    ).mappings().first()
                    if settings.is_production and (
                        not role_state or bool(role_state["rolsuper"]) or bool(role_state["rolbypassrls"])
                    ):
                        raise HTTPException(
                            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            detail="Le rôle PostgreSQL de production doit être non-superutilisateur et sans BYPASSRLS.",
                        )
                    row = (
                        await session.execute(
                            text("""
                                SELECT is_active, is_super_admin, membership_role, membership_status
                                FROM monty_auth_validate_session(
                                    CAST(:uid AS uuid), CAST(:aid AS uuid), :role
                                )
                            """),
                            {"uid": user_id, "aid": agency_id, "role": role.value},
                        )
                    ).mappings().first()
        except Exception as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Impossible de vérifier les droits de la session.",
            ) from exc

        valid = bool(row and row["is_active"])
        if role == MontyRole.SUPER_ADMIN:
            valid = valid and bool(row["is_super_admin"])
        else:
            valid = valid and row["membership_status"] == "ACTIVE" and row["membership_role"] == role.value
        if not valid:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Session révoquée ou appartenance à l'agence inactive.",
            )

    ctx = TenantContext(user_id=user_id, agency_id=agency_id, role=role)
    rl = _rate_limiter.check_and_consume(
        f"api:{ctx.agency_id}:{ctx.user_id}", max_requests=120, window_seconds=60
    )
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
