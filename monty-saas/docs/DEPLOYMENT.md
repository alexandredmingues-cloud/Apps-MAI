# Déploiement MONTY (Render + PostgreSQL + Redis + stockage objet)

## 1. Architecture Render

Le fichier racine `/render.yaml` déclare les deux services applicatifs :
- `monty-saas-api` : FastAPI / Python 3.11, plan Starter, disque persistant de 2 Go.
- `monty-site` : Next.js 15 / Node 20, plan Starter.

**PostgreSQL et Redis ne sont pas créés par ce Blueprint.** Ils doivent être provisionnés dans Render (ou chez un fournisseur compatible) avant le premier déploiement. Cette séparation évite de créer des ressources payantes sans validation explicite.

Configurer dans les variables du service API :
- `DATABASE_URL` : URL PostgreSQL privée/interne, accessible depuis Render. Elle est obligatoire ; `preDeployCommand: alembic upgrade head` applique les migrations et fait échouer le déploiement si la base ou les migrations sont indisponibles.
- `REDIS_URL` : URL Redis/Valkey privée/interne. Recommandée pour partager le rate limiting entre workers et activer les tâches distribuées.
- `JWT_SECRET` : généré par Render ; conserver au moins 32 caractères.
- `PLATFORM_ADMIN_USER_IDS` : identifiants Firebase des administrateurs plateforme autorisés.
- `STRIPE_SECRET_KEY` et `STRIPE_WEBHOOK_SECRET` : secrets Stripe, requis pour activer les paiements.
- `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY` : facultatives selon les moteurs IA activés.
- `SUPABASE_URL` et `SUPABASE_SERVICE_ROLE_KEY` (ou les variables `S3_*`) : stockage objet recommandé pour les APK et documents. Le disque local persistant est un fallback, pas un stockage partagé multi-instance.

Ne jamais placer de secrets dans le dépôt, le frontend ou les variables `NEXT_PUBLIC_*`.

## 2. Déploiement du frontend

Le Blueprint utilise `npm ci` pour installer exactement les dépendances du `package-lock.json`, puis construit Next.js.

Variable requise :
- `NEXT_PUBLIC_API_BASE_URL=https://monty-saas-api.onrender.com`

Vérifier que l'origine réelle du frontend figure dans `ALLOWED_ORIGINS` côté API. Après changement de domaine, mettre à jour les deux côtés.

## 3. Application Android

Le dépôt contient les propriétés du Gradle Wrapper, mais pas les scripts `gradlew` / `gradlew.bat` ni le JAR du wrapper. La CI installe donc explicitement Gradle 9.3.1. Pour un poste de développement, ouvrir le projet dans Android Studio et utiliser la version Gradle définie dans `gradle/wrapper/gradle-wrapper.properties`.

Pour compiler localement :
```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

Pour une release signée, configurer la signature privée hors du dépôt, puis compiler `assembleRelease`. L'APK peut ensuite être publié via `/api/v1/admin/mobile/releases/upload` ; le serveur contrôle le ZIP et calcule le SHA-256.

## 4. Contrôles avant mise en production

- Vérifier le succès des trois jobs GitHub Actions : backend, frontend et Android.
- Vérifier la connexion PostgreSQL, l'application de toutes les migrations Alembic et la disponibilité Redis.
- Configurer les secrets Stripe et tester les webhooks en environnement de test avant activation réelle.
- Restreindre la clé API Firebase dans Google Cloud Console au package Android, à l'empreinte de signature et aux API nécessaires.
- Vérifier les règles Firestore et l'App Check dans le projet Firebase de production.
- Configurer un bucket privé et les règles d'accès/expiration pour les documents personnels.
- Vérifier les sauvegardes PostgreSQL, la restauration et la supervision avant d'ouvrir le service au public.
