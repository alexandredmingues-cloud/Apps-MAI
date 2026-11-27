# MON-AGENT — recette de préproduction et critères de mise en service

Ce document distingue les contrôles exécutés automatiquement des validations qui nécessitent les secrets réels, le domaine de préproduction ou un appareil physique. Une étape non exécutée ne doit jamais être déclarée validée.

## 1. Contrôles automatisés dans GitHub Actions

La workflow `.github/workflows/monty-ci.yml` exécute :

- Frontend : installation verrouillée, TypeScript, build Next.js et contrôle HTTP de la page publique.
- Backend : compilation Python, suite pytest complète et migrations Alembic sur PostgreSQL 16 vierge.
- Sécurité : matrice RBAC (particulier, agent, admin, directeur, super-admin), isolation inter-agences et notes privées.
- Firestore : tests des règles avec l'émulateur, notamment accès anonyme, séparation des utilisateurs et isolation multi-agences.
- Données : sauvegarde PostgreSQL, contrôle SHA-256, restauration dans une base isolée et vérification d'une donnée témoin.
- Performance de base : 80 appels concurrents à `/api/v1/health`, avec mesure p50/p95. Ce test CI n'est pas un benchmark de charge de production.
- Android : compilation debug, tests unitaires et assemblage release minifié avec une clé CI jetable. Cette clé ne doit jamais servir à publier sur Google Play.

## 2. Variables obligatoires sur Render

Renseigner les variables marquées `sync: false` dans le tableau de bord Render, séparément en préproduction et production :

### Backend
- `DATABASE_URL`
- `REDIS_URL`
- `ALLOWED_ORIGINS` : origines exactes du frontend, séparées par des virgules.
- `JWT_SECRET` : secret pérenne d'au moins 32 caractères.
- `TURNSTILE_SECRET_KEY` : clé secrète Cloudflare, côté backend uniquement.
- `TURNSTILE_ALLOWED_HOSTNAMES` : hôtes exacts, sans protocole ni joker (ex. `staging.mon-agent.fr,mon-agent.fr,www.mon-agent.fr`).
- Les clés de fournisseurs réellement utilisés (paiement, IA, stockage, messagerie).

### Frontend
- `NEXT_PUBLIC_API_BASE_URL` : URL HTTPS de l'API.
- `NEXT_PUBLIC_TURNSTILE_SITE_KEY` : clé publique du widget Cloudflare.

Ne jamais placer la clé secrète Turnstile, les mots de passe ou le keystore Android dans le frontend ou dans Git.

## 3. Recette Cloudflare Turnstile réelle

À effectuer sur le domaine de préproduction avec un widget Cloudflare dédié à ce domaine :

1. Ouvrir les pages de connexion, inscription agence et demande publicitaire ; vérifier que le widget est rendu.
2. Soumettre sans validation : l'opération doit être refusée côté interface et API.
3. Valider le widget : le jeton doit être envoyé à l'API et accepté uniquement pour le hostname autorisé.
4. Réutiliser un jeton, utiliser un jeton expiré ou invalide : refus attendu.
5. Tester un hostname non autorisé : refus attendu.
6. Simuler l'indisponibilité de Cloudflare : réponse temporaire 503, sans contournement.
7. Vérifier les journaux : aucun jeton ni secret ne doit apparaître.

Utiliser des clés de test en préproduction et des clés distinctes en production. La recette réelle reste en attente tant que les clés Cloudflare actives et le domaine de préproduction ne sont pas configurés.

## 4. Parcours fonctionnels à valider sur préproduction

### Visiteur et particulier
- Ouvrir la page publique, naviguer dans les annonces et les outils accessibles sans compte.
- Créer un compte acheteur, se déconnecter puis se reconnecter.
- Compléter et enregistrer le profil, les critères de recherche et les informations du projet.
- Vérifier que le compte ne peut ni consulter le CRM agence, ni accéder aux données d'un autre particulier.

### Vendeur particulier
- Créer ou ouvrir un compte vendeur.
- Compléter le dossier vendeur et transmettre les informations demandées.
- Vérifier que les données privées et les documents ne sont visibles que par les personnes autorisées.
- Vérifier les états de consentement et les demandes RGPD.

### Agence, directeur et équipe
- Créer un espace agence et ouvrir une session directeur.
- Inviter un agent, accepter l'invitation et vérifier le rattachement à la bonne agence.
- Créer un contact, une recherche acquéreur et un bien ; modifier les données puis vérifier leur persistance après reconnexion.
- Vérifier les permissions respectives directeur, administrateur, agent et assistant.
- Connecter un utilisateur d'une seconde agence : aucune lecture ni modification des contacts, biens, notes, membres ou journaux de la première agence ne doit être possible.
- Vérifier qu'un agent ne peut pas gérer la facturation, les membres ou la modération réservée.

### Assistant IA
- Tester un utilisateur non connecté : aucune donnée privée d'agence ne doit être exposée.
- Tester un agent connecté : réponses limitées à son contexte et aux permissions effectives.
- Tester les demandes hors périmètre et les données personnelles ; vérifier la minimisation et l'absence de fuite inter-agences.

Consigner pour chaque parcours le compte de test, le résultat attendu, le résultat observé et la preuve (capture ou identifiant de requête). Ne jamais utiliser de vraies données clients pour cette recette.

## 5. Données, sécurité et performances

- Vérifier que toutes les migrations passent sur une base vierge.
- Exécuter les tests PostgreSQL RLS et les tests Firestore de séparation multi-tenant.
- Faire une sauvegarde, vérifier son empreinte, restaurer dans une base isolée et comparer les enregistrements critiques.
- Activer les sauvegardes automatiques/PITR du fournisseur de base ; le script local ne remplace pas une politique de sauvegarde distante.
- Réaliser un test de charge progressif sur l'environnement de préproduction (trafic nominal puis pic), surveiller p50/p95/p99, erreurs 4xx/5xx, connexions PostgreSQL, Redis et mémoire.
- Tester une restauration réelle et mesurer le RPO/RTO avant mise en production.

## 6. Android — appareil physique

Avant la recette :
- fournir `app/google-services.json` correspondant au package Android réel ;
- restreindre les clés Firebase aux empreintes et API autorisées ;
- utiliser le keystore de publication pérenne et stocker ses secrets dans GitHub Actions Secrets ;
- ne jamais remplacer ou régénérer une clé de publication déjà utilisée sur Google Play.

Sur au moins un appareil Android physique :
- installer l'APK de test et lancer l'application à froid ;
- vérifier connexion, déconnexion, persistance de session et rôles ;
- tester réseau lent, perte réseau puis reconnexion ;
- vérifier navigation, formulaires, permissions, liens externes et absence de crash ;
- contrôler les journaux Android et les erreurs réseau.

La clé Android générée en CI est éphémère : l'APK signé avec cette clé sert uniquement aux essais et ne constitue pas un artefact Play Store.

## 7. Conditions de feu vert

Ne déclarer la mise en service prête qu'après :
- tous les jobs CI verts sur le commit candidat ;
- recette Turnstile réelle réussie sur le domaine cible ;
- parcours particuliers, vendeurs et agences validés ;
- tests d'isolation et d'attaque sans anomalie critique ;
- restauration distante vérifiée et charge de préproduction acceptable ;
- APK testé sur appareil réel avec configuration Firebase et signature appropriées ;
- vérification des domaines, CORS, secrets, sauvegardes et alertes de production.

Aucun déploiement ni merge de production n'est autorisé par cette recette sans validation explicite du propriétaire du projet.
