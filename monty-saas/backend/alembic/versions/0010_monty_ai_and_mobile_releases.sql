-- Migration 0010 : Monty AI Core (mémoires, conversations, actions humaines, moteurs, audit),
-- Distribution APK Mobile (versions, empreintes SHA-256, téléchargements),
-- Gestion centralisée des modules (Feature Flags) et Mots-clés Monty Leads.
-- Strictement non destructive et réversible.

CREATE TABLE IF NOT EXISTS monty_ai_engine_configs (
  engine_id varchar(64) PRIMARY KEY,
  provider varchar(48) NOT NULL,
  model_name varchar(120) NOT NULL,
  tier varchar(24) NOT NULL DEFAULT 'hybrid',
  is_local boolean NOT NULL DEFAULT false,
  confidential_safe boolean NOT NULL DEFAULT false,
  enabled boolean NOT NULL DEFAULT true,
  is_default boolean NOT NULL DEFAULT false,
  is_fallback boolean NOT NULL DEFAULT false,
  priority integer NOT NULL DEFAULT 100,
  min_ram_gb integer NOT NULL DEFAULT 0,
  masked_api_key varchar(32) NOT NULL DEFAULT '',
  updated_by varchar(120) NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS monty_ai_memories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  user_id varchar(120) NOT NULL,
  scope varchar(32) NOT NULL DEFAULT 'USER',
  title varchar(180) NOT NULL,
  content text NOT NULL,
  source_ref varchar(180) NOT NULL DEFAULT '',
  retention_until date NOT NULL DEFAULT (CURRENT_DATE + INTERVAL '365 days'),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE monty_ai_memories ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'monty_ai_memories' AND policyname = 'monty_ai_memories_tenant_rls'
  ) THEN
    CREATE POLICY monty_ai_memories_tenant_rls ON monty_ai_memories
      USING (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid)
      WITH CHECK (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS monty_ai_conversations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  user_id varchar(120) NOT NULL,
  task_type varchar(48) NOT NULL,
  engine_used varchar(80) NOT NULL,
  execution_mode varchar(48) NOT NULL,
  sanitized_prompt text NOT NULL,
  response_text text NOT NULL,
  sources_json jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE monty_ai_conversations ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'monty_ai_conversations' AND policyname = 'monty_ai_conversations_tenant_rls'
  ) THEN
    CREATE POLICY monty_ai_conversations_tenant_rls ON monty_ai_conversations
      USING (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid)
      WITH CHECK (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS monty_ai_pending_actions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  requested_by_user_id varchar(120) NOT NULL,
  action_type varchar(64) NOT NULL,
  title varchar(200) NOT NULL,
  summary text NOT NULL,
  payload_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  status varchar(24) NOT NULL DEFAULT 'A_VALIDER',
  reviewed_by_user_id varchar(120),
  created_at timestamptz NOT NULL DEFAULT now(),
  reviewed_at timestamptz
);

ALTER TABLE monty_ai_pending_actions ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'monty_ai_pending_actions' AND policyname = 'monty_ai_pending_actions_tenant_rls'
  ) THEN
    CREATE POLICY monty_ai_pending_actions_tenant_rls ON monty_ai_pending_actions
      USING (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid)
      WITH CHECK (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS monty_ai_audit_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  user_id varchar(120) NOT NULL,
  engine_id varchar(64) NOT NULL,
  task_type varchar(48) NOT NULL,
  status varchar(32) NOT NULL,
  latency_ms integer NOT NULL DEFAULT 0,
  estimated_cost_euro numeric(10, 4) NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE monty_ai_audit_events ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'monty_ai_audit_events' AND policyname = 'monty_ai_audit_events_tenant_rls'
  ) THEN
    CREATE POLICY monty_ai_audit_events_tenant_rls ON monty_ai_audit_events
      USING (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid)
      WITH CHECK (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS mobile_app_releases (
  id varchar(64) PRIMARY KEY,
  version_name varchar(32) NOT NULL,
  version_code integer NOT NULL,
  package_id varchar(140) NOT NULL,
  min_android_sdk integer NOT NULL DEFAULT 24,
  sha256 varchar(64) NOT NULL,
  file_size_bytes bigint NOT NULL,
  file_name varchar(180) NOT NULL,
  storage_path text NOT NULL,
  release_notes text NOT NULL DEFAULT '',
  signature_verified boolean NOT NULL DEFAULT false,
  is_published boolean NOT NULL DEFAULT false,
  is_recommended boolean NOT NULL DEFAULT false,
  download_count integer NOT NULL DEFAULT 0,
  published_by varchar(120) NOT NULL DEFAULT 'system',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agency_module_flags (
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  module_key varchar(48) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'DISPONIBLE',
  visible boolean NOT NULL DEFAULT true,
  updated_by varchar(120) NOT NULL DEFAULT 'system',
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (agency_id, module_key)
);

ALTER TABLE agency_module_flags ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'agency_module_flags' AND policyname = 'agency_module_flags_tenant_rls'
  ) THEN
    CREATE POLICY agency_module_flags_tenant_rls ON agency_module_flags
      USING (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid)
      WITH CHECK (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS lead_keyword_rules (
  id varchar(64) PRIMARY KEY,
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  category varchar(32) NOT NULL,
  keyword varchar(120) NOT NULL,
  geo_scope varchar(80) NOT NULL DEFAULT 'France',
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE lead_keyword_rules ENABLE ROW LEVEL SECURITY;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'lead_keyword_rules' AND policyname = 'lead_keyword_rules_tenant_rls'
  ) THEN
    CREATE POLICY lead_keyword_rules_tenant_rls ON lead_keyword_rules
      USING (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid)
      WITH CHECK (agency_id = NULLIF(current_setting('app.current_agency_id', true), '')::uuid);
  END IF;
END $$;
