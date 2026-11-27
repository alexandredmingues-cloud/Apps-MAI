"""Add Monty Price Index configuration and evaluation history tables."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0011_monty_price_index"
down_revision = "0010_monty_ai_and_mobile_releases"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS monty_price_index_history;
        DROP TABLE IF EXISTS monty_price_index_configs;
        """
    )
