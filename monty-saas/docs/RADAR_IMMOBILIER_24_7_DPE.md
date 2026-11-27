# MON-AGENT — Radar immobilier 24/7 et DPE

## Objectif produit

Construire un radar immobilier continu, multi-sources et multi-agences, qui collecte uniquement des annonces dont la source autorise explicitement la réutilisation, puis enrichit les biens avec les diagnostics ADEME disponibles. Le service doit rester transparent sur la provenance, la fraîcheur, les droits et le niveau de rapprochement.

## État vérifié dans le code au 27 novembre 2026

### Déjà présent ou ajouté sur la branche

- Flux partenaires XML, JSON et JSON-LD avec accord explicite de diffusion.
- Routes de gestion des flux : `GET/POST /api/v1/marketplace/feeds` et `POST /api/v1/marketplace/feeds/{feed_id}/sync`.
- Stockage PostgreSQL des flux et des références externes dans les migrations/repositories existants.
- Import avec déduplication par identifiant source, modération et nettoyage des coordonnées personnelles dans les descriptions.
- Suppression du flux de démonstration fictif et des valeurs par défaut trompeuses (ville, DPE, état, photos). Une annonce incomplète est rejetée au lieu d'être inventée.
- Vérification de `robots.txt` en mode fermé en cas d'incertitude réseau.
- Service ADEME raccordé au jeu officiel `dpe03existant` (logements existants depuis juillet 2021), avec cache mémoire et extraction des champs énergie/GES.
- Nouvelle route authentifiée `GET /api/v1/marketplace/dpe/lookup`, paramètres : `postal_code`, `city`, `dpe_number`, `limit`.
- Service IGN API Carto raccordé pour les parcelles cadastrales (référence, section, numéro, commune, contenance et géométrie).
- Couches Géoportail de l'Urbanisme : zonage PLU/PLUi, prescriptions surfaciques/linéaires/ponctuelles, servitudes d'utilité publique et documents d'urbanisme.
- Couche Géorisques interrogée par coordonnées GPS.
- Nouvelle route authentifiée `GET /api/v1/marketplace/cadastre/lookup?latitude=...&longitude=...` avec état de disponibilité par couche, avertissements et réserve juridique.

### Pas encore activé / non démontré

- Aucune synchronisation automatique permanente des flux n'est actuellement configurée. La synchronisation partenaire est déclenchée par la route manuelle existante.
- Aucun connecteur contractuel SeLoger, Leboncoin, Bien'ici, PAP ou autre portail n'est activé par défaut.
- Le cache DPE actuel est en mémoire et ne constitue pas un entrepôt national durable.
- Le rapprochement automatique entre annonce et DPE n'est pas encore implémenté comme une liaison certifiée.
- Le rapprochement automatique des annonces avec les parcelles n'est pas encore enregistré dans un dossier de bien : la recherche cadastrale fonctionne par coordonnées et renvoie les parcelles candidates.
- La recherche cadastrale/urbanisme est exposée par API ; l'écran web MON-AGENT n'est pas encore raccordé à cette route. L'application Android contient déjà un écran cartographique/urbanisme utilisant ces couches, à tester sur la dernière version.
- Aucun système d'alertes temps réel (baisse de prix, nouvelle annonce, republication, retrait) n'est déclaré opérationnel.

## Architecture cible du service continu

1. **Worker planifié indépendant de l'API web** : exécution toutes les 10 à 15 minutes, redémarrage automatique, verrouillage distribué, reprise après incident et arrêt propre.
2. **File des sources autorisées** : chaque source possède un propriétaire/tenant, un contrat ou consentement documenté, un format, une fréquence, un statut, une date de prochaine synchronisation et un plafond de requêtes.
3. **Connecteurs officiels** : API partenaires, flux XML/JSON/JSON-LD fournis par les agences, exports autorisés et intégrations contractuelles. Aucune extraction de pages derrière connexion, CAPTCHA, paywall ou mécanisme anti-robot.
4. **Pipeline d'annonces** : collecte, validation de schéma, normalisation, déduplication multi-source, résolution de l'identité du bien, historique des changements, statut actif/retiré, provenance et URL source.
5. **Cadastre et urbanisme** : associer le point géocodé à la ou aux parcelles candidates, conserver la référence cadastrale et la géométrie, puis enrichir avec zonage PLU/PLUi, prescriptions, SUP et Géorisques. Ne jamais déduire une constructibilité de la seule présence d'une zone.
6. **Pipeline DPE ADEME** : interrogation par numéro DPE, adresse BAN ou localisation suffisamment précise ; stockage du numéro, date, étiquettes, consommations, GES, validité et date de dernière mise à jour.
7. **Rapprochement prudent** : priorité à l'identifiant BAN et au numéro DPE ; sinon adresse normalisée + bâtiment + surface/type. Un score faible ou plusieurs candidats doivent produire « correspondance à vérifier », jamais un DPE présenté comme certain.
8. **Alertes** : nouvelles annonces, prix modifié, annonce republiée, bien retiré, DPE trouvé/modifié/expirant, passoire énergétique. Alertes tenant-scoped et désactivables.
9. **Observabilité** : dernier succès, dernier échec, durée, nombre récupéré/dédupliqué/rejeté, fraîcheur par source, quota et alertes de panne.

## Règles de données et de conformité

- Les annonces restent attribuées à leur source ; ne pas recopier photos, descriptions ou coordonnées hors du périmètre contractuel.
- Conserver les URLs canoniques et les dates de collecte ; afficher la provenance aux utilisateurs.
- Respecter les licences open data et les conditions propres à chaque fournisseur.
- La contenance cadastrale est une information foncière, distincte de la surface habitable et d'un mesurage de géomètre. Le PLU, les prescriptions et les servitudes doivent être vérifiés dans les documents opposables auprès de la collectivité compétente.
- Le DPE ADEME est une donnée brute : afficher le numéro et la date, vérifier la validité, et signaler les diagnostics absents ou incohérents. La base ne couvre pas tous les logements.
- Ne pas inférer une classe DPE manquante à partir de l'âge, du quartier ou d'un logement voisin.
- Cloisonnement agence/tenant, contrôle des droits, journal d'audit, limitation de débit et protection SSRF sur les URLs de flux.
- Les sources en erreur ne doivent jamais être présentées comme « synchronisées ».

## Conditions de mise en service 24/7

- Déployer un worker/cron indépendant dans l'infrastructure d'hébergement.
- Ajouter les colonnes de planification et de statut nécessaires, un verrou distribué et des migrations réversibles.
- Configurer des secrets d'exploitation et un mécanisme d'authentification machine-à-machine.
- Ajouter tests unitaires, tests PostgreSQL/RLS, tests d'intégration source, tests de reprise et tests anti-SSRF/redirect.
- Valider les contrats et clés d'API de chaque portail avant d'activer son connecteur.
- Vérifier les journaux et la fraîcheur réelle en environnement de staging avant toute activation en production.

## Critères d'acceptation

- Un flux autorisé est collecté à la fréquence configurée même si aucun utilisateur n'est connecté.
- Un arrêt/redémarrage du worker ne provoque ni perte ni doublon de données.
- Une annonce multi-publiée est regroupée sans effacer les sources ni l'historique des prix.
- Chaque DPE relié à un bien indique sa provenance, son numéro, sa date et la confiance du rapprochement.
- Une erreur de source, un refus robots.txt ou une limite API est visible et n'est jamais masqué.
- Les données d'une agence ne sont jamais visibles par une autre agence sans partage autorisé.

## Sources officielles de référence

- ADEME — API DPE logements existants : https://www.data.gouv.fr/dataservices/dpe-logements-existants-depuis-juillet-2021
- ADEME — Observatoire DPE-AUDIT : https://observatoire-dpe-audit.ademe.fr/
- SeLoger — Conditions générales et opposition explicite à l'extraction automatisée : https://www.seloger.com/Conditions_Generales_d_Utilisation.html
- LeBoncoin — Règles de référencement des annonces : https://www.leboncoin.fr/dc/rules
- IGN — API Carto : https://apicarto.ign.fr/
- Géoportail de l'Urbanisme : https://www.geoportail-urbanisme.gouv.fr/
- Géorisques : https://www.georisques.gouv.fr/
- Cadastre : https://www.cadastre.gouv.fr/
- DVF — Demandes de valeurs foncières : https://app.dvf.etalab.gouv.fr/faq.html
