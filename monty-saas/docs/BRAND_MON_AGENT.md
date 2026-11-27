# MON-AGENT — Brand migration policy

**Official product name:** MON-AGENT  
**Tagline:** L'humain avant tout. La technologie au service de l'immobilier.

## Product naming

| Previous public name | Official public name |
|---|---|
| MONTY | MON-AGENT |
| Monty AI Core | Mon-Agent AI Core |
| Monty Pro | Mon-Agent Pro |
| Monty Particulier | Mon-Agent Particulier |
| Monty Deal Score | Mon-Agent Deal Score |
| Monty OS | Mon-Agent OS |

## Migration safety rules

This first branding pass changes public-facing names, application metadata, Android launcher label, and documentation only.

The following identifiers are intentionally preserved until a dedicated compatibility migration is designed and tested:

- Kotlin/Java package names, namespaces, application IDs and Firebase identifiers.
- Backend Python modules, imports, API routes, database schemas, SQL tables, migration history and persisted enum values.
- Existing Firestore collection names, Firebase project IDs, OAuth client IDs and signing configuration.
- Existing route paths, storage bucket names, environment variable names and deployment service identifiers.
- Internal class names such as `MontyShell`, `MontyAiService`, and `Monty AI Core` where they are code contracts.

Do not perform a global text replacement across source code or database history. Add compatibility aliases and explicit migrations before changing any technical identifier.

## Domains and repository

The preferred future repository slug is `mon-agent`. Domain and repository availability must be checked and registered by the owner before changing canonical URLs. Until then, keep current Git remotes, deployment URLs, OAuth redirect URIs, Firebase configuration and external callbacks unchanged.

## Brand rollout checklist

- [x] Update main public titles and descriptions.
- [x] Update web navigation and Android launcher label.
- [x] Update PWA manifest display name.
- [x] Update package display name.
- [x] Preserve internal technical identifiers and existing data.
- [ ] Check and register the desired domain.
- [ ] Rename GitHub repository after confirming slug availability and updating integrations.
- [ ] Update OAuth redirect URIs, Firebase authorized domains, webhook URLs and deployment references only after the canonical domain is active.
- [ ] Replace visual logo assets after the final logo is approved.
- [ ] Run frontend, Android, backend and end-to-end regression tests.
