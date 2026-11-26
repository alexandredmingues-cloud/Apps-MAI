from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any
import uuid

from app.core.rbac import MontyRole, Permission, TenantContext
from app.core.security import InputSanitizer


@dataclass
class AgencyNotificationRecord:
    id: str
    agency_id: str
    event_type: str
    severity: str
    title: str
    detail: str
    link_section: str
    target_id: str
    is_read: bool
    created_at: str


@dataclass
class AgencyFeatureFlags:
    agency_id: str
    auto_matching_enabled: bool = True
    ai_copywriting_enabled: bool = True
    rnic_free_for_sellers: bool = True
    public_appointment_booking: bool = True
    notary_auto_task_on_compromis: bool = True
    updated_at: str = ""


@dataclass
class ClientFavoriteRecord:
    id: str
    agency_id: str
    user_id: str
    listing_id: str
    listing_snapshot: dict[str, Any]
    created_at: str


@dataclass
class ClientDocumentRecord:
    id: str
    agency_id: str
    owner_user_id: str
    title: str
    doc_category: str
    file_name: str
    mime_type: str
    file_size_bytes: int
    notes: str
    status: str
    created_at: str


class InterconnectionsService:
    """
    Bus d'interconnexions en mémoire (fallback sans DATABASE_URL) :
    - Notifications agence en temps réel (nouveau bien -> matching, lead web / RDV, compromis notaire)
    - Tâches automatiques inter-modules (visite réalisée, compromis -> ouverture dossier notaire)
    - Paramètres Feature Flags par agence
    - Portail Particulier / Vendeur : Favoris & Coffre documentaire
    """

    def __init__(self) -> None:
        self._notifications: list[AgencyNotificationRecord] = [
            AgencyNotificationRecord(
                id="notif-seed-1",
                agency_id="agency-france-reseau",
                event_type="SYSTEM_READY",
                severity="INFO",
                title="Interconnexion Monty activée",
                detail="Les nouveaux mandats, demandes de visite web et passages en compromis génèrent automatiquement des notifications et tâches.",
                link_section="dashboard",
                target_id="",
                is_read=False,
                created_at="2026-11-25T09:00:00+00:00",
            )
        ]
        self._flags_by_agency: dict[str, AgencyFeatureFlags] = {}
        self._favorites: list[ClientFavoriteRecord] = []
        self._client_documents: list[ClientDocumentRecord] = []
        self._auto_tasks: list[dict[str, Any]] = []

    def emit_notification(
        self,
        *,
        agency_id: str,
        event_type: str,
        severity: str,
        title: str,
        detail: str,
        link_section: str = "dashboard",
        target_id: str = "",
    ) -> AgencyNotificationRecord:
        rec = AgencyNotificationRecord(
            id=f"notif-{uuid.uuid4().hex[:10]}",
            agency_id=agency_id,
            event_type=InputSanitizer.sanitize_text(event_type, 48),
            severity=severity if severity in ("INFO", "HIGH", "SUCCESS") else "INFO",
            title=InputSanitizer.sanitize_text(title, 200),
            detail=InputSanitizer.sanitize_text(detail, 500),
            link_section=InputSanitizer.sanitize_text(link_section, 40) or "dashboard",
            target_id=InputSanitizer.sanitize_text(target_id, 120),
            is_read=False,
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._notifications.insert(0, rec)
        return rec

    def add_auto_task(
        self,
        *,
        agency_id: str,
        title: str,
        reason: str,
        priority: str = "HIGH",
        due_date: str = "",
        link_section: str = "documents",
    ) -> dict[str, Any]:
        item = {
            "id": f"atask-{uuid.uuid4().hex[:10]}",
            "agency_id": agency_id,
            "title": InputSanitizer.sanitize_text(title, 200),
            "reason": InputSanitizer.sanitize_text(reason, 400),
            "priority": priority,
            "due_date": due_date or datetime.now(timezone.utc).date().isoformat(),
            "link_section": link_section,
            "completed": False,
        }
        self._auto_tasks.insert(0, item)
        return item

    def list_notifications(self, ctx: TenantContext, *, unread_only: bool = False) -> list[AgencyNotificationRecord]:
        ctx.require_permission(Permission.PROPERTY_READ)
        items = [n for n in self._notifications if n.agency_id == ctx.agency_id or ctx.role == MontyRole.SUPER_ADMIN]
        if unread_only:
            items = [n for n in items if not n.is_read]
        return items[:80]

    def mark_notification_read(self, ctx: TenantContext, notification_id: str) -> AgencyNotificationRecord:
        ctx.require_permission(Permission.PROPERTY_READ)
        for n in self._notifications:
            if n.id == notification_id:
                ctx.assert_same_agency(n.agency_id)
                n.is_read = True
                return n
        raise ValueError("Notification introuvable.")

    def list_auto_tasks(self, ctx: TenantContext) -> list[dict[str, Any]]:
        ctx.require_permission(Permission.PROPERTY_READ)
        return [t for t in self._auto_tasks if t["agency_id"] == ctx.agency_id or ctx.role == MontyRole.SUPER_ADMIN]

    def get_feature_flags(self, ctx: TenantContext) -> AgencyFeatureFlags:
        ctx.require_permission(Permission.PROPERTY_READ)
        return self._flags_by_agency.get(
            ctx.agency_id,
            AgencyFeatureFlags(
                agency_id=ctx.agency_id,
                updated_at=datetime.now(timezone.utc).isoformat(),
            ),
        )

    def update_feature_flags(
        self,
        ctx: TenantContext,
        *,
        auto_matching_enabled: bool,
        ai_copywriting_enabled: bool,
        rnic_free_for_sellers: bool,
        public_appointment_booking: bool,
        notary_auto_task_on_compromis: bool,
    ) -> AgencyFeatureFlags:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        flags = AgencyFeatureFlags(
            agency_id=ctx.agency_id,
            auto_matching_enabled=auto_matching_enabled,
            ai_copywriting_enabled=ai_copywriting_enabled,
            rnic_free_for_sellers=rnic_free_for_sellers,
            public_appointment_booking=public_appointment_booking,
            notary_auto_task_on_compromis=notary_auto_task_on_compromis,
            updated_at=datetime.now(timezone.utc).isoformat(),
        )
        self._flags_by_agency[ctx.agency_id] = flags
        return flags

    # --- PORTAIL PARTICULIER / VENDEUR : FAVORIS & DOCUMENTS ---
    def _require_portal_or_pro(self, ctx: TenantContext) -> None:
        if not (
            ctx.has_permission(Permission.BUYER_PORTAL_SELF)
            or ctx.has_permission(Permission.SELLER_PORTAL_SELF)
            or ctx.has_permission(Permission.PROPERTY_READ)
        ):
            raise PermissionError("Authentification requise pour accéder à votre espace personnel.")

    def list_favorites(self, ctx: TenantContext) -> list[ClientFavoriteRecord]:
        self._require_portal_or_pro(ctx)
        return [
            f for f in self._favorites if f.agency_id == ctx.agency_id and f.user_id == ctx.user_id
        ]

    def upsert_favorite(
        self,
        ctx: TenantContext,
        *,
        listing_id: str,
        listing_snapshot: dict[str, Any],
    ) -> ClientFavoriteRecord:
        self._require_portal_or_pro(ctx)
        clean_id = InputSanitizer.sanitize_text(listing_id, 120)
        if not clean_id:
            raise ValueError("Identifiant annonce obligatoire.")
        for f in self._favorites:
            if f.user_id == ctx.user_id and f.listing_id == clean_id:
                f.listing_snapshot = listing_snapshot
                return f
        rec = ClientFavoriteRecord(
            id=f"fav-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            user_id=ctx.user_id,
            listing_id=clean_id,
            listing_snapshot=listing_snapshot,
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._favorites.insert(0, rec)
        return rec

    def remove_favorite(self, ctx: TenantContext, *, listing_id: str) -> bool:
        self._require_portal_or_pro(ctx)
        before = len(self._favorites)
        self._favorites = [
            f
            for f in self._favorites
            if not (f.user_id == ctx.user_id and f.listing_id == listing_id and f.agency_id == ctx.agency_id)
        ]
        return len(self._favorites) < before

    def list_client_documents(self, ctx: TenantContext) -> list[ClientDocumentRecord]:
        self._require_portal_or_pro(ctx)
        if ctx.has_permission(Permission.PROPERTY_READ):
            return [d for d in self._client_documents if d.agency_id == ctx.agency_id]
        return [
            d for d in self._client_documents if d.agency_id == ctx.agency_id and d.owner_user_id == ctx.user_id
        ]

    def add_client_document(
        self,
        ctx: TenantContext,
        *,
        title: str,
        doc_category: str,
        file_name: str,
        mime_type: str = "application/pdf",
        file_size_bytes: int = 0,
        notes: str = "",
    ) -> ClientDocumentRecord:
        self._require_portal_or_pro(ctx)
        clean_title = InputSanitizer.sanitize_text(title, 180)
        clean_file = InputSanitizer.sanitize_text(file_name, 200)
        if not clean_title or not clean_file:
            raise ValueError("Titre et nom de fichier obligatoires.")
        rec = ClientDocumentRecord(
            id=f"doc-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            owner_user_id=ctx.user_id,
            title=clean_title,
            doc_category=InputSanitizer.sanitize_text(doc_category, 48).upper() or "DOSSIER",
            file_name=clean_file,
            mime_type=InputSanitizer.sanitize_text(mime_type, 100) or "application/pdf",
            file_size_bytes=max(0, int(file_size_bytes)),
            notes=InputSanitizer.sanitize_text(notes, 600),
            status="DEPOSE",
            created_at=datetime.now(timezone.utc).isoformat(),
        )
        self._client_documents.insert(0, rec)
        self.emit_notification(
            agency_id=ctx.agency_id,
            event_type="CLIENT_DOCUMENT_UPLOADED",
            severity="INFO",
            title=f"Nouvelle pièce client déposée : {clean_title}",
            detail=f"Catégorie {rec.doc_category} — {clean_file}",
            link_section="documents",
            target_id=rec.id,
        )
        return rec


interconnections_service = InterconnectionsService()

