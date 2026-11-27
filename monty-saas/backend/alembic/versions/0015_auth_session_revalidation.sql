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
