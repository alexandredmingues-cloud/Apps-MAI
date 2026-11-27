# Déploiement MONTY (Render + PostgreSQL + Redis + Vercel)

## 1. Backend API & PostgreSQL + Redis (`render.yaml`)
Le blueprint racine `/render.yaml` unifie l'infrastructure :
- Service web `monty-saas-api` sur **`plan: starter`** (sans mise en veille) avec disque persistant (`/var/data/monty`, 5 Go).
- Base PostgreSQL `monty-postgres` (PostgreSQL 16 + migrations `0001` à `0012`).
- Cache & file distribuée `monty-redis` (rate limiting distribué + jobs ARQ).
- Variables obligatoires à renseigner dans Render Secrets :
  - `JWT_SECRET` (généré automatiquement ou >= 32 caractères)
  - `PLATFORM_ADMIN_USER_IDS` (UID des administrateurs plateforme autorisés)
  - `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
  - `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` (optionnels selon moteurs activés)
  - `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` (ou `S3_*`) pour les APK et pièces documentaires

> Note : l'ancien fichier `monty-saas/backend/railway.toml` a été supprimé afin d'éviter toute dérive de configuration entre hébergeurs.

## 2. Frontend Next.js 15.5+
- Patché contre CVE-2025-66478 (`next@^15.5.0`).
- Variable requise : `NEXT_PUBLIC_API_BASE_URL=https://monty-saas-api.onrender.com`.

## 3. Application Android
- APK compilé via `gradle :app:assembleRelease` et publié via `/api/v1/admin/mobile/releases/upload` (contrôle ZIP + signature SHA-256).
