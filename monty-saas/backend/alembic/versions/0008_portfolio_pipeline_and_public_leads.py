"""Add PostgreSQL persistence for Pro portfolio pipeline and RGPD public inbound leads."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0008_portfolio_pipeline_and_public_leads"
down_revision = "0007_v2_dedup_and_kill_switch"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP FUNCTION IF EXISTS monty_submit_public_lead(
          uuid, varchar, varchar, varchar, citext, varchar, varchar, bigint, integer,
          varchar, integer, varchar, varchar, text, boolean
        );
        DROP TABLE IF EXISTS public_inbound_leads;
        """
    )
