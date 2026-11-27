from __future__ import annotations

import asyncio
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Response, status
from pydantic import BaseModel, Field

from app.api.v1.deps import get_db_session_factory, get_settings, require_permission
from app.core.config import Settings
from app.core.rbac import MontyRole, Permission, TenantContext
from app.core.security import decode_access_token
from app.db.postgres_notaries_rnic_repository import PostgresNotariesRnicRepository
from app.domain.notaries_service import NotaryBookService, search_french_notary_offices
from app.domain.rnic_copro_service import (
    RnicAccessService,
    is_rnic_free_for_role,
    search_rnic_coproprietes,
)

notaries_rnic_router = APIRouter(tags=["Notaires de France & Copropriétés RNIC"])

_notary_book = NotaryBookService()
_rnic_access = RnicAccessService()


def _resolve_optional_tenant(authorization: str | None, settings: Settings) -> TenantContext | None:
    if not authorization or not authorization.startswith("Bearer "):
        return None
    token = authorization.removeprefix("Bearer ").strip()
    try:
        payload = decode_access_token(token, settings.effective_jwt_secret)
        return TenantContext(
            user_id=str(payload["sub"]),
            agency_id=str(payload["aid"]),
            role=MontyRole(payload["role"]),
        )
    except Exception:
        return None


# ==============================================================================
# 1. ANNUAIRE OFFICIEL DES NOTAIRES DE FRANCE & TRANSMISSIONS DOSSIERS COMPROMIS
# ==============================================================================


@notaries_rnic_router.get("/notaries/search")
async def search_official_french_notaries(
    query: str = Query(default="", max_length=100),
    department_code: str = Query(default="", max_length=3),
    postal_code: str = Query(default="", max_length=5),
    per_page: int = Query(default=25, ge=5, le=25),
    page: int = Query(default=1, ge=1, le=1000),
) -> dict[str, Any]:
    """
    Recherche live dans l'annuaire officiel des études notariales de France
    (DINUM/INSEE API Recherche d'Entreprises — code NAF 69.10Z).
    """
    return await asyncio.to_thread(
        search_french_notary_offices,
        query=query,
        department_code=department_code,
        postal_code=postal_code,
        per_page=per_page,
        page=page,
    )


@notaries_rnic_router.get("/notaries/book")
async def list_agency_notary_book(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        items = await PostgresNotariesRnicRepository(sf).list_notaries(ctx)
    else:
        items = _notary_book.list_notaries(ctx)
    return {"count": len(items), "notaries": [n.__dict__ for n in items]}


class SaveNotaryRequest(BaseModel):
    siret: str = Field(default="", max_length=20)
    office_name: str = Field(min_length=2, max_length=180)
    notary_name: str = Field(default="", max_length=160)
    email: str = Field(default="", max_length=160)
    phone: str = Field(default="", max_length=40)
    email_verified: bool = False
    street_address: str = Field(default="", max_length=240)
    postal_code: str = Field(default="", max_length=16)
    city: str = Field(default="", max_length=100)
    source: str = Field(default="DINUM_API_GOUV", max_length=40)
    notes: str = Field(default="", max_length=600)


@notaries_rnic_router.post("/notaries/book", status_code=status.HTTP_201_CREATED)
async def save_agency_notary(
    body: SaveNotaryRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if sf is not None:
            rec = await PostgresNotariesRnicRepository(sf).save_notary(
                ctx,
                siret=body.siret,
                office_name=body.office_name,
                notary_name=body.notary_name,
                email=body.email,
                phone=body.phone,
                email_verified=body.email_verified,
                street_address=body.street_address,
                postal_code=body.postal_code,
                city=body.city,
                source=body.source,
                notes=body.notes,
            )
        else:
            rec = _notary_book.save_notary(
                ctx,
                siret=body.siret,
                office_name=body.office_name,
                notary_name=body.notary_name,
                email=body.email,
                phone=body.phone,
                street_address=body.street_address,
                postal_code=body.postal_code,
                city=body.city,
                source=body.source,
                notes=body.notes,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return rec.__dict__


class CreateNotaryTransmissionRequest(BaseModel):
    notary_contact_id: str | None = None
    transaction_id: str | None = None
    property_id: str | None = None
    dossier_ref: str = Field(min_length=2, max_length=80)
    transmission_type: str = Field(default="OUVERTURE_DOSSIER")
    recipient_email: str = Field(default="", max_length=160)
    recipient_office: str = Field(min_length=2, max_length=180)
    recipient_address: str = Field(default="", max_length=260)
    property_title: str = Field(min_length=2, max_length=180)
    property_address: str = Field(default="", max_length=220)
    seller_name: str = Field(default="", max_length=140)
    buyer_name: str = Field(default="", max_length=140)
    agreed_price_euro: int = Field(ge=0, le=50_000_000)
    target_date: str = Field(default="", max_length=30)
    checklist_items: list[str] = Field(default_factory=list, max_length=25)
    notes: str = Field(default="", max_length=1200)
    pre_etat_date_html: str = Field(default="", max_length=200_000)
    send_now: bool = False


@notaries_rnic_router.get("/notaries/transmissions")
async def list_notary_transmissions(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        items = await PostgresNotariesRnicRepository(sf).list_transmissions(ctx)
    else:
        items = _notary_book.list_transmissions(ctx)
    return {"count": len(items), "transmissions": [t.__dict__ for t in items]}


@notaries_rnic_router.post("/notaries/transmissions", status_code=status.HTTP_201_CREATED)
async def create_notary_transmission(
    body: CreateNotaryTransmissionRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if sf is not None:
            rec = await PostgresNotariesRnicRepository(sf).create_transmission(
                ctx,
                notary_contact_id=body.notary_contact_id,
                transaction_id=body.transaction_id,
                property_id=body.property_id,
                dossier_ref=body.dossier_ref,
                transmission_type=body.transmission_type,
                recipient_email=body.recipient_email,
                recipient_office=body.recipient_office,
                recipient_address=body.recipient_address,
                property_title=body.property_title,
                property_address=body.property_address,
                seller_name=body.seller_name,
                buyer_name=body.buyer_name,
                agreed_price_euro=body.agreed_price_euro,
                target_date=body.target_date,
                checklist_items=body.checklist_items,
                notes=body.notes,
                pre_etat_date_html=body.pre_etat_date_html,
                send_now=body.send_now,
                settings=settings,
            )
        else:
            rec = _notary_book.create_transmission(
                ctx,
                notary_contact_id=body.notary_contact_id,
                transaction_id=body.transaction_id,
                property_id=body.property_id,
                dossier_ref=body.dossier_ref,
                transmission_type=body.transmission_type,
                recipient_email=body.recipient_email,
                recipient_office=body.recipient_office,
                recipient_address=body.recipient_address,
                property_title=body.property_title,
                property_address=body.property_address,
                seller_name=body.seller_name,
                buyer_name=body.buyer_name,
                agreed_price_euro=body.agreed_price_euro,
                target_date=body.target_date,
                checklist_items=body.checklist_items,
                notes=body.notes,
                pre_etat_date_html=body.pre_etat_date_html,
                send_now=body.send_now,
                settings=settings,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return rec.__dict__


@notaries_rnic_router.get("/notaries/transmissions/{transmission_id}/eml")
async def download_notary_transmission_eml(
    transmission_id: str,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> Response:
    items = (
        await PostgresNotariesRnicRepository(sf).list_transmissions(ctx)
        if sf is not None
        else _notary_book.list_transmissions(ctx)
    )
    match = next((t for t in items if t.id == transmission_id), None)
    if match is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Transmission introuvable.")
    return Response(
        content=match.eml_content,
        media_type="message/rfc822; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="notaire-{match.dossier_ref}.eml"'},
    )


# ==============================================================================
# 2. BASE NATIONALE COPROPRIÉTÉS DE FRANCE (RNIC ANAH) — GRATUIT AGENCES/VENDEURS, PAYANT PARTICULIERS
# ==============================================================================


@notaries_rnic_router.get("/rnic/access-status")
async def get_rnic_access_status(
    authorization: str | None = Header(default=None),
    rnic_pass_token: str = Query(default="", max_length=128),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    ctx = _resolve_optional_tenant(authorization, settings)
    role = ctx.role if ctx else None
    free_for_role = is_rnic_free_for_role(role)
    has_paid_pass = False
    if not free_for_role:
        if sf is not None:
            has_paid_pass = await PostgresNotariesRnicRepository(sf).has_active_rnic_pass(
                pass_token=rnic_pass_token,
                user_id=ctx.user_id if ctx else None,
            )
        else:
            has_paid_pass = _rnic_access.has_active_pass(
                pass_token=rnic_pass_token,
                user_id=ctx.user_id if ctx else None,
            )
    return {
        "role": role.value if role else "PUBLIC",
        "free_tier_eligible": free_for_role,
        "has_paid_pass": has_paid_pass,
        "has_full_access": free_for_role or has_paid_pass,
        "particulier_price_euro": settings.rnic_particulier_price_euro,
        "pricing_policy": "Gratuit pour les agences immobilières et vendeurs vérifiés ; Pass payant pour les particuliers / acquéreurs.",
    }


@notaries_rnic_router.get("/rnic/search")
async def search_rnic_coproprietes_endpoint(
    postal_code: str = Query(default="", max_length=10),
    commune: str = Query(default="", max_length=80),
    immatriculation: str = Query(default="", max_length=32),
    address_query: str = Query(default="", max_length=120),
    page_size: int = Query(default=12, ge=1, le=25),
    rnic_pass_token: str = Query(default="", max_length=128),
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    ctx = _resolve_optional_tenant(authorization, settings)
    role = ctx.role if ctx else None
    has_full = is_rnic_free_for_role(role)
    if not has_full:
        if sf is not None:
            has_full = await PostgresNotariesRnicRepository(sf).has_active_rnic_pass(
                pass_token=rnic_pass_token,
                user_id=ctx.user_id if ctx else None,
            )
        else:
            has_full = _rnic_access.has_active_pass(
                pass_token=rnic_pass_token,
                user_id=ctx.user_id if ctx else None,
            )

    try:
        return await asyncio.to_thread(
            search_rnic_coproprietes,
            settings=settings,
            postal_code=postal_code,
            commune=commune,
            immatriculation=immatriculation,
            address_query=address_query,
            page_size=page_size,
            has_full_access=has_full,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
