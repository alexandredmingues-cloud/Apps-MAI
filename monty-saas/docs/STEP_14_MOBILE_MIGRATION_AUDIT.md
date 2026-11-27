# Étape 14 — Audit de migration mobile multiplateforme

Date : 27 novembre 2026  
Dépôt : `alexandredmingues-cloud/Apps-MAI`  
Branche de travail : `fix/step2-production-hardening-2026-11-27`

## 1. Objectif et périmètre

Préparer la migration mobile demandée vers **Flutter**, avec les cibles **Android + iOS + Web**, sans perte de fonctionnalités ni de données.

Cette étape est un audit de préparation et de sécurisation. Elle ne supprime pas l'application Kotlin existante et ne prétend pas que la migration Flutter est déjà réalisée.

## 2. État constaté dans le dépôt

### Application Android existante

Les fichiers inspectés confirment une application Android native Kotlin / Jetpack Compose :

- Projet Gradle racine : `settings.gradle.kts`, module `:app`.
- Configuration Android : `app/build.gradle.kts`.
- Point d'entrée : `app/src/main/java/com/example/MainActivity.kt`.
- Identifiant Android : `com.aistudio.montyimmo.kxmpzq`.
- Authentification Firebase Auth et état de session réactif.
- Données cloud via Firebase Firestore.
- Base locale Room (`monty_immo_local.db`) et dépôt acquéreurs local.
- Client HTTP vers le backend FastAPI dans `MontySaasApiClient.kt`.
- App Check, contrôle d'intégrité, protection contre les captures d'écran en release et écran de confidentialité au retour en arrière-plan.
- Parcours professionnels, vendeurs, acquéreurs et financement déjà présents dans l'application.

### Services SaaS déjà présents

Le backend FastAPI expose un service de distribution des versions Android (releases, version latest, téléchargement et administration). Le frontend web possède également un parcours de téléchargement APK.

### État Flutter

Aucun `pubspec.yaml` n'a été retrouvé par la recherche de code GitHub effectuée pendant cet audit. Le dépôt consulté reste organisé autour du projet Android Gradle/Kotlin. Il faut donc considérer que **la migration Flutter n'est pas encore démontrée dans cette branche**.

## 3. Risques à traiter avant toute bascule

| Domaine | Risque | Exigence de migration |
|---|---|---|
| Authentification | Perte de session ou changement d'identité | Reproduire Firebase Auth et l'échange de jeton SaaS sans modifier les comptes |
| Autorisations | Élévation de privilèges ou fuite inter-agences | Conserver les contrôles côté serveur et tester chaque rôle |
| Données cloud | Divergence entre Firestore et PostgreSQL | Documenter les sources de vérité et les règles de synchronisation |
| Données locales | Perte de dossiers hors ligne Room | Export, migration versionnée et test de restauration avant remplacement |
| API | Régression des contrats existants | Réutiliser les endpoints et DTO existants, tests de compatibilité |
| Paiement | Checkout ou vérification incohérents | Conserver le traitement Stripe côté serveur |
| Sécurité mobile | Régression App Check / confidentialité / intégrité | Équivalence de sécurité documentée par plateforme |
| Distribution | Perte de capacité à publier une mise à jour Android | Conserver l'applicationId, la signature et la compatibilité des versions |
| iOS / Web | Fonctionnalités Android indisponibles | Matrice des capacités et solutions de remplacement explicites |

## 4. Architecture cible proposée

- **Flutter** pour l'interface et la logique de présentation partagée.
- **Android, iOS et Web** comme cibles de compilation distinctes.
- Firebase Auth pour l'identité, sous réserve de compatibilité et de configuration par plateforme.
- Backend FastAPI comme frontière métier et de sécurité pour les opérations SaaS.
- PostgreSQL / services SaaS comme source de vérité des données métier qui y sont déjà stockées.
- Firestore uniquement pour les domaines qui en dépendent déjà, jusqu'à décision de convergence documentée.
- Stockage local Flutter avec migrations versionnées et stratégie de synchronisation explicite.
- Adaptateurs par plateforme pour notifications, fichiers, caméra, géolocalisation et partage.
- Secrets exclusivement côté serveur ; aucune clé de service Supabase ou autre secret privé dans l'application.

Ne pas déplacer les règles d'autorisation dans le client mobile. Le client ne constitue jamais une frontière de sécurité.

## 5. Ordre de migration sans perte

1. Créer l'application Flutter dans un répertoire dédié, sans modifier `app/`.
2. Documenter les écrans, parcours, modèles, dépôts et services Kotlin.
3. Définir les contrats partagés API et les tests de compatibilité.
4. Mettre en place l'authentification et l'échange de session sur Flutter.
5. Migrer les parcours en tranches verticales, avec tests Android, iOS et Web.
6. Mettre en place la persistance locale et les migrations de données.
7. Reproduire les contrôles de sécurité et les capacités natives.
8. Comparer les parcours Kotlin et Flutter avec les mêmes jeux de tests.
9. Produire et tester les builds de distribution pour chaque cible.
10. Maintenir Kotlin comme solution de repli jusqu'à validation complète et approbation explicite.

## 6. Conditions de validation (Definition of Done)

La migration ne pourra être déclarée terminée que si :

- [ ] Le projet Flutter est présent et reproductible depuis une installation propre.
- [ ] Android, iOS et Web compilent dans la CI.
- [ ] Les parcours d'authentification, déconnexion et expiration de session sont testés.
- [ ] Les rôles et permissions sont vérifiés côté serveur.
- [ ] Les parcours conseiller, vendeur et acquéreur sont couverts.
- [ ] Les fonctions CRM, dossiers, visites, offres et financement sont comparées à l'existant.
- [ ] Les données locales Kotlin ont une procédure de migration et de restauration prouvée.
- [ ] Les scénarios hors ligne et de reprise réseau sont testés.
- [ ] Les API existantes restent compatibles.
- [ ] Les protections de confidentialité et de sécurité sont validées par plateforme.
- [ ] Les builds de release sont générés et les artefacts vérifiés.
- [ ] Aucun écran ou bouton fictif ne remplace une fonctionnalité existante.
- [ ] L'application Kotlin n'est retirée qu'après validation complète et autorisation explicite.

## 7. Décision de cette étape

**Résultat : audit préparatoire réalisé ; migration non commencée dans ce commit.**

Le dépôt contient une application Android Kotlin fonctionnelle et intégrée à plusieurs services. La migration Flutter doit donc être progressive, parallèle et pilotée par les tests. La suppression de Kotlin, toute migration de données destructive et tout changement de production restent interdits à ce stade.

## 8. Dépendances restantes

- Configuration et validation des identifiants Firebase propres à iOS et Web.
- Vérification des capacités App Check et des contrôles d'intégrité selon chaque plateforme.
- Définition du format de migration des données Room.
- Validation réelle du stockage objet Supabase, suivie séparément dans l'étape de stockage.
