-- Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code.
-- Close RLS gaps on tables created without policies (identity, reference, payments, messaging).
-- Auth paths that must bypass tenant GUCs continue via SECURITY DEFINER functions (0015).

-- ---------------------------------------------------------------------------
-- users : self + super-admin only (avoid recursive policy via memberships)
-- ---------------------------------------------------------------------------
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS users_select_self_or_super ON users;
CREATE POLICY users_select_self_or_super ON users
  FOR SELECT
  USING (
    monty_is_super_admin()
    OR id = monty_current_user_id()
  );

DROP POLICY IF EXISTS users_update_self_or_super ON users;
CREATE POLICY users_update_self_or_super ON users
  FOR UPDATE
  USING (
    monty_is_super_admin()
    OR id = monty_current_user_id()
  )
  WITH CHECK (
    monty_is_super_admin()
    OR id = monty_current_user_id()
  );

DROP POLICY IF EXISTS users_insert_super_only ON users;
CREATE POLICY users_insert_super_only ON users
  FOR INSERT
  WITH CHECK (monty_is_super_admin());

DROP POLICY IF EXISTS users_delete_super_only ON users;
CREATE POLICY users_delete_super_only ON users
  FOR DELETE
  USING (monty_is_super_admin());

-- ---------------------------------------------------------------------------
-- country_jurisdiction_packs : référentiel lecture ouverte, écriture super-admin
-- ---------------------------------------------------------------------------
ALTER TABLE country_jurisdiction_packs ENABLE ROW LEVEL SECURITY;
ALTER TABLE country_jurisdiction_packs FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS country_packs_public_read ON country_jurisdiction_packs;
CREATE POLICY country_packs_public_read ON country_jurisdiction_packs
  FOR SELECT
  USING (is_active = true OR monty_is_super_admin());

DROP POLICY IF EXISTS country_packs_super_write ON country_jurisdiction_packs;
CREATE POLICY country_packs_super_write ON country_jurisdiction_packs
  FOR ALL
  USING (monty_is_super_admin())
  WITH CHECK (monty_is_super_admin());

-- ---------------------------------------------------------------------------
-- dvf_mutations_cache : cache métier lisible pro, écriture super-admin
-- ---------------------------------------------------------------------------
ALTER TABLE dvf_mutations_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE dvf_mutations_cache FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS dvf_cache_pro_read ON dvf_mutations_cache;
CREATE POLICY dvf_cache_pro_read ON dvf_mutations_cache
  FOR SELECT
  USING (
    monty_is_super_admin()
    OR monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
  );

DROP POLICY IF EXISTS dvf_cache_super_write ON dvf_mutations_cache;
CREATE POLICY dvf_cache_super_write ON dvf_mutations_cache
  FOR ALL
  USING (monty_is_super_admin())
  WITH CHECK (monty_is_super_admin());

-- ---------------------------------------------------------------------------
-- mobile_app_releases : lecture des builds publiés, écriture super-admin
-- ---------------------------------------------------------------------------
ALTER TABLE mobile_app_releases ENABLE ROW LEVEL SECURITY;
ALTER TABLE mobile_app_releases FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS mobile_releases_public_read_published ON mobile_app_releases;
CREATE POLICY mobile_releases_public_read_published ON mobile_app_releases
  FOR SELECT
  USING (is_published = true OR monty_is_super_admin());

DROP POLICY IF EXISTS mobile_releases_super_write ON mobile_app_releases;
CREATE POLICY mobile_releases_super_write ON mobile_app_releases
  FOR ALL
  USING (monty_is_super_admin())
  WITH CHECK (monty_is_super_admin());

-- ---------------------------------------------------------------------------
-- monty_ai_engine_configs : lecture pro, écriture super-admin
-- ---------------------------------------------------------------------------
ALTER TABLE monty_ai_engine_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE monty_ai_engine_configs FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS monty_ai_engine_configs_pro_read ON monty_ai_engine_configs;
CREATE POLICY monty_ai_engine_configs_pro_read ON monty_ai_engine_configs
  FOR SELECT
  USING (
    monty_is_super_admin()
    OR monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
  );

DROP POLICY IF EXISTS monty_ai_engine_configs_super_write ON monty_ai_engine_configs;
CREATE POLICY monty_ai_engine_configs_super_write ON monty_ai_engine_configs
  FOR ALL
  USING (monty_is_super_admin())
  WITH CHECK (monty_is_super_admin());

-- ---------------------------------------------------------------------------
-- agent_governance_state : kill-switch visible pro, mutation super-admin
-- ---------------------------------------------------------------------------
ALTER TABLE agent_governance_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_governance_state FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS agent_governance_pro_read ON agent_governance_state;
CREATE POLICY agent_governance_pro_read ON agent_governance_state
  FOR SELECT
  USING (
    monty_is_super_admin()
    OR monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT')
  );

DROP POLICY IF EXISTS agent_governance_super_write ON agent_governance_state;
CREATE POLICY agent_governance_super_write ON agent_governance_state
  FOR ALL
  USING (monty_is_super_admin())
  WITH CHECK (monty_is_super_admin());

-- ---------------------------------------------------------------------------
-- stripe_webhook_events : processeur paiement + lecture agence concernée
-- ---------------------------------------------------------------------------
ALTER TABLE stripe_webhook_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE stripe_webhook_events FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS stripe_webhook_payment_processor ON stripe_webhook_events;
CREATE POLICY stripe_webhook_payment_processor ON stripe_webhook_events
  FOR ALL
  USING (
    current_setting('app.payment_processor', true) = 'on'
    OR monty_is_super_admin()
  )
  WITH CHECK (
    current_setting('app.payment_processor', true) = 'on'
    OR monty_is_super_admin()
  );

DROP POLICY IF EXISTS stripe_webhook_agency_read ON stripe_webhook_events;
CREATE POLICY stripe_webhook_agency_read ON stripe_webhook_events
  FOR SELECT
  USING (
    agency_id IS NOT NULL
    AND agency_id = monty_current_agency_id()
    AND monty_current_role() IN ('DIRECTOR', 'ADMIN')
  );

-- ---------------------------------------------------------------------------
-- whatsapp_consent_events : PII — processeur messaging ou super-admin uniquement
-- ---------------------------------------------------------------------------
ALTER TABLE whatsapp_consent_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE whatsapp_consent_events FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS whatsapp_consent_processor_only ON whatsapp_consent_events;
CREATE POLICY whatsapp_consent_processor_only ON whatsapp_consent_events
  FOR ALL
  USING (
    current_setting('app.messaging_processor', true) = 'on'
    OR monty_is_super_admin()
  )
  WITH CHECK (
    current_setting('app.messaging_processor', true) = 'on'
    OR monty_is_super_admin()
  );
