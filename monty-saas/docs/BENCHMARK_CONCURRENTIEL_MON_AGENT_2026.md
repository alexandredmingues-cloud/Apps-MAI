# MON-AGENT — Benchmark concurrentiel & feuille de route produit (2026)

> Objectif : construire une plateforme immobilière de référence, utile aux particuliers, agents indépendants, agences et réseaux. Ce document distingue les capacités déjà visibles dans le dépôt, les références du marché et les évolutions recommandées. Il ne constitue pas une certification de production.

## 1. Positionnement

**Promesse :** « L'humain avant tout. La technologie au service de l'immobilier. »

MON-AGENT ne doit pas être un portail d'annonces de plus ni un CRM généraliste repeint aux couleurs de l'immobilier. Son avantage doit venir d'un parcours continu et vérifiable :

- Particulier : rechercher, comparer, comprendre le marché, préparer son financement, vendre et suivre son projet dans un espace unique.
- Agent : prospecter, estimer, qualifier, suivre les contacts, rapprocher biens et acquéreurs, organiser les visites et piloter les dossiers.
- Agence / réseau : travailler en multi-équipes et multi-agences, contrôler les accès, mesurer l'activité, automatiser les tâches et personnaliser la marque blanche.
- Administrateur : superviser les tenants, les droits, les intégrations, la sécurité, la facturation et la qualité des données.

## 2. Benchmark des références

| Référence | Force produit observée dans le benchmark | Standard que MON-AGENT doit atteindre |
|---|---|---|
| Leboncoin | Audience et profondeur du catalogue, alertes et recherche grand public | Recherche rapide, annonces fiables, favoris, alertes, dépôt simple |
| SeLoger | Fiches détaillées, filtres, carte et expérience mobile | Recherche cartographique, filtres pertinents, pages de biens rapides |
| Bien'ici | Exploration géographique et visualisation du territoire | Carte fluide, points d'intérêt, quartiers et temps de trajet |
| Hektor | CRM métier, pige, mandats, rapprochement, suivi de transaction | Dossier immobilier complet, traçabilité, suivi des mandats et rapprochement |
| Apimo | CRM, multidiffusion, documents, sites et dimension internationale | Internationalisation réelle, intégrations, publication maîtrisée |
| Modelo (ex-Netty) | Regroupement des outils d'agence et du site | Simplicité d'usage et continuité entre CRM et vitrine |
| HubSpot / Pipedrive | Pipeline commercial, tâches, séquences, automatisations et reporting | Vue 360° des contacts, prochaines actions et automatisations configurables |

### Sources de veille (consultées le 27 novembre 2026)

- Pretto, comparatif des portails immobiliers : https://www.pretto.fr/recherche-immobiliere/trouver-bien-immobilier/meilleur-site-immobilier/
- Freya Suite, état de l'art CRM immobilier 2026 : https://freyasuite.com/blog/etat-de-lart-crm-immobilier-2026
- Hack'celeration, comparatif CRM immobilier : https://www.hackceleration.com/fr/labs/meilleur/crm-immobilier
- Eazyclick, Apimo / Hektor / Modelo / SweepBright : https://www.eazyclick.fr/ressources/blog/logiciel-crm-immobilier
- TS-Immo, critères API et synchronisation : https://www.ts-immo.org/fr/choisir-crm-immobilier-2026

Les chiffres de trafic, de prix et de couverture fonctionnelle publiés par des comparateurs sont indicatifs et doivent être revérifiés auprès des éditeurs avant toute communication commerciale.

## 3. Capacités déjà présentes dans le dépôt

Éléments repérés dans le code source (présence de composants et de parcours, pas validation complète de leur fonctionnement en production) :

- Vitrine publique, recherche et publication d'annonces, carte interactive, filtres de mobilité et comparateur.
- Espace client, espace professionnel, rôles, tenant agence et console d'administration.
- CRM contacts, vendeurs/acquéreurs, demandes web, notes, échanges, tâches de relance et import/export CSV avec protection contre les formules CSV.
- Portefeuille de biens, rapprochement acquéreur/bien, visites, agenda et pipeline de transactions.
- Outils d'estimation DVF, indice de prix, estimation de délai de vente et simulateurs.
- Modules IA, veille, documents, copropriété, conformité et téléchargement Android.
- Client API centralisé et backend FastAPI présent dans le dépôt.

## 4. Écarts et risques à traiter avant de revendiquer le leadership

1. **Données de démonstration :** plusieurs écrans possèdent des jeux de données locaux de démonstration et basculent en mode local lorsque l'API ou la session ne répond pas. L'interface doit distinguer explicitement données réelles, données de démonstration et indisponibilité API. Aucune donnée fictive ne doit être présentée comme une activité réelle.
2. **Persistance :** vérifier chaque parcours de création / modification / suppression contre l'API et PostgreSQL. Une action affichée comme enregistrée doit être confirmée côté serveur.
3. **Multidiffusion :** ne connecter que des flux, API et contrats autorisés par les portails. Pas de scraping contournant les CGU, authentifications ou protections.
4. **Signature et actes :** utiliser un prestataire de signature qualifié/adapté et un circuit documentaire juridiquement validé. Ne pas présenter un simple upload comme une signature électronique conforme.
5. **Estimation :** afficher la date, la source, le nombre de comparables, la zone, les limites et une fourchette de confiance. Une estimation automatique ne remplace pas un avis professionnel.
6. **RGPD :** contrôler les bases légales, consentements, durées de conservation, droits d'accès/export/suppression, journalisation, sous-traitants et cloisonnement des agences.
7. **Marque blanche :** séparer identité globale, thème de tenant, domaines, logos, e-mails, mentions légales et droits. Les données d'une agence ne doivent jamais être visibles par une autre.
8. **Mobile :** tester les usages réels sur petits écrans, réseau lent, reprise hors ligne, notifications et installation PWA/APK.
9. **Observabilité :** ajouter suivi des erreurs, métriques, alertes, sauvegardes testées et procédures de restauration.
10. **Performance et accessibilité :** budgets Core Web Vitals, navigation clavier, contrastes, labels, lecteurs d'écran et réduction des animations.

## 5. Feuille de route priorisée

### P0 — Fiabilité et confiance (bloquant avant lancement commercial)
- Contrat API documenté et tests de contrat frontend/backend.
- Tests automatisés des parcours inscription, connexion, création contact, création bien, demande de visite, publication et transaction.
- Isolation multi-tenant testée avec scénarios d'accès croisé interdits.
- États explicites : connecté, hors ligne, démo, erreur, synchronisation en attente.
- Journal d'audit des opérations sensibles et contrôle des permissions côté serveur.
- Sauvegardes PostgreSQL, restauration testée, secrets hors dépôt et rotation.
- Suppression des exemples de données personnelles réalistes des environnements de démonstration.

### P1 — Avantage métier mesurable
- Fiche contact 360° : échanges, consentements, biens, visites, offres, documents et prochaine action.
- Pipeline visuel personnalisable : prospection → estimation → mandat → commercialisation → offre → compromis → acte.
- Matching explicable : critères satisfaits/non satisfaits, score justifié, alertes et historique.
- Relances multicanales avec consentement, plages horaires, modèles et désinscription.
- Agenda unifié et synchronisation calendrier via intégrations autorisées.
- Rapport vendeur automatisé : vues, contacts, visites, retours, concurrence et recommandation d'action.
- Estimation documentée avec comparables vérifiables et export PDF.

### P2 — Expérience grand public
- Recherche sauvegardée, alertes configurables et détection de doublons.
- Carte avec temps de trajet réellement calculés par fournisseur identifié, date de calcul et solution de repli.
- Comparaison de biens, partage privé et parcours de visite.
- Espace vendeur avec checklist, documents, étapes, historique et messagerie.
- Transparence des annonces : source, date de mise à jour, statut de disponibilité et modération.

### P3 — Plateforme et écosystème
- API publique versionnée, webhooks signés et documentation OpenAPI.
- Connecteurs CRM, e-mail, calendrier, signature, portails et comptabilité.
- Marque blanche par tenant avec domaine personnalisé et configuration guidée.
- Modules locatifs, copropriété, neuf, viager et immobilier international.
- Marketplace d'intégrations avec permissions granulaires.
- Analytique réseau : conversion, délais, stock, performance par équipe et qualité de service.

## 6. Principes d'interface

- Une action principale claire par écran ; les actions secondaires restent visibles sans encombrer.
- Navigation stable, recherche globale, raccourcis clavier et commandes rapides.
- Tableaux filtrables, colonnes configurables, vues enregistrées et actions groupées.
- Fiches synthétiques avec détails progressifs, historique et prochaine action.
- Mobile-first pour les agents en déplacement et espace client très lisible.
- Design system unique : couleurs, typographies, espacements, états, composants et accessibilité.
- Pas de chiffres de performance fictifs, de faux avis, de faux stocks ou de badges « IA » sans fonctionnalité effective.

## 7. Critères de sortie avant ouverture au public

- [ ] Build production et vérification TypeScript réussis sur le commit candidat.
- [ ] Tests backend, frontend et Android réussis.
- [ ] Parcours critiques testés de bout en bout avec base de test.
- [ ] Cloisonnement multi-tenant et permissions vérifiés.
- [ ] Aucune clé secrète ou donnée personnelle de test dans le dépôt public.
- [ ] États démo/API clairement identifiables.
- [ ] Sauvegarde et restauration validées.
- [ ] Mentions légales, politique de confidentialité, cookies et contrats partenaires validés.
- [ ] Mesure des performances et accessibilité effectuée.
- [ ] Déploiement progressif, monitoring et procédure de retour arrière prêts.

**Règle produit :** ne jamais confondre une fonctionnalité représentée dans l'interface, une fonctionnalité codée, une fonctionnalité testée et une fonctionnalité effectivement disponible en production.


## 8. Benchmark étendu — portails, outils de veille et logiciels métier

Relevé de veille du 27 novembre 2026. Il s'agit d'une analyse fonctionnelle des promesses et parcours publics, pas d'un audit interne des produits concurrents. Les offres, tarifs, données et conditions d'accès peuvent évoluer.

### 8.1 Particuliers : portails et agrégateurs

| Acteur | Force à retenir | Réponse MON-AGENT |
|---|---|---|
| Leboncoin | Profondeur d'audience, particuliers et professionnels, alertes et volume d'offres | Recherche unifiée, annonces qualifiées, anti-doublons et alertes réellement actives |
| SeLoger | Filtres détaillés, annonces d'agences et de particuliers, expérience mobile | Filtres métier complets, transparence honoraires, disponibilité et historique |
| Bien'ici | Exploration cartographique et contexte géographique | Carte enrichie, quartiers, transports, commerces et temps de trajet sourcés |
| PAP | Vente et location entre particuliers, contact direct et pédagogie vendeur | Parcours vendeur autonome, estimation argumentée, documents et messagerie sécurisée |
| Logic-Immo / Figaro Immobilier / ParuVendu | Sources complémentaires et couverture de segments différents | Connecteurs uniquement autorisés, normalisation et déduplication |
| Jinka | Agrégation multi-sites, alertes et rapidité de détection | Alertes configurables, traçabilité de la source et suivi de chaque bien |
| Immobilier.notaires.fr | Annonces notariales et contexte transactionnel | Données notariales clairement identifiées et liens vers sources officielles |

### 8.2 Veille, pige et intelligence immobilière

| Acteur | Fonction différenciante | Fonction cible MON-AGENT |
|---|---|---|
| Castorus | Historique du bien au-delà d'une URL, variations de prix, annonces rapprochées entre agences et alertes | **Chronologie du bien** : prix initial, baisses, republications, agences observées, durée de commercialisation et sources |
| La Loupe Immo Transaction | Carte de prospection, DVF, DPE, PLU, annonces, invendus, analyse flash et avis de valeur | **Carte d'opportunités** reliée au CRM : parcelles/secteurs, ventes, DPE, annonces et actions de prospection |
| La Loupe Immo Location | Carte des loyers, prospection bailleurs et analyse du marché locatif | **Radar bailleurs** : tension locative, loyers observés, rendement indicatif et portefeuille de gestion |
| Loupy (option IA) | Courriers, e-mails, retouche photo, home staging, adresses à potentiel et suggestions de relance | IA métier qui agit sur les dossiers autorisés, cite ses données et demande validation avant envoi |
| Lybox | Recherche orientée investisseurs, rentabilité, fiscalité, loyers et analyse de secteur | **Investisseur 360°** : rendement brut/net, cash-flow, financement, travaux, fiscalité paramétrable et risques |
| Moteur Immo / Melo | Recherche multi-sources, alertes et possibilités d'intégration selon l'offre | Connecteurs et fournisseurs interchangeables, API documentée, webhooks et coûts maîtrisés |
| MeilleursAgents / DVF | Estimation, prix au m² et transactions observées | Fourchette expliquée, comparables datés, qualité de l'échantillon et limites explicites |

### 8.3 Professionnels : CRM et logiciels d'agence

| Acteur | Force métier | Réponse MON-AGENT |
|---|---|---|
| Hektor | CRM transaction, mandats, biens, rapprochement et suivi d'agence | Dossier 360° avec pipeline personnalisable, historique et prochaines actions |
| Apimo | CRM, multidiffusion et dimension internationale | Multi-pays, langues, devises, flux partenaires et gouvernance des données |
| Modelo (ex-Netty) | Outils regroupés pour agents, agences et gestionnaires | Un espace métier cohérent et modulaire, sans multiplier les logiciels |
| Immo-Facile / Adapt Immo | Gestion transaction/location et processus métier | Parcours complets vente, location, gestion et suivi documentaire |
| Infoparcelle | Prospection foncière, données cadastrales/DVF/DPE et outils de mandat | Prospection cartographique reliée à la fiche contact, au mandat et au reporting |
| HubSpot / Pipedrive / Zoho | Pipelines, automatisations, tâches et reporting généralistes | Automatisations immobilières natives, avec déclencheurs, consentements et journal d'exécution |

### 8.4 Synthèse des écarts stratégiques

Les concurrents sont souvent très bons sur une tranche du parcours : audience d'annonces, historique de prix, prospection géographique, CRM ou rentabilité d'investissement. MON-AGENT doit se différencier par la continuité et la qualité des données, pas par l'accumulation de widgets.

**Objectif de produit : une fiche unique du bien et une fiche unique du projet**, partagées avec les droits adaptés entre particulier, conseiller et agence :
- le bien garde son identité malgré les changements d'annonce, d'agence ou de prix ;
- chaque observation conserve source, date, confiance et preuve ;
- chaque prospect et chaque mandat sont reliés aux biens et actions utiles ;
- chaque recommandation IA expose ses critères et reste validable par un humain ;
- les données personnelles et notes internes restent cloisonnées par rôle et tenant.

## 9. Fonctionnalités à développer — spécification opérationnelle

### P0 — Corriger la confiance et les états de synchronisation
- Supprimer tout faux prospect affiché comme contact réel en mode local.
- Distinguer « recherche enregistrée sur cet appareil », « demande reçue par le serveur » et « alerte automatique active ».
- Ne jamais afficher une confirmation d'enregistrement après échec API.
- Afficher la date de dernière synchronisation, les erreurs, les tentatives et l'état hors ligne.
- Ajouter des tests de non-régression sur ces états.

### P1 — Property Timeline (inspiré de Castorus, mais relié au CRM)
- Identifiant interne stable du bien, distinct de l'identifiant de chaque annonce.
- Rapprochement probabiliste de doublons sur adresse normalisée, géolocalisation approximative, surface, pièces, photos perceptuelles et caractéristiques ; validation manuelle si confiance faible.
- Événements historisés : première observation, nouvelle agence, changement de prix, retrait, republication, statut et source.
- Graphique prix demandé / prix au m² / durée observée, avec distinction stricte entre prix affiché et prix de vente DVF.
- Alertes de baisse, nouvelle publication, réapparition et changement d'agence.
- Historique exportable dans le dossier vendeur/acquéreur, avec provenance et date.
- Aucune collecte qui contourne authentification, CAPTCHA, mesures techniques ou conditions d'utilisation. Sources autorisées : API/flux sous contrat, jeux ouverts, imports de l'utilisateur et données partagées avec base légale documentée.

### P1 — Carte de prospection (inspirée de La Loupe Immo)
- Couches activables : DVF, DPE, cadastre/parcelles lorsque la licence le permet, PLU/zonage, annonces autorisées, biens du portefeuille et contacts CRM.
- Filtres par date, typologie, prix/m², surface, DPE, ancienneté, statut et distance.
- Détection de signaux : écart au marché, baisse de prix, annonce ancienne, DPE F/G, ventes récentes, potentiel locatif et biens sans suivi CRM.
- Clic sur une zone ou un bien : fiche source, historique, estimation indicative et action CRM (créer une tâche, ajouter une note, préparer un courrier).
- Export des secteurs et tournées de prospection ; journaliser les actions et respecter les oppositions et règles de prospection.

### P1 — Investisseur 360° (inspiré de Lybox)
- Calculs brut/net, cash-flow mensuel, coût total d'acquisition, frais, travaux, vacance, charges, fiscalité paramétrable et financement.
- Scénarios prudent / central / optimiste ; taux, loyer et coûts modifiables.
- Analyse DPE, travaux énergétiques, risques réglementaires et tension locative, avec source et date.
- Comparaison de plusieurs biens et sauvegarde des hypothèses.
- Aucune promesse de rendement ; afficher hypothèses, incertitudes et avertissements.

### P1 — CRM métier augmenté
- Fiche 360° contact, ménage, société, bien, mandat et transaction ; détection de doublons et fusion auditée.
- Pipeline distincts vente, location, gestion et investissement, personnalisables par agence.
- Règles d'automatisation : déclencheur, condition, action, délai, consentement, arrêt et historique.
- Relances e-mail/SMS/WhatsApp uniquement via fournisseurs autorisés, consentements et désinscription.
- Rapport vendeur périodique alimenté par les événements réels : vues disponibles, demandes, visites, retours, concurrence observée et recommandations.
- Matching avec score explicable, critères bloquants et justification en langage clair.

### P2 — Portail particulier et vendeur
- Espace projet achat/vente/location, checklist, documents, rendez-vous, messagerie et suivi des étapes.
- Comparateur de biens, favoris partagés, historique de prix et coût total d'acquisition.
- Estimation vendeur avec comparables, fourchette, confiance, date et demande de rendez-vous.
- Consentements granulaires, export, rectification et suppression conformément aux obligations applicables.

### P2 — Marque blanche et intégrations
- Personnalisation par agence : nom, logo, couleurs, domaine, langues, modèles, mentions légales et coordonnées.
- API versionnée, webhooks signés, clés par tenant, quotas et journalisation.
- Connecteurs autorisés pour portails, calendrier, e-mail, téléphonie, signature et comptabilité.
- Import/export contrôlé et stratégie de sortie des données pour éviter l'enfermement.

## 10. Mesure du niveau de maturité

Une fonctionnalité ne passe à l'état « terminée » que si :
1. le parcours existe dans l'interface ;
2. les API et permissions sont implémentées côté serveur ;
3. les données persistent et sont cloisonnées ;
4. les tests automatisés passent ;
5. les états d'erreur, hors ligne et synchronisation sont traités ;
6. la fonctionnalité a été vérifiée sur un environnement de recette ;
7. les sources, licences, consentements et limites sont documentés.

### Sources de veille complémentaires

- Castorus : https://www.castorus.com/ et https://www.castorus.com/faq
- La Loupe Immo : https://la-loupe.immo/
- Loupy / Option IA : https://la-loupe.immo/loupe-immo/option-ia/
- Comparatif portails 2026 : https://www.pretto.fr/recherche-immobiliere/trouver-bien-immobilier/meilleur-site-immobilier/
- Comparatif CRM 2026 : https://www.infoparcelle.fr/comparatif/crm-immobilier
- Agrégateurs et outils de recherche : https://regroupe-immo.fr/comparatif
- Lybox : https://lybox.fr/
- DVF : https://www.data.gouv.fr/fr/datasets/demandes-de-valeurs-foncieres/
