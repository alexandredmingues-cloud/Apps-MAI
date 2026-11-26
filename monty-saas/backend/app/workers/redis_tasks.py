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


async def run_dvf_sync_job(
    ctx: dict[str, Any],
    *,
    sector_codes: list[str] | None = None,
) -> dict[str, Any]:
    """Job planifié DVF : importe, valide et met en cache PostgreSQL + fallback si source indisponible."""
    from app.domain.dvf_sync_service import DvfSyncService

    settings = load_settings()
    dvf_service: DvfSyncService = (
        ctx.get("dvf_service") if isinstance(ctx, dict) and ctx.get("dvf_service") else DvfSyncService()
    )
    targets = sector_codes or ["69003", "34000", "33000", "75015"]
    reports = []
    for code in targets:
        rep = dvf_service.sync_sector_from_remote(
            sector_code=code,
            source_url=settings.dvf_source_url,
        )
        if settings.database_url and rep.status == "synced_live":
            from sqlalchemy import text
            from app.db.session import get_session_factory

            sf = get_session_factory(settings.database_url)
            if sf is not None:
                async with sf() as session:
                    async with session.begin():
                        for comp in dvf_service.get_cached_comparables(code):
                            await session.execute(
                                text(
                                    """
                                    INSERT INTO dvf_mutations_cache
                                      (mutation_id, sector_code, date_mutation, year, price_euro, surface_m2, property_type, distance_meters, synced_at)
                                    VALUES
                                      (:mutation_id, :sector_code, :date_mutation::date, :year, :price_euro, :surface_m2, :property_type, :distance_meters, now())
                                    ON CONFLICT (mutation_id) DO UPDATE SET
                                      price_euro = EXCLUDED.price_euro,
                                      surface_m2 = EXCLUDED.surface_m2,
                                      synced_at = now()
                                    """
                                ),
                                {
                                    "mutation_id": comp.mutation_id,
                                    "sector_code": code,
                                    "date_mutation": comp.date_mutation,
                                    "year": comp.year,
                                    "price_euro": comp.price_euro,
                                    "surface_m2": comp.surface_m2,
                                    "property_type": comp.property_type,
                                    "distance_meters": comp.distance_meters,
                                },
                            )
        reports.append(rep.__dict__)

    return {
        "status": "dvf_sync_completed",
        "sectors_processed": len(reports),
        "reports": reports,
    }


async def run_authorized_feeds_sync_job(
    ctx: dict[str, Any],
    *,
    agency_ids: list[str] | None = None,
) -> dict[str, Any]:
    """Job planifié ARQ : synchronise les flux partenaires autorisés (XML/JSON/JSON-LD) avec respect robots.txt & déduplication."""
    settings = load_settings()
    feeds_service = ctx.get("feeds_service") if isinstance(ctx, dict) else None
    fetcher = ctx.get("fetcher") if isinstance(ctx, dict) else None
    robots_fetcher = ctx.get("robots_fetcher") if isinstance(ctx, dict) else None

    if settings.database_url and feeds_service is None:
        from sqlalchemy import text
        from app.db.postgres_v2_ops_repository import PostgresV2OpsRepository
        from app.db.session import get_session_factory

        sf = get_session_factory(settings.database_url)
        if sf is not None:
            repo = PostgresV2OpsRepository(sf)
            async with sf() as session:
                async with session.begin():
                    if agency_ids is None:
                        target_agencies = [
                            str(aid)
                            for aid in (await session.execute(text("SELECT id::text FROM agencies"))).scalars().all()
                        ]
                    else:
                        target_agencies = list(agency_ids)

            reports: list[dict[str, Any]] = []
            for aid in target_agencies:
                async with sf() as session:
                    async with session.begin():
                        actor_row = (
                            await session.execute(
                                text(
                                    """
                                    SELECT user_id::text, role
                                    FROM agency_memberships
                                    WHERE agency_id = CAST(:aid AS uuid) AND status = 'ACTIVE'
                                    ORDER BY CASE role
                                      WHEN 'DIRECTOR' THEN 1
                                      WHEN 'ADMIN' THEN 2
                                      WHEN 'AGENT' THEN 3
                                      ELSE 4
                                    END
                                    LIMIT 1
                                    """
                                ),
                                {"aid": aid},
                            )
                        ).mappings().first()
                if actor_row is None:
                    continue
                tenant = TenantContext(
                    user_id=str(actor_row["user_id"]),
                    agency_id=aid,
                    role=MontyRole(str(actor_row["role"])),
                )
                feeds = await repo.list_feeds(tenant)
                for feed in feeds:
                    if not feed.agreement_confirmed or ".example/" in feed.feed_url:
                        continue
                    res = await repo.sync_feed_to_postgres(
                        tenant,
                        feed_id=feed.id,
                        fetcher=fetcher,
                        robots_fetcher=robots_fetcher,
                    )
                    reports.append(res.__dict__)
            logger.info("PostgreSQL authorized feeds sync completed feeds_synced=%s", len(reports))
            return {
                "status": "authorized_feeds_sync_completed",
                "storage_mode": "postgresql_rls",
                "feeds_processed": len(reports),
                "reports": reports,
            }

    if feeds_service is None:
        from app.domain.authorized_feeds_service import AuthorizedListingFeedsService
        from app.domain.marketplace_service import MarketplaceService

        feeds_service = AuthorizedListingFeedsService(MarketplaceService())

    target_agencies = agency_ids or sorted({f.agency_id for f in feeds_service._feeds.values()})
    reports = []
    for aid in target_agencies:
        system_ctx = TenantContext(user_id="feeds-worker", agency_id=aid, role=MontyRole.DIRECTOR)
        for feed in feeds_service.list_feeds(system_ctx):
            if not feed.agreement_confirmed or ".example/" in feed.feed_url:
                continue
            res = feeds_service.sync_feed(
                system_ctx,
                feed_id=feed.id,
                fetcher=fetcher,
                robots_fetcher=robots_fetcher,
            )
            reports.append(res.__dict__)

    logger.info("Authorized feeds sync completed feeds_synced=%s", len(reports))
    return {
        "status": "authorized_feeds_sync_completed",
        "storage_mode": "in_memory_fallback",
        "feeds_processed": len(reports),
        "reports": reports,
    }


try:
    import os as _os
    from arq import cron as _arq_cron
    from arq.connections import RedisSettings as _ArqRedisSettings

    _CRON_JOBS = [
        _arq_cron(run_rgpd_retention_sweep, hour=2, minute=0),
        _arq_cron(run_dvf_sync_job, hour=3, minute=0),
        _arq_cron(run_authorized_feeds_sync_job, hour=4, minute=0),
    ]
    _REDIS_SETTINGS = _ArqRedisSettings.from_dsn(_os.environ.get("REDIS_URL") or "redis://localhost:6379/0")
except ImportError:
    _CRON_JOBS = []
    _REDIS_SETTINGS = None


class WorkerSettings:
    functions = [
        run_csv_excel_import_job,
        run_rgpd_retention_sweep,
        run_dvf_sync_job,
        run_authorized_feeds_sync_job,
    ]
    cron_jobs = _CRON_JOBS
    if _REDIS_SETTINGS is not None:
        redis_settings = _REDIS_SETTINGS

