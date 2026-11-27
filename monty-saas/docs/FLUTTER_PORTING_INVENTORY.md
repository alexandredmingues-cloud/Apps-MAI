# Inventaire de portage Kotlin → Flutter — MON-AGENT

- Date de vérification : 2026-11-27
- Source : branche `main` du dépôt `alexandredmingues-cloud/Apps-MAI`
- Branche de travail Flutter : `migration/flutter-multiplatform-2026-11`
- Périmètre observé : 57 fichiers Kotlin (`.kt`), tests inclus.
- Statut global : **sources récupérées et inventoriées ; portage métier Flutter non réalisé**.

> Cet inventaire est un point de départ basé sur les fichiers réellement présents. Il ne prouve ni le fonctionnement de chaque écran ni la parité avec le backend. Les fichiers Kotlin restent la référence jusqu'à validation des équivalents Flutter.

## Sources Android identifiées

### Application et navigation
- `app/src/main/java/com/example/MainActivity.kt` — point d'entrée Compose et assemblage de l'application.
- `app/src/main/java/com/example/ui/MontyViewModel.kt` — état principal et orchestration.
- `app/src/main/java/com/example/ui/auth/AuthScreen.kt` — authentification.
- `app/src/main/java/com/example/ui/theme/Color.kt`, `Theme.kt`, `Type.kt` — identité visuelle.

### Données, modèles et persistance
- `data/model/MontyModels.kt` — espaces, navigation, dossiers clients, biens et objets métier.
- `data/model/SaasModels.kt` — agences, rôles, membres, CRM, échanges et tâches.
- `data/model/SellerModels.kt` — documents vendeurs, copropriété/ALUR, critères DVF et estimations.
- `data/MontyRepository.kt`, `data/SaasAgencyRepository.kt`, `data/SellerRepository.kt` — dépôts métier.
- `data/local/AcquereurDao.kt`, `AcquereurEntity.kt`, `AcquereurRepository.kt`, `MontyLocalDatabase.kt` — persistance Room locale.
- `data/remote/MontySaasApiClient.kt` — client de l'API SaaS.
- `data/SeedData.kt` — données de référence et moteurs associés.

### Services métier
- `data/DvfValuationService.kt` — estimation DVF et références de marché.
- `data/SaleTimelineEngine.kt` — délai de vente.
- `data/GeoCommuteService.kt` — géolocalisation et temps de trajet.
- `data/UrbanismCadastreService.kt` — cadastre, PLU, servitudes et risques.
- `data/RealEstateMarketBriefing.kt` — synthèse du marché.
- `data/ExcelCsvCodec.kt` — import/export et protection des données tabulaires.
- `data/MontyAiService.kt` — assistant IA, scoring et rédaction.
- `data/SellerDocumentVault.kt` — coffre documentaire.

### Sécurité et Firebase
- `firebase/AppCheckInit.kt`, `firebase/FirestoreErrorHandler.kt`.
- `security/AppSecurityManager.kt`, `security/RgpdComplianceManager.kt`.
- À conserver et analyser conjointement : `firestore.rules`, `firestore.test.js`, `firebase.json`, `firebase-blueprint.json`.
- Ne jamais déplacer de configuration privée, jeton, secret ou clé de signature vers Flutter ou dans Git.

### Interfaces métier Compose
- Conseiller : `ui/advisor/AcquereursScreen.kt`, `AcquereursViewModel.kt`, `AdvisorAnalyticsAndAiTabs.kt`, `AdvisorDashboardAndClientsTabs.kt`, `AdvisorMapUrbanismTab.kt`, `AdvisorModals.kt`, `AdvisorPortfolioTabs.kt`, `AdvisorWorkspaceScreen.kt`.
- Acheteur : `ui/buyer/BuyerDocumentVaultDialogs.kt`, `BuyerDossierAndAiTab.kt`, `BuyerPortalScreen.kt`, `BuyerPropertyDetailDialog.kt`.
- Agence / SaaS : `ui/saas/AgencyWorkspaceTab.kt`, `CrmContactModals.kt`, `CrmContactsAndTasksTab.kt`, `SaasViewModel.kt`.
- Vendeur : `ui/seller/SellerListingAndMandateSection.kt`, `SellerModals.kt`, `SellerViewModel.kt`, `SellerWorkspaceScreen.kt`.
- Financement : `ui/finance/HcsfCapacityScreen.kt`.
- Composants partagés : `ui/components/AiAutoRelevanceComponents.kt`, `MontyCommonComponents.kt`, `RealEstateHomeInfoSection.kt`.

### Tests Kotlin présents
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- `app/src/test/java/com/example/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`

Les noms de ces tests ne suffisent pas à établir une couverture métier. Leur contenu et leurs assertions doivent être examinés et complétés par des tests de caractérisation.

## Matrice de migration

| Domaine | Sources de référence | Cible Flutter | État |
|---|---|---|---|
| Authentification et session | AuthScreen, AppCheckInit, repositories | `features/auth` | À caractériser |
| Espaces conseiller/vendeur/acheteur | MainActivity, ViewModels, écrans Compose | `features/workspaces` | À caractériser |
| CRM et multi-agences | SaasModels, SaasAgencyRepository, SaaS UI, Firestore rules | `features/crm`, `features/agencies` | À caractériser |
| Acquéreurs et matching | MontyModels, Acquereur*, SeedData, GeoCommuteService | `features/buyers`, `features/matching` | À caractériser |
| Biens, mandats, visites, offres | MontyModels, MontyRepository, AdvisorPortfolioTabs | `features/properties`, `features/transactions` | À caractériser |
| Espace vendeur et ALUR | SellerModels, SellerRepository, SellerDocumentVault | `features/seller`, `features/documents` | À caractériser |
| Estimation et délai de vente | DvfValuationService, SaleTimelineEngine | `features/valuation` | À caractériser |
| Financement HCSF | SeedData, HcsfCapacityScreen | `features/finance` | À caractériser |
| Carte, PLU, cadastre, risques | UrbanismCadastreService, GeoCommuteService | `features/maps` | À caractériser |
| IA | MontyAiService, écrans IA | `features/assistant` | À caractériser |
| Import/export | ExcelCsvCodec | `features/import_export` | À caractériser |
| RGPD et sécurité | AppSecurityManager, RgpdComplianceManager, rules, tests | `core/security`, API serveur | À caractériser |
| Marché immobilier | RealEstateMarketBriefing | `features/market` | À caractériser |

## Ordre de portage et preuves obligatoires

1. **Contrats et sécurité** : comparer les DTO Kotlin, modèles FastAPI/Pydantic, schéma PostgreSQL/Alembic et règles Firestore. Aucune hypothèse de correspondance automatique.
2. **Socle Flutter** : configuration par environnement, navigation adaptative, erreurs réseau, authentification, stockage sécurisé mobile et gestion de session. Les secrets privilégiés restent côté serveur.
3. **CRM et agences** : CRUD, affectations, rôles et isolation inter-agences vérifiés côté serveur.
4. **Acquéreurs, biens et transactions** : critères, matching déterministe, visites, offres, persistance et synchronisation.
5. **Vendeur et documents** : checklist ALUR, pièces, autorisations, stockage objet et suppression RGPD.
6. **Moteurs métier** : DVF, délai de vente, HCSF et géotraitement comparés à des cas de référence.
7. **Carte, IA, import/export, marché, notifications et conformité**.
8. **Recette Android/iOS/Web** : tests unitaires, widget, intégration, end-to-end, accessibilité, réseau dégradé et sécurité.

Pour chaque lot : test de caractérisation de la source, test Dart équivalent, test API/persistance, test d'autorisations, preuve CI et revue manuelle. La parité n'est déclarée qu'après comparaison des résultats et validation du parcours.

## Garde-fous

- Aucun fichier Kotlin, Firebase, Room ou workflow Android supprimé pendant le portage.
- Aucune migration de données réelles lancée depuis la CI.
- Aucun double-écriture Firebase/PostgreSQL non spécifiée, instrumentée et réconciliable.
- Sauvegarde, dry-run, idempotence, journal de migration et rollback exigés avant toute migration de données.
- Aucun merge dans `main`, déploiement production ou retrait de Kotlin sans autorisation explicite.
