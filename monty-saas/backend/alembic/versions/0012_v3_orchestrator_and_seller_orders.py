"""Add 6-tier Price Index threshold and server-side Seller Pack orders table."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0012_v3_orchestrator_and_seller_orders"
down_revision = "0011_monty_price_index"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS seller_pack_orders;
        ALTER TABLE monty_price_index_configs DROP COLUMN IF EXISTS excellent_deal_pct;
        """
    )
