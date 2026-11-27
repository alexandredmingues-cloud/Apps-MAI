"""Add Monty AI Core tables, Mobile APK releases registry, Module feature flags, and Lead keywords."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0010_monty_ai_and_mobile_releases"
down_revision = "0009_notaries_rnic_interconnections"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS lead_keyword_rules;
        DROP TABLE IF EXISTS agency_module_flags;
        DROP TABLE IF EXISTS mobile_app_releases;
        DROP TABLE IF EXISTS monty_ai_audit_events;
        DROP TABLE IF EXISTS monty_ai_pending_actions;
        DROP TABLE IF EXISTS monty_ai_conversations;
        DROP TABLE IF EXISTS monty_ai_memories;
        DROP TABLE IF EXISTS monty_ai_engine_configs;
        """
    )
