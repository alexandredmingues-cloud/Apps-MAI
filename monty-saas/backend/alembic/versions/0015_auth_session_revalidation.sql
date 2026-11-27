-- Revalidate JWTs against current user and agency membership state.
-- The flag is transaction-local and is only set inside the SECURITY DEFINER lookup.

DROP POLICY IF EXISTS tenant_isolation_memberships ON agency_memberships;
CREATE POLICY tenant_isolation_memberships ON agency_memberships
  USING (
    COALESCE(current_setting('app.auth_validation', true), '') = 'on'
    OR monty_is_super_admin()
    OR agency_id = monty_current_agency_id()
  )
  WITH CHECK (
    monty_is_super_admin()
    OR agency_id = monty_current_agency_id()
  );

CREATE OR REPLACE FUNCTION monty_auth_validate_session(
  p_user_id uuid,
  p_agency_id uuid,
  p_role varchar(30)
) RETURNS TABLE(
  is_active boolean,
  is_super_admin boolean,
  membership_role varchar(30),
  membership_status varchar(20)
) AS $$
BEGIN
  PERFORM set_config('app.auth_validation', 'on', true);

  RETURN QUERY
  SELECT u.is_active, u.is_super_admin, m.role, m.status
  FROM users AS u
  LEFT JOIN agency_memberships AS m
    ON m.user_id = u.id AND m.agency_id = p_agency_id
  WHERE u.id = p_user_id;
END;
$$ LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp;

COMMENT ON FUNCTION monty_auth_validate_session(uuid, uuid, varchar) IS
  'Returns current account and membership state for JWT revalidation; never returns credentials.';

-- Link verified Firebase identities to an existing SaaS account. The agency
-- and role always come from an active PostgreSQL membership, never client claims.
ALTER TABLE users ADD COLUMN IF NOT EXISTS firebase_uid varchar(128);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_firebase_uid
  ON users (firebase_uid) WHERE firebase_uid IS NOT NULL;

CREATE OR REPLACE FUNCTION monty_auth_firebase_exchange(
  p_firebase_uid varchar(128),
  p_email citext,
  p_email_verified boolean,
  p_agency_id uuid
) RETURNS TABLE(
  user_id uuid,
  agency_id uuid,
  role varchar(30),
  agency_name varchar(120),
  display_name varchar(120),
  email text,
  is_active boolean
) AS $$
DECLARE
  v_user_id uuid;
BEGIN
  PERFORM set_config('app.auth_validation', 'on', true);

  SELECT u.id INTO v_user_id
  FROM users AS u
  WHERE u.firebase_uid = p_firebase_uid;

  IF v_user_id IS NULL AND p_email_verified AND p_email IS NOT NULL THEN
    UPDATE users AS u
       SET firebase_uid = p_firebase_uid, updated_at = now()
     WHERE u.email = p_email AND u.firebase_uid IS NULL
     RETURNING u.id INTO v_user_id;
  END IF;

  IF v_user_id IS NULL THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT u.id, m.agency_id, m.role, a.name, u.display_name, u.email::text, u.is_active
  FROM users AS u
  JOIN agency_memberships AS m ON m.user_id = u.id
  JOIN agencies AS a ON a.id = m.agency_id
  WHERE u.id = v_user_id
    AND m.agency_id = p_agency_id
    AND m.status = 'ACTIVE';
END;
$$ LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp;

COMMENT ON FUNCTION monty_auth_firebase_exchange(varchar, citext, boolean, uuid) IS
  'Links only verified Firebase emails to existing SaaS users and returns role/agency from an active membership.';
