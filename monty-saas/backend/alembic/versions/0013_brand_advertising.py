"""Create moderated brand advertising campaigns."""
from __future__ import annotations

import pathlib
from alembic import op

revision = "0013_brand_advertising"
down_revision = "0012_v3_orchestrator_and_seller_orders"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS brand_ad_campaigns;")
