# Contrat d'authentification — audit de portage Flutter

Date : 27 novembre 2026
Branche : `migration/flutter-multiplatform-2026-11`

## Contrats HTTP observés

Préfixe API : `/api/v1`.

| Parcours | Méthode et route | Corps / résultat |
|---|---|---|
| Inscription agence | POST `/auth/register` | `agency_name, email, password, display_name, city, country_code` → `SessionResponse` |
| Inscription particulier | POST `/auth/register-client` | `email, password, display_name, role (BUYER/SELLER), country_code` → `SessionResponse` |
| Connexion e-mail | POST `/auth/login` | `email, password` → `SessionResponse` |
| Échange Firebase | POST `/auth/firebase-exchange` | `id_token, preferred_role, agency_id` → `SessionResponse` |
| Session courante | GET `/auth/me` | Bearer JWT → `user_id, agency_id, role, permissions` |
| Session de développement | POST `/auth/dev-session` | Désactivée sauf opt-in hors production |
| Inviter un membre | POST `/team/invites` | Permission `team:manage` requise |
| Accepter une invitation | POST `/team/invites/claim` | `invite_code, password, display_name` |
| Membres d'équipe | GET `/team/members` | Permission `team:manage` requise |

Réponse de session : `access_token`, `token_type=bearer`, `user_id`, `agency_id`, `agency_name`, `display_name`, `email`, `role`, `permissions`.

Le JWT est signé HS256. Sa durée de vie est pilotée par `JWT_TTL_MINUTES` (défaut observé : 60 minutes). Les appels protégés utilisent `Authorization: Bearer <token>`. Les permissions sont recalculées côté API à partir du rôle, et les services PostgreSQL appliquent un contexte de tenant et RLS.

## Persistance et environnement

- Avec `DATABASE_URL`, les inscriptions, connexions et invitations utilisent `PostgresAuthRepository` et les fonctions PostgreSQL.
- Sans base configurée, le routeur utilise `AuthService` en mémoire : ce mode ne doit jamais être considéré comme persistant.
- Le mode de stockage est exposé par `GET /api/v1/health` (`postgresql_rls` ou `in_memory_fallback`).
- `JWT_SECRET` est obligatoire en production et doit faire au moins 32 caractères.
- `/auth/dev-session` doit rester désactivé en production.

## RBAC observé

Rôles définis : `SUPER_ADMIN`, `DIRECTOR`, `ADMIN`, `AGENT`, `ASSISTANT`, `BUYER`, `SELLER`.

Les permissions incluent notamment `team:manage`, `crm:read`, `crm:write`, `property:read`, `property:write`, `matching:run`, `estimation:run`, `portal:buyer_self` et `portal:seller_self`. Le client Flutter ne doit utiliser ces permissions que pour adapter l'interface : l'autorisation finale reste côté serveur.

## Points de vigilance identifiés — non corrigés dans ce cycle

1. **Échange Firebase à revalider avant portage.** Le handler reçoit `agency_id` et `preferred_role` du client, puis construit le JWT à partir de ces valeurs et d'un indicateur `staff/advisor` issu des claims. Le code consulté n'effectue pas de vérification de l'appartenance active à l'agence via `agency_memberships` avant d'émettre ce JWT. Ne pas utiliser ce parcours pour attribuer un rôle professionnel tant que ce contrôle serveur n'est pas ajouté et testé.
2. **Référence de rôle invalide possible.** Le handler Firebase référence `MontyRole.ADMIN_STAFF`, alors que l'enum `MontyRole` ne définit pas cette valeur. Le chemin conditionnel correspondant peut lever `AttributeError`. Corriger avec un test ciblé avant d'activer l'échange Firebase dans Flutter.
3. **Réinitialisation de mot de passe non exposée dans le routeur v1 observé.** Aucun endpoint `forgot/reset password` n'apparaît dans les routes déclarées. Ne pas inventer ce parcours côté Flutter.
4. **Révocation/renouvellement de JWT.** Aucun endpoint de refresh ou de révocation n'apparaît dans le routeur inspecté. Les JWT expirent, mais un logout Flutter ne révoque pas à lui seul un jeton déjà émis. Le stockage client devra être sécurisé et le risque documenté.
5. **Vérification réelle sur Supabase.** Les tests unitaires existants ne prouvent pas à eux seuls que les fonctions SQL, RLS et les données de production sont correctement configurées. Il faudra des tests d'intégration isolés sur une base de test, sans toucher aux données existantes.

## Contrat Flutter recommandé

- Base URL par environnement, jamais codée en dur vers une instance de production.
- Un seul client HTTP partagé, timeout explicite, erreurs réseau et erreurs API typées.
- Bearer token ajouté automatiquement uniquement aux routes protégées.
- Persistance du jeton dans un stockage sécurisé natif ; sur Web, choisir explicitement une stratégie résistante au vol XSS plutôt que de copier mécaniquement le stockage natif.
- Ne jamais stocker le mot de passe.
- Ne pas faire confiance au rôle ou à la liste de permissions fournis par un écran local pour autoriser une action.
- Au démarrage, restaurer le jeton puis appeler `GET /auth/me`; en cas de 401, vider la session locale et revenir à la connexion.
- Ne pas activer l'authentification Firebase Flutter avant la correction et les tests du handler serveur.
- Les erreurs 401, 403, 429, 5xx, timeout et absence de réseau doivent être distinguées.

## Prochaine tranche obligatoire

1. Ajouter des tests de sécurité pour l'échange Firebase (agency_id arbitraire, rôle professionnel, membership absent/suspendu, utilisateur désactivé, token expiré/projet incorrect).
2. Corriger le handler afin que l'agence et le rôle soient déterminés à partir d'une identité Firebase vérifiée et d'une appartenance active côté PostgreSQL, jamais à partir d'un choix libre du client.
3. Corriger la référence `ADMIN_STAFF` et vérifier la validation de l'audience/émetteur Firebase.
4. Vérifier les migrations et fonctions SQL sur une base de test.
5. Seulement ensuite, porter le client de session Flutter et ses tests.

Aucune modification de base, aucun changement de secret, aucune révocation de compte et aucun déploiement n'ont été effectués pendant cet audit.
