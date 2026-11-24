"""Multi-country & multi-locale jurisdiction separation (FR active, PT ready, Europe extensible)."""

from __future__ import annotations

import pathlib

from alembic import op

revision = "0003_multi_country_jurisdiction"
down_revision = "0002_dual_audience_news_hub"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = pathlib.Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS country_jurisdiction_packs;
        DROP INDEX IF EXISTS idx_news_articles_country_pub;
        ALTER TABLE user_news_preferences DROP COLUMN IF EXISTS locale, DROP COLUMN IF EXISTS country_code;
        ALTER TABLE news_articles DROP COLUMN IF EXISTS regulation_jurisdiction, DROP COLUMN IF EXISTS locale, DROP COLUMN IF EXISTS country_code;
        ALTER TABLE news_sources DROP COLUMN IF EXISTS locale, DROP COLUMN IF EXISTS country_code;
        ALTER TABLE properties DROP COLUMN IF EXISTS region_name, DROP COLUMN IF EXISTS city, DROP COLUMN IF EXISTS postal_code, DROP COLUMN IF EXISTS country_code;
        ALTER TABLE agencies DROP COLUMN IF EXISTS currency_code, DROP COLUMN IF EXISTS default_locale, DROP COLUMN IF EXISTS country_code;
        """
    )
