-- Monty SaaS — Migration 0005 (V2 Production Ops)
-- Cache DVF planifié, logs d'audit des 11 agents IA, file Monty Scout, consentements WhatsApp & Pré-État Daté

CREATE TABLE IF NOT EXISTS dvf_mutations_cache (
  mutation_id varchar(80) PRIMARY KEY,
  sector_code varchar(16) NOT NULL,
  date_mutation date NOT NULL,
  year integer NOT NULL CHECK (year BETWEEN 2014 AND 2035),
  price_euro integer NOT NULL CHECK (price_euro >= 15000),
  surface_m2 integer NOT NULL CHECK (surface_m2 >= 10),
  property_type varchar(40) NOT NULL,
  distance_meters integer NOT NULL DEFAULT 0,
  synced_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dvf_cache_sector_date
  ON dvf_mutations_cache (sector_code, date_mutation DESC);

CREATE TABLE IF NOT EXISTS agent_execution_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  actor_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  agent_id varchar(64) NOT NULL,
  model_used varchar(80) NOT NULL,
  sanitized_input text NOT NULL,
  output_summary text NOT NULL,
  duration_ms integer NOT NULL DEFAULT 0,
  cost_euro numeric(10, 4) NOT NULL DEFAULT 0.0,
  cache_hit boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE agent_execution_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_execution_logs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_agent_logs ON agent_execution_logs;
CREATE POLICY tenant_isolation_agent_logs ON agent_execution_logs
  USING (agency_id::text = current_setting('app.current_agency_id', true));

CREATE TABLE IF NOT EXISTS scout_proposals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  created_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_contact_name varchar(120) NOT NULL,
  channel varchar(20) NOT NULL CHECK (channel IN ('WHATSAPP', 'EMAIL', 'SMS')),
  legal_basis varchar(80) NOT NULL,
  opt_out_keyword varchar(20) NOT NULL DEFAULT 'STOP',
  message_draft text NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'A_VALIDER' CHECK (status IN ('A_VALIDER', 'VALIDEE', 'REJETEE')),
  reviewed_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  reviewed_at timestamptz
);

ALTER TABLE scout_proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE scout_proposals FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_scout_proposals ON scout_proposals;
CREATE POLICY tenant_isolation_scout_proposals ON scout_proposals
  USING (agency_id::text = current_setting('app.current_agency_id', true));

CREATE TABLE IF NOT EXISTS whatsapp_consent_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_masked varchar(32) NOT NULL,
  phone_hash varchar(64) NOT NULL,
  direction varchar(16) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
  action varchar(32) NOT NULL CHECK (action IN ('OPT_IN', 'OPT_OUT', 'MESSAGE_RECEIVED', 'TEMPLATE_SENT')),
  keyword_matched varchar(32) NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS pre_etat_date_dossiers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  dossier_ref varchar(64) NOT NULL,
  document_version varchar(32) NOT NULL,
  export_allowed boolean NOT NULL DEFAULT false,
  payload_json jsonb NOT NULL,
  result_json jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE pre_etat_date_dossiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE pre_etat_date_dossiers FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_pre_etat ON pre_etat_date_dossiers;
CREATE POLICY tenant_isolation_pre_etat ON pre_etat_date_dossiers
  USING (agency_id::text = current_setting('app.current_agency_id', true));
