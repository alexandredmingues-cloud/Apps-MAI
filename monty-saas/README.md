# MONTY SAAS — Plateforme Immobilière Multi-Agences & Multi-Pays

> **Philosophie :** « L'humain avant tout. La technologie au service du conseiller immobilier. »

## Stratégie Géographique & Cloisonnement Réglementaire

- **France entière (`FR`)** — Priorité 1 & 2 active (HCSF 35 %, DMTO/Notaires, DPE A–G, Loi Le Meur, Plus-values CGI, DVF).
- **Portugal (`PT`)** — Priorité 4 module national isolé prêt (Banco de Portugal DSTI/LTV, IMT & Imposto do Selo / IMT Jovem, Certificado Energético ADENE A+–F).
- **Europe & International** — Socle technique commun + packs juridictionnels nationaux indépendants (`country_code`, `locale`, `regulation_jurisdiction`).

## Arborescence du Monorepo

```text
monty-saas/
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
│   │       └── 0003_multi_country_jurisdiction.{py,sql}
│   ├── app/
│   │   ├── main.py
│   │   ├── core/{config.py,rbac.py,rgpd.py,security.py}
│   │   ├── db/session.py
│   │   ├── domain/{country_packs.py,crm_service.py,csv_import_export.py,marketplace_service.py,news_hub.py,real_estate_engines.py}
│   │   ├── services/openai_news_adapter.py
│   │   ├── api/v1/{crm_router.py,deps.py,marketplace_router.py,news_router.py,router.py}
│   │   └── workers/redis_tasks.py
│   ├── tests/
│   │   ├── test_phase1_foundation.py
│   │   ├── test_dual_audience_news_and_calculators.py
│   │   └── test_phase2_crm_marketplace.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── railway.toml
└── frontend/                       # React + TypeScript + Next.js 15 + Tailwind CSS
    ├── src/
    │   ├── app/
    │   │   ├── page.tsx            # Cockpit SaaS Pro (CRM, Notes privées, Relances, CSV, Marketplace)
    │   │   ├── site/page.tsx       # Site Web Public (Particuliers & Agences)
    │   │   ├── annonces/           # Recherche par Temps de Trajet (6 modes) & Comparateur DVF
    │   │   ├── publier/            # Publication Vendeur + Rédaction IA sans PII + Délai estimé
    │   │   ├── actualites/         # Centre d'actualités public & SEO (/actualites/[slug])
    │   │   ├── simulateurs/        # 6 Simulateurs Immobiliers + Module PT isolé
    │   │   └── pro/actualites/     # Veille Pro + IA Double Format + Admin Sources
    │   ├── components/
    │   │   ├── calculators/RealEstateSimulatorsSuite.tsx
    │   │   ├── crm/CrmCockpitPanel.tsx
    │   │   ├── layout/{MontyShell.tsx,PublicWebHeader.tsx}
    │   │   ├── marketplace/MarketplaceAndPublishView.tsx
    │   │   └── news/NewsHubExperience.tsx
    │   └── lib/{api-client.ts,country-packs.ts,marketplace-local.ts,news-catalog.ts,real-estate-calculators.ts,types.ts}
    ├── package.json
    ├── tailwind.config.ts
    ├── tsconfig.json
    └── railway.toml
```

## Variables d'environnement

- Copier `backend/.env.example` vers `backend/.env` et renseigner côté serveur uniquement :
  `DATABASE_URL`, `REDIS_URL`, `JWT_SECRET`, `OPENAI_API_KEY`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `S3_*`.
- Copier `frontend/.env.example` vers `frontend/.env.local` (`NEXT_PUBLIC_API_BASE_URL`). Aucune clé secrète côté frontend.

## Tests & Validation

```bash
# Tests unitaires backend (Python 3.11)
python3 -m unittest discover -s monty-saas/backend/tests -v

# Lancement complet avec Docker Compose (sur poste local / CI avec Docker)
cd monty-saas && docker compose up --build
```
