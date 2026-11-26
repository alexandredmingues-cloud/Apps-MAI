from __future__ import annotations

from datetime import datetime, timezone
import time

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field, model_validator
from sqlalchemy import text

from app.api.v1.billing_router import billing_router
from app.api.v1.crm_router import crm_router
from app.api.v1.deps import (
    get_current_tenant,
    get_db_session_factory,
    get_settings,
    require_permission,
)
from app.api.v1.interconnect_router import interconnect_router
from app.api.v1.legal_agents_router import legal_agents_router
from app.api.v1.marketplace_router import marketplace_router
from app.api.v1.news_router import news_router
from app.api.v1.notaries_rnic_router import notaries_rnic_router
from app.api.v1.portfolio_router import portfolio_router
from app.core.config import Settings
from app.core.rbac import ROLE_PERMISSIONS, MontyRole, Permission, TenantContext
from app.core.rgpd import compute_retention_until, redact_direct_pii_for_ai
from app.core.security import create_access_token
from app.db.postgres_repositories import PostgresAuthRepository
from app.domain.auth_and_billing import AuthService
from app.domain.country_packs import CountryCode
from app.domain.real_estate_engines import (
    BuyerMatchInput,
    DvfComparable,
    DvfValuationCriteria,
    HcsfCapacityInput,
    PropertyMatchInput,
    SearchPerimeterMode,
    TransportMode,
    compute_compatibility,
    compute_dvf_valuation_report,
    compute_hcsf_borrowing_capacity,
    estimate_sale_timeline,
    validate_dvf_comparable,
)

router = APIRouter(prefix="/api/v1")
router.include_router(news_router)
router.include_router(crm_router)
router.include_router(marketplace_router)
router.include_router(billing_router)
router.include_router(legal_agents_router)
router.include_router(portfolio_router)
router.include_router(notaries_rnic_router)
router.include_router(interconnect_router)

_auth_service = AuthService()


class HealthResponse(BaseModel):
    status: str
    version: str = "2.0.0"
    service: str
    environment: str
    database_configured: bool
    database_reachable: bool | None = None
    database_latency_ms: float | None = None
    storage_mode: str
    dev_auth_enabled: bool
    redis_configured: bool
    redis_reachable: bool | None = None
    redis_latency_ms: float | None = None
    openai_configured: bool
    stripe_configured: bool
    checked_at: str = ""


@router.get("/health", response_model=HealthResponse, tags=["System"])
async def health_check(
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> HealthResponse:
    db_reachable: bool | None = None
    db_latency_ms: float | None = None
    redis_reachable: bool | None = None
    redis_latency_ms: float | None = None
    overall_status = "ok"

    if sf is not None:
        t0 = time.perf_counter()
        try:
            async with sf() as session:
                await session.execute(text("SELECT 1"))
            db_reachable = True
            db_latency_ms = round((time.perf_counter() - t0) * 1000, 2)
        except Exception:
            db_reachable = False
            db_latency_ms = round((time.perf_counter() - t0) * 1000, 2)
            overall_status = "degraded"

    if settings.redis_url:
        t1 = time.perf_counter()
        redis_client = None
        try:
            import redis.asyncio as aioredis

            redis_client = aioredis.from_url(
                settings.redis_url,
                socket_connect_timeout=2.0,
                socket_timeout=2.0,
            )
            pong = await redis_client.ping()
            redis_reachable = bool(pong)
            redis_latency_ms = round((time.perf_counter() - t1) * 1000, 2)
            if not redis_reachable:
                overall_status = "degraded"
        except Exception:
            redis_reachable = False
            redis_latency_ms = round((time.perf_counter() - t1) * 1000, 2)
            overall_status = "degraded"
        finally:
            if redis_client is not None:
                try:
                    await redis_client.aclose()
                except Exception:
                    pass

    return HealthResponse(
        status=overall_status,
        service="monty-saas-api",
        environment=settings.environment,
        database_configured=bool(settings.database_url),
        database_reachable=db_reachable,
        database_latency_ms=db_latency_ms,
        storage_mode="postgresql_rls" if settings.database_url else "in_memory_fallback",
        dev_auth_enabled=settings.can_issue_dev_session,
        redis_configured=bool(settings.redis_url),
        redis_reachable=redis_reachable,
        redis_latency_ms=redis_latency_ms,
        openai_configured=bool(settings.openai_api_key),
        stripe_configured=bool(settings.stripe_secret_key and settings.stripe_webhook_secret),
        checked_at=datetime.now(timezone.utc).isoformat(),
    )


class DevSessionRequest(BaseModel):
    user_id: str = Field(min_length=3, max_length=128)
    agency_id: str = Field(min_length=3, max_length=128)
    role: MontyRole = MontyRole.DIRECTOR


class SessionResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: str = ""
    agency_id: str
    agency_name: str = ""
    display_name: str = ""
    email: str = ""
    role: MontyRole
    permissions: list[str]


@router.post("/auth/dev-session", response_model=SessionResponse, tags=["Auth"])
async def issue_dev_session(
    body: DevSessionRequest,
    settings: Settings = Depends(get_settings),
) -> SessionResponse:
    """Endpoint de développement strictement opt-in (ENABLE_DEV_AUTH=true hors production, sans SUPER_ADMIN)."""
    if not settings.can_issue_dev_session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Route de session de développement désactivée.",
        )
    if body.role == MontyRole.SUPER_ADMIN:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Le rôle SUPER_ADMIN ne peut pas être émis via dev-session.",
        )
    token = create_access_token(
        subject_user_id=body.user_id,
        agency_id=body.agency_id,
        role=body.role.value,
        secret=settings.effective_jwt_secret,
        ttl_seconds=settings.jwt_ttl_minutes * 60,
    )
    perms = sorted(p.value for p in ROLE_PERMISSIONS[body.role])
    return SessionResponse(
        access_token=token,
        user_id=body.user_id,
        agency_id=body.agency_id,
        role=body.role,
        permissions=perms,
    )


class RegisterAgencyRequest(BaseModel):
    agency_name: str = Field(min_length=2, max_length=120)
    email: str = Field(min_length=5, max_length=160)
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(min_length=2, max_length=120)
    city: str = Field(default="Lyon", max_length=80)
    country_code: CountryCode = CountryCode.FR


@router.post("/auth/register", response_model=SessionResponse, status_code=status.HTTP_201_CREATED, tags=["Auth"])
async def register_agency_account(
    body: RegisterAgencyRequest,
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> SessionResponse:
    try:
        if sf is not None:
            res = await PostgresAuthRepository(sf).register_agency_director(
                agency_name=body.agency_name,
                email=body.email,
                password=body.password,
                display_name=body.display_name,
                city=body.city,
                country_code=body.country_code,
                settings=settings,
            )
        else:
            res = _auth_service.register_agency_director(
                agency_name=body.agency_name,
                email=body.email,
                password=body.password,
                display_name=body.display_name,
                city=body.city,
                country_code=body.country_code,
                settings=settings,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return SessionResponse(
        access_token=res.access_token,
        user_id=res.user_id,
        agency_id=res.agency_id,
        agency_name=res.agency_name,
        display_name=res.display_name,
        email=res.email,
        role=res.role,
        permissions=res.permissions,
    )


class RegisterClientRequest(BaseModel):
    email: str = Field(min_length=5, max_length=160)
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(min_length=2, max_length=120)
    role: MontyRole = MontyRole.BUYER
    country_code: CountryCode = CountryCode.FR


@router.post(
    "/auth/register-client",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED,
    tags=["Auth"],
)
async def register_client_account(
    body: RegisterClientRequest,
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> SessionResponse:
    try:
        if sf is not None:
            res = await PostgresAuthRepository(sf).register_client_account(
                email=body.email,
                password=body.password,
                display_name=body.display_name,
                role=body.role,
                country_code=body.country_code,
                settings=settings,
            )
        else:
            res = _auth_service.register_client_account(
                email=body.email,
                password=body.password,
                display_name=body.display_name,
                role=body.role,
                country_code=body.country_code,
                settings=settings,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return SessionResponse(
        access_token=res.access_token,
        user_id=res.user_id,
        agency_id=res.agency_id,
        agency_name=res.agency_name,
        display_name=res.display_name,
        email=res.email,
        role=res.role,
        permissions=res.permissions,
    )


class LoginRequest(BaseModel):
    email: str = Field(min_length=5, max_length=160)
    password: str = Field(min_length=8, max_length=128)


@router.post("/auth/login", response_model=SessionResponse, tags=["Auth"])
async def login_account(
    body: LoginRequest,
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> SessionResponse:
    try:
        if sf is not None:
            res = await PostgresAuthRepository(sf).login(
                email=body.email,
                password=body.password,
                settings=settings,
            )
        else:
            res = _auth_service.login(
                email=body.email,
                password=body.password,
                settings=settings,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(exc)) from exc
    return SessionResponse(
        access_token=res.access_token,
        user_id=res.user_id,
        agency_id=res.agency_id,
        agency_name=res.agency_name,
        display_name=res.display_name,
        email=res.email,
        role=res.role,
        permissions=res.permissions,
    )


class CreateTeamInviteRequest(BaseModel):
    target_email: str = Field(min_length=5, max_length=160)
    target_name: str = Field(default="", max_length=120)
    role: MontyRole = MontyRole.AGENT


@router.post("/team/invites", status_code=status.HTTP_201_CREATED, tags=["Équipe & Invitations"])
async def create_team_invite(
    body: CreateTeamInviteRequest,
    ctx: TenantContext = Depends(require_permission(Permission.TEAM_MANAGE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            inv = await PostgresAuthRepository(sf).create_invite(
                ctx,
                target_email=body.target_email,
                target_name=body.target_name,
                role=body.role,
            )
        else:
            inv = _auth_service.create_invite(
                ctx,
                target_email=body.target_email,
                target_name=body.target_name,
                role=body.role,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return {
        "invite_id": inv.id,
        "invite_code": inv.invite_code,
        "target_email": inv.target_email,
        "role": inv.role.value,
        "expires_at": inv.expires_at,
    }


class ClaimInviteRequest(BaseModel):
    invite_code: str = Field(min_length=8, max_length=128)
    password: str = Field(min_length=8, max_length=128)
    display_name: str = Field(default="", max_length=120)


@router.post("/team/invites/claim", response_model=SessionResponse, tags=["Équipe & Invitations"])
async def claim_team_invite(
    body: ClaimInviteRequest,
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> SessionResponse:
    try:
        if sf is not None:
            res = await PostgresAuthRepository(sf).claim_invite(
                invite_code=body.invite_code,
                password=body.password,
                display_name=body.display_name,
                settings=settings,
            )
        else:
            res = _auth_service.claim_invite(
                invite_code=body.invite_code,
                password=body.password,
                display_name=body.display_name,
                settings=settings,
            )
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return SessionResponse(
        access_token=res.access_token,
        user_id=res.user_id,
        agency_id=res.agency_id,
        agency_name=res.agency_name,
        display_name=res.display_name,
        email=res.email,
        role=res.role,
        permissions=res.permissions,
    )


@router.get("/team/members", tags=["Équipe & Invitations"])
async def list_team_members(
    ctx: TenantContext = Depends(require_permission(Permission.TEAM_MANAGE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        members = await PostgresAuthRepository(sf).list_team_members(ctx)
    else:
        members = _auth_service.list_team_members(ctx)
    return {"count": len(members), "members": members}


@router.get("/auth/me", tags=["Auth"])
async def get_me(ctx: TenantContext = Depends(get_current_tenant)) -> dict:
    return {
        "user_id": ctx.user_id,
        "agency_id": ctx.agency_id,
        "role": ctx.role.value,
        "permissions": sorted(p.value for p in ROLE_PERMISSIONS[ctx.role]),
    }


class HcsfRequest(BaseModel):
    monthly_net_income: int = Field(ge=0, le=1_000_000)
    existing_monthly_credits: int = Field(default=0, ge=0, le=500_000)
    down_payment: int = Field(default=0, ge=0, le=50_000_000)
    duration_years: int = Field(default=25, ge=5, le=30)
    interest_rate_percent: float = Field(default=3.35, ge=0.0, le=15.0)
    insurance_rate_percent: float = Field(default=0.32, ge=0.0, le=3.0)
    max_debt_ratio_percent: float = Field(default=35.0, ge=10.0, le=45.0)
    notary_rate_percent: float = Field(default=7.8, ge=0.0, le=15.0)
    is_vefa_neuf: bool = False


@router.post("/calculators/hcsf", tags=["Calculators"])
async def calculate_hcsf(
    body: HcsfRequest,
    _ctx: TenantContext = Depends(require_permission(Permission.FINANCE_SIMULATE)),
) -> dict:
    res = compute_hcsf_borrowing_capacity(
        HcsfCapacityInput(
            monthly_net_income=body.monthly_net_income,
            existing_monthly_credits=body.existing_monthly_credits,
            down_payment=body.down_payment,
            duration_years=body.duration_years,
            interest_rate_percent=body.interest_rate_percent,
            insurance_rate_percent=body.insurance_rate_percent,
            max_debt_ratio_percent=body.max_debt_ratio_percent,
            notary_rate_percent=body.notary_rate_percent,
            is_vefa_neuf=body.is_vefa_neuf,
        )
    )
    return {
        "max_monthly_debt_total": res.max_monthly_debt_total,
        "max_monthly_loan_payment": res.max_monthly_loan_payment,
        "max_borrowed_capital": res.max_borrowed_capital,
        "max_total_project_budget": res.max_total_project_budget,
        "max_property_price_net": res.max_property_price_net,
        "estimated_notary_fees": res.estimated_notary_fees,
        "total_credit_cost": res.total_credit_cost,
        "effective_debt_ratio_percent": res.effective_debt_ratio_percent,
        "remainder_to_live": res.remainder_to_live,
        "is_hcsf_ratio_compliant": res.is_hcsf_ratio_compliant,
        "is_hcsf_duration_compliant": res.is_hcsf_duration_compliant,
        "duration_scenarios": [
            {
                "duration_years": s.duration_years,
                "max_borrowed_capital": s.max_borrowed_capital,
                "max_property_price_net": s.max_property_price_net,
                "is_hcsf_compliant": s.is_hcsf_compliant,
            }
            for s in res.duration_scenarios
        ],
    }


class MatchingRequest(BaseModel):
    price: int = Field(ge=10_000, le=50_000_000)
    estimated_works: int = Field(default=0, ge=0, le=5_000_000)
    sector: str
    property_type: str
    surface: int = Field(ge=5, le=5000)
    bedrooms: int = Field(ge=0, le=30)
    dpe: str = "C"
    exterior: str = ""
    parking: str = ""
    environment: str = ""
    transports: str = ""
    floor: str = ""
    latitude: float = 43.6108
    longitude: float = 3.8767
    buyer_budget_max: int = Field(ge=10_000, le=50_000_000)
    buyer_sectors: list[str] = Field(default_factory=list)
    buyer_property_type: str = "Appartement"
    buyer_min_surface: int = 0
    buyer_min_rooms: int = 0
    buyer_essential_criteria: list[str] = Field(default_factory=list)
    search_mode: SearchPerimeterMode = SearchPerimeterMode.SECTOR_LIST
    search_center_lat: float = 0.0
    search_center_lon: float = 0.0
    search_center_address: str = ""
    search_radius_km: int = 5
    search_max_travel_minutes: int = 20
    search_transport_mode: TransportMode = TransportMode.TRAM


@router.post("/calculators/matching", tags=["Calculators"])
async def calculate_matching(
    body: MatchingRequest,
    _ctx: TenantContext = Depends(require_permission(Permission.MATCHING_RUN)),
) -> dict:
    res = compute_compatibility(
        PropertyMatchInput(
            price=body.price,
            estimated_works=body.estimated_works,
            sector=body.sector,
            property_type=body.property_type,
            surface=body.surface,
            bedrooms=body.bedrooms,
            dpe=body.dpe,
            exterior=body.exterior,
            parking=body.parking,
            environment=body.environment,
            transports=body.transports,
            floor=body.floor,
            latitude=body.latitude,
            longitude=body.longitude,
        ),
        BuyerMatchInput(
            budget_max=body.buyer_budget_max,
            sectors=body.buyer_sectors,
            property_type=body.buyer_property_type,
            min_surface=body.buyer_min_surface,
            min_rooms=body.buyer_min_rooms,
            essential_criteria=body.buyer_essential_criteria,
            search_mode=body.search_mode,
            search_center_lat=body.search_center_lat,
            search_center_lon=body.search_center_lon,
            search_center_address=body.search_center_address,
            search_radius_km=body.search_radius_km,
            search_max_travel_minutes=body.search_max_travel_minutes,
            search_transport_mode=body.search_transport_mode,
        ),
    )
    return {
        "score": res.score,
        "matched_criteria": res.matched_criteria,
        "incompatible_criteria": res.incompatible_criteria,
    }


class DvfComparableIn(BaseModel):
    mutation_id: str = Field(min_length=1, max_length=80)
    date_mutation: str = Field(min_length=8, max_length=20)
    year: int = Field(ge=2014, le=2035)
    price_euro: int = Field(ge=15000, le=50_000_000)
    surface_m2: int = Field(ge=10, le=2500)
    property_type: str = Field(min_length=2, max_length=40)
    distance_meters: int = Field(ge=0, le=5000)

    @model_validator(mode="after")
    def _validate_coherent_price_per_m2(self) -> "DvfComparableIn":
        validate_dvf_comparable(
            mutation_id=self.mutation_id,
            year=self.year,
            price_euro=self.price_euro,
            surface_m2=self.surface_m2,
        )
        return self


class DvfEstimationRequest(BaseModel):
    comparables: list[DvfComparableIn]
    property_kind: str
    surface_m2: int = Field(ge=5, le=5000)
    dpe: str = "C"
    condition_label: str = "Bon état"
    provided_docs_ratio: float = Field(default=0.5, ge=0.0, le=1.0)
    floor_and_elevator: str = "Étage avec ascenseur"
    view_and_exposure: str = "Lumineux / Traversant SO"
    exterior_type: str = "Balcon / Loggia"
    parking_type: str = "Place parking privée"
    environment_calm: str = "Urbain standard"


@router.post("/calculators/dvf", tags=["Calculators"])
async def calculate_dvf(
    body: DvfEstimationRequest,
    _ctx: TenantContext = Depends(require_permission(Permission.ESTIMATION_RUN)),
) -> dict:
    report = compute_dvf_valuation_report(
        comparables=[
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
        ],
        property_kind=body.property_kind,
        surface_m2=body.surface_m2,
        dpe=body.dpe,
        condition_label=body.condition_label,
        provided_docs_ratio=body.provided_docs_ratio,
        criteria=DvfValuationCriteria(
            floor_and_elevator=body.floor_and_elevator,
            view_and_exposure=body.view_and_exposure,
            exterior_type=body.exterior_type,
            parking_type=body.parking_type,
            environment_calm=body.environment_calm,
        ),
    )
    return {
        "estimated_low_euro": report.estimated_low_euro,
        "estimated_mid_euro": report.estimated_mid_euro,
        "estimated_high_euro": report.estimated_high_euro,
        "raw_dvf_price_m2": report.raw_dvf_price_m2,
        "adjusted_price_m2": report.adjusted_price_m2,
        "total_adjustment_percent": report.total_adjustment_percent,
        "valuation_score": report.valuation_score,
        "score_label": report.score_label,
        "comparables_used_count": report.comparables_used_count,
        "outliers_removed_count": report.outliers_removed_count,
        "expanded_to_neighbor_sector": report.expanded_to_neighbor_sector,
        "min_sample_required": report.min_sample_required,
        "confidence_index": report.confidence_index,
        "confidence_label": report.confidence_label,
        "data_date": report.data_date,
        "disclaimer": report.disclaimer,
        "justifications": report.justifications,
    }


class SaleTimelineRequest(BaseModel):
    sector_name: str
    property_kind: str
    asking_price_euro: int = Field(default=0, ge=0)
    surface_m2: int = Field(ge=0)
    dpe: str = "C"
    condition_label: str = "Bon état"
    dvf_price_m2: int = 0
    dvf_count: int = 0
    photo_count: int = 0
    provided_docs_ratio: float = 0.0
    mandate_type: str = ""


@router.post("/calculators/sale-timeline", tags=["Calculators"])
async def calculate_sale_timeline(
    body: SaleTimelineRequest,
    _ctx: TenantContext = Depends(require_permission(Permission.ESTIMATION_RUN)),
) -> dict:
    est = estimate_sale_timeline(
        sector_name=body.sector_name,
        property_kind=body.property_kind,
        asking_price_euro=body.asking_price_euro,
        surface_m2=body.surface_m2,
        dpe=body.dpe,
        condition_label=body.condition_label,
        dvf_price_m2=body.dvf_price_m2,
        dvf_count=body.dvf_count,
        photo_count=body.photo_count,
        provided_docs_ratio=body.provided_docs_ratio,
        mandate_type=body.mandate_type,
    )
    return {
        "status": est.status,
        "low_days": est.low_days,
        "likely_days": est.likely_days,
        "high_days": est.high_days,
        "confidence_label": est.confidence_label,
        "warnings": est.warnings,
    }


class RgpdRetentionCheckRequest(BaseModel):
    legal_basis: str
    sample_prompt_text: str = ""


@router.post("/rgpd/policy-check", tags=["RGPD"])
async def rgpd_policy_check(
    body: RgpdRetentionCheckRequest,
    _ctx: TenantContext = Depends(get_current_tenant),
) -> dict:
    return {
        "retention_until": compute_retention_until(body.legal_basis).isoformat(),
        "redacted_prompt_preview": redact_direct_pii_for_ai(body.sample_prompt_text),
    }
