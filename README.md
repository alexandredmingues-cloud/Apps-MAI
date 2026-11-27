> **Migration Flutter en cours — 27 novembre 2026 :** le client Flutter Android/iOS/Web est actuellement un socle initial, pas encore un remplacement fonctionnel du client Android historique. Consultez [l'état et les critères de migration](monty-saas/mobile/README.md) et [l'architecture cible](monty-saas/docs/ARCHITECTURE.md). Kotlin, Room et Firebase sont conservés jusqu'à validation documentée de la parité et des données.

<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# MONTY IMMOBILIER — Écosystème Tri-Plateforme (Android + Web Next.js + SaaS FastAPI)

Plateforme immobilière complète multi-pays (France active • Portugal isolé & prêt) composée de :
- **Application Android (`app/`)** : Jetpack Compose, Room hors-ligne, Cloud Firestore + App Check, Google Sign-In (`CredentialManager`), Firebase AI Logic (`MontyAiModelConfig.AI_MODEL_NAME`) et convergence SaaS (`MontySaasApiClient`).
- **Backend SaaS (`monty-saas/backend/`)** : FastAPI, PostgreSQL 16 + RLS (`0001` à `0012`), Redis (`SlidingWindowRateLimiter` + ARQ), `Monty AI Core` multi-moteurs, ADEME Open Data DPE, Monty Price Index 6 paliers, Stripe Checkout & Webhooks (abonnements, Pass RNIC, Pack Vendeur), et Stockage Objet S3/R2/Supabase.
- **Frontend Web (`monty-saas/frontend/`)** : Next.js 15.5+ / React 19, i18n (`fr-FR`, `pt-PT`, `en-GB`), espace pro SaaS + portail public + téléchargement APK vérifié SHA-256.

Voir l'app dans AI Studio : https://ai.studio/apps/cd75241a-b5fc-44ff-a2a6-4ccc33fec8d3

---

## Démarrage rapide Android

**Prérequis :** [Android Studio](https://developer.android.com/studio)

1. Ouvrez le dossier avec **Open** dans Android Studio.
2. Placez votre fichier `app/google-services.json` localement (ce fichier est désormais ignoré par `.gitignore` pour ne pas être poussé sur GitHub).
   - **Restriction de la clé API Firebase (`AIzaSy...`)** : une clé Firebase dans `google-services.json` est publique par nature dans un APK. Restreignez-la impérativement dans **Google Cloud Console > APIs & Services > Credentials** à votre nom de package Android (`com.aistudio.montyimmo.kxmpzq`), votre empreinte **SHA-1** de signature et aux seules API Firebase/AI Logic requises.
3. **Aucune clé Gemini dans le dépôt** : les appels mobiles passent par Firebase AI Logic + App Check ou via le backend SaaS (`POST /api/v1/auth/firebase-exchange`).
4. En local uniquement (si `debug.keystore` n'est pas généré), ajustez temporairement `signingConfig` dans `app/build.gradle.kts` sans commiter ce changement.
5. Si l'app est déjà publiée sur Play Console via AI Studio, consultez la procédure de [réinitialisation de clé d'upload](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset).

---

## Sécurité Firebase, Règles Firestore & Paiements

```bash
npm install
npm test        # démarre l'émulateur Firestore (port 8085) et lance firestore.test.js
```

Déploiement : `firebase deploy --only firestore:rules`.
> **Portabilité `firebase.json`** : remplacez l'identifiant `"database"` dans `firebase.json` si vous déployez vers un autre projet Firebase / base `(default)`.

### Claims Custom (`staff` et `advisor`)

- `staff: true` : modération des annonces particuliers (`A_VERIFIER` → `PUBLIEE`) et écriture serveur/admin sur `buyerOrders`.
- `advisor: true` : requis pour créer ou maintenir des annonces `publisherType: "AGENCE"` (un particulier sans claim ne peut plus usurper le statut agence).

```js
admin.auth().setCustomUserClaims(uid, { advisor: true });
admin.auth().setCustomUserClaims(staffUid, { staff: true, advisor: true });
```

### Paiement serveur du Pack Vendeur

Les règles Firestore (`seller_dossiers`) bloquent désormais toute écriture client directe de `buyerOrders[].status = "PAID"` ou `"DELIVERED"`. Les commandes client sont enregistrées en `PENDING_PAYMENT` et la validation réelle passe exclusivement par **Stripe Checkout + Webhook** (`POST /api/v1/billing/seller-pack/checkout` + `POST /api/v1/billing/webhook`).

---

## Documentation détaillée (`monty-saas/docs/`)

- `monty-saas/docs/ARCHITECTURE.md` — Architecture tri-plateforme & flux
- `monty-saas/docs/DATABASE.md` — Migrations PostgreSQL 16 + RLS (`0001`–`0012`) & Firestore
- `monty-saas/docs/AI_ORCHESTRATOR.md` — Monty AI Core multi-fournisseurs, cache sémantique & garde-fous
- `monty-saas/docs/SECURITY.md` — RBAC `PLATFORM_ADMIN_USER_IDS`, anti-SSRF, Redis rate limiting, Stripe & Firebase
- `monty-saas/docs/DEPLOYMENT.md` — Déploiement unifié Render (`render.yaml`, `plan: starter`, disque, Redis) & Vercel
- `monty-saas/docs/ANDROID_SAAS_SYNC_STRATEGY.md` — Stratégie de convergence Android ↔ SaaS
- `monty-saas/docs/CHANGELOG.md` — Journal des évolutions V3
