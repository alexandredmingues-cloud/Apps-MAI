"""Add authorized partner listing feeds table with PostgreSQL RLS."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0006_authorized_listing_feeds"
down_revision = "0005_dvf_cache_and_v2_ops"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS authorized_listing_feeds;
        """
    )
