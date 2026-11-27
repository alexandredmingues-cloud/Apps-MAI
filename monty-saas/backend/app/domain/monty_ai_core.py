from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from enum import Enum
import hashlib
import ipaddress
import json
import os
import re
import time
from typing import Any, Callable
import urllib.error
import urllib.parse
import urllib.request
import uuid

from app.core.config import Settings, load_settings
from app.core.rbac import Permission, TenantContext
from app.core.rgpd import redact_direct_pii_for_ai
from app.core.security import InputSanitizer, SlidingWindowRateLimiter
from app.domain.real_estate_engines import (
    HcsfCapacityInput,
    MONTPELLIER_SECTORS,
    compute_hcsf_borrowing_capacity,
)


class MontyAITaskType(str, Enum):
    CONVERSATION = "CONVERSATION"
    QUICK_SUMMARY = "QUICK_SUMMARY"
    DOCUMENT_ANALYSIS = "DOCUMENT_ANALYSIS"
    WRITING = "WRITING"
    COMPLEX_REASONING = "COMPLEX_REASONING"
    CONFIDENTIAL = "CONFIDENTIAL"
    LONG_CONTEXT = "LONG_CONTEXT"
    PRICE_EXPLANATION = "PRICE_EXPLANATION"
    MULTIMODAL = "MULTIMODAL"


class SensitiveActionType(str, Enum):
    SEND_CLIENT_MESSAGE = "SEND_CLIENT_MESSAGE"
    PUBLISH_LISTING = "PUBLISH_LISTING"
    UPDATE_MANDATE = "UPDATE_MANDATE"
    DELETE_DATA = "DELETE_DATA"
    TRANSMIT_NOTARY_DOC = "TRANSMIT_NOTARY_DOC"
    FINANCIAL_COMMITMENT = "FINANCIAL_COMMITMENT"


@dataclass
class AIEngineConfig:
    engine_id: str
    provider: str  # "ollama" | "vllm" | "llamacpp" | "openai" | "anthropic" | "gemini" | "mistral" | "deepseek"
    model_name: str
    display_label: str
    tier: str  # "local" | "external"
    is_local: bool
    confidential_safe: bool
    enabled: bool = True
    is_default: bool = False
    is_fallback: bool = False
    priority: int = 100
    min_ram_gb: int = 0
    cost_per_1k_tokens_euro: float = 0.0
    supported_tasks: tuple[str, ...] = ("CONVERSATION", "QUICK_SUMMARY", "WRITING")
    masked_api_key: str = ""
    updated_by: str = "system"
    updated_at: str = ""


@dataclass
class AIMemoryEntry:
    id: str
    agency_id: str
    user_id: str
    scope: str  # "USER" | "AGENCY"
    title: str
    content: str
    source_ref: str
    retention_until: str
    created_at: str
    updated_at: str


@dataclass
class AIConversationTurn:
    id: str
    agency_id: str
    user_id: str
    task_type: str
    engine_used: str
    execution_mode: str  # "LOCAL_OPEN_SOURCE" | "EXTERNAL_PROVIDER" | "TOOL_ASSISTED_OFFLINE"
    sanitized_prompt: str
    response_text: str
    sources: list[dict[str, str]]
    tools_invoked: list[str]
    latency_ms: int
    estimated_cost_euro: float
    created_at: str


@dataclass
class AIPendingAction:
    id: str
    agency_id: str
    requested_by_user_id: str
    action_type: SensitiveActionType
    title: str
    summary: str
    payload: dict[str, Any]
    status: str  # "A_VALIDER" | "VALIDEE" | "REJETEE"
    reviewed_by_user_id: str | None = None
    created_at: str = ""
    reviewed_at: str | None = None


@dataclass(frozen=True)
class AIAuditEvent:
    id: str
    agency_id: str
    user_id: str
    engine_id: str
    task_type: str
    status: str  # "SUCCESS" | "FALLBACK" | "OFFLINE_TOOLS" | "BLOCKED_SECURITY" | "QUOTA_EXCEEDED"
    latency_ms: int
    estimated_cost_euro: float
    created_at: str


def _mask_secret(raw: str) -> str:
    clean = raw.strip()
    if not clean:
        return ""
    if len(clean) <= 8:
        return "••••"
    return f"{clean[:4]}••••{clean[-4:]}"


def validate_local_llm_url(raw_url: str, *, allow_private: bool = False) -> str:
    """Validation anti-SSRF pour l'URL d'un serveur LLM privé/local."""
    clean = raw_url.strip().rstrip("/")
    if not clean:
        return ""
    parsed = urllib.parse.urlparse(clean)
    if parsed.scheme.lower() not in ("http", "https"):
        raise ValueError("URL LLM invalide : seuls les schémas http et https sont autorisés.")
    host = (parsed.hostname or "").strip().lower()
    if not host:
        raise ValueError("URL LLM invalide : nom d'hôte manquant.")

    # Blocage inconditionnel des endpoints de métadonnées cloud / link-local
    if host in ("metadata.google.internal", "metadata", "instance-data") or host.endswith(".internal.cloud"):
        raise ValueError("URL bloquée par la protection anti-SSRF (métadonnées cloud interdites).")
    try:
        ip = ipaddress.ip_address(host)
        if ip.is_link_local:
            raise ValueError("URL bloquée par la protection anti-SSRF (plage link-local 169.254/fe80 interdite).")
        if not allow_private and (ip.is_private or ip.is_loopback or ip.is_reserved or ip.is_multicast or ip.is_unspecified):
            raise ValueError(
                "Hôte privé/loopback bloqué par défaut (activez LOCAL_LLM_ALLOW_PRIVATE_HOSTS=true si volontaire)."
            )
    except ValueError as exc:
        if "SSRF" in str(exc) or "LOCAL_LLM_ALLOW_PRIVATE_HOSTS" in str(exc):
            raise
        # Hostname DNS : bloquer localhost / .local / .internal si allow_private=False
        if not allow_private and (host == "localhost" or host.endswith(".local") or host.endswith(".internal")):
            raise ValueError(
                "Hôte local bloqué par défaut (activez LOCAL_LLM_ALLOW_PRIVATE_HOSTS=true si volontaire)."
            )
    return clean


def is_platform_admin(ctx: TenantContext, settings: Settings) -> bool:
    """Vérifie de façon fail-closed si l'utilisateur appartient à PLATFORM_ADMIN_USER_IDS."""
    if not settings.platform_admin_user_ids:
        return False
    return ctx.user_id in settings.platform_admin_user_ids and (
        ctx.has_permission(Permission.AGENCY_MANAGE) or ctx.has_permission(Permission.PLATFORM_ADMIN)
    )


class AISecurityManager:
    """Filtre injection de prompt, redaction PII et gouvernance confidentielle."""

    _INJECTION_PATTERNS = (
        re.compile(r"ignore\s+(all\s+)?(previous|prior)\s+instructions", re.I),
        re.compile(r"reveal\s+(system\s+prompt|api[_\s]?key|secret)", re.I),
        re.compile(r"(affiche|révèle|donne).{0,24}(clé\s*api|mot\s*de\s*passe|secret)", re.I),
    )

    @classmethod
    def check_and_sanitize(cls, text: str, *, redact_pii: bool = True) -> tuple[str, bool, str]:
        clean = InputSanitizer.sanitize_text(text, 4000)
        for pat in cls._INJECTION_PATTERNS:
            if pat.search(clean):
                return (
                    "",
                    False,
                    "Demande bloquée par la protection anti-injection / exfiltration de secrets.",
                )
        if redact_pii:
            clean = redact_direct_pii_for_ai(clean)
        return clean, True, ""


class AIModelRegistry:
    """Registre central des moteurs locaux open-source (Niveau 1) et APIs externes (Niveau 2)."""

    def __init__(self) -> None:
        now = datetime.now(timezone.utc).isoformat()
        self._engines: dict[str, AIEngineConfig] = {
            "local-qwen-2.5": AIEngineConfig(
                engine_id="local-qwen-2.5",
                provider="ollama",
                model_name="qwen2.5:7b-instruct",
                display_label="Moteur Local Qwen 2.5 (7B Instruct)",
                tier="local",
                is_local=True,
                confidential_safe=True,
                enabled=True,
                is_default=True,
                is_fallback=True,
                priority=10,
                min_ram_gb=8,
                cost_per_1k_tokens_euro=0.0,
                supported_tasks=("CONVERSATION", "QUICK_SUMMARY", "WRITING", "CONFIDENTIAL", "DOCUMENT_ANALYSIS", "PRICE_EXPLANATION"),
                updated_at=now,
            ),
            "local-mistral-nemo": AIEngineConfig(
                engine_id="local-mistral-nemo",
                provider="ollama",
                model_name="mistral-nemo:12b",
                display_label="Moteur Local Mistral Nemo (12B)",
                tier="local",
                is_local=True,
                confidential_safe=True,
                enabled=True,
                priority=20,
                min_ram_gb=16,
                cost_per_1k_tokens_euro=0.0,
                supported_tasks=("WRITING", "COMPLEX_REASONING", "CONFIDENTIAL", "DOCUMENT_ANALYSIS", "PRICE_EXPLANATION"),
                updated_at=now,
            ),
            "local-llama-3.3": AIEngineConfig(
                engine_id="local-llama-3.3",
                provider="vllm",
                model_name="llama-3.3-70b-instruct",
                display_label="Moteur Local Llama 3.3 (vLLM)",
                tier="local",
                is_local=True,
                confidential_safe=True,
                enabled=True,
                priority=30,
                min_ram_gb=32,
                cost_per_1k_tokens_euro=0.0,
                supported_tasks=("COMPLEX_REASONING", "LONG_CONTEXT", "CONFIDENTIAL"),
                updated_at=now,
            ),
            "local-deepseek-r1": AIEngineConfig(
                engine_id="local-deepseek-r1",
                provider="llamacpp",
                model_name="deepseek-r1-distill-qwen-14b",
                display_label="Moteur Local DeepSeek R1 (llama.cpp)",
                tier="local",
                is_local=True,
                confidential_safe=True,
                enabled=True,
                priority=35,
                min_ram_gb=16,
                cost_per_1k_tokens_euro=0.0,
                supported_tasks=("COMPLEX_REASONING", "CONFIDENTIAL", "DOCUMENT_ANALYSIS"),
                updated_at=now,
            ),
            "ext-anthropic-sonnet": AIEngineConfig(
                engine_id="ext-anthropic-sonnet",
                provider="anthropic",
                model_name="claude-sonnet-4-6",
                display_label="Anthropic Claude Sonnet",
                tier="external",
                is_local=False,
                confidential_safe=False,
                enabled=True,
                is_fallback=True,
                priority=40,
                cost_per_1k_tokens_euro=0.015,
                supported_tasks=("COMPLEX_REASONING", "DOCUMENT_ANALYSIS", "WRITING", "LONG_CONTEXT", "PRICE_EXPLANATION"),
                updated_at=now,
            ),
            "ext-openai-gpt": AIEngineConfig(
                engine_id="ext-openai-gpt",
                provider="openai",
                model_name="gpt-4.1-mini",
                display_label="OpenAI GPT-4.1 Mini",
                tier="external",
                is_local=False,
                confidential_safe=False,
                enabled=True,
                priority=50,
                cost_per_1k_tokens_euro=0.008,
                supported_tasks=("CONVERSATION", "QUICK_SUMMARY", "WRITING", "MULTIMODAL"),
                updated_at=now,
            ),
            "ext-gemini-flash": AIEngineConfig(
                engine_id="ext-gemini-flash",
                provider="gemini",
                model_name="gemini-2.5-flash",
                display_label="Google Gemini Flash",
                tier="external",
                is_local=False,
                confidential_safe=False,
                enabled=True,
                priority=55,
                cost_per_1k_tokens_euro=0.004,
                supported_tasks=("LONG_CONTEXT", "MULTIMODAL", "QUICK_SUMMARY", "CONVERSATION"),
                updated_at=now,
            ),
            "ext-mistral-cloud": AIEngineConfig(
                engine_id="ext-mistral-cloud",
                provider="mistral",
                model_name="mistral-small-latest",
                display_label="Mistral Cloud",
                tier="external",
                is_local=False,
                confidential_safe=False,
                enabled=True,
                priority=60,
                cost_per_1k_tokens_euro=0.005,
                supported_tasks=("WRITING", "CONVERSATION", "QUICK_SUMMARY"),
                updated_at=now,
            ),
            "ext-deepseek-cloud": AIEngineConfig(
                engine_id="ext-deepseek-cloud",
                provider="deepseek",
                model_name="deepseek-chat",
                display_label="DeepSeek Cloud",
                tier="external",
                is_local=False,
                confidential_safe=False,
                enabled=True,
                priority=65,
                cost_per_1k_tokens_euro=0.003,
                supported_tasks=("COMPLEX_REASONING", "DOCUMENT_ANALYSIS"),
                updated_at=now,
            ),
        }

    def list_engines(self) -> list[AIEngineConfig]:
        return sorted(self._engines.values(), key=lambda e: e.priority)

    def get(self, engine_id: str) -> AIEngineConfig | None:
        return self._engines.get(engine_id)


class AITaskRouter:
    """Sélectionne l'ordre optimal des moteurs selon la tâche, le mode confidentiel et la disponibilité."""

    @staticmethod
    def select_candidates(
        registry: AIModelRegistry,
        *,
        task_type: MontyAITaskType,
        confidential_mode: bool,
        has_key_fn: Callable[[AIEngineConfig], bool],
    ) -> list[AIEngineConfig]:
        candidates: list[AIEngineConfig] = []
        for eng in registry.list_engines():
            if not eng.enabled:
                continue
            if confidential_mode and not eng.confidential_safe:
                continue
            if not has_key_fn(eng):
                continue
            candidates.append(eng)

        def _score(eng: AIEngineConfig) -> tuple[int, int, int]:
            task_match = 0 if task_type.value in eng.supported_tasks else 1
            local_pref = 0 if (confidential_mode or task_type in (MontyAITaskType.QUICK_SUMMARY, MontyAITaskType.CONVERSATION)) and eng.is_local else (0 if eng.is_default else 1)
            return (task_match, local_pref, eng.priority)

        return sorted(candidates, key=_score)


class AIProviderManager:
    """Gère les appels réels aux moteurs locaux (Ollama/vLLM/llama.cpp) et fournisseurs externes."""

    def __init__(self) -> None:
        self._runtime_keys: dict[str, str] = {}
        self._runtime_local_url: str = ""

    def set_provider_key(self, provider: str, api_key: str) -> str:
        clean_provider = provider.strip().lower()
        clean_key = api_key.strip()
        if not clean_key:
            self._runtime_keys.pop(clean_provider, None)
            return ""
        self._runtime_keys[clean_provider] = clean_key
        return _mask_secret(clean_key)

    def set_local_base_url(self, base_url: str, *, allow_private: bool = False) -> str:
        validated = validate_local_llm_url(base_url, allow_private=allow_private)
        self._runtime_local_url = validated
        return self._runtime_local_url

    def resolve_key(self, provider: str, settings: Settings) -> str:
        p = provider.lower()
        if p in self._runtime_keys:
            return self._runtime_keys[p]
        if p == "openai":
            return settings.openai_api_key
        if p == "anthropic":
            keys = settings.effective_anthropic_keys
            return keys[0] if keys else ""
        if p == "gemini":
            return settings.gemini_api_key
        if p == "mistral":
            return settings.mistral_api_key
        if p == "deepseek":
            return settings.deepseek_api_key
        return ""

    def resolve_local_url(self, settings: Settings) -> str:
        return (self._runtime_local_url or settings.local_llm_base_url).strip().rstrip("/")

    def is_engine_configured(self, eng: AIEngineConfig, settings: Settings) -> bool:
        if eng.is_local:
            return bool(self.resolve_local_url(settings))
        return bool(self.resolve_key(eng.provider, settings))

    def get_masked_key(self, eng: AIEngineConfig, settings: Settings) -> str:
        if eng.is_local:
            url = self.resolve_local_url(settings)
            return f"local:{url}" if url else ""
        return _mask_secret(self.resolve_key(eng.provider, settings))

    def invoke_engine(
        self,
        *,
        eng: AIEngineConfig,
        system_prompt: str,
        user_prompt: str,
        settings: Settings,
        transport: Callable[[str, dict[str, str], dict[str, Any], float], tuple[int, dict[str, Any]]] | None = None,
    ) -> str:
        sender = transport or self._http_post_json
        timeout_s = min(20.0, max(4.0, settings.anthropic_timeout_seconds))

        if eng.is_local:
            base = self.resolve_local_url(settings)
            if not base:
                raise RuntimeError("Aucun serveur LLM local configuré (OLLAMA_BASE_URL / LOCAL_LLM_BASE_URL).")
            if eng.provider == "ollama":
                url = f"{base}/api/chat"
                payload = {
                    "model": eng.model_name,
                    "stream": False,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt},
                    ],
                }
                code, data = sender(url, {"Content-Type": "application/json"}, payload, timeout_s)
                if code >= 400:
                    raise RuntimeError(f"Ollama HTTP {code}")
                content = ((data.get("message") or {}).get("content") or "").strip()
                if not content:
                    raise RuntimeError("Réponse locale vide.")
                return content
            # vLLM / llama.cpp OpenAI-compatible
            url = f"{base}/v1/chat/completions"
            payload = {
                "model": eng.model_name,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                "temperature": 0.2,
            }
            code, data = sender(url, {"Content-Type": "application/json"}, payload, timeout_s)
            if code >= 400:
                raise RuntimeError(f"Local OpenAI-compat HTTP {code}")
            choices = data.get("choices") or []
            content = (((choices[0] if choices else {}).get("message") or {}).get("content") or "").strip()
            if not content:
                raise RuntimeError("Réponse vLLM/llama.cpp vide.")
            return content

        key = self.resolve_key(eng.provider, settings)
        if not key:
            raise RuntimeError(f"Clé serveur absente pour {eng.provider}.")

        if eng.provider == "anthropic":
            effective_model = (
                settings.anthropic_model_sonnet
                if eng.model_name in ("claude-sonnet-4-6", "claude-3-7-sonnet-latest")
                else eng.model_name
            )
            code, data = sender(
                "https://api.anthropic.com/v1/messages",
                {
                    "x-api-key": key,
                    "anthropic-version": "2023-06-01",
                    "content-type": "application/json",
                },
                {
                    "model": effective_model,
                    "max_tokens": min(1200, settings.anthropic_max_tokens),
                    "system": system_prompt,
                    "messages": [{"role": "user", "content": user_prompt}],
                },
                timeout_s,
            )
            if code >= 400:
                raise RuntimeError(f"Anthropic HTTP {code}")
            blocks = data.get("content") or []
            text = " ".join(str(b.get("text") or "") for b in blocks if isinstance(b, dict)).strip()
            if not text:
                raise RuntimeError("Réponse Anthropic vide.")
            return text

        if eng.provider == "gemini":
            effective_gemini = (
                settings.gemini_model
                if eng.model_name == "gemini-2.5-flash"
                else eng.model_name
            )
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{effective_gemini}:generateContent?key={key}"
            code, data = sender(
                url,
                {"Content-Type": "application/json"},
                {
                    "contents": [
                        {"role": "user", "parts": [{"text": f"{system_prompt}\n\n{user_prompt}"}]}
                    ]
                },
                timeout_s,
            )
            if code >= 400:
                raise RuntimeError(f"Gemini HTTP {code}")
            cands = data.get("candidates") or []
            parts = (((cands[0] if cands else {}).get("content") or {}).get("parts") or [])
            text = " ".join(str(p.get("text") or "") for p in parts if isinstance(p, dict)).strip()
            if not text:
                raise RuntimeError("Réponse Gemini vide.")
            return text

        # OpenAI / Mistral / DeepSeek OpenAI-compatible
        endpoint_map = {
            "openai": "https://api.openai.com/v1/chat/completions",
            "mistral": "https://api.mistral.ai/v1/chat/completions",
            "deepseek": "https://api.deepseek.com/chat/completions",
        }
        url = endpoint_map.get(eng.provider, "https://api.openai.com/v1/chat/completions")
        code, data = sender(
            url,
            {
                "Authorization": f"Bearer {key}",
                "Content-Type": "application/json",
            },
            {
                "model": eng.model_name,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                "temperature": 0.25,
            },
            timeout_s,
        )
        if code >= 400:
            raise RuntimeError(f"{eng.provider} HTTP {code}")
        choices = data.get("choices") or []
        text = (((choices[0] if choices else {}).get("message") or {}).get("content") or "").strip()
        if not text:
            raise RuntimeError(f"Réponse {eng.provider} vide.")
        return text

    @staticmethod
    def _http_post_json(url: str, headers: dict[str, str], payload: dict[str, Any], timeout_s: float) -> tuple[int, dict[str, Any]]:
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout_s) as resp:
                raw = resp.read().decode("utf-8")
                return int(getattr(resp, "status", 200)), json.loads(raw) if raw else {}
        except urllib.error.HTTPError as err:
            return int(err.code), {}


class AIMemoryManager:
    """Mémoire isolée par agence & utilisateur avec contrôle RGPD (voir, éditer, supprimer)."""

    def __init__(self) -> None:
        self._entries: dict[str, AIMemoryEntry] = {}

    def list_memories(self, ctx: TenantContext) -> list[AIMemoryEntry]:
        out = []
        for m in self._entries.values():
            if m.agency_id != ctx.agency_id:
                continue
            if m.scope == "AGENCY" or m.user_id == ctx.user_id:
                out.append(m)
        return sorted(out, key=lambda x: x.updated_at, reverse=True)

    def upsert_memory(
        self,
        ctx: TenantContext,
        *,
        memory_id: str | None = None,
        scope: str = "USER",
        title: str,
        content: str,
        source_ref: str = "manual",
    ) -> AIMemoryEntry:
        clean_title = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(title, 180))
        clean_content = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(content, 2000))
        if not clean_title or not clean_content:
            raise ValueError("Titre et contenu mémoire obligatoires.")
        norm_scope = "AGENCY" if scope.upper() == "AGENCY" and ctx.has_permission(Permission.PROPERTY_READ) else "USER"
        now = datetime.now(timezone.utc).isoformat()
        ret = (date.today() + timedelta(days=365)).isoformat()
        if memory_id and memory_id in self._entries:
            existing = self._entries[memory_id]
            ctx.assert_same_agency(existing.agency_id)
            if existing.scope == "USER" and existing.user_id != ctx.user_id:
                raise PermissionError("Mémoire personnelle appartenant à un autre utilisateur.")
            existing.title = clean_title
            existing.content = clean_content
            existing.scope = norm_scope
            existing.updated_at = now
            return existing
        entry = AIMemoryEntry(
            id=f"mem-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            user_id=ctx.user_id,
            scope=norm_scope,
            title=clean_title,
            content=clean_content,
            source_ref=InputSanitizer.sanitize_text(source_ref, 120),
            retention_until=ret,
            created_at=now,
            updated_at=now,
        )
        self._entries[entry.id] = entry
        return entry

    def delete_memory(self, ctx: TenantContext, memory_id: str) -> bool:
        entry = self._entries.get(memory_id)
        if entry is None:
            return False
        ctx.assert_same_agency(entry.agency_id)
        if entry.scope == "USER" and entry.user_id != ctx.user_id and not ctx.has_permission(Permission.AGENCY_MANAGE):
            raise PermissionError("Suppression interdite sur la mémoire d'un autre utilisateur.")
        del self._entries[memory_id]
        return True


class AIToolManager:
    """Connecte Monty AI aux moteurs internes réels (DVF, HCSF, Secteurs) sans inventer de données."""

    @staticmethod
    def run_relevant_tools(prompt: str, context_payload: dict[str, Any]) -> tuple[list[str], list[dict[str, str]], list[str]]:
        lowered = prompt.lower()
        invoked: list[str] = []
        sources: list[dict[str, str]] = []
        facts: list[str] = []

        # Tool 1 : HCSF
        if any(k in lowered for k in ("emprunt", "hcsf", "financement", "crédit", "salaire", "revenu", "taux")):
            income = int(context_payload.get("monthly_net_income") or 5200)
            down = int(context_payload.get("down_payment") or 40000)
            hcsf = compute_hcsf_borrowing_capacity(HcsfCapacityInput(monthly_net_income=income, down_payment=down))
            invoked.append("compute_hcsf_borrowing_capacity")
            sources.append({"label": "Moteur HCSF 35%", "ref": "real_estate_engines.compute_hcsf_borrowing_capacity"})
            facts.append(
                f"Simulation HCSF indicative (base {income} €/mois, apport {down} €) : "
                f"mensualité max {hcsf.max_monthly_loan_payment} €, capital empruntable {hcsf.max_borrowed_capital:,} €, "
                f"prix net bien max {hcsf.max_property_price_net:,} €.".replace(",", " ")
            )

        # Tool 2 : Référentiel secteurs / DVF
        for sec in MONTPELLIER_SECTORS:
            short = sec.name.split(" (")[0].lower()
            if short in lowered or "montpellier" in lowered and len(facts) < 2:
                invoked.append("sector_benchmark_lookup")
                sources.append({"label": f"Référentiel {sec.name}", "ref": "MONTPELLIER_SECTORS"})
                facts.append(
                    f"Repère secteur {sec.name} : Appartement {sec.price_m2_apartment} €/m² "
                    f"(évolution {sec.evolution}, tension {sec.tension})."
                )
                break

        # Tool 3 : Price Index payload context
        if isinstance(context_payload.get("price_index"), dict):
            pi = context_payload["price_index"]
            invoked.append("monty_price_index_grounding")
            sources.append({"label": "Monty Price Index Engine", "ref": str(pi.get("category") or "ENGINE")})
            facts.append(
                f"Monty Price Index : prix affiché {pi.get('asking_price_euro')} € vs estimation marché "
                f"{pi.get('estimated_market_value')} € (écart {pi.get('gap_percent')} %, "
                f"catégorie {pi.get('category')}, fiabilité {pi.get('confidence_tier')})."
            )

        return invoked, sources, facts


class AIContextManager:
    @staticmethod
    def build_system_and_user_prompt(
        *,
        task_type: MontyAITaskType,
        sanitized_prompt: str,
        memories: list[AIMemoryEntry],
        tool_facts: list[str],
        confidential_mode: bool,
    ) -> tuple[str, str]:
        system_prompt = (
            "Tu es Monty AI, l'assistant immobilier central et souverain de l'écosystème Monty (France & Portugal). "
            "Réponds en français structuré, factuel, sans jamais mentionner le nom du fournisseur LLM sous-jacent ni inventer "
            "de transactions, prix ou données juridiques. Cite uniquement les faits calculés fournis."
        )
        if confidential_mode:
            system_prompt += " MODE CONFIDENTIEL ACTIF : aucune donnée identifiante ne doit être extrapolée."

        parts = [f"Tâche : {task_type.value}", f"Demande utilisateur : {sanitized_prompt}"]
        if memories:
            mem_lines = [f"- [{m.scope}] {m.title} : {m.content}" for m in memories[:5]]
            parts.append("Mémoire contextuelle autorisée :\n" + "\n".join(mem_lines))
        if tool_facts:
            parts.append("Données vérifiées des moteurs Monty :\n" + "\n".join(f"- {f}" for f in tool_facts))
        return system_prompt, "\n\n".join(parts)


class AICostManager:
    def __init__(self) -> None:
        self._daily_spend: dict[tuple[str, str], float] = {}

    def get_daily_spend(self, agency_id: str) -> float:
        return round(self._daily_spend.get((agency_id, date.today().isoformat()), 0.0), 4)

    def can_spend_external(self, agency_id: str, settings: Settings) -> bool:
        return self.get_daily_spend(agency_id) < max(0.5, float(settings.ai_daily_budget_euro))

    def record_cost(self, agency_id: str, cost_euro: float) -> None:
        key = (agency_id, date.today().isoformat())
        self._daily_spend[key] = round(self._daily_spend.get(key, 0.0) + max(0.0, cost_euro), 6)


class AIFallbackManager:
    @staticmethod
    def build_offline_tool_response(
        *,
        sanitized_prompt: str,
        tool_facts: list[str],
        confidential_mode: bool,
        failure_reasons: list[str],
    ) -> str:
        lines = [
            "Monty AI — Analyse structurée (mode moteurs internes sans serveur LLM externe joignable).",
            f"Objet analysé : {sanitized_prompt[:220]}",
        ]
        if tool_facts:
            lines.append("Résultats vérifiés des moteurs Monty :")
            lines.extend(f"• {f}" for f in tool_facts)
        else:
            lines.append(
                "Aucun moteur génératif local (Ollama/vLLM) ou fournisseur cloud actif n'a répondu sur cette instance ; "
                "aucune donnée fictive n'a été générée."
            )
        if confidential_mode:
            lines.append("Protection : mode confidentiel actif (aucune transmission vers des serveurs externes).")
        if failure_reasons:
            lines.append("État orchestration : bascule prête dès configuration d'un moteur local ou d'une clé serveur Admin.")
        return "\n".join(lines)


class AIEvaluationManager:
    def __init__(self) -> None:
        self._events: list[AIAuditEvent] = []
        self._cache_hits_by_agency: dict[str, int] = {}

    def record(self, event: AIAuditEvent, *, cache_hit: bool = False) -> None:
        self._events.insert(0, event)
        if len(self._events) > 300:
            self._events = self._events[:300]
        if cache_hit:
            self._cache_hits_by_agency[event.agency_id] = self._cache_hits_by_agency.get(event.agency_id, 0) + 1

    def metrics_summary(self, agency_id: str) -> dict[str, Any]:
        scoped = [e for e in self._events if e.agency_id == agency_id]
        total = len(scoped)
        avg_latency = round(sum(e.latency_ms for e in scoped) / total, 1) if total else 0.0
        fallbacks = sum(1 for e in scoped if e.status in ("FALLBACK", "OFFLINE_TOOLS"))
        by_engine: dict[str, int] = {}
        provider_stats: dict[str, dict[str, Any]] = {}
        for e in scoped:
            by_engine[e.engine_id] = by_engine.get(e.engine_id, 0) + 1
            st = provider_stats.setdefault(
                e.engine_id,
                {"engine_id": e.engine_id, "requests": 0, "total_latency_ms": 0, "total_cost_euro": 0.0},
            )
            st["requests"] += 1
            st["total_latency_ms"] += e.latency_ms
            st["total_cost_euro"] = round(st["total_cost_euro"] + e.estimated_cost_euro, 6)
        provider_comparison = [
            {
                "engine_id": eid,
                "requests": s["requests"],
                "avg_latency_ms": round(s["total_latency_ms"] / max(1, s["requests"]), 1),
                "total_cost_euro": s["total_cost_euro"],
            }
            for eid, s in sorted(provider_stats.items(), key=lambda kv: kv[1]["requests"], reverse=True)
        ]
        return {
            "total_requests": total,
            "avg_latency_ms": avg_latency,
            "fallback_count": fallbacks,
            "fallback_rate_percent": round((fallbacks / total) * 100.0, 1) if total else 0.0,
            "cache_hits": self._cache_hits_by_agency.get(agency_id, 0),
            "requests_by_engine": by_engine,
            "provider_comparison": provider_comparison,
            "recent_events": [e.__dict__ for e in scoped[:30]],
        }


class MontyAICore:
    """Orchestrateur unifié Monty AI : multi-moteurs invisibles, mémoire RGPD, validation humaine et audit."""

    CACHE_TTL_SECONDS = 900

    def __init__(self) -> None:
        self.registry = AIModelRegistry()
        self.provider_manager = AIProviderManager()
        self.memory_manager = AIMemoryManager()
        self.cost_manager = AICostManager()
        self.evaluation_manager = AIEvaluationManager()
        self._rate_limiter = SlidingWindowRateLimiter(redis_url=os.environ.get("REDIS_URL", "").strip())
        self._conversations: list[AIConversationTurn] = []
        self._pending_actions: dict[str, AIPendingAction] = {}
        self._semantic_cache: dict[str, tuple[float, dict[str, Any]]] = {}

    def _detect_sensitive_action(self, prompt: str) -> tuple[SensitiveActionType, str] | None:
        lowered = prompt.lower()
        if any(k in lowered for k in ("envoie", "envoyer", "whatsapp", "email au client", "sms")):
            return (SensitiveActionType.SEND_CLIENT_MESSAGE, "Envoi d'un message client préparé par Monty AI")
        if any(k in lowered for k in ("publie l'annonce", "publier l'annonce", "mettre en ligne")):
            return (SensitiveActionType.PUBLISH_LISTING, "Publication d'annonce soumise à validation")
        if any(k in lowered for k in ("transmettre au notaire", "envoyer au notaire", "dossier notaire")):
            return (SensitiveActionType.TRANSMIT_NOTARY_DOC, "Transmission de dossier notarial")
        if any(k in lowered for k in ("supprime", "supprimer", "effacer", "purge")):
            return (SensitiveActionType.DELETE_DATA, "Suppression / purge de données")
        if any(k in lowered for k in ("mandat", "avenant")):
            return (SensitiveActionType.UPDATE_MANDATE, "Modification de mandat")
        return None

    def chat(
        self,
        ctx: TenantContext,
        *,
        prompt: str,
        task_type: MontyAITaskType = MontyAITaskType.CONVERSATION,
        confidential_mode: bool = False,
        context_payload: dict[str, Any] | None = None,
        settings: Settings,
        transport: Callable[[str, dict[str, str], dict[str, Any], float], tuple[int, dict[str, Any]]] | None = None,
    ) -> dict[str, Any]:
        if not (ctx.has_permission(Permission.AI_ASSISTANT_PRO) or ctx.has_permission(Permission.AI_ASSISTANT_CLIENT)):
            raise PermissionError("Authentification requise pour utiliser Monty AI.")

        decision = self._rate_limiter.check_and_consume(
            f"monty_ai:{ctx.agency_id}:{ctx.user_id}",
            max_requests=30,
            window_seconds=60,
        )
        if not decision.allowed:
            raise ValueError(f"Limite de requêtes atteinte. Réessayez dans {decision.retry_after_seconds}s.")

        t0 = time.perf_counter()
        eff_confidential = confidential_mode or settings.monty_ai_confidential_default or task_type == MontyAITaskType.CONFIDENTIAL
        clean_prompt, ok, block_reason = AISecurityManager.check_and_sanitize(prompt, redact_pii=True)
        if not ok or not clean_prompt:
            self.evaluation_manager.record(
                AIAuditEvent(
                    id=f"aiev-{uuid.uuid4().hex[:8]}",
                    agency_id=ctx.agency_id,
                    user_id=ctx.user_id,
                    engine_id="security-guard",
                    task_type=task_type.value,
                    status="BLOCKED_SECURITY",
                    latency_ms=1,
                    estimated_cost_euro=0.0,
                    created_at=datetime.now(timezone.utc).isoformat(),
                )
            )
            raise ValueError(block_reason or "Requête invalide.")

        tools_invoked, sources, tool_facts = AIToolManager.run_relevant_tools(clean_prompt, context_payload or {})
        detected = self._detect_sensitive_action(clean_prompt)
        cache_key = ""
        if detected is None:
            digest = hashlib.sha256(
                f"{clean_prompt.lower()}|{'|'.join(tool_facts)}".encode("utf-8")
            ).hexdigest()
            cache_key = f"{ctx.agency_id}:{task_type.value}:{int(eff_confidential)}:{digest}"
            cached_entry = self._semantic_cache.get(cache_key)
            if cached_entry is not None:
                cached_at, cached_payload = cached_entry
                if (time.monotonic() - cached_at) <= self.CACHE_TTL_SECONDS:
                    hit_turn_id = f"conv-{uuid.uuid4().hex[:10]}"
                    now_iso = datetime.now(timezone.utc).isoformat()
                    self.evaluation_manager.record(
                        AIAuditEvent(
                            id=f"aiev-{uuid.uuid4().hex[:8]}",
                            agency_id=ctx.agency_id,
                            user_id=ctx.user_id,
                            engine_id=str(cached_payload.get("_engine_used", "semantic-cache")),
                            task_type=task_type.value,
                            status="CACHE_HIT",
                            latency_ms=1,
                            estimated_cost_euro=0.0,
                            created_at=now_iso,
                        ),
                        cache_hit=True,
                    )
                    return {
                        "conversation_id": hit_turn_id,
                        "assistant_name": "Monty AI",
                        "response_text": cached_payload["response_text"],
                        "execution_mode": cached_payload["execution_mode"],
                        "confidential_mode": eff_confidential,
                        "sources": cached_payload["sources"],
                        "tools_invoked": cached_payload["tools_invoked"],
                        "latency_ms": 1,
                        "cache_hit": True,
                        "requires_human_validation": False,
                        "pending_action": None,
                        "_engine_used": cached_payload.get("_engine_used", "semantic-cache"),
                        "_estimated_cost_euro": 0.0,
                        "_sanitized_prompt": clean_prompt,
                    }
                self._semantic_cache.pop(cache_key, None)

        memories = self.memory_manager.list_memories(ctx)
        system_prompt, user_prompt = AIContextManager.build_system_and_user_prompt(
            task_type=task_type,
            sanitized_prompt=clean_prompt,
            memories=memories,
            tool_facts=tool_facts,
            confidential_mode=eff_confidential,
        )

        candidates = AITaskRouter.select_candidates(
            self.registry,
            task_type=task_type,
            confidential_mode=eff_confidential,
            has_key_fn=lambda e: self.provider_manager.is_engine_configured(e, settings),
        )

        response_text = ""
        engine_used = "monty-tools-offline"
        execution_mode = "TOOL_ASSISTED_OFFLINE"
        status_label = "OFFLINE_TOOLS"
        est_cost = 0.0
        failure_reasons: list[str] = []

        for idx, eng in enumerate(candidates):
            if not eng.is_local and not self.cost_manager.can_spend_external(ctx.agency_id, settings):
                failure_reasons.append(f"{eng.engine_id}: budget journalier atteint")
                continue
            try:
                response_text = self.provider_manager.invoke_engine(
                    eng=eng,
                    system_prompt=system_prompt,
                    user_prompt=user_prompt,
                    settings=settings,
                    transport=transport,
                )
                engine_used = eng.engine_id
                execution_mode = "LOCAL_OPEN_SOURCE" if eng.is_local else "EXTERNAL_PROVIDER"
                status_label = "FALLBACK" if idx > 0 else "SUCCESS"
                est_cost = eng.cost_per_1k_tokens_euro
                if est_cost > 0:
                    self.cost_manager.record_cost(ctx.agency_id, est_cost)
                break
            except Exception as exc:
                failure_reasons.append(f"{eng.engine_id}: {exc}")

        if not response_text:
            response_text = AIFallbackManager.build_offline_tool_response(
                sanitized_prompt=clean_prompt,
                tool_facts=tool_facts,
                confidential_mode=eff_confidential,
                failure_reasons=failure_reasons,
            )

        latency_ms = max(1, round((time.perf_counter() - t0) * 1000))
        pending_action: AIPendingAction | None = None
        if detected is not None:
            action_type, action_title = detected
            pending_action = AIPendingAction(
                id=f"act-{uuid.uuid4().hex[:10]}",
                agency_id=ctx.agency_id,
                requested_by_user_id=ctx.user_id,
                action_type=action_type,
                title=action_title,
                summary=clean_prompt[:280],
                payload={"draft": response_text[:800], "task_type": task_type.value},
                status="A_VALIDER",
                created_at=datetime.now(timezone.utc).isoformat(),
            )
            self._pending_actions[pending_action.id] = pending_action
        elif cache_key and execution_mode != "TOOL_ASSISTED_OFFLINE":
            self._semantic_cache[cache_key] = (
                time.monotonic(),
                {
                    "response_text": response_text,
                    "execution_mode": execution_mode,
                    "sources": sources,
                    "tools_invoked": tools_invoked,
                    "_engine_used": engine_used,
                },
            )
            if len(self._semantic_cache) > 256:
                oldest_key = next(iter(self._semantic_cache))
                self._semantic_cache.pop(oldest_key, None)

        turn = AIConversationTurn(
            id=f"conv-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            user_id=ctx.user_id,
            task_type=task_type.value,
            engine_used=engine_used,
            execution_mode=execution_mode,
            sanitized_prompt=clean_prompt,
            response_text=response_text,
            sources=sources,
            tools_invoked=tools_invoked,
            latency_ms=latency_ms,
            estimated_cost_euro=est_cost,
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._conversations.insert(0, turn)
        if len(self._conversations) > 200:
            self._conversations = self._conversations[:200]

        self.evaluation_manager.record(
            AIAuditEvent(
                id=f"aiev-{uuid.uuid4().hex[:8]}",
                agency_id=ctx.agency_id,
                user_id=ctx.user_id,
                engine_id=engine_used,
                task_type=task_type.value,
                status=status_label,
                latency_ms=latency_ms,
                estimated_cost_euro=est_cost,
                created_at=turn.created_at,
            )
        )

        return {
            "conversation_id": turn.id,
            "assistant_name": "Monty AI",
            "response_text": response_text,
            "execution_mode": execution_mode,
            "confidential_mode": eff_confidential,
            "sources": sources,
            "tools_invoked": tools_invoked,
            "latency_ms": latency_ms,
            "cache_hit": False,
            "requires_human_validation": pending_action is not None,
            "pending_action": (
                {
                    "id": pending_action.id,
                    "action_type": pending_action.action_type.value,
                    "title": pending_action.title,
                    "summary": pending_action.summary,
                    "status": pending_action.status,
                }
                if pending_action
                else None
            ),
            "_engine_used": engine_used,
            "_estimated_cost_euro": est_cost,
            "_sanitized_prompt": clean_prompt,
        }

    def list_conversations(self, ctx: TenantContext, limit: int = 30) -> list[AIConversationTurn]:
        return [c for c in self._conversations if c.agency_id == ctx.agency_id and (c.user_id == ctx.user_id or ctx.has_permission(Permission.AGENCY_MANAGE))][:limit]

    def list_pending_actions(self, ctx: TenantContext) -> list[AIPendingAction]:
        return sorted(
            [a for a in self._pending_actions.values() if a.agency_id == ctx.agency_id],
            key=lambda x: x.created_at,
            reverse=True,
        )

    def decide_pending_action(self, ctx: TenantContext, action_id: str, approve: bool) -> AIPendingAction:
        if not ctx.has_permission(Permission.PROPERTY_WRITE):
            raise PermissionError("Droits insuffisants pour valider ou rejeter cette action.")
        act = self._pending_actions.get(action_id)
        if act is None:
            raise ValueError("Action en attente introuvable.")
        ctx.assert_same_agency(act.agency_id)
        act.status = "VALIDEE" if approve else "REJETEE"
        act.reviewed_by_user_id = ctx.user_id
        act.reviewed_at = datetime.now(timezone.utc).isoformat()
        return act

    def admin_list_engines(self, ctx: TenantContext, settings: Settings) -> list[dict[str, Any]]:
        if not ctx.has_permission(Permission.AGENCY_MANAGE):
            raise PermissionError("Accès réservé aux administrateurs.")
        out = []
        for eng in self.registry.list_engines():
            out.append(
                {
                    "engine_id": eng.engine_id,
                    "provider": eng.provider,
                    "model_name": eng.model_name,
                    "display_label": eng.display_label,
                    "tier": eng.tier,
                    "is_local": eng.is_local,
                    "confidential_safe": eng.confidential_safe,
                    "enabled": eng.enabled,
                    "is_default": eng.is_default,
                    "is_fallback": eng.is_fallback,
                    "priority": eng.priority,
                    "min_ram_gb": eng.min_ram_gb,
                    "cost_per_1k_tokens_euro": eng.cost_per_1k_tokens_euro,
                    "configured": self.provider_manager.is_engine_configured(eng, settings),
                    "masked_api_key": self.provider_manager.get_masked_key(eng, settings) or eng.masked_api_key,
                    "updated_by": eng.updated_by,
                    "updated_at": eng.updated_at,
                }
            )
        return out

    def admin_update_engine(
        self,
        ctx: TenantContext,
        *,
        engine_id: str,
        enabled: bool | None = None,
        is_default: bool | None = None,
        is_fallback: bool | None = None,
        priority: int | None = None,
        model_name: str | None = None,
        settings: Settings,
    ) -> dict[str, Any]:
        if not is_platform_admin(ctx, settings):
            raise PermissionError(
                "Modification globale des moteurs Monty AI réservée aux administrateurs plateforme (PLATFORM_ADMIN_USER_IDS)."
            )
        eng = self.registry.get(engine_id)
        if eng is None:
            raise ValueError("Moteur IA inconnu.")
        if is_default:
            for item in self.registry.list_engines():
                item.is_default = False
            eng.is_default = True
        elif is_default is False:
            eng.is_default = False
        if enabled is not None:
            eng.enabled = enabled
        if is_fallback is not None:
            eng.is_fallback = is_fallback
        if priority is not None:
            eng.priority = max(1, min(999, int(priority)))
        if model_name is not None and model_name.strip():
            eng.model_name = InputSanitizer.sanitize_text(model_name, 120)
        eng.updated_by = ctx.user_id
        eng.updated_at = datetime.now(timezone.utc).isoformat()
        return next(e for e in self.admin_list_engines(ctx, settings) if e["engine_id"] == engine_id)

    def admin_configure_provider_key(
        self,
        ctx: TenantContext,
        *,
        provider: str,
        api_key: str = "",
        local_base_url: str = "",
        settings: Settings | None = None,
    ) -> dict[str, Any]:
        eff_settings = settings or load_settings()
        if not is_platform_admin(ctx, eff_settings):
            raise PermissionError(
                "Configuration des clés fournisseurs et URL LLM réservée aux administrateurs plateforme (PLATFORM_ADMIN_USER_IDS)."
            )
        clean_provider = provider.strip().lower()
        if clean_provider in ("ollama", "vllm", "llamacpp", "local"):
            url = self.provider_manager.set_local_base_url(
                local_base_url,
                allow_private=eff_settings.local_llm_allow_private_hosts,
            )
            return {"provider": clean_provider, "configured": bool(url), "masked_value": f"local:{url}" if url else ""}
        masked = self.provider_manager.set_provider_key(clean_provider, api_key)
        for eng in self.registry.list_engines():
            if eng.provider == clean_provider:
                eng.masked_api_key = masked
                eng.updated_by = ctx.user_id
                eng.updated_at = datetime.now(timezone.utc).isoformat()
        return {"provider": clean_provider, "configured": bool(masked), "masked_value": masked}


monty_ai_core = MontyAICore()
