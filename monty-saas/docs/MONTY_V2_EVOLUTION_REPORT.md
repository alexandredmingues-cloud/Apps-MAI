# MONTY V2 — RAPPORT DE PRESERVATION, EVOLUTIONS ET EXPLOITATION (PHASES 27, 28, 29 & V2)

## 1. Principe directeur : Preservation et continuite de l'existant
Toutes les fonctionnalites preexistantes de Monty ont ete strictement conservees et enrichies sans suppression ni reecriture destructive :
- **Backend FastAPI (`monty-saas/backend/app/`)** : Authentification JWT + MFA TOTP, RBAC multi-tenant + RLS PostgreSQL 16 (`0001` a `0009`), CRM contacts/deals, Portefeuille biens/visites/transactions, Matching mobilite 6 modes, Coproprietes RNIC, Notaires & avant-contrats, Facturation Stripe, Veille actualites, 11 Agents & Monty Scout, Sante API/Postgres/Redis (`/health`).
- **Frontend Next.js 15 (`monty-saas/frontend/`)** : `PublicWebHeader`, `MontyShell`, page d'accueil `/`, Marketplace & publication, Portefeuille & pipeline, Simulateur HCSF, Estimation DVF, Coproprietes RNIC, CRM & Portail acquereur/vendeur, Console Admin SaaS & `HealthCheckDashboard`.
- **Application Android (`app/`)** : Application Kotlin / Jetpack Compose compilee et verifiee (`com.aistudio.montyimmo.kxmpzq`).

---

## 2. Nouvelles capacites et migrations ajoutees

### A. Cycle V2 — Geolocation BAN/PT, Monty Leads, Feature Flags & PWA
- **Geolocation unifiee (`app/domain/geocoding_service.py`, `marketplace_router.py`, `InteractiveGeoMap.tsx`)** :
  - Autocompletion BAN officielle France + repertoire Portugal (`GET /api/v1/geo/autocomplete`).
  - Detection d'homonymes (`homonym_warning: bool`) et distinction stricte des 5 entites (`postal_address`, `gps`, `building_ref`, `cadastral_parcel`, `copro_lot`).
  - Geocodage inverse (`GET /api/v1/geo/reverse`) et carte interactive synchronisee avec les annonces.
- **Monty Leads (`portfolio_and_leads_service.py`, `crm_router.py`, `CrmCockpitPanel.tsx`)** :
  - Regles de mots-cles par categorie (`VENDEURS`, `ACQUEREURS`, `INVESTISSEURS`, `BAILLEURS`) + portee geographique.
  - Scoring objectif `0-100` (`compute_lead_qualification`), stade d'intention et raisons detaillees.
- **Gouvernance modulaire centralisee (`interconnections_service.py`, `interconnect_router.py`, `MontyShell.tsx`, `AdminV2GovernancePanels.tsx`)** :
  - Statuts `EN_DEVELOPPEMENT`, `EN_TEST`, `DISPONIBLE`, bascule de visibilite avec verrou sur les modules critiques (`dashboard`, `admin`), restauration par defaut et journal d'audit.
- **PWA (`public/manifest.webmanifest`, `public/icon-monty.svg`, `app/layout.tsx`)** :
  - Manifest web instalable et icone vectorielle Monty.

### B. Phase 27 — Monty AI Core (Multi-moteurs invisibles)
- **Orchestration backend (`app/domain/monty_ai_core.py`, `app/api/v1/monty_ai_router.py`)** :
  - `AIModelRegistry` : moteurs locaux open-source prioritaires (Ollama `qwen2.5:7b-instruct`, `mistral-nemo:12b`, vLLM `llama-3.3-70b-instruct`, llama.cpp `deepseek-r1`) + fournisseurs cloud serveur (Anthropic, OpenAI, Gemini, Mistral Cloud, DeepSeek Cloud).
  - `AITaskRouter` & `AIFallbackManager` : routage par type de tache, bascule automatique sur erreur HTTP/quota, et repli `TOOL_ASSISTED_OFFLINE` alimente par les moteurs Monty (HCSF, secteurs, Price Index).
  - `AISecurityManager` : filtrage anti-injection / anti-exfiltration de secrets et redaction PII avant envoi.
  - `AIMemoryManager` : memoire isolee `USER` / `AGENCY` avec edition et suppression RGPD.
  - `AIPendingAction` : file de validation humaine obligatoire avant toute action sensible (envoi message client, publication, transmission notaire, mandat, suppression).
  - Administration (`/api/v1/monty-ai/admin/*`) : cles serveur masquees, moteur par defaut/secours, metriques de latence et taux de repli.
- **Frontend (`MontyAIChatWorkspace.tsx`, integre dans `AgentsAndScoutPanel.tsx`)** :
  - Interface conversationnelle unique sans exposer les moteurs sous-jacents a l'utilisateur final, mode confidentiel, import de pieces texte, file de validation en 1 clic et gestion memoire RGPD.

### C. Phase 28 — Onglet Telechargement de l'application Android (APK)
- **Distribution backend (`app/domain/mobile_releases_service.py`, `app/api/v1/mobile_releases_router.py`)** :
  - APK Android reel compile depuis `app/` et deploye dans `monty-saas/backend/storage/apk/monty-immo-v1.0.apk` (copie egalement incluse dans `monty-saas/backend/Dockerfile`).
  - Verification cryptographique ZIP + `AndroidManifest.xml` + `.dex` + signature v1/v2/v3 (`validate_apk_bytes`), empreinte SHA-256 et compteur de telechargements.
  - Endpoints publics (`GET /api/v1/mobile/latest`, `GET /api/v1/mobile/releases`, `GET /api/v1/mobile/download/{release_id}`) et endpoints Admin (upload base64 valide, publication, recommandation).
- **Frontend (`/telecharger`, `ApkDownloadExperience.tsx`)** :
  - Acces depuis `PublicWebHeader`, `MontyShell`, page d'accueil et console Admin.

### D. Phase 29 — Monty Price Index
- **Moteur backend (`app/domain/monty_price_index_service.py`, `app/api/v1/price_index_router.py`)** :
  - 5 categories configurables (`TRES_BONNE_AFFAIRE`, `BONNE_AFFAIRE`, `PRIX_COHERENT`, `AU_DESSUS_DU_MARCHE`, `TROP_CHER`) + `DONNEES_INSUFFISANTES` (masquage automatique du badge sous le seuil minimal de comparables).
  - Filtrage statistique IQR des valeurs aberrantes, ponderation distance/recence, multiplicateurs qualitatifs (DPE, etat, exterieur, stationnement, travaux), 3 niveaux de fiabilite (`HIGH`, `MEDIUM`, `LOW`) et explication chiffree ancree.
- **Frontend (`MontyPriceGauge.tsx`)** :
  - Jauge interactive 5 zones coloree, integee aux cartes Marketplace, au filtre/tri des annonces, au Portefeuille Pro et a la console Admin (seuils + historique).

### E. Migrations PostgreSQL 16 (RLS)
- `monty-saas/backend/alembic/versions/0010_monty_ai_and_mobile_releases.{sql,py}` : tables `monty_ai_engine_configs`, `monty_ai_memories`, `monty_ai_conversations`, `monty_ai_pending_actions`, `monty_ai_audit_events`, `mobile_app_releases`, `lead_keyword_rules`, `agency_module_flags` + politiques RLS.
- `monty-saas/backend/alembic/versions/0011_monty_price_index.{sql,py}` : tables `monty_price_index_configs`, `monty_price_index_history` + politiques RLS.

---

## 3. Exploitation en production (Render / Docker)
1. **Migrations base de donnees** : executer `alembic upgrade head` apres mise a jour du service backend sur PostgreSQL 16.
2. **Variables d'environnement optionnelles pour Monty AI Core** :
   - Moteur local open-source : `LOCAL_LLM_BASE_URL` (ex. URL serveur Ollama/vLLM).
   - Fournisseurs cloud (cote serveur uniquement) : `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY`, `MISTRAL_API_KEY`, `DEEPSEEK_API_KEY`, `AI_DAILY_BUDGET_EURO`, `MONTY_AI_CONFIDENTIAL_DEFAULT`.
3. **Stockage APK** : le fichier initial `monty-immo-v1.0.apk` est embarque dans l'image Docker backend (`COPY storage ./storage`). Pour persister les futurs APK telecharges via l'Admin sur Render, monter un disque persistant sur `/app/storage/apk` ou `MONTY_APK_STORAGE_DIR`.
