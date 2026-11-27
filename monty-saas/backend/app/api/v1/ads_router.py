"""Brand advertising requests, moderated campaigns and privacy-friendly aggregate metrics."""
# Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code.
from __future__ import annotations

import re
from datetime import datetime, timezone
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field, HttpUrl, field_validator
from sqlalchemy import text

from app.api.v1.deps import get_db_session_factory, require_permission
from app.core.rbac import Permission, TenantContext

ads_router = APIRouter(prefix="/ads", tags=["Publicité de marques"])


class AdRequest(BaseModel):
    advertiser_name: str = Field(min_length=2, max_length=140)
    brand_name: str = Field(min_length=2, max_length=120)
    contact_email: str = Field(min_length=5, max_length=254)
    website_url: str = Field(default="", max_length=500)
    creative_image_url: str = Field(default="", max_length=1000)
    target_url: str = Field(max_length=1000)
    placement: Literal["HOME", "SEARCH", "CONTENT", "PRO"] = "HOME"
    country_code: Literal["FR", "PT", "GB"] = "FR"
    city: str = Field(default="", max_length=100)
    headline: str = Field(min_length=4, max_length=100)
    description: str = Field(min_length=10, max_length=240)
    campaign_budget_euro: int = Field(default=0, ge=0, le=10_000_000)
    consent: bool

    @field_validator("contact_email")
    @classmethod
    def valid_email(cls, value: str) -> str:
        value = value.strip().lower()
        if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", value):
            raise ValueError("Adresse e-mail invalide.")
        return value

    @field_validator("target_url", "website_url", "creative_image_url")
    @classmethod
    def safe_https_url(cls, value: str) -> str:
        value = value.strip()
        if value and not value.startswith("https://"):
            raise ValueError("Les liens publicitaires doivent utiliser HTTPS.")
        return value

    @field_validator("consent")
    @classmethod
    def consent_required(cls, value: bool) -> bool:
        if not value:
            raise ValueError("Le consentement au traitement de la demande est obligatoire.")
        return value


class AdReview(BaseModel):
    status: Literal["APPROVED", "REJECTED", "PAUSED"]
    starts_at: datetime | None = None
    ends_at: datetime | None = None


async def _session(sf):
    if sf is None:
        raise HTTPException(status_code=503, detail="Base de données indisponible.")
    return sf


@ads_router.post("/public/requests", status_code=status.HTTP_201_CREATED)
async def submit_ad_request(body: AdRequest, sf=Depends(get_db_session_factory)) -> dict:
    factory = await _session(sf)
    if not body.consent:
        raise HTTPException(status_code=400, detail="Consentement requis.")
    now = datetime.now(timezone.utc)
    async with factory() as session:
        async with session.begin():
            result = await session.execute(text("""
                INSERT INTO brand_ad_campaigns
                  (advertiser_name, brand_name, contact_email, website_url, creative_image_url,
                   target_url, placement, country_code, city, headline, description,
                   campaign_budget_euro, consent_at)
                VALUES (:advertiser_name, :brand_name, :contact_email, :website_url, :creative_image_url,
                        :target_url, :placement, :country_code, :city, :headline, :description,
                        :budget, :consent_at)
                RETURNING id
            """), {
                "advertiser_name": body.advertiser_name.strip(), "brand_name": body.brand_name.strip(),
                "contact_email": body.contact_email, "website_url": body.website_url,
                "creative_image_url": body.creative_image_url, "target_url": body.target_url,
                "placement": body.placement, "country_code": body.country_code, "city": body.city.strip(),
                "headline": body.headline.strip(), "description": body.description.strip(),
                "budget": body.campaign_budget_euro, "consent_at": now,
            })
            ad_id = result.scalar_one()
    return {"id": str(ad_id), "status": "PENDING_REVIEW", "message": "Demande reçue. Elle sera vérifiée avant toute diffusion."}


@ads_router.get("/public")
async def list_public_ads(
    placement: Literal["HOME", "SEARCH", "CONTENT", "PRO"] = "HOME",
    country_code: Literal["FR", "PT", "GB"] = "FR",
    city: str = Query(default="", max_length=100),
    limit: int = Query(default=3, ge=1, le=10),
    sf=Depends(get_db_session_factory),
) -> dict:
    factory = await _session(sf)
    now = datetime.now(timezone.utc)
    async with factory() as session:
        result = await session.execute(text("""
            SELECT id, brand_name, creative_image_url, target_url, headline, description, placement
            FROM brand_ad_campaigns
            WHERE status = 'APPROVED' AND placement = :placement AND country_code = :country
              AND (starts_at IS NULL OR starts_at <= :now) AND (ends_at IS NULL OR ends_at >= :now)
              AND (city = '' OR lower(city) = lower(:city))
            ORDER BY campaign_budget_euro DESC, created_at DESC LIMIT :limit
        """), {"placement": placement, "country": country_code, "city": city.strip(), "now": now, "limit": limit})
        ads = [dict(row) for row in result.mappings().all()]
    return {"ads": [{**ad, "id": str(ad["id"]), "sponsored": True} for ad in ads]}


@ads_router.post("/public/{ad_id}/impression", status_code=204)
async def record_ad_impression(ad_id: UUID, sf=Depends(get_db_session_factory)) -> None:
    factory = await _session(sf)
    async with factory() as session:
        async with session.begin():
            await session.execute(text("""UPDATE brand_ad_campaigns SET impressions = impressions + 1
                WHERE id = :id AND status = 'APPROVED' AND (starts_at IS NULL OR starts_at <= now())
                AND (ends_at IS NULL OR ends_at >= now())"""), {"id": ad_id})


@ads_router.post("/public/{ad_id}/click", status_code=204)
async def record_ad_click(ad_id: UUID, sf=Depends(get_db_session_factory)) -> None:
    factory = await _session(sf)
    async with factory() as session:
        async with session.begin():
            await session.execute(text("""UPDATE brand_ad_campaigns SET clicks = clicks + 1
                WHERE id = :id AND status = 'APPROVED' AND (starts_at IS NULL OR starts_at <= now())
                AND (ends_at IS NULL OR ends_at >= now())"""), {"id": ad_id})


@ads_router.get("/admin/requests")
async def list_ad_requests(
    ctx: TenantContext = Depends(require_permission(Permission.PLATFORM_ADMIN)),
    sf=Depends(get_db_session_factory),
) -> dict:
    factory = await _session(sf)
    async with factory() as session:
        result = await session.execute(text("""SELECT id, advertiser_name, brand_name, contact_email,
            website_url, creative_image_url, target_url, placement, country_code, city, headline,
            description, campaign_budget_euro, status, starts_at, ends_at, impressions, clicks, created_at
            FROM brand_ad_campaigns ORDER BY created_at DESC LIMIT 200"""))
        rows = [dict(row) for row in result.mappings().all()]
    return {"requests": [{**row, "id": str(row["id"])} for row in rows]}


@ads_router.post("/admin/{ad_id}/review")
async def review_ad_request(
    ad_id: UUID, body: AdReview,
    ctx: TenantContext = Depends(require_permission(Permission.PLATFORM_ADMIN)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if body.starts_at and body.ends_at and body.ends_at <= body.starts_at:
        raise HTTPException(status_code=422, detail="La fin de campagne doit être postérieure au début.")
    factory = await _session(sf)
    async with factory() as session:
        async with session.begin():
            result = await session.execute(text("""UPDATE brand_ad_campaigns SET status = :status,
                starts_at = COALESCE(:starts_at, starts_at), ends_at = COALESCE(:ends_at, ends_at),
                reviewed_at = now(), updated_at = now() WHERE id = :id RETURNING id, status"""),
                {"id": ad_id, "status": body.status, "starts_at": body.starts_at, "ends_at": body.ends_at})
            row = result.mappings().first()
            if row is None:
                raise HTTPException(status_code=404, detail="Demande publicitaire introuvable.")
    return {"id": str(row["id"]), "status": row["status"]}
