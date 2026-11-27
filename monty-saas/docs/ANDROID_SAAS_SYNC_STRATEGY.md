# Stratégie de synchronisation — MON-AGENT Flutter ↔ FastAPI

> Ce document remplace la stratégie de convergence centrée sur Ktor/Retrofit. La migration Flutter est en cours et n'est pas encore fonctionnelle ; le contenu décrit les décisions et critères cibles, pas des capacités déjà livrées.

## 1. État de départ à préserver

L'historique Android peut contenir Firebase Auth, Firestore, Room, App Check, FCM et des moteurs métier déterministes. Le SaaS comprend FastAPI, PostgreSQL/RLS, Redis/ARQ et les services métier Web. La PR Flutter #4 mise à jour confirme que les sources Kotlin métier sont présentes dans l'arborescence actuelle (modèles, dépôts CRM/agences, services DVF, cartographie, IA, espace vendeur et interfaces Compose). Leur inventaire exhaustif et les tests de caractérisation sont le préalable bloquant.

Le socle Flutter actuel ne comporte pas encore de client API, d'authentification, de persistance locale, de synchronisation ou de modules métier. Il ne faut donc pas prétendre que Flutter synchronise déjà avec le SaaS.

## 2. Architecture cible

- **Flutter Android/iOS/Web** : interface unique, règles de présentation, orchestration de cas d'usage et cache local limité.
- **FastAPI** : point d'entrée métier, validation, authentification applicative, autorisations, transactions, audit et intégrations.
- **PostgreSQL + RLS** : source de vérité des données métier SaaS, strictement isolée par agence.
- **Redis/ARQ** : tâches asynchrones et traitements de synchronisation.
- **Firebase** : conservation transitoire des usages historiques justifiés (par exemple App Check ou FCM) jusqu'à migration vérifiée.

Aucun client ne peut choisir librement son agence ou son rôle. Le serveur dérive l'appartenance de l'identité authentifiée et vérifie chaque ressource.

## 3. Identité et sessions

1. Inventorier les identités existantes (Firebase UID, comptes SaaS, utilisateurs et agences).
2. Définir une table de correspondance unique et contrainte entre identité externe et utilisateur interne.
3. Vérifier côté serveur les jetons d'identité avec les clés, émetteurs, audiences et états de compte attendus.
4. Émettre un jeton applicatif de courte durée avec identifiant utilisateur, agence autorisée, rôle, expiration et identifiant de session.
5. Implémenter renouvellement, révocation, déconnexion, rotation et tests de rejeu.
6. Ne jamais embarquer de secret serveur ou de clé privée dans Flutter.

## 4. Synchronisation hors ligne

Le stockage local Flutter est un cache et une outbox, pas une source de vérité concurrente.

Chaque commande hors ligne doit inclure :
- un identifiant unique généré côté client pour l'idempotence ;
- l'identité de l'utilisateur et le contexte d'agence établi par le serveur ;
- l'identifiant de ressource et sa version connue ;
- une date client informative et une date serveur faisant autorité ;
- un état explicite : `PENDING`, `SYNCED`, `CONFLICT`, `FAILED`.

Le serveur doit traiter les commandes de manière idempotente et retourner un résultat de conflit structuré. Les conflits ne doivent jamais être écrasés silencieusement. Les données personnelles mises en cache doivent être minimisées, protégées sur l'appareil et effaçables à la déconnexion ou à la demande RGPD.

## 5. Migration des données historiques

- Exporter les données Firestore/Room avec inventaire des collections, volumes, pièces jointes et relations.
- Définir les règles de mapping et conserver les identifiants historiques nécessaires à la traçabilité.
- Importer par lots idempotents, avec journal d'audit et reprise après interruption.
- Réconcilier les volumes, identifiants, relations, pièces jointes et échantillons fonctionnels.
- Traiter explicitement doublons, données orphelines, suppressions et conflits.
- Ne désactiver aucune écriture historique avant validation du rapprochement et plan de retour arrière.

## 6. Parité des calculs et des parcours

Les moteurs déterministes historiques (DVF/estimation, capacité HCSF, frais, fiscalité, échéanciers et autres calculateurs effectivement présents) doivent être inventoriés avant portage. Chaque moteur reçoit des jeux de référence anonymisés, des tests de limites et des comparaisons de résultats. Le même contrat de test doit pouvoir être exécuté contre l'ancien moteur, Flutter et l'API lorsque le calcul est serveur.

## 7. Sécurité multi-agences

Pour chaque ressource et rôle, tester :
- lecture et écriture autorisées dans la bonne agence ;
- refus d'accès à une autre agence ;
- refus sans agence, avec agence falsifiée ou utilisateur désactivé ;
- séparation des notes privées et des documents ;
- tâches asynchrones exécutées avec le contexte d'autorisation correct.

Les tests RLS doivent s'exécuter sur PostgreSQL réel ou un environnement de test représentatif, pas uniquement sur mocks.

## 8. Critères avant retrait de l'ancien client

Aucun retrait Kotlin/Room/Firestore avant :
- inventaire fonctionnel et de données complet ;
- parité validée des parcours et calculs ;
- tests de permissions et RLS inter-agences réussis ;
- synchronisation hors ligne testée avec perte réseau, doublons et conflits ;
- import et réconciliation signés ;
- builds Android, iOS et Web reproductibles ;
- sauvegarde/restauration et rollback testés ;
- pilote opérationnel et validation explicite du propriétaire.
