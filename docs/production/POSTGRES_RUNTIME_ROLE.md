# PostgreSQL production runtime role — MON-AGENT

## Objective

Use a dedicated PostgreSQL login for the API. Keep the Supabase project owner / migration login out of the API runtime.

The API currently rejects a production connection whose `current_user` is SUPERUSER or BYPASSRLS. Do not weaken that check to make a privileged connection work.

## Separation of duties

- **Migration/admin connection**: used only by the deployment/migration job. It may own schema objects and apply Alembic migrations.
- **Runtime connection**: used by FastAPI. It must not be a superuser, must not have BYPASSRLS, must not be a member of a privileged role, and must not own tenant tables.
- Never commit a database password or paste it into an issue, pull request, chat, or source file.

## Provision a runtime login (Supabase SQL Editor)

Run once as the project database administrator. Replace the password placeholder locally with a strong generated secret; do not commit or share it.

```sql
CREATE ROLE mon_agent_app
  LOGIN
  PASSWORD 'REPLACE_LOCALLY_WITH_A_LONG_RANDOM_SECRET'
  NOSUPERUSER
  NOCREATEDB
  NOCREATEROLE
  NOINHERIT
  NOREPLICATION
  NOBYPASSRLS;

GRANT CONNECT ON DATABASE postgres TO mon_agent_app;
GRANT USAGE ON SCHEMA public TO mon_agent_app;

-- Runtime DML only; no CREATE/ALTER/DROP privileges are granted.
GRANT SELECT, INSERT, UPDATE, DELETE
  ON ALL TABLES IN SCHEMA public
  TO mon_agent_app;

GRANT USAGE, SELECT, UPDATE
  ON ALL SEQUENCES IN SCHEMA public
  TO mon_agent_app;

-- Required for calling application functions, including narrowly-scoped
-- SECURITY DEFINER functions installed by reviewed migrations.
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO mon_agent_app;
```

**Important:** this is a compatibility baseline, not a final per-table least-privilege grant set. It grants DML on all current public-schema tables; RLS remains responsible for row isolation. A subsequent privilege inventory should narrow grants to the tables and functions actually used by the API. Do not grant table ownership, schema CREATE, role membership, SUPERUSER, or BYPASSRLS.

## Future objects

Default privileges are creator-specific in PostgreSQL. Apply the following as the role that owns/creates future application objects (often the migration owner in Supabase), after confirming the actual owner:

```sql
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mon_agent_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO mon_agent_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO mon_agent_app;
```

If migrations create objects under a different owner, repeat the default-privilege statements for that exact owner. Do not assume `postgres` owns every object.

## Verify before switching the API

Run while connected as `mon_agent_app`:

```sql
SELECT current_user,
       r.rolsuper,
       r.rolbypassrls,
       r.rolcreatedb,
       r.rolcreaterole
FROM pg_roles AS r
WHERE r.rolname = current_user;

SELECT
  count(*) FILTER (WHERE c.relrowsecurity) AS rls_enabled_tables,
  count(*) FILTER (WHERE c.relforcerowsecurity) AS rls_forced_tables,
  count(*) AS public_tables
FROM pg_class AS c
JOIN pg_namespace AS n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p');
```

Expected:
- `current_user = mon_agent_app`
- `rolsuper = false`
- `rolbypassrls = false`
- the tenant/payment tables introduced by migrations 0016 and 0017 have both RLS enabled and FORCE RLS enabled.

Also verify that the role is not a member of a privileged role and does not own application tables:

```sql
SELECT parent.rolname AS granted_role
FROM pg_auth_members AS m
JOIN pg_roles AS parent ON parent.oid = m.roleid
JOIN pg_roles AS member ON member.oid = m.member
WHERE member.rolname = current_user;

SELECT c.relname
FROM pg_class AS c
JOIN pg_namespace AS n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)
  AND c.relkind IN ('r', 'p');
```

The membership query should return no unexpected privileged roles. The ownership query should return no tenant/application tables.

## Controlled cutover

1. Keep the existing admin/migration secret unchanged.
2. Store the new runtime URL/password in the API host's secret manager as the runtime database setting (for this project, `DATABASE_URL`).
3. Use the Supabase pooler connection mode compatible with the deployment; do not put credentials in the repository.
4. Restart only a staging/preview API first.
5. Check the API health response: database connected, `database_role_rls_safe = true`, and `storage_mode` is not `unsafe_postgresql_role`.
6. Run authenticated tenant-isolation, billing, RNIC and seller-pack tests with two distinct test agencies.
7. Only then schedule the production secret change with a rollback URL stored securely.
8. Keep Alembic migrations on the admin/migration connection; the runtime role must not receive DDL rights.

## Rollback

If staging checks fail, restore the previous runtime secret in the hosting secret manager and restart the API. Do not drop the new role until logs and connection pools show it is unused. Never disable FORCE RLS or the production role guard as a rollback shortcut.

## Scope and limitations

This runbook does not change Supabase, create secrets, or rotate production credentials. Those actions require access to the user's Supabase and hosting control planes and must be performed deliberately. The CI PostgreSQL instance passing migrations is not proof that the live Supabase role and grants are correct.
