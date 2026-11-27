from __future__ import annotations

import os
from contextlib import asynccontextmanager
from functools import lru_cache
from typing import AsyncIterator

from sqlalchemy import text
from sqlalchemy.engine import make_url
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import load_settings
from app.core.rbac import TenantContext


def normalize_async_pg_url(raw_url: str) -> str:
    """Normalize PostgreSQL URLs for SQLAlchemy's asyncpg driver.

    Supabase may provide `postgresql://` URLs with `sslmode=require`.
    asyncpg expects the equivalent query parameter to be named `ssl`.
    """
    raw_url = raw_url.strip()
    if not raw_url:
        return ""

    url = make_url(raw_url)
    if url.drivername in {"postgres", "postgresql"}:
        url = url.set(drivername="postgresql+asyncpg")

    if url.drivername == "postgresql+asyncpg":
        query = dict(url.query)
        sslmode = query.pop("sslmode", None)
        if sslmode and "ssl" not in query:
            query["ssl"] = sslmode
        url = url.set(query=query)

    if url.drivername != "postgresql+asyncpg":
        raise ValueError(
            "DATABASE_URL must use PostgreSQL (postgresql:// or postgresql+asyncpg://)."
        )

    return url.render_as_string(hide_password=False)


def _pool_options() -> dict[str, int]:
    """Keep each API worker within conservative managed-Postgres pool limits."""
    pool_size = max(1, int(os.environ.get("DB_POOL_SIZE", "5")))
    max_overflow = max(0, int(os.environ.get("DB_MAX_OVERFLOW", "5")))
    return {
        "pool_size": pool_size,
        "max_overflow": max_overflow,
        "pool_timeout": 30,
        "pool_recycle": 1800,
    }


def build_engine(database_url: str | None = None) -> AsyncEngine | None:
    raw_url = database_url if database_url is not None else load_settings().database_url
    url = normalize_async_pg_url(raw_url)
    if not url:
        return None
    return create_async_engine(url, pool_pre_ping=True, **_pool_options())


@lru_cache(maxsize=4)
def _cached_session_factory(normalized_url: str) -> async_sessionmaker[AsyncSession]:
    engine = create_async_engine(normalized_url, pool_pre_ping=True, **_pool_options())
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
