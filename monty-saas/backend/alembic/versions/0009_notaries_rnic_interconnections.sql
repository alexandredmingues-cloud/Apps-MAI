-- Monty SaaS — Migration 0009
-- 1. Annuaire & Carnet d'Études Notariales + Transmissions Compromis / Ouvertures Dossier (RLS strict)
-- 2. Base Nationale Copropriétés RNIC : Pass Payant Particulier vs Accès Gratuit Agence & Vendeur
-- 3. Interconnexions Modules (Notifications Agence, Feature Flags, Favoris & Coffre Client, Inscription Particulier)

-- 1. CARNET D'ÉTUDES NOTARIALES PAR AGENCE
CREATE TABLE IF NOT EXISTS agency_notary_contacts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  siret varchar(20) NOT NULL DEFAULT '',
  office_name varchar(180) NOT NULL,
  notary_name varchar(160) NOT NULL DEFAULT '',
  email citext NOT NULL DEFAULT '',
  phone varchar(40) NOT NULL DEFAULT '',
  street_address varchar(240) NOT NULL DEFAULT '',
  postal_code varchar(16) NOT NULL DEFAULT '',
  city varchar(100) NOT NULL DEFAULT '',
  department_code varchar(8) NOT NULL DEFAULT '',
  source varchar(40) NOT NULL DEFAULT 'DINUM_API_GOUV',
  email_verified boolean NOT NULL DEFAULT false,
  notes text NOT NULL DEFAULT '',
  created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (agency_id, office_name, postal_code)
);

CREATE INDEX IF NOT EXISTS idx_agency_notary_contacts_agency
  ON agency_notary_contacts (agency_id, department_code, city);

ALTER TABLE agency_notary_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE agency_notary_contacts FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_agency_notary_contacts ON agency_notary_contacts;
CREATE POLICY tenant_isolation_agency_notary_contacts ON agency_notary_contacts
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
    )
  );

-- 2. TRANSMISSIONS COMPROMIS & DEMANDES D'OUVERTURE DE DOSSIER NOTAIRE
CREATE TABLE IF NOT EXISTS notary_transmissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  notary_contact_id uuid REFERENCES agency_notary_contacts(id) ON DELETE SET NULL,
  transaction_id uuid REFERENCES offers(id) ON DELETE SET NULL,
  property_id uuid REFERENCES properties(id) ON DELETE SET NULL,
  dossier_ref varchar(80) NOT NULL,
  transmission_type varchar(40) NOT NULL CHECK (
    transmission_type IN ('OUVERTURE_DOSSIER', 'SIGNATURE_COMPROMIS', 'COMPLEMENT_ALUR')
  ),
  recipient_email citext NOT NULL DEFAULT '',
  recipient_office varchar(180) NOT NULL,
  recipient_address varchar(260) NOT NULL DEFAULT '',
  subject varchar(240) NOT NULL,
  checklist_items jsonb NOT NULL DEFAULT '[]'::jsonb,
  eml_content text NOT NULL,
  delivery_status varchar(32) NOT NULL CHECK (
    delivery_status IN ('READY_EML_DOWNLOAD', 'SENT_SMTP', 'SENT_RESEND', 'FAILED')
  ),
  delivery_provider_id varchar(160) NOT NULL DEFAULT '',
  created_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notary_transmissions_agency
  ON notary_transmissions (agency_id, created_at DESC);

ALTER TABLE notary_transmissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE notary_transmissions FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_notary_transmissions ON notary_transmissions;
CREATE POLICY tenant_isolation_notary_transmissions ON notary_transmissions
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
    )
  );

-- 3. PASS ACCÈS RNIC COPROPRIÉTÉS POUR PARTICULIERS / ACQUÉREURS (Gratuit Agences & Vendeurs)
CREATE TABLE IF NOT EXISTS rnic_access_passes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  buyer_email citext NOT NULL DEFAULT '',
  pass_token varchar(128) NOT NULL UNIQUE,
  stripe_session_id varchar(160) NOT NULL DEFAULT '' UNIQUE,
  amount_euro integer NOT NULL DEFAULT 19 CHECK (amount_euro >= 0),
  status varchar(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'REVOKED')),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rnic_passes_user_status
  ON rnic_access_passes (user_id, status, expires_at DESC);

-- 4. NOTIFICATIONS INTERNES INTERCONNECTÉES PAR AGENCE
CREATE TABLE IF NOT EXISTS agency_notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  event_type varchar(48) NOT NULL,
  severity varchar(16) NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO', 'HIGH', 'SUCCESS')),
  title varchar(200) NOT NULL,
  detail text NOT NULL DEFAULT '',
  link_section varchar(40) NOT NULL DEFAULT 'dashboard',
  target_id varchar(120) NOT NULL DEFAULT '',
  is_read boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agency_notifications_unread
  ON agency_notifications (agency_id, is_read, created_at DESC);

ALTER TABLE agency_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE agency_notifications FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_agency_notifications ON agency_notifications;
CREATE POLICY tenant_isolation_agency_notifications ON agency_notifications
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
    )
  );

-- 5. FEATURE FLAGS / PARAMÈTRES GOUVERNANCE AGENCE
CREATE TABLE IF NOT EXISTS agency_feature_flags (
  agency_id uuid PRIMARY KEY REFERENCES agencies(id) ON DELETE CASCADE,
  auto_matching_enabled boolean NOT NULL DEFAULT true,
  ai_copywriting_enabled boolean NOT NULL DEFAULT true,
  rnic_free_for_sellers boolean NOT NULL DEFAULT true,
  public_appointment_booking boolean NOT NULL DEFAULT true,
  notary_auto_task_on_compromis boolean NOT NULL DEFAULT true,
  updated_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE agency_feature_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE agency_feature_flags FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_agency_feature_flags ON agency_feature_flags;
CREATE POLICY tenant_isolation_agency_feature_flags ON agency_feature_flags
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
    )
  );

-- 6. FAVORIS & COFFRE DOCUMENTAIRE CLIENT (PARTICULIERS & VENDEURS)
CREATE TABLE IF NOT EXISTS client_portal_favorites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  listing_id varchar(120) NOT NULL,
  listing_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, listing_id)
);

ALTER TABLE client_portal_favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE client_portal_favorites FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_client_favorites ON client_portal_favorites;
CREATE POLICY tenant_isolation_client_favorites ON client_portal_favorites
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND (
        user_id = monty_current_user_id()
        OR monty_current_role() IN ('DIRECTOR', 'ADMIN')
      )
    )
  );

CREATE TABLE IF NOT EXISTS client_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
  owner_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title varchar(180) NOT NULL,
  doc_category varchar(48) NOT NULL DEFAULT 'FINANCEMENT',
  file_name varchar(200) NOT NULL,
  mime_type varchar(100) NOT NULL DEFAULT 'application/pdf',
  file_size_bytes bigint NOT NULL DEFAULT 0 CHECK (file_size_bytes >= 0),
  notes text NOT NULL DEFAULT '',
  status varchar(32) NOT NULL DEFAULT 'DEPOSE' CHECK (status IN ('DEPOSE', 'VERIFIE', 'A_COMPLETER')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_client_documents_owner
  ON client_documents (agency_id, owner_user_id, created_at DESC);

ALTER TABLE client_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE client_documents FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_client_documents ON client_documents;
CREATE POLICY tenant_isolation_client_documents ON client_documents
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND (
        owner_user_id = monty_current_user_id()
        OR monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
      )
    )
  );

-- 7. EXTENSION LEADS PUBLICS : PRISE DE RDV VISITE ('VISIT_REQUEST') + NOTIFICATION AGENCE AUTOMATIQUE
ALTER TABLE public_inbound_leads DROP CONSTRAINT IF EXISTS public_inbound_leads_lead_kind_check;
ALTER TABLE public_inbound_leads ADD CONSTRAINT public_inbound_leads_lead_kind_check
  CHECK (lead_kind IN ('BUYER_CONCIERGE', 'SEARCH_ALERT', 'SELLER_VALUATION', 'VISIT_REQUEST'));

CREATE OR REPLACE FUNCTION monty_submit_public_lead(
  p_agency_id uuid,
  p_lead_kind varchar(32),
  p_country_code varchar(2),
  p_full_name varchar(140),
  p_email citext,
  p_phone varchar(40),
  p_city varchar(120),
  p_budget_euro bigint,
  p_min_rooms integer,
  p_search_transport_mode varchar(24),
  p_max_travel_minutes integer,
  p_horizon varchar(80),
  p_property_title varchar(180),
  p_notes text,
  p_rgpd_consent_granted boolean
) RETURNS TABLE(
  lead_id uuid,
  agency_id uuid,
  crm_contact_id uuid,
  created_at timestamptz
) AS $$
DECLARE
  v_agency_id uuid;
  v_user_id uuid;
  v_contact_id uuid;
  v_lead_id uuid;
  v_created_at timestamptz;
  v_display_name varchar(140);
  v_contact_role varchar(40);
  v_phone_last9 varchar(12);
  v_task_title varchar(200);
BEGIN
  IF p_rgpd_consent_granted IS NOT TRUE THEN
    RAISE EXCEPTION 'Consentement RGPD explicite obligatoire (Art. 6.1.a).';
  END IF;
  IF length(trim(COALESCE(p_email::text, ''))) < 5 THEN
    RAISE EXCEPTION 'Adresse e-mail valide obligatoire.';
  END IF;

  IF p_agency_id IS NOT NULL THEN
    SELECT id INTO v_agency_id FROM agencies WHERE id = p_agency_id;
  END IF;
  IF v_agency_id IS NULL THEN
    SELECT id INTO v_agency_id
    FROM agencies
    WHERE country_code = COALESCE(NULLIF(p_country_code, ''), 'FR')
    ORDER BY created_at ASC
    LIMIT 1;
  END IF;
  IF v_agency_id IS NULL THEN
    SELECT id INTO v_agency_id FROM agencies ORDER BY created_at ASC LIMIT 1;
  END IF;

  v_display_name := COALESCE(NULLIF(trim(p_full_name), ''), split_part(p_email::text, '@', 1), 'Prospect Web');
  v_contact_role := CASE WHEN p_lead_kind = 'SELLER_VALUATION' THEN 'SELLER' ELSE 'BUYER' END;
  v_phone_last9 := right(regexp_replace(COALESCE(p_phone, ''), '[^0-9]', '', 'g'), 9);

  IF v_agency_id IS NOT NULL THEN
    SELECT user_id INTO v_user_id
    FROM agency_memberships
    WHERE agency_memberships.agency_id = v_agency_id AND status = 'ACTIVE'
    ORDER BY CASE role WHEN 'DIRECTOR' THEN 1 WHEN 'ADMIN' THEN 2 WHEN 'AGENT' THEN 3 ELSE 4 END, created_at ASC
    LIMIT 1;

    IF v_user_id IS NOT NULL THEN
      INSERT INTO crm_contacts (
        agency_id, created_by_user_id, assigned_to_user_id,
        full_name, email, phone, phone_normalized_last9,
        contact_type, status, priority, source, country_code,
        city, budget_max, sector, desired_rooms_min, project_timeline,
        rgpd_consent_granted, rgpd_consent_date, rgpd_legal_basis, rgpd_retention_until
      ) VALUES (
        v_agency_id, v_user_id, v_user_id,
        v_display_name, p_email, COALESCE(p_phone, ''), v_phone_last9,
        v_contact_role, 'Nouveau', 'Élevé', 'Site Web Monty', COALESCE(NULLIF(p_country_code, ''), 'FR'),
        COALESCE(p_city, ''), GREATEST(0, COALESCE(p_budget_euro, 0)), COALESCE(p_city, ''),
        GREATEST(1, COALESCE(p_min_rooms, 1)), COALESCE(p_horizon, ''),
        true, CURRENT_DATE, 'Consentement explicite (Art. 6.1.a)', (CURRENT_DATE + INTERVAL '36 months')::date
      )
      RETURNING id INTO v_contact_id;

      v_task_title := CASE
        WHEN p_lead_kind = 'SELLER_VALUATION' THEN 'Avis conseiller vendeur — ' || v_display_name
        WHEN p_lead_kind = 'SEARCH_ALERT' THEN 'Alerte recherche acquéreur — ' || v_display_name
        WHEN p_lead_kind = 'VISIT_REQUEST' THEN 'Demande RDV visite — ' || COALESCE(NULLIF(p_property_title, ''), v_display_name)
        ELSE 'Recherche guidée acquéreur — ' || v_display_name
      END;

      INSERT INTO follow_up_tasks (
        agency_id, contact_id, created_by_user_id, assigned_to_user_id,
        title, contact_name, reason, channel, due_at, priority, status, auto_generated
      ) VALUES (
        v_agency_id, v_contact_id, v_user_id, v_user_id,
        left(v_task_title, 200), v_display_name,
        left(COALESCE(NULLIF(p_notes, ''), 'Demande entrante depuis le site public Monty (' || COALESCE(p_city, '') || ').'), 400),
        'Email', now() + INTERVAL '4 hours', 'HIGH', 'OPEN', true
      );

      INSERT INTO agency_notifications (
        agency_id, event_type, severity, title, detail, link_section, target_id
      ) VALUES (
        v_agency_id, 'PUBLIC_LEAD_RECEIVED', 'HIGH',
        left(v_task_title, 200),
        left(v_display_name || ' (' || COALESCE(p_city, '') || ') — ' || COALESCE(NULLIF(p_notes, ''), 'Nouveau contact web RGPD'), 300),
        'crm', v_contact_id::text
      );
    END IF;
  END IF;

  INSERT INTO public_inbound_leads (
    agency_id, crm_contact_id, lead_kind, country_code,
    full_name, email, phone, city, budget_euro, min_rooms,
    search_transport_mode, max_travel_minutes, horizon, property_title, notes,
    rgpd_consent_granted, rgpd_consent_at, status
  ) VALUES (
    v_agency_id, v_contact_id, p_lead_kind, COALESCE(NULLIF(p_country_code, ''), 'FR'),
    v_display_name, p_email, COALESCE(p_phone, ''), COALESCE(p_city, ''),
    GREATEST(0, COALESCE(p_budget_euro, 0)), GREATEST(1, LEAST(30, COALESCE(p_min_rooms, 1))),
    COALESCE(NULLIF(p_search_transport_mode, ''), 'TRAM'),
    GREATEST(5, LEAST(240, COALESCE(p_max_travel_minutes, 30))),
    COALESCE(p_horizon, ''), COALESCE(p_property_title, ''), COALESCE(p_notes, ''),
    true, now(), 'NEW'
  )
  RETURNING id, public_inbound_leads.agency_id, public_inbound_leads.crm_contact_id, public_inbound_leads.created_at
  INTO v_lead_id, v_agency_id, v_contact_id, v_created_at;

  RETURN QUERY SELECT v_lead_id, v_agency_id, v_contact_id, v_created_at;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8. INSCRIPTION SÉCURISÉE PARTICULIER (BUYER / SELLER) SANS CRÉER D'AGENCE
CREATE OR REPLACE FUNCTION monty_auth_register_client(
  p_email citext,
  p_password_hash text,
  p_display_name varchar(120),
  p_role varchar(30),
  p_country_code varchar(2)
) RETURNS TABLE(
  user_id uuid,
  agency_id uuid,
  agency_name varchar(120),
  role varchar(30)
) AS $$
DECLARE
  v_uid uuid;
  v_aid uuid;
  v_aname varchar(120);
  v_clean_role varchar(30);
BEGIN
  v_clean_role := upper(trim(COALESCE(p_role, 'BUYER')));
  IF v_clean_role NOT IN ('BUYER', 'SELLER') THEN
    RAISE EXCEPTION 'Rôle client autorisé uniquement : BUYER ou SELLER.';
  END IF;

  SELECT id, name INTO v_aid, v_aname
  FROM agencies
  WHERE country_code = COALESCE(NULLIF(p_country_code, ''), 'FR')
  ORDER BY created_at ASC
  LIMIT 1;

  IF v_aid IS NULL THEN
    SELECT id, name INTO v_aid, v_aname FROM agencies ORDER BY created_at ASC LIMIT 1;
  END IF;

  IF v_aid IS NULL THEN
    INSERT INTO agencies (name, slug, city, country_code, plan_code, subscription_status, seat_limit)
    VALUES ('Monty Réseau France', 'monty-reseau-france', 'Lyon', COALESCE(NULLIF(p_country_code, ''), 'FR'), 'NETWORK', 'ACTIVE', 50)
    RETURNING id, name INTO v_aid, v_aname;
  END IF;

  INSERT INTO users (email, password_hash, display_name)
  VALUES (p_email, p_password_hash, p_display_name)
  RETURNING id INTO v_uid;

  INSERT INTO agency_memberships (agency_id, user_id, role, status)
  VALUES (v_aid, v_uid, v_clean_role, 'ACTIVE');

  RETURN QUERY SELECT v_uid, v_aid, v_aname, v_clean_role;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
