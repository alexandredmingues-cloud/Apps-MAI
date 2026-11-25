from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timezone
from enum import Enum
import uuid

from app.core.rbac import Permission, TenantContext
from app.core.rgpd import LEGAL_BASIS_CONSENT, redact_direct_pii_for_ai
from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode
from app.domain.crm_service import ContactRole, CrmService


class LeadKind(str, Enum):
    BUYER_CONCIERGE = "BUYER_CONCIERGE"
    SEARCH_ALERT = "SEARCH_ALERT"
    SELLER_VALUATION = "SELLER_VALUATION"


class LeadStatus(str, Enum):
    NEW = "NEW"
    CONTACTED = "CONTACTED"
    CONVERTED = "CONVERTED"
    ARCHIVED = "ARCHIVED"


class VisitStatus(str, Enum):
    PLANIFIEE = "PLANIFIEE"
    REALISEE = "REALISEE"
    ANNULEE = "ANNULEE"


class TransactionStage(str, Enum):
    OFFRE = "OFFRE"
    COMPROMIS = "COMPROMIS"
    ACTE = "ACTE"


@dataclass
class PortfolioProperty:
    id: str
    agency_id: str
    title: str
    sector: str
    property_type: str
    price: int
    surface: int
    bedrooms: int
    dpe: str
    mandate: str
    docs_ratio: float
    created_at: str


@dataclass
class PortfolioVisit:
    id: str
    agency_id: str
    property_id: str
    property_title: str
    buyer_name: str
    date_label: str
    status: VisitStatus
    feedback: str
    created_at: str


@dataclass
class PortfolioTransaction:
    id: str
    agency_id: str
    property_id: str
    property_title: str
    buyer_name: str
    offer_euro: int
    stage: TransactionStage
    notary_target_date: str
    created_at: str


@dataclass
class PublicInboundLead:
    id: str
    agency_id: str | None
    crm_contact_id: str | None
    lead_kind: LeadKind
    country_code: CountryCode
    full_name: str
    email: str
    phone: str
    city: str
    budget_euro: int
    min_rooms: int
    search_transport_mode: str
    max_travel_minutes: int
    horizon: str
    property_title: str
    notes: str
    rgpd_consent_granted: bool
    rgpd_consent_at: str
    status: LeadStatus
    created_at: str


class PortfolioAndLeadsService:
    """
    Service domaine en mémoire (fallback sans DATABASE_URL) reflétant 1:1
    le Portefeuille Pro (Biens, Visites, Transactions) et les Leads Publics RGPD.
    """

    def __init__(self, crm_service: CrmService | None = None) -> None:
        self._crm = crm_service
        self._properties: dict[str, PortfolioProperty] = {}
        self._visits: dict[str, PortfolioVisit] = {}
        self._transactions: dict[str, PortfolioTransaction] = {}
        self._leads: dict[str, PublicInboundLead] = {}
        self._seed_demo_agency()

    def _seed_demo_agency(self) -> None:
        aid = "agency-france-reseau"
        p1 = PortfolioProperty(
            id="prop-lyon-t3",
            agency_id=aid,
            title="T3 Lumineux Terrasse Part-Dieu",
            sector="Lyon 3e Part-Dieu",
            property_type="Appartement",
            price=418000,
            surface=78,
            bedrooms=2,
            dpe="B",
            mandate="EXCLUSIF",
            docs_ratio=0.85,
            created_at="2026-11-20T10:00:00+00:00",
        )
        p2 = PortfolioProperty(
            id="prop-montpellier-maison",
            agency_id=aid,
            title="Maison Contemporaine Jardin Beaux-Arts",
            sector="Montpellier Beaux-Arts",
            property_type="Maison",
            price=595000,
            surface=132,
            bedrooms=4,
            dpe="C",
            mandate="EXCLUSIF",
            docs_ratio=0.72,
            created_at="2026-11-21T10:00:00+00:00",
        )
        self._properties[p1.id] = p1
        self._properties[p2.id] = p2

        v1 = PortfolioVisit(
            id="vis-1",
            agency_id=aid,
            property_id=p1.id,
            property_title=p1.title,
            buyer_name="Camille Laurent",
            date_label="Jeudi 17h30",
            status=VisitStatus.PLANIFIEE,
            feedback="Financement HCSF vérifié à 31,4 %.",
            created_at="2026-11-22T14:00:00+00:00",
        )
        self._visits[v1.id] = v1

        t1 = PortfolioTransaction(
            id="tx-1",
            agency_id=aid,
            property_id=p2.id,
            property_title=p2.title,
            buyer_name="Marc & Claire V.",
            offer_euro=582000,
            stage=TransactionStage.COMPROMIS,
            notary_target_date="2026-12-20",
            created_at="2026-11-22T16:00:00+00:00",
        )
        self._transactions[t1.id] = t1

    # --- PORTEFEUILLE BIENS ---
    def list_properties(self, ctx: TenantContext) -> list[PortfolioProperty]:
        ctx.require_permission(Permission.PROPERTY_READ)
        return sorted(
            [p for p in self._properties.values() if p.agency_id == ctx.agency_id],
            key=lambda p: p.created_at,
            reverse=True,
        )

    def create_property(
        self,
        ctx: TenantContext,
        *,
        title: str,
        sector: str,
        property_type: str = "Appartement",
        price: int,
        surface: int,
        bedrooms: int = 2,
        dpe: str = "C",
        mandate: str = "EXCLUSIF",
        docs_ratio: float = 0.70,
    ) -> PortfolioProperty:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        clean_title = InputSanitizer.sanitize_text(title, 160)
        clean_sector = InputSanitizer.sanitize_text(sector, 100)
        if not clean_title or not clean_sector or price < 10_000 or surface < 5:
            raise ValueError("Titre, secteur, prix (>= 10 000 €) et surface (>= 5 m²) requis.")
        mandate_norm = "EXCLUSIF" if mandate.strip().upper().startswith("EXCLU") else "SIMPLE"
        prop = PortfolioProperty(
            id=f"prop-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            title=clean_title,
            sector=clean_sector,
            property_type=InputSanitizer.sanitize_text(property_type, 60) or "Appartement",
            price=price,
            surface=surface,
            bedrooms=max(0, min(30, bedrooms)),
            dpe=(dpe.strip().upper()[:2] or "C"),
            mandate=mandate_norm,
            docs_ratio=max(0.0, min(1.0, float(docs_ratio))),
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._properties[prop.id] = prop
        return prop

    # --- VISITES ---
    def list_visits(self, ctx: TenantContext) -> list[PortfolioVisit]:
        ctx.require_permission(Permission.PROPERTY_READ)
        return sorted(
            [v for v in self._visits.values() if v.agency_id == ctx.agency_id],
            key=lambda v: v.created_at,
            reverse=True,
        )

    def create_visit(
        self,
        ctx: TenantContext,
        *,
        property_id: str,
        buyer_name: str,
        date_label: str,
        feedback: str = "",
    ) -> PortfolioVisit:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        prop = self._properties.get(property_id)
        if prop is None:
            raise ValueError("Bien introuvable.")
        ctx.assert_same_agency(prop.agency_id)
        clean_buyer = InputSanitizer.sanitize_text(buyer_name, 140)
        clean_date = InputSanitizer.sanitize_text(date_label, 120)
        if not clean_buyer or not clean_date:
            raise ValueError("Nom de l'acquéreur et créneau de visite requis.")
        visit = PortfolioVisit(
            id=f"vis-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            property_id=prop.id,
            property_title=prop.title,
            buyer_name=clean_buyer,
            date_label=clean_date,
            status=VisitStatus.PLANIFIEE,
            feedback=InputSanitizer.sanitize_text(feedback, 500) or "Créneau confirmé depuis le cockpit.",
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._visits[visit.id] = visit
        return visit

    def update_visit_status(
        self,
        ctx: TenantContext,
        *,
        visit_id: str,
        status: VisitStatus,
        feedback: str | None = None,
    ) -> PortfolioVisit:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        visit = self._visits.get(visit_id)
        if visit is None:
            raise ValueError("Visite introuvable.")
        ctx.assert_same_agency(visit.agency_id)
        visit.status = status
        if feedback is not None and feedback.strip():
            visit.feedback = InputSanitizer.sanitize_text(feedback, 500)
        return visit

    # --- TRANSACTIONS / OFFRES ---
    def list_transactions(self, ctx: TenantContext) -> list[PortfolioTransaction]:
        ctx.require_permission(Permission.PROPERTY_READ)
        return sorted(
            [t for t in self._transactions.values() if t.agency_id == ctx.agency_id],
            key=lambda t: t.created_at,
            reverse=True,
        )

    def create_transaction(
        self,
        ctx: TenantContext,
        *,
        property_id: str,
        buyer_name: str,
        offer_euro: int,
        stage: TransactionStage = TransactionStage.OFFRE,
        notary_target_date: str = "",
    ) -> PortfolioTransaction:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        prop = self._properties.get(property_id)
        if prop is None:
            raise ValueError("Bien introuvable.")
        ctx.assert_same_agency(prop.agency_id)
        clean_buyer = InputSanitizer.sanitize_text(buyer_name, 140)
        if not clean_buyer or offer_euro < 10_000:
            raise ValueError("Acquéreur et montant d'offre (>= 10 000 €) requis.")
        tx = PortfolioTransaction(
            id=f"tx-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            property_id=prop.id,
            property_title=prop.title,
            buyer_name=clean_buyer,
            offer_euro=offer_euro,
            stage=stage,
            notary_target_date=notary_target_date.strip() or date.today().isoformat(),
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._transactions[tx.id] = tx
        return tx

    def update_transaction_stage(
        self,
        ctx: TenantContext,
        *,
        tx_id: str,
        stage: TransactionStage,
    ) -> PortfolioTransaction:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        tx = self._transactions.get(tx_id)
        if tx is None:
            raise ValueError("Transaction introuvable.")
        ctx.assert_same_agency(tx.agency_id)
        tx.stage = stage
        return tx

    # --- LEADS PUBLICS ENTRANTS (RGPD) ---
    def submit_public_lead(
        self,
        *,
        agency_id: str | None = None,
        lead_kind: LeadKind,
        country_code: CountryCode = CountryCode.FR,
        full_name: str = "",
        email: str,
        phone: str = "",
        city: str = "",
        budget_euro: int = 0,
        min_rooms: int = 1,
        search_transport_mode: str = "TRAM",
        max_travel_minutes: int = 30,
        horizon: str = "",
        property_title: str = "",
        notes: str = "",
        rgpd_consent_granted: bool,
    ) -> PublicInboundLead:
        if rgpd_consent_granted is not True:
            raise ValueError("Le consentement explicite RGPD (Art. 6.1.a) est obligatoire.")
        clean_email = InputSanitizer.sanitize_text(email, 160).lower()
        if "@" not in clean_email or "." not in clean_email.split("@")[-1]:
            raise ValueError("Adresse e-mail valide obligatoire.")

        target_agency = agency_id.strip() if agency_id and agency_id.strip() else "agency-france-reseau"
        display_name = InputSanitizer.sanitize_text(full_name, 140) or clean_email.split("@")[0]
        clean_city = InputSanitizer.sanitize_text(city, 120)
        clean_notes = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(notes, 1500))
        clean_prop_title = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(property_title, 180))
        now_iso = datetime.now(timezone.utc).isoformat()

        crm_contact_id: str | None = None
        if self._crm is not None:
            from app.core.rbac import MontyRole

            bridge_ctx = TenantContext(
                user_id="usr-director",
                agency_id=target_agency,
                role=MontyRole.DIRECTOR,
            )
            contact = self._crm.create_contact(
                bridge_ctx,
                full_name=display_name,
                email=clean_email,
                phone=InputSanitizer.sanitize_text(phone, 40),
                role=ContactRole.SELLER if lead_kind == LeadKind.SELLER_VALUATION else ContactRole.BUYER,
                country_code=country_code,
                city=clean_city,
                budget_euro=max(0, budget_euro),
                legal_basis=LEGAL_BASIS_CONSENT,
                consent_granted=True,
            )
            crm_contact_id = contact.id

        lead = PublicInboundLead(
            id=f"lead-{uuid.uuid4().hex[:10]}",
            agency_id=target_agency,
            crm_contact_id=crm_contact_id,
            lead_kind=lead_kind,
            country_code=country_code,
            full_name=display_name,
            email=clean_email,
            phone=InputSanitizer.sanitize_text(phone, 40),
            city=clean_city,
            budget_euro=max(0, budget_euro),
            min_rooms=max(1, min(30, min_rooms)),
            search_transport_mode=InputSanitizer.sanitize_text(search_transport_mode, 24) or "TRAM",
            max_travel_minutes=max(5, min(240, max_travel_minutes)),
            horizon=InputSanitizer.sanitize_text(horizon, 80),
            property_title=clean_prop_title,
            notes=clean_notes,
            rgpd_consent_granted=True,
            rgpd_consent_at=now_iso,
            status=LeadStatus.NEW,
            created_at=now_iso,
        )
        self._leads[lead.id] = lead
        return lead

    def list_public_leads(
        self,
        ctx: TenantContext,
        *,
        status: LeadStatus | None = None,
    ) -> list[PublicInboundLead]:
        ctx.require_permission(Permission.CRM_READ)
        items = [l for l in self._leads.values() if l.agency_id == ctx.agency_id]
        if status is not None:
            items = [l for l in items if l.status == status]
        return sorted(items, key=lambda l: l.created_at, reverse=True)

    def update_public_lead_status(
        self,
        ctx: TenantContext,
        *,
        lead_id: str,
        status: LeadStatus,
    ) -> PublicInboundLead:
        ctx.require_permission(Permission.CRM_WRITE)
        lead = self._leads.get(lead_id)
        if lead is None:
            raise ValueError("Demande entrante introuvable.")
        ctx.assert_same_agency(lead.agency_id or "")
        lead.status = status
        return lead
