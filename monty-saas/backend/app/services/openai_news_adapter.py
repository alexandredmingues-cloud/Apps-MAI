from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass

from app.core.config import Settings
from app.core.rgpd import redact_direct_pii_for_ai
from app.core.security import InputSanitizer
from app.domain.news_hub import LEGAL_DISCLAIMER_DEFAULT


@dataclass(frozen=True)
class DualAudienceBriefing:
    mode: str  # "openai_live" | "deterministic_fallback"
    model_used: str
    sanitized_topic: str
    pro_analysis: str
    pro_commercial_arguments: list[str]
    public_summary: str
    public_takeaways: list[str]
    legal_disclaimer: str


DUAL_AUDIENCE_SYSTEM_PROMPT = (
    "Tu es un analyste immobilier rigoureux au sein de Monty SaaS. "
    "À partir d'une information immobilière vérifiée et sourcée fournie par l'utilisateur, "
    "tu produis STRICTEMENT un objet JSON contenant deux formats complémentaires : "
    "(1) 'pro_analysis' (analyse technique approfondie destinée aux conseillers immobiliers) et "
    "'pro_commercial_arguments' (liste de 3 leviers d'accompagnement client concrets), "
    "(2) 'public_summary' (explication claire et vulgarisée pour les particuliers) et "
    "'public_takeaways' (liste de 3 points pratiques). "
    "RÈGLES IMPÉRATIVES : n'invente jamais de chiffres, taux, dates ou références de lois absents du texte source ; "
    "n'affirme jamais qu'une interprétation vaut règle juridique officielle ; mentionne les limites indicatives."
)


def build_openai_payload(
    *,
    topic: str,
    source_excerpt: str,
    location_label: str,
    model: str,
) -> dict:
    clean_topic = redact_direct_pii_for_ai(InputSanitizer.sanitize_ai_prompt_input(topic, 300))
    clean_excerpt = redact_direct_pii_for_ai(InputSanitizer.sanitize_ai_prompt_input(source_excerpt, 1800))
    clean_location = InputSanitizer.sanitize_text(location_label, 120) or "National"

    return {
        "model": model,
        "temperature": 0.2,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": DUAL_AUDIENCE_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": (
                    f"Territoire : {clean_location}\n"
                    f"Sujet : {clean_topic}\n"
                    f"Éléments sourcés : {clean_excerpt}"
                ),
            },
        ],
    }


def _deterministic_dual_fallback(
    *,
    clean_topic: str,
    clean_excerpt: str,
    clean_location: str,
    reason: str,
) -> DualAudienceBriefing:
    preview = clean_excerpt[:260].rstrip()
    return DualAudienceBriefing(
        mode="deterministic_fallback",
        model_used=reason,
        sanitized_topic=clean_topic,
        pro_analysis=(
            f"[Synthèse déterministe — {clean_location}] {clean_topic}. "
            f"Éléments sources retenus : {preview}. "
            "Recommandation conseiller : vérifier l'impact chiffré sur le plan de financement HCSF (35 %), "
            "le prix au m² DVF local et l'échéancier réglementaire (DPE / copropriété) avant tout engagement."
        ),
        pro_commercial_arguments=[
            "Appuyer l'échange client sur les données vérifiées de la source citée sans extrapolation chiffrée.",
            "Valider l'impact budgétaire ou locatif avec les simulateurs Monty (HCSF, frais de notaire, rendement).",
            "Orienter le client vers un notaire ou conseiller fiscal dès qu'une situation patrimoniale particulière se présente.",
        ],
        public_summary=(
            f"En bref ({clean_location}) — {clean_topic} : {preview}. "
            "Avant de prendre une décision d'achat, de vente ou d'investissement, testez votre scénario dans les simulateurs Monty "
            "et appuyez-vous sur les textes officiels sourcés."
        ),
        public_takeaways=[
            "Vérifiez la date et la source officielle de l'information avant d'agir.",
            "Mesurez l'impact concret sur votre budget mensuel, vos frais de notaire ou votre fiscalité.",
            "Cette synthèse informative ne remplace pas l'avis d'un notaire ou d'un professionnel habilité.",
        ],
        legal_disclaimer=LEGAL_DISCLAIMER_DEFAULT,
    )


def generate_dual_audience_briefing(
    *,
    settings: Settings,
    topic: str,
    source_excerpt: str,
    location_label: str = "National",
    complex_mode: bool = False,
) -> DualAudienceBriefing:
    clean_topic = redact_direct_pii_for_ai(InputSanitizer.sanitize_ai_prompt_input(topic, 300))
    clean_excerpt = redact_direct_pii_for_ai(InputSanitizer.sanitize_ai_prompt_input(source_excerpt, 1800))
    clean_location = InputSanitizer.sanitize_text(location_label, 120) or "National"

    if not settings.openai_api_key:
        return _deterministic_dual_fallback(
            clean_topic=clean_topic,
            clean_excerpt=clean_excerpt,
            clean_location=clean_location,
            reason="local-deterministic (OPENAI_API_KEY non configurée)",
        )

    model = settings.openai_model_complex if complex_mode else settings.openai_model_fast
    payload = build_openai_payload(
        topic=clean_topic,
        source_excerpt=clean_excerpt,
        location_label=clean_location,
        model=model,
    )

    req = urllib.request.Request(
        url="https://api.openai.com/v1/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={
            "Authorization": f"Bearer {settings.openai_api_key}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=18.0) as resp:
            raw = json.loads(resp.read().decode("utf-8"))
        content = raw["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        return DualAudienceBriefing(
            mode="openai_live",
            model_used=model,
            sanitized_topic=clean_topic,
            pro_analysis=InputSanitizer.sanitize_text(str(parsed.get("pro_analysis", "")), 3000),
            pro_commercial_arguments=[
                InputSanitizer.sanitize_text(str(x), 280)
                for x in parsed.get("pro_commercial_arguments", [])
                if str(x).strip()
            ][:5],
            public_summary=InputSanitizer.sanitize_text(str(parsed.get("public_summary", "")), 2500),
            public_takeaways=[
                InputSanitizer.sanitize_text(str(x), 280)
                for x in parsed.get("public_takeaways", [])
                if str(x).strip()
            ][:5],
            legal_disclaimer=LEGAL_DISCLAIMER_DEFAULT,
        )
    except (urllib.error.URLError, TimeoutError, KeyError, ValueError, json.JSONDecodeError):
        return _deterministic_dual_fallback(
            clean_topic=clean_topic,
            clean_excerpt=clean_excerpt,
            clean_location=clean_location,
            reason="fallback-erreur-reseau-openai",
        )
