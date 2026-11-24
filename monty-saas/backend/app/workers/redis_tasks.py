from __future__ import annotations

from datetime import date, datetime, timezone
import logging
from typing import Any

from app.core.config import load_settings
from app.core.rbac import MontyRole, TenantContext
from app.domain.country_packs import CountryCode
from app.domain.crm_service import CrmService
from app.domain.csv_import_export import import_contacts_from_csv

logger = logging.getLogger("monty.workers")


async def run_csv_excel_import_job(
    ctx: dict[str, Any],
    agency_id: str,
    actor_user_id: str,
    *,
    actor_role: str = "DIRECTOR",
    csv_text: str = "",
    default_country: str = "FR",
) -> dict[str, Any]:
    """Tâche Redis ARQ asynchrone : import CSV/XLSX dédupliqué + traçabilité PostgreSQL ou service CRM."""
    tenant = TenantContext(
        user_id=actor_user_id,
        agency_id=agency_id,
        role=MontyRole(actor_role),
    )
    country = CountryCode(default_country.upper()) if default_country else CountryCode.FR
    if not csv_text.strip():
        return {
            "status": "no_payload",
            "agency_id": agency_id,
            "imported_count": 0,
            "skipped_duplicates": 0,
            "rejected_rows": 0,
        }

    settings = load_settings()
    crm_service: CrmService | None = ctx.get("crm_service") if isinstance(ctx, dict) else None

    if settings.database_url and crm_service is None:
        from sqlalchemy import text
        from app.db.postgres_repositories import PostgresCrmRepository
        from app.db.session import get_session_factory, tenant_db_session

        sf = get_session_factory(settings.database_url)
        if sf is not None:
            repo = PostgresCrmRepository(sf)
            # Chargement en mémoire temporaire pour déduplication stricte avant insertion RLS
            existing = await repo.list_contacts(tenant)
            temp_crm = CrmService()
            temp_crm._contacts = {c.id: c for c in existing}
            summary = import_contacts_from_csv(tenant, temp_crm, csv_text=csv_text, default_country=country)
            created_ids: list[str] = []
            for cid in summary.created_contact_ids:
                c = temp_crm._contacts[cid]
                persisted = await repo.create_contact(
                    tenant,
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
                created_ids.append(persisted.id)
            async with tenant_db_session(sf, tenant) as session:
                await session.execute(text("SELECT 1"))
            logger.info("Async PostgreSQL CSV import agency=%s imported=%s", agency_id, len(created_ids))
            return {
                "status": "completed",
                "storage_mode": "postgresql_rls",
                "agency_id": agency_id,
                "imported_count": len(created_ids),
                "skipped_duplicates": summary.skipped_duplicates,
                "rejected_rows": summary.rejected_rows,
                "warnings": summary.warnings,
                "created_contact_ids": created_ids,
            }

    target_crm = crm_service or CrmService()
    summary = import_contacts_from_csv(tenant, target_crm, csv_text=csv_text, default_country=country)
    logger.info("Async CSV import agency=%s imported=%s", agency_id, summary.imported_count)
    return {
        "status": "completed",
        "storage_mode": "in_memory_fallback",
        "agency_id": agency_id,
        "imported_count": summary.imported_count,
        "skipped_duplicates": summary.skipped_duplicates,
        "rejected_rows": summary.rejected_rows,
        "warnings": summary.warnings,
        "created_contact_ids": summary.created_contact_ids,
    }


async def run_rgpd_retention_sweep(
    ctx: dict[str, Any],
    *,
    agency_ids: list[str] | None = None,
    reference_date: date | None = None,
) -> dict[str, Any]:
    """Tâche planifiée Redis : anonymisation RGPD des contacts expirés + suppression des notes privées associées."""
    ref_today = reference_date or date.today()
    settings = load_settings()
    crm_service: CrmService | None = ctx.get("crm_service") if isinstance(ctx, dict) else None

    if settings.database_url and crm_service is None:
        from sqlalchemy import text
        from app.db.postgres_repositories import PostgresCrmRepository
        from app.db.session import get_session_factory

        sf = get_session_factory(settings.database_url)
        if sf is not None:
            repo = PostgresCrmRepository(sf)
            if agency_ids is None:
                async with sf() as session:
                    async with session.begin():
                        target_agencies = [
                            str(aid)
                            for aid in (await session.execute(text("SELECT id::text FROM agencies"))).scalars().all()
                        ]
            else:
                target_agencies = list(agency_ids)

            total_anonymized = 0
            for aid in target_agencies:
                system_ctx = TenantContext(
                    user_id="00000000-0000-0000-0000-000000000000",
                    agency_id=aid,
                    role=MontyRole.DIRECTOR,
                )
                total_anonymized += await repo.execute_rgpd_purge(system_ctx, today=ref_today)
            logger.info("PostgreSQL RGPD sweep completed agencies=%s anonymized=%s", len(target_agencies), total_anonymized)
            return {
                "status": "sweep_completed",
                "storage_mode": "postgresql_rls",
                "agencies_checked": len(target_agencies),
                "anonymized_count": total_anonymized,
                "checked_at": datetime.now(timezone.utc).isoformat(),
            }

    target_crm = crm_service or CrmService()
    discovered_agencies = agency_ids or sorted({c.agency_id for c in target_crm._contacts.values()})
    total_anonymized = 0
    for aid in discovered_agencies:
        system_ctx = TenantContext(user_id="rgpd-worker", agency_id=aid, role=MontyRole.DIRECTOR)
        total_anonymized += target_crm.execute_rgpd_purge(system_ctx, today=ref_today)

    logger.info("RGPD sweep completed agencies=%s anonymized=%s", len(discovered_agencies), total_anonymized)
    return {
        "status": "sweep_completed",
        "storage_mode": "in_memory_fallback",
        "agencies_checked": len(discovered_agencies),
        "anonymized_count": total_anonymized,
        "checked_at": datetime.now(timezone.utc).isoformat(),
    }


class WorkerSettings:
    functions = [run_csv_excel_import_job, run_rgpd_retention_sweep]
