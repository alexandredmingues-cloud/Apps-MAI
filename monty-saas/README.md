# MON-AGENT SAAS — Plateforme Immobilière Multi-Agences & Multi-Pays

> **Philosophie :** « L'humain avant tout. La technologie au service du conseiller immobilier. »

## Stratégie Géographique & Cloisonnement Réglementaire

- **France entière (`FR`)** — Priorité 1 & 2 active (HCSF 35 %, DMTO/Notaires, DPE A–G, Loi Le Meur, Plus-values CGI, DVF).
- **Portugal (`PT`)** — Priorité 4 module national isolé prêt (Banco de Portugal DSTI/LTV, IMT & Imposto do Selo / IMT Jovem, Certificado Energético ADENE A+–F).
- **Europe & International** — Socle technique commun + packs juridictionnels nationaux indépendants (`country_code`, `locale`, `regulation_jurisdiction`).

## Arborescence du Monorepo

```text
mon-agent-saas/
├── docs/
│   └── PHASE_0_AUDIT.md            # Audit intégral de l'existant & plan de migration
├── docker-compose.yml              # Stack locale PostgreSQL 16 + Redis 7 + FastAPI + Worker + Next.js
├── backend/                        # Python 3.11 + FastAPI + SQLAlchemy + Alembic
│   ├── alembic.ini
│   ├── alembic/
│   │   ├── env.py
│   │   └── versions/
│   │       ├── 0001_initial_multi_tenant_rls.{py,sql}
│   │       ├── 0002_dual_audience_news_hub.{py,sql}
│   │       ├── 0003_multi_country_jurisdiction.{py,sql}
│   │       ├── 0004_crm_marketplace_rgpd.{py,sql}
│   │       ├── 0005_legal_pre_etat_and_agents.{py,sql}
│   │       ├── 0006_auth_billing_hardening.{py,sql}
│   │       ├── 0007_portfolio_and_public_leads.{py,sql}
│   │       ├── 0008_admin_audit_and_ops.{py,sql}
│   │       └── 0009_notaries_rnic_interconnections.{py,sql}
│   ├── app/
│   │   ├── main.py
│   │   ├── core/{config.py,rbac.py,rgpd.py,security.py}
│   │   ├── db/
│   │   │   ├── session.py
│   │   │   ├── postgres_repositories.py
│   │   │   ├── postgres_crm_marketplace_repository.py
│   │   │   ├── postgres_portfolio_leads_repository.py
│   │   │   ├── postgres_legal_agents_repository.py
│   │   │   ├── postgres_notaries_rnic_repository.py
│   │   │   └── postgres_interconnections_repository.py
│   │   ├── domain/
│   │   │   ├── auth_and_billing.py
│   │   │   ├── country_packs.py
│   │   │   ├── crm_service.py
│   │   │   ├── csv_import_export.py
│   │   │   ├── dvf_score_v2.py
│   │   │   ├── interconnections_service.py
│   │   │   ├── legal_pre_etat_date.py
│   │   │   ├── marketplace_service.py
│   │   │   ├── news_hub.py
│   │   │   ├── notaries_service.py
│   │   │   ├── portfolio_and_leads_service.py
│   │   │   ├── real_estate_agents.py
│   │   │   ├── real_estate_engines.py
│   │   │   └── rnic_copro_service.py
│   │   ├── services/openai_news_adapter.py
│   │   ├── api/v1/{router.py,crm_router.py,marketplace_router.py,news_router.py,portfolio_router.py,legal_agents_router.py,billing_router.py,notaries_rnic_router.py,interconnect_router.py,deps.py}
│   │   └── workers/redis_tasks.py
│   ├── tests/
│   └── requirements.txt
└── frontend/                       # React + TypeScript + Next.js 15 + Tailwind CSS
    ├── src/
    │   ├── app/
    │   │   ├── page.tsx            # Cockpit SaaS Pro (KPI Live, Notifications, Agenda Unifié, CRM, Pipeline, Admin)
    │   │   ├── site/page.tsx       # Site Web Public (Particuliers & Agences)
    │   │   ├── annonces/           # Recherche par Temps de Trajet (6 modes), Favoris, RDV Visite RGPD & Comparateur DVF
    │   │   ├── publier/            # Publication Vendeur + Rédaction IA sans PII + Délai estimé
    │   │   ├── coproprietes/       # Base Nationale Copropriétés RNIC ANAH + Annuaire Notaires de France
    │   │   ├── mon-compte/         # Portail Particulier / Vendeur (Connexion, Favoris, Alertes, Coffre Documents, RNIC)
    │   │   ├── actualites/         # Centre d'actualités public & SEO (/actualites/[slug])
    │   │   ├── simulateurs/        # 6 Simulateurs Immobiliers + Module PT isolé
    │   │   └── pro/actualites/     # Veille Pro + IA Double Format + Admin Sources
    │   └── components/
    │       ├── admin/AdminSaasConsole.tsx
    │       ├── ai/AgentsAndScoutPanel.tsx
    │       ├── calculators/RealEstateSimulatorsSuite.tsx
    │       ├── crm/CrmCockpitPanel.tsx
    │       ├── layout/{Mon-AgentShell.tsx,PublicWebHeader.tsx}
    │       ├── legal/{NotariesAndRnicPanel.tsx,PreEtatDateWorkspace.tsx}
    │       ├── marketplace/MarketplaceAndPublishView.tsx
    │       ├── news/NewsHubExperience.tsx
    │       ├── portal/ClientPortalView.tsx
    │       └── portfolio/PortfolioMatchingPipelinePanel.tsx
    └── package.json
```

## Modules Clés Ajoutés

- **Annuaire Officiel des Notaires de France & Dossiers Compromis (`/api/v1/notaries/*`)** :
  - Recherche en direct via API DINUM / INSEE (`recherche-entreprises.api.gouv.fr`, code NAF `69.10Z`) avec lien direct vers l'annuaire CSN `notaires.fr` pour vérification des e-mails `@notaires.fr`.
  - Carnet des notaires de l'agence (`agency_notary_contacts`), génération de courriers RFC-2822 `.eml` (`OUVERTURE_DOSSIER`, `SIGNATURE_COMPROMIS`, `COMPLEMENT_ALUR`) avec pièce jointe Pré-État Daté HTML, et envoi SMTP / Resend si configuré.
- **Base Nationale des Copropriétés de France — RNIC ANAH (`/api/v1/rnic/*`, `/coproprietes`)** :
  - Interrogation en direct de l'API Tabulaire `data.gouv.fr` du Registre National d'Immatriculation des Copropriétés.
  - **Règle d'accès différenciée côté serveur** : gratuit et illimité pour les agences immobilières (`SUPER_ADMIN`, `DIRECTOR`, `ADMIN`, `AGENT`, `ASSISTANT`) et les vendeurs vérifiés (`SELLER`) ; aperçu limité pour les particuliers/acquéreurs avec déblocage via Pass 30 jours Stripe Checkout (`/api/v1/billing/rnic-pass`).
- **Interconnexions Temps Réel & Portail Particulier (`/mon-compte`)** :
  - Création de bien → matching automatique acquéreurs CRM + notification agence.
  - Passage transaction en `COMPROMIS` / `ACTE` → relance notaire automatique + notification.
  - Demande de visite publique (`VISIT_REQUEST` RGPD) → création contact CRM + relance + notification.

## Variables d'environnement

- Copier `backend/.env.example` vers `backend/.env` et renseigner côté serveur uniquement :
  `DATABASE_URL`, `REDIS_URL`, `JWT_SECRET`, `OPENAI_API_KEY`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `S3_*`,
  `RNIC_DATA_GOUV_RESOURCE_ID`, `RNIC_PARTICULIER_PRICE_EURO`, `RESEND_API_KEY`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM_EMAIL`.
- Copier `frontend/.env.example` vers `frontend/.env.local` (`NEXT_PUBLIC_API_BASE_URL`). Aucune clé secrète côté frontend.

## Tests & Validation

```bash
# Tests unitaires backend (Python 3.11)
python3 -m unittest discover -s mon-agent-saas/backend/tests -v

# Lancement complet avec Docker Compose (sur poste local / CI avec Docker)
cd monty-saas && docker compose up --build
```
