from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.api.v1.deps import get_current_tenant, require_permission
from app.core.rbac import Permission, TenantContext
from app.domain.country_packs import CountryCode
from app.domain.monty_price_index_service import monty_price_engine
from app.domain.real_estate_engines import DvfComparable

price_index_router = APIRouter(tags=["Phase 29 — Monty Price Index"])


class ComparableIn(BaseModel):
    mutation_id: str = Field(min_length=1, max_length=80)
    date_mutation: str = Field(min_length=8, max_length=20)
    year: int = Field(ge=2014, le=2035)
    price_euro: int = Field(ge=15000, le=50_000_000)
    surface_m2: int = Field(ge=10, le=2500)
    property_type: str = Field(min_length=2, max_length=40)
    distance_meters: int = Field(ge=0, le=15000)


class PriceIndexEvaluateRequest(BaseModel):
    agency_id: str = Field(default="agency-france-reseau", max_length=128)
    target_ref: str = Field(default="", max_length=120)
    title: str = Field(default="Bien analysé", max_length=200)
    country_code: CountryCode = CountryCode.FR
    city: str = Field(min_length=2, max_length=120)
    sector_label: str = Field(default="", max_length=120)
    postal_code: str = Field(default="", max_length=16)
    property_kind: str = Field(default="Appartement", max_length=60)
    asking_price_euro: int = Field(ge=10_000, le=50_000_000)
    surface_m2: int = Field(ge=5, le=5000)
    dpe: str = Field(default="C", max_length=4)
    condition_label: str = Field(default="Bon état", max_length=80)
    has_exterior: bool = False
    has_parking: bool = False
    estimated_works_euro: int = Field(default=0, ge=0, le=5_000_000)
    comparables: list[ComparableIn] = Field(default_factory=list)


@price_index_router.post("/price-index/evaluate")
async def evaluate_monty_price_index(body: PriceIndexEvaluateRequest) -> dict[str, Any]:
    comps = [
        DvfComparable(
            mutation_id=c.mutation_id,
            date_mutation=c.date_mutation,
            year=c.year,
            price_euro=c.price_euro,
            surface_m2=c.surface_m2,
            property_type=c.property_type,
            distance_meters=c.distance_meters,
        )
        for c in body.comparables
    ]
    try:
        return monty_price_engine.evaluate_property(
            agency_id=body.agency_id,
            target_ref=body.target_ref,
            title=body.title,
            country_code=body.country_code,
            city=body.city,
            sector_label=body.sector_label,
            postal_code=body.postal_code,
            property_kind=body.property_kind,
            asking_price_euro=body.asking_price_euro,
            surface_m2=body.surface_m2,
            dpe=body.dpe,
            condition_label=body.condition_label,
            has_exterior=body.has_exterior,
            has_parking=body.has_parking,
            estimated_works_euro=body.estimated_works_euro,
            explicit_comparables=comps or None,
            record_history=True,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc


@price_index_router.get("/price-index/history")
async def get_price_index_history(
    limit: int = Query(default=40, ge=1, le=200),
    ctx: TenantContext = Depends(get_current_tenant),
) -> dict[str, Any]:
    items = monty_price_engine.list_history(ctx, limit=limit)
    return {"count": len(items), "history": [h.__dict__ for h in items]}


@price_index_router.get("/admin/price-index/config")
async def admin_get_price_index_config(
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
) -> dict[str, Any]:
    return monty_price_engine.get_config(ctx.agency_id).__dict__


class UpdatePriceIndexConfigRequest(BaseModel):
    very_good_deal_pct: float = Field(ge=-40.0, le=-2.0)
    good_deal_pct: float = Field(ge=-30.0, le=-1.0)
    above_market_pct: float = Field(ge=1.0, le=30.0)
    overpriced_pct: float = Field(ge=2.0, le=50.0)
    min_comparables_required: int = Field(ge=2, le=20)
    max_comparable_distance_m: int = Field(ge=300, le=15000)
    max_comparable_age_months: int = Field(ge=6, le=60)


@price_index_router.put("/admin/price-index/config")
async def admin_update_price_index_config(
    body: UpdatePriceIndexConfigRequest,
    ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
) -> dict[str, Any]:
    try:
        cfg = monty_price_engine.update_config(
            ctx,
            very_good_deal_pct=body.very_good_deal_pct,
            good_deal_pct=body.good_deal_pct,
            above_market_pct=body.above_market_pct,
            overpriced_pct=body.overpriced_pct,
            min_comparables_required=body.min_comparables_required,
            max_comparable_distance_m=body.max_comparable_distance_m,
            max_comparable_age_months=body.max_comparable_age_months,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return cfg.__dict__
