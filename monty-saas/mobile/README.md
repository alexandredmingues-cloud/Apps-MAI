# MON-AGENT Flutter — état réel et règles de migration

> Statut au 27 novembre 2026 : **socle initial uniquement**. Ce répertoire ne constitue pas encore une application métier migrée.

## Périmètre cible

Un seul client Flutter maintenu pour Android, iOS et Web. Le backend FastAPI reste le point d'entrée métier et PostgreSQL/RLS la source de vérité des données SaaS. Les adaptations spécifiques à une plateforme doivent rester derrière des interfaces dédiées.

## État vérifié du socle

- Flutter package : `mon_agent_mobile`, version initiale `0.1.0+1`.
- SDK déclaré : Dart `>=3.5.0 <4.0.0`, Flutter `>=3.24.0`.
- Dépendances applicatives : Flutter uniquement.
- Écran : fondation visuelle MON-AGENT et libellés Android/iOS/Web.
- Test : vérifie l'identité et les trois libellés de plateforme.
- Authentification, API, persistance, synchronisation, navigation métier, télémétrie et tests de parité : **non implémentés dans ce socle**.

Ne pas présenter les trois libellés affichés comme une preuve de compilation ou de fonctionnement sur les trois plateformes. Seuls les jobs CI correspondants et leurs artefacts font foi.

## Architecture cible

```
Flutter (Android / iOS / Web)
        |
        +-- Présentation & navigation
        +-- Modules métier (CRM, biens, contacts, visites, estimation...)
        +-- Domaine indépendant du framework
        +-- Adaptateurs (API, stockage local, fichiers, notifications)
        |
        HTTPS + jeton court-terme
        |
FastAPI — authentification, autorisation, règles métier
        |
PostgreSQL — source de vérité SaaS + RLS par agence
        |
Redis / ARQ — tâches asynchrones et synchronisation
```

Firebase ne doit pas être supprimé ou réutilisé par défaut. Chaque usage existant (identité, App Check, FCM, Firestore) doit être inventorié et faire l'objet d'une décision documentée. Toute donnée Firestore existante doit être migrée et réconciliée avant retrait.

## Règles non négociables

1. Ne jamais supprimer Kotlin, Room, Firestore, routes API, tables ou migrations avant preuve de parité et plan de retour arrière.
2. Ne jamais stocker de secret serveur dans l'application cliente. Les clés publiques Firebase ne sont pas des secrets, mais doivent être restreintes.
3. Chaque requête métier est autorisée côté serveur. L'identifiant d'agence fourni par le client ne constitue jamais une preuve d'appartenance.
4. Toutes les lectures et écritures multi-agences doivent être protégées par les contrôles d'accès applicatifs et PostgreSQL RLS.
5. Les données locales hors ligne sont un cache/une file de commandes, pas une seconde source de vérité. Les conflits doivent être explicites et auditables.
6. Les calculs immobiliers déterministes doivent être partagés ou vérifiés par des jeux de tests de référence communs.
7. Aucun écran de démonstration, mock ou fixture ne doit être présenté comme une fonctionnalité connectée.
8. Aucun déploiement de production, merge de PR ou suppression de l'ancien client sans validation explicite du propriétaire.

## Lots de migration et critères de sortie

### Lot 0 — Inventaire et preuve de référence
- Récupérer et inventorier les sources Android historiques, les schémas Room/Firestore, routes API et parcours utilisateurs.
- Établir une matrice fonctionnalité → source existante → cible Flutter → API → données → tests.
- Capturer les résultats des tests existants et les formats d'export/import.
- **Sortie :** inventaire exhaustif et jeux de données de référence versionnés sans données personnelles.

### Lot 1 — Fondations
- Environnements dev/test/prod séparés, configuration typée et validation au démarrage.
- Gestion d'état, navigation, design system accessible, journalisation sans données personnelles.
- Client HTTP typé, gestion centralisée des erreurs, timeout et renouvellement sécurisé des sessions.
- **Sortie :** builds Android, iOS simulateur et Web release reproductibles ; tests unitaires et widget verts.

### Lot 2 — Identité et cloisonnement
- Échange d'identité côté serveur, sessions courtes, déconnexion et révocation.
- Rôles explicites : particulier, conseiller, administrateur d'agence, administrateur plateforme.
- Tests négatifs inter-agences sur chaque ressource sensible.
- **Sortie :** tests d'autorisation et RLS prouvant qu'une agence A ne lit ni ne modifie les données de B.

### Lot 3 — Modules métier par priorité
Pour chaque module : contrats API, règles métier, états de chargement/vide/erreur, validation, accessibilité, persistance, tests unitaires/intégration et tests de non-régression.
Ordre proposé : authentification/profil, CRM/contacts, biens/portefeuille, tâches/visites, acquéreurs et matching, estimations/DVF, documents/rapports, marketplace et paiements.

### Lot 4 — Hors-ligne et convergence
- Cache local chiffré si des données personnelles y sont conservées.
- Outbox idempotente, identifiants de commande, reprises réseau et résolution des conflits.
- Import avec comptage, empreintes, journal d'audit et rapprochement source/cible.
- **Sortie :** tests de coupure réseau, reprise, doublons, conflits et réconciliation sans perte.

### Lot 5 — Parité et retrait progressif
- Comparaison fonctionnelle et données entre l'ancien client et Flutter.
- Tests Android physiques, iOS simulateur puis appareils, Web navigateurs supportés.
- Observabilité, sauvegardes/restauration testées, procédure de rollback et pilote.
- **Sortie :** validation explicite du propriétaire avant retrait de Kotlin ou bascule de production.

## Matrice de preuve obligatoire

Pour chaque fonctionnalité, conserver :
- identifiant et description du parcours ;
- comportement de référence ;
- implémentation Flutter et endpoint(s) utilisés ;
- source de données et règles d'autorisation ;
- tests exécutés et résultat CI horodaté ;
- preuve de persistance et test de reprise si applicable ;
- statut : `NOT_STARTED`, `IN_PROGRESS`, `VERIFIED`, `BLOCKED`.

`VERIFIED` exige des preuves reproductibles. Une capture d'écran seule n'est jamais suffisante.

## Première action bloquante

La PR Flutter #4 mise à jour confirme que les sources Kotlin métier sont présentes dans l'arborescence GitHub actuelle (modèles, dépôts CRM/agences, services DVF, cartographie, IA, espace vendeur et interfaces Compose). Le préalable bloquant est donc leur inventaire exhaustif et la création de tests de caractérisation, pas leur récupération. Ne pas commencer le portage par des écrans fictifs : établir d'abord la matrice fonctionnalité → source Kotlin → API → modèle de données → écran Flutter → tests.
