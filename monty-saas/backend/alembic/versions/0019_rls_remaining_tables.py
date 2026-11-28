"""Enable FORCE RLS and policies on tables previously left unprotected."""
from __future__ import annotations

import pathlib

from alembic import op

revision = "0019_rls_remaining_tables"
down_revision = "0018_mobile_release_version_uniqueness"
branch_labels = None
depends_on = None

_TABLES = (
    "users",
    "country_jurisdiction_packs",
    "dvf_mutations_cache",
    "mobile_app_releases",
    "monty_ai_engine_configs",
    "agent_governance_state",
    "stripe_webhook_events",
    "whatsapp_consent_events",
)


def upgrade() -> None:
    op.execute(pathlib.Path(__file__).with_suffix(".sql").read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP POLICY IF EXISTS users_select_self_or_super ON users;
        DROP POLICY IF EXISTS users_update_self_or_super ON users;
        DROP POLICY IF EXISTS users_insert_super_only ON users;
        DROP POLICY IF EXISTS users_delete_super_only ON users;

        DROP POLICY IF EXISTS country_packs_public_read ON country_jurisdiction_packs;
        DROP POLICY IF EXISTS country_packs_super_write ON country_jurisdiction_packs;

        DROP POLICY IF EXISTS dvf_cache_pro_read ON dvf_mutations_cache;
        DROP POLICY IF EXISTS dvf_cache_super_write ON dvf_mutations_cache;

        DROP POLICY IF EXISTS mobile_releases_public_read_published ON mobile_app_releases;
        DROP POLICY IF EXISTS mobile_releases_super_write ON mobile_app_releases;

        DROP POLICY IF EXISTS monty_ai_engine_configs_pro_read ON monty_ai_engine_configs;
        DROP POLICY IF EXISTS monty_ai_engine_configs_super_write ON monty_ai_engine_configs;

        DROP POLICY IF EXISTS agent_governance_pro_read ON agent_governance_state;
        DROP POLICY IF EXISTS agent_governance_super_write ON agent_governance_state;

        DROP POLICY IF EXISTS stripe_webhook_payment_processor ON stripe_webhook_events;
        DROP POLICY IF EXISTS stripe_webhook_agency_read ON stripe_webhook_events;

        DROP POLICY IF EXISTS whatsapp_consent_processor_only ON whatsapp_consent_events;
        """
    )
    for table in _TABLES:
        op.execute(f"ALTER TABLE {table} NO FORCE ROW LEVEL SECURITY")
        op.execute(f"ALTER TABLE {table} DISABLE ROW LEVEL SECURITY")
