from __future__ import annotations

import calendar
import re
from datetime import date, datetime, timezone


LEGAL_BASIS_CONSENT = "Consentement explicite (Art. 6.1.a)"
LEGAL_BASIS_PRECONTRACT = "Mesures précontractuelles (Art. 6.1.b)"
LEGAL_BASIS_MANDATE = "Exécution de mandat / contrat (Art. 6.1.b)"
LEGAL_BASIS_LEGITIMATE = "Intérêt légitime prospection (Art. 6.1.f)"
LEGAL_BASIS_OBLIGATION = "Obligation légale ALUR / TRACFIN (Art. 6.1.c)"

PHONE_REGEX = re.compile(r"(?:\+33|0)\s*[1-9](?:[\s.\-]?\d{2}){4}")
EMAIL_REGEX = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
IBAN_REGEX = re.compile(r"\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){2,7}\b")


def retention_months_for(legal_basis: str) -> int:
    lower = legal_basis.lower()
    if "mandat" in lower or "obligation" in lower or "tracfin" in lower:
        return 60
    return 36


def _add_months(ref_date: date, months: int) -> date:
    total_months = (ref_date.year * 12 + (ref_date.month - 1)) + months
    year = total_months // 12
    month = (total_months % 12) + 1
    max_day = calendar.monthrange(year, month)[1]
    return date(year, month, min(ref_date.day, max_day))


def compute_retention_until(legal_basis: str, reference_date: date | None = None) -> date:
    ref = reference_date or date.today()
    return _add_months(ref, retention_months_for(legal_basis))


def is_retention_expired(retention_until: date | str | None, today: date | None = None) -> bool:
    if not retention_until:
        return False
    ref_today = today or date.today()
    if isinstance(retention_until, str):
        clean = retention_until.strip()[:10]
        if len(clean) < 10:
            return False
        retention_date = date.fromisoformat(clean)
    else:
        retention_date = retention_until
    return retention_date < ref_today


def redact_direct_pii_for_ai(text: str) -> str:
    """Minimise les données envoyées aux modèles externes (Art. 5.1.c RGPD)."""
    if not text or not text.strip():
        return ""
    masked = EMAIL_REGEX.sub("[email-masqué-rgpd]", text)
    masked = PHONE_REGEX.sub("[tél-masqué-rgpd]", masked)
    masked = IBAN_REGEX.sub("[iban-masqué-rgpd]", masked)
    return masked


def build_rgpd_portability_export(
    *,
    agency_id: str,
    exported_by_user_id: str,
    subject_type: str,
    subject_payload: dict,
    related_exchanges: list[dict] | None = None,
    related_tasks: list[dict] | None = None,
) -> dict:
    return {
        "exportStandard": "RGPD UE 2016/679 — Droit d'accès & Portabilité (Art. 15 & 20)",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "agencyId": agency_id,
        "exportedBy": exported_by_user_id,
        "subjectType": subject_type,
        "data": subject_payload,
        "exchanges": related_exchanges or [],
        "followUpTasks": related_tasks or [],
    }
