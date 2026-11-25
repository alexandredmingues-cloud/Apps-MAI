from __future__ import annotations

import asyncio
from datetime import date
import json
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, status
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, Field

from app.api.v1.deps import (
    check_ai_rate_limit,
    get_current_tenant,
    get_db_session_factory,
    get_settings,
    require_permission,
)
from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.db.postgres_v2_ops_repository import PostgresV2OpsRepository
from app.domain.ai_agents_service import (
    AGENTS_CATALOG,
    APPROVED_WHATSAPP_TEMPLATES,
    MontyAgentsGovernanceService,
)
from app.domain.dvf_sync_service import DvfSyncService
from app.domain.pre_etat_date import (
    CoproLotInput,
    PreEtatDateInput,
    compute_pre_etat_date,
    render_pre_etat_date_html,
    serialize_pre_etat_result,
)

legal_agents_router = APIRouter(tags=["Pré-État Daté, 11 Agents IA, DVF Sync & WhatsApp"])

_agents_service = MontyAgentsGovernanceService()
_dvf_sync_service = DvfSyncService()


class CoproLotIn(BaseModel):
    lot_number: str = Field(min_length=1, max_length=32)
    nature: str = Field(min_length=2, max_length=60)
    tantiemes_generaux: int = Field(gt=0, le=1_000_000)
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)
    validated: bool = True


class PreEtatDateRequest(BaseModel):
    dossier_ref: str = Field(min_length=2, max_length=64)
    seller_name: str = Field(min_length=2, max_length=120)
    property_address: str = Field(min_length=4, max_length=200)
    syndic_name: str = Field(default="Syndic non précisé", max_length=120)
    total_tantiemes_copro: int = Field(gt=0, le=10_000_000)
    lots: list[CoproLotIn] = Field(min_length=1, max_length=30)
    annual_budget_copro_euro: float = Field(ge=0.0, le=50_000_000.0)
    charges_n_minus_1_lot_euro: float = Field(ge=0.0, le=5_000_000.0)
    charges_n_minus_2_lot_euro: float = Field(ge=0.0, le=5_000_000.0)
    non_budget_works_voted_euro: float = Field(default=0.0, ge=0.0, le=5_000_000.0)
    seller_unpaid_charges_euro: float = Field(default=0.0, ge=0.0, le=5_000_000.0)
    works_fund_alur_balance_euro: float = Field(default=0.0, ge=0.0, le=5_000_000.0)
    current_quarter_called_euro: float = Field(default=0.0, ge=0.0, le=1_000_000.0)
    sale_date: date = date(2026, 6, 15)
    quarter_start_date: date = date(2026, 4, 1)
    quarter_end_date: date = date(2026, 6, 30)
    custom_syndic_fee_euro: float = Field(default=420.0, ge=0.0, le=5000.0)
    has_fiche_synthetique: bool = True
    has_reglement_copro_edd: bool = True
    has_pv_ag_3_years: bool = True
    has_carnet_entretien: bool = True
    has_dtg_or_pppt: bool = True
    has_financial_annex: bool = True
    has_pending_procedures_statement: bool = True
    has_alur_works_fund_statement: bool = True
    confidence_map: dict[str, float] = Field(default_factory=dict)
    validated_fields: list[str] = Field(default_factory=list)


def _to_domain_pre_etat(body: PreEtatDateRequest) -> PreEtatDateInput:
    return PreEtatDateInput(
        dossier_ref=body.dossier_ref,
        seller_name=body.seller_name,
        property_address=body.property_address,
        syndic_name=body.syndic_name,
        total_tantiemes_copro=body.total_tantiemes_copro,
        lots=[
            CoproLotInput(
                lot_number=l.lot_number,
                nature=l.nature,
                tantiemes_generaux=l.tantiemes_generaux,
                confidence=l.confidence,
                validated=l.validated,
            )
            for l in body.lots
        ],
        annual_budget_copro_euro=body.annual_budget_copro_euro,
        charges_n_minus_1_lot_euro=body.charges_n_minus_1_lot_euro,
        charges_n_minus_2_lot_euro=body.charges_n_minus_2_lot_euro,
        non_budget_works_voted_euro=body.non_budget_works_voted_euro,
        seller_unpaid_charges_euro=body.seller_unpaid_charges_euro,
        works_fund_alur_balance_euro=body.works_fund_alur_balance_euro,
        current_quarter_called_euro=body.current_quarter_called_euro,
        sale_date=body.sale_date,
        quarter_start_date=body.quarter_start_date,
        quarter_end_date=body.quarter_end_date,
        custom_syndic_fee_euro=body.custom_syndic_fee_euro,
        has_fiche_synthetique=body.has_fiche_synthetique,
        has_reglement_copro_edd=body.has_reglement_copro_edd,
        has_pv_ag_3_years=body.has_pv_ag_3_years,
        has_carnet_entretien=body.has_carnet_entretien,
        has_dtg_or_pppt=body.has_dtg_or_pppt,
        has_financial_annex=body.has_financial_annex,
        has_pending_procedures_statement=body.has_pending_procedures_statement,
        has_alur_works_fund_statement=body.has_alur_works_fund_statement,
        confidence_map=body.confidence_map,
        validated_fields=set(body.validated_fields),
    )


@legal_agents_router.post("/pre-etat-date/compute")
async def compute_pre_etat_date_endpoint(
    body: PreEtatDateRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        res = compute_pre_etat_date(_to_domain_pre_etat(body))
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    serialized = serialize_pre_etat_result(res)
    if sf is not None:
        await PostgresV2OpsRepository(sf).save_pre_etat_dossier(
            ctx,
            dossier_ref=res.dossier_ref,
            document_version=res.document_version,
            export_allowed=res.export_allowed,
            payload_dict=body.model_dump(mode="json"),
            result_dict=serialized,
        )
    return serialized


@legal_agents_router.post("/pre-etat-date/export")
async def export_pre_etat_date_endpoint(
    body: PreEtatDateRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        inp = _to_domain_pre_etat(body)
        res = compute_pre_etat_date(inp)
        if not res.export_allowed:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail={"message": "Export bloqué jusqu'à régularisation.", "blocking_reasons": res.blocking_reasons},
            )
        html_doc = render_pre_etat_date_html(inp, res)
    except HTTPException:
        raise
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    if sf is not None:
        await PostgresV2OpsRepository(sf).save_pre_etat_dossier(
            ctx,
            dossier_ref=res.dossier_ref,
            document_version=res.document_version,
            export_allowed=res.export_allowed,
            payload_dict=body.model_dump(mode="json"),
            result_dict=serialize_pre_etat_result(res),
        )
    return {
        "dossier_ref": res.dossier_ref,
        "document_version": res.document_version,
        "generated_at_iso": res.generated_at_iso,
        "filename": f"pre-etat-date-{res.dossier_ref}.html",
        "html_content": html_doc,
    }


@legal_agents_router.get("/pre-etat-date/dossiers")
async def list_pre_etat_dossiers(
    ctx: TenantContext = Depends(require_permission(Permission.PROPERTY_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is None:
        return {"count": 0, "dossiers": []}
    dossiers = await PostgresV2OpsRepository(sf).list_pre_etat_dossiers(ctx)
    return {"count": len(dossiers), "dossiers": dossiers}


@legal_agents_router.get("/agents/catalog")
async def get_agents_catalog(
    ctx: TenantContext = Depends(get_current_tenant),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        repo = PostgresV2OpsRepository(sf)
        ks_enabled, ks_reason = await repo.get_kill_switch_state()
        daily_spend = await repo.get_daily_spend(ctx)
    else:
        ks_enabled, ks_reason = _agents_service.is_kill_switch_active()
        daily_spend = _agents_service.get_daily_spend(ctx.agency_id)
    return {
        "agents": [a.__dict__ for a in AGENTS_CATALOG],
        "kill_switch_enabled": ks_enabled,
        "kill_switch_reason": ks_reason,
        "daily_spend_euro": daily_spend,
        "daily_budget_euro": settings.ai_daily_budget_euro,
    }


class RunAgentRequest(BaseModel):
    agent_id: str = Field(min_length=2, max_length=64)
    prompt_text: str = Field(min_length=4, max_length=2000)


@legal_agents_router.post("/agents/run")
async def run_autonomous_agent(
    body: RunAgentRequest,
    ctx: TenantContext = Depends(get_current_tenant),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    check_ai_rate_limit(ctx)
    repo = PostgresV2OpsRepository(sf) if sf is not None else None
    if repo is not None:
        ks_enabled, ks_reason = await repo.get_kill_switch_state()
        _agents_service._kill_switch_enabled = ks_enabled
        _agents_service._kill_switch_reason = ks_reason
        db_spend = await repo.get_daily_spend(ctx)
        _agents_service._daily_spend_by_agency[(ctx.agency_id, date.today().isoformat())] = db_spend

    try:
        log = await asyncio.to_thread(
            _agents_service.run_agent,
            ctx,
            agent_id=body.agent_id,
            prompt_text=body.prompt_text,
            settings=settings,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    except RuntimeError as exc:
        ks_active, _ = _agents_service.is_kill_switch_active()
        code = status.HTTP_503_SERVICE_UNAVAILABLE if ks_active else status.HTTP_429_TOO_MANY_REQUESTS
        raise HTTPException(status_code=code, detail=str(exc)) from exc

    if repo is not None:
        log = await repo.record_agent_log(ctx, log)
    return log.__dict__


@legal_agents_router.get("/agents/logs")
async def list_agent_logs(
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        logs = await PostgresV2OpsRepository(sf).list_agent_logs(ctx)
    else:
        logs = _agents_service.list_logs(ctx)
    return {"count": len(logs), "logs": [l.__dict__ for l in logs]}


class KillSwitchRequest(BaseModel):
    enabled: bool
    reason: str = Field(default="", max_length=200)


@legal_agents_router.post("/agents/kill-switch")
async def toggle_agents_kill_switch(
    body: KillSwitchRequest,
    ctx: TenantContext = Depends(get_current_tenant),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if sf is not None:
            res = await PostgresV2OpsRepository(sf).set_kill_switch_state(
                ctx, enabled=body.enabled, reason=body.reason
            )
            _agents_service._kill_switch_enabled = bool(res["kill_switch_enabled"])
            _agents_service._kill_switch_reason = str(res["reason"])
            return res
        return _agents_service.set_kill_switch(ctx, enabled=body.enabled, reason=body.reason)
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc


@legal_agents_router.get("/agents/scout/proposals")
async def list_scout_proposals(
    ctx: TenantContext = Depends(require_permission(Permission.CRM_READ)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        proposals = await PostgresV2OpsRepository(sf).list_scout_proposals(ctx)
    else:
        proposals = _agents_service.list_scout_proposals(ctx)
    return {"count": len(proposals), "proposals": [p.__dict__ for p in proposals]}


class CreateScoutProposalRequest(BaseModel):
    target_contact_name: str = Field(min_length=2, max_length=120)
    channel: str = Field(default="WHATSAPP", max_length=20)
    legal_basis: str = Field(default="INTERET_LEGITIME_PROSPECTION_B2C", max_length=80)
    message_draft: str = Field(min_length=10, max_length=600)


@legal_agents_router.post("/agents/scout/proposals", status_code=status.HTTP_201_CREATED)
async def create_scout_proposal(
    body: CreateScoutProposalRequest,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if sf is not None:
            prop = await PostgresV2OpsRepository(sf).create_scout_proposal(
                ctx,
                target_contact_name=body.target_contact_name,
                channel=body.channel,
                legal_basis=body.legal_basis,
                message_draft=body.message_draft,
            )
        else:
            prop = _agents_service.create_scout_proposal(
                ctx,
                target_contact_name=body.target_contact_name,
                channel=body.channel,
                legal_basis=body.legal_basis,
                message_draft=body.message_draft,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return prop.__dict__


class ScoutDecisionRequest(BaseModel):
    approve: bool


@legal_agents_router.post("/agents/scout/proposals/{proposal_id}/decision")
async def decide_scout_proposal(
    proposal_id: str,
    body: ScoutDecisionRequest,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    try:
        if sf is not None:
            prop = await PostgresV2OpsRepository(sf).review_scout_proposal(
                ctx, proposal_id=proposal_id, approve=body.approve
            )
        else:
            prop = _agents_service.review_scout_proposal(ctx, proposal_id=proposal_id, approve=body.approve)
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return prop.__dict__


@legal_agents_router.get("/webhooks/whatsapp")
async def verify_whatsapp_webhook(
    hub_mode: str = Query(default="", alias="hub.mode"),
    hub_verify_token: str = Query(default="", alias="hub.verify_token"),
    hub_challenge: str = Query(default="", alias="hub.challenge"),
    settings: Settings = Depends(get_settings),
) -> PlainTextResponse:
    if (
        hub_mode == "subscribe"
        and settings.whatsapp_verify_token
        and hub_verify_token == settings.whatsapp_verify_token
    ):
        return PlainTextResponse(content=hub_challenge)
    raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Vérification WhatsApp refusée.")


@legal_agents_router.post("/webhooks/whatsapp")
async def receive_whatsapp_webhook(
    request: Request,
    x_hub_signature_256: str = Header(default="", alias="X-Hub-Signature-256"),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    payload_bytes = await request.body()
    try:
        event = json.loads(payload_bytes.decode("utf-8"))
        res = _agents_service.handle_whatsapp_payload(
            payload_bytes=payload_bytes,
            signature_header=x_hub_signature_256,
            app_secret=settings.whatsapp_app_secret,
            event=event,
        )
        if sf is not None:
            repo = PostgresV2OpsRepository(sf)
            for entry in event.get("entry", []):
                for change in entry.get("changes", []):
                    for msg in change.get("value", {}).get("messages", []):
                        sender = str(msg.get("from", "")).strip()
                        body = str(msg.get("text", {}).get("body", "")).strip().upper()
                        if any(kw in body.split() for kw in ("STOP", "ARRET", "ARRÊT", "UNSUBSCRIBE")):
                            await repo.record_whatsapp_event(
                                phone=sender, direction="INBOUND", action="OPT_OUT", keyword_matched="STOP"
                            )
                        else:
                            await repo.record_whatsapp_event(
                                phone=sender, direction="INBOUND", action="MESSAGE_RECEIVED", keyword_matched=""
                            )
        return res
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc


class SendWhatsAppTemplateRequest(BaseModel):
    to_phone: str = Field(min_length=6, max_length=24)
    template_name: str = Field(min_length=3, max_length=80)
    body_params: list[str] = Field(default_factory=list, max_length=8)


@legal_agents_router.post("/webhooks/whatsapp/send-template")
async def send_whatsapp_template_endpoint(
    body: SendWhatsAppTemplateRequest,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_WRITE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    repo = PostgresV2OpsRepository(sf) if sf is not None else None
    try:
        if repo is not None and await repo.is_phone_opted_out(body.to_phone):
            raise PermissionError("Envoi bloqué : le destinataire s'est désinscrit via STOP.")
        res = await asyncio.to_thread(
            _agents_service.send_whatsapp_template,
            ctx,
            to_phone=body.to_phone,
            template_name=body.template_name,
            body_params=body.body_params,
            settings=settings,
        )
        if repo is not None:
            await repo.record_whatsapp_event(
                phone=body.to_phone,
                direction="OUTBOUND",
                action="TEMPLATE_SENT",
                keyword_matched=body.template_name,
            )
        return res
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc)) from exc


@legal_agents_router.get("/webhooks/whatsapp/templates")
async def list_approved_whatsapp_templates(
    _ctx: TenantContext = Depends(get_current_tenant),
) -> dict[str, Any]:
    return {"templates": APPROVED_WHATSAPP_TEMPLATES}


@legal_agents_router.get("/calculators/dvf/cache-status")
async def get_dvf_cache_status(
    _ctx: TenantContext = Depends(require_permission(Permission.ESTIMATION_RUN)),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    if sf is not None:
        db_status = await PostgresV2OpsRepository(sf).list_dvf_cache_status()
        if db_status["sectors_cached"]:
            return db_status
    return _dvf_sync_service.get_cache_status()


class DvfSyncRequest(BaseModel):
    sector_code: str = Field(default="69003", min_length=4, max_length=12)


@legal_agents_router.post("/calculators/dvf/sync")
async def trigger_dvf_sync(
    body: DvfSyncRequest,
    _ctx: TenantContext = Depends(require_permission(Permission.AGENCY_MANAGE)),
    settings: Settings = Depends(get_settings),
    sf=Depends(get_db_session_factory),
) -> dict[str, Any]:
    sector = body.sector_code.strip()
    report = await asyncio.to_thread(
        _dvf_sync_service.sync_sector_from_remote,
        sector_code=sector,
        source_url=settings.dvf_source_url,
    )
    if sf is not None and report.status == "synced_live":
        await PostgresV2OpsRepository(sf).upsert_dvf_comparables(
            sector, _dvf_sync_service.get_cached_comparables(sector)
        )
    return report.__dict__
