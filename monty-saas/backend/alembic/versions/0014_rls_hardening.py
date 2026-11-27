"""Harden tenant row-level security and isolate advertising campaigns."""
# Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code.
from __future__ import annotations

import pathlib
from alembic import op

revision = "0014_rls_hardening"
down_revision = "0013_brand_advertising"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(pathlib.Path(__file__).with_suffix(".sql").read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute("DROP POLICY IF EXISTS brand_ads_super_admin_all ON brand_ad_campaigns;")
    op.execute("DROP POLICY IF EXISTS brand_ads_public_update_metrics ON brand_ad_campaigns;")
    op.execute("DROP POLICY IF EXISTS brand_ads_public_insert_pending ON brand_ad_campaigns;")
    op.execute("DROP POLICY IF EXISTS brand_ads_public_read_approved ON brand_ad_campaigns;")
    op.execute("ALTER TABLE IF EXISTS brand_ad_campaigns NO FORCE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE IF EXISTS brand_ad_campaigns DISABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE IF EXISTS agency_invites NO FORCE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE IF EXISTS agency_memberships NO FORCE ROW LEVEL SECURITY;")
