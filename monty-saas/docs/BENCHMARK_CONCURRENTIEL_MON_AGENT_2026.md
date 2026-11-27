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
