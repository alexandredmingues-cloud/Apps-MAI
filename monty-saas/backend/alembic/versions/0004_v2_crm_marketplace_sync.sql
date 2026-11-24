-- Monty SaaS — Migration 0004 (V2 Production)
-- Synchronisation 1:1 CRM / Marketplace avec PostgreSQL RLS, modération annonces, et fonctions auth SECURITY DEFINER

-- 1. Colonnes complémentaires CRM V2 (multi-pays, relances auto, anonymisation RGPD)
ALTER TABLE crm_contacts
  ADD COLUMN IF NOT EXISTS country_code varchar(2) NOT NULL DEFAULT 'FR' CHECK (country_code ~ '^[A-Z]{2}$'),
  ADD COLUMN IF NOT EXISTS has_missing_docs boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS pending_offer_days integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_visit_days_ago integer,
  ADD COLUMN IF NOT EXISTS is_anonymized boolean NOT NULL DEFAULT false;

-- Autoriser les valeurs canoniques API (FR + EN) tout en préservant les valeurs Phase 1
ALTER TABLE crm_contacts DROP CONSTRAINT IF EXISTS crm_contacts_contact_type_check;
ALTER TABLE crm_contacts ADD CONSTRAINT crm_contacts_contact_type_check
  CHECK (contact_type IN ('BUYER', 'SELLER', 'LEAD', 'PROSPECT', 'PARTNER', 'Acquéreur', 'Vendeur', 'Propriétaire', 'Locataire', 'Investisseur', 'Partenaire', 'Autre', 'Mixte'));

ALTER TABLE contact_exchanges DROP CONSTRAINT IF EXISTS contact_exchanges_channel_check;
ALTER TABLE contact_exchanges ADD CONSTRAINT contact_exchanges_channel_check
  CHECK (channel IN ('CALL', 'EMAIL', 'VISIT', 'OFFER', 'MEETING', 'Appel', 'SMS', 'Email', 'Note', 'Visite'));

-- Autoriser plusieurs notes privées horodatées par contact et conseiller
ALTER TABLE advisor_private_notes
  DROP CONSTRAINT IF EXISTS advisor_private_notes_agency_id_entity_type_entity_id_author_key;

ALTER TABLE follow_up_tasks
  ADD COLUMN IF NOT EXISTS contact_name varchar(140) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS reason text NOT NULL DEFAULT '';

ALTER TABLE follow_up_tasks DROP CONSTRAINT IF EXISTS follow_up_tasks_channel_check;
ALTER TABLE follow_up_tasks ADD CONSTRAINT follow_up_tasks_channel_check
  CHECK (channel IN ('CALL', 'EMAIL', 'SMS', 'VISIT', 'Appel', 'Email', 'Visite'));

ALTER TABLE follow_up_tasks DROP CONSTRAINT IF EXISTS follow_up_tasks_priority_check;
ALTER TABLE follow_up_tasks ADD CONSTRAINT follow_up_tasks_priority_check
  CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW', 'Faible', 'Normale', 'Élevée', 'Urgente'));

ALTER TABLE follow_up_tasks DROP CONSTRAINT IF EXISTS follow_up_tasks_status_check;
ALTER TABLE follow_up_tasks ADD CONSTRAINT follow_up_tasks_status_check
  CHECK (status IN ('OPEN', 'COMPLETED', 'À faire', 'En cours', 'Terminée'));

-- 2. Colonnes Marketplace V2 + Modération stricte des annonces
ALTER TABLE properties
  ADD COLUMN IF NOT EXISTS photo_count integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS highlights jsonb NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS moderation_note text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS moderated_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS moderated_at timestamptz;

ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_publication_status_check;
ALTER TABLE properties ADD CONSTRAINT properties_publication_status_check
  CHECK (publication_status IN ('BROUILLON', 'A_VERIFIER', 'PUBLIEE', 'REJETEE', 'EXPIREE'));

-- Politique SELECT publique limitée EXCLUSIVEMENT aux annonces modérées et publiées
DROP POLICY IF EXISTS public_marketplace_read_published ON properties;
CREATE POLICY public_marketplace_read_published ON properties
  FOR SELECT
  USING (publication_status = 'PUBLIEE');

-- Index de purge RGPD et de modération
CREATE INDEX IF NOT EXISTS idx_crm_contacts_rgpd_purge
  ON crm_contacts (agency_id, is_anonymized, rgpd_retention_until);

CREATE INDEX IF NOT EXISTS idx_properties_moderation_queue
  ON properties (agency_id, publication_status, created_at DESC);

-- 3. Fonctions d'authentification SECURITY DEFINER (gardent FORCE RLS intacte sur agencies/memberships/invites)
CREATE OR REPLACE FUNCTION monty_auth_register_agency(
  p_agency_name varchar(120),
  p_agency_slug varchar(140),
  p_city varchar(80),
  p_country_code varchar(2),
  p_email citext,
  p_password_hash text,
  p_display_name varchar(120)
) RETURNS TABLE(user_id uuid, agency_id uuid, role varchar(30)) AS $$
DECLARE
  v_agency_id uuid;
  v_user_id uuid;
BEGIN
  INSERT INTO agencies (name, slug, city, country_code, default_locale, plan_code, subscription_status, seat_limit)
  VALUES (
    p_agency_name,
    p_agency_slug,
    COALESCE(NULLIF(p_city, ''), 'Lyon'),
    COALESCE(NULLIF(p_country_code, ''), 'FR'),
    CASE WHEN p_country_code = 'PT' THEN 'pt-PT' ELSE 'fr-FR' END,
    'INDIVIDUAL',
    'TRIAL',
    1
  )
  RETURNING id INTO v_agency_id;

  INSERT INTO users (email, password_hash, display_name, is_active)
  VALUES (p_email, p_password_hash, p_display_name, true)
  RETURNING id INTO v_user_id;

  INSERT INTO agency_memberships (agency_id, user_id, role, status)
  VALUES (v_agency_id, v_user_id, 'DIRECTOR', 'ACTIVE');

  RETURN QUERY SELECT v_user_id, v_agency_id, 'DIRECTOR'::varchar(30);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION monty_auth_lookup_login(
  p_email citext
) RETURNS TABLE(
  user_id uuid,
  email text,
  display_name varchar(120),
  password_hash text,
  is_active boolean,
  is_super_admin boolean,
  agency_id uuid,
  role varchar(30)
) AS $$
BEGIN
  RETURN QUERY
  SELECT
    u.id,
    u.email::text,
    u.display_name,
    u.password_hash,
    u.is_active,
    u.is_super_admin,
    m.agency_id,
    m.role
  FROM users u
  JOIN agency_memberships m ON m.user_id = u.id AND m.status = 'ACTIVE'
  WHERE u.email = p_email
  ORDER BY m.created_at ASC
  LIMIT 1;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION monty_auth_claim_invite(
  p_invite_code varchar(64),
  p_password_hash text,
  p_display_name varchar(120)
) RETURNS TABLE(user_id uuid, agency_id uuid, role varchar(30), email text) AS $$
DECLARE
  v_inv agency_invites%ROWTYPE;
  v_user_id uuid;
BEGIN
  SELECT * INTO v_inv
  FROM agency_invites
  WHERE invite_code = p_invite_code
    AND used_at IS NULL
    AND expires_at > now()
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Invitation introuvable, déjà utilisée ou expirée.';
  END IF;

  SELECT id INTO v_user_id FROM users WHERE users.email = v_inv.target_email;
  IF NOT FOUND THEN
    INSERT INTO users (email, password_hash, display_name, is_active)
    VALUES (v_inv.target_email, p_password_hash, COALESCE(NULLIF(p_display_name, ''), v_inv.target_name, 'Membre Monty'), true)
    RETURNING id INTO v_user_id;
  END IF;

  INSERT INTO agency_memberships (agency_id, user_id, role, status)
  VALUES (v_inv.agency_id, v_user_id, v_inv.role, 'ACTIVE')
  ON CONFLICT (agency_id, user_id) DO UPDATE SET role = EXCLUDED.role, status = 'ACTIVE', updated_at = now();

  UPDATE agency_invites
  SET used_at = now(), claimed_by_user_id = v_user_id
  WHERE id = v_inv.id;

  RETURN QUERY SELECT v_user_id, v_inv.agency_id, v_inv.role, v_inv.target_email::text;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
