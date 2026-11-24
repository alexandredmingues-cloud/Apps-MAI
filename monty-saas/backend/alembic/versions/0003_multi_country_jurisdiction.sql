-- Monty SaaS — Migration 0003 : Architecture Multi-Pays & Multilingue (Socle Commun + Juridictions Nationales Isolées)
-- Priorité 1/2 : France entière ('FR') | Priorité 4 : Portugal ('PT') | Extensible Europe & International

ALTER TABLE agencies
  ADD COLUMN IF NOT EXISTS country_code varchar(2) NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
  ADD COLUMN IF NOT EXISTS default_locale varchar(10) NOT NULL DEFAULT 'fr-FR',
  ADD COLUMN IF NOT EXISTS currency_code varchar(3) NOT NULL DEFAULT 'EUR';

ALTER TABLE properties
  ADD COLUMN IF NOT EXISTS country_code varchar(2) NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
  ADD COLUMN IF NOT EXISTS postal_code varchar(16) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS city varchar(120) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS region_name varchar(120) NOT NULL DEFAULT '';

ALTER TABLE news_sources
  ADD COLUMN IF NOT EXISTS country_code varchar(2) NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
  ADD COLUMN IF NOT EXISTS locale varchar(10) NOT NULL DEFAULT 'fr-FR';

ALTER TABLE news_articles
  ADD COLUMN IF NOT EXISTS country_code varchar(2) NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
  ADD COLUMN IF NOT EXISTS locale varchar(10) NOT NULL DEFAULT 'fr-FR',
  ADD COLUMN IF NOT EXISTS regulation_jurisdiction varchar(24) NOT NULL DEFAULT 'FR_CODE_CIVIL';

CREATE INDEX IF NOT EXISTS idx_news_articles_country_pub
  ON news_articles (country_code, locale, status, audience_scope, published_at DESC);

ALTER TABLE user_news_preferences
  ADD COLUMN IF NOT EXISTS country_code varchar(2) NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
  ADD COLUMN IF NOT EXISTS locale varchar(10) NOT NULL DEFAULT 'fr-FR';

-- Table de configuration des packs juridictionnels nationaux (sans jamais mélanger les règles entre pays)
CREATE TABLE IF NOT EXISTS country_jurisdiction_packs (
  country_code varchar(2) PRIMARY KEY CHECK (country_code ~ '^[A-Z]{2}$'),
  country_name_fr varchar(100) NOT NULL,
  country_name_native varchar(100) NOT NULL,
  default_locale varchar(10) NOT NULL,
  currency_code varchar(3) NOT NULL DEFAULT 'EUR',
  rollout_phase varchar(24) NOT NULL CHECK (rollout_phase IN ('ACTIVE_PRIORITY_1', 'READY_PRIORITY_4', 'PLANNED_EUROPE')),
  tax_authority_name varchar(140) NOT NULL,
  credit_regulator_name varchar(140) NOT NULL,
  energy_scale_label varchar(80) NOT NULL,
  is_active boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT now()
);
