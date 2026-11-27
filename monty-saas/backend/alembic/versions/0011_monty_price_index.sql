-- Migration 0011 : Monty Price Index (configuration des seuils & historique des évaluations)
-- Strictement non destructive et réversible.

CREATE TABLE IF NOT EXISTS monty_price_index_configs (
  agency_id uuid PRIMARY KEY REFERENCES agencies(id) ON DELETE CASCADE,
  very_good_deal_pct numeric(5, 2) NOT NULL DEFAULT -10.0,
  good_deal_pct numeric(5, 2) NOT NULL DEFAULT -5.0,
  above_market_pct numeric(5, 2) NOT NULL DEFAULT 5.0,
  overpriced_pct numeric(5, 2) NOT NULL DEFAULT 10.0,
  min_comparables_required integer NOT NULL DEFAULT 3,
  max_comparable_distance_m integer NOT NULL DEFAULT 2500,
  max_comparable_age_months integer NOT NULL DEFAULT 24,
  updated_by varchar(120) NOT NULL DEFAULT 'system',
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE monty_price_index_configs ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'monty_price_index_configs' AND policyname = 'monty_price_index_configs_tenant_rls'
  ) THEN
    CREATE POLICY monty_price_index_configs_tenant_rls ON monty_price_index_configs
      USING (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid)
      WITH CHECK (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS monty_price_index_history (
  id varchar(64) PRIMARY KEY,
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  target_ref varchar(120) NOT NULL DEFAULT '',
  title varchar(200) NOT NULL,
  country_code varchar(8) NOT NULL DEFAULT 'FR',
  city varchar(120) NOT NULL,
  property_kind varchar(60) NOT NULL,
  asking_price_euro bigint NOT NULL,
  estimated_market_value bigint NOT NULL,
  gap_percent numeric(6, 2) NOT NULL,
  category varchar(32) NOT NULL,
  confidence_tier varchar(24) NOT NULL,
  comparables_count integer NOT NULL DEFAULT 0,
  evaluated_by varchar(120) NOT NULL DEFAULT 'system',
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE monty_price_index_history ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'monty_price_index_history' AND policyname = 'monty_price_index_history_tenant_rls'
  ) THEN
    CREATE POLICY monty_price_index_history_tenant_rls ON monty_price_index_history
      USING (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid)
      WITH CHECK (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid);
  END IF;
END $$;
