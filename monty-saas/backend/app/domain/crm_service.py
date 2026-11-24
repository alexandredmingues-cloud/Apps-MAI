from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import date, datetime, timedelta, timezone
from enum import Enum
import uuid

from app.core.rbac import Permission, TenantContext
from app.core.rgpd import (
    LEGAL_BASIS_CONSENT,
    LEGAL_BASIS_MANDATE,
    LEGAL_BASIS_PRECONTRACT,
    build_rgpd_portability_export,
    compute_retention_until,
    is_retention_expired,
)
from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode, get_country_pack


class ContactRole(str, Enum):
    BUYER = "BUYER"
    SELLER = "SELLER"
    LEAD = "LEAD"
    PROSPECT = "PROSPECT"
    PARTNER = "PARTNER"


class ExchangeChannel(str, Enum):
    CALL = "CALL"
    EMAIL = "EMAIL"
    VISIT = "VISIT"
    OFFER = "OFFER"
    MEETING = "MEETING"


class FollowUpPriority(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


@dataclass(frozen=True)
class CrmContact:
    id: str
    agency_id: str
    assigned_user_id: str
    country_code: CountryCode
    full_name: str
    email: str
    phone: str
    role: ContactRole
    city: str
    budget_euro: int
    legal_basis: str
    consent_granted: bool
    retention_until: str
    created_at: str
    last_contact_at: str
    has_missing_docs: bool = False
    pending_offer_days: int = 0
    last_visit_days_ago: int | None = None
    is_anonymized: bool = False


@dataclass(frozen=True)
class AdvisorPrivateNote:
    id: str
    agency_id: str
    contact_id: str
    author_user_id: str
    content: str
    created_at: str


@dataclass(frozen=True)
class ContactExchange:
    id: str
    agency_id: str
    contact_id: str
    author_user_id: str
    channel: ExchangeChannel
    summary: str
    created_at: str


@dataclass(frozen=True)
class FollowUpTask:
    id: str
    agency_id: str
    contact_id: str
    contact_name: str
    assigned_user_id: str
    title: str
    reason: str
    priority: FollowUpPriority
    due_date: str
    is_auto_generated: bool = True
    completed: bool = False


class CrmService:
    """Service CRM multi-agences avec isolation tenant, notes privées séparées, relances auto et RGPD."""

    def __init__(self) -> None:
        self._contacts: dict[str, CrmContact] = {}
        self._private_notes: dict[str, list[AdvisorPrivateNote]] = {}
        self._exchanges: dict[str, list[ContactExchange]] = {}
        self._tasks: dict[str, FollowUpTask] = {}
        self._seed_demo_agency_contacts()

    def _seed_demo_agency_contacts(self) -> None:
        today = date.today()
        seeds = [
            CrmContact(
                id="cnt-fr-1",
                agency_id="agency-france-reseau",
                assigned_user_id="usr-director",
                country_code=CountryCode.FR,
                full_name="Camille Laurent",
                email="camille.laurent@exemple.fr",
                phone="06 42 18 90 11",
                role=ContactRole.BUYER,
                city="Lyon",
                budget_euro=420000,
                legal_basis=LEGAL_BASIS_PRECONTRACT,
                consent_granted=True,
                retention_until=compute_retention_until(LEGAL_BASIS_PRECONTRACT, today).isoformat(),
                created_at=(today - timedelta(days=20)).isoformat(),
                last_contact_at=(today - timedelta(days=4)).isoformat(),
                last_visit_days_ago=4,
            ),
            CrmContact(
                id="cnt-fr-2",
                agency_id="agency-france-reseau",
                assigned_user_id="usr-agent",
                country_code=CountryCode.FR,
                full_name="Thomas Mercier",
                email="thomas.mercier@exemple.fr",
                phone="06 77 31 04 55",
                role=ContactRole.SELLER,
                city="Bordeaux",
                budget_euro=540000,
                legal_basis=LEGAL_BASIS_MANDATE,
                consent_granted=True,
                retention_until=compute_retention_until(LEGAL_BASIS_MANDATE, today).isoformat(),
                created_at=(today - timedelta(days=35)).isoformat(),
                last_contact_at=(today - timedelta(days=9)).isoformat(),
                has_missing_docs=True,
                pending_offer_days=8,
            ),
            CrmContact(
                id="cnt-pt-1",
                agency_id="agency-france-reseau",
                assigned_user_id="usr-director",
                country_code=CountryCode.PT,
                full_name="Inês Ferreira",
                email="ines.ferreira@exemplo.pt",
                phone="+351 912 345 678",
                role=ContactRole.BUYER,
                city="Lisboa",
                budget_euro=360000,
                legal_basis=LEGAL_BASIS_CONSENT,
                consent_granted=True,
                retention_until=compute_retention_until(LEGAL_BASIS_CONSENT, today).isoformat(),
                created_at=(today - timedelta(days=10)).isoformat(),
                last_contact_at=(today - timedelta(days=2)).isoformat(),
            ),
        ]
        for c in seeds:
            self._contacts[c.id] = c

    def list_contacts(
        self,
        ctx: TenantContext,
        *,
        role: ContactRole | None = None,
        country_code: CountryCode | None = None,
        query: str = "",
    ) -> list[CrmContact]:
        ctx.require_permission(Permission.CRM_READ)
        q = query.strip().lower()
        results = []
        for c in self._contacts.values():
            if ctx.role.value != "SUPER_ADMIN" and c.agency_id != ctx.agency_id:
                continue
            if role and c.role != role:
                continue
            if country_code and c.country_code != country_code:
                continue
            if q:
                hay = f"{c.full_name} {c.email} {c.city} {c.role.value}".lower()
                if q not in hay:
                    continue
            results.append(c)
        results.sort(key=lambda item: item.last_contact_at, reverse=True)
        return results

    def create_contact(
        self,
        ctx: TenantContext,
        *,
        full_name: str,
        email: str = "",
        phone: str = "",
        role: ContactRole = ContactRole.LEAD,
        country_code: CountryCode = CountryCode.FR,
        city: str = "",
        budget_euro: int = 0,
        legal_basis: str = LEGAL_BASIS_CONSENT,
        consent_granted: bool = True,
        retention_until: str | None = None,
    ) -> CrmContact:
        ctx.require_permission(Permission.CRM_WRITE)
        pack = get_country_pack(country_code)
        safe_name = InputSanitizer.sanitize_text(full_name, 140)
        if not safe_name:
            raise ValueError("Le nom du contact est obligatoire.")
        safe_email = InputSanitizer.sanitize_text(email, 160).lower()
        safe_phone = InputSanitizer.sanitize_text(phone, 40)
        safe_city = InputSanitizer.sanitize_text(city, 100)
        safe_basis = InputSanitizer.sanitize_text(legal_basis, 100) or LEGAL_BASIS_CONSENT
        today = date.today().isoformat()
        ret = retention_until or compute_retention_until(safe_basis).isoformat()

        contact = CrmContact(
            id=f"cnt-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            assigned_user_id=ctx.user_id,
            country_code=pack.country_code,
            full_name=safe_name,
            email=safe_email,
            phone=safe_phone,
            role=role,
            city=safe_city,
            budget_euro=max(0, int(budget_euro)),
            legal_basis=safe_basis,
            consent_granted=consent_granted,
            retention_until=ret,
            created_at=today,
            last_contact_at=today,
        )
        self._contacts[contact.id] = contact
        return contact

    def _get_scoped_contact(self, ctx: TenantContext, contact_id: str) -> CrmContact:
        contact = self._contacts.get(contact_id)
        if contact is None:
            raise ValueError("Contact introuvable.")
        ctx.assert_same_agency(contact.agency_id)
        return contact

    def add_private_note(self, ctx: TenantContext, *, contact_id: str, content: str) -> AdvisorPrivateNote:
        ctx.require_permission(Permission.PRIVATE_NOTES_WRITE)
        contact = self._get_scoped_contact(ctx, contact_id)
        safe_content = InputSanitizer.sanitize_text(content, 2000)
        if not safe_content:
            raise ValueError("Contenu de note privée obligatoire.")
        note = AdvisorPrivateNote(
            id=f"note-{uuid.uuid4().hex[:10]}",
            agency_id=contact.agency_id,
            contact_id=contact.id,
            author_user_id=ctx.user_id,
            content=safe_content,
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._private_notes.setdefault(contact.id, []).append(note)
        return note

    def list_private_notes(self, ctx: TenantContext, *, contact_id: str) -> list[AdvisorPrivateNote]:
        ctx.require_permission(Permission.PRIVATE_NOTES_READ)
        contact = self._get_scoped_contact(ctx, contact_id)
        notes = self._private_notes.get(contact.id, [])
        return [
            n for n in notes
            if ctx.can_read_private_note(author_user_id=n.author_user_id, note_agency_id=n.agency_id)
        ]

    def log_exchange(
        self,
        ctx: TenantContext,
        *,
        contact_id: str,
        channel: ExchangeChannel,
        summary: str,
    ) -> ContactExchange:
        ctx.require_permission(Permission.CRM_WRITE)
        contact = self._get_scoped_contact(ctx, contact_id)
        safe_summary = InputSanitizer.sanitize_text(summary, 1500)
        if not safe_summary:
            raise ValueError("Résumé d'échange obligatoire.")
        now_iso = datetime.now(timezone.utc).isoformat()
        ex = ContactExchange(
            id=f"exc-{uuid.uuid4().hex[:10]}",
            agency_id=contact.agency_id,
            contact_id=contact.id,
            author_user_id=ctx.user_id,
            channel=channel,
            summary=safe_summary,
            created_at=now_iso,
        )
        self._exchanges.setdefault(contact.id, []).append(ex)
        self._contacts[contact.id] = replace(contact, last_contact_at=date.today().isoformat())
        return ex

    def list_exchanges(self, ctx: TenantContext, *, contact_id: str) -> list[ContactExchange]:
        ctx.require_permission(Permission.CRM_READ)
        contact = self._get_scoped_contact(ctx, contact_id)
        return list(self._exchanges.get(contact.id, []))

    def generate_automatic_followups(self, ctx: TenantContext, *, today: date | None = None) -> list[FollowUpTask]:
        ctx.require_permission(Permission.CRM_READ)
        ref_today = today or date.today()
        generated: list[FollowUpTask] = []

        for c in self.list_contacts(ctx):
            if c.is_anonymized:
                continue
            if c.last_visit_days_ago is not None and c.last_visit_days_ago >= 3:
                t = FollowUpTask(
                    id=f"tsk-visit-{c.id}",
                    agency_id=c.agency_id,
                    contact_id=c.id,
                    contact_name=c.full_name,
                    assigned_user_id=c.assigned_user_id,
                    title=f"Débriefing visite J+{c.last_visit_days_ago} — {c.full_name}",
                    reason="Visite réalisée sans retour enregistré depuis ≥ 3 jours.",
                    priority=FollowUpPriority.HIGH,
                    due_date=ref_today.isoformat(),
                )
                self._tasks[t.id] = t
                generated.append(t)

            if c.pending_offer_days >= 7:
                t = FollowUpTask(
                    id=f"tsk-offer-{c.id}",
                    agency_id=c.agency_id,
                    contact_id=c.id,
                    contact_name=c.full_name,
                    assigned_user_id=c.assigned_user_id,
                    title=f"Relance offre / décision J+{c.pending_offer_days} — {c.full_name}",
                    reason="Proposition ou négociation ouverte depuis ≥ 7 jours.",
                    priority=FollowUpPriority.HIGH,
                    due_date=ref_today.isoformat(),
                )
                self._tasks[t.id] = t
                generated.append(t)

            if c.has_missing_docs:
                t = FollowUpTask(
                    id=f"tsk-docs-{c.id}",
                    agency_id=c.agency_id,
                    contact_id=c.id,
                    contact_name=c.full_name,
                    assigned_user_id=c.assigned_user_id,
                    title=f"Pièces réglementaires à compléter — {c.full_name}",
                    reason="Diagnostics, DPE/SCE ou pièces pré-contractuelles manquantes.",
                    priority=FollowUpPriority.MEDIUM,
                    due_date=(ref_today + timedelta(days=2)).isoformat(),
                )
                self._tasks[t.id] = t
                generated.append(t)

            if is_retention_expired(c.retention_until, ref_today):
                t = FollowUpTask(
                    id=f"tsk-rgpd-{c.id}",
                    agency_id=c.agency_id,
                    contact_id=c.id,
                    contact_name=c.full_name,
                    assigned_user_id=c.assigned_user_id,
                    title=f"Purge RGPD requise — {c.full_name}",
                    reason=f"Durée de conservation expirée ({c.retention_until}).",
                    priority=FollowUpPriority.HIGH,
                    due_date=ref_today.isoformat(),
                )
                self._tasks[t.id] = t
                generated.append(t)

        return generated

    def complete_task(self, ctx: TenantContext, task_id: str) -> FollowUpTask:
        ctx.require_permission(Permission.CRM_WRITE)
        task = self._tasks.get(task_id)
        if task is None:
            raise ValueError("Tâche introuvable.")
        ctx.assert_same_agency(task.agency_id)
        updated = replace(task, completed=True)
        self._tasks[task_id] = updated
        return updated

    def export_contact_rgpd(self, ctx: TenantContext, *, contact_id: str) -> dict:
        ctx.require_permission(Permission.CRM_READ)
        contact = self._get_scoped_contact(ctx, contact_id)
        exchanges = [e.__dict__ for e in self._exchanges.get(contact.id, [])]
        tasks = [t.__dict__ for t in self._tasks.values() if t.contact_id == contact.id]
        # Règle critique : les notes privées internes ne sont JAMAIS incluses dans l'export portabilité client
        return build_rgpd_portability_export(
            agency_id=contact.agency_id,
            exported_by_user_id=ctx.user_id,
            subject_type="CRM_CONTACT",
            subject_payload={
                "id": contact.id,
                "country_code": contact.country_code.value,
                "full_name": contact.full_name,
                "email": contact.email,
                "phone": contact.phone,
                "role": contact.role.value,
                "city": contact.city,
                "budget_euro": contact.budget_euro,
                "legal_basis": contact.legal_basis,
                "retention_until": contact.retention_until,
            },
            related_exchanges=exchanges,
            related_tasks=tasks,
        )

    def execute_rgpd_purge(self, ctx: TenantContext, *, today: date | None = None) -> int:
        ctx.require_permission(Permission.CRM_DELETE_RGPD)
        ref_today = today or date.today()
        purged_count = 0
        for cid, contact in list(self._contacts.items()):
            if ctx.role.value != "SUPER_ADMIN" and contact.agency_id != ctx.agency_id:
                continue
            if not contact.is_anonymized and is_retention_expired(contact.retention_until, ref_today):
                self._contacts[cid] = replace(
                    contact,
                    full_name="[Contact anonymisé RGPD]",
                    email="",
                    phone="",
                    is_anonymized=True,
                )
                self._private_notes.pop(cid, None)
                purged_count += 1
        return purged_count
