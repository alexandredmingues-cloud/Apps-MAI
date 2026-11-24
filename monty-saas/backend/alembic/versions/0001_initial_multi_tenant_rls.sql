-- Monty SaaS — PostgreSQL 16 Multi-Tenant Schema & Row Level Security (Phase 1)
-- Isolation stricte par agency_id + séparation des notes privées + audit immuable + conformité RGPD

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- Fonctions utilitaires de contexte session transactionnel (SET LOCAL par requête FastAPI)
CREATE OR REPLACE FUNCTION monty_current_agency_id() RETURNS uuid AS $$
  SELECT NULLIF(current_setting('app.current_agency_id', true), '')::uuid;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION monty_current_user_id() RETURNS uuid AS $$
  SELECT NULLIF(current_setting('app.current_user_id', true), '')::uuid;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION monty_current_role() RETURNS text AS $$
  SELECT COALESCE(NULLIF(current_setting('app.current_role', true), ''), 'ANONYMOUS');
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION monty_is_super_admin() RETURNS boolean AS $$
  SELECT monty_current_role() = 'SUPER_ADMIN';
$$ LANGUAGE sql STABLE;

-- 1. AGENCES & ABONNEMENTS
CREATE TABLE agencies (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name varchar(120) NOT NULL,
  slug varchar(140) NOT NULL UNIQUE,
  city varchar(80) NOT NULL DEFAULT 'Montpellier',
  plan_code varchar(30) NOT NULL DEFAULT 'INDIVIDUAL' CHECK (plan_code IN ('INDIVIDUAL', 'AGENCY', 'NETWORK')),
  subscription_status varchar(30) NOT NULL DEFAULT 'TRIAL' CHECK (subscription_status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELED')),
  seat_limit integer NOT NULL DEFAULT 1 CHECK (seat_limit >= 1 AND seat_limit <= 500),
  ai_monthly_quota_tokens integer NOT NULL DEFAULT 250000,
  stripe_customer_id varchar(128) UNIQUE,
  stripe_subscription_id varchar(128) UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- 2. UTILISATEURS & MEMBRES MULTI-AGENCES
CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email citext NOT NULL UNIQUE,
  password_hash text NOT NULL,
  display_name varchar(120) NOT NULL,
  phone varchar(40) NOT NULL DEFAULT '',
  is_super_admin boolean NOT NULL DEFAULT false,
  is_active boolean NOT NULL DEFAULT true,
  last_login_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE agency_memberships (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role varchar(30) NOT NULL CHECK (role IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT', 'BUYER', 'SELLER')),
  status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INVITED', 'SUSPENDED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (agency_id, user_id)
);
CREATE INDEX idx_memberships_agency_role ON agency_memberships (agency_id, role, status);
CREATE INDEX idx_memberships_user ON agency_memberships (user_id, status);

CREATE TABLE agency_invites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  invite_code varchar(64) NOT NULL UNIQUE,
  target_email citext NOT NULL,
  target_name varchar(120) NOT NULL DEFAULT '',
  role varchar(30) NOT NULL CHECK (role IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT', 'BUYER', 'SELLER')),
  invited_by_user_id uuid NOT NULL REFERENCES users(id),
  claimed_by_user_id uuid REFERENCES users(id),
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_invites_agency ON agency_invites (agency_id, used_at);

-- 3. MODULE A — CRM IMMOBILIER
CREATE TABLE crm_contacts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  created_by_user_id uuid NOT NULL REFERENCES users(id),
  assigned_to_user_id uuid NOT NULL REFERENCES users(id),
  first_name varchar(80) NOT NULL DEFAULT '',
  last_name varchar(80) NOT NULL DEFAULT '',
  full_name varchar(140) NOT NULL,
  email citext NOT NULL DEFAULT '',
  phone varchar(40) NOT NULL DEFAULT '',
  phone_normalized_last9 varchar(12) NOT NULL DEFAULT '',
  postal_address varchar(240) NOT NULL DEFAULT '',
  contact_type varchar(40) NOT NULL CHECK (contact_type IN ('Acquéreur', 'Vendeur', 'Propriétaire', 'Locataire', 'Investisseur', 'Partenaire', 'Autre', 'Mixte')),
  status varchar(60) NOT NULL DEFAULT 'Nouveau',
  priority varchar(20) NOT NULL DEFAULT 'Normal' CHECK (priority IN ('Faible', 'Normal', 'Élevé', 'Urgent')),
  source varchar(60) NOT NULL DEFAULT 'Manuel',
  budget_min bigint NOT NULL DEFAULT 0 CHECK (budget_min >= 0),
  budget_max bigint NOT NULL DEFAULT 0 CHECK (budget_max >= 0),
  sector varchar(160) NOT NULL DEFAULT '',
  desired_property_type varchar(80) NOT NULL DEFAULT '',
  desired_surface_min integer NOT NULL DEFAULT 0,
  desired_rooms_min integer NOT NULL DEFAULT 0,
  financing_mode varchar(80) NOT NULL DEFAULT '',
  project_timeline varchar(80) NOT NULL DEFAULT '',
  archived boolean NOT NULL DEFAULT false,
  last_contacted_at timestamptz,
  next_follow_up_at timestamptz,
  rgpd_consent_granted boolean NOT NULL DEFAULT false,
  rgpd_consent_date date,
  rgpd_legal_basis varchar(80) NOT NULL DEFAULT 'Consentement explicite (Art. 6.1.a)',
  rgpd_retention_until date,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_crm_contacts_agency_status ON crm_contacts (agency_id, archived, status, priority);
CREATE INDEX idx_crm_contacts_dedup ON crm_contacts (agency_id, email, phone_normalized_last9);
CREATE INDEX idx_crm_contacts_assigned ON crm_contacts (agency_id, assigned_to_user_id);

-- Table séparée pour les notes privées confidentielles (jamais exposée aux assistants ni aux clients portail)
CREATE TABLE advisor_private_notes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  entity_type varchar(30) NOT NULL CHECK (entity_type IN ('CRM_CONTACT', 'BUYER_DOSSIER', 'PROPERTY', 'SELLER_DOSSIER')),
  entity_id uuid NOT NULL,
  author_user_id uuid NOT NULL REFERENCES users(id),
  content text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (agency_id, entity_type, entity_id, author_user_id)
);
CREATE INDEX idx_private_notes_lookup ON advisor_private_notes (agency_id, entity_type, entity_id);

CREATE TABLE contact_exchanges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  contact_id uuid NOT NULL REFERENCES crm_contacts(id) ON DELETE CASCADE,
  author_user_id uuid NOT NULL REFERENCES users(id),
  channel varchar(30) NOT NULL CHECK (channel IN ('Appel', 'SMS', 'Email', 'Note', 'Visite')),
  summary text NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_exchanges_contact ON contact_exchanges (agency_id, contact_id, occurred_at DESC);

CREATE TABLE follow_up_tasks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  contact_id uuid REFERENCES crm_contacts(id) ON DELETE SET NULL,
  created_by_user_id uuid NOT NULL REFERENCES users(id),
  assigned_to_user_id uuid NOT NULL REFERENCES users(id),
  title varchar(200) NOT NULL,
  channel varchar(30) NOT NULL CHECK (channel IN ('Appel', 'Email', 'SMS', 'Visite')),
  due_at timestamptz NOT NULL,
  priority varchar(20) NOT NULL DEFAULT 'Normale',
  status varchar(20) NOT NULL DEFAULT 'À faire' CHECK (status IN ('À faire', 'En cours', 'Terminée')),
  auto_generated boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_due ON follow_up_tasks (agency_id, status, due_at);

-- 4. MODULES D & E — ACQUÉREURS, BIENS, VENDEURS, VISITES, OFFRES
CREATE TABLE buyer_dossiers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  crm_contact_id uuid REFERENCES crm_contacts(id) ON DELETE SET NULL,
  portal_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  assigned_agent_id uuid NOT NULL REFERENCES users(id),
  display_name varchar(120) NOT NULL,
  project_stage smallint NOT NULL DEFAULT 0 CHECK (project_stage BETWEEN 0 AND 5),
  budget_max bigint NOT NULL CHECK (budget_max BETWEEN 10000 AND 50000000),
  down_payment bigint NOT NULL DEFAULT 0,
  monthly_income bigint NOT NULL DEFAULT 0,
  existing_credits bigint NOT NULL DEFAULT 0,
  bank_approval varchar(250) NOT NULL DEFAULT '',
  sectors jsonb NOT NULL DEFAULT '[]'::jsonb,
  property_type varchar(60) NOT NULL DEFAULT 'Appartement',
  min_surface integer NOT NULL DEFAULT 0,
  min_rooms integer NOT NULL DEFAULT 0,
  essential_criteria jsonb NOT NULL DEFAULT '[]'::jsonb,
  secondary_criteria jsonb NOT NULL DEFAULT '[]'::jsonb,
  timeline_delai varchar(100) NOT NULL DEFAULT 'Sous 3 mois',
  search_center_address varchar(220) NOT NULL DEFAULT '',
  search_center_lat double precision NOT NULL DEFAULT 0.0,
  search_center_lon double precision NOT NULL DEFAULT 0.0,
  search_mode varchar(24) NOT NULL DEFAULT 'SECTOR_LIST' CHECK (search_mode IN ('SECTOR_LIST', 'RADIUS_KM', 'TRAVEL_TIME')),
  search_radius_km integer NOT NULL DEFAULT 5,
  search_max_travel_minutes integer NOT NULL DEFAULT 20,
  search_transport_mode varchar(24) NOT NULL DEFAULT 'TRAM' CHECK (search_transport_mode IN ('VOITURE', 'TRAM', 'BUS', 'TRAIN', 'VELO', 'PIED')),
  portal_active boolean NOT NULL DEFAULT true,
  rgpd_consent_granted boolean NOT NULL DEFAULT false,
  rgpd_consent_date date,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_buyers_agency ON buyer_dossiers (agency_id, assigned_agent_id);
CREATE INDEX idx_buyers_portal_user ON buyer_dossiers (agency_id, portal_user_id);

CREATE TABLE properties (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  assigned_agent_id uuid NOT NULL REFERENCES users(id),
  seller_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  title varchar(160) NOT NULL,
  sector varchar(100) NOT NULL,
  address varchar(220) NOT NULL DEFAULT '',
  property_type varchar(60) NOT NULL,
  price_euro bigint NOT NULL CHECK (price_euro BETWEEN 10000 AND 50000000),
  surface_m2 integer NOT NULL CHECK (surface_m2 BETWEEN 5 AND 5000),
  rooms integer NOT NULL DEFAULT 1,
  bedrooms integer NOT NULL DEFAULT 0,
  floor_label varchar(80) NOT NULL DEFAULT '',
  dpe varchar(5) NOT NULL DEFAULT 'C',
  ges varchar(5) NOT NULL DEFAULT 'B',
  monthly_charges bigint NOT NULL DEFAULT 0,
  property_tax bigint NOT NULL DEFAULT 0,
  estimated_works bigint NOT NULL DEFAULT 0,
  exterior varchar(160) NOT NULL DEFAULT '',
  parking varchar(160) NOT NULL DEFAULT '',
  environment varchar(300) NOT NULL DEFAULT '',
  transports varchar(200) NOT NULL DEFAULT '',
  general_condition varchar(200) NOT NULL DEFAULT '',
  strengths jsonb NOT NULL DEFAULT '[]'::jsonb,
  weaknesses jsonb NOT NULL DEFAULT '[]'::jsonb,
  description text NOT NULL DEFAULT '',
  mandate_ref varchar(64) NOT NULL DEFAULT '',
  mandate_type varchar(40) NOT NULL DEFAULT '',
  mandate_date date,
  agency_fee_percent numeric(5,2) NOT NULL DEFAULT 0.0,
  listing_url varchar(500) NOT NULL DEFAULT '',
  publisher_type varchar(20) NOT NULL DEFAULT 'AGENCE' CHECK (publisher_type IN ('AGENCE', 'PARTICULIER')),
  publication_status varchar(20) NOT NULL DEFAULT 'BROUILLON' CHECK (publication_status IN ('BROUILLON', 'A_VERIFIER', 'PUBLIEE', 'EXPIREE')),
  hide_exact_address boolean NOT NULL DEFAULT false,
  latitude double precision NOT NULL DEFAULT 0.0,
  longitude double precision NOT NULL DEFAULT 0.0,
  cadastral_ref varchar(80) NOT NULL DEFAULT '',
  plu_zone varchar(60) NOT NULL DEFAULT '',
  sale_delay_min_days integer NOT NULL DEFAULT 0,
  sale_delay_likely_days integer NOT NULL DEFAULT 0,
  sale_delay_max_days integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_properties_agency_status ON properties (agency_id, publication_status, sector);

CREATE TABLE dvf_valuations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  property_id uuid REFERENCES properties(id) ON DELETE CASCADE,
  created_by_user_id uuid NOT NULL REFERENCES users(id),
  resolved_address varchar(240) NOT NULL,
  estimated_low_euro bigint NOT NULL,
  estimated_mid_euro bigint NOT NULL,
  estimated_high_euro bigint NOT NULL,
  raw_dvf_price_m2 bigint NOT NULL,
  adjusted_price_m2 bigint NOT NULL,
  total_adjustment_percent integer NOT NULL,
  valuation_score integer NOT NULL CHECK (valuation_score BETWEEN 0 AND 100),
  criteria_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  comparables_payload jsonb NOT NULL DEFAULT '[]'::jsonb,
  data_source varchar(100) NOT NULL,
  is_algorithmic_indicative boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_dvf_property ON dvf_valuations (agency_id, property_id, created_at DESC);

CREATE TABLE visits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  property_id uuid NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
  buyer_dossier_id uuid NOT NULL REFERENCES buyer_dossiers(id) ON DELETE CASCADE,
  advisor_user_id uuid NOT NULL REFERENCES users(id),
  visit_date date NOT NULL,
  time_slot varchar(40) NOT NULL,
  status varchar(60) NOT NULL DEFAULT 'Programmée',
  overall_rating numeric(3,1),
  client_impressions text NOT NULL DEFAULT '',
  positives text NOT NULL DEFAULT '',
  negatives text NOT NULL DEFAULT '',
  shared_with_client boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_visits_agency ON visits (agency_id, visit_date DESC);

CREATE TABLE offers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  property_id uuid NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
  buyer_dossier_id uuid NOT NULL REFERENCES buyer_dossiers(id) ON DELETE CASCADE,
  advisor_user_id uuid NOT NULL REFERENCES users(id),
  asking_price bigint NOT NULL,
  offered_price bigint NOT NULL,
  status varchar(60) NOT NULL DEFAULT 'Transmise au vendeur',
  client_financing text NOT NULL DEFAULT '',
  suspensive_conditions text NOT NULL DEFAULT '',
  arguments jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_offers_agency ON offers (agency_id, created_at DESC);

CREATE TABLE seller_dossiers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  property_id uuid NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
  seller_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  property_kind varchar(30) NOT NULL CHECK (property_kind IN ('Maison', 'Appartement')),
  residence_type varchar(50) NOT NULL CHECK (residence_type IN ('Maison individuelle', 'Résidence / Copropriété', 'Lotissement ASL')),
  syndic_name varchar(120) NOT NULL DEFAULT '',
  annual_copro_charges bigint NOT NULL DEFAULT 0,
  alur_work_fund_euro bigint NOT NULL DEFAULT 0,
  executive_summary text NOT NULL DEFAULT '',
  rgpd_consent_granted boolean NOT NULL DEFAULT false,
  rgpd_consent_date date,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE seller_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  seller_dossier_id uuid NOT NULL REFERENCES seller_dossiers(id) ON DELETE CASCADE,
  uploaded_by_user_id uuid REFERENCES users(id),
  code varchar(64) NOT NULL,
  title varchar(160) NOT NULL,
  category varchar(40) NOT NULL,
  required boolean NOT NULL DEFAULT true,
  status varchar(30) NOT NULL DEFAULT 'Manquant',
  s3_object_key varchar(300) NOT NULL DEFAULT '',
  mime_type varchar(100) NOT NULL DEFAULT '',
  size_bytes bigint NOT NULL DEFAULT 0,
  sha256_checksum varchar(64) NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_seller_docs ON seller_documents (agency_id, seller_dossier_id);

-- 5. IA QUOTAS, FACTURATION STRIPE & AUDIT IMMUABLE
CREATE TABLE ai_usage_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id),
  assistant_name varchar(40) NOT NULL,
  model_name varchar(60) NOT NULL,
  prompt_tokens integer NOT NULL DEFAULT 0,
  completion_tokens integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_usage_month ON ai_usage_logs (agency_id, created_at DESC);

CREATE TABLE stripe_webhook_events (
  event_id varchar(128) PRIMARY KEY,
  event_type varchar(100) NOT NULL,
  agency_id uuid REFERENCES agencies(id) ON DELETE SET NULL,
  processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  actor_role varchar(30) NOT NULL,
  action_type varchar(60) NOT NULL,
  target_type varchar(60) NOT NULL DEFAULT '',
  target_id varchar(128) NOT NULL DEFAULT '',
  detail text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_agency ON audit_logs (agency_id, created_at DESC);

-- Trigger d'immuabilité sur audit_logs (aucun UPDATE/DELETE applicatif possible)
CREATE OR REPLACE FUNCTION prevent_audit_log_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();

-- 6. ROW LEVEL SECURITY (RLS) MULTI-TENANT STRICT
ALTER TABLE agencies ENABLE ROW LEVEL SECURITY;
ALTER TABLE agency_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE agency_invites ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE advisor_private_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE contact_exchanges ENABLE ROW LEVEL SECURITY;
ALTER TABLE follow_up_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE buyer_dossiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE properties ENABLE ROW LEVEL SECURITY;
ALTER TABLE dvf_valuations ENABLE ROW LEVEL SECURITY;
ALTER TABLE visits ENABLE ROW LEVEL SECURITY;
ALTER TABLE offers ENABLE ROW LEVEL SECURITY;
ALTER TABLE seller_dossiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE seller_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_usage_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

ALTER TABLE agencies FORCE ROW LEVEL SECURITY;
ALTER TABLE crm_contacts FORCE ROW LEVEL SECURITY;
ALTER TABLE advisor_private_notes FORCE ROW LEVEL SECURITY;
ALTER TABLE contact_exchanges FORCE ROW LEVEL SECURITY;
ALTER TABLE follow_up_tasks FORCE ROW LEVEL SECURITY;
ALTER TABLE buyer_dossiers FORCE ROW LEVEL SECURITY;
ALTER TABLE properties FORCE ROW LEVEL SECURITY;
ALTER TABLE dvf_valuations FORCE ROW LEVEL SECURITY;
ALTER TABLE visits FORCE ROW LEVEL SECURITY;
ALTER TABLE offers FORCE ROW LEVEL SECURITY;
ALTER TABLE seller_dossiers FORCE ROW LEVEL SECURITY;
ALTER TABLE seller_documents FORCE ROW LEVEL SECURITY;
ALTER TABLE ai_usage_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;

-- Politiques standard d'isolation par agency_id
CREATE POLICY tenant_isolation_agencies ON agencies
  USING (monty_is_super_admin() OR id = monty_current_agency_id());

CREATE POLICY tenant_isolation_memberships ON agency_memberships
  USING (monty_is_super_admin() OR agency_id = monty_current_agency_id());

CREATE POLICY tenant_isolation_invites ON agency_invites
  USING (monty_is_super_admin() OR agency_id = monty_current_agency_id());

CREATE POLICY tenant_isolation_crm_contacts ON crm_contacts
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
    )
  );

-- Politique renforcée pour notes privées : interdite aux clients et assistants
CREATE POLICY tenant_isolation_private_notes ON advisor_private_notes
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT')
      AND (monty_current_role() IN ('DIRECTOR', 'ADMIN') OR author_user_id = monty_current_user_id())
    )
  );

CREATE POLICY tenant_isolation_exchanges ON contact_exchanges
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
    )
  );

CREATE POLICY tenant_isolation_tasks ON follow_up_tasks
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
    )
  );

-- Portail Acquéreur isolé : un BUYER ne voit que son propre dossier
CREATE POLICY tenant_isolation_buyer_dossiers ON buyer_dossiers
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND (
        monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
        OR (monty_current_role() = 'BUYER' AND portal_user_id = monty_current_user_id())
      )
    )
  );

CREATE POLICY tenant_isolation_properties ON properties
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND (
        monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
        OR (monty_current_role() = 'BUYER' AND publication_status = 'PUBLIEE')
        OR (monty_current_role() = 'SELLER' AND seller_user_id = monty_current_user_id())
      )
    )
  );

CREATE POLICY tenant_isolation_dvf ON dvf_valuations
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT')
    )
  );

CREATE POLICY tenant_isolation_visits ON visits
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND (
        monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
        OR (
          monty_current_role() = 'BUYER' AND shared_with_client = true
          AND EXISTS (
            SELECT 1 FROM buyer_dossiers b
            WHERE b.id = visits.buyer_dossier_id AND b.portal_user_id = monty_current_user_id()
          )
        )
        OR (
          monty_current_role() = 'SELLER' AND shared_with_client = true
          AND EXISTS (
            SELECT 1 FROM properties p
            WHERE p.id = visits.property_id AND p.seller_user_id = monty_current_user_id()
          )
        )
      )
    )
  );

CREATE POLICY tenant_isolation_offers ON offers
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND (
        monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT')
        OR EXISTS (
          SELECT 1 FROM buyer_dossiers b
          WHERE b.id = offers.buyer_dossier_id AND b.portal_user_id = monty_current_user_id()
        )
        OR EXISTS (
          SELECT 1 FROM properties p
          WHERE p.id = offers.property_id AND p.seller_user_id = monty_current_user_id()
        )
      )
    )
  );

CREATE POLICY tenant_isolation_seller_dossiers ON seller_dossiers
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND (
        monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
        OR (monty_current_role() = 'SELLER' AND seller_user_id = monty_current_user_id())
      )
    )
  );

CREATE POLICY tenant_isolation_seller_docs ON seller_documents
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND (
        monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
        OR EXISTS (
          SELECT 1 FROM seller_dossiers sd
          WHERE sd.id = seller_documents.seller_dossier_id AND sd.seller_user_id = monty_current_user_id()
        )
      )
    )
  );

CREATE POLICY tenant_isolation_ai_usage ON ai_usage_logs
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT')
    )
  );

CREATE POLICY tenant_isolation_audit_logs ON audit_logs
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN')
    )
  );
