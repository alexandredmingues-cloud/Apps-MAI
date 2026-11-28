-- Vérifie que le rôle runtime est compatible RLS.
SELECT
  r.rolname,
  r.rolsuper,
  r.rolbypassrls,
  r.rolcreatedb,
  r.rolcreaterole,
  r.rolcanlogin
FROM pg_roles AS r
WHERE r.rolname = 'monty_app';

SELECT
  CASE
    WHEN NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'monty_app')
      THEN 'FAIL: rôle monty_app absent'
    WHEN EXISTS (
      SELECT 1 FROM pg_roles
      WHERE rolname = 'monty_app' AND (rolsuper OR rolbypassrls OR NOT rolcanlogin)
    )
      THEN 'FAIL: monty_app ne doit pas être superuser / BYPASSRLS / sans LOGIN'
    ELSE 'OK: monty_app conforme au contrat runtime RLS'
  END AS rbac_runtime_check;
