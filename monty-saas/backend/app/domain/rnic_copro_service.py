from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import json
import re
import secrets
import time
from typing import Any
from urllib.parse import urlencode
from urllib.request import Request as UrlRequest, urlopen
import uuid

try:
    import httpx
except ImportError:  # pragma: no cover - environnement sans httpx
    httpx = None  # type: ignore[assignment]

from app.core.config import Settings
from app.core.rbac import MontyRole
from app.core.security import InputSanitizer

RNIC_DATASET_API_URL = "https://www.data.gouv.fr/api/1/datasets/registre-national-dimmatriculation-des-coproprietes/"
TABULAR_API_BASE = "https://tabular-api.data.gouv.fr/api/resources"

FREE_RNIC_ROLES: frozenset[MontyRole] = frozenset({
    MontyRole.SUPER_ADMIN,
    MontyRole.DIRECTOR,
    MontyRole.ADMIN,
    MontyRole.AGENT,
    MontyRole.ASSISTANT,
    MontyRole.SELLER,
})

_RESOURCE_CACHE: dict[str, Any] = {"rid": "", "fetched_at": 0.0}


def is_rnic_free_for_role(role: MontyRole | None) -> bool:
    """
    Règle commerciale explicite Monty :
    - GRATUIT pour les Agences (DIRECTOR, ADMIN, AGENT, ASSISTANT, SUPER_ADMIN) et Vendeurs (SELLER)
    - PAYANT pour les Particuliers / Acquéreurs (BUYER ou visiteur public sans pass actif)
    """
    if role is None:
        return False
    return role in FREE_RNIC_ROLES


@dataclass(frozen=True)
class RnicCoproRecord:
    numero_immatriculation: str
    nom_usage: str
    adresse_reference: str
    code_postal: str
    commune: str
    nombre_total_lots: int | None
    nombre_lots_habitation: int | None
    nombre_lots_stationnement: int | None
    periode_construction: str
    type_syndic: str
    syndic_en_exercice: str
    copro_en_difficulte: bool
    date_immatriculation: str
    locked: bool = False


@dataclass(frozen=True)
class RnicPassRecord:
    id: str
    user_id: str | None
    buyer_email: str
    pass_token: str
    stripe_session_id: str
    amount_euro: int
    status: str
    expires_at: str
    created_at: str


def resolve_rnic_resource_id(settings: Settings, http_client: Any = None) -> str:
    """Résout l'UUID de la ressource tabulaire active du RNIC sur data.gouv.fr (ou override env)."""
    if settings.rnic_resource_id:
        return settings.rnic_resource_id

    now = time.time()
    if _RESOURCE_CACHE["rid"] and (now - float(_RESOURCE_CACHE["fetched_at"])) < 43200:
        return str(_RESOURCE_CACHE["rid"])

    try:
        if http_client is not None:
            resp = http_client.get(RNIC_DATASET_API_URL, timeout=10.0)
            if resp.status_code >= 400:
                return ""
            data = resp.json()
        elif httpx is not None:
            with httpx.Client(timeout=10.0) as client:
                resp = client.get(RNIC_DATASET_API_URL)
            if resp.status_code >= 400:
                return ""
            data = resp.json()
        else:
            with urlopen(UrlRequest(RNIC_DATASET_API_URL), timeout=10.0) as r:
                data = json.loads(r.read().decode("utf-8"))
        resources = data.get("resources") or []
        csv_candidates = [
            r for r in resources
            if str(r.get("format") or "").lower() == "csv"
            and r.get("id")
        ]
        # Préférer le fichier principal contenant 'rnic' ou 'copro' (plus gros CSV actif)
        csv_candidates.sort(
            key=lambda r: (
                1 if ("rnic" in str(r.get("title") or "").lower() or "copro" in str(r.get("title") or "").lower()) else 0,
                int(r.get("filesize") or 0),
            ),
            reverse=True,
        )
        if csv_candidates:
            rid = str(csv_candidates[0]["id"])
            _RESOURCE_CACHE["rid"] = rid
            _RESOURCE_CACHE["fetched_at"] = now
            return rid
    except Exception:
        return ""
    return ""


def _pick(row: dict[str, Any], *keys: str) -> Any:
    for k in keys:
        if k in row and row[k] not in (None, ""):
            return row[k]
    return None


def _to_int(val: Any) -> int | None:
    if val in (None, ""):
        return None
    try:
        return int(float(str(val).strip()))
    except (ValueError, TypeError):
        return None


def normalize_rnic_row(row: dict[str, Any]) -> RnicCoproRecord:
    """Normalise une ligne brute de l'API Tabulaire data.gouv.fr RNIC ANAH."""
    immat = InputSanitizer.sanitize_text(
        str(_pick(row, "numero_immatriculation", "num_immatriculation", "immatriculation") or ""),
        40,
    )
    nom = InputSanitizer.sanitize_text(
        str(_pick(row, "nom_usage_copropriete", "nom_usage", "nom_copropriete") or "Copropriété immatriculée"),
        180,
    )
    adresse = InputSanitizer.sanitize_text(
        str(_pick(row, "adresse_reference", "adresse", "numero_voie_adresse_reference") or ""),
        220,
    )
    cp = InputSanitizer.sanitize_text(
        str(_pick(row, "code_postal", "code_postal_reference", "cp") or ""),
        16,
    )
    commune = InputSanitizer.sanitize_text(
        str(_pick(row, "commune", "nom_commune", "libelle_commune") or ""),
        100,
    )
    total_lots = _to_int(_pick(row, "nombre_total_lots", "nombre_de_lots", "nb_total_lots"))
    hab_lots = _to_int(_pick(row, "nombre_lots_habitation", "nb_lots_habitation", "nombre_lots_a_usage_habitation"))
    park_lots = _to_int(_pick(row, "nombre_lots_stationnement", "nb_lots_stationnement"))
    periode = InputSanitizer.sanitize_text(
        str(_pick(row, "periode_construction", "annee_construction", "tranche_construction") or "Non renseignée"),
        80,
    )
    type_syndic = InputSanitizer.sanitize_text(
        str(_pick(row, "type_syndic", "statut_syndic") or "Non précisé"),
        60,
    )
    syndic_name = InputSanitizer.sanitize_text(
        str(_pick(row, "syndic_en_exercice", "nom_syndic", "raison_sociale_syndic") or "Consultable sur fiche RNIC complète"),
        160,
    )
    diff_raw = str(_pick(row, "copro_en_difficulte", "procedure_en_cours", "etat_copro_difficulte") or "").strip().lower()
    in_difficulty = diff_raw in {"oui", "true", "1", "o"}
    date_immat = InputSanitizer.sanitize_text(
        str(_pick(row, "date_immatriculation", "date_creation") or ""),
        32,
    )

    return RnicCoproRecord(
        numero_immatriculation=immat,
        nom_usage=nom,
        adresse_reference=adresse,
        code_postal=cp,
        commune=commune,
        nombre_total_lots=total_lots,
        nombre_lots_habitation=hab_lots,
        nombre_lots_stationnement=park_lots,
        periode_construction=periode,
        type_syndic=type_syndic,
        syndic_en_exercice=syndic_name,
        copro_en_difficulte=in_difficulty,
        date_immatriculation=date_immat,
        locked=False,
    )


def redact_rnic_record_for_unpaid_buyer(rec: RnicCoproRecord) -> dict[str, Any]:
    """
    Aperçu bridé pour un particulier sans Pass RNIC actif :
    prouve l'existence de la copropriété sur la commune/code postal sans dévoiler l'immatriculation,
    l'adresse exacte, le syndic ni les indicateurs de difficulté avant paiement.
    """
    lots = rec.nombre_total_lots or 0
    if lots <= 0:
        band = "Taille non publiée"
    elif lots <= 15:
        band = "Petite copropriété (≤ 15 lots)"
    elif lots <= 50:
        band = "Copropriété moyenne (16–50 lots)"
    else:
        band = "Grande copropriété (> 50 lots)"

    return {
        "numero_immatriculation": "Verrouillé (Pass Particulier requis)",
        "nom_usage": "Copropriété enregistrée au RNIC (ANAH)",
        "adresse_reference": "Adresse exacte masquée — déblocage Pass RNIC",
        "code_postal": rec.code_postal,
        "commune": rec.commune,
        "lot_count_band": band,
        "nombre_total_lots": None,
        "nombre_lots_habitation": None,
        "nombre_lots_stationnement": None,
        "periode_construction": rec.periode_construction,
        "type_syndic": "Masqué",
        "syndic_en_exercice": "Masqué",
        "copro_en_difficulte": None,
        "date_immatriculation": "",
        "locked": True,
    }


def search_rnic_coproprietes(
    *,
    settings: Settings,
    postal_code: str = "",
    commune: str = "",
    immatriculation: str = "",
    address_query: str = "",
    page_size: int = 12,
    has_full_access: bool,
    http_client: Any = None,
) -> dict[str, Any]:
    """Interroge l'API tabulaire officielle data.gouv.fr du RNIC (ANAH)."""
    clean_cp = re.sub(r"[^0-9]", "", postal_code or "")[:5]
    clean_commune = InputSanitizer.sanitize_text(commune, 80)
    clean_immat = InputSanitizer.sanitize_text(immatriculation, 32).upper()
    clean_addr = InputSanitizer.sanitize_text(address_query, 120)

    if not any([clean_cp, clean_commune, clean_immat, clean_addr]):
        raise ValueError("Saisissez au moins un code postal, une commune, une adresse ou un numéro d'immatriculation RNIC.")

    rid = resolve_rnic_resource_id(settings, http_client=http_client)
    if not rid:
        return {
            "status": "resource_unavailable",
            "source": "data.gouv.fr (RNIC ANAH)",
            "access_tier": "FULL" if has_full_access else "PAID_REQUIRED",
            "unlock_price_euro": settings.rnic_particulier_price_euro,
            "message": "Impossible de joindre le catalogue data.gouv.fr ou RNIC_DATA_GOUV_RESOURCE_ID non configuré.",
            "count": 0,
            "records": [],
        }

    url = f"{TABULAR_API_BASE}/{rid}/data/"
    params: dict[str, Any] = {"page": 1, "page_size": max(1, min(25, page_size))}
    if clean_immat:
        params["numero_immatriculation__exact"] = clean_immat
    if clean_cp:
        params["code_postal__exact"] = clean_cp
    if clean_commune:
        params["commune__contains"] = clean_commune
    if clean_addr:
        params["adresse_reference__contains"] = clean_addr

    try:
        if http_client is not None:
            resp = http_client.get(url, params=params, timeout=12.0)
            status_code = resp.status_code
            raw = resp.json() if status_code < 400 else {}
        elif httpx is not None:
            with httpx.Client(timeout=12.0) as client:
                resp = client.get(url, params=params)
            status_code = resp.status_code
            raw = resp.json() if status_code < 400 else {}
        else:
            with urlopen(UrlRequest(f"{url}?{urlencode(params)}"), timeout=12.0) as r:
                status_code = getattr(r, "status", 200)
                raw = json.loads(r.read().decode("utf-8"))
        if status_code >= 400:
            return {
                "status": "api_error",
                "status_code": status_code,
                "source": f"tabular-api.data.gouv.fr ({rid})",
                "access_tier": "FULL" if has_full_access else "PAID_REQUIRED",
                "unlock_price_euro": settings.rnic_particulier_price_euro,
                "count": 0,
                "records": [],
            }
        raw_rows = raw.get("data") or []
        normalized = [normalize_rnic_row(r) for r in raw_rows]
        if has_full_access:
            out_records = [r.__dict__ for r in normalized]
        else:
            out_records = [redact_rnic_record_for_unpaid_buyer(r) for r in normalized]

        return {
            "status": "live",
            "source": "RNIC ANAH / data.gouv.fr",
            "resource_id": rid,
            "access_tier": "FULL" if has_full_access else "PAID_REQUIRED",
            "unlock_price_euro": settings.rnic_particulier_price_euro,
            "count": len(out_records),
            "records": out_records,
        }
    except Exception as exc:
        return {
            "status": "network_unavailable",
            "error": str(exc)[:180],
            "source": "RNIC ANAH / data.gouv.fr",
            "access_tier": "FULL" if has_full_access else "PAID_REQUIRED",
            "unlock_price_euro": settings.rnic_particulier_price_euro,
            "count": 0,
            "records": [],
        }


class RnicAccessService:
    """Gestion en mémoire (fallback sans DATABASE_URL) des Pass RNIC payants pour les particuliers."""

    def __init__(self) -> None:
        self._passes_by_token: dict[str, RnicPassRecord] = {}
        self._passes_by_session: dict[str, RnicPassRecord] = {}

    def issue_active_pass(
        self,
        *,
        user_id: str | None,
        buyer_email: str,
        stripe_session_id: str,
        pass_token: str = "",
        amount_euro: int = 19,
        ttl_days: int = 30,
    ) -> RnicPassRecord:
        existing = self._passes_by_session.get(stripe_session_id) if stripe_session_id else None
        if existing is not None:
            return existing
        now = datetime.now(timezone.utc)
        rec = RnicPassRecord(
            id=f"rnic-{uuid.uuid4().hex[:10]}",
            user_id=user_id,
            buyer_email=InputSanitizer.sanitize_text(buyer_email, 160).lower(),
            pass_token=pass_token.strip() or f"rnic_pass_{secrets.token_urlsafe(24)}",
            stripe_session_id=stripe_session_id,
            amount_euro=amount_euro,
            status="ACTIVE",
            expires_at=(now + timedelta(days=ttl_days)).isoformat(),
            created_at=now.isoformat(),
        )
        self._passes_by_token[rec.pass_token] = rec
        if stripe_session_id:
            self._passes_by_session[stripe_session_id] = rec
        return rec

    def has_active_pass(self, *, pass_token: str = "", user_id: str | None = None) -> bool:
        now = datetime.now(timezone.utc)
        if pass_token and pass_token in self._passes_by_token:
            p = self._passes_by_token[pass_token]
            if p.status == "ACTIVE" and datetime.fromisoformat(p.expires_at) > now:
                return True
        if user_id:
            for p in self._passes_by_token.values():
                if p.user_id == user_id and p.status == "ACTIVE" and datetime.fromisoformat(p.expires_at) > now:
                    return True
        return False
