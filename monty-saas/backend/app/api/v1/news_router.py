from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.api.v1.deps import get_current_tenant, get_settings, require_permission
from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.domain.country_packs import (
    COUNTRY_PACKS,
    CountryCode,
    compute_portugal_acquisition_costs,
)
from app.domain.news_hub import (
    AudienceScope,
    NewsCategory,
    NewsHubRepository,
)
from app.domain.real_estate_engines import (
    HcsfCapacityInput,
    MONTPELLIER_SECTORS,
    compute_capital_gain_tax,
    compute_finance_metrics,
    compute_hcsf_borrowing_capacity,
    compute_notary_fees,
    compute_rental_yield,
)
from app.services.openai_news_adapter import generate_dual_audience_briefing

news_router = APIRouter()
_repo = NewsHubRepository()

# Référentiel national multi-villes France + repères Portugal isolés (pour estimations indicatives)
NATIONAL_BENCHMARKS_M2: dict[str, tuple[int, int, str]] = {
    "Paris (Île-de-France)": (9650, 11200, "Très forte"),
    "Lyon (Auvergne-Rhône-Alpes)": (4950, 5600, "Forte"),
    "Bordeaux (Nouvelle-Aquitaine)": (4550, 5100, "Forte"),
    "Nantes (Pays de la Loire)": (3850, 4400, "Forte"),
    "Toulouse (Occitanie)": (3650, 4150, "Forte"),
    "Marseille (PACA)": (3550, 4200, "Modérée à forte"),
    "Lille (Hauts-de-France)": (3700, 4050, "Forte"),
    "Nice (PACA)": (5250, 6100, "Très forte"),
    "Strasbourg (Grand Est)": (3800, 4200, "Forte"),
    "Rennes (Bretagne)": (4100, 4600, "Très forte"),
    **{
        s.name: (s.price_m2_apartment, s.price_m2_house or round(s.price_m2_apartment * 1.12), s.tension)
        for s in MONTPELLIER_SECTORS
    },
}


# ---------------------------------------------------------------------------
# Packs Nationaux & Espace Particulier (Routes Publiques — Zéro donnée client/agence)
# ---------------------------------------------------------------------------
@news_router.get("/public/country-packs", tags=["Architecture Multi-Pays"])
async def list_country_packs() -> dict:
    return {
        "packs": [
            {
                "country_code": p.country_code.value,
                "name_fr": p.name_fr,
                "name_native": p.name_native,
                "default_locale": p.default_locale,
                "supported_locales": list(p.supported_locales),
                "currency_code": p.currency_code,
                "rollout_priority": p.rollout_priority,
                "regulation_jurisdiction": p.regulation_jurisdiction,
                "credit_regulator": p.credit_regulator,
                "max_debt_ratio_guideline_percent": p.max_debt_ratio_guideline_percent,
                "tax_system_label": p.tax_system_label,
                "energy_certificate_label": p.energy_certificate_label,
                "regions": list(p.regions),
                "major_cities": list(p.major_cities),
                "official_data_sources": list(p.official_data_sources),
            }
            for p in COUNTRY_PACKS.values()
        ]
    }


@news_router.get("/public/sources", tags=["Actualités Publiques"])
async def list_public_sources(country_code: CountryCode | None = None) -> dict:
    sources = _repo.list_sources(include_inactive=False, country_code=country_code)
    return {
        "sources": [
            {
                "id": s.id,
                "country_code": s.country_code.value,
                "locale": s.locale,
                "name": s.name,
                "domain": s.domain,
                "base_url": s.base_url,
                "reliability_tier": s.reliability_tier,
                "last_verified_at": s.last_verified_at,
            }
            for s in sources
        ]
    }


@news_router.get("/public/news", tags=["Actualités Publiques"])
async def list_public_news(
    country_code: CountryCode = CountryCode.FR,
    category: NewsCategory | None = None,
    region_name: str = Query(default="", max_length=100),
    department_code: str = Query(default="", max_length=10),
    city: str = Query(default="", max_length=100),
    query: str = Query(default="", max_length=120),
    include_archived: bool = False,
) -> dict:
    items = _repo.query_public_articles(
        country_code=country_code,
        category=category,
        region_name=region_name,
        department_code=department_code,
        city=city,
        query=query,
        include_archived=include_archived,
    )
    return {"country_code": country_code.value, "count": len(items), "articles": items}


@news_router.get("/public/news/{slug}", tags=["Actualités Publiques"])
async def get_public_article(slug: str) -> dict:
    article = _repo.get_public_article_by_slug(slug)
    if article is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Actualité publique introuvable.",
        )
    return article


# ---------------------------------------------------------------------------
# Simulateurs Immobiliers Publics (France + Module Portugal Isolé)
# ---------------------------------------------------------------------------
class MonthlyPaymentRequest(BaseModel):
    price: int = Field(ge=10_000, le=50_000_000)
    down_payment: int = Field(default=30_000, ge=0, le=50_000_000)
    notary_rate_percent: float = Field(default=7.8, ge=0.0, le=15.0)
    duration_years: int = Field(default=25, ge=5, le=30)
    interest_rate_percent: float = Field(default=3.35, ge=0.0, le=15.0)
    insurance_rate_percent: float = Field(default=0.32, ge=0.0, le=3.0)
    monthly_income: int = Field(default=5500, ge=0, le=1_000_000)
    existing_credits: int = Field(default=0, ge=0, le=500_000)
    monthly_charges: int = Field(default=180, ge=0, le=100_000)


@news_router.post("/public/calculators/monthly-payment", tags=["Simulateurs Publics"])
async def public_monthly_payment(body: MonthlyPaymentRequest) -> dict:
    m = compute_finance_metrics(
        price=body.price,
        down_payment=body.down_payment,
        notary_rate_percent=body.notary_rate_percent,
        duration_years=body.duration_years,
        interest_rate_percent=body.interest_rate_percent,
        insurance_rate_percent=body.insurance_rate_percent,
        monthly_income=body.monthly_income,
        existing_credits=body.existing_credits,
        monthly_charges=body.monthly_charges,
    )
    return m.__dict__


class PublicHcsfRequest(BaseModel):
    monthly_net_income: int = Field(ge=0, le=1_000_000)
    existing_monthly_credits: int = Field(default=0, ge=0, le=500_000)
    down_payment: int = Field(default=50_000, ge=0, le=50_000_000)
    duration_years: int = Field(default=25, ge=5, le=30)
    interest_rate_percent: float = Field(default=3.35, ge=0.0, le=15.0)
    insurance_rate_percent: float = Field(default=0.32, ge=0.0, le=3.0)
    notary_rate_percent: float = Field(default=7.8, ge=0.0, le=15.0)
    is_vefa_neuf: bool = False


@news_router.post("/public/calculators/hcsf", tags=["Simulateurs Publics"])
async def public_hcsf_capacity(body: PublicHcsfRequest) -> dict:
    res = compute_hcsf_borrowing_capacity(
        HcsfCapacityInput(
            monthly_net_income=body.monthly_net_income,
            existing_monthly_credits=body.existing_monthly_credits,
            down_payment=body.down_payment,
            duration_years=body.duration_years,
            interest_rate_percent=body.interest_rate_percent,
            insurance_rate_percent=body.insurance_rate_percent,
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
        "duration_scenarios": [s.__dict__ for s in res.duration_scenarios],
    }


class NotaryFeesRequest(BaseModel):
    property_price: int = Field(ge=10_000, le=50_000_000)
    is_neuf: bool = False
    department_code: str = Field(default="75", max_length=3)
    use_increased_dmto_if_applicable: bool = True


@news_router.post("/public/calculators/notary-fees", tags=["Simulateurs Publics"])
async def public_notary_fees(body: NotaryFeesRequest) -> dict:
    res = compute_notary_fees(
        property_price=body.property_price,
        is_neuf=body.is_neuf,
        department_code=body.department_code,
        use_increased_dmto_if_applicable=body.use_increased_dmto_if_applicable,
    )
    return res.__dict__


class PortugalAcquisitionRequest(BaseModel):
    property_price_euro: int = Field(ge=10_000, le=50_000_000)
    loan_amount_euro: int = Field(default=200_000, ge=0, le=50_000_000)
    is_primary_residence_hpp: bool = True
    is_imt_jovem_eligible: bool = False


@news_router.post("/public/calculators/pt/acquisition-costs", tags=["Simulateurs Portugal (Isolés)"])
async def public_portugal_acquisition_costs(body: PortugalAcquisitionRequest) -> dict:
    res = compute_portugal_acquisition_costs(
        property_price_euro=body.property_price_euro,
        loan_amount_euro=body.loan_amount_euro,
        is_primary_residence_hpp=body.is_primary_residence_hpp,
        is_imt_jovem_eligible=body.is_imt_jovem_eligible,
    )
    return res.__dict__


class RentalYieldRequest(BaseModel):
    purchase_price: int = Field(ge=10_000, le=50_000_000)
    notary_fees: int = Field(ge=0, le=10_000_000)
    works_cost: int = Field(default=0, ge=0, le=10_000_000)
    monthly_rent_hc: int = Field(ge=100, le=200_000)
    vacancy_months_per_year: float = Field(default=0.5, ge=0.0, le=12.0)
    non_recoverable_charges_year: int = Field(default=600, ge=0)
    property_tax_year: int = Field(default=900, ge=0)
    management_fee_percent: float = Field(default=6.5, ge=0.0, le=20.0)
    insurance_pno_gli_year: int = Field(default=380, ge=0)
    monthly_loan_payment: int = Field(default=0, ge=0)
    dpe: str = Field(default="C", max_length=2)


@news_router.post("/public/calculators/rental-yield", tags=["Simulateurs Publics"])
async def public_rental_yield(body: RentalYieldRequest) -> dict:
    res = compute_rental_yield(
        purchase_price=body.purchase_price,
        notary_fees=body.notary_fees,
        works_cost=body.works_cost,
        monthly_rent_hc=body.monthly_rent_hc,
        vacancy_months_per_year=body.vacancy_months_per_year,
        non_recoverable_charges_year=body.non_recoverable_charges_year,
        property_tax_year=body.property_tax_year,
        management_fee_percent=body.management_fee_percent,
        insurance_pno_gli_year=body.insurance_pno_gli_year,
        monthly_loan_payment=body.monthly_loan_payment,
        dpe=body.dpe,
    )
    return res.__dict__


class IndicativeEstimationRequest(BaseModel):
    sector_name: str = Field(default="Lyon (Auvergne-Rhône-Alpes)", max_length=100)
    property_kind: str = Field(default="Appartement", max_length=40)
    surface_m2: int = Field(ge=9, le=2000)
    dpe: str = Field(default="C", max_length=2)
    condition_label: str = Field(default="Bon état", max_length=60)
    has_terrace_or_garden: bool = False
    has_parking: bool = True


@news_router.post("/public/calculators/estimation", tags=["Simulateurs Publics"])
async def public_indicative_estimation(body: IndicativeEstimationRequest) -> dict:
    matched_name = "Lyon (Auvergne-Rhône-Alpes)"
    apt_m2, house_m2, tension = NATIONAL_BENCHMARKS_M2[matched_name]
    for name, vals in NATIONAL_BENCHMARKS_M2.items():
        if name.lower() == body.sector_name.lower() or body.sector_name.lower() in name.lower():
            matched_name = name
            apt_m2, house_m2, tension = vals
            break

    base_m2 = house_m2 if body.property_kind.lower() == "maison" else apt_m2
    dpe_map = {"A": 0.05, "B": 0.05, "C": 0.02, "D": 0.0, "E": -0.04, "F": -0.09, "G": -0.14}
    delta = dpe_map.get(body.dpe.upper(), 0.0)
    cond_l = body.condition_label.lower()
    if "rénové" in cond_l or "neuf" in cond_l:
        delta += 0.06
    elif "rafraîchir" in cond_l:
        delta -= 0.07
    elif "rénover" in cond_l:
        delta -= 0.15
    if body.has_terrace_or_garden:
        delta += 0.05
    if body.has_parking:
        delta += 0.03

    adj_m2 = round(base_m2 * (1.0 + delta))
    mid = round((adj_m2 * body.surface_m2) / 500.0) * 500
    low = round((mid * 0.93) / 500.0) * 500
    high = round((mid * 1.07) / 500.0) * 500
    return {
        "sector_name": matched_name,
        "base_price_m2": base_m2,
        "adjusted_price_m2": adj_m2,
        "estimated_low_euro": low,
        "estimated_mid_euro": mid,
        "estimated_high_euro": high,
        "market_tension": tension,
        "data_date": "2026-11-25",
        "disclaimer": (
            "Fourchette indicative basée sur les repères médians nationaux Monty. "
            "Pour un avis de valeur opposable, demandez une expertise locale avec comparables DVF exacts."
        ),
    }


class CapitalGainRequest(BaseModel):
    purchase_price: int = Field(ge=1000, le=50_000_000)
    sale_price: int = Field(ge=1000, le=50_000_000)
    holding_years: int = Field(ge=0, le=100)
    is_primary_residence: bool = False
    use_flat_acquisition_7_5_pct: bool = True
    actual_acquisition_fees: int = Field(default=0, ge=0)
    use_flat_works_15_pct: bool = True
    actual_works_cost: int = Field(default=0, ge=0)
    sale_agency_fees_seller_paid: int = Field(default=0, ge=0)


@news_router.post("/public/calculators/capital-gain", tags=["Simulateurs Publics"])
async def public_capital_gain(body: CapitalGainRequest) -> dict:
    res = compute_capital_gain_tax(
        purchase_price=body.purchase_price,
        sale_price=body.sale_price,
        holding_years=body.holding_years,
        is_primary_residence=body.is_primary_residence,
        use_flat_acquisition_7_5_pct=body.use_flat_acquisition_7_5_pct,
        actual_acquisition_fees=body.actual_acquisition_fees,
        use_flat_works_15_pct=body.use_flat_works_15_pct,
        actual_works_cost=body.actual_works_cost,
        sale_agency_fees_seller_paid=body.sale_agency_fees_seller_paid,
    )
    return res.__dict__


# ---------------------------------------------------------------------------
# Espace Professionnel SaaS (RBAC strict) + Préférences & Favoris + Admin Sources
# ---------------------------------------------------------------------------
@news_router.get("/pro/news", tags=["Actualités Pro"])
async def list_pro_news(
    country_code: CountryCode = CountryCode.FR,
    category: NewsCategory | None = None,
    region_name: str = Query(default="", max_length=100),
    department_code: str = Query(default="", max_length=10),
    city: str = Query(default="", max_length=100),
    query: str = Query(default="", max_length=120),
    include_archived: bool = False,
    include_drafts: bool = False,
    ctx: TenantContext = Depends(require_permission(Permission.NEWS_PRO_READ)),
) -> dict:
    items = _repo.query_pro_articles(
        ctx,
        country_code=country_code,
        category=category,
        region_name=region_name,
        department_code=department_code,
        city=city,
        query=query,
        include_archived=include_archived,
        include_drafts=include_drafts,
    )
    return {"country_code": country_code.value, "count": len(items), "articles": items}


class DualBriefRequest(BaseModel):
    topic: str = Field(min_length=4, max_length=300)
    source_excerpt: str = Field(min_length=10, max_length=1800)
    location_label: str = Field(default="France (National)", max_length=120)
    complex_mode: bool = False


@news_router.post("/pro/news/ai-dual-brief", tags=["Actualités Pro"])
async def generate_pro_public_dual_brief(
    body: DualBriefRequest,
    _ctx: TenantContext = Depends(require_permission(Permission.AI_ASSISTANT_PRO)),
    settings: Settings = Depends(get_settings),
) -> dict:
    brief = generate_dual_audience_briefing(
        settings=settings,
        topic=body.topic,
        source_excerpt=body.source_excerpt,
        location_label=body.location_label,
        complex_mode=body.complex_mode,
    )
    return {
        "mode": brief.mode,
        "model_used": brief.model_used,
        "sanitized_topic": brief.sanitized_topic,
        "pro_analysis": brief.pro_analysis,
        "pro_commercial_arguments": brief.pro_commercial_arguments,
        "public_summary": brief.public_summary,
        "public_takeaways": brief.public_takeaways,
        "legal_disclaimer": brief.legal_disclaimer,
    }


class PreferencesRequest(BaseModel):
    country_code: CountryCode = CountryCode.FR
    locale: str = Field(default="fr-FR", max_length=10)
    city: str = Field(default="", max_length=100)
    department_code: str = Field(default="", max_length=10)
    region_name: str = Field(default="National", max_length=100)
    interests: list[NewsCategory] = Field(default_factory=list)
    notifications_enabled: bool = False
    rgpd_consent_granted: bool = False


@news_router.get("/news/preferences", tags=["Préférences Actualités"])
async def get_my_news_preferences(ctx: TenantContext = Depends(get_current_tenant)) -> dict:
    pref = _repo.get_preferences(ctx.user_id)
    return {
        "user_id": pref.user_id,
        "country_code": pref.country_code.value,
        "locale": pref.locale,
        "city": pref.city,
        "department_code": pref.department_code,
        "region_name": pref.region_name,
        "interests": [c.value for c in pref.interests],
        "notifications_enabled": pref.notifications_enabled,
        "rgpd_consent_granted": pref.rgpd_consent_granted,
        "rgpd_consent_date": pref.rgpd_consent_date,
    }


@news_router.put("/news/preferences", tags=["Préférences Actualités"])
async def update_my_news_preferences(
    body: PreferencesRequest,
    ctx: TenantContext = Depends(get_current_tenant),
) -> dict:
    try:
        pref = _repo.upsert_preferences(
            user_id=ctx.user_id,
            country_code=body.country_code,
            locale=body.locale,
            city=body.city,
            department_code=body.department_code,
            region_name=body.region_name,
            interests=body.interests,
            notifications_enabled=body.notifications_enabled,
            rgpd_consent_granted=body.rgpd_consent_granted,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return {
        "user_id": pref.user_id,
        "country_code": pref.country_code.value,
        "locale": pref.locale,
        "city": pref.city,
        "department_code": pref.department_code,
        "region_name": pref.region_name,
        "interests": [c.value for c in pref.interests],
        "notifications_enabled": pref.notifications_enabled,
        "rgpd_consent_granted": pref.rgpd_consent_granted,
        "rgpd_consent_date": pref.rgpd_consent_date,
    }


@news_router.get("/news/bookmarks", tags=["Préférences Actualités"])
async def get_my_bookmarks(ctx: TenantContext = Depends(get_current_tenant)) -> dict:
    items = _repo.list_bookmarks(ctx.user_id)
    return {
        "bookmarks": [
            {
                "article_id": b.article_id,
                "alert_on_update": b.alert_on_update,
                "created_at": b.created_at,
            }
            for b in items
        ]
    }


@news_router.post("/news/bookmarks/{article_id}", tags=["Préférences Actualités"])
async def toggle_my_bookmark(
    article_id: str,
    alert_on_update: bool = True,
    ctx: TenantContext = Depends(get_current_tenant),
) -> dict:
    try:
        saved = _repo.toggle_bookmark(ctx.user_id, article_id, alert_on_update=alert_on_update)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return {"article_id": article_id, "saved": saved}


class CreateSourceRequest(BaseModel):
    country_code: CountryCode = CountryCode.FR
    locale: str = ""
    name: str = Field(min_length=2, max_length=140)
    domain: str = Field(min_length=3, max_length=140)
    base_url: str = Field(min_length=8, max_length=400)
    reliability_tier: str = "INSTITUTIONNELLE"


@news_router.post("/admin/news/sources", tags=["Administration Actualités"])
async def admin_create_source(
    body: CreateSourceRequest,
    ctx: TenantContext = Depends(require_permission(Permission.NEWS_ADMIN)),
) -> dict:
    try:
        src = _repo.add_source(
            ctx,
            country_code=body.country_code,
            locale=body.locale,
            name=body.name,
            domain=body.domain,
            base_url=body.base_url,
            reliability_tier=body.reliability_tier,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return {
        "id": src.id,
        "country_code": src.country_code.value,
        "locale": src.locale,
        "name": src.name,
        "domain": src.domain,
        "base_url": src.base_url,
        "reliability_tier": src.reliability_tier,
        "last_verified_at": src.last_verified_at,
    }


class CreateArticleRequest(BaseModel):
    source_id: str
    source_url: str
    country_code: CountryCode | None = None
    locale: str = ""
    category: NewsCategory
    audience_scope: AudienceScope = AudienceScope.BOTH
    title: str = Field(min_length=5, max_length=200)
    city: str = ""
    department_code: str = ""
    region_name: str = ""
    public_summary: str = ""
    public_takeaways: list[str] = Field(default_factory=list)
    pro_analysis: str = ""
    pro_commercial_arguments: list[str] = Field(default_factory=list)
    pro_key_metrics: dict[str, str] = Field(default_factory=dict)
    ai_dual_generated: bool = False
    publish_now: bool = True


@news_router.post("/admin/news/articles", tags=["Administration Actualités"])
async def admin_create_article(
    body: CreateArticleRequest,
    ctx: TenantContext = Depends(require_permission(Permission.NEWS_ADMIN)),
) -> dict:
    try:
        art = _repo.create_article(
            ctx,
            source_id=body.source_id,
            source_url=body.source_url,
            country_code=body.country_code,
            locale=body.locale,
            category=body.category,
            audience_scope=body.audience_scope,
            title=body.title,
            city=body.city,
            department_code=body.department_code,
            region_name=body.region_name,
            public_summary=body.public_summary,
            public_takeaways=body.public_takeaways,
            pro_analysis=body.pro_analysis,
            pro_commercial_arguments=body.pro_commercial_arguments,
            pro_key_metrics=body.pro_key_metrics,
            ai_dual_generated=body.ai_dual_generated,
            publish_now=body.publish_now,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _repo.to_pro_dto(art)
