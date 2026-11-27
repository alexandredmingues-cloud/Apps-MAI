# Architecture Écosystème MONTY (Tri-Plateforme V3)

```
┌───────────────────────────┐       ┌──────────────────────────┐
│  Android Kotlin / Compose │       │   Web Next.js 15.5+      │
│  - Firebase Auth & AI     │       │   - Espace Pro SaaS      │
│  - Room Offline + Vault   │       │   - Portail Public B2C   │
│  - MontySaasApiClient     │       │   - i18n FR / PT / EN    │
└─────────────┬─────────────┘       └────────────┬─────────────┘
              │ Firebase ID Token / Bearer JWT    │
              ▼                                   ▼
┌──────────────────────────────────────────────────────────────┐
│                 monty-saas-api (FastAPI)                     │
│  - /api/v1/auth/firebase-exchange                            │
│  - /api/v1/monty-ai/* (Monty AI Core + Semantic Cache)       │
│  - /api/v1/price-index/* (+ ADEME Open Data DPE)             │
│  - /api/v1/billing/* (Stripe Checkout + Webhooks)            │
│  - /api/v1/mobile/releases/* (APK SHA-256 + ObjectStorage)   │
└──────┬──────────────────────┬───────────────────────┬────────┘
       │                      │                       │
       ▼                      ▼                       ▼
┌─────────────┐      ┌─────────────────┐     ┌────────────────┐
│ PostgreSQL  │      │      Redis      │     │ Object Storage │
│  16 + RLS   │      │ Rate Limiter +  │     │  Supabase / S3 │
│ (0001-0012) │      │    ARQ Queue    │     │  / Local Disk  │
└─────────────┘      └─────────────────┘     └────────────────┘
```

- **Isolation Multi-Tenant** : chaque requête authentifiée injecte `app.current_agency_id`, `app.current_user_id` et `app.current_role` dans PostgreSQL.
- **Calculateurs Déterministes** : HCSF 35 %, frais de notaire, PV immobilière, DVF score, Pré-État Daté L.721-2, Monty Price Index 6 paliers.
