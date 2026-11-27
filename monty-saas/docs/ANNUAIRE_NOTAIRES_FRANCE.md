# MON-AGENT — Annuaire national des notaires

## Périmètre et sources

L'annuaire officiel du Conseil supérieur du notariat (CSN) est la source de référence pour identifier un office et les notaires qui y exercent. Au 27 novembre 2026, la page nationale consultée annonce 7 409 offices et 17 764 notaires. Les fiches détaillées des offices peuvent publier le téléphone, le courriel, le site internet, l'adresse, les langues et les notaires associés.

- Annuaire officiel CSN : https://www.notaires.fr/fr/directory
- Recherche officielle des offices : https://www.notaires.fr/fr/directory/offices
- API Recherche d'entreprises DINUM : https://recherche-entreprises.api.gouv.fr/docs/
- Annuaire régional de la Chambre des notaires de Montpellier : https://cr-montpellier.notaires.fr/annuaire-notaires

## Fonctionnalités présentes dans MON-AGENT

- Recherche nationale des établissements notariaux actifs via DINUM/INSEE (NAF 69.10Z).
- Filtres par nom, ville, département et code postal.
- Pagination de 25 résultats, avec total d'établissements et nombre de pages.
- Identifiants SIREN/SIRET, nom de l'étude, adresse et géolocalisation lorsqu'ils sont fournis par SIRENE.
- Lien vers l'annuaire officiel CSN pour consulter la fiche de contact de l'office.
- Ajout au carnet notaires de l'agence et préparation de transmissions de dossiers.
- Champs courriel et téléphone dans le carnet de l'agence.
- Indicateur de vérification du courriel : un e-mail saisi n'est plus automatiquement déclaré vérifié. L'utilisateur doit confirmer l'avoir contrôlé sur la fiche officielle.
- Persistance PostgreSQL tenant-scoped lorsque la base est configurée ; carnet mémoire uniquement en mode dégradé sans base.

## Limite importante sur les coordonnées

L'API DINUM/INSEE est un registre d'identification d'entreprises et d'établissements : elle renvoie notamment les identifiants et adresses, mais pas les téléphones ni les e-mails de contact des offices. Ces coordonnées doivent être lues sur les fiches publiques du CSN ou d'une chambre notariale, puis enregistrées avec leur source et leur vérification.

MON-AGENT ne génère pas d'adresse e-mail à partir du nom de l'étude, ne déduit pas de numéro de téléphone et ne marque pas automatiquement une coordonnée comme vérifiée. Cette précaution évite les erreurs de destinataire lors de l'envoi de pièces notariales.

## Ce qui reste à faire pour une base nationale totalement enrichie

- Obtenir du CSN ou des chambres notariales un export structuré autorisé des coordonnées, ou un accès API/flux documenté.
- À défaut, réaliser une collecte progressive des fiches officielles dans le respect des conditions d'utilisation, avec contrôle des doublons et date de vérification.
- Ajouter les champs de provenance par coordonnée, date de vérification, statut actif/fermé et historique de changement.
- Mettre en place une actualisation périodique des offices et une file de vérification des coordonnées manquantes.
- Tester l'import en masse, la déduplication et l'isolation multi-agences sur PostgreSQL.

## Règles d'usage

- Les coordonnées professionnelles sont utilisées pour les échanges liés aux dossiers immobiliers et notariaux.
- Respecter les conditions de réutilisation des annuaires et les règles de prospection applicables.
- Ne pas considérer une fiche SIRENE comme preuve qu'un office est encore ouvert au public : vérifier également son statut dans l'annuaire CSN.
- Ne pas transmettre de données personnelles ou de pièces sensibles par e-mail ordinaire sans procédure sécurisée et accord du destinataire.
