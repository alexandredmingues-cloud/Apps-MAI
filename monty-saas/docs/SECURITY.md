# Sécurité & Durcissement MONTY

## 1. RBAC Plateforme (`PLATFORM_ADMIN_USER_IDS`)
Les endpoints d'administration globale (`/api/v1/monty-ai/admin/engines/*`, `/api/v1/monty-ai/admin/providers/key`, `/api/v1/monty-ai/admin/metrics`) appliquent un contrôle **fail-closed** :
- Le rôle `SUPER_ADMIN` seul ne suffit pas si `PLATFORM_ADMIN_USER_IDS` est vide.
- Seuls les `user_id` explicitement listés dans `PLATFORM_ADMIN_USER_IDS` peuvent activer/modifier les moteurs IA ou enregistrer des clés/URLs fournisseurs.

## 2. Protection Anti-SSRF (`validate_local_llm_url`)
La configuration d'une URL Ollama / LLM locale rejette systématiquement :
- Tout schéma autre que `http`/`https`.
- Les adresses de métadonnées cloud (`169.254.169.254`, `169.254.*`, `fe80:*`).
- Les hôtes locaux/privés (`localhost`, `127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `.local`, `.internal`) sauf si `LOCAL_LLM_ALLOW_PRIVATE_HOSTS=true`.

## 3. Rate Limiting Distribué Redis
`SlidingWindowRateLimiter` utilise un sorted set Redis atomique (`ZREMRANGEBYSCORE` + `ZCARD` + `ZADD`) partagé entre tous les workers uvicorn lorsque `REDIS_URL` est défini, en mode fail-closed en production : si Redis est absent ou injoignable, les requêtes protégées sont temporairement refusées (HTTP 429) au lieu de retomber sur une limite locale contournable entre instances.

## 4. Isolation PostgreSQL et publicité
- La migration `0014_rls_hardening` force le RLS sur les adhésions et invitations d'agence et active un RLS dédié aux campagnes publicitaires.
- La lecture publique ne retourne que les campagnes approuvées et actives ; les soumissions restent en attente de modération.
- Les routes publiques de suivi ne peuvent modifier que les compteurs d'impressions/clics, au maximum un incrément par requête ; les modifications éditoriales exigent le contexte serveur `SUPER_ADMIN`.
- Exécuter les migrations Alembic jusqu'à `0014_rls_hardening` avant d'exposer ces routes.

## 5. Clé API Firebase (`app/google-services.json`)
`app/google-services.json` est ignoré dans `.gitignore`. La clé Firebase API (`AIzaSy...`) étant publique dans l'APK, elle doit être restreinte dans Google Cloud Console au package `com.aistudio.montyimmo.kxmpzq`, au SHA-1 de signature et aux API Firebase autorisées.

## 5. Paiement Serveur & Claims Firebase
- Pack Vendeur : règlement validé par Stripe Webhook (`POST /api/v1/billing/webhook`) + vérification serveur (`GET /api/v1/billing/seller-pack/verify`), bloqué en écriture client `PAID`/`DELIVERED` dans `firestore.rules`.
- Annonces Agence : `publisherType == "AGENCE"` protégé par claims serveur `advisor: true` / `staff: true`.
