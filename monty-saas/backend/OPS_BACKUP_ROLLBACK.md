# Plan de Sauvegarde Quotidienne & Retour Arrière (Rollback) — Monty SaaS

## 1. Sauvegarde quotidienne PostgreSQL
- **Railways / Render Managed Postgres** : activer les sauvegardes automatiques quotidiennes (PITR + snapshots quotidiens sur 7 à 30 jours).
- **Script autonome (`scripts/backup_postgres.sh`)** : exécute `pg_dump --format=custom --compress=9` + empreinte `sha256sum` + purge > 14 jours.
- Planification cron recommandée (02h30 UTC) :
  ```cron
  30 2 * * * /app/scripts/backup_postgres.sh >> /var/log/monty-backup.log 2>&1
  ```

## 2. Procédure de Retour Arrière Applicatif & Base de Données
1. **Activer le Kill-Switch IA** (`POST /api/v1/agents/kill-switch` avec `enabled: true`) si un incident concerne les agents.
2. **Rollback applicatif** : redéployer le tag/commit précédent dans Railway ou Render (`Deployments -> Rollback`).
3. **Rollback schéma Alembic (si une migration a échoué)** :
   ```bash
   alembic downgrade -1
   ```
4. **Restauration complète depuis snapshot (`pg_restore`)** :
   ```bash
   sha256sum -c monty_saas_YYYYMMDD_HHMMSSZ.dump.sha256
   pg_restore --clean --if-exists --no-owner --dbname="$DATABASE_URL" monty_saas_YYYYMMDD_HHMMSSZ.dump
   ```
5. **Vérification post-retour arrière** : contrôler `GET /api/v1/health` (`status: "ok"` et `database_reachable: true`).
