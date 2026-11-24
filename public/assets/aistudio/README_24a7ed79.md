<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Mon Agent Immo

Application Android de conseil immobilier pour Montpellier & Métropole (CRM acquéreurs, matching, visites, offres, simulateur HCSF, copilote IA).

Voir l'app dans AI Studio : https://ai.studio/apps/cd75241a-b5fc-44ff-a2a6-4ccc33fec8d3

## Lancer en local

**Prérequis :** [Android Studio](https://developer.android.com/studio)

1. Ouvrez le dossier du projet avec **Open** dans Android Studio et laissez-le corriger les incompatibilités à l'import.
2. Placez votre `google-services.json` (console Firebase, même nom de package que l'app) dans `app/`.
   Sans ce fichier le build passe (`googleServices.missing.passthrough=true`) mais Firebase échoue au lancement.
3. **Aucune clé Gemini n'est requise** : les appels IA passent par Firebase AI Logic avec App Check
   (voir `.env.example`). N'ajoutez pas de clé dans le dépôt.
4. Retirez de `app/build.gradle.kts` la ligne `signingConfig = signingConfigs.getByName("debugConfig")`.
5. Lancez l'app sur un émulateur ou un appareil.
6. Si l'app est déjà publiée via AI Studio, [demandez une réinitialisation de la clé d'upload](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) dans Google Play Console.

## Règles Firestore et tests

```bash
npm install
npm test        # démarre l'émulateur Firestore (port 8085) et lance firestore_test.js
```

Déploiement : `firebase deploy --only firestore:rules`.

### Claim `staff` (modération des annonces)

Une annonce de **Particulier** ne peut pas être publiée par son propriétaire : elle reste en `BROUILLON` / `A_VERIFIER`.
Seul un compte portant le claim custom `staff: true` peut la passer en `PUBLIEE` (et ne peut modifier que
`publicationStatus` / `listingStatus`). Le claim se pose côté serveur uniquement :

```js
admin.auth().setCustomUserClaims(uid, { staff: true });
```

Limite connue : sans claim sur les comptes conseillers, les règles ne peuvent pas distinguer un conseiller d'un
particulier qui déclarerait `publisherType: "AGENCE"` à la création. Posez le claim aux conseillers puis
imposez `PARTICULIER` aux autres comptes quand le flux est en place.

## Limites connues (à traiter côté serveur / app)

- **Paiement du pack vendeur** : `buyerOrders[].status = "PAID"` est écrit par le client. Passez par un backend
  (webhook Stripe ou équivalent) avant toute monétisation réelle.
- **Notes privées** : `privateNotes` est dans le même document que les données du portail client. Si le portail lit
  un jour ce document, déplacez les notes dans une sous-collection dédiée.
- **Portail client** : aucune règle ne donne accès à un non-propriétaire ; le portail doit passer par un backend.
- **Suppression d'agence** : Firestore ne supprime pas les sous-collections (contacts, tâches, logs). Utilisez une
  Cloud Function / l'Admin SDK pour la purge (RGPD, art. 17). Les `audit_logs` sont volontairement immuables côté client.
- **Révocation d'un agent** : le directeur retire l'UID de `memberUids` **et** supprime `members/{uid}` (batch). Les accès
  aux contacts, tâches et logs sont alors refusés même si l'UID reste dans `viewerUids`.
