# PHASE 0 — AUDIT TECHNIQUE & PLAN DE MIGRATION MONTY SAAS

> **Source analysée** : Dépôt local du projet **Mon Agent Immo / Monty** (`app/`, `firestore.rules`, `firebase-blueprint.json`, `firestore.test.js`, `README.md`, `package.json`, `.env.example`).  
> *Note environnementale : le workspace courant contient directement l'arborescence extraite du projet Android Kotlin/Compose plutôt qu'un fichier `.zip` non décompressé.*

---

## 1. Inventaire Technique de l'Existant

| Couche | Fichiers / Composants clés | Rôle actuel |
|---|---|---|
| **Mobile / UI** | `MainActivity.kt`, `ui/auth/AuthScreen.kt`, `ui/advisor/*` (8 fichiers), `ui/buyer/*` (4 fichiers), `ui/seller/*` (4 fichiers), `ui/saas/*` (4 fichiers), `ui/finance/*`, `ui/components/*`, `ui/theme/*` | Application Android Kotlin + Jetpack Compose Material 3 (3 espaces : Conseiller, Vendeur, Portail Client) |
| **State Management** | `ui/MontyViewModel.kt`, `ui/saas/SaasViewModel.kt`, `ui/seller/SellerViewModel.kt`, `ui/advisor/AcquereursViewModel.kt` | ViewModels `StateFlow` pilotant la synchronisation Firestore & Room |
| **Modèles Métier** | `data/model/MontyModels.kt` (504 l.), `SaasModels.kt` (415 l.), `SellerModels.kt` (590 l.), `data/local/AcquereurEntity.kt` | Dossiers acquéreurs, biens, visites, offres, chats, agences, membres RBAC, contacts CRM, relances, audit logs, dossiers vendeurs/ALUR, DVF |
| **Moteurs Métier** | `SeedData.kt` (`MontyMarketData`), `DvfValuationService.kt` (709 l.), `SaleTimelineEngine.kt` (298 l.), `GeoCommuteService.kt` (282 l.), `ExcelCsvCodec.kt` (645 l.), `RealEstateMarketBriefing.kt` (256 l.) | Calculs financiers HCSF 35%, matching déterministe, estimation DVF 50/100/300m, délai de vente, temps de trajet multimodal, import/export CSV & XLSX, baromètre immo |
| **Connecteurs Open Data** | `UrbanismCadastreService.kt` (485 l.), `DvfValuationService.kt`, `RealEstateMarketBriefing.kt` | BAN (`api-adresse.data.gouv.fr`), DVF Etalab/cquest & Cerema, IGN Carto Cadastre & GPU (PLU/SUP/Prescriptions), Géorisques GASPAR, taux dépôt BCE |
| **IA Générative** | `data/MontyAiService.kt` (574 l.) | Copilote conseiller (streaming), Assistant acquéreur, Scoring IA JSON (`relevanceSchema`), Rédaction annonce vendeur (multimodale photo) via Firebase AI Logic (`gemini-3.8-flash`) |
| **Persistance & Stockage** | `MontyRepository.kt`, `SaasAgencyRepository.kt`, `SellerRepository.kt`, `SellerDocumentVault.kt`, `local/MontyLocalDatabase.kt` (Room) | Cloud Firestore (collections `users/{uid}/*`, `agencies/{agencyId}/*`, `agency_invites`), Room SQLite local, coffre-fort fichiers local `filesDir/seller_vault` |
| **Sécurité & RGPD** | `security/AppSecurityManager.kt` (277 l.), `security/RgpdComplianceManager.kt` (221 l.), `firestore.rules` (965 l.), `firestore.test.js` | Rate-limiter par canal, `InputSanitizer` (XSS/URL/prompt-injection), masquage PII pour IA (Art. 5.1.c), durée conservation 36/60 mois, export JSON (Art. 15/20), suite émulateur 10 tests |

---

## 2. Cartographie Fonctionnelle & Règles Métier à Préserver Intégralement

1. **CRM Immobilier Multi-Agence (`SaasModels.kt`, `SaasAgencyRepository.kt`, `CrmContactsAndTasksTab.kt`)**
   - Contacts typés (`Acquéreur`, `Vendeur`, `Propriétaire`, `Locataire`, `Investisseur`, `Partenaire`, `Autre`, `Mixte`), statuts, priorités, sources, journal d'échanges (`ContactExchangeEntry`), affectation collaborateur, archivage.
   - Tâches et relances (`FollowUpTask`) avec canaux (`Appel`, `Email`, `SMS`, `Visite`), échéance `dueDate` + `dueTime`, détection de retard (`isOverdue`) et KPIs (`computeCrmDashboardMetrics`).
   - Journal d'audit immuable (`AgencyAuditLog`).
2. **Import / Export Excel & CSV (`ExcelCsvCodec.kt`)**
   - Détection automatique de format (`.xlsx` via XML OpenXML / `.csv` `;`, `,`, `\t`), mapping de 20 champs `MontyImportField`, détection de doublons par e-mail ou 9 derniers chiffres téléphone, protection contre l'injection de formules CSV/Excel (`=`, `+`, `-`, `@`, `\t`, `\r`), export complet incluant colonnes RGPD.
3. **Matching Déterministe Acquéreur / Bien (`MontyMarketData.computeCompatibility` + `GeoCommuteService`)**
   - Score 12..99 calculé de façon déterministe :
     - Écart budget max vs `prix + travaux estimés` (+22, +15, +3, -25).
     - Géolocalisation par liste de quartiers (+15 / -8) **OU** périmètre spatial autour d'une adresse BAN (`RADIUS_KM` ou `TRAVEL_TIME`) en 6 modes (`VOITURE` 32 km/h, `TRAM` 18 km/h, `BUS` 15 km/h, `TRAIN` 50 km/h, `VELO` 16 km/h, `PIED` 4.8 km/h avec facteur de détour et temps d'accès).
     - Surface habitable (+8 / -10), chambres (+7 / -12), typologie (+8), critères indispensables (`terrasse`, `parking`, `tram`, `calme`, `ascenseur`, `dernier`, `dpe`), valorisation DPE A–C.
4. **Estimation Immobilière DVF & Délai de Vente (`DvfValuationService.kt`, `SaleTimelineEngine.kt`)**
   - Double source DVF (Etalab `api.cquest.org/dvf` + repli Cerema `apidf-preprod.cerema.fr`), pondération 3x ≤ 50 m / 2x ≤ 100 m / 1x ≤ 300 m et fraîcheur des actes (1.25x ≤ 1 an, 1.0x 2 ans, 0.75x > 2 ans).
   - Ajustements objectifs par critères : DPE (`+5%` à `-14%`), état général (`+6%` à `-15%`), étage & ascenseur (`-10%` à `+8%`), vue/exposition (`-7%` à `+7%`), extérieur (`-4%` à `+8%`), parking (`-3%` à `+7%`), calme/bruit (`-6%` à `+3%`), annexes/clim/vitrage/vis-à-vis/gros travaux, bornés à `[-45%, +55%]`.
   - Score de confiance transparent sur 100 pts (proximité 50/100/300m, coefficient de variation homogénéité, fraîcheur, complétude dossier).
   - **Règle d'honnêteté** : `SaleTimelineEngine` renvoie `INSUFFICIENT_DATA` si `surface <= 0` ou aucune référence DVF/secteur — aucune donnée inventée. Mention obligatoire que l'estimation algorithmique est indicative et ne remplace pas une expertise.
5. **Simulateur Financier & Capacité HCSF 35% (`MontyMarketData.computeFinanceMetrics` & `computeHcsfBorrowingCapacity`)**
   - Frais de notaire, capital emprunté, mensualité hors/avec assurance, coût total crédit, taux d'endettement `<= 35%`, durée max `25 ans` (ou `27 ans` VEFA/Neuf), reste à vivre, comparatif 15/20/25 ans.
6. **Espace Vendeur, Loi ALUR & Marketplace (`SellerModels.kt`, `SellerDocumentVault.kt`, `MarketplaceModule`)**
   - Check-list dynamique selon `PropertyKind` (Maison / Appartement) et `ResidenceType` (Maison individuelle / Copropriété / Lotissement ASL — conservation des pièces ALUR lors d'un basculement).
   - Publication `AGENCE` vs `PARTICULIER` (statuts `BROUILLON`, `A_VERIFIER`, `PUBLIEE`), masquage d'adresse exacte (`hideExactAddress`), attestation propriétaire et consentement légal.
7. **Cartographie & Urbanisme (`UrbanismCadastreService.kt`)**
   - BAN, IGN Cadastre (parcelle/contenance), Géoportail de l'Urbanisme (zonage PLU, prescriptions surf/lin/pct, servitudes SUP, documents PDF), Géorisques GASPAR.
8. **Conformité RGPD (`RgpdComplianceManager.kt`)**
   - Masquage systématique des emails, téléphones et IBAN avant envoi aux LLM (`redactDirectPiiForAi`).
   - Durées de conservation différenciées : **36 mois** (consentement / prospection / précontractuel) vs **60 mois** (mandat / obligation légale ALUR / TRACFIN).
   - Export de portabilité JSON (Art. 15 & 20) et droit à l'effacement (Art. 17).

---

## 3. Dépendances Existantes & Limitations / Risques Identifiés

### Dépendances principales de l'existant
- Firebase Auth (Google Sign-In / Credential Manager), Cloud Firestore, Firebase App Check, Firebase AI Logic (`gemini-3.8-flash`), Room/KSP, OkHttp, Retrofit, Moshi.
- APIs publiques françaises sans clé : BAN, DVF cquest/Cerema, IGN Carto/GPU, Géorisques, BCE SDMX.

### Problèmes de sécurité & fonctionnalités incomplètes (confirmés dans `README.md` & le code)
1. **Absence de backend souverain & d'isolation PostgreSQL** : Tout repose sur des règles Firestore client ; le portail client (`ClientDossier`) n'est pas isolé par un vrai token/compte client côté serveur (`README.md` ligne 54).
2. **`privateNotes` stockées dans le même document `ClientDossier`** : Risque d'exposition si le portail client lit le document complet (doit être isolé dans une table/colonne protégée par RBAC).
3. **Monétisation non sécurisée** : `buyerOrders[].status = "PAID"` est actuellement écrivable côté client sans webhook Stripe vérifié (`README.md` ligne 50).
4. **Modération & rôle `staff` non provisionnés** : Aucun backend ne pose le claim `staff` ni ne distingue réellement `AGENCE` vs `PARTICULIER` à la création (`README.md` lignes 44–46).
5. **Suppression RGPD incomplète** : Firestore ne cascade pas la suppression des sous-collections (`contacts`, `tasks`, `audit_logs`) ni la purge du coffre documentaire.
6. **Stockage local uniquement sur appareil (`SellerDocumentVault`)** : Les documents et photos sont stockés dans `filesDir` du téléphone, inutilisables en SaaS multi-postes sans stockage objet S3 chiffré + URL signées.
7. **Absence de file asynchrone & cache distribué** : Les imports Excel > 100 lignes, rappels de relance et synchronisation DVF s'exécutent dans le thread UI/coroutine client au lieu de workers Redis.
8. **IA liée à Firebase Android SDK** : Doit être migrée vers des adaptateurs serveur **OpenAI** (modèle rapide/économique `gpt-4o-mini` / `gpt-4.1-mini` pour synthèses/annonces + modèle avancé `gpt-4.1` / `gpt-4o` pour analyses complexes) avec compteurs de quotas par agence.

---

## 4. Correspondance Ancien Projet → Nouvelle Architecture Monty SaaS

| Ancien (Android / Firebase) | Nouveau (Web SaaS Multi-Tenant) | Stratégie |
|---|---|---|
| `SaasModels` (`AgencyOrg`, `AgencyMember`, `AgencyInvite`) | PostgreSQL `agencies`, `users`, `agency_memberships`, `agency_invites` + RLS | **Réécrire + Migrer données** (ajout rôles `SUPER_ADMIN`, `ASSISTANT`, `BUYER`, `SELLER`) |
| `CrmContact`, `FollowUpTask`, `AgencyAuditLog` | `crm_contacts`, `contact_exchanges`, `follow_up_tasks`, `audit_logs` + API `/api/v1/crm` | **Réécrire backend/UI**, préserver catalogue & KPIs |
| `ClientDossier` (`privateNotes` inclus) | `buyer_dossiers` + `advisor_private_notes` (table séparée RBAC) | **Séparer** les notes privées et ouvrir un vrai portail client authentifié |
| `PropertyItem`, `VisitRecord`, `OfferRecord` | `properties`, `visits`, `offers` + API REST v1 | **Migrer** champs DVF, mandats, publication, délai |
| `SellerPropertyDossier` + `SellerDocumentVault` | `seller_dossiers`, `seller_documents` + Stockage objet S3/R2 présigné | **Réécrire stockage** (plus de `filesDir` local) + préserver catalogue ALUR/DDT |
| `MontyMarketData` (Matching, Finance, HCSF) | `backend/app/domain/real_estate_engines.py` | **Migrer 1:1** les formules déterministes |
| `DvfValuationService`, `SaleTimelineEngine`, `GeoCommuteService` | Services Python FastAPI + cache Redis (TTL 24h DVF/BAN/IGN) | **Migrer 1:1** + cache serveur & logs |
| `ExcelCsvCodec` | Worker Redis + `openpyxl`/`csv` Python (sans limite de 100 lignes) | **Réécrire en tâche asynchrone** en conservant la protection anti-formules |
| `MontyAiService` (Firebase AI Logic) | Service serveur `OpenAiOrchestrator` (7 assistants + quotas par agence) | **Réécrire côté serveur uniquement** + `redact_direct_pii_for_ai` |
| `RgpdComplianceManager` | `backend/app/core/rgpd.py` + purge en cascade PostgreSQL/S3 | **Migrer + Renforcer** |
| Paiement manuel / lien statique | Stripe Billing + Checkout + Webhooks HMAC vérifiés (`stripe.Webhook`) | **Créer** de zéro côté serveur |
| UI Jetpack Compose (`EmeraldDeep`, `IvoryWarm`, `GoldAntique`) | Next.js + React + TypeScript + Tailwind CSS (même DA Émeraude/Ivoire/Or/Noir) | **Réécrire** en UI SaaS Web responsive |

---

## 5. Architecture Multi-Tenant & Rôles RBAC

- **Isolation stricte par `agency_id` (UUID)** :
  - Chaque table métier porte `agency_id NOT NULL REFERENCES agencies(id)`.
  - PostgreSQL applique **Row Level Security (`FORCE ROW LEVEL SECURITY`)** sur les variables de session transactionnelles `app.current_agency_id`, `app.current_user_id`, `app.current_role`.
  - Une requête sans contexte tenant valide renvoie 0 ligne côté SQL, même en cas de bug applicatif (défense en profondeur).
- **7 Rôles Hiérarchiques** :
  1. `SUPER_ADMIN` : Administration globale plateforme Monty, supervision agences/abonnements.
  2. `DIRECTOR` : Directeur d'agence — gestion équipe, licences, facturation Stripe, accès tous dossiers agence & audit logs.
  3. `ADMIN` : Administrateur agence — gestion CRM, biens, exports, modération, affectations.
  4. `AGENT` : Conseiller immobilier — gestion de ses contacts/acquéreurs/vendeurs/biens/visites/offres/estimations + notes privées.
  5. `ASSISTANT` : Assistant commercial — création/édition fiches, planification visites, relances, sans accès aux notes privées confidentielles ni facturation.
  6. `BUYER` : Client acquéreur — accès strict à son propre dossier, sa sélection, ses critères, visites partagées et messagerie conseiller.
  7. `SELLER` : Client vendeur — accès strict à son bien, suivi commercialisation, comptes rendus autorisés, offres reçues et pièces de son dossier.

---

## 6. Plan de Migration par Phases

- **Phase 0 (Fait)** : Audit complet du code existant, inventaire métier/sécurité, schéma PostgreSQL RLS, architecture monorepo.
- **Phase 1 (Socle livré dans ce lot)** : Monorepo `monty-saas/`, backend FastAPI + SQLAlchemy + Alembic SQL RLS + RBAC + RGPD + moteurs déterministes (HCSF, Matching, DVF, Délai, GeoCommute), worker Redis, configuration Railway/Docker, frontend Next.js/Tailwind thème Monty, suite de tests automatisés.
- **Phase 2 — CRM & Import/Export** : CRUD complet contacts, historique échanges, tâches/relances, worker import Excel/CSV asynchrone avec prévisualisation et déduplication, export RGPD.
- **Phase 3 — Métier Immobilier & Portail Client** : Acquéreurs, Vendeurs, Biens, Mandats, Visites, Offres, Coffre documentaire S3 ALUR/DDT, Portails Acquéreur & Vendeur dédiés.
- **Phase 4 — IA, DVF Live, Matching & Carte Urbanisme** : 7 assistants OpenAI avec gestion des quotas, connecteurs temps réel BAN/DVF/IGN/Géorisques mis en cache Redis, carte Leaflet/MapLibre.
- **Phase 5 — Commercialisation & Marketplace** : Stripe Checkout/Billing/Webhooks (Individuel / Agence / Options), licences, modération annonces Agence & Particuliers.
- **Phase 6 — Sécurisation & Tests de Charge** : Tests d'intrusion inter-agences, fuzzing RLS, tests de restauration sauvegarde PostgreSQL, audit RGPD final.
- **Phase 7 — Lancement Bêta** : Déploiement Railway production + agences pilotes.
