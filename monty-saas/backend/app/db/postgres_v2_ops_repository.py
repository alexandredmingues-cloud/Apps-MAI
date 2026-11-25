from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from dataclasses import replace
from datetime import datetime, timezone
import hashlib
import json
from typing import Any, Callable
import urllib.request

from app.core.rbac import Permission, TenantContext
from app.core.rgpd import redact_direct_pii_for_ai
from app.core.security import InputSanitizer
from app.domain.ai_agents_service import (
    AGENTS_BY_ID,
    AgentExecutionLog,
    ScoutProposal,
)
from app.domain.authorized_feeds_service import (
    AuthorizedFeedSource,
    FeedFormat,
    FeedSyncResult,
    check_robots_txt_allowed,
    parse_json_listings,
    parse_xml_listings,
)
from app.domain.country_packs import CountryCode
from app.domain.geocoding_service import GeocodingResult, geocode_french_address
from app.domain.marketplace_service import PublisherType
from app.domain.real_estate_engines import DvfComparable


def _default_sql_text(sql: str) -> Any:
    from sqlalchemy import text

    return text(sql)


def _hash_phone(phone: str) -> str:
    return hashlib.sha256(phone.strip().encode("utf-8")).hexdigest()


def _mask_phone(phone: str) -> str:
    clean = phone.strip()
    return (clean[:4] + "***" + clean[-2:]) if len(clean) >= 6 else "***"


class PostgresV2OpsRepository:
    """
    Persistance PostgreSQL RLS pour les modules V2 :
    - Pré-État Daté L. 721-2 (`pre_etat_date_dossiers`)
    - Logs & Kill-Switch des 11 Agents IA (`agent_execution_logs`, `agent_governance_state`)
    - Propositions Monty Scout (`scout_proposals`)
    - Consentements / Opt-out WhatsApp (`whatsapp_consent_events`)
    - Cache DVF (`dvf_mutations_cache`)
    - Flux Partenaires Autorisés & Déduplication (`authorized_listing_feeds`, `authorized_feed_items`)
    """

    def __init__(
        self,
        session_factory: Any,
        *,
        tenant_session_cm: Callable[[Any, TenantContext], Any] | None = None,
        text_fn: Callable[[str], Any] | None = None,
    ) -> None:
        self._sf = session_factory
        self._tenant_session_cm = tenant_session_cm
        self._text = text_fn or _default_sql_text

    @asynccontextmanager
    async def _open_tenant_session(self, ctx: TenantContext):
        if self._tenant_session_cm is not None:
            async with self._tenant_session_cm(self._sf, ctx) as session:
                yield session
            return
        from app.db.session import tenant_db_session

        async with tenant_db_session(self._sf, ctx) as session:
            yield session

    @asynccontextmanager
    async def _open_system_session(self):
        async with self._sf() as session:
            async with session.begin():
                yield session

    # --- Pré-État Daté L. 721-2 ---
    async def save_pre_etat_dossier(
        self,
        ctx: TenantContext,
        *,
        dossier_ref: str,
        document_version: str,
        export_allowed: bool,
        payload_dict: dict[str, Any],
        result_dict: dict[str, Any],
    ) -> str:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO pre_etat_date_dossiers (
                          agency_id, dossier_ref, document_version, export_allowed, payload_json, result_json
                        ) VALUES (
                          CAST(:aid AS uuid), :dref, :ver, :allowed, CAST(:payload AS jsonb), CAST(:result AS jsonb)
                        )
                        RETURNING id::text
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "dref": InputSanitizer.sanitize_text(dossier_ref, 64),
                        "ver": InputSanitizer.sanitize_text(document_version, 32),
                        "allowed": bool(export_allowed),
                        "payload": json.dumps(payload_dict, default=str),
                        "result": json.dumps(result_dict, default=str),
                    },
                )
            ).first()
            return str(row[0]) if row else ""

    async def list_pre_etat_dossiers(self, ctx: TenantContext, limit: int = 20) -> list[dict[str, Any]]:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id::text, dossier_ref, document_version, export_allowed, result_json, created_at::text
                        FROM pre_etat_date_dossiers
                        ORDER BY created_at DESC
                        LIMIT :lim
                        """
                    ),
                    {"lim": max(1, min(100, limit))},
                )
            ).mappings().all()
            return [dict(r) for r in rows]

    # --- 11 Agents IA : Kill-Switch global, budget quotidien & logs ---
    async def get_kill_switch_state(self) -> tuple[bool, str]:
        async with self._open_system_session() as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        SELECT kill_switch_enabled, kill_switch_reason
                        FROM agent_governance_state
                        WHERE scope_key = 'global'
                        """
                    )
                )
            ).mappings().first()
            if row is None:
                return False, ""
            return bool(row["kill_switch_enabled"]), str(row["kill_switch_reason"] or "")

    async def set_kill_switch_state(
        self,
        ctx: TenantContext,
        *,
        enabled: bool,
        reason: str = "",
    ) -> dict[str, Any]:
        if not (ctx.has_permission(Permission.AGENCY_MANAGE) or ctx.has_permission(Permission.NEWS_ADMIN)):
            raise PermissionError("Seul un Directeur ou Administrateur peut activer/désactiver le kill-switch agents.")
        clean_reason = InputSanitizer.sanitize_text(reason, 200) if enabled else ""
        async with self._open_system_session() as session:
            await session.execute(
                self._text(
                    """
                    INSERT INTO agent_governance_state (
                      scope_key, kill_switch_enabled, kill_switch_reason, updated_by_user_id, updated_at
                    ) VALUES ('global', :enabled, :reason, :uid, now())
                    ON CONFLICT (scope_key) DO UPDATE SET
                      kill_switch_enabled = EXCLUDED.kill_switch_enabled,
                      kill_switch_reason = EXCLUDED.kill_switch_reason,
                      updated_by_user_id = EXCLUDED.updated_by_user_id,
                      updated_at = now()
                    """
                ),
                {"enabled": bool(enabled), "reason": clean_reason, "uid": ctx.user_id},
            )
        return {
            "kill_switch_enabled": bool(enabled),
            "reason": clean_reason,
            "updated_by": ctx.user_id,
        }

    async def get_daily_spend(self, ctx: TenantContext) -> float:
        async with self._open_tenant_session(ctx) as session:
            val = (
                await session.execute(
                    self._text(
                        """
                        SELECT COALESCE(SUM(cost_euro), 0.0)
                        FROM agent_execution_logs
                        WHERE agency_id = CAST(:aid AS uuid)
                          AND created_at >= date_trunc('day', now())
                        """
                    ),
                    {"aid": ctx.agency_id},
                )
            ).scalar()
            return round(float(val or 0.0), 4)

    async def record_agent_log(self, ctx: TenantContext, log: AgentExecutionLog) -> AgentExecutionLog:
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO agent_execution_logs (
                          agency_id, actor_user_id, agent_id, model_used,
                          sanitized_input, output_summary, duration_ms, cost_euro, cache_hit
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:uid AS uuid), :agent_id, :model_used,
                          :sanitized_input, :output_summary, :duration_ms, :cost_euro, :cache_hit
                        )
                        RETURNING id::text, created_at::text
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "agent_id": log.agent_id,
                        "model_used": log.model_used,
                        "sanitized_input": log.sanitized_input,
                        "output_summary": log.output_summary,
                        "duration_ms": int(log.duration_ms),
                        "cost_euro": float(log.cost_euro),
                        "cache_hit": bool(log.cache_hit),
                    },
                )
            ).mappings().first()
            if row is not None:
                return replace(log, id=str(row["id"]), created_at=str(row["created_at"]))
            return log

    async def list_agent_logs(self, ctx: TenantContext, limit: int = 30) -> list[AgentExecutionLog]:
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id::text, agency_id::text, actor_user_id::text, agent_id, model_used,
                               sanitized_input, output_summary, duration_ms, cost_euro, cache_hit,
                               created_at::text
                        FROM agent_execution_logs
                        ORDER BY created_at DESC
                        LIMIT :lim
                        """
                    ),
                    {"lim": max(1, min(100, limit))},
                )
            ).mappings().all()
            out: list[AgentExecutionLog] = []
            for r in rows:
                agent_def = AGENTS_BY_ID.get(r["agent_id"])
                out.append(
                    AgentExecutionLog(
                        id=str(r["id"]),
                        agency_id=str(r["agency_id"]),
                        actor_user_id=str(r["actor_user_id"]),
                        agent_id=str(r["agent_id"]),
                        agent_name=agent_def.name if agent_def else str(r["agent_id"]),
                        model_used=str(r["model_used"]),
                        sanitized_input=str(r["sanitized_input"]),
                        output_summary=str(r["output_summary"]),
                        duration_ms=int(r["duration_ms"] or 0),
                        cost_euro=float(r["cost_euro"] or 0.0),
                        cache_hit=bool(r["cache_hit"]),
                        created_at=str(r["created_at"]),
                    )
                )
            return out

    # --- Monty Scout : Propositions RGPD & Validation 1-clic ---
    @staticmethod
    def _row_to_scout(row: Any) -> ScoutProposal:
        return ScoutProposal(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]),
            created_by_user_id=str(row["created_by_user_id"]),
            target_contact_name=str(row["target_contact_name"]),
            channel=str(row["channel"]),
            legal_basis=str(row["legal_basis"]),
            rgpd_notice="Traitement encadré RGPD avec opposition immédiate par mot-clé STOP.",
            opt_out_keyword=str(row["opt_out_keyword"] or "STOP"),
            message_draft=str(row["message_draft"]),
            status=str(row["status"]),
            reviewed_by_user_id=str(row["reviewed_by_user_id"]) if row.get("reviewed_by_user_id") else None,
            created_at=str(row["created_at"]),
            reviewed_at=str(row["reviewed_at"]) if row.get("reviewed_at") else None,
        )

    async def list_scout_proposals(self, ctx: TenantContext) -> list[ScoutProposal]:
        ctx.require_permission(Permission.CRM_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id::text, agency_id::text, created_by_user_id::text, target_contact_name,
                               channel, legal_basis, opt_out_keyword, message_draft, status,
                               reviewed_by_user_id::text, created_at::text, reviewed_at::text
                        FROM scout_proposals
                        ORDER BY created_at DESC
                        LIMIT 100
                        """
                    )
                )
            ).mappings().all()
            return [self._row_to_scout(r) for r in rows]

    async def create_scout_proposal(
        self,
        ctx: TenantContext,
        *,
        target_contact_name: str,
        channel: str,
        legal_basis: str,
        message_draft: str,
    ) -> ScoutProposal:
        ctx.require_permission(Permission.CRM_WRITE)
        clean_name = InputSanitizer.sanitize_text(target_contact_name, 120)
        clean_draft = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(message_draft, 600))
        if not clean_name or len(clean_draft) < 10:
            raise ValueError("Nom du destinataire et brouillon (≥ 10 caractères) requis.")
        if "STOP" not in clean_draft.upper():
            clean_draft = f"{clean_draft} (Répondez STOP pour ne plus être contacté)"
        clean_channel = channel.upper() if channel.upper() in {"WHATSAPP", "EMAIL", "SMS"} else "WHATSAPP"
        clean_basis = InputSanitizer.sanitize_text(legal_basis or "INTERET_LEGITIME_PROSPECTION_B2C", 80)

        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO scout_proposals (
                          agency_id, created_by_user_id, target_contact_name,
                          channel, legal_basis, opt_out_keyword, message_draft, status
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:uid AS uuid), :target,
                          :channel, :basis, 'STOP', :draft, 'A_VALIDER'
                        )
                        RETURNING id::text, agency_id::text, created_by_user_id::text, target_contact_name,
                                  channel, legal_basis, opt_out_keyword, message_draft, status,
                                  reviewed_by_user_id::text, created_at::text, reviewed_at::text
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "target": clean_name,
                        "channel": clean_channel,
                        "basis": clean_basis,
                        "draft": clean_draft,
                    },
                )
            ).mappings().one()
            return self._row_to_scout(row)

    async def review_scout_proposal(
        self,
        ctx: TenantContext,
        *,
        proposal_id: str,
        approve: bool,
    ) -> ScoutProposal:
        ctx.require_permission(Permission.CRM_WRITE)
        new_status = "VALIDEE" if approve else "REJETEE"
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        UPDATE scout_proposals
                        SET status = :status,
                            reviewed_by_user_id = CAST(:uid AS uuid),
                            reviewed_at = now()
                        WHERE id = CAST(:pid AS uuid)
                        RETURNING id::text, agency_id::text, created_by_user_id::text, target_contact_name,
                                  channel, legal_basis, opt_out_keyword, message_draft, status,
                                  reviewed_by_user_id::text, created_at::text, reviewed_at::text
                        """
                    ),
                    {"pid": proposal_id, "status": new_status, "uid": ctx.user_id},
                )
            ).mappings().first()
            if row is None:
                raise ValueError("Proposition Monty Scout introuvable ou hors périmètre agence.")
            return self._row_to_scout(row)

    # --- WhatsApp Meta Cloud API : Consentements & Opt-Out persistants ---
    async def record_whatsapp_event(
        self,
        *,
        phone: str,
        direction: str,
        action: str,
        keyword_matched: str = "",
    ) -> str:
        async with self._open_system_session() as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO whatsapp_consent_events (
                          phone_masked, phone_hash, direction, action, keyword_matched
                        ) VALUES (
                          :masked, :phash, :direction, :action, :kw
                        )
                        RETURNING id::text
                        """
                    ),
                    {
                        "masked": _mask_phone(phone),
                        "phash": _hash_phone(phone),
                        "direction": direction,
                        "action": action,
                        "kw": InputSanitizer.sanitize_text(keyword_matched, 32),
                    },
                )
            ).first()
            return str(row[0]) if row else ""

    async def is_phone_opted_out(self, phone: str) -> bool:
        ph = _hash_phone(phone)
        async with self._open_system_session() as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        SELECT action
                        FROM whatsapp_consent_events
                        WHERE phone_hash = :ph
                          AND action IN ('OPT_OUT', 'OPT_IN')
                        ORDER BY created_at DESC
                        LIMIT 1
                        """
                    ),
                    {"ph": ph},
                )
            ).first()
            return bool(row is not None and str(row[0]) == "OPT_OUT")

    # --- Cache DVF ---
    async def upsert_dvf_comparables(self, sector_code: str, comparables: list[DvfComparable]) -> int:
        if not comparables:
            return 0
        async with self._open_system_session() as session:
            for comp in comparables:
                await session.execute(
                    self._text(
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
                        "sector_code": sector_code,
                        "date_mutation": comp.date_mutation,
                        "year": comp.year,
                        "price_euro": comp.price_euro,
                        "surface_m2": comp.surface_m2,
                        "property_type": comp.property_type,
                        "distance_meters": comp.distance_meters,
                    },
                )
        return len(comparables)

    async def list_dvf_cache_status(self) -> dict[str, Any]:
        async with self._open_system_session() as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT sector_code, COUNT(*)::int AS cnt, MAX(synced_at)::text AS last_sync
                        FROM dvf_mutations_cache
                        GROUP BY sector_code
                        ORDER BY sector_code ASC
                        """
                    )
                )
            ).mappings().all()
        sectors = [str(r["sector_code"]) for r in rows]
        total = sum(int(r["cnt"] or 0) for r in rows)
        by_sector = {str(r["sector_code"]): str(r["last_sync"] or "") for r in rows}
        return {
            "sectors_cached": sectors,
            "total_comparables_cached": total,
            "last_synced_at_by_sector": by_sector,
        }

    # --- Flux Partenaires Autorisés & Déduplication PostgreSQL ---
    @staticmethod
    def _row_to_feed(row: Any) -> AuthorizedFeedSource:
        return AuthorizedFeedSource(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]),
            name=str(row["name"]),
            country_code=CountryCode(str(row["country_code"])),
            publisher_type=PublisherType(str(row["publisher_type"])),
            feed_format=FeedFormat(str(row["feed_format"])),
            feed_url=str(row["feed_url"]),
            agreement_confirmed=bool(row["agreement_confirmed"]),
            respect_robots_txt=bool(row["respect_robots_txt"]),
            auto_publish_verified_agency=bool(row["auto_publish_verified_agency"]),
            last_synced_at=str(row["last_synced_at"]) if row.get("last_synced_at") else None,
            last_status=str(row["last_status"] or "READY"),
            total_ingested=int(row["total_ingested"] or 0),
            created_at=str(row["created_at"]),
        )

    async def list_feeds(self, ctx: TenantContext) -> list[AuthorizedFeedSource]:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id::text, agency_id::text, name, country_code, publisher_type,
                               feed_format, feed_url, agreement_confirmed, respect_robots_txt,
                               auto_publish_verified_agency, last_synced_at::text, last_status,
                               total_ingested, created_at::text
                        FROM authorized_listing_feeds
                        ORDER BY created_at DESC
                        """
                    )
                )
            ).mappings().all()
            return [self._row_to_feed(r) for r in rows]

    async def register_feed(
        self,
        ctx: TenantContext,
        *,
        name: str,
        country_code: CountryCode,
        publisher_type: PublisherType,
        feed_format: FeedFormat,
        feed_url: str,
        agreement_confirmed: bool,
        auto_publish_verified_agency: bool = False,
    ) -> AuthorizedFeedSource:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        if not agreement_confirmed:
            raise ValueError(
                "Accord explicite de diffusion & conformité RGPD obligatoire pour connecter un flux externe."
            )
        safe_url = InputSanitizer.sanitize_public_http_url(feed_url)
        if not safe_url:
            raise ValueError("URL de flux invalide ou bloquée par la protection SSRF (HTTPS/HTTP public requis).")

        clean_name = InputSanitizer.sanitize_text(name, 120)
        if len(clean_name) < 3:
            raise ValueError("Nom du flux partenaire requis (≥ 3 caractères).")

        auto_pub = bool(auto_publish_verified_agency and publisher_type == PublisherType.AGENCE)
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO authorized_listing_feeds (
                          agency_id, name, country_code, publisher_type, feed_format, feed_url,
                          agreement_confirmed, respect_robots_txt, auto_publish_verified_agency, last_status
                        ) VALUES (
                          CAST(:aid AS uuid), :name, :cc, :pub_type, :fmt, :url,
                          true, true, :auto_pub, 'READY'
                        )
                        RETURNING id::text, agency_id::text, name, country_code, publisher_type,
                                  feed_format, feed_url, agreement_confirmed, respect_robots_txt,
                                  auto_publish_verified_agency, last_synced_at::text, last_status,
                                  total_ingested, created_at::text
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "name": clean_name,
                        "cc": country_code.value,
                        "pub_type": publisher_type.value,
                        "fmt": feed_format.value,
                        "url": safe_url,
                        "auto_pub": auto_pub,
                    },
                )
            ).mappings().one()
            return self._row_to_feed(row)

    async def sync_feed_to_postgres(
        self,
        ctx: TenantContext,
        *,
        feed_id: str,
        inline_payload: str | None = None,
        fetcher: Callable[[str], str] | None = None,
        robots_fetcher: Callable[[str], str] | None = None,
        geocoder: Callable[[str, CountryCode, str], GeocodingResult] | None = None,
        publish_fn: Callable[..., Any] | None = None,
        moderate_fn: Callable[..., Any] | None = None,
    ) -> FeedSyncResult:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        async with self._open_tenant_session(ctx) as session:
            feed_row = (
                await session.execute(
                    self._text(
                        """
                        SELECT id::text, agency_id::text, name, country_code, publisher_type,
                               feed_format, feed_url, agreement_confirmed, respect_robots_txt,
                               auto_publish_verified_agency, last_synced_at::text, last_status,
                               total_ingested, created_at::text
                        FROM authorized_listing_feeds
                        WHERE id = CAST(:fid AS uuid)
                        """
                    ),
                    {"fid": feed_id},
                )
            ).mappings().first()
            if feed_row is None:
                raise ValueError("Flux partenaire introuvable ou hors périmètre agence.")
            feed = self._row_to_feed(feed_row)
            existing_ext_ids = {
                str(r[0])
                for r in (
                    await session.execute(
                        self._text(
                            """
                            SELECT external_id
                            FROM authorized_feed_items
                            WHERE feed_id = CAST(:fid AS uuid)
                            """
                        ),
                        {"fid": feed.id},
                    )
                ).all()
            }

        now_iso = datetime.now(timezone.utc).isoformat()
        if inline_payload is None:
            if feed.respect_robots_txt:
                robots_ok = await asyncio.to_thread(
                    check_robots_txt_allowed,
                    feed.feed_url,
                    robots_fetcher=robots_fetcher,
                )
                if not robots_ok:
                    await self._update_feed_sync_status(ctx, feed.id, "ROBOTS_BLOCKED", 0)
                    return FeedSyncResult(
                        feed_id=feed.id,
                        feed_name=feed.name,
                        status="ROBOTS_BLOCKED",
                        fetched_items=0,
                        accepted_count=0,
                        skipped_duplicates=0,
                        rejected_count=0,
                        created_listing_ids=[],
                        warnings=["Récupération annulée : le fichier robots.txt de la source interdit l'accès automatisé."],
                        synced_at=now_iso,
                    )

            try:
                if fetcher is not None:
                    raw_payload = fetcher(feed.feed_url)
                else:
                    def _fetch(url: str) -> str:
                        req = urllib.request.Request(url, headers={"User-Agent": "MontyImmo-PartnerFeedBot/2.0"})
                        with urllib.request.urlopen(req, timeout=12.0) as resp:
                            return resp.read().decode("utf-8", errors="replace")

                    raw_payload = await asyncio.to_thread(_fetch, feed.feed_url)
            except Exception as exc:
                await self._update_feed_sync_status(ctx, feed.id, "FETCH_ERROR", 0)
                return FeedSyncResult(
                    feed_id=feed.id,
                    feed_name=feed.name,
                    status="FETCH_ERROR",
                    fetched_items=0,
                    accepted_count=0,
                    skipped_duplicates=0,
                    rejected_count=0,
                    created_listing_ids=[],
                    warnings=[f"Source indisponible : {exc}"],
                    synced_at=now_iso,
                )
        else:
            raw_payload = inline_payload

        if feed.feed_format == FeedFormat.XML:
            raw_items = parse_xml_listings(raw_payload)
        else:
            raw_items = parse_json_listings(raw_payload)

        if publish_fn is None or moderate_fn is None:
            from app.db.postgres_repositories import PostgresMarketplaceRepository

            m_repo = PostgresMarketplaceRepository(self._sf)
            publish_fn = publish_fn or m_repo.publish_listing
            moderate_fn = moderate_fn or m_repo.moderate_listing

        accepted_ids: list[str] = []
        accepted_pairs: list[tuple[str, str]] = []
        skipped_dups = 0
        rejected = 0
        warnings: list[str] = []

        for idx, item in enumerate(raw_items):
            ext_id = str(item.get("external_id") or item.get("id") or f"row-{idx}").strip()[:160]
            if ext_id in existing_ext_ids:
                skipped_dups += 1
                continue

            try:
                title = InputSanitizer.sanitize_text(str(item.get("title") or item.get("name") or ""), 160)
                city = InputSanitizer.sanitize_text(str(item.get("city") or "Lyon"), 80)
                sector = InputSanitizer.sanitize_text(str(item.get("sector_label") or item.get("sector") or "Centre"), 80)
                prop_kind = InputSanitizer.sanitize_text(str(item.get("property_kind") or "Appartement"), 40)
                price = int(float(item.get("price_euro") or item.get("price") or 0))
                surface = int(float(item.get("surface_m2") or item.get("surface") or 0))
                rooms = max(1, int(float(item.get("rooms") or 2)))
                dpe = InputSanitizer.sanitize_text(str(item.get("dpe") or "C"), 4).upper()
                cond = InputSanitizer.sanitize_text(str(item.get("condition_label") or "Bon état"), 40)
                clean_desc = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(str(item.get("description") or ""), 1600))
                photo_count = max(0, int(float(item.get("photo_count") or 3)))
                canonical_url = InputSanitizer.sanitize_public_http_url(str(item.get("canonical_url") or item.get("url") or ""))

                if len(title) < 5 or price < 10_000 or surface < 9 or len(clean_desc) < 10:
                    rejected += 1
                    continue

                geo = (
                    geocoder(f"{sector} {city}".strip(), feed.country_code, city)
                    if geocoder is not None
                    else await asyncio.to_thread(
                        geocode_french_address,
                        f"{sector} {city}".strip(),
                        country_code=feed.country_code,
                        fallback_city=city,
                    )
                )
                attribution = f" [Source partenaire : {feed.name}" + (f" • {canonical_url}]" if canonical_url else "]")
                listing = await publish_fn(
                    ctx,
                    country_code=feed.country_code,
                    publisher_type=feed.publisher_type,
                    publisher_label=feed.name,
                    title=title,
                    property_kind=prop_kind,
                    city=city,
                    sector_label=sector,
                    price_euro=price,
                    surface_m2=surface,
                    rooms=rooms,
                    dpe=dpe,
                    condition_label=cond,
                    highlights=[f"Flux {feed.feed_format.value}", f"Réf {ext_id}"],
                    description=clean_desc + attribution,
                    photo_count=photo_count,
                    latitude=geo.latitude,
                    longitude=geo.longitude,
                    region_name=geo.region_name,
                )
                if feed.auto_publish_verified_agency and ctx.has_permission(Permission.PROPERTY_MODERATE):
                    await moderate_fn(
                        ctx,
                        listing_id=listing.id,
                        approve=True,
                        moderation_note="Publication auto flux agence partenaire vérifié",
                    )

                existing_ext_ids.add(ext_id)
                accepted_ids.append(listing.id)
                accepted_pairs.append((ext_id, listing.id))
            except Exception as exc:
                rejected += 1
                if len(warnings) < 5:
                    warnings.append(f"Ligne {idx + 1} ignorée : {exc}")

        if accepted_pairs:
            async with self._open_tenant_session(ctx) as session:
                for ext_id, prop_id in accepted_pairs:
                    await session.execute(
                        self._text(
                            """
                            INSERT INTO authorized_feed_items (feed_id, external_id, agency_id, property_id)
                            VALUES (CAST(:fid AS uuid), :ext_id, CAST(:aid AS uuid), CAST(:pid AS uuid))
                            ON CONFLICT (feed_id, external_id) DO NOTHING
                            """
                        ),
                        {"fid": feed.id, "ext_id": ext_id, "aid": ctx.agency_id, "pid": prop_id},
                    )
        await self._update_feed_sync_status(ctx, feed.id, "SYNCED", len(accepted_ids))

        return FeedSyncResult(
            feed_id=feed.id,
            feed_name=feed.name,
            status="SYNCED",
            fetched_items=len(raw_items),
            accepted_count=len(accepted_ids),
            skipped_duplicates=skipped_dups,
            rejected_count=rejected,
            created_listing_ids=accepted_ids,
            warnings=warnings,
            synced_at=now_iso,
        )

    async def _update_feed_sync_status(
        self,
        ctx: TenantContext,
        feed_id: str,
        status_label: str,
        added_count: int,
    ) -> None:
        async with self._open_tenant_session(ctx) as session:
            await session.execute(
                self._text(
                    """
                    UPDATE authorized_listing_feeds
                    SET last_synced_at = now(),
                        last_status = :st,
                        total_ingested = total_ingested + :added
                    WHERE id = CAST(:fid AS uuid)
                    """
                ),
                {"fid": feed_id, "st": status_label, "added": int(added_count)},
            )
