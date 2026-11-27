# Architecture MON-AGENT — transition vers Flutter

> État au 27 novembre 2026 : architecture cible approuvée, migration Flutter en phase de fondation. Ce schéma décrit la cible, pas un déploiement déjà opérationnel.

## Architecture cible

```
Flutter (Android / iOS / Web) — client multiplateforme unique
  ├─ Présentation, navigation et design system
  ├─ Modules métier : identité, CRM, biens, visites, estimations, documents
  ├─ Domaine et règles déterministes testables
  └─ Adaptateurs : API, cache local, synchronisation, fichiers
                         |
                  HTTPS / JWT court-terme
                         |
                FastAPI — cœur métier
             AuthN / AuthZ / validation / audit
                         |
              PostgreSQL 16 — source de vérité
                    + RLS par agence
                         |
             Redis / ARQ — tâches asynchrones
                         |
                Stockage objet privé
```

Le portail public et les surfaces web marketing existants peuvent rester en Next.js. Ils consomment les mêmes contrats FastAPI et ne doivent pas recréer une seconde implémentation des règles métier. Le client Flutter Web cible l'espace applicatif authentifié ; le choix de coexistence avec le portail Next.js doit être documenté par parcours et non par duplication arbitraire.

## État réellement présent

- **Android historique :** le dépôt et ses branches doivent être audités pour identifier les sources Kotlin, Room, Firebase et les fonctionnalités réellement présentes. La PR de migration signale que les sources Kotlin attendues ne sont pas dans l'arborescence consultée ; cette absence doit être résolue avant tout portage.
- **SaaS Web :** Next.js / React reste présent.
- **Backend :** FastAPI, PostgreSQL, migrations Alembic, RLS, Redis/ARQ et intégrations Stripe sont documentés et partiellement implémentés. La présence d'un composant ou d'une migration ne prouve pas sa disponibilité en production.
- **Flutter :** seul un socle initial existe dans `monty-saas/mobile/`. Le package déclare Android/iOS/Web, mais les modules métier, authentification, client API, cache, synchronisation et parité ne sont pas encore implémentés.
- **Firebase / Firestore :** conserver pendant l'inventaire et la transition. Ne pas effectuer de double écriture non idempotente ni supprimer des données/règles avant rapprochement documenté.

## Responsabilités et source de vérité

1. **PostgreSQL** : source de vérité des données métier SaaS, avec `agency_id`, contraintes, transactions, audit et RLS.
2. **FastAPI** : autorité sur l'identité applicative, les permissions, les règles métier et les opérations sensibles. Un `agency_id` fourni par le client n'est jamais une preuve d'autorisation.
3. **Flutter** : client de présentation et orchestration locale ; aucun secret serveur ni décision d'autorisation ne doit y résider.
4. **Stockage local** : cache et outbox hors ligne, avec identifiants idempotents, états de synchronisation et conflits visibles. Ne pas le traiter comme une base de vérité parallèle.
5. **Firebase** : maintien temporaire des fonctions existantes qui le nécessitent (identité, App Check, notifications ou données historiques) jusqu'à décision explicite et preuve de remplacement.

## Sécurité multi-agences

- Toute ressource métier doit être liée à une agence et contrôlée côté serveur.
- Les contrôles applicatifs et RLS doivent tous deux refuser les accès inter-agences.
- Ajouter des tests positifs et négatifs pour chaque rôle et chaque ressource sensible.
- Tester les accès sans contexte d'agence, avec agence invalide, utilisateur désactivé et rôle insuffisant.
- Ne pas considérer la documentation RLS comme preuve : exécuter des tests d'intégration contre PostgreSQL configuré comme en production.

## Migration et critères de retrait

1. Inventorier les fonctionnalités, sources de données, schémas, routes et calculs existants.
2. Créer une matrice de parité et des jeux de tests anonymisés de référence.
3. Migrer un module à la fois, sans supprimer le module historique.
4. Vérifier les résultats, la persistance, les permissions, les exports et les scénarios hors ligne.
5. Importer les données avec comptage, empreintes, journal d'audit et réconciliation.
6. Exécuter les builds et tests Android, iOS simulateur/appareils et Web.
7. Piloter, surveiller, préparer le rollback et obtenir une validation explicite avant retrait de Kotlin/Room/Firestore.

**Interdiction de déclarer la migration terminée sur la seule base d'un scaffold Flutter ou d'un build vert.** La sortie exige une parité fonctionnelle démontrée, la conservation des données et des preuves de sécurité.
