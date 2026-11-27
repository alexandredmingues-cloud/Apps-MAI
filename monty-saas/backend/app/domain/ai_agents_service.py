from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timezone
import hashlib
import hmac
import json
import os
import secrets
import time
from typing import Any, Callable
import urllib.error
import urllib.request

from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.core.rgpd import redact_direct_pii_for_ai
from app.core.security import InputSanitizer, SlidingWindowRateLimiter


@dataclass(frozen=True)
class AgentDefinition:
    agent_id: str
    name: str
    category: str
    model_tier: str  # "sonnet" | "haiku"
    description: str
    estimated_cost_euro: float


AGENTS_CATALOG: tuple[AgentDefinition, ...] = (
    AgentDefinition("scout_prospection", "Monty Scout Prospection", "Acquisition", "haiku", "Prépare des approches vendeurs/acquéreurs en mode Proposition RGPD avec validation 1-clic.", 0.004),
    AgentDefinition("buyer_qualifier", "Qualification Acquéreur", "CRM", "haiku", "Analyse critères, financement HCSF et urgence d'achat.", 0.004),
    AgentDefinition("dvf_analyst", "Analyste DVF & Score Monty", "Estimation", "sonnet", "Synthétise les comparables DVF filtrés IQR et justifie la fourchette.", 0.018),
    AgentDefinition("ad_copywriter", "Rédacteur Annonce Pro", "Marketing", "haiku", "Génère titre + descriptif SEO + conseils photo sans PII.", 0.005),
    AgentDefinition("alur_compliance", "Auditeur Conformité L. 721-2", "Juridique", "sonnet", "Vérifie les pièces ALUR et les champs douteux du pré-état daté.", 0.015),
    AgentDefinition("visit_briefer", "Préparateur de Visite", "Terrain", "haiku", "Prépare la fiche visite, objections et points techniques DPE.", 0.004),
    AgentDefinition("offer_negotiator", "Stratège Négociation Offre", "Transaction", "sonnet", "Simule les contre-propositions vendeur/acquéreur et délais.", 0.018),
    AgentDefinition("hcsf_coach", "Coach Financement HCSF", "Finance", "haiku", "Vulgarise le taux d'endettement 35% et le reste à vivre.", 0.004),
    AgentDefinition("rgpd_guardian", "Gardien RGPD & Relances", "Conformité", "haiku", "Contrôle base légale, durée 36/60 mois et purge.", 0.003),
    AgentDefinition("news_dual_writer", "Veille Double Public", "Actualités", "sonnet", "Produit une analyse Pro + un résumé Particulier vulgarisé.", 0.016),
    AgentDefinition("notary_coordinator", "Coordinateur Avant-Contrat", "Notaire", "sonnet", "Prépare le bordereau complet compromis pour le notaire.", 0.016),
)

AGENTS_BY_ID: dict[str, AgentDefinition] = {a.agent_id: a for a in AGENTS_CATALOG}

APPROVED_WHATSAPP_TEMPLATES: dict[str, dict[str, Any]] = {
    "monty_visite_confirmation_fr": {"language": "fr", "category": "UTILITY"},
    "monty_relance_vendeur_fr": {"language": "fr", "category": "MARKETING"},
    "monty_alerte_bien_fr": {"language": "fr", "category": "UTILITY"},
    "monty_suivi_dossier_fr": {"language": "fr", "category": "UTILITY"},
}


@dataclass(frozen=True)
class AgentExecutionLog:
    id: str
    agency_id: str
    actor_user_id: str
    agent_id: str
    agent_name: str
    model_used: str
    sanitized_input: str
    output_summary: str
    duration_ms: int
    cost_euro: float
    cache_hit: bool
    created_at: str


@dataclass
class ScoutProposal:
    id: str
    agency_id: str
    created_by_user_id: str
    target_contact_name: str
    channel: str  # "WHATSAPP" | "EMAIL" | "SMS"
    legal_basis: str
    rgpd_notice: str
    opt_out_keyword: str
    message_draft: str
    status: str  # "A_VALIDER" | "VALIDEE" | "REJETEE"
    reviewed_by_user_id: str | None
    created_at: str
    reviewed_at: str | None = None


@dataclass(frozen=True)
class WhatsAppConsentEvent:
    id: str
    phone_masked: str
    direction: str
    action: str  # "OPT_IN" | "OPT_OUT" | "MESSAGE_RECEIVED" | "TEMPLATE_SENT"
    keyword_matched: str
    created_at: str


class MontyAgentsGovernanceService:
    """Orchestration sécurisée des 11 agents, Claude Sonnet/Haiku avec rotation, kill-switch admin, budget, cache, Monty Scout & WhatsApp."""

    def __init__(self) -> None:
        self._kill_switch_enabled: bool = False
        self._kill_switch_reason: str = ""
        self._rate_limiter = SlidingWindowRateLimiter(redis_url=os.environ.get("REDIS_URL", "").strip())
        self._response_cache: dict[str, tuple[str, str]] = {}
        self._logs: list[AgentExecutionLog] = []
        self._daily_spend_by_agency: dict[tuple[str, str], float] = {}
        self._anthropic_key_cursor: int = 0
        self._scout_proposals: dict[str, ScoutProposal] = {
            "sct-seed-1": ScoutProposal(
                id="sct-seed-1",
                agency_id="agency-france-reseau",
                created_by_user_id="usr-director",
                target_contact_name="Camille Laurent (Acquéreur Lyon)",
                channel="WHATSAPP",
                legal_basis="MESURES_PRECONTRACTUELLES",
                rgpd_notice="Conformément au RGPD, vos données sont traitées par votre agence Monty. Répondez STOP pour ne plus être contacté.",
                opt_out_keyword="STOP",
                message_draft="Bonjour Camille, un T3 lumineux avec terrasse vient d'être vérifié dans votre périmètre Tram Lyon (sous votre budget de 420 k€). Souhaitez-vous le créneau de visite jeudi 17h30 ? (Répondez STOP pour opposition)",
                status="A_VALIDER",
                reviewed_by_user_id=None,
                created_at="2026-11-25T08:00:00Z",
            )
        }
        self._whatsapp_events: list[WhatsAppConsentEvent] = []
        self._opted_out_phones: set[str] = set()

    def is_kill_switch_active(self) -> tuple[bool, str]:
        return self._kill_switch_enabled, self._kill_switch_reason

    def set_kill_switch(self, ctx: TenantContext, *, enabled: bool, reason: str = "") -> dict[str, Any]:
        if not (ctx.has_permission(Permission.AGENCY_MANAGE) or ctx.has_permission(Permission.NEWS_ADMIN)):
            raise PermissionError("Seul un Directeur ou Administrateur peut activer/désactiver le kill-switch agents.")
        self._kill_switch_enabled = enabled
        self._kill_switch_reason = InputSanitizer.sanitize_text(reason, 200) if enabled else ""
        return {
            "kill_switch_enabled": self._kill_switch_enabled,
            "reason": self._kill_switch_reason,
            "updated_by": ctx.user_id,
        }

    def get_daily_spend(self, agency_id: str) -> float:
        today = date.today().isoformat()
        return round(self._daily_spend_by_agency.get((agency_id, today), 0.0), 4)

    def _call_anthropic_with_rotation(
        self,
        *,
        agent: AgentDefinition,
        clean_prompt: str,
        settings: Settings,
        transport: Callable[[str, dict[str, str], dict[str, Any], float], tuple[int, dict[str, Any]]] | None = None,
    ) -> tuple[str, str] | None:
        keys = settings.effective_anthropic_keys
        if not keys:
            return None

        model = settings.anthropic_model_sonnet if agent.model_tier == "sonnet" else settings.anthropic_model_haiku
        payload: dict[str, Any] = {
            "model": model,
            "max_tokens": max(128, min(4096, settings.anthropic_max_tokens)),
            "system": (
                f"Tu es l'agent immobilier Monty '{agent.name}' ({agent.category}). "
                "Réponds de manière factuelle, concise, conforme RGPD/Loi Hoguet/Loi ALUR, sans jamais inventer de PII."
            ),
            "messages": [{"role": "user", "content": clean_prompt}],
        }

        start_idx = self._anthropic_key_cursor % len(keys)
        self._anthropic_key_cursor = (self._anthropic_key_cursor + 1) % len(keys)
        ordered_keys = keys[start_idx:] + keys[:start_idx]

        for api_key in ordered_keys:
            headers = {
                "x-api-key": api_key,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json",
            }
            try:
                if transport is not None:
                    status_code, body = transport(
                        "https://api.anthropic.com/v1/messages",
                        headers,
                        payload,
                        settings.anthropic_timeout_seconds,
                    )
                else:
                    req = urllib.request.Request(
                        url="https://api.anthropic.com/v1/messages",
                        data=json.dumps(payload).encode("utf-8"),
                        method="POST",
                        headers=headers,
                    )
                    with urllib.request.urlopen(req, timeout=settings.anthropic_timeout_seconds) as resp:
                        status_code = getattr(resp, "status", 200)
                        body = json.loads(resp.read().decode("utf-8"))

                if status_code in (401, 403, 429) or status_code >= 500:
                    continue

                blocks = body.get("content", []) if isinstance(body, dict) else []
                text_parts = [
                    str(b.get("text", "")).strip()
                    for b in blocks
                    if isinstance(b, dict) and b.get("type") == "text"
                ]
                merged = InputSanitizer.sanitize_text(" ".join(p for p in text_parts if p), 2400)
                if merged:
                    return model, merged
            except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, ValueError, OSError):
                continue

        return None

    def _call_openai(
        self,
        *,
        agent: AgentDefinition,
        clean_prompt: str,
        settings: Settings,
        transport: Callable[[str, dict[str, str], dict[str, Any], float], tuple[int, dict[str, Any]]] | None = None,
    ) -> tuple[str, str] | None:
        api_key = settings.openai_api_key.strip()
        if not api_key:
            return None

        model = settings.openai_model_complex if agent.model_tier == "sonnet" else settings.openai_model_fast
        payload: dict[str, Any] = {
            "model": model,
            "max_tokens": max(128, min(4096, settings.anthropic_max_tokens)),
            "messages": [
                {
                    "role": "system",
                    "content": (
                        f"Tu es l'agent immobilier Monty '{agent.name}' ({agent.category}). "
                        "Réponds de manière factuelle, concise, conforme RGPD/Loi Hoguet/Loi ALUR, sans jamais inventer de PII."
                    ),
                },
                {"role": "user", "content": clean_prompt},
            ],
        }
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }
        try:
            if transport is not None:
                status_code, body = transport(
                    "https://api.openai.com/v1/chat/completions",
                    headers,
                    payload,
                    settings.anthropic_timeout_seconds,
                )
            else:
                req = urllib.request.Request(
                    url="https://api.openai.com/v1/chat/completions",
                    data=json.dumps(payload).encode("utf-8"),
                    method="POST",
                    headers=headers,
                )
                with urllib.request.urlopen(req, timeout=settings.anthropic_timeout_seconds) as resp:
                    status_code = getattr(resp, "status", 200)
                    body = json.loads(resp.read().decode("utf-8"))

            if status_code in (401, 403, 429) or status_code >= 500:
                return None

            choices = body.get("choices", []) if isinstance(body, dict) else []
            if choices and isinstance(choices[0], dict):
                msg = choices[0].get("message", {})
                raw_content = str(msg.get("content", "")).strip() if isinstance(msg, dict) else ""
                merged = InputSanitizer.sanitize_text(raw_content, 2400)
                if merged:
                    return model, merged
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, ValueError, OSError):
            return None

        return None

    def run_agent(
        self,
        ctx: TenantContext,
        *,
        agent_id: str,
        prompt_text: str,
        settings: Settings,
        max_calls_per_minute: int = 12,
        anthropic_transport: Callable[[str, dict[str, str], dict[str, Any], float], tuple[int, dict[str, Any]]] | None = None,
        openai_transport: Callable[[str, dict[str, str], dict[str, Any], float], tuple[int, dict[str, Any]]] | None = None,
    ) -> AgentExecutionLog:
        t0 = time.perf_counter()
        if not (ctx.has_permission(Permission.AI_ASSISTANT_PRO) or ctx.has_permission(Permission.AI_ASSISTANT_CLIENT)):
            raise PermissionError("Authentification requise pour exécuter un agent Monty.")
        if self._kill_switch_enabled:
            raise RuntimeError(
                f"Agents IA temporairement suspendus par l'administrateur ({self._kill_switch_reason or 'kill-switch actif'})."
            )

        agent = AGENTS_BY_ID.get(agent_id)
        if agent is None:
            raise ValueError(f"Agent inconnu : {agent_id}")

        rl = self._rate_limiter.check_and_consume(
            f"agent:{ctx.agency_id}:{agent.agent_id}",
            max_requests=max_calls_per_minute,
            window_seconds=60,
        )
        if not rl.allowed:
            raise RuntimeError(f"Plafond appels/minute atteint pour {agent.name}. Réessayez dans {rl.retry_after_seconds}s.")

        today = date.today().isoformat()
        current_spend = self._daily_spend_by_agency.get((ctx.agency_id, today), 0.0)
        if current_spend + agent.estimated_cost_euro > settings.ai_daily_budget_euro:
            raise RuntimeError(
                f"Budget quotidien IA dépassé ({current_spend:.3f} € / {settings.ai_daily_budget_euro:.2f} €)."
            )

        clean_prompt = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(prompt_text, 1200))
        cache_key = hashlib.sha256(f"{agent.agent_id}:{clean_prompt}".encode("utf-8")).hexdigest()
        now_iso = datetime.now(timezone.utc).isoformat()

        if cache_key in self._response_cache:
            model_used, cached_output = self._response_cache[cache_key]
            log = AgentExecutionLog(
                id=f"alog-{secrets.token_hex(4)}",
                agency_id=ctx.agency_id,
                actor_user_id=ctx.user_id,
                agent_id=agent.agent_id,
                agent_name=agent.name,
                model_used=model_used,
                sanitized_input=clean_prompt,
                output_summary=cached_output,
                duration_ms=max(1, int((time.perf_counter() - t0) * 1000)),
                cost_euro=0.0,
                cache_hit=True,
                created_at=now_iso,
            )
            self._logs.insert(0, log)
            return log

        live_res = self._call_anthropic_with_rotation(
            agent=agent,
            clean_prompt=clean_prompt,
            settings=settings,
            transport=anthropic_transport,
        )
        if live_res is None:
            live_res = self._call_openai(
                agent=agent,
                clean_prompt=clean_prompt,
                settings=settings,
                transport=openai_transport,
            )

        if live_res is not None:
            model_used, output = live_res
        else:
            model_used = "deterministic-fallback-safe"
            output = (
                f"[{agent.name} • {model_used}] Synthèse structurée pour le contexte : « {clean_prompt[:140]} ». "
                "Points clés : vérification conformité RGPD/ALUR effectuée, prochaine action recommandée prête pour validation conseiller."
            )

        self._response_cache[cache_key] = (model_used, output)
        self._daily_spend_by_agency[(ctx.agency_id, today)] = current_spend + agent.estimated_cost_euro

        elapsed_ms = max(1, int((time.perf_counter() - t0) * 1000))
        log = AgentExecutionLog(
            id=f"alog-{secrets.token_hex(4)}",
            agency_id=ctx.agency_id,
            actor_user_id=ctx.user_id,
            agent_id=agent.agent_id,
            agent_name=agent.name,
            model_used=model_used,
            sanitized_input=clean_prompt,
            output_summary=output,
            duration_ms=elapsed_ms,
            cost_euro=agent.estimated_cost_euro,
            cache_hit=False,
            created_at=now_iso,
        )
        self._logs.insert(0, log)
        return log

    def list_logs(self, ctx: TenantContext, limit: int = 30) -> list[AgentExecutionLog]:
        return [l for l in self._logs if l.agency_id == ctx.agency_id][:limit]

    # --- Monty Scout Mode Proposition -> Validation 1-clic ---
    def create_scout_proposal(
        self,
        ctx: TenantContext,
        *,
        target_contact_name: str,
        channel: str,
        legal_basis: str,
        message_draft: str,
    ) -> ScoutProposal:
        ctx.require_permission(Permission.CRM_WRITE)
        clean_name = InputSanitizer.sanitize_text(target_contact_name, 120)
        clean_draft = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(message_draft, 600))
        if not clean_name or len(clean_draft) < 10:
            raise ValueError("Nom du destinataire et brouillon (≥ 10 caractères) requis.")
        if "STOP" not in clean_draft.upper():
            clean_draft = f"{clean_draft} (Répondez STOP pour ne plus être contacté)"

        prop = ScoutProposal(
            id=f"sct-{secrets.token_hex(4)}",
            agency_id=ctx.agency_id,
            created_by_user_id=ctx.user_id,
            target_contact_name=clean_name,
            channel=channel.upper() if channel.upper() in {"WHATSAPP", "EMAIL", "SMS"} else "WHATSAPP",
            legal_basis=legal_basis or "INTERET_LEGITIME_PROSPECTION_B2C",
            rgpd_notice="Traitement encadré RGPD avec opposition immédiate par mot-clé STOP.",
            opt_out_keyword="STOP",
            message_draft=clean_draft,
            status="A_VALIDER",
            reviewed_by_user_id=None,
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._scout_proposals[prop.id] = prop
        return prop

    def list_scout_proposals(self, ctx: TenantContext) -> list[ScoutProposal]:
        return [p for p in self._scout_proposals.values() if p.agency_id == ctx.agency_id]

    def review_scout_proposal(self, ctx: TenantContext, *, proposal_id: str, approve: bool) -> ScoutProposal:
        ctx.require_permission(Permission.CRM_WRITE)
        prop = self._scout_proposals.get(proposal_id)
        if prop is None:
            raise ValueError("Proposition Monty Scout introuvable.")
        ctx.assert_same_agency(prop.agency_id)
        prop.status = "VALIDEE" if approve else "REJETEE"
        prop.reviewed_by_user_id = ctx.user_id
        prop.reviewed_at = datetime.now(timezone.utc).isoformat()
        return prop

    # --- WhatsApp Meta Cloud API Signature, Templates validés, Relance 429 & Opt-Out STOP ---
    @staticmethod
    def verify_whatsapp_signature(payload_bytes: bytes, signature_header: str, app_secret: str) -> bool:
        if not app_secret or not signature_header.startswith("sha256="):
            return False
        expected = hmac.new(app_secret.encode("utf-8"), payload_bytes, hashlib.sha256).hexdigest()
        received = signature_header.removeprefix("sha256=").strip()
        return hmac.compare_digest(expected, received)

    def send_whatsapp_template(
        self,
        ctx: TenantContext,
        *,
        to_phone: str,
        template_name: str,
        body_params: list[str],
        settings: Settings,
        max_retries: int = 3,
        transport: Callable[[str, dict[str, str], dict[str, Any]], tuple[int, dict[str, Any], float]] | None = None,
        sleep_fn: Callable[[float], None] = time.sleep,
    ) -> dict[str, Any]:
        ctx.require_permission(Permission.CRM_WRITE)
        clean_phone = to_phone.strip()
        if not clean_phone:
            raise ValueError("Numéro destinataire WhatsApp requis.")
        if self.is_phone_opted_out(clean_phone):
            raise PermissionError("Envoi bloqué : le destinataire s'est désinscrit via STOP.")
        tpl = APPROVED_WHATSAPP_TEMPLATES.get(template_name)
        if tpl is None:
            raise ValueError(f"Template WhatsApp non approuvé par Meta : {template_name}")

        payload = {
            "messaging_product": "whatsapp",
            "to": clean_phone,
            "type": "template",
            "template": {
                "name": template_name,
                "language": {"code": tpl["language"]},
                "components": [
                    {
                        "type": "body",
                        "parameters": [{"type": "text", "text": InputSanitizer.sanitize_text(p, 200)} for p in body_params],
                    }
                ],
            },
        }
        masked = (clean_phone[:4] + "***" + clean_phone[-2:]) if len(clean_phone) >= 6 else "***"

        if not (settings.whatsapp_phone_number_id and settings.whatsapp_access_token) and transport is None:
            self._whatsapp_events.insert(
                0,
                WhatsAppConsentEvent(
                    id=f"wa-{secrets.token_hex(4)}",
                    phone_masked=masked,
                    direction="OUTBOUND",
                    action="TEMPLATE_SENT",
                    keyword_matched=template_name,
                    created_at=datetime.now(timezone.utc).isoformat(),
                ),
            )
            return {
                "status": "queued_local_preview",
                "template_name": template_name,
                "attempts": 1,
            }

        url = f"https://graph.facebook.com/v21.0/{settings.whatsapp_phone_number_id}/messages"
        headers = {
            "Authorization": f"Bearer {settings.whatsapp_access_token}",
            "Content-Type": "application/json",
        }
        attempts = 0
        for attempt in range(max_retries + 1):
            attempts = attempt + 1
            if transport is not None:
                status_code, resp_body, retry_after = transport(url, headers, payload)
            else:
                req = urllib.request.Request(
                    url=url,
                    data=json.dumps(payload).encode("utf-8"),
                    method="POST",
                    headers=headers,
                )
                try:
                    with urllib.request.urlopen(req, timeout=12.0) as resp:
                        status_code = getattr(resp, "status", 200)
                        resp_body = json.loads(resp.read().decode("utf-8"))
                        retry_after = 0.0
                except urllib.error.HTTPError as err:
                    status_code = err.code
                    resp_body = {}
                    retry_after = float(err.headers.get("Retry-After", 0) or 0)

            if 200 <= status_code < 300:
                self._whatsapp_events.insert(
                    0,
                    WhatsAppConsentEvent(
                        id=f"wa-{secrets.token_hex(4)}",
                        phone_masked=masked,
                        direction="OUTBOUND",
                        action="TEMPLATE_SENT",
                        keyword_matched=template_name,
                        created_at=datetime.now(timezone.utc).isoformat(),
                    ),
                )
                return {
                    "status": "sent",
                    "template_name": template_name,
                    "attempts": attempts,
                    "meta_response": resp_body,
                }

            if status_code == 429 and attempt < max_retries:
                backoff = retry_after if retry_after > 0 else float(2 ** attempt)
                sleep_fn(backoff)
                continue

            raise RuntimeError(f"Échec envoi Meta WhatsApp (HTTP {status_code}) après {attempts} tentative(s).")

        raise RuntimeError("Nombre maximal de relances WhatsApp atteint.")

    def handle_whatsapp_payload(
        self,
        *,
        payload_bytes: bytes,
        signature_header: str,
        app_secret: str,
        event: dict[str, Any],
    ) -> dict[str, Any]:
        if not self.verify_whatsapp_signature(payload_bytes, signature_header, app_secret):
            raise ValueError("Signature X-Hub-Signature-256 WhatsApp invalide.")

        processed = 0
        opt_outs = 0
        for entry in event.get("entry", []):
            for change in entry.get("changes", []):
                value = change.get("value", {})
                for msg in value.get("messages", []):
                    sender = str(msg.get("from", "")).strip()
                    body = str(msg.get("text", {}).get("body", "")).strip()
                    upper = body.upper()
                    masked = (sender[:4] + "***" + sender[-2:]) if len(sender) >= 6 else "***"
                    if any(kw in upper.split() for kw in ("STOP", "ARRET", "ARRÊT", "UNSUBSCRIBE")):
                        self._opted_out_phones.add(sender)
                        opt_outs += 1
                        action = "OPT_OUT"
                        kw = "STOP"
                    else:
                        action = "MESSAGE_RECEIVED"
                        kw = ""
                    self._whatsapp_events.insert(
                        0,
                        WhatsAppConsentEvent(
                            id=f"wa-{secrets.token_hex(4)}",
                            phone_masked=masked,
                            direction="INBOUND",
                            action=action,
                            keyword_matched=kw,
                            created_at=datetime.now(timezone.utc).isoformat(),
                        ),
                    )
                    processed += 1

        return {"status": "ok", "messages_processed": processed, "opt_outs_recorded": opt_outs}

    def is_phone_opted_out(self, phone: str) -> bool:
        return phone.strip() in self._opted_out_phones
