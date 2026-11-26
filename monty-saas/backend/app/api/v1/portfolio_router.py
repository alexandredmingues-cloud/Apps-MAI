from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field

from app.api.v1.crm_router import _crm
from app.api.v1.deps import get_db_session_factory, require_permission
from app.core.rbac import Permission, TenantContext
from app.db.postgres_portfolio_leads_repository import PostgresPortfolioLeadsRepository
from app.domain.interconnections_service import interconnections_service
from app.domain.portfolio_and_leads_service import (
    PortfolioAndLeadsService,
    TransactionStage,
    VisitStatus,
)

portfolio_router = APIRouter(prefix="/portfolio", tags=["Phase 3 — Portefeuille, Visites & Transactions"])
_portfolio_service = PortfolioAndLeadsService(_crm, interconnections_service)


def _prop_dto(p) -> dict:
    return {
        "id": p.id,
        "agency_id": p.agency_id,
        "title": p.title,
        "sector": p.sector,
        "property_type": p.property_type,
        "price": p.price,
        "surface": p.surface,
        "bedrooms": p.bedrooms,
        "dpe": p.dpe,
        "mandate": p.mandate,
        "docs_ratio": p.docs_ratio,
        "created_at": p.created_at,
    }


def _visit_dto(v) -> dict:
    return {
        "id": v.id,
        "agency_id": v.agency_id,
        "property_id": v.property_id,
        "property_title": v.property_title,
        "buyer_name": v.buyer_name,
        "date_label": v.date_label,
        "status": v.status.value,
        "feedback": v.feedback,
        "created_at": v.created_at,
    }


def _tx_dto(t) -> dict:
    return {
        "id": t.id,
        "agency_id": t.agency_id,
        "property_id": t.property_id,
        "property_title": t.property_title,
        "buyer_name": t.buyer_name,
        "offer_euro": t.offer_euro,
        "stage": t.stage.value,
        "notary_target_date": t.notary_target_date,
        "created_at": t.created_at,
    }


@portfolio_router.get("/properties")
async def list_portfolio_properties(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        items = await PostgresPortfolioLeadsRepository(sf).list_properties(ctx)
    else:
        items = _portfolio_service.list_properties(ctx)
    return {"count": len(items), "properties": [_prop_dto(p) for p in items]}


class CreatePortfolioPropertyRequest(BaseModel):
    title: str = Field(min_length=3, max_length=160)
    sector: str = Field(min_length=2, max_length=100)
    property_type: str = Field(default="Appartement", max_length=60)
    price: int = Field(ge=10_000, le=50_000_000)
    surface: int = Field(ge=5, le=5000)
    bedrooms: int = Field(default=2, ge=0, le=30)
    dpe: str = Field(default="C", max_length=4)
    mandate: str = Field(default="EXCLUSIF", max_length=20)
    docs_ratio: float = Field(default=0.70, ge=0.0, le=1.0)


@portfolio_router.post("/properties", status_code=status.HTTP_201_CREATED)
async def create_portfolio_property(
    body: CreatePortfolioPropertyRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            p = await PostgresPortfolioLeadsRepository(sf).create_property(
                ctx,
                title=body.title,
                sector=body.sector,
                property_type=body.property_type,
                price=body.price,
                surface=body.surface,
                bedrooms=body.bedrooms,
                dpe=body.dpe,
                mandate=body.mandate,
                docs_ratio=body.docs_ratio,
            )
        else:
            p = _portfolio_service.create_property(
                ctx,
                title=body.title,
                sector=body.sector,
                property_type=body.property_type,
                price=body.price,
                surface=body.surface,
                bedrooms=body.bedrooms,
                dpe=body.dpe,
                mandate=body.mandate,
                docs_ratio=body.docs_ratio,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _prop_dto(p)


@portfolio_router.get("/visits")
async def list_portfolio_visits(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        items = await PostgresPortfolioLeadsRepository(sf).list_visits(ctx)
    else:
        items = _portfolio_service.list_visits(ctx)
    return {"count": len(items), "visits": [_visit_dto(v) for v in items]}


class CreateVisitRequest(BaseModel):
    property_id: str = Field(min_length=2, max_length=120)
    buyer_name: str = Field(min_length=2, max_length=140)
    date_label: str = Field(min_length=2, max_length=120)
    feedback: str = Field(default="", max_length=500)


@portfolio_router.post("/visits", status_code=status.HTTP_201_CREATED)
async def create_portfolio_visit(
    body: CreateVisitRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            v = await PostgresPortfolioLeadsRepository(sf).create_visit(
                ctx,
                property_id=body.property_id,
                buyer_name=body.buyer_name,
                date_label=body.date_label,
                feedback=body.feedback,
            )
        else:
            v = _portfolio_service.create_visit(
                ctx,
                property_id=body.property_id,
                buyer_name=body.buyer_name,
                date_label=body.date_label,
                feedback=body.feedback,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _visit_dto(v)


class UpdateVisitStatusRequest(BaseModel):
    status: VisitStatus
    feedback: str | None = Field(default=None, max_length=500)


@portfolio_router.patch("/visits/{visit_id}")
async def update_portfolio_visit(
    visit_id: str,
    body: UpdateVisitStatusRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            v = await PostgresPortfolioLeadsRepository(sf).update_visit_status(
                ctx,
                visit_id=visit_id,
                status=body.status,
                feedback=body.feedback,
            )
        else:
            v = _portfolio_service.update_visit_status(
                ctx,
                visit_id=visit_id,
                status=body.status,
                feedback=body.feedback,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return _visit_dto(v)


@portfolio_router.get("/transactions")
async def list_portfolio_transactions(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        items = await PostgresPortfolioLeadsRepository(sf).list_transactions(ctx)
    else:
        items = _portfolio_service.list_transactions(ctx)
    return {"count": len(items), "transactions": [_tx_dto(t) for t in items]}


class CreateTransactionRequest(BaseModel):
    property_id: str = Field(min_length=2, max_length=120)
    buyer_name: str = Field(min_length=2, max_length=140)
    offer_euro: int = Field(ge=10_000, le=50_000_000)
    stage: TransactionStage = TransactionStage.OFFRE
    notary_target_date: str = Field(default="", max_length=20)


@portfolio_router.post("/transactions", status_code=status.HTTP_201_CREATED)
async def create_portfolio_transaction(
    body: CreateTransactionRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            t = await PostgresPortfolioLeadsRepository(sf).create_transaction(
                ctx,
                property_id=body.property_id,
                buyer_name=body.buyer_name,
                offer_euro=body.offer_euro,
                stage=body.stage,
                notary_target_date=body.notary_target_date,
            )
        else:
            t = _portfolio_service.create_transaction(
                ctx,
                property_id=body.property_id,
                buyer_name=body.buyer_name,
                offer_euro=body.offer_euro,
                stage=body.stage,
                notary_target_date=body.notary_target_date,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _tx_dto(t)


class UpdateTransactionStageRequest(BaseModel):
    stage: TransactionStage


@portfolio_router.patch("/transactions/{tx_id}")
async def update_portfolio_transaction_stage(
    tx_id: str,
    body: UpdateTransactionStageRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            t = await PostgresPortfolioLeadsRepository(sf).update_transaction_stage(
                ctx,
                tx_id=tx_id,
                stage=body.stage,
            )
        else:
            t = _portfolio_service.update_transaction_stage(
                ctx,
                tx_id=tx_id,
                stage=body.stage,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return _tx_dto(t)
