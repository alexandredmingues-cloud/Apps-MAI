#!/usr/bin/env bash
set -euo pipefail

# Sauvegarde quotidienne PostgreSQL Monty SaaS avec rétention 14 jours
: "${DATABASE_URL:?DATABASE_URL doit être défini}"

BACKUP_DIR="${BACKUP_DIR:-./backups/postgres}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date -u +%Y%m%d_%H%M%SZ)"
OUT_FILE="${BACKUP_DIR}/monty_saas_${TIMESTAMP}.dump"

mkdir -p "${BACKUP_DIR}"
pg_dump "${DATABASE_URL}" --format=custom --compress=9 --no-owner --no-acl --file="${OUT_FILE}"
sha256sum "${OUT_FILE}" > "${OUT_FILE}.sha256"

# Rotation automatique des sauvegardes anciennes
find "${BACKUP_DIR}" -name "monty_saas_*.dump*" -type f -mtime +"${RETENTION_DAYS}" -delete

echo "Sauvegarde terminée : ${OUT_FILE}"
