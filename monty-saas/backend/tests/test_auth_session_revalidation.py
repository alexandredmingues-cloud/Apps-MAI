from __future__ import annotations

import os
import uuid

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from app.db.session import normalize_async_pg_url


@pytest.mark.asyncio
async def test_auth_session_validation_reflects_live_membership_state() -> None:
    database_url = os.environ.get("DATABASE_URL", "").strip()
    if not database_url:
        pytest.skip("DATABASE_URL absent: PostgreSQL integration test only")

    engine = create_async_engine(normalize_async_pg_url(database_url))
    agency_a, agency_b, user_id = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
    try:
        async with engine.begin() as conn:
            await conn.execute(
                text("INSERT INTO agencies (id, name, slug, city) VALUES (:id, 'Auth test A', :slug, 'Test')"),
                {"id": agency_a, "slug": f"auth-test-{agency_a.hex}"},
            )
            await conn.execute(
                text("INSERT INTO agencies (id, name, slug, city) VALUES (:id, 'Auth test B', :slug, 'Test')"),
                {"id": agency_b, "slug": f"auth-test-{agency_b.hex}"},
            )
            await conn.execute(
                text("""
                    INSERT INTO users (id, email, password_hash, display_name, is_active)
                    VALUES (:id, :email, 'test-hash', 'Auth Test', true)
                """),
                {"id": user_id, "email": f"{user_id.hex}@example.test"},
            )
            await conn.execute(
                text("""
                    INSERT INTO agency_memberships (agency_id, user_id, role, status)
                    VALUES (:agency, :user, 'AGENT', 'ACTIVE')
                """),
                {"agency": agency_a, "user": user_id},
            )

            async def validate(agency):
                return (
                    await conn.execute(
                        text("""
                            SELECT is_active, is_super_admin, membership_role, membership_status
                            FROM monty_auth_validate_session(:user_id, :agency_id, 'AGENT')
                        """),
                        {"user_id": user_id, "agency_id": agency},
                    )
                ).mappings().first()

            active = await validate(agency_a)
            assert active is not None
            assert active["is_active"] is True
            assert active["membership_role"] == "AGENT"
            assert active["membership_status"] == "ACTIVE"

            # A user cannot reuse an agency-A token against agency B.
            other_agency = await validate(agency_b)
            assert other_agency is not None
            assert other_agency["membership_role"] is None
            assert other_agency["membership_status"] is None

            # Suspension is reflected immediately, without waiting for JWT expiry.
            await conn.execute(
                text("UPDATE agency_memberships SET status = 'SUSPENDED' WHERE agency_id = :agency AND user_id = :user"),
                {"agency": agency_a, "user": user_id},
            )
            suspended = await validate(agency_a)
            assert suspended["membership_status"] == "SUSPENDED"

            # Role changes are visible to the next authenticated request.
            await conn.execute(
                text("UPDATE agency_memberships SET status = 'ACTIVE', role = 'ADMIN' WHERE agency_id = :agency AND user_id = :user"),
                {"agency": agency_a, "user": user_id},
            )
            changed_role = await validate(agency_a)
            assert changed_role["membership_role"] == "ADMIN"
            assert changed_role["membership_status"] == "ACTIVE"

            await conn.execute(
                text("UPDATE users SET is_active = false WHERE id = :user"),
                {"user": user_id},
            )
            disabled_user = await validate(agency_a)
            assert disabled_user["is_active"] is False
    finally:
        await engine.dispose()
