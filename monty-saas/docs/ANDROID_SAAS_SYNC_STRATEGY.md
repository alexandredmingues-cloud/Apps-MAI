# Stratégie de Synchronisation & Convergence — Android (Kotlin/Compose) ↔ Monty SaaS (FastAPI + PostgreSQL 16 RLS)

## 1. État actuel des deux plateformes

Monty repose aujourd'hui sur deux clients riches complémentaires :

1. **Application mobile Android (`app/`)** :
   - Authentification Google Sign-In via Jetpack `CredentialManager` et Firebase Auth.
   - Persistance cloud temps réel via **Cloud Firestore** (`SaasAgencyRepository`, `MontyRepository`, `SellerRepository`) sécurisée par `firestore.rules` (vérification d'appartenance `memberUids` / `viewerUids`, RBAC agence, notes privées séparées) et **Firebase App Check**.
   - Persistance locale hors ligne via **Room** (`MontyLocalDatabase`, `AcquereurRepository`).
   - Moteurs métier déterministes embarqués (`DvfValuationService`, `SaleTimelineEngine`, `GeoCommuteService`, `ExcelCsvCodec`, `UrbanismCadastreService`, `HcsfCapacityScreen`).

2. **Plateforme Web SaaS (`monty-saas/`)** :
   - **Backend FastAPI (`monty-saas/backend/`)** connecté à **PostgreSQL 16 avec Row-Level Security (RLS)** (`0001` à `0009`), files asynchrones **ARQ / Redis**, connecteur **Stripe** (`INDIVIDUAL`, `AGENCY`, `NETWORK` + Pass RNIC Particulier 19 €), annuaire des **Notaires de France**, **Base Nationale des Copropriétés (RNIC)**, **11 Agents IA avec gouvernance** et **Pré-État Daté ALUR L. 721-2**.
   - **Frontend Next.js 15 / React 19 (`monty-saas/frontend/`)** consommé en SaaS B2B (`Monty Pro`) et portail B2C (`Monty Particulier`).

Aujourd'hui, les deux bases de données (Firestore côté Android, PostgreSQL 16 RLS côté SaaS Web) sont **indépendantes** : un bien ou un contact créé sur Android n'est pas encore répliqué automatiquement dans PostgreSQL 16 RLS et inversement.

---

## 2. Architecture cible de convergence (3 phases progressives)

### Phase A — Passerelle d'identité & JWT d'échange (Zéro rupture mobile)
- Conserver **Google Sign-In (`CredentialManager`) + Firebase Auth** côté Android pour l'ergonomie mobile et la protection App Check.
- Ajouter un endpoint FastAPI `POST /api/v1/auth/firebase-exchange` vérifiant le `Firebase ID Token` (via clé publique Google OIDC) et délivrant un JWT Monty court-terme (`sub`, `aid`, `role`) compatible avec l'ensemble des endpoints FastAPI et PostgreSQL RLS.
- Mapper les identifiants `agencyId` et `userId` via une table de liaison PostgreSQL (`external_identity_links`) afin de préserver la compatibilité des comptes existants.

### Phase B — Client Ktor / Retrofit unifié sur Android (Source de vérité SaaS)
- Introduire un client HTTP **Ktor** (ou Retrofit + `kotlinx.serialization`) dans `app/src/main/java/com/example/data/remote/MontySaasApiClient.kt` pointant vers `BuildConfig.MONTY_API_BASE_URL`.
- Conserver **Room (`MontyLocalDatabase`)** comme cache hors-ligne et file d'attente de synchronisation (`SyncState = PENDING | SYNCED | CONFLICT`) via **WorkManager** pour les visites terrain sans réseau 4G/5G.
- Basculer progressivement les flux métier critiques vers l'API FastAPI PostgreSQL RLS :
  1. **CRM & Notes privées cloisonnées** (`/api/v1/crm/*`) — garantit l'unicité RGPD (durées 36/60 mois, portabilité Art. 15/20, purge globale et audit immuable).
  2. **Portefeuille, Visites & Pipeline Transactions** (`/api/v1/portfolio/*`) — déclenche automatiquement les notifications agences, le matching acquéreurs et les tâches notaire au passage en `COMPROMIS`.
  3. **Marketplace, Notaires & RNIC Copropriétés** (`/api/v1/marketplace/*`, `/api/v1/notaries/*`, `/api/v1/rnic/*`) — partage le même moteur de modération `A_VERIFIER -> PUBLIEE` et le contrôle serveur du Pass RNIC Stripe.

### Phase C — Décommissionnement graduel de la double écriture Firestore
- Activer un job ARQ (`monty-saas/backend/app/workers/redis_tasks.py`) pour l'import/export initial des agences pilotes depuis Firestore vers PostgreSQL 16 RLS (avec journal d'audit `MIGRATION_IMPORT`).
- Conserver uniquement **Firebase App Check** + **FCM (Notifications Push Android)** pour alerter les conseillers en temps réel lors d'un `BUYER_MATCH_FOUND`, d'un `VISIT_REQUEST` ou d'une échéance `TRANSACTION_COMPROMIS`.

---

## 3. Règles invariantes pendant la transition

- **Aucune divergence des calculs déterministes** : DVF (IQR + score Monty), HCSF 35 %, Pré-État Daté L. 721-2, matching acquéreurs/biens et frais de notaire doivent produire exactement les mêmes résultats sur Android et sur le SaaS Web (tests de parité croisée).
- **Zéro secret embarqué dans l'APK** : seules les URL publiques HTTPS et l'authentification par jeton utilisateur échangé côté serveur sont autorisées dans `BuildConfig`.
- **Conformité RGPD & Isolation Tenant** : toute donnée synchronisée reste strictement cloisonnée par `agency_id` (RLS PostgreSQL + règles Firestore pendant la phase transitoire).
