# Audit critique MON-AGENT — 27 novembre 2026

## Synthèse exécutive

Le dépôt possède un socle métier et SaaS significatif, mais il ne faut pas confondre richesse du périmètre, code présent, fonctionnalité validée et capacité de mise en production.

**Décision : poursuivre la migration sur la branche dédiée, conserver Kotlin et les systèmes de données historiques, ne pas fusionner et ne pas déployer en production à ce stade.**

## Éléments vérifiés

- Dépôt : `alexandredmingues-cloud/Apps-MAI`, branche par défaut `main`.
- PR Flutter #4 : brouillon ouvert, migration progressive Android/iOS/Web.
- Flutter : package initial, écran de fondation et test widget simple.
- CI Flutter du run `46039428511` : analyse Dart, widget tests, APK debug, build Web et build iOS simulateur réussis.
- CI socle du run `46039428401` : compilation/tests Android Kotlin, tests backend FastAPI et typecheck/build Next.js réussis.
- Ces runs sont des preuves pour les commits auxquels ils sont attachés, pas pour les commits documentaires ultérieurs. Les commits documentaires récents n'ont pas de nouveaux runs GitHub Actions remontés.
- Le statut Vercel sur le dernier commit documentaire est en échec avec le motif `build-rate-limit`. Il s'agit d'une limite de build du compte/projet, pas d'une preuve d'échec du code applicatif.
- Le corps actuel de la PR #4 confirme que les sources Kotlin métier sont présentes : modèles, dépôts CRM/agences, services DVF, cartographie, IA, espace vendeur et interfaces Compose.

## Constats critiques et priorités

### P0 — Préserver la continuité fonctionnelle et les données

Le client Flutter reste un socle. Il ne remplace pas encore les parcours Kotlin. Construire une matrice de parité à partir des sources Kotlin réelles, des routes FastAPI, des schémas Room/Firestore et des données de référence. Aucun retrait de Kotlin/Room/Firestore avant tests de parité, migration réconciliée et rollback testé.

### P0 — Isolation multi-agences démontrée

La documentation mentionne PostgreSQL RLS et le contexte d'agence. Il faut des tests d'intégration négatifs réels : agence A ne peut ni lire, ni modifier, ni supprimer les ressources de B ; refus également sans agence, avec rôle insuffisant et utilisateur désactivé. Les mocks et la seule présence de politiques RLS ne suffisent pas.

### P1 — Rendre la CI réellement bloquante

La PR #4 indique que le workflow utilise encore `npm run lint || true`, alors que le script frontend est `next lint` et que les logs rapportent l'ouverture d'un assistant interactif. Le lint n'est donc pas validé et son échec est neutralisé. Corriger la configuration ESLint pour Next.js 15, utiliser une commande non interactive, verrouiller les dépendances et retirer tout `|| true` des contrôles qualité.

### P1 — Clarifier la source de vérité

PostgreSQL doit être la source de vérité des données métier SaaS. Flutter peut garder un cache local et une outbox hors ligne, mais ne doit pas introduire une base métier parallèle. Firebase/Firestore reste pendant la transition pour les usages historiques prouvés ; chaque usage doit avoir une décision de maintien ou de retrait.

### P1 — Durcir l'authentification et l'autorisation

Vérifier la chaîne complète : identité, audience/émetteur du jeton, expiration, révocation, appartenance à l'agence, rôle, autorisation par ressource, audit. Ne jamais faire confiance à un `agency_id` envoyé par le client. Vérifier également que les tâches ARQ conservent le contexte d'autorisation attendu.

### P1 — Distinguer démo et données réelles

Le CRM en mode local a été explicitement identifié comme non persisté. Les interfaces doivent continuer à distinguer clairement données de démonstration, cache local, données synchronisées et données serveur. Ne jamais afficher un succès de sauvegarde sans confirmation serveur ou état local explicite.

### P2 — Qualité produit et accessibilité

Définir des parcours prioritaires et les états chargement/vide/erreur/hors ligne, accessibilité clavier/lecteur d'écran, contrastes, langues FR/PT/EN, formats et fuseaux. Mesurer les temps de réponse et les erreurs par parcours plutôt que multiplier les écrans.

### P2 — Fiabilité des analyses immobilières et IA

Chaque estimation ou analyse doit afficher ses sources, date, zone géographique, échantillon, limites et niveau de confiance. Séparer les calculs déterministes des réponses génératives. L'IA ne doit jamais inventer une donnée DVF, DPE, copropriété ou réglementaire ; les résultats sensibles doivent être traçables et accompagnés de leurs réserves.

## Travaux documentaires réalisés sur la branche Flutter

- Ajout de `monty-saas/mobile/README.md` : état réel, architecture cible, lots et critères de preuve.
- Réécriture de `monty-saas/docs/ARCHITECTURE.md` pour refléter la cible Flutter sans prétendre qu'elle est déjà livrée.
- Réécriture de `monty-saas/docs/ANDROID_SAAS_SYNC_STRATEGY.md` en stratégie de synchronisation Flutter.
- Mise à jour du README racine pour signaler le statut réel de la migration.
- Correction documentaire après vérification de la PR #4 : les sources Kotlin sont présentes et doivent être inventoriées, elles ne sont pas à récupérer depuis une source inconnue.

## Prochain cycle technique recommandé

1. Cartographier les sources Kotlin et dresser la matrice de parité exhaustive.
2. Examiner les migrations PostgreSQL/RLS et exécuter des tests inter-agences.
3. Corriger le lint frontend non bloquant et auditer les dépendances.
4. Définir les contrats API Flutter (auth, profil, CRM, biens) et les tests de contrat.
5. Implémenter l'authentification et le premier parcours CRM connecté, avec persistance réelle.
6. Ajouter cache/outbox idempotente et tests de coupure réseau avant toute migration de données.
7. Répéter analyse, tests et builds Android/iOS/Web sur chaque lot.

## Critères de sortie

Une fonctionnalité est `VERIFIED` uniquement si son comportement, son autorisation, sa persistance, ses erreurs et ses tests sont reproductibles. Un build vert du squelette n'est pas une preuve de parité métier. PR #4 reste en brouillon ; aucun merge, retrait Kotlin ou déploiement production n'est autorisé par cet audit.
