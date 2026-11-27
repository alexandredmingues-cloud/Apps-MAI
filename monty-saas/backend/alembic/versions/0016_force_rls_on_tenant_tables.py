"""Force tenant RLS policies for the remaining agency-scoped tables."""
from __future__ import annotations

import pathlib
from alembic import op

revision = "0016_force_rls_on_tenant_tables"
down_revision = "0015_auth_session_revalidation"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(pathlib.Path(__file__).with_suffix(".sql").read_text(encoding="utf-8"))


def downgrade() -> None:
    # RLS remains enabled; only the FORCE attribute is reverted.
    for table in (
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
    ):
        op.execute(f"ALTER TABLE {table} NO FORCE ROW LEVEL SECURITY")
