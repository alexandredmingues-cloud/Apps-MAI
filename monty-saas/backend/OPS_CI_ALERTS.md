# Alertes CI — MON-AGENT

## Comportement

Sur échec d’un job du workflow `monty-ci.yml`, le job **CI Failure Alert** :

1. Ouvre (ou commente) une **issue GitHub** labellisée `ci-failure` avec le lien du run et le détail des jobs.
2. Envoie un message **Slack** si le secret `SLACK_WEBHOOK_URL` est défini.

Les annulations concurrency (`cancel-in-progress`) ne déclenchent **pas** d’alerte.

## Configuration optionnelle Slack

1. Créer un Incoming Webhook Slack.
2. Ajouter le secret dépôt **Settings → Secrets and variables → Actions → `SLACK_WEBHOOK_URL`**.

Sans ce secret, seule l’issue GitHub est créée.

## Notifications GitHub natives (recommandé en complément)

**Settings → Notifications → Actions** : activer les e-mails sur échec de workflow pour les branches suivies.

## Labels

Le job crée au besoin les labels `ci-failure` et `automated`.
