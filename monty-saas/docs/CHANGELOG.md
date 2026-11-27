# Changelog MONTY V3

## Convergence & Durcissement V3
- **Persistence PostgreSQL 16 RLS (`0010`–`0012`)** : migrations et repository unifié pour Monty AI Core, Releases APK, Monty Price Index 6 paliers, mots-clés CRM, drapeaux de modules et commandes Pack Vendeur Stripe.
- **Sécurité & Infrastructure** :
  - `SlidingWindowRateLimiter` distribué sur Redis (`REDIS_URL`).
  - `PLATFORM_ADMIN_USER_IDS` fail-closed + protection anti-SSRF `validate_local_llm_url`.
  - Suppression de l'écriture client `PAID`/`DELIVERED` du Pack Vendeur (`firestore.rules`, `SellerRepository.kt`, `BuyerDocumentVaultDialogs.kt`) au profit du checkout & webhook Stripe serveur.
  - Exigence de claim `advisor` / `staff` pour publier une annonce `AGENCE` (`firestore.rules` + `firestore.test.js`).
  - Blueprint Render standardisé sur `plan: starter` (suppression de `railway.toml` et ajout de `app/google-services.json` à `.gitignore`).
- **Monty AI, Price Index & ADEME** :
  - Modèles Anthropic/Gemini mis à jour et configurables (`claude-sonnet-4-6`, `claude-haiku-4-5`, `gemini-2.5-flash`) + extraction de `MontyAiModelConfig.AI_MODEL_NAME` côté Android.
  - Cache sémantique 900 s et métriques comparatives `provider_comparison`.
  - Connecteur officiel ADEME Open Data DPE (`/api/v1/price-index/ademe-dpe`) et jauge 6 paliers (`EXCELLENTE_AFFAIRE` + mention légale non contractuelle).
- **Web & Convergence Mobile** :
  - Mise à jour Next.js `^15.5.0` (CVE-2025-66478).
  - Support i18n complet `fr-FR`, `pt-PT`, `en-GB`.
  - Passerelle `POST /api/v1/auth/firebase-exchange` et `MontySaasApiClient.kt`.


## Durcissement production — 27 novembre 2026
- Migration PostgreSQL 0014 : RLS forcé sur les adhésions/invitations et isolation des campagnes publicitaires.
- Compteurs publicitaires limités aux impressions/clics ; modération réservée au rôle SUPER_ADMIN.
- Rate limiting distribué fail-closed en production si Redis est indisponible.
- CI étendue à un PostgreSQL 16 vierge pour exécuter toutes les migrations Alembic avant les tests.
