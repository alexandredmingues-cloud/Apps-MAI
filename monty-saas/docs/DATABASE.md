# Base de Données PostgreSQL 16 (RLS) & Cloud Firestore

## Migrations PostgreSQL (`monty-saas/backend/alembic/versions/`)

- `0001` à `0009` : Socle agences, utilisateurs, CRM, marketplace, notaires, RNIC, notifications, drapeaux de modules et RLS multi-tenant.
- `0010` : Tables Phase 27/28/29 (`monty_ai_conversations`, `monty_ai_memories`, `monty_ai_actions`, `monty_ai_metrics`, `mobile_app_releases`, `price_index_configs`, `price_index_history`).
- `0011` : Tables gouvernance V3 (`lead_keyword_rules`, `agency_module_flags`) avec politiques RLS.
- `0012` : Seuils 6 paliers Monty Price Index (`excellent_deal_pct`) et table `seller_pack_orders` pour suivi serveur des paiements Stripe.

## Cloud Firestore (`firestore.rules` & `firestore.test.js`)

- `properties` : création/maintien de `publisherType: "AGENCE"` réservés aux comptes disposant d'un claim serveur `advisor: true` ou `staff: true`. Les annonces `PARTICULIER` restent soumises à modération (`A_VERIFIER` → `PUBLIEE` par `staff: true`).
- `seller_dossiers` : les clients ne peuvent écrire que `buyerOrders[].status = "PENDING_PAYMENT"`. Le passage à `PAID` / `DELIVERED` est verrouillé côté serveur (Stripe Webhook / Admin SDK `staff: true`).
- `firebase.json` : paramétré par défaut sur la base nommée AI Studio ; à adapter vers `(default)` ou l'ID cible lors d'un déploiement vers un autre projet Firebase.
