# ADR — Migration Flutter multiplateforme MON-AGENT

- **Statut :** accepté — migration progressive, non destructive
- **Date :** 2026-11-27
- **Branche :** `migration/flutter-multiplatform-2026-11`
- **Cibles :** Android, iOS et Web

## Décision d'architecture

Créer un client Flutter partagé pour Android, iOS et Web. Conserver temporairement l'application Kotlin/Jetpack Compose comme référence fonctionnelle et solution de repli. Conserver Next.js comme site public/SEO et cockpit web existant pendant la migration ; aucune route ni fonctionnalité Next.js ne sera supprimée avant décision dédiée et validation de parité. Flutter consommera les API FastAPI existantes. PostgreSQL/Supabase demeure la source de vérité métier ; aucune base locale mobile ne devient source de vérité.

## Constat vérifié dans le dépôt au démarrage

- Le monorepo `monty-saas/` contient le backend FastAPI, les migrations Alembic, les tests et le frontend Next.js.
- `monty-saas/docs/PHASE_0_AUDIT.md` décrit précisément l'ancien client Kotlin/Compose, ses ViewModels, modèles, services DVF, géographiques, IA, CRM, coffre vendeur, Firebase/Firestore et Room.
- **Les sources Kotlin originales ne sont pas présentes dans l'arborescence GitHub consultable sur la branche `main` au moment de cette préparation.** L'audit indique qu'elles se trouvaient dans un workspace extrait. Une migration fidèle des écrans, règles et comportements ne peut donc pas être déclarée faite tant que ces sources et, si nécessaire, les jeux de tests/données de référence n'ont pas été versés dans un emplacement de travail contrôlé.
- Les fonctionnalités Android répertoriées dans l'audit sont des exigences à préserver, pas des fonctionnalités Flutter déjà portées.

## Règles non négociables

1. Ne supprimer, renommer ou désactiver aucun fichier Kotlin, configuration Firebase, règle Firestore, schéma Room, workflow APK ni endpoint de release pendant la migration.
2. Ne jamais copier de secrets Firebase, clés de signature, fichiers `.env`, données clients ou exports réels dans Git.
3. PostgreSQL/API FastAPI est la cible métier ; Firebase/Firestore reste en lecture/écriture selon le comportement actuel jusqu'à migration de données vérifiée. Pas de bascule implicite ni de double-écriture non maîtrisée.
4. Toute migration de données doit être idempotente, journalisée, réconciliable (comptages, clés métier, échantillons), sauvegardée et testée sur copie avant production.
5. Les autorisations multi-agences sont imposées et testées côté serveur (RBAC/RLS). Masquer une commande dans l'interface ne constitue pas un contrôle d'accès.
6. L'IA, les paiements, documents vendeurs, données privées et opérations RGPD restent derrière les API serveur ; aucune clé privilégiée dans le client Flutter.
7. Le web Flutter et le web Next.js doivent partager les contrats API et les règles métier ; Next.js reste le site public SEO jusqu'à parité et décision explicite.
8. Aucun merge dans `main`, déploiement production ou retrait de Kotlin sans validation explicite.

## Matrice de portage — parité obligatoire

| Domaine de l'ancien client | Cible Flutter | Preuve exigée avant parité |
|---|---|---|
| Authentification, Google Sign-In, sessions | Auth API / fournisseur validé, stockage sécurisé mobile | Tests connexion, expiration, révocation, rôles |
| Conseiller, CRM, contacts, échanges, tâches | Modules CRM | CRUD API, pagination, filtres, tests RBAC |
| Agences, membres, invitations | Gestion multi-tenant | Tests d'isolation entre deux agences et rôles |
| Acquéreurs, critères, matching | Recherche et matching serveur | Jeux d'oracle comparant scores/règles |
| Biens, mandats, visites, offres | Parcours transactionnels | Tests de bout en bout et persistance |
| Vendeur, pièces ALUR, coffre documentaire | Portail vendeur + stockage objet signé | Autorisations, upload/download, suppression RGPD |
| Estimation DVF, délai de vente, simulateur HCSF | Moteurs via API | Tests numériques de référence et gestion données insuffisantes |
| Carte, BAN, cadastre, PLU, Géorisques, trajets | Carte multiplateforme | Tests sources, erreurs réseau, permissions localisation |
| Assistant IA, rédaction, scoring | API IA FastAPI | Tests redaction PII, quotas, erreurs et streaming |
| Import/export CSV/XLSX | Import/export serveur | Fichiers de référence, doublons, injection de formules |
| RGPD, audit, consentements | Écrans + API dédiée | Export, effacement, rétention, journaux |
| Notifications, chat et agenda | Services/API + notifications natives/web | Tests livraison, permissions et synchronisation |

## Phases et portes de sortie

### P0 — Préparation (ce commit)
- Créer le projet Flutter non destructif et le pipeline de vérification.
- Documenter l'inventaire connu, les risques et les preuves attendues.
- Résultat : squelette compilable, pas encore une parité métier.

### P1 — Récupération et caractérisation de l'existant
- Récupérer le dépôt/source Kotlin original, ressources, tests, règles Firestore et schéma Room sans secrets.
- Inventorier tous les écrans, routes, modèles, événements, permissions et dépendances.
- Capturer des jeux de tests anonymisés et résultats attendus.
- Résultat : inventaire exhaustif signé avant portage.

### P2 — Socle Flutter
- Architecture par fonctionnalités, navigation, thème, localisation FR/EN/PT, gestion d'état, client API, erreurs, observabilité.
- Authentification, gestion de session, RBAC et isolation agence.
- Tests unitaires, widget et intégration.

### P3 — Portage métier par lots
Ordre : identité/session → CRM → acquéreurs/biens/visites/offres → espace vendeur et documents → estimation/simulateurs → carte/urbanisme → IA → import/export → notifications/RGPD.
Chaque lot doit passer ses tests et sa revue de sécurité avant le suivant.

### P4 — Données et synchronisation
- Cartographier Firebase/Room vers PostgreSQL/S3 et identifier les données non migrées.
- Scripts dry-run puis migration sur copie, sauvegarde préalable, rapports de réconciliation.
- Aucun effacement de la source avant rapprochement exhaustif et période de retour arrière validée.

### P5 — Validation multiplateforme
- CI : analyse Dart, tests, build Android, build Web ; build iOS sur runner macOS.
- Recette manuelle sur vrais appareils Android/iOS, navigateurs mobiles/desktop, réseau dégradé et accessibilité.
- Tests de sécurité multi-agences, RGPD, restauration et charge.
- Publication interne/bêta seulement après validation.

### P6 — Retrait éventuel de Kotlin
Autorisé uniquement si toutes les cases suivantes sont prouvées :
- 100 % des fonctionnalités critiques et données métier ont une parité acceptée ;
- tests de référence et tests de non-régression réussis ;
- aucune perte après migration et réconciliation signée ;
- sauvegarde/restauration et rollback testés ;
- sécurité/RLS/RBAC et RGPD validés ;
- APK/AAB, iOS et Web distribuables et testés ;
- période de coexistence terminée et accord explicite de mise hors service.
Sinon Kotlin demeure conservé.

## Definition of Done

Un module n'est « terminé » que si son code, tests automatisés, tests réels pertinents, persistance, permissions, états d'erreur, accessibilité et preuves CI sont disponibles. Une compilation seule ne prouve pas la parité métier.

## Limites de cette première livraison

Cette branche prépare l'architecture et la CI. Elle ne prétend pas avoir migré les fonctionnalités Kotlin, les données Firebase/Room, ni produit un binaire iOS signé. L'absence des sources Kotlin dans le dépôt empêche de porter fidèlement les comportements à ce stade.
