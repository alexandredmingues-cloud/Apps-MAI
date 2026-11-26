from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from email.message import EmailMessage
from email.utils import formatdate
import json
import re
import smtplib
import ssl
from typing import Any
from urllib.parse import quote_plus, urlencode
from urllib.request import Request as UrlRequest, urlopen
import uuid

try:
    import httpx
except ImportError:  # pragma: no cover - environnement sans httpx
    httpx = None  # type: ignore[assignment]

from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.core.security import InputSanitizer

DINUM_SEARCH_URL = "https://recherche-entreprises.api.gouv.fr/search"
NOTAIRES_FR_DIRECTORY_URL = "https://www.notaires.fr/fr/directory"


def _strip_crlf(value: str, max_len: int = 240) -> str:
    cleaned = re.sub(r"[\r\n]+", " ", value or "").strip()
    return cleaned[:max_len]


@dataclass(frozen=True)
class OfficialNotaryOffice:
    siret: str
    siren: str
    office_name: str
    street_address: str
    postal_code: str
    city: str
    department_code: str
    latitude: float | None
    longitude: float | None
    csn_directory_url: str
    source: str = "DINUM_API_GOUV_6910Z"


@dataclass
class AgencyNotaryContact:
    id: str
    agency_id: str
    siret: str
    office_name: str
    notary_name: str
    email: str
    phone: str
    street_address: str
    postal_code: str
    city: str
    department_code: str
    source: str
    email_verified: bool
    is_official_notaire_domain: bool
    notes: str
    created_at: str


@dataclass
class NotaryTransmissionRecord:
    id: str
    agency_id: str
    notary_contact_id: str | None
    transaction_id: str | None
    property_id: str | None
    dossier_ref: str
    transmission_type: str
    recipient_email: str
    recipient_office: str
    recipient_address: str
    subject: str
    checklist_items: list[str]
    eml_content: str
    delivery_status: str
    delivery_provider_id: str
    created_at: str


def parse_dinum_notary_results(payload: dict[str, Any]) -> list[OfficialNotaryOffice]:
    """Parse la réponse JSON officielle de recherche-entreprises.api.gouv.fr (NAF 69.10Z)."""
    results: list[OfficialNotaryOffice] = []
    seen_siret: set[str] = set()
    for item in payload.get("results", []) or []:
        office_name = InputSanitizer.sanitize_text(str(item.get("nom_complet") or item.get("nom_raison_sociale") or ""), 180)
        if not office_name:
            continue

        etabs = item.get("matching_etablissements") or []
        if not etabs and isinstance(item.get("siege"), dict):
            etabs = [item["siege"]]

        for etab in etabs[:3]:
            naf = str(etab.get("activite_principale") or item.get("activite_principale") or "").strip()
            name_upper = office_name.upper()
            if naf and naf != "69.10Z" and "NOTAIRE" not in name_upper and "NOTARIAL" not in name_upper:
                continue
            siret = re.sub(r"[^0-9]", "", str(etab.get("siret") or ""))[:14]
            if siret and siret in seen_siret:
                continue
            if siret:
                seen_siret.add(siret)

            street = InputSanitizer.sanitize_text(str(etab.get("adresse") or etab.get("geo_adresse") or ""), 240)
            postal = InputSanitizer.sanitize_text(str(etab.get("code_postal") or ""), 16)
            city = InputSanitizer.sanitize_text(str(etab.get("libelle_commune") or ""), 100)
            dept = postal[:2] if len(postal) >= 2 else ""
            if postal.startswith(("97", "98")) and len(postal) >= 3:
                dept = postal[:3]
            lat_raw = etab.get("latitude")
            lon_raw = etab.get("longitude")
            try:
                lat = float(lat_raw) if lat_raw not in (None, "") else None
                lon = float(lon_raw) if lon_raw not in (None, "") else None
            except (TypeError, ValueError):
                lat, lon = None, None

            csn_q = quote_plus(f"{office_name} {postal} {city}".strip())
            results.append(
                OfficialNotaryOffice(
                    siret=siret,
                    siren=str(item.get("siren") or "")[:9],
                    office_name=office_name,
                    street_address=street,
                    postal_code=postal,
                    city=city,
                    department_code=dept,
                    latitude=lat,
                    longitude=lon,
                    csn_directory_url=f"{NOTAIRES_FR_DIRECTORY_URL}?search={csn_q}",
                )
            )
    return results


def search_french_notary_offices(
    *,
    query: str = "",
    department_code: str = "",
    postal_code: str = "",
    per_page: int = 15,
    http_client: Any = None,
) -> dict[str, Any]:
    """
    Interroge l'API officielle DINUM/INSEE (recherche-entreprises.api.gouv.fr, code NAF 69.10Z)
    pour lister les études notariales actives en France (nom, SIRET, adresse officielle).
    L'e-mail @notaires.fr n'étant pas publié en open-data par le CSN (anti-phishing), un lien direct
    vers l'annuaire officiel notaires.fr est fourni pour vérification et ajout au carnet d'agence.
    """
    clean_q = InputSanitizer.sanitize_text(query, 100)
    clean_dept = re.sub(r"[^0-9ABab]", "", department_code or "")[:3].upper()
    clean_cp = re.sub(r"[^0-9]", "", postal_code or "")[:5]
    q_term = f"notaire {clean_q}".strip() if "notair" not in clean_q.lower() else clean_q

    params: dict[str, Any] = {
        "q": q_term or "notaire",
        "activite_principale": "69.10Z",
        "etat_administratif": "A",
        "per_page": max(5, min(25, per_page)),
    }
    if clean_dept:
        params["departement"] = clean_dept
    if clean_cp:
        params["code_postal"] = clean_cp

    try:
        if http_client is not None:
            resp = http_client.get(DINUM_SEARCH_URL, params=params, timeout=10.0)
            status_code = resp.status_code
            payload_json = resp.json() if status_code < 400 else {}
        elif httpx is not None:
            with httpx.Client(timeout=10.0) as client:
                resp = client.get(DINUM_SEARCH_URL, params=params)
            status_code = resp.status_code
            payload_json = resp.json() if status_code < 400 else {}
        else:
            req = UrlRequest(f"{DINUM_SEARCH_URL}?{urlencode(params)}")
            with urlopen(req, timeout=10.0) as r:
                status_code = getattr(r, "status", 200)
                payload_json = json.loads(r.read().decode("utf-8"))
        if status_code >= 400:
            return {
                "status": "api_error",
                "status_code": status_code,
                "source": "recherche-entreprises.api.gouv.fr",
                "email_notice": "Les e-mails @notaires.fr ne sont pas diffusés en open-data : vérifiez sur notaires.fr et enregistrez-les dans votre carnet agence.",
                "offices": [],
            }
        offices = parse_dinum_notary_results(payload_json)
        return {
            "status": "live",
            "source": "recherche-entreprises.api.gouv.fr (NAF 69.10Z)",
            "email_notice": "Les e-mails @notaires.fr ne sont pas diffusés en open-data : utilisez le lien Annuaire CSN notaires.fr puis enregistrez l'adresse vérifiée dans le carnet d'agence.",
            "count": len(offices),
            "offices": [o.__dict__ for o in offices],
        }
    except Exception as exc:
        return {
            "status": "network_unavailable",
            "error": str(exc)[:180],
            "source": "recherche-entreprises.api.gouv.fr",
            "email_notice": "Service DINUM momentanément inaccessible depuis cet environnement.",
            "count": 0,
            "offices": [],
        }


def build_notary_transmission_eml(
    *,
    from_email: str,
    recipient_email: str,
    recipient_office: str,
    recipient_address: str,
    dossier_ref: str,
    transmission_type: str,
    property_title: str,
    property_address: str,
    seller_name: str,
    buyer_name: str,
    agreed_price_euro: int,
    target_date: str,
    checklist_items: list[str],
    notes: str = "",
    pre_etat_date_html: str = "",
) -> tuple[str, str]:
    """Construit un courrier RFC-2822 (.eml) prêt à l'envoi pour ouverture de dossier ou signature de compromis."""
    type_labels = {
        "OUVERTURE_DOSSIER": "Demande d'ouverture de dossier de vente",
        "SIGNATURE_COMPROMIS": "Transmission pièces signature de compromis",
        "COMPLEMENT_ALUR": "Complément dossier Loi ALUR / Pré-État Daté",
    }
    label = type_labels.get(transmission_type, "Transmission dossier immobilier")
    safe_ref = _strip_crlf(dossier_ref, 60)
    safe_title = _strip_crlf(property_title, 120)
    subject = f"[Monty] {label} — {safe_ref} ({safe_title})".strip()

    body_lines = [
        f"Maître, ({_strip_crlf(recipient_office, 140)})",
        f"Adresse de l'étude : {_strip_crlf(recipient_address, 200) or 'Non précisée'}",
        "",
        f"Objet : {label} — Référence dossier {safe_ref}",
        "",
        "1. IDENTIFICATION DE L'OPÉRATION",
        f"- Bien concerné : {safe_title}",
        f"- Adresse du bien : {_strip_crlf(property_address, 200) or 'À confirmer'}",
        f"- Vendeur(s) : {_strip_crlf(seller_name, 140) or 'À compléter'}",
        f"- Acquéreur(s) : {_strip_crlf(buyer_name, 140) or 'À compléter'}",
        f"- Prix convenu / Offre acceptée : {agreed_price_euro:,} €".replace(",", " "),
        f"- Échéance souhaitée : {_strip_crlf(target_date, 30) or 'Dès que possible'}",
        "",
        "2. PIÈCES DU DOSSIER & CONFORMITÉ L. 721-2 CCH",
    ]
    if checklist_items:
        for item in checklist_items:
            body_lines.append(f"  [x] {_strip_crlf(item, 160)}")
    else:
        body_lines.append("  [ ] Liste des pièces en cours de consolidation dans Monty.")

    if notes.strip():
        body_lines.extend(["", "3. OBSERVATIONS CONSEILLER", InputSanitizer.sanitize_text(notes, 1200)])

    body_lines.extend(
        [
            "",
            "Restant à votre disposition pour fixer la date de signature ou compléter toute pièce demandée.",
            "Cordialement,",
            "L'équipe Monty Immobilier (transmission sécurisée)",
        ]
    )

    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = _strip_crlf(from_email or "noreply@monty-immobilier.fr", 140)
    msg["To"] = _strip_crlf(recipient_email or "etude@notaires.fr", 160)
    msg["Date"] = formatdate(localtime=True)
    msg["X-Monty-Dossier-Ref"] = safe_ref
    msg.set_content("\n".join(body_lines), charset="utf-8")

    if pre_etat_date_html.strip():
        msg.add_attachment(
            pre_etat_date_html.encode("utf-8"),
            maintype="text",
            subtype="html",
            filename=f"pre-etat-date-{re.sub(r'[^a-zA-Z0-9_-]', '-', safe_ref)}.html",
        )

    return subject, msg.as_string()


def dispatch_notary_email_if_configured(
    *,
    recipient_email: str,
    subject: str,
    eml_content: str,
    settings: Settings,
) -> tuple[str, str]:
    """
    Tente un envoi réel uniquement si Resend API ou SMTP est configuré.
    Retourne (delivery_status, provider_id). Ne prétend jamais avoir envoyé sans transport réel.
    """
    clean_to = _strip_crlf(recipient_email, 160)
    if not clean_to or "@" not in clean_to:
        return "READY_EML_DOWNLOAD", ""

    if settings.resend_api_key:
        from_addr = settings.smtp_from_email or "Monty Immobilier <onboarding@resend.dev>"
        body_payload = {
            "from": from_addr,
            "to": [clean_to],
            "subject": subject,
            "text": eml_content.split("\n\n", 1)[-1][:12000],
        }
        try:
            if httpx is not None:
                with httpx.Client(timeout=12.0) as client:
                    resp = client.post(
                        "https://api.resend.com/emails",
                        headers={"Authorization": f"Bearer {settings.resend_api_key}"},
                        json=body_payload,
                    )
                if resp.status_code < 400:
                    return "SENT_RESEND", str(resp.json().get("id", ""))
                return "FAILED", f"resend_http_{resp.status_code}"
            req = UrlRequest(
                "https://api.resend.com/emails",
                data=json.dumps(body_payload).encode("utf-8"),
                headers={
                    "Authorization": f"Bearer {settings.resend_api_key}",
                    "Content-Type": "application/json",
                },
                method="POST",
            )
            with urlopen(req, timeout=12.0) as r:
                res_data = json.loads(r.read().decode("utf-8"))
                return "SENT_RESEND", str(res_data.get("id", ""))
        except Exception as exc:
            return "FAILED", f"resend_err:{str(exc)[:80]}"

    if settings.smtp_host and settings.smtp_from_email:
        try:
            context = ssl.create_default_context()
            with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=12) as server:
                server.starttls(context=context)
                if settings.smtp_user and settings.smtp_password:
                    server.login(settings.smtp_user, settings.smtp_password)
                server.sendmail(settings.smtp_from_email, [clean_to], eml_content.encode("utf-8"))
            return "SENT_SMTP", f"smtp:{settings.smtp_host}"
        except Exception as exc:
            return "FAILED", f"smtp_err:{str(exc)[:80]}"

    return "READY_EML_DOWNLOAD", ""


class NotaryBookService:
    """Gestion en mémoire (fallback sans DATABASE_URL) du carnet notaires agence et des transmissions."""

    def __init__(self) -> None:
        self._contacts: dict[str, AgencyNotaryContact] = {}
        self._transmissions: dict[str, NotaryTransmissionRecord] = {}

    def list_notaries(self, ctx: TenantContext) -> list[AgencyNotaryContact]:
        ctx.require_permission(Permission.PROPERTY_READ)
        return sorted(
            [c for c in self._contacts.values() if c.agency_id == ctx.agency_id],
            key=lambda c: c.created_at,
            reverse=True,
        )

    def save_notary(
        self,
        ctx: TenantContext,
        *,
        siret: str = "",
        office_name: str,
        notary_name: str = "",
        email: str = "",
        phone: str = "",
        street_address: str = "",
        postal_code: str = "",
        city: str = "",
        source: str = "DINUM_API_GOUV",
        notes: str = "",
    ) -> AgencyNotaryContact:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        clean_office = InputSanitizer.sanitize_text(office_name, 180)
        if len(clean_office) < 2:
            raise ValueError("Le nom de l'étude notariale est obligatoire.")
        clean_email = InputSanitizer.sanitize_text(email, 160).lower()
        if clean_email and ("@" not in clean_email or "." not in clean_email.split("@")[-1]):
            raise ValueError("Adresse e-mail notariale invalide.")
        clean_cp = re.sub(r"[^0-9]", "", postal_code or "")[:5]
        dept = clean_cp[:3] if clean_cp.startswith(("97", "98")) and len(clean_cp) >= 3 else clean_cp[:2]

        # Upsert par (agency_id, office_name, postal_code)
        for existing in self._contacts.values():
            if (
                existing.agency_id == ctx.agency_id
                and existing.office_name.lower() == clean_office.lower()
                and existing.postal_code == clean_cp
            ):
                existing.siret = re.sub(r"[^0-9]", "", siret or existing.siret)[:14]
                existing.notary_name = InputSanitizer.sanitize_text(notary_name, 160) or existing.notary_name
                existing.email = clean_email or existing.email
                existing.phone = InputSanitizer.sanitize_text(phone, 40) or existing.phone
                existing.street_address = InputSanitizer.sanitize_text(street_address, 240) or existing.street_address
                existing.city = InputSanitizer.sanitize_text(city, 100) or existing.city
                existing.department_code = dept or existing.department_code
                existing.email_verified = bool(existing.email)
                existing.is_official_notaire_domain = existing.email.endswith("@notaires.fr")
                existing.notes = InputSanitizer.sanitize_text(notes, 600) or existing.notes
                return existing

        rec = AgencyNotaryContact(
            id=f"not-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            siret=re.sub(r"[^0-9]", "", siret or "")[:14],
            office_name=clean_office,
            notary_name=InputSanitizer.sanitize_text(notary_name, 160),
            email=clean_email,
            phone=InputSanitizer.sanitize_text(phone, 40),
            street_address=InputSanitizer.sanitize_text(street_address, 240),
            postal_code=clean_cp,
            city=InputSanitizer.sanitize_text(city, 100),
            department_code=dept,
            source=InputSanitizer.sanitize_text(source, 40) or "DINUM_API_GOUV",
            email_verified=bool(clean_email),
            is_official_notaire_domain=clean_email.endswith("@notaires.fr"),
            notes=InputSanitizer.sanitize_text(notes, 600),
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._contacts[rec.id] = rec
        return rec

    def create_transmission(
        self,
        ctx: TenantContext,
        *,
        notary_contact_id: str | None = None,
        transaction_id: str | None = None,
        property_id: str | None = None,
        dossier_ref: str,
        transmission_type: str,
        recipient_email: str,
        recipient_office: str,
        recipient_address: str,
        property_title: str,
        property_address: str = "",
        seller_name: str = "",
        buyer_name: str = "",
        agreed_price_euro: int = 0,
        target_date: str = "",
        checklist_items: list[str] | None = None,
        notes: str = "",
        pre_etat_date_html: str = "",
        send_now: bool = False,
        settings: Settings | None = None,
    ) -> NotaryTransmissionRecord:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        if transmission_type not in ("OUVERTURE_DOSSIER", "SIGNATURE_COMPROMIS", "COMPLEMENT_ALUR"):
            raise ValueError("Type de transmission notariale invalide.")

        items = list(checklist_items or [])
        subject, eml = build_notary_transmission_eml(
            from_email=(settings.smtp_from_email if settings else "") or "dossier@monty-immobilier.fr",
            recipient_email=recipient_email,
            recipient_office=recipient_office,
            recipient_address=recipient_address,
            dossier_ref=dossier_ref,
            transmission_type=transmission_type,
            property_title=property_title,
            property_address=property_address,
            seller_name=seller_name,
            buyer_name=buyer_name,
            agreed_price_euro=agreed_price_euro,
            target_date=target_date,
            checklist_items=items,
            notes=notes,
            pre_etat_date_html=pre_etat_date_html,
        )
        if send_now and settings is not None:
            delivery_status, provider_id = dispatch_notary_email_if_configured(
                recipient_email=recipient_email,
                subject=subject,
                eml_content=eml,
                settings=settings,
            )
        else:
            delivery_status, provider_id = "READY_EML_DOWNLOAD", ""

        rec = NotaryTransmissionRecord(
            id=f"ntx-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            notary_contact_id=notary_contact_id,
            transaction_id=transaction_id,
            property_id=property_id,
            dossier_ref=InputSanitizer.sanitize_text(dossier_ref, 80),
            transmission_type=transmission_type,
            recipient_email=InputSanitizer.sanitize_text(recipient_email, 160).lower(),
            recipient_office=InputSanitizer.sanitize_text(recipient_office, 180),
            recipient_address=InputSanitizer.sanitize_text(recipient_address, 260),
            subject=subject,
            checklist_items=[InputSanitizer.sanitize_text(c, 160) for c in items],
            eml_content=eml,
            delivery_status=delivery_status,
            delivery_provider_id=provider_id,
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._transmissions[rec.id] = rec
        return rec

    def list_transmissions(self, ctx: TenantContext) -> list[NotaryTransmissionRecord]:
        ctx.require_permission(Permission.PROPERTY_READ)
        return sorted(
            [t for t in self._transmissions.values() if t.agency_id == ctx.agency_id],
            key=lambda t: t.created_at,
            reverse=True,
        )
