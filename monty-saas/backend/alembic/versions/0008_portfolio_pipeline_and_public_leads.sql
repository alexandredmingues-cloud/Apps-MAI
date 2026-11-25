-- Monty SaaS — Migration 0008
-- Persistance complète Portefeuille Pro (Biens, Visites, Transactions/Offres)
-- + Captation Leads Publics Entrants (Alertes, Chasse Acquéreur, Avis Vendeur) reliée au CRM avec RLS strict & RGPD

-- 1. Enrichissement Portefeuille Biens, Visites & Offres/Transactions
ALTER TABLE properties
  ADD COLUMN IF NOT EXISTS docs_ratio numeric(4,2) NOT NULL DEFAULT 0.70;

ALTER TABLE visits
  ALTER COLUMN buyer_dossier_id DROP NOT NULL,
  ALTER COLUMN visit_date SET DEFAULT CURRENT_DATE,
  ALTER COLUMN time_slot SET DEFAULT '';

ALTER TABLE visits
  ADD COLUMN IF NOT EXISTS property_title varchar(180) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS buyer_name varchar(140) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS date_label varchar(120) NOT NULL DEFAULT '';

ALTER TABLE offers
  ALTER COLUMN buyer_dossier_id DROP NOT NULL;

ALTER TABLE offers
  ADD COLUMN IF NOT EXISTS property_title varchar(180) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS buyer_name varchar(140) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS stage varchar(20) NOT NULL DEFAULT 'OFFRE',
  ADD COLUMN IF NOT EXISTS notary_target_date date;

ALTER TABLE offers DROP CONSTRAINT IF EXISTS offers_stage_check;
ALTER TABLE offers ADD CONSTRAINT offers_stage_check
  CHECK (stage IN ('OFFRE', 'COMPROMIS', 'ACTE'));

-- 2. Leads Publics Entrants (Alertes, Recherche Guidée, Avis Conseiller Vendeur)
CREATE TABLE IF NOT EXISTS public_inbound_leads (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  agency_id uuid REFERENCES agencies(id) ON DELETE CASCADE,
  crm_contact_id uuid REFERENCES crm_contacts(id) ON DELETE SET NULL,
  lead_kind varchar(32) NOT NULL CHECK (lead_kind IN ('BUYER_CONCIERGE', 'SEARCH_ALERT', 'SELLER_VALUATION')),
  country_code varchar(2) NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
  full_name varchar(140) NOT NULL DEFAULT '',
  email citext NOT NULL,
  phone varchar(40) NOT NULL DEFAULT '',
  city varchar(120) NOT NULL DEFAULT '',
  budget_euro bigint NOT NULL DEFAULT 0 CHECK (budget_euro >= 0),
  min_rooms integer NOT NULL DEFAULT 1 CHECK (min_rooms BETWEEN 1 AND 30),
  search_transport_mode varchar(24) NOT NULL DEFAULT 'TRAM',
  max_travel_minutes integer NOT NULL DEFAULT 30 CHECK (max_travel_minutes BETWEEN 5 AND 240),
  horizon varchar(80) NOT NULL DEFAULT '',
  property_title varchar(180) NOT NULL DEFAULT '',
  notes text NOT NULL DEFAULT '',
  rgpd_consent_granted boolean NOT NULL DEFAULT false CHECK (rgpd_consent_granted = true),
  rgpd_consent_at timestamptz NOT NULL DEFAULT now(),
  status varchar(24) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'CONTACTED', 'CONVERTED', 'ARCHIVED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_public_leads_agency_status
  ON public_inbound_leads (agency_id, status, created_at DESC);

ALTER TABLE public_inbound_leads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public_inbound_leads FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_public_inbound_leads ON public_inbound_leads;
CREATE POLICY tenant_isolation_public_inbound_leads ON public_inbound_leads
  USING (
    monty_is_super_admin() OR (
      agency_id = monty_current_agency_id()
      AND monty_current_role() IN ('DIRECTOR', 'ADMIN', 'AGENT', 'ASSISTANT')
    )
  );

-- 3. Fonction SECURITY DEFINER d'ingestion publique sécurisée -> public_inbound_leads + crm_contacts + follow_up_tasks
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
