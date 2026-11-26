from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.api.v1.crm_router import _crm, _leads_service
from app.api.v1.deps import get_current_tenant, get_db_session_factory, require_permission
from app.api.v1.legal_agents_router import _agents_service
from app.api.v1.marketplace_router import _market
from app.api.v1.portfolio_router import _portfolio_service
from app.core.rbac import Permission, TenantContext
from app.db.postgres_interconnections_repository import PostgresInterconnectionsRepository
from app.domain.interconnections_service import interconnections_service

interconnect_router = APIRouter(tags=["Interconnexions, Portail Particulier & Gouvernance"])


def _require_portal_or_pro(ctx: TenantContext) -> None:
    if not (
        ctx.has_permission(Permission.BUYER_PORTAL_SELF)
        or ctx.has_permission(Permission.SELLER_PORTAL_SELF)
        or ctx.has_permission(Permission.PROPERTY_READ)
    ):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Authentification requise pour accéder à l'espace personnel.",
        )


# ==============================================================================
# 1. NOTIFICATIONS AGENCE & AGENDA UNIFIÉ PRO
# ==============================================================================


@interconnect_router.get("/notifications")
async def list_agency_notifications(
    unread_only: bool = Query(default=False),
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        items = await PostgresInterconnectionsRepository(sf).list_notifications(ctx, unread_only=unread_only)
    else:
        items = interconnections_service.list_notifications(ctx, unread_only=unread_only)
    unread_count = sum(1 for n in items if not n.is_read)
    return {
        "count": len(items),
        "unread_count": unread_count,
        "notifications": [n.__dict__ for n in items],
    }


@interconnect_router.post("/notifications/{notif_id}/read")
async def mark_agency_notification_read(
    notif_id: str,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if sf is not None:
            rec = await PostgresInterconnectionsRepository(sf).mark_notification_read(ctx, notif_id)
        else:
            rec = interconnections_service.mark_notification_read(ctx, notif_id)
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return rec.__dict__


@interconnect_router.get("/agenda")
async def get_unified_agency_agenda(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        events = await PostgresInterconnectionsRepository(sf).get_unified_agenda(ctx)
        return {"count": len(events), "items": events}

    items: list[dict[str, Any]] = []
    for v in _portfolio_service.list_visits(ctx):
        if v.status.value == "ANNULEE":
            continue
        items.append(
            {
                "id": f"visit:{v.id}",
                "kind": "VISITE",
                "title": f"Visite — {v.property_title}",
                "subtitle": f"{v.buyer_name} • {v.date_label}",
                "date_iso": v.created_at[:10],
                "status": v.status.value,
                "priority": "NORMAL",
                "link_section": "visits",
            }
        )
    for t in _crm.generate_automatic_followups(ctx):
        if t.completed:
            continue
        items.append(
            {
                "id": f"task:{t.id}",
                "kind": "RELANCE",
                "title": t.title,
                "subtitle": t.reason,
                "date_iso": t.due_date,
                "status": "OPEN",
                "priority": t.priority.value,
                "link_section": "crm",
            }
        )
    for at in interconnections_service.list_auto_tasks(ctx):
        if at.get("completed"):
            continue
        items.append(
            {
                "id": f"autotask:{at['id']}",
                "kind": "RELANCE",
                "title": at["title"],
                "subtitle": at["reason"],
                "date_iso": at["due_date"],
                "status": "OPEN",
                "priority": at["priority"],
                "link_section": at.get("link_section", "documents"),
            }
        )
    for tx in _portfolio_service.list_transactions(ctx):
        if not tx.notary_target_date:
            continue
        items.append(
            {
                "id": f"notary:{tx.id}",
                "kind": "NOTAIRE",
                "title": f"Échéance Notaire ({tx.stage.value}) — {tx.property_title}",
                "subtitle": f"{tx.buyer_name} • {tx.offer_euro:,} €".replace(",", " "),
                "date_iso": tx.notary_target_date,
                "status": tx.stage.value,
                "priority": "HIGH",
                "link_section": "transactions",
            }
        )

    items.sort(key=lambda x: x["date_iso"] or "9999-12-31")
    return {"count": len(items), "items": items}


# ==============================================================================
# 2. PORTAIL PARTICULIER & VENDEUR (FAVORIS, COFFRE DOCUMENTAIRE, ANNONCES)
# ==============================================================================


@interconnect_router.get("/portal/favorites")
async def list_portal_favorites(
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    _require_portal_or_pro(ctx)
    if sf is not None:
        items = await PostgresInterconnectionsRepository(sf).list_favorites(ctx)
    else:
        items = interconnections_service.list_favorites(ctx)
    return {"count": len(items), "favorites": [f.__dict__ for f in items]}


class UpsertFavoriteRequest(BaseModel):
    listing_id: str = Field(min_length=2, max_length=120)
    listing_snapshot: dict[str, Any] = Field(default_factory=dict)


@interconnect_router.post("/portal/favorites", status_code=status.HTTP_201_CREATED)
async def add_portal_favorite(
    body: UpsertFavoriteRequest,
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    _require_portal_or_pro(ctx)
    try:
        if sf is not None:
            rec = await PostgresInterconnectionsRepository(sf).upsert_favorite(
                ctx, listing_id=body.listing_id, listing_snapshot=body.listing_snapshot
            )
        else:
            rec = interconnections_service.upsert_favorite(
                ctx, listing_id=body.listing_id, listing_snapshot=body.listing_snapshot
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return rec.__dict__


@interconnect_router.delete("/portal/favorites/{listing_id}")
async def remove_portal_favorite(
    listing_id: str,
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    _require_portal_or_pro(ctx)
    if sf is not None:
        removed = await PostgresInterconnectionsRepository(sf).remove_favorite(ctx, listing_id=listing_id)
    else:
        removed = interconnections_service.remove_favorite(ctx, listing_id=listing_id)
    return {"removed": removed, "listing_id": listing_id}


@interconnect_router.get("/portal/documents")
async def list_portal_documents(
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    _require_portal_or_pro(ctx)
    if sf is not None:
        docs = await PostgresInterconnectionsRepository(sf).list_client_documents(ctx)
    else:
        docs = interconnections_service.list_client_documents(ctx)
    return {"count": len(docs), "documents": [d.__dict__ for d in docs]}


class AddClientDocumentRequest(BaseModel):
    title: str = Field(min_length=2, max_length=180)
    doc_category: str = Field(default="FINANCEMENT", max_length=48)
    file_name: str = Field(min_length=2, max_length=200)
    mime_type: str = Field(default="application/pdf", max_length=100)
    file_size_bytes: int = Field(default=0, ge=0, le=100_000_000)
    notes: str = Field(default="", max_length=600)


@interconnect_router.post("/portal/documents", status_code=status.HTTP_201_CREATED)
async def add_portal_document(
    body: AddClientDocumentRequest,
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    _require_portal_or_pro(ctx)
    try:
        if sf is not None:
            doc = await PostgresInterconnectionsRepository(sf).add_client_document(
                ctx,
                title=body.title,
                doc_category=body.doc_category,
                file_name=body.file_name,
                mime_type=body.mime_type,
                file_size_bytes=body.file_size_bytes,
                notes=body.notes,
            )
        else:
            doc = interconnections_service.add_client_document(
                ctx,
                title=body.title,
                doc_category=body.doc_category,
                file_name=body.file_name,
                mime_type=body.mime_type,
                file_size_bytes=body.file_size_bytes,
                notes=body.notes,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return doc.__dict__


@interconnect_router.get("/portal/seller-listings")
async def list_portal_seller_listings(
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    _require_portal_or_pro(ctx)
    if sf is not None:
        items = await PostgresInterconnectionsRepository(sf).list_seller_listings(ctx)
        return {"count": len(items), "listings": items}

    all_listings = _market.list_all_listings() if hasattr(_market, "list_all_listings") else getattr(_market, "_listings", [])
    if ctx.has_permission(Permission.PROPERTY_READ):
        filtered = [l for l in all_listings if l.agency_id == ctx.agency_id]
    else:
        filtered = [
            l for l in all_listings
            if l.author_user_id == ctx.user_id or (l.agency_id == ctx.agency_id and l.author_user_id == "")
        ]
    return {
        "count": len(filtered),
        "listings": [
            {
                "id": l.id,
                "title": l.title,
                "city": l.city,
                "sector": l.sector_label,
                "property_type": l.property_kind,
                "price_euro": l.price_euro,
                "surface_m2": l.surface_m2,
                "rooms": l.rooms,
                "dpe": l.dpe,
                "moderation_status": l.moderation_status.value,
                "moderation_note": l.moderation_note,
                "created_at": l.published_at,
            }
            for l in filtered
        ],
    }


# ==============================================================================
# 3. TABLEAU DE BORD KPI LIVE, JOURNAL D'AUDIT IMMUABLE & FEATURE FLAGS
# ==============================================================================


@interconnect_router.get("/admin/overview")
async def get_admin_overview(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        return await PostgresInterconnectionsRepository(sf).get_admin_overview(ctx)

    props = _portfolio_service.list_properties(ctx)
    contacts = _crm.list_contacts(ctx)
    visits = [v for v in _portfolio_service.list_visits(ctx) if v.status.value != "ANNULEE"]
    txs = _portfolio_service.list_transactions(ctx)
    open_tasks = len([t for t in _crm.generate_automatic_followups(ctx) if not t.completed]) + len(
        interconnections_service.list_auto_tasks(ctx)
    )
    new_leads = len([l for l in _leads_service.list_public_leads(ctx) if l.status.value == "NEW"])
    unread_notifs = len(interconnections_service.list_notifications(ctx, unread_only=True))
    ai_spend = _agents_service.get_daily_spend(ctx.agency_id)
    return {
        "agency_id": ctx.agency_id,
        "properties_count": len(props),
        "active_contacts_count": len(contacts),
        "scheduled_visits_count": len(visits),
        "transactions_count": len(txs),
        "pipeline_volume_euro": sum(t.offer_euro for t in txs),
        "open_followups_count": open_tasks,
        "new_public_leads_count": new_leads,
        "unread_notifications_count": unread_notifs,
        "ai_daily_spend_euro": ai_spend,
    }


@interconnect_router.get("/admin/audit-logs")
async def list_agency_audit_logs(
    limit: int = Query(default=40, ge=5, le=120),
    ctx: TenantContext = Depends(require_permission(Permission.AUDIT_LOGS_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        logs = await PostgresInterconnectionsRepository(sf).list_audit_logs(ctx, limit=limit)
        return {"count": len(logs), "logs": logs}

    notifs = interconnections_service.list_notifications(ctx)
    logs = [
        {
            "id": n.id,
            "agency_id": n.agency_id,
            "actor_user_id": ctx.user_id,
            "actor_role": ctx.role.value,
            "action_type": n.event_type,
            "target_type": n.link_section.upper(),
            "target_id": n.target_id,
            "detail": f"{n.title} — {n.detail}",
            "created_at": n.created_at,
        }
        for n in notifs[:limit]
    ]
    return {"count": len(logs), "logs": logs}


@interconnect_router.get("/admin/feature-flags")
async def get_agency_feature_flags(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        flags = await PostgresInterconnectionsRepository(sf).get_feature_flags(ctx)
    else:
        flags = interconnections_service.get_feature_flags(ctx)
    return flags.__dict__


class UpdateFeatureFlagsRequest(BaseModel):
    auto_matching_enabled: bool = True
    ai_copywriting_enabled: bool = True
    rnic_free_for_sellers: bool = True
    public_appointment_booking: bool = True
    notary_auto_task_on_compromis: bool = True


@interconnect_router.put("/admin/feature-flags")
async def update_agency_feature_flags(
    body: UpdateFeatureFlagsRequest,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if sf is not None:
            flags = await PostgresInterconnectionsRepository(sf).update_feature_flags(
                ctx,
                auto_matching_enabled=body.auto_matching_enabled,
                ai_copywriting_enabled=body.ai_copywriting_enabled,
                rnic_free_for_sellers=body.rnic_free_for_sellers,
                public_appointment_booking=body.public_appointment_booking,
                notary_auto_task_on_compromis=body.notary_auto_task_on_compromis,
            )
        else:
            flags = interconnections_service.update_feature_flags(
                ctx,
                auto_matching_enabled=body.auto_matching_enabled,
                ai_copywriting_enabled=body.ai_copywriting_enabled,
                rnic_free_for_sellers=body.rnic_free_for_sellers,
                public_appointment_booking=body.public_appointment_booking,
                notary_auto_task_on_compromis=body.notary_auto_task_on_compromis,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    return flags.__dict__
