"""Alembic revision 0001: Initial multi-tenant PostgreSQL schema & RLS policies."""
from pathlib import Path
from alembic import op

revision = "0001_initial_multi_tenant_rls"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    tables = [
        "audit_logs",
        "stripe_webhook_events",
        "ai_usage_logs",
        "seller_documents",
        "seller_dossiers",
        "offers",
        "visits",
        "dvf_valuations",
        "properties",
        "buyer_dossiers",
        "follow_up_tasks",
        "contact_exchanges",
        "advisor_private_notes",
        "crm_contacts",
        "agency_invites",
        "agency_memberships",
        "users",
        "agencies",
    ]
    for table in tables:
        op.execute(f"DROP TABLE IF EXISTS {table} CASCADE;")
    op.execute("DROP FUNCTION IF EXISTS prevent_audit_log_mutation() CASCADE;")
    op.execute("DROP FUNCTION IF EXISTS monty_is_super_admin() CASCADE;")
    op.execute("DROP FUNCTION IF EXISTS monty_current_role() CASCADE;")
    op.execute("DROP FUNCTION IF EXISTS monty_current_user_id() CASCADE;")
    op.execute("DROP FUNCTION IF EXISTS monty_current_agency_id() CASCADE;")
