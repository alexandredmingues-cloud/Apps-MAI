from __future__ import annotations

from contextlib import asynccontextmanager
from functools import lru_cache
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


def build_engine(database_url: str | None = None) -> AsyncEngine | None:
    url = normalize_async_pg_url(database_url or load_settings().database_url)
    if not url:
        return None
    return create_async_engine(url, pool_pre_ping=True, pool_size=10, max_overflow=20)


@lru_cache(maxsize=4)
def _cached_session_factory(normalized_url: str) -> async_sessionmaker[AsyncSession]:
    engine = create_async_engine(normalized_url, pool_pre_ping=True, pool_size=10, max_overflow=20)
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
