"""Restrict payment and RNIC entitlement rows with transaction-local RLS context."""
from __future__ import annotations

import pathlib
from alembic import op

revision = "0017_payment_entitlement_rls"
down_revision = "0016_force_rls_on_tenant_tables"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(pathlib.Path(__file__).with_suffix(".sql").read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP POLICY IF EXISTS payment_processor_only_seller_pack_orders ON seller_pack_orders;
        ALTER TABLE seller_pack_orders NO FORCE ROW LEVEL SECURITY;
        ALTER TABLE seller_pack_orders DISABLE ROW LEVEL SECURITY;

        DROP POLICY IF EXISTS rnic_pass_owner_or_payment_processor ON rnic_access_passes;
        ALTER TABLE rnic_access_passes NO FORCE ROW LEVEL SECURITY;
        ALTER TABLE rnic_access_passes DISABLE ROW LEVEL SECURITY;
        """
    )
