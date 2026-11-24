-- Monty SaaS — Migration 0002 : Centre d'Information Double Public (Pro & Particulier), SEO, Préférences & Favoris
-- Sécurité : ces tables ne référencent JAMAIS les clients, mandats, notes privées ou données commerciales d'agence.

CREATE TABLE news_sources (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name varchar(140) NOT NULL,
  domain varchar(140) NOT NULL,
  base_url varchar(400) NOT NULL,
  reliability_tier varchar(30) NOT NULL DEFAULT 'OFFICIELLE' CHECK (reliability_tier IN ('OFFICIELLE', 'INSTITUTIONNELLE', 'PROFESSIONNELLE')),
  is_active boolean NOT NULL DEFAULT true,
  last_verified_at timestamptz,
  created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE news_articles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug varchar(180) NOT NULL UNIQUE,
  source_id uuid NOT NULL REFERENCES news_sources(id) ON DELETE RESTRICT,
  source_url varchar(500) NOT NULL,
  category varchar(40) NOT NULL CHECK (
    category IN ('TAUX_CREDIT', 'MARCHE_LOCAL', 'LOIS_FISCALITE', 'DPE_TRAVAUX', 'LOCATION_COPRO', 'INVESTISSEMENT', 'METIER_CONSEILLER')
  ),
  audience_scope varchar(20) NOT NULL DEFAULT 'BOTH' CHECK (audience_scope IN ('BOTH', 'PRO_ONLY', 'PUBLIC_ONLY')),
  city varchar(100) NOT NULL DEFAULT '',
  department_code varchar(10) NOT NULL DEFAULT '',
  region_name varchar(100) NOT NULL DEFAULT 'National',
  title varchar(200) NOT NULL,
  seo_meta_title varchar(160) NOT NULL DEFAULT '',
  seo_meta_description varchar(300) NOT NULL DEFAULT '',
  public_summary_markdown text NOT NULL DEFAULT '',
  public_takeaways jsonb NOT NULL DEFAULT '[]'::jsonb,
  pro_analysis_markdown text NOT NULL DEFAULT '',
  pro_commercial_arguments jsonb NOT NULL DEFAULT '[]'::jsonb,
  pro_key_metrics jsonb NOT NULL DEFAULT '{}'::jsonb,
  ai_dual_generated boolean NOT NULL DEFAULT false,
  legal_disclaimer text NOT NULL DEFAULT 'Information générale et analyse indicative : ne constitue pas un conseil juridique ou fiscal officiel.',
  status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  published_at timestamptz,
  created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_news_articles_public ON news_articles (status, audience_scope, published_at DESC);
CREATE INDEX idx_news_articles_geo ON news_articles (region_name, department_code, city);

CREATE TABLE user_news_preferences (
  user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  city varchar(100) NOT NULL DEFAULT 'Montpellier',
  department_code varchar(10) NOT NULL DEFAULT '34',
  region_name varchar(100) NOT NULL DEFAULT 'Occitanie',
  interests jsonb NOT NULL DEFAULT '["TAUX_CREDIT", "MARCHE_LOCAL", "LOIS_FISCALITE", "DPE_TRAVAUX"]'::jsonb,
  notifications_enabled boolean NOT NULL DEFAULT false,
  rgpd_consent_granted boolean NOT NULL DEFAULT false,
  rgpd_consent_date date,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_news_bookmarks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  article_id uuid NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
  alert_on_update boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, article_id)
);

-- RLS sur préférences, favoris et publications
ALTER TABLE news_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE news_articles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_news_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_news_bookmarks ENABLE ROW LEVEL SECURITY;

ALTER TABLE user_news_preferences FORCE ROW LEVEL SECURITY;
ALTER TABLE user_news_bookmarks FORCE ROW LEVEL SECURITY;

CREATE POLICY news_sources_read_policy ON news_sources
  FOR SELECT USING (is_active = true OR monty_current_role() IN ('SUPER_ADMIN', 'DIRECTOR', 'ADMIN'));

CREATE POLICY news_sources_admin_write ON news_sources
  FOR ALL USING (monty_current_role() IN ('SUPER_ADMIN', 'DIRECTOR', 'ADMIN'));

-- Les particuliers / non-pro ne voient jamais PRO_ONLY ni les brouillons
CREATE POLICY news_articles_read_policy ON news_articles
  FOR SELECT USING (
    (status = 'PUBLISHED' AND audience_scope IN ('BOTH', 'PUBLIC_ONLY'))
    OR (status = 'PUBLISHED' AND monty_current_role() IN ('SUPER_ADMIN', 'DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT'))
    OR monty_current_role() IN ('SUPER_ADMIN', 'DIRECTOR', 'ADMIN')
  );

CREATE POLICY news_articles_admin_write ON news_articles
  FOR ALL USING (monty_current_role() IN ('SUPER_ADMIN', 'DIRECTOR', 'ADMIN'));

CREATE POLICY user_news_prefs_self ON user_news_preferences
  FOR ALL USING (monty_is_super_admin() OR user_id = monty_current_user_id());

CREATE POLICY user_news_bookmarks_self ON user_news_bookmarks
  FOR ALL USING (monty_is_super_admin() OR user_id = monty_current_user_id());
