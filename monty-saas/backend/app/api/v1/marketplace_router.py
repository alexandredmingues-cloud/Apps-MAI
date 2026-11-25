from __future__ import annotations

import asyncio

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.api.v1.deps import (
    check_ai_rate_limit,
    get_current_tenant,
    get_db_session_factory,
    require_permission,
)
from app.api.v1.crm_router import _leads_service, _public_lead_dto
from app.core.rbac import Permission, TenantContext
from app.db.postgres_portfolio_leads_repository import PostgresPortfolioLeadsRepository
from app.db.postgres_repositories import PostgresMarketplaceRepository
from app.db.postgres_v2_ops_repository import PostgresV2OpsRepository
from app.domain.authorized_feeds_service import AuthorizedListingFeedsService, FeedFormat
from app.domain.country_packs import CountryCode
from app.domain.geocoding_service import geocode_french_address
from app.domain.marketplace_service import (
    MarketplaceService,
    PublisherType,
    generate_listing_copy,
)
from app.domain.portfolio_and_leads_service import LeadKind
from app.domain.real_estate_engines import SearchPerimeterMode, TransportMode

marketplace_router = APIRouter(prefix="/marketplace", tags=["Phase 2 — Annonces & Mobilité"])
_market = MarketplaceService()
_feeds_service = AuthorizedListingFeedsService(_market)


def _listing_dto(item) -> dict:
    return {
        "id": item.id,
        "country_code": item.country_code.value,
        "publisher_type": item.publisher_type.value,
        "publisher_label": item.publisher_label,
        "title": item.title,
        "property_kind": item.property_kind,
        "city": item.city,
        "region_name": item.region_name,
        "sector_label": item.sector_label,
        "price_euro": item.price_euro,
        "surface_m2": item.surface_m2,
        "rooms": item.rooms,
        "dpe": item.dpe,
        "condition_label": item.condition_label,
        "highlights": list(item.highlights),
        "description": item.description,
        "photo_count": item.photo_count,
        "latitude": item.latitude,
        "longitude": item.longitude,
        "moderation_status": item.moderation_status.value,
        "moderation_note": item.moderation_note,
        "sale_timeline": {
            "status": item.sale_timeline.status,
            "low_days": item.sale_timeline.low_days,
            "likely_days": item.sale_timeline.likely_days,
            "high_days": item.sale_timeline.high_days,
            "confidence_label": item.sale_timeline.confidence_label,
        },
        "published_at": item.published_at,
    }


def _feed_dto(f) -> dict:
    return {
        "id": f.id,
        "agency_id": f.agency_id,
        "name": f.name,
        "country_code": f.country_code.value,
        "publisher_type": f.publisher_type.value,
        "feed_format": f.feed_format.value,
        "feed_url": f.feed_url,
        "agreement_confirmed": f.agreement_confirmed,
        "respect_robots_txt": f.respect_robots_txt,
        "auto_publish_verified_agency": f.auto_publish_verified_agency,
        "last_synced_at": f.last_synced_at,
        "last_status": f.last_status,
        "total_ingested": f.total_ingested,
    }


@marketplace_router.get("/listings")
async def search_marketplace_listings(
    country_code: CountryCode = CountryCode.FR,
    publisher_type: PublisherType | None = None,
    origin_address: str = Query(default="Lyon", max_length=120),
    origin_lat: float | None = Query(default=None, ge=-90.0, le=90.0),
    origin_lon: float | None = Query(default=None, ge=-180.0, le=180.0),
    search_mode: SearchPerimeterMode = SearchPerimeterMode.TRAVEL_TIME,
    transport_mode: TransportMode = TransportMode.TRAM,
    max_radius_km: int = Query(default=20, ge=1, le=500),
    max_travel_minutes: int = Query(default=35, ge=5, le=240),
    max_price_euro: int | None = Query(default=None, ge=10_000),
    min_surface_m2: int = Query(default=0, ge=0),
    only_within_perimeter: bool = True,
    sf=Depends(get_db_session_factory),
) -> dict:
    if origin_lat is not None and origin_lon is not None:
        resolved_lat = origin_lat
        resolved_lon = origin_lon
    else:
        origin_geo = await asyncio.to_thread(
            geocode_french_address,
            origin_address,
            country_code=country_code,
            fallback_city=origin_address,
        )
        resolved_lat = origin_geo.latitude
        resolved_lon = origin_geo.longitude
    if sf is not None:
        hits = await PostgresMarketplaceRepository(sf).search_listings(
            country_code=country_code,
            publisher_type=publisher_type,
            origin_address=origin_address,
            origin_lat=resolved_lat,
            origin_lon=resolved_lon,
            search_mode=search_mode,
            transport_mode=transport_mode,
            max_radius_km=max_radius_km,
            max_travel_minutes=max_travel_minutes,
            max_price_euro=max_price_euro,
            min_surface_m2=min_surface_m2,
            only_within_perimeter=only_within_perimeter,
        )
    else:
        hits = _market.search_listings(
            country_code=country_code,
            publisher_type=publisher_type,
            origin_address=origin_address,
            origin_lat=resolved_lat,
            origin_lon=resolved_lon,
            search_mode=search_mode,
            transport_mode=transport_mode,
            max_radius_km=max_radius_km,
            max_travel_minutes=max_travel_minutes,
            max_price_euro=max_price_euro,
            min_surface_m2=min_surface_m2,
            only_within_perimeter=only_within_perimeter,
        )
    return {
        "count": len(hits),
        "hits": [
            {
                "listing": _listing_dto(h.listing),
                "distance_km": h.distance_km,
                "estimated_minutes": h.estimated_minutes,
                "transport_badge": h.transport_badge,
                "within_perimeter": h.within_perimeter,
            }
            for h in hits
        ],
    }


class CopywritingRequest(BaseModel):
    country_code: CountryCode = CountryCode.FR
    property_kind: str = "Appartement"
    city: str = "Lyon"
    sector_label: str = "Centre"
    surface_m2: int = Field(ge=9, le=2000)
    rooms: int = Field(ge=1, le=30)
    price_euro: int = Field(ge=10_000, le=50_000_000)
    dpe: str = "C"
    condition_label: str = "Bon état"
    highlights: list[str] = Field(default_factory=list)
    photo_count: int = Field(default=5, ge=0, le=50)
    estimated_sale_days: int | None = None


@marketplace_router.post("/ad-copy-assistant")
async def assist_listing_copywriting(
    body: CopywritingRequest,
    ctx: TenantContext = Depends(get_current_tenant),
) -> dict:
    if not (ctx.has_permission(Permission.AI_ASSISTANT_PRO) or ctx.has_permission(Permission.AI_ASSISTANT_CLIENT)):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Authentification Pro ou Vendeur/Acquéreur requise pour l'assistant rédactionnel.",
        )
    check_ai_rate_limit(ctx)
    return generate_listing_copy(
        country_code=body.country_code,
        property_kind=body.property_kind,
        city=body.city,
        sector_label=body.sector_label,
        surface_m2=body.surface_m2,
        rooms=body.rooms,
        price_euro=body.price_euro,
        dpe=body.dpe,
        condition_label=body.condition_label,
        highlights=body.highlights,
        photo_count=body.photo_count,
        estimated_sale_days=body.estimated_sale_days,
    )


class PublishListingRequest(BaseModel):
    country_code: CountryCode = CountryCode.FR
    publisher_type: PublisherType = PublisherType.PARTICULIER
    publisher_label: str = ""
    title: str = Field(min_length=5, max_length=180)
    property_kind: str = "Appartement"
    city: str = Field(min_length=2, max_length=80)
    sector_label: str = ""
    price_euro: int = Field(ge=10_000, le=50_000_000)
    surface_m2: int = Field(ge=9, le=2000)
    rooms: int = Field(ge=1, le=30)
    dpe: str = "C"
    condition_label: str = "Bon état"
    highlights: list[str] = Field(default_factory=list)
    description: str = Field(min_length=10, max_length=2000)
    photo_count: int = Field(default=5, ge=0, le=50)


@marketplace_router.post("/listings", status_code=status.HTTP_201_CREATED)
async def publish_marketplace_listing(
    body: PublishListingRequest,
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict:
    geo = await asyncio.to_thread(
        geocode_french_address,
        f"{body.sector_label} {body.city}".strip(),
        country_code=body.country_code,
        fallback_city=body.city,
    )
    try:
        if sf is not None:
            created = await PostgresMarketplaceRepository(sf).publish_listing(
                ctx,
                country_code=body.country_code,
                publisher_type=body.publisher_type,
                publisher_label=body.publisher_label,
                title=body.title,
                property_kind=body.property_kind,
                city=body.city,
                sector_label=body.sector_label,
                price_euro=body.price_euro,
                surface_m2=body.surface_m2,
                rooms=body.rooms,
                dpe=body.dpe,
                condition_label=body.condition_label,
                highlights=body.highlights,
                description=body.description,
                photo_count=body.photo_count,
                latitude=geo.latitude,
                longitude=geo.longitude,
                region_name=geo.region_name,
            )
        else:
            created = _market.publish_listing(
                ctx=ctx,
                country_code=body.country_code,
                publisher_type=body.publisher_type,
                publisher_label=body.publisher_label,
                title=body.title,
                property_kind=body.property_kind,
                city=body.city,
                sector_label=body.sector_label,
                price_euro=body.price_euro,
                surface_m2=body.surface_m2,
                rooms=body.rooms,
                dpe=body.dpe,
                condition_label=body.condition_label,
                highlights=body.highlights,
                description=body.description,
                photo_count=body.photo_count,
                latitude=geo.latitude,
                longitude=geo.longitude,
                region_name=geo.region_name,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _listing_dto(created)


@marketplace_router.get("/moderation/pending")
async def list_pending_moderation_listings(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_MODERATE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        items = await PostgresMarketplaceRepository(sf).list_pending_moderation(ctx)
    else:
        items = _market.list_pending_moderation(ctx)
    return {"count": len(items), "listings": [_listing_dto(item) for item in items]}


class ModerationDecisionRequest(BaseModel):
    approve: bool
    moderation_note: str = Field(default="", max_length=500)


@marketplace_router.post("/listings/{listing_id}/moderate")
async def moderate_marketplace_listing(
    listing_id: str,
    body: ModerationDecisionRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_MODERATE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            updated = await PostgresMarketplaceRepository(sf).moderate_listing(
                ctx,
                listing_id=listing_id,
                approve=body.approve,
                moderation_note=body.moderation_note,
            )
        else:
            updated = _market.moderate_listing(
                ctx,
                listing_id=listing_id,
                approve=body.approve,
                moderation_note=body.moderation_note,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return _listing_dto(updated)


@marketplace_router.get("/feeds")
async def list_authorized_feeds(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        feeds = await PostgresV2OpsRepository(sf).list_feeds(ctx)
    else:
        feeds = _feeds_service.list_feeds(ctx)
    return {"count": len(feeds), "feeds": [_feed_dto(f) for f in feeds]}


class RegisterFeedRequest(BaseModel):
    name: str = Field(min_length=3, max_length=120)
    country_code: CountryCode = CountryCode.FR
    publisher_type: PublisherType = PublisherType.AGENCE
    feed_format: FeedFormat = FeedFormat.XML
    feed_url: str = Field(min_length=8, max_length=600)
    agreement_confirmed: bool = False
    auto_publish_verified_agency: bool = False


@marketplace_router.post("/feeds", status_code=status.HTTP_201_CREATED)
async def register_authorized_feed(
    body: RegisterFeedRequest,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            f = await PostgresV2OpsRepository(sf).register_feed(
                ctx,
                name=body.name,
                country_code=body.country_code,
                publisher_type=body.publisher_type,
                feed_format=body.feed_format,
                feed_url=body.feed_url,
                agreement_confirmed=body.agreement_confirmed,
                auto_publish_verified_agency=body.auto_publish_verified_agency,
            )
        else:
            f = _feeds_service.register_feed(
                ctx,
                name=body.name,
                country_code=body.country_code,
                publisher_type=body.publisher_type,
                feed_format=body.feed_format,
                feed_url=body.feed_url,
                agreement_confirmed=body.agreement_confirmed,
                auto_publish_verified_agency=body.auto_publish_verified_agency,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _feed_dto(f)


class SyncFeedRequest(BaseModel):
    inline_payload: str | None = Field(default=None, max_length=500_000)


@marketplace_router.post("/feeds/{feed_id}/sync")
async def sync_authorized_feed(
    feed_id: str,
    body: SyncFeedRequest = SyncFeedRequest(),
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            res = await PostgresV2OpsRepository(sf).sync_feed_to_postgres(
                ctx,
                feed_id=feed_id,
                inline_payload=body.inline_payload,
            )
        else:
            res = await asyncio.to_thread(
                _feeds_service.sync_feed,
                ctx,
                feed_id=feed_id,
                inline_payload=body.inline_payload,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return res.__dict__


class PublicInboundLeadRequest(BaseModel):
    agency_id: str | None = Field(default=None, max_length=128)
    lead_kind: LeadKind
    country_code: CountryCode = CountryCode.FR
    full_name: str = Field(default="", max_length=140)
    email: str = Field(min_length=5, max_length=160)
    phone: str = Field(default="", max_length=40)
    city: str = Field(default="", max_length=120)
    budget_euro: int = Field(default=0, ge=0, le=50_000_000)
    min_rooms: int = Field(default=1, ge=1, le=30)
    search_transport_mode: str = Field(default="TRAM", max_length=24)
    max_travel_minutes: int = Field(default=30, ge=5, le=240)
    horizon: str = Field(default="", max_length=80)
    property_title: str = Field(default="", max_length=180)
    notes: str = Field(default="", max_length=1500)
    rgpd_consent_granted: bool = False


@marketplace_router.post("/public-leads", status_code=status.HTTP_201_CREATED)
async def submit_public_inbound_lead(
    body: PublicInboundLeadRequest,
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            lead = await PostgresPortfolioLeadsRepository(sf).submit_public_lead(
                agency_id=body.agency_id,
                lead_kind=body.lead_kind,
                country_code=body.country_code,
                full_name=body.full_name,
                email=body.email,
                phone=body.phone,
                city=body.city,
                budget_euro=body.budget_euro,
                min_rooms=body.min_rooms,
                search_transport_mode=body.search_transport_mode,
                max_travel_minutes=body.max_travel_minutes,
                horizon=body.horizon,
                property_title=body.property_title,
                notes=body.notes,
                rgpd_consent_granted=body.rgpd_consent_granted,
            )
        else:
            lead = _leads_service.submit_public_lead(
                agency_id=body.agency_id,
                lead_kind=body.lead_kind,
                country_code=body.country_code,
                full_name=body.full_name,
                email=body.email,
                phone=body.phone,
                city=body.city,
                budget_euro=body.budget_euro,
                min_rooms=body.min_rooms,
                search_transport_mode=body.search_transport_mode,
                max_travel_minutes=body.max_travel_minutes,
                horizon=body.horizon,
                property_title=body.property_title,
                notes=body.notes,
                rgpd_consent_granted=body.rgpd_consent_granted,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _public_lead_dto(lead)

