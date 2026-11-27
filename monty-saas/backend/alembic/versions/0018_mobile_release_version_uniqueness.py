"""Prevent duplicate Android build numbers per package without deleting history."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0018_mobile_release_version_uniqueness"
down_revision = "0017_payment_entitlement_rls"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS uq_mobile_app_releases_package_version;")
