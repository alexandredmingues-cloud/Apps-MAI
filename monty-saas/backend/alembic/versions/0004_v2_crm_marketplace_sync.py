"""V2 synchronization: CRM/Marketplace columns, moderation policy, and auth routines."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0004_v2_crm_marketplace_sync"
down_revision = "0003_multi_country_jurisdiction"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP FUNCTION IF EXISTS monty_auth_claim_invite(varchar, text, varchar);
        DROP FUNCTION IF EXISTS monty_auth_lookup_login(citext);
        DROP FUNCTION IF EXISTS monty_auth_register_agency(varchar, varchar, varchar, varchar, citext, text, varchar);
        DROP POLICY IF EXISTS public_marketplace_read_published ON properties;
        DROP INDEX IF EXISTS idx_properties_moderation_queue;
        DROP INDEX IF EXISTS idx_crm_contacts_rgpd_purge;
        ALTER TABLE properties
          DROP COLUMN IF EXISTS moderated_at,
          DROP COLUMN IF EXISTS moderated_by_user_id,
          DROP COLUMN IF EXISTS moderation_note,
          DROP COLUMN IF EXISTS highlights,
          DROP COLUMN IF EXISTS photo_count;
        ALTER TABLE follow_up_tasks
          DROP COLUMN IF EXISTS reason,
          DROP COLUMN IF EXISTS contact_name;
        ALTER TABLE crm_contacts
          DROP COLUMN IF EXISTS is_anonymized,
          DROP COLUMN IF EXISTS last_visit_days_ago,
          DROP COLUMN IF EXISTS pending_offer_days,
          DROP COLUMN IF EXISTS has_missing_docs,
          DROP COLUMN IF EXISTS country_code;
        """
    )
