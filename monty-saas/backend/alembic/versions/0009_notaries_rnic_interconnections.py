"""Add French notary book & transmissions, RNIC copropriété passes, module notifications, feature flags, and client portal storage."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0009_notaries_rnic_interconnections"
down_revision = "0008_portfolio_pipeline_and_public_leads"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP FUNCTION IF EXISTS monty_auth_register_client(citext, text, varchar, varchar, varchar);
        DROP TABLE IF EXISTS client_documents;
        DROP TABLE IF EXISTS client_portal_favorites;
        DROP TABLE IF EXISTS agency_feature_flags;
        DROP TABLE IF EXISTS agency_notifications;
        DROP TABLE IF EXISTS rnic_access_passes;
        DROP TABLE IF EXISTS notary_transmissions;
        DROP TABLE IF EXISTS agency_notary_contacts;
        """
    )
