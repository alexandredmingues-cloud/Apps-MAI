"""Alembic revision 0002: Dual-audience news hub, verifiable sources, geo preferences & bookmarks."""
from pathlib import Path
from alembic import op

revision = "0002_dual_audience_news_hub"
down_revision = "0001_initial_multi_tenant_rls"
branch_labels = None
depends_on = None


def upgrade() -> None:
    sql_path = Path(__file__).with_suffix(".sql")
    op.execute(sql_path.read_text(encoding="utf-8"))


def downgrade() -> None:
    for table in ("user_news_bookmarks", "user_news_preferences", "news_articles", "news_sources"):
        op.execute(f"DROP TABLE IF EXISTS {table} CASCADE;")
