from __future__ import annotations

from contextlib import asynccontextmanager
from functools import lru_cache
import os
from typing import AsyncIterator

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import load_settings
from app.core.rbac import TenantContext


def normalize_async_pg_url(raw_url: str) -> str:
    if raw_url.startswith("postgres://"):
        return raw_url.replace("postgres://", "postgresql+asyncpg://", 1)
    if raw_url.startswith("postgresql://"):
        return raw_url.replace("postgresql://", "postgresql+asyncpg://", 1)
    return raw_url


def get_engine_pool_options() -> dict[str, int | float | bool]:
    """Pool PostgreSQL configurable par instance, avec bornes pour éviter l'épuisement des connexions."""
    def bounded_int(name: str, default: int, minimum: int, maximum: int) -> int:
        raw = os.environ.get(name, str(default)).strip()
        try:
            value = int(raw)
        except ValueError as exc:
            raise RuntimeError(f"{name} doit être un entier.") from exc
        if not minimum <= value <= maximum:
            raise RuntimeError(f"{name} doit être compris entre {minimum} et {maximum}.")
        return value

    def bounded_float(name: str, default: float, minimum: float, maximum: float) -> float:
        raw = os.environ.get(name, str(default)).strip()
        try:
            value = float(raw)
        except ValueError as exc:
            raise RuntimeError(f"{name} doit être un nombre.") from exc
        if not minimum <= value <= maximum:
            raise RuntimeError(f"{name} doit être compris entre {minimum} et {maximum}.")
        return value

    return {
        "pool_pre_ping": True,
        "pool_size": bounded_int("DB_POOL_SIZE", 10, 1, 50),
        "max_overflow": bounded_int("DB_MAX_OVERFLOW", 20, 0, 100),
        "pool_timeout": bounded_float("DB_POOL_TIMEOUT_SECONDS", 30.0, 1.0, 120.0),
        "pool_recycle": bounded_int("DB_POOL_RECYCLE_SECONDS", 1800, 60, 86400),
    }

def build_engine(database_url: str | None = None) -> AsyncEngine | None:
    url = normalize_async_pg_url(database_url or load_settings().database_url)
    if not url:
        return None
    return create_async_engine(url, **get_engine_pool_options())


@lru_cache(maxsize=4)
def _cached_session_factory(normalized_url: str) -> async_sessionmaker[AsyncSession]:
    engine = create_async_engine(normalized_url, **get_engine_pool_options())
    return async_sessionmaker(engine, expire_on_commit=False)


def get_session_factory(database_url: str | None = None) -> async_sessionmaker[AsyncSession] | None:
    raw = database_url if database_url is not None else load_settings().database_url
    url = normalize_async_pg_url(raw)
    if not url:
        return None
    return _cached_session_factory(url)


@asynccontextmanager
async def tenant_db_session(
    session_factory: async_sessionmaker[AsyncSession],
    ctx: TenantContext,
) -> AsyncIterator[AsyncSession]:
    """
    Ouvre une session PostgreSQL et injecte le contexte multi-tenant dans la transaction
    afin que les politiques Row Level Security (RLS) isolent strictement l'agence.
    """
    async with session_factory() as session:
        async with session.begin():
            await session.execute(
                text("SELECT set_config('app.current_agency_id', :aid, true)"),
                {"aid": ctx.agency_id},
            )
            await session.execute(
                text("SELECT set_config('app.current_user_id', :uid, true)"),
                {"uid": ctx.user_id},
            )
            await session.execute(
                text("SELECT set_config('app.current_role', :role, true)"),
                {"role": ctx.role.value},
            )
            yield session
