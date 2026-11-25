"""Add persistent feed deduplication and global agent kill-switch governance state."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0007_v2_dedup_and_kill_switch"
down_revision = "0006_authorized_listing_feeds"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS authorized_feed_items;
        DROP TABLE IF EXISTS agent_governance_state;
        """
    )
