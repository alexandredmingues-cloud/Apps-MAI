# Monty AI Core — Orchestrateur Multi-Modèles

## Architecture & Modèles Supportés

- **Routage dynamique** par type de tâche (`QUICK_SUMMARY`, `WRITING`, `COMPLEX_REASONING`, `DOCUMENT_ANALYSIS`, `CONFIDENTIAL`, `LONG_CONTEXT`, `PRICE_EXPLANATION`, `MULTIMODAL`).
- **Fournisseurs compatibles** : OpenAI (`OPENAI_MODEL`), Anthropic (`ANTHROPIC_MODEL_SONNET` défaut `claude-sonnet-4-6`, `ANTHROPIC_MODEL_HAIKU` défaut `claude-haiku-4-5`), Google Gemini (`GEMINI_MODEL` défaut `gemini-2.5-flash`), Mistral, DeepSeek, Meta Llama / Qwen (via `OPENAI_BASE_URL` ou Ollama local).
- **Côté Android (`MontyAiService.kt`)** : modèle centralisé dans `MontyAiModelConfig.AI_MODEL_NAME` (`gemini-3.8-flash` Firebase AI Logic) avec passerelle optionnelle vers `MontySaasApiClient.chatWithMontyAi` et repli local transparent.
- **Cache sémantique TTL (900 s)** et métriques comparatives par moteur (`provider_comparison`) consultables par les administrateurs plateforme.
- **Mode Confidentiel** : interdit l'envoi vers des fournisseurs cloud externes et privilégie le moteur local open-source ou les outils déterministes hors-ligne (`TOOL_ASSISTED_OFFLINE`).
