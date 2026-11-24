# MON AGENT V3 — annonces, délai de vente et marketplace

## Fonctionnalités livrées dans le module `mon_agent_v3_annonces.js`

- `estimateSaleTime(property, market)` : fourchette de délai de vente basée sur une médiane locale fournie et des facteurs explicables (prix vs estimation, état, DPE, photos, extérieur, stationnement, étage, localisation, documents, diffusion, saisonnalité).
- `draftPropertyListing(property, photoObservations)` : première rédaction structurée à partir des données du bien et des observations photo saisies/validées.
- `generateListingWithAI(...)` : connecteur vers un endpoint serveur facultatif pour vraie génération IA et analyse photo. La clé du modèle doit rester côté serveur.
- `validateListingForPublication(listing)` : contrôles avant soumission.
- `searchMarketplaceListings(listings, filters)` : catalogue acheteur unique qui accepte les sources `particulier` et `agence`.
- `createListingRecord(form, user)` : création d'un brouillon et passage en vérification avant publication.

## Intégration dans l'application React

1. Copier `mon_agent_v3_annonces.js` dans `src/features/listings/`.
2. Importer les fonctions nécessaires depuis les écrans vendeur, professionnel et acquéreur.
3. Ajouter un écran vendeur « Publier une annonce » avec formulaire, upload photo, prévisualisation, génération de texte et case de validation des informations.
4. Ajouter une file de modération : les annonces passent en `a_verifier`, puis un rôle autorisé les publie (`publiee`). Les annonces non validées ne doivent jamais apparaître dans la recherche publique.
5. Ajouter au portail acheteur un filtre « Particuliers / Agences / Tous » ; par défaut « Tous ».
6. Stocker les annonces et photos dans une base et un stockage privés/publics séparés, avec contrôle d'accès serveur. Ne pas utiliser uniquement `localStorage`.

## Données marché nécessaires au délai de vente

Fournir `marketMedianDays`, `sampleSize` et `updatedAt` par micro-secteur + type de bien + tranche de prix. Calculer la médiane à partir de dates réelles de mise en vente et de compromis/vente, en excluant les annonces retirées ou toujours actives du calcul du délai vendu. Si l'échantillon est insuffisant, afficher « données insuffisantes » au lieu d'inventer un délai. Les coefficients actuels sont des hypothèses de départ à recalibrer sur les résultats réels de l'agence.

## Limites et sécurité

- Le module n'effectue pas à lui seul de publication web, de géocodage, d'upload persistant ni d'analyse réelle de photos. Il prépare les données et les validations côté interface ; un backend est nécessaire.
- Les observations visuelles générées par IA doivent être confirmées par le vendeur. L'IA ne doit jamais inventer une vue, une exposition, un état, une surface ou un équipement.
- Pour les annonces d'agence, prévoir preuve de mandat/autorisation de diffusion et identité professionnelle. Pour les particuliers, preuve de propriété ou qualité à publier et acceptation des conditions.
- Masquer l'adresse exacte des particuliers par défaut ; afficher le quartier et une position approximative. Protéger coordonnées et documents.
- Prévoir signalement d'annonce, modération, détection des doublons, expiration, retrait, statut vendu et procédure de contestation.
- Vérifier les obligations légales françaises applicables aux annonces immobilières (prix honoraires inclus ou non, DPE, mentions réglementaires, diagnostics, statut du diffuseur) avant mise en production.

## Attention à l'état du fichier source

Le fichier `ai_studio_code.txt` fourni dans la conversation se termine au milieu d'un champ JSX de formulaire. Le module ci-dessus est autonome et ne peut pas être présenté comme déjà intégré dans l'application complète. Il faut récupérer le projet complet (ZIP ou dépôt) pour effectuer une fusion, corriger les imports et lancer un build réel.
