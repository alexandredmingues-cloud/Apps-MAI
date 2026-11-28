-- MON-AGENT — rôle applicatif PostgreSQL pour le runtime FastAPI
-- Exécuter une fois par environnement avec un rôle privilégié (DBA / owner).
-- Ne définit PAS de mot de passe : secret hors dépôt uniquement.
--
-- Couche 1 (Python) : MontyRole + Permission dans app/core/rbac.py
-- Couche 2 (Postgres) : GUCs app.current_* + policies RLS (migrations 0001+)
-- Ce script gère le RÔLE DE CONNEXION runtime, pas les rôles métier.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'monty_app') THEN
    CREATE ROLE monty_app WITH
      LOGIN
      NOSUPERUSER
      NOCREATEDB
      NOCREATEROLE
      NOINHERIT
      NOREPLICATION
      NOBYPASSRLS;
  ELSE
    ALTER ROLE monty_app WITH
      NOSUPERUSER
      NOCREATEDB
      NOCREATEROLE
      NOBYPASSRLS;
  END IF;
END
$$;

-- CONNECT : adapter le nom de base si besoin.
-- Exemple : GRANT CONNECT ON DATABASE monty TO monty_app;

GRANT USAGE ON SCHEMA public TO monty_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO monty_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO monty_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO monty_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO monty_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO monty_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO monty_app;

COMMENT ON ROLE monty_app IS
  'Runtime MON-AGENT: NOSUPERUSER NOBYPASSRLS. Tenant via set_config(app.current_*).';
