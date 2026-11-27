"""Revalidate JWT sessions against current user and agency membership state."""
from __future__ import annotations

import pathlib
from alembic import op

revision = "0015_auth_session_revalidation"
down_revision = "0014_rls_hardening"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute(pathlib.Path(__file__).with_suffix(".sql").read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute("DROP FUNCTION IF EXISTS monty_auth_validate_session(uuid, uuid, varchar);")
    op.execute("DROP POLICY IF EXISTS tenant_isolation_memberships ON agency_memberships;")
    op.execute("""
        CREATE POLICY tenant_isolation_memberships ON agency_memberships
          USING (monty_is_super_admin() OR agency_id = monty_current_agency_id());
    """)
