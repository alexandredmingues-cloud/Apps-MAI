"""V2 Ops: DVF cache, AI agent audit logs, Monty Scout proposals, WhatsApp consent & Pre-Etat Date."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0005_dvf_cache_and_v2_ops"
down_revision = "0004_v2_crm_marketplace_sync"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS pre_etat_date_dossiers;
        DROP TABLE IF EXISTS whatsapp_consent_events;
        DROP TABLE IF EXISTS scout_proposals;
        DROP TABLE IF EXISTS agent_execution_logs;
        DROP TABLE IF EXISTS dvf_mutations_cache;
        """
    )
