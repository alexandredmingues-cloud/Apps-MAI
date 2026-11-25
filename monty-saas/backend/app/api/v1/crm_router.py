from __future__ import annotations

import csv
import io

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from pydantic import BaseModel, Field

from app.api.v1.deps import get_db_session_factory, require_permission
from app.core.rbac import Permission, TenantContext
from app.core.rgpd import LEGAL_BASIS_CONSENT
from app.core.security import InputSanitizer
from app.db.postgres_portfolio_leads_repository import PostgresPortfolioLeadsRepository
from app.db.postgres_repositories import PostgresCrmRepository
from app.domain.country_packs import CountryCode
from app.domain.crm_service import (
    ContactRole,
    CrmService,
    ExchangeChannel,
)
from app.domain.csv_import_export import (
    export_contacts_to_csv,
    import_contacts_from_csv,
)
from app.domain.portfolio_and_leads_service import (
    LeadStatus,
    PortfolioAndLeadsService,
)

crm_router = APIRouter(prefix="/crm", tags=["Phase 2 — CRM & RGPD"])
_crm = CrmService()
_leads_service = PortfolioAndLeadsService(_crm)


def _public_lead_dto(lead) -> dict:
    return {
        "id": lead.id,
        "agency_id": lead.agency_id,
        "crm_contact_id": lead.crm_contact_id,
        "lead_kind": lead.lead_kind.value,
        "country_code": lead.country_code.value,
        "full_name": lead.full_name,
        "email": lead.email,
        "phone": lead.phone,
        "city": lead.city,
        "budget_euro": lead.budget_euro,
        "min_rooms": lead.min_rooms,
        "search_transport_mode": lead.search_transport_mode,
        "max_travel_minutes": lead.max_travel_minutes,
        "horizon": lead.horizon,
        "property_title": lead.property_title,
        "notes": lead.notes,
        "rgpd_consent_granted": lead.rgpd_consent_granted,
        "rgpd_consent_at": lead.rgpd_consent_at,
        "status": lead.status.value,
        "created_at": lead.created_at,
    }


def _contact_dto(c) -> dict:
    return {
        "id": c.id,
        "agency_id": c.agency_id,
        "assigned_user_id": c.assigned_user_id,
        "country_code": c.country_code.value,
        "full_name": c.full_name,
        "email": c.email,
        "phone": c.phone,
        "role": c.role.value,
        "city": c.city,
        "budget_euro": c.budget_euro,
        "legal_basis": c.legal_basis,
        "consent_granted": c.consent_granted,
        "retention_until": c.retention_until,
        "created_at": c.created_at,
        "last_contact_at": c.last_contact_at,
        "has_missing_docs": c.has_missing_docs,
        "pending_offer_days": c.pending_offer_days,
        "last_visit_days_ago": c.last_visit_days_ago,
        "is_anonymized": c.is_anonymized,
    }


@crm_router.get("/contacts")
async def list_crm_contacts(
    role: ContactRole | None = None,
    country_code: CountryCode | None = None,
    query: str = Query(default="", max_length=120),
    ctx: TenantContext = Depends(require_permission(Permission.CRM_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        items = await PostgresCrmRepository(sf).list_contacts(ctx, role=role, country_code=country_code, query=query)
    else:
        items = _crm.list_contacts(ctx, role=role, country_code=country_code, query=query)
    return {"count": len(items), "contacts": [_contact_dto(c) for c in items]}


class CreateContactRequest(BaseModel):
    full_name: str = Field(min_length=2, max_length=140)
    email: str = Field(default="", max_length=160)
    phone: str = Field(default="", max_length=40)
    role: ContactRole = ContactRole.LEAD
    country_code: CountryCode = CountryCode.FR
    city: str = Field(default="", max_length=100)
    budget_euro: int = Field(default=0, ge=0)
    legal_basis: str = LEGAL_BASIS_CONSENT
    consent_granted: bool = True


@crm_router.post("/contacts", status_code=status.HTTP_201_CREATED)
async def create_crm_contact(
    body: CreateContactRequest,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            c = await PostgresCrmRepository(sf).create_contact(
                ctx,
                full_name=body.full_name,
                email=body.email,
                phone=body.phone,
                role=body.role,
                country_code=body.country_code,
                city=body.city,
                budget_euro=body.budget_euro,
                legal_basis=body.legal_basis,
                consent_granted=body.consent_granted,
            )
        else:
            c = _crm.create_contact(
                ctx,
                full_name=body.full_name,
                email=body.email,
                phone=body.phone,
                role=body.role,
                country_code=body.country_code,
                city=body.city,
                budget_euro=body.budget_euro,
                legal_basis=body.legal_basis,
                consent_granted=body.consent_granted,
            )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return _contact_dto(c)


class NoteRequest(BaseModel):
    content: str = Field(min_length=2, max_length=2000)


@crm_router.get("/contacts/{contact_id}/private-notes")
async def get_private_notes(
    contact_id: str,
    ctx: TenantContext = Depends(require_permission(Permission.PRIVATE_NOTES_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            notes = await PostgresCrmRepository(sf).list_private_notes(ctx, contact_id=contact_id)
        else:
            notes = _crm.list_private_notes(ctx, contact_id=contact_id)
    except (ValueError, PermissionError) as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    return {"notes": [n.__dict__ for n in notes]}


@crm_router.post("/contacts/{contact_id}/private-notes", status_code=status.HTTP_201_CREATED)
async def add_private_note(
    contact_id: str,
    body: NoteRequest,
    ctx: TenantContext = Depends(require_permission(Permission.PRIVATE_NOTES_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            note = await PostgresCrmRepository(sf).add_private_note(ctx, contact_id=contact_id, content=body.content)
        else:
            note = _crm.add_private_note(ctx, contact_id=contact_id, content=body.content)
    except (ValueError, PermissionError) as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return note.__dict__


class ExchangeRequest(BaseModel):
    channel: ExchangeChannel = ExchangeChannel.CALL
    summary: str = Field(min_length=2, max_length=1500)


@crm_router.post("/contacts/{contact_id}/exchanges", status_code=status.HTTP_201_CREATED)
async def add_exchange(
    contact_id: str,
    body: ExchangeRequest,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            ex = await PostgresCrmRepository(sf).log_exchange(
                ctx, contact_id=contact_id, channel=body.channel, summary=body.summary
            )
        else:
            ex = _crm.log_exchange(ctx, contact_id=contact_id, channel=body.channel, summary=body.summary)
    except (ValueError, PermissionError) as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return ex.__dict__


@crm_router.get("/followups/auto")
async def get_auto_followups(
    ctx: TenantContext = Depends(require_permission(Permission.CRM_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        tasks = await PostgresCrmRepository(sf).generate_automatic_followups(ctx)
    else:
        tasks = _crm.generate_automatic_followups(ctx)
    return {"count": len(tasks), "tasks": [t.__dict__ for t in tasks]}


@crm_router.post("/followups/{task_id}/complete")
async def complete_followup(
    task_id: str,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            task = await PostgresCrmRepository(sf).complete_task(ctx, task_id)
        else:
            task = _crm.complete_task(ctx, task_id)
    except (ValueError, PermissionError) as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return task.__dict__


class CsvImportRequest(BaseModel):
    csv_text: str = Field(min_length=5, max_length=500_000)
    default_country: CountryCode = CountryCode.FR


@crm_router.post("/import-csv")
async def import_csv(
    body: CsvImportRequest,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        repo = PostgresCrmRepository(sf)
        existing = await repo.list_contacts(ctx)
        temp_crm = CrmService()
        temp_crm._contacts = {c.id: c for c in existing}
        summary = import_contacts_from_csv(ctx, temp_crm, csv_text=body.csv_text, default_country=body.default_country)
        persisted_ids: list[str] = []
        for cid in summary.created_contact_ids:
            c = temp_crm._contacts[cid]
            persisted = await repo.create_contact(
                ctx,
                full_name=c.full_name,
                email=c.email,
                phone=c.phone,
                role=c.role,
                country_code=c.country_code,
                city=c.city,
                budget_euro=c.budget_euro,
                legal_basis=c.legal_basis,
                consent_granted=c.consent_granted,
                retention_until=c.retention_until,
            )
            persisted_ids.append(persisted.id)
        return {
            "imported_count": len(persisted_ids),
            "skipped_duplicates": summary.skipped_duplicates,
            "rejected_rows": summary.rejected_rows,
            "warnings": summary.warnings,
            "created_contact_ids": persisted_ids,
        }

    summary = import_contacts_from_csv(
        ctx,
        _crm,
        csv_text=body.csv_text,
        default_country=body.default_country,
    )
    return summary.__dict__


@crm_router.get("/export-csv")
async def export_csv(
    ctx: TenantContext = Depends(require_permission(Permission.CRM_READ)),
    sf=Depends(get_db_session_factory),
) -> Response:
    if sf is not None:
        contacts = await PostgresCrmRepository(sf).list_contacts(ctx)
        buf = io.StringIO()
        buf.write("\ufeff")
        writer = csv.writer(buf, delimiter=";")
        writer.writerow(["id", "pays", "nom_complet", "email", "telephone", "role", "ville", "budget_euro", "base_legale_rgpd", "conservation_jusqu_au"])
        for c in contacts:
            writer.writerow(
                [
                    InputSanitizer.sanitize_spreadsheet_cell(c.id),
                    InputSanitizer.sanitize_spreadsheet_cell(c.country_code.value),
                    InputSanitizer.sanitize_spreadsheet_cell(c.full_name),
                    InputSanitizer.sanitize_spreadsheet_cell(c.email),
                    InputSanitizer.sanitize_spreadsheet_cell(c.phone),
                    InputSanitizer.sanitize_spreadsheet_cell(c.role.value),
                    InputSanitizer.sanitize_spreadsheet_cell(c.city),
                    str(c.budget_euro),
                    InputSanitizer.sanitize_spreadsheet_cell(c.legal_basis),
                    InputSanitizer.sanitize_spreadsheet_cell(c.retention_until),
                ]
            )
        csv_data = buf.getvalue()
    else:
        csv_data = export_contacts_to_csv(ctx, _crm)
    return Response(
        content=csv_data,
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": 'attachment; filename="monty-contacts-rgpd.csv"'},
    )


@crm_router.get("/contacts/{contact_id}/rgpd-export")
async def rgpd_portability_export(
    contact_id: str,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            return await PostgresCrmRepository(sf).export_contact_rgpd(ctx, contact_id=contact_id)
        return _crm.export_contact_rgpd(ctx, contact_id=contact_id)
    except (ValueError, PermissionError) as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc


@crm_router.post("/rgpd/purge-expired")
async def rgpd_purge_expired(
    ctx: TenantContext = Depends(require_permission(Permission.CRM_DELETE_RGPD)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        count = await PostgresCrmRepository(sf).execute_rgpd_purge(ctx)
    else:
        count = _crm.execute_rgpd_purge(ctx)
    return {"anonymized_count": count}


@crm_router.get("/public-leads")
async def list_public_inbound_leads(
    lead_status: LeadStatus | None = Query(default=None, alias="status"),
    ctx: TenantContext = Depends(require_permission(Permission.CRM_READ)),
    sf=Depends(get_db_session_factory),
) -> dict:
    if sf is not None:
        items = await PostgresPortfolioLeadsRepository(sf).list_public_leads(ctx, status=lead_status)
    else:
        items = _leads_service.list_public_leads(ctx, status=lead_status)
    return {"count": len(items), "leads": [_public_lead_dto(item) for item in items]}


class UpdatePublicLeadStatusRequest(BaseModel):
    status: LeadStatus


@crm_router.patch("/public-leads/{lead_id}")
async def update_public_inbound_lead(
    lead_id: str,
    body: UpdatePublicLeadStatusRequest,
    ctx: TenantContext = Depends(require_permission(Permission.CRM_WRITE)),
    sf=Depends(get_db_session_factory),
) -> dict:
    try:
        if sf is not None:
            updated = await PostgresPortfolioLeadsRepository(sf).update_public_lead_status(
                ctx,
                lead_id=lead_id,
                status=body.status,
            )
        else:
            updated = _leads_service.update_public_lead_status(
                ctx,
                lead_id=lead_id,
                status=body.status,
            )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return _public_lead_dto(updated)

