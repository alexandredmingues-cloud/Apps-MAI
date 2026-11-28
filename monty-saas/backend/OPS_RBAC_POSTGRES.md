# RBAC PostgreSQL — MON-AGENT

## Deux couches (ne pas les confondre)

| Couche | Où | Rôle |
|---|---|---|
| **Métier** | `app/core/rbac.py` (`MontyRole`, `Permission`) | Qui peut appeler quel endpoint (`require_permission`) |
| **Isolation données** | GUCs `app.current_*` + policies RLS | Quelle agence / quel utilisateur voit quelles lignes |
| **Connexion DB** | Rôle Postgres `monty_app` | Compte technique du pool FastAPI |

Les rôles métier (`DIRECTOR`, `AGENT`, …) **ne sont pas** des rôles Postgres. Ils sont poussés en session via `set_config` dans `tenant_db_session` (`app/db/session.py`).

## Contrat du rôle de connexion

En production, `DATABASE_URL` doit utiliser un rôle :

- `NOSUPERUSER`
- `NOBYPASSRLS` (sinon RLS inactive pour ce rôle)
- sans `CREATEDB` / `CREATEROLE`

Le backend refuse la connexion en production si `rolsuper` ou `rolbypassrls` est vrai (`deps.py`).

## Provisionnement

```bash
psql "$ADMIN_DATABASE_URL" -f monty-saas/backend/scripts/create_monty_app_role.sql
psql "$ADMIN_DATABASE_URL" -f monty-saas/backend/scripts/verify_monty_app_role.sql
# GRANT CONNECT ON DATABASE <nom_base> TO monty_app;
# Définir le mot de passe hors git, mettre à jour DATABASE_URL.
```

## Tests automatisés

- `tests/test_production_acceptance_rbac.py` — matrice permissions Python
- `tests/test_rls_tenant_isolation.py` — isolation réelle sous rôle `NOBYPASSRLS`
