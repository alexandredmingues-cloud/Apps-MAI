from __future__ import annotations

import uuid

import pytest
from sqlalchemy import text
from sqlalchemy.exc import DBAPIError
from sqlalchemy.ext.asyncio import create_async_engine

from app.db.session import normalize_async_pg_url


@pytest.mark.asyncio
async def test_rls_blocks_cross_agency_reads_and_writes_as_non_superuser(monkeypatch) -> None:
    """Exercise PostgreSQL RLS as a real role without SUPERUSER/BYPASSRLS."""
    import os

    database_url = os.environ.get("DATABASE_URL", "").strip()
    if not database_url:
        pytest.skip("DATABASE_URL absent: PostgreSQL RLS integration test only")

    engine = create_async_engine(normalize_async_pg_url(database_url))
    suffix = uuid.uuid4().hex
    role_name = f"monagent_rls_{suffix}"
    agency_a, agency_b = uuid.uuid4(), uuid.uuid4()
    user_a, user_b = uuid.uuid4(), uuid.uuid4()
    contact_a, contact_b = uuid.uuid4(), uuid.uuid4()

    try:
        # Provision fixtures and a dedicated role. CI runs this test against its
        # disposable PostgreSQL service using the postgres owner account.
        async with engine.begin() as conn:
            await conn.execute(text(f'CREATE ROLE "{role_name}" NOLOGIN NOSUPERUSER NOBYPASSRLS'))
            await conn.execute(text(f'GRANT SELECT, INSERT, UPDATE, DELETE ON crm_contacts TO "{role_name}"'))
            await conn.execute(
                text("INSERT INTO agencies (id, name, slug) VALUES (:a, 'RLS A', :sa), (:b, 'RLS B', :sb)"),
                {"a": agency_a, "b": agency_b, "sa": f"rls-a-{suffix}", "sb": f"rls-b-{suffix}"},
            )
            await conn.execute(
                text("""
                    INSERT INTO users (id, email, password_hash, display_name)
                    VALUES (:ua, :ea, 'test', 'RLS A'), (:ub, :eb, 'test', 'RLS B')
                """),
                {"ua": user_a, "ub": user_b, "ea": f"rls-a-{suffix}@example.test", "eb": f"rls-b-{suffix}@example.test"},
            )
            await conn.execute(
                text("""
                    INSERT INTO crm_contacts
                      (id, agency_id, created_by_user_id, assigned_to_user_id, full_name, contact_type)
                    VALUES
                      (:ca, :a, :ua, :ua, 'Contact agence A', 'Autre'),
                      (:cb, :b, :ub, :ub, 'Contact agence B', 'Autre')
                """),
                {"ca": contact_a, "cb": contact_b, "a": agency_a, "b": agency_b, "ua": user_a, "ub": user_b},
            )

        async with engine.begin() as conn:
            await conn.execute(text(f'SET LOCAL ROLE "{role_name}"'))
            role_state = (
                await conn.execute(
                    text("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user")
                )
            ).one()
            assert role_state == (False, False), "The RLS test must not run with a privileged role"

            await conn.execute(
                text("SELECT set_config('app.current_agency_id', :agency, true)"),
                {"agency": str(agency_a)},
            )
            await conn.execute(text("SELECT set_config('app.current_user_id', :user, true)"), {"user": str(user_a)})
            await conn.execute(text("SELECT set_config('app.current_role', 'AGENT', true)"))

            visible = (
                await conn.execute(text("SELECT id FROM crm_contacts ORDER BY id"))
            ).scalars().all()
            assert visible == [contact_a], "Agency A must not read agency B CRM rows"

            hidden_update = await conn.execute(
                text("UPDATE crm_contacts SET full_name = 'Intrusion' WHERE id = :id"),
                {"id": contact_b},
            )
            assert hidden_update.rowcount == 0, "Agency A must not update agency B CRM rows"

            hidden_delete = await conn.execute(
                text("DELETE FROM crm_contacts WHERE id = :id"),
                {"id": contact_b},
            )
            assert hidden_delete.rowcount == 0, "Agency A must not delete agency B CRM rows"

            # A row with a foreign agency_id must be rejected by WITH CHECK.
            with pytest.raises(DBAPIError):
                async with conn.begin_nested():
                    await conn.execute(
                        text("""
                            INSERT INTO crm_contacts
                              (agency_id, created_by_user_id, assigned_to_user_id, full_name, contact_type)
                            VALUES (:agency, :user, :user, 'Forbidden cross-tenant insert', 'Autre')
                        """),
                        {"agency": str(agency_b), "user": str(user_a)},
                    )

            own_update = await conn.execute(
                text("UPDATE crm_contacts SET full_name = 'A modifié' WHERE id = :id"),
                {"id": contact_a},
            )
            assert own_update.rowcount == 1, "Agency A should retain access to its own CRM row"
    finally:
        # SET LOCAL ROLE is transaction-scoped; cleanup runs as the owner.
        try:
            async with engine.begin() as conn:
                await conn.execute(text(f"""
                    DO $cleanup$
                    BEGIN
                      IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '{role_name}') THEN
                        EXECUTE 'DROP OWNED BY "{role_name}"';
                        EXECUTE 'DROP ROLE "{role_name}"';
                      END IF;
                    END
                    $cleanup$;
                """))
        finally:
            await engine.dispose()

@pytest.mark.asyncio
async def test_agency_scoped_tables_force_rls_for_table_owners() -> None:
    import os

    database_url = os.environ.get("DATABASE_URL", "").strip()
    if not database_url:
        pytest.skip("DATABASE_URL absent: PostgreSQL RLS integration test only")

    expected = [
        "news_sources",
        "news_articles",
        "monty_ai_memories",
        "monty_ai_conversations",
        "monty_ai_pending_actions",
        "monty_ai_audit_events",
        "agency_module_flags",
        "lead_keyword_rules",
        "monty_price_index_configs",
        "monty_price_index_history",
    ]
    engine = create_async_engine(normalize_async_pg_url(database_url))
    try:
        async with engine.connect() as conn:
            rows = (
                await conn.execute(
                    text("""
                        SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'public' AND c.relname = ANY(:tables)
                    """),
                    {"tables": expected},
                )
            ).all()
        state = {row.relname: (row.relrowsecurity, row.relforcerowsecurity) for row in rows}
        assert set(state) == set(expected), "Every expected agency-scoped table must exist"
        assert all(enabled and forced for enabled, forced in state.values()), (
            "Agency-scoped tables must have RLS enabled and FORCE RLS set"
        )
    finally:
        await engine.dispose()
