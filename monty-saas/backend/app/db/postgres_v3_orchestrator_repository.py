from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta, timezone
import json
from typing import Any, Callable
import uuid

from app.core.rbac import MontyRole, Permission, TenantContext
from app.core.rgpd import redact_direct_pii_for_ai
from app.core.security import InputSanitizer
from app.domain.interconnections_service import DEFAULT_MODULE_FLAGS, ModuleFeatureFlag
from app.domain.mobile_releases_service import MobileAppRelease
from app.domain.monty_ai_core import (
    AIConversationTurn,
    AIMemoryEntry,
    AIPendingAction,
    SensitiveActionType,
)
from app.domain.monty_price_index_service import PriceIndexConfig, PriceIndexHistoryRecord
from app.domain.portfolio_and_leads_service import LeadKeywordRule


def _default_sql_text(sql: str) -> Any:
    from sqlalchemy import text

    return text(sql)


def _iso(val: Any) -> str:
    if val is None:
        return ""
    if hasattr(val, "isoformat"):
        return val.isoformat()
    return str(val)


def is_valid_uuid(raw: str) -> bool:
    try:
        uuid.UUID(str(raw))
        return True
    except Exception:
        return False


class PostgresV3OrchestratorRepository:
    """
    Persistance PostgreSQL 16 + RLS (Migrations 0010, 0011, 0012) pour :
    - Monty AI Core (moteurs, mémoires RGPD, conversations, actions humaines, audit)
    - Distribution APK Mobile (`mobile_app_releases`)
    - Feature Flags modules (`agency_module_flags`)
    - Règles mots-clés Monty Leads (`lead_keyword_rules`)
    - Monty Price Index (`monty_price_index_configs`, `monty_price_index_history`)
    - Commandes Pack Vendeur vérifiées Stripe (`seller_pack_orders`)
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
    async def _open_global_session(self):
        async with self._sf() as session:
            async with session.begin():
                yield session

    # ==========================================================================
    # 1. MONTY AI CORE (ENGINES, MEMORIES, CONVERSATIONS, ACTIONS, METRICS)
    # ==========================================================================

    async def list_engine_configs(self) -> list[dict[str, Any]]:
        async with self._open_global_session() as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT engine_id, provider, model_name, tier, is_local,
                               confidential_safe, enabled, is_default, is_fallback,
                               priority, min_ram_gb, masked_api_key, updated_by, updated_at
                        FROM monty_ai_engine_configs
                        ORDER BY priority ASC
                        """
                    )
                )
            ).mappings().all()
            return [dict(r) for r in rows]

    async def upsert_engine_config(
        self,
        ctx: TenantContext,
        *,
        engine_id: str,
        provider: str,
        model_name: str,
        tier: str,
        is_local: bool,
        confidential_safe: bool,
        enabled: bool,
        is_default: bool,
        is_fallback: bool,
        priority: int,
        min_ram_gb: int,
        masked_api_key: str,
    ) -> None:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        async with self._open_global_session() as session:
            if is_default:
                await session.execute(self._text("UPDATE monty_ai_engine_configs SET is_default = false"))
            await session.execute(
                self._text(
                    """
                    INSERT INTO monty_ai_engine_configs (
                        engine_id, provider, model_name, tier, is_local, confidential_safe,
                        enabled, is_default, is_fallback, priority, min_ram_gb,
                        masked_api_key, updated_by, updated_at
                    )
                    VALUES (
                        :eid, :provider, :model_name, :tier, :is_local, :confidential_safe,
                        :enabled, :is_default, :is_fallback, :priority, :min_ram_gb,
                        :masked_api_key, :updated_by, now()
                    )
                    ON CONFLICT (engine_id) DO UPDATE SET
                        model_name = EXCLUDED.model_name,
                        enabled = EXCLUDED.enabled,
                        is_default = EXCLUDED.is_default,
                        is_fallback = EXCLUDED.is_fallback,
                        priority = EXCLUDED.priority,
                        masked_api_key = CASE WHEN EXCLUDED.masked_api_key <> '' THEN EXCLUDED.masked_api_key ELSE monty_ai_engine_configs.masked_api_key END,
                        updated_by = EXCLUDED.updated_by,
                        updated_at = now()
                    """
                ),
                {
                    "eid": engine_id,
                    "provider": provider,
                    "model_name": model_name,
                    "tier": tier,
                    "is_local": is_local,
                    "confidential_safe": confidential_safe,
                    "enabled": enabled,
                    "is_default": is_default,
                    "is_fallback": is_fallback,
                    "priority": priority,
                    "min_ram_gb": min_ram_gb,
                    "masked_api_key": masked_api_key,
                    "updated_by": ctx.user_id,
                },
            )

    async def list_memories(self, ctx: TenantContext) -> list[AIMemoryEntry]:
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, user_id, scope, title, content,
                               source_ref, retention_until, created_at, updated_at
                        FROM monty_ai_memories
                        WHERE scope = 'AGENCY' OR user_id = :uid
                        ORDER BY updated_at DESC
                        LIMIT 100
                        """
                    ),
                    {"uid": ctx.user_id},
                )
            ).mappings().all()
            return [
                AIMemoryEntry(
                    id=str(r["id"]),
                    agency_id=str(r["agency_id"]),
                    user_id=str(r["user_id"]),
                    scope=str(r["scope"]),
                    title=str(r["title"]),
                    content=str(r["content"]),
                    source_ref=str(r.get("source_ref") or ""),
                    retention_until=_iso(r["retention_until"]),
                    created_at=_iso(r["created_at"]),
                    updated_at=_iso(r["updated_at"]),
                )
                for r in rows
            ]

    async def upsert_memory(
        self,
        ctx: TenantContext,
        *,
        memory_id: str | None = None,
        scope: str = "USER",
        title: str,
        content: str,
        source_ref: str = "manual",
    ) -> AIMemoryEntry:
        clean_title = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(title, 180))
        clean_content = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(content, 2000))
        if not clean_title or not clean_content:
            raise ValueError("Titre et contenu mémoire obligatoires.")
        norm_scope = "AGENCY" if scope.upper() == "AGENCY" and ctx.has_permission(Permission.PROPERTY_READ) else "USER"
        ret_date = date.today() + timedelta(days=365)

        async with self._open_tenant_session(ctx) as session:
            if memory_id and is_valid_uuid(memory_id):
                existing = (
                    await session.execute(
                        self._text("SELECT id, user_id, scope FROM monty_ai_memories WHERE id = CAST(:mid AS uuid)"),
                        {"mid": memory_id},
                    )
                ).mappings().first()
                if existing is not None:
                    if str(existing["scope"]) == "USER" and str(existing["user_id"]) != ctx.user_id:
                        raise PermissionError("Mémoire personnelle appartenant à un autre utilisateur.")
                    row = (
                        await session.execute(
                            self._text(
                                """
                                UPDATE monty_ai_memories
                                SET scope = :scope, title = :title, content = :content,
                                    source_ref = :source_ref, updated_at = now()
                                WHERE id = CAST(:mid AS uuid)
                                RETURNING id, agency_id, user_id, scope, title, content,
                                          source_ref, retention_until, created_at, updated_at
                                """
                            ),
                            {
                                "mid": memory_id,
                                "scope": norm_scope,
                                "title": clean_title,
                                "content": clean_content,
                                "source_ref": InputSanitizer.sanitize_text(source_ref, 120),
                            },
                        )
                    ).mappings().one()
                    return AIMemoryEntry(
                        id=str(row["id"]),
                        agency_id=str(row["agency_id"]),
                        user_id=str(row["user_id"]),
                        scope=str(row["scope"]),
                        title=str(row["title"]),
                        content=str(row["content"]),
                        source_ref=str(row.get("source_ref") or ""),
                        retention_until=_iso(row["retention_until"]),
                        created_at=_iso(row["created_at"]),
                        updated_at=_iso(row["updated_at"]),
                    )

            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO monty_ai_memories (
                            agency_id, user_id, scope, title, content, source_ref, retention_until
                        )
                        VALUES (
                            CAST(:aid AS uuid), :uid, :scope, :title, :content, :source_ref, :ret
                        )
                        RETURNING id, agency_id, user_id, scope, title, content,
                                  source_ref, retention_until, created_at, updated_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "scope": norm_scope,
                        "title": clean_title,
                        "content": clean_content,
                        "source_ref": InputSanitizer.sanitize_text(source_ref, 120),
                        "ret": ret_date,
                    },
                )
            ).mappings().one()
            return AIMemoryEntry(
                id=str(row["id"]),
                agency_id=str(row["agency_id"]),
                user_id=str(row["user_id"]),
                scope=str(row["scope"]),
                title=str(row["title"]),
                content=str(row["content"]),
                source_ref=str(row.get("source_ref") or ""),
                retention_until=_iso(row["retention_until"]),
                created_at=_iso(row["created_at"]),
                updated_at=_iso(row["updated_at"]),
            )

    async def delete_memory(self, ctx: TenantContext, memory_id: str) -> bool:
        if not is_valid_uuid(memory_id):
            return False
        async with self._open_tenant_session(ctx) as session:
            existing = (
                await session.execute(
                    self._text("SELECT id, user_id, scope FROM monty_ai_memories WHERE id = CAST(:mid AS uuid)"),
                    {"mid": memory_id},
                )
            ).mappings().first()
            if existing is None:
                return False
            if str(existing["scope"]) == "USER" and str(existing["user_id"]) != ctx.user_id and not ctx.has_permission(Permission.AGENCY_MANAGE):
                raise PermissionError("Suppression interdite sur la mémoire d'un autre utilisateur.")
            await session.execute(
                self._text("DELETE FROM monty_ai_memories WHERE id = CAST(:mid AS uuid)"),
                {"mid": memory_id},
            )
            return True

    async def record_chat_turn(
        self,
        ctx: TenantContext,
        *,
        task_type: str,
        engine_used: str,
        execution_mode: str,
        sanitized_prompt: str,
        response_text: str,
        sources: list[dict[str, str]],
        latency_ms: int,
        estimated_cost_euro: float,
        audit_status: str,
        pending_action: dict[str, Any] | None = None,
    ) -> dict[str, str]:
        conv_id = str(uuid.uuid4())
        action_id = ""
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO monty_ai_conversations (
                            id, agency_id, user_id, task_type, engine_used, execution_mode,
                            sanitized_prompt, response_text, sources_json
                        )
                        VALUES (
                            CAST(:cid AS uuid), CAST(:aid AS uuid), :uid, :task_type, :engine_used,
                            :execution_mode, :prompt, :resp, CAST(:sources AS jsonb)
                        )
                        RETURNING id
                        """
                    ),
                    {
                        "cid": conv_id,
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "task_type": task_type,
                        "engine_used": engine_used,
                        "execution_mode": execution_mode,
                        "prompt": sanitized_prompt,
                        "resp": response_text,
                        "sources": json.dumps(sources),
                    },
                )
            ).mappings().first()
            if row:
                conv_id = str(row["id"])

            await session.execute(
                self._text(
                    """
                    INSERT INTO monty_ai_audit_events (
                        agency_id, user_id, engine_id, task_type, status, latency_ms, estimated_cost_euro
                    )
                    VALUES (
                        CAST(:aid AS uuid), :uid, :engine_id, :task_type, :status, :latency_ms, :cost
                    )
                    """
                ),
                {
                    "aid": ctx.agency_id,
                    "uid": ctx.user_id,
                    "engine_id": engine_used,
                    "task_type": task_type,
                    "status": audit_status,
                    "latency_ms": max(1, int(latency_ms)),
                    "cost": float(estimated_cost_euro),
                },
            )

            if pending_action is not None:
                action_id = str(uuid.uuid4())
                arow = (
                    await session.execute(
                        self._text(
                            """
                            INSERT INTO monty_ai_pending_actions (
                                id, agency_id, requested_by_user_id, action_type, title, summary, payload_json, status
                            )
                            VALUES (
                                CAST(:act_id AS uuid), CAST(:aid AS uuid), :uid, :atype, :title, :summary, CAST(:payload AS jsonb), 'A_VALIDER'
                            )
                            RETURNING id
                            """
                        ),
                        {
                            "act_id": action_id,
                            "aid": ctx.agency_id,
                            "uid": ctx.user_id,
                            "atype": str(pending_action.get("action_type", "SEND_CLIENT_MESSAGE")),
                            "title": str(pending_action.get("title", "Action Monty AI")),
                            "summary": str(pending_action.get("summary", "")),
                            "payload": json.dumps({"draft": response_text[:800], "task_type": task_type}),
                        },
                    )
                ).mappings().first()
                if arow:
                    action_id = str(arow["id"])

        return {"conversation_id": conv_id, "pending_action_id": action_id}

    async def list_conversations(self, ctx: TenantContext, limit: int = 25) -> list[AIConversationTurn]:
        can_manage = ctx.has_permission(Permission.AGENCY_MANAGE)
        async with self._open_tenant_session(ctx) as session:
            sql = """
                SELECT id, agency_id, user_id, task_type, engine_used, execution_mode,
                       sanitized_prompt, response_text, sources_json, created_at
                FROM monty_ai_conversations
            """
            if not can_manage:
                sql += " WHERE user_id = :uid"
            sql += " ORDER BY created_at DESC LIMIT :lim"
            rows = (
                await session.execute(self._text(sql), {"uid": ctx.user_id, "lim": limit})
            ).mappings().all()
            return [
                AIConversationTurn(
                    id=str(r["id"]),
                    agency_id=str(r["agency_id"]),
                    user_id=str(r["user_id"]),
                    task_type=str(r["task_type"]),
                    engine_used=str(r["engine_used"]),
                    execution_mode=str(r["execution_mode"]),
                    sanitized_prompt=str(r["sanitized_prompt"]),
                    response_text=str(r["response_text"]),
                    sources=list(r["sources_json"] or []) if isinstance(r["sources_json"], list) else [],
                    tools_invoked=[],
                    latency_ms=0,
                    estimated_cost_euro=0.0,
                    created_at=_iso(r["created_at"]),
                )
                for r in rows
            ]

    async def list_pending_actions(self, ctx: TenantContext) -> list[AIPendingAction]:
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, requested_by_user_id, action_type, title,
                               summary, payload_json, status, reviewed_by_user_id,
                               created_at, reviewed_at
                        FROM monty_ai_pending_actions
                        ORDER BY created_at DESC
                        LIMIT 100
                        """
                    )
                )
            ).mappings().all()
            out: list[AIPendingAction] = []
            for r in rows:
                try:
                    atype = SensitiveActionType(str(r["action_type"]))
                except Exception:
                    atype = SensitiveActionType.SEND_CLIENT_MESSAGE
                out.append(
                    AIPendingAction(
                        id=str(r["id"]),
                        agency_id=str(r["agency_id"]),
                        requested_by_user_id=str(r["requested_by_user_id"]),
                        action_type=atype,
                        title=str(r["title"]),
                        summary=str(r["summary"]),
                        payload=dict(r["payload_json"] or {}) if isinstance(r["payload_json"], dict) else {},
                        status=str(r["status"]),
                        reviewed_by_user_id=str(r["reviewed_by_user_id"]) if r.get("reviewed_by_user_id") else None,
                        created_at=_iso(r["created_at"]),
                        reviewed_at=_iso(r.get("reviewed_at")) or None,
                    )
                )
            return out

    async def decide_pending_action(self, ctx: TenantContext, action_id: str, approve: bool) -> AIPendingAction:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        if not is_valid_uuid(action_id):
            raise ValueError("Action en attente introuvable.")
        new_status = "VALIDEE" if approve else "REJETEE"
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        UPDATE monty_ai_pending_actions
                        SET status = :st, reviewed_by_user_id = :uid, reviewed_at = now()
                        WHERE id = CAST(:act_id AS uuid)
                        RETURNING id, agency_id, requested_by_user_id, action_type, title,
                                  summary, payload_json, status, reviewed_by_user_id,
                                  created_at, reviewed_at
                        """
                    ),
                    {"st": new_status, "uid": ctx.user_id, "act_id": action_id},
                )
            ).mappings().first()
            if row is None:
                raise ValueError("Action en attente introuvable.")
            try:
                atype = SensitiveActionType(str(row["action_type"]))
            except Exception:
                atype = SensitiveActionType.SEND_CLIENT_MESSAGE
            return AIPendingAction(
                id=str(row["id"]),
                agency_id=str(row["agency_id"]),
                requested_by_user_id=str(row["requested_by_user_id"]),
                action_type=atype,
                title=str(row["title"]),
                summary=str(row["summary"]),
                payload=dict(row["payload_json"] or {}) if isinstance(row["payload_json"], dict) else {},
                status=str(row["status"]),
                reviewed_by_user_id=str(row["reviewed_by_user_id"]) if row.get("reviewed_by_user_id") else None,
                created_at=_iso(row["created_at"]),
                reviewed_at=_iso(row.get("reviewed_at")) or None,
            )

    async def get_ai_metrics_summary(self, ctx: TenantContext) -> dict[str, Any]:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, engine_id, task_type, status, latency_ms, estimated_cost_euro, created_at
                        FROM monty_ai_audit_events
                        ORDER BY created_at DESC
                        LIMIT 200
                        """
                    )
                )
            ).mappings().all()
        total = len(rows)
        avg_lat = round(sum(int(r["latency_ms"]) for r in rows) / total, 1) if total else 0.0
        fallbacks = sum(1 for r in rows if str(r["status"]) in ("FALLBACK", "OFFLINE_TOOLS"))
        cache_hits = sum(1 for r in rows if str(r["status"]) == "CACHE_HIT")
        by_engine: dict[str, int] = {}
        provider_stats: dict[str, dict[str, Any]] = {}
        daily_spend = 0.0
        today_prefix = date.today().isoformat()
        for r in rows:
            eid = str(r["engine_id"])
            cost = float(r.get("estimated_cost_euro") or 0.0)
            lat = int(r["latency_ms"])
            by_engine[eid] = by_engine.get(eid, 0) + 1
            st = provider_stats.setdefault(
                eid, {"engine_id": eid, "requests": 0, "total_latency_ms": 0, "total_cost_euro": 0.0}
            )
            st["requests"] += 1
            st["total_latency_ms"] += lat
            st["total_cost_euro"] = round(st["total_cost_euro"] + cost, 6)
            if _iso(r["created_at"]).startswith(today_prefix):
                daily_spend += cost
        return {
            "total_requests": total,
            "avg_latency_ms": avg_lat,
            "fallback_count": fallbacks,
            "fallback_rate_percent": round((fallbacks / total) * 100.0, 1) if total else 0.0,
            "cache_hits": cache_hits,
            "daily_spend_euro": round(daily_spend, 4),
            "requests_by_engine": by_engine,
            "provider_comparison": [
                {
                    "engine_id": eid,
                    "requests": s["requests"],
                    "avg_latency_ms": round(s["total_latency_ms"] / max(1, s["requests"]), 1),
                    "total_cost_euro": s["total_cost_euro"],
                }
                for eid, s in sorted(provider_stats.items(), key=lambda kv: kv[1]["requests"], reverse=True)
            ],
            "recent_events": [
                {
                    "id": str(r["id"]),
                    "engine_id": str(r["engine_id"]),
                    "task_type": str(r["task_type"]),
                    "status": str(r["status"]),
                    "latency_ms": int(r["latency_ms"]),
                    "estimated_cost_euro": float(r.get("estimated_cost_euro") or 0.0),
                    "created_at": _iso(r["created_at"]),
                }
                for r in rows[:30]
            ],
        }

    # ==========================================================================
    # 2. MOBILE APP RELEASES (APK DISTRIBUTION)
    # ==========================================================================

    @staticmethod
    def _row_to_release(r: Any) -> MobileAppRelease:
        sdk = int(r["min_android_sdk"])
        return MobileAppRelease(
            id=str(r["id"]),
            version_name=str(r["version_name"]),
            version_code=int(r["version_code"]),
            package_id=str(r["package_id"]),
            min_android_sdk=sdk,
            min_android_label=f"Android {max(7, sdk - 17)}.0 (API {sdk}) minimum",
            sha256=str(r["sha256"]),
            file_size_bytes=int(r["file_size_bytes"]),
            file_name=str(r["file_name"]),
            storage_path=str(r["storage_path"]),
            release_notes=str(r.get("release_notes") or ""),
            signature_verified=bool(r["signature_verified"]),
            is_published=bool(r["is_published"]),
            is_recommended=bool(r["is_recommended"]),
            download_count=int(r["download_count"]),
            published_by=str(r["published_by"]),
            created_at=_iso(r["created_at"]),
            updated_at=_iso(r["updated_at"]),
        )

    async def upsert_mobile_release(self, rel: MobileAppRelease) -> MobileAppRelease:
        async with self._open_global_session() as session:
            if rel.is_recommended:
                await session.execute(
                    self._text("UPDATE mobile_app_releases SET is_recommended = false WHERE id <> :rid"),
                    {"rid": rel.id},
                )
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO mobile_app_releases (
                            id, version_name, version_code, package_id, min_android_sdk,
                            sha256, file_size_bytes, file_name, storage_path, release_notes,
                            signature_verified, is_published, is_recommended, download_count, published_by
                        )
                        VALUES (
                            :id, :vname, :vcode, :pkg, :sdk, :sha, :fsize, :fname, :spath, :notes,
                            :sig_ok, :pub, :rec, :dcount, :pub_by
                        )
                        ON CONFLICT (id) DO UPDATE SET
                            version_name = EXCLUDED.version_name,
                            version_code = EXCLUDED.version_code,
                            sha256 = EXCLUDED.sha256,
                            file_size_bytes = EXCLUDED.file_size_bytes,
                            file_name = EXCLUDED.file_name,
                            storage_path = EXCLUDED.storage_path,
                            release_notes = EXCLUDED.release_notes,
                            signature_verified = EXCLUDED.signature_verified,
                            is_published = EXCLUDED.is_published,
                            is_recommended = EXCLUDED.is_recommended,
                            updated_at = now()
                        RETURNING *
                        """
                    ),
                    {
                        "id": rel.id,
                        "vname": rel.version_name,
                        "vcode": rel.version_code,
                        "pkg": rel.package_id,
                        "sdk": rel.min_android_sdk,
                        "sha": rel.sha256,
                        "fsize": rel.file_size_bytes,
                        "fname": rel.file_name,
                        "spath": rel.storage_path,
                        "notes": rel.release_notes,
                        "sig_ok": rel.signature_verified,
                        "pub": rel.is_published,
                        "rec": rel.is_recommended,
                        "dcount": rel.download_count,
                        "pub_by": rel.published_by,
                    },
                )
            ).mappings().one()
            return self._row_to_release(row)

    async def list_mobile_releases(self, *, published_only: bool = True) -> list[MobileAppRelease]:
        async with self._open_global_session() as session:
            sql = "SELECT * FROM mobile_app_releases"
            if published_only:
                sql += " WHERE is_published = true AND signature_verified = true"
            sql += " ORDER BY is_recommended DESC, version_code DESC"
            rows = (await session.execute(self._text(sql))).mappings().all()
            return [self._row_to_release(r) for r in rows]

    async def increment_mobile_download(self, release_id: str) -> None:
        async with self._open_global_session() as session:
            await session.execute(
                self._text(
                    "UPDATE mobile_app_releases SET download_count = download_count + 1, updated_at = now() WHERE id = :rid"
                ),
                {"rid": release_id},
            )

    # ==========================================================================
    # 3. AGENCY MODULE FLAGS & LEAD KEYWORD RULES
    # ==========================================================================

    async def list_module_flags(self, agency_id: str, ctx: TenantContext | None = None) -> list[ModuleFeatureFlag]:
        now_iso = datetime.now(timezone.utc).isoformat()
        defaults = {
            key: ModuleFeatureFlag(
                module_key=key,
                label=label,
                category=cat,
                status=st,
                visible=vis,
                critical=crit,
                updated_by="system",
                updated_at=now_iso,
            )
            for key, label, cat, st, vis, crit in DEFAULT_MODULE_FLAGS
        }
        if not is_valid_uuid(agency_id):
            return list(defaults.values())
        read_ctx = ctx or TenantContext(user_id="public-reader", agency_id=agency_id, role=MontyRole.BUYER)
        async with self._open_tenant_session(read_ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT module_key, status, visible, updated_by, updated_at
                        FROM agency_module_flags
                        WHERE agency_id = CAST(:aid AS uuid)
                        """
                    ),
                    {"aid": agency_id},
                )
            ).mappings().all()
            for r in rows:
                mk = str(r["module_key"])
                if mk in defaults:
                    defaults[mk].status = str(r["status"])
                    defaults[mk].visible = bool(r["visible"])
                    defaults[mk].updated_by = str(r["updated_by"])
                    defaults[mk].updated_at = _iso(r["updated_at"])
        return list(defaults.values())

    async def upsert_module_flag(
        self,
        ctx: TenantContext,
        *,
        module_key: str,
        status: str,
        visible: bool,
    ) -> ModuleFeatureFlag:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        meta = {key: (label, cat, crit) for key, label, cat, _st, _vis, crit in DEFAULT_MODULE_FLAGS}.get(module_key)
        if meta is None:
            raise ValueError("Module inconnu.")
        label, cat, crit = meta
        norm_status = status.strip().upper()
        if norm_status not in ("EN_DEVELOPPEMENT", "EN_TEST", "DISPONIBLE"):
            raise ValueError("Statut module invalide (EN_DEVELOPPEMENT, EN_TEST, DISPONIBLE).")
        if crit and not visible:
            raise ValueError("Les modules critiques de sécurité et d'administration ne peuvent pas être masqués.")
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO agency_module_flags (agency_id, module_key, status, visible, updated_by, updated_at)
                        VALUES (CAST(:aid AS uuid), :mk, :st, :vis, :uid, now())
                        ON CONFLICT (agency_id, module_key) DO UPDATE SET
                            status = EXCLUDED.status,
                            visible = EXCLUDED.visible,
                            updated_by = EXCLUDED.updated_by,
                            updated_at = now()
                        RETURNING module_key, status, visible, updated_by, updated_at
                        """
                    ),
                    {"aid": ctx.agency_id, "mk": module_key, "st": norm_status, "vis": visible, "uid": ctx.user_id},
                )
            ).mappings().one()
            return ModuleFeatureFlag(
                module_key=str(row["module_key"]),
                label=label,
                category=cat,
                status=str(row["status"]),
                visible=bool(row["visible"]),
                critical=crit,
                updated_by=str(row["updated_by"]),
                updated_at=_iso(row["updated_at"]),
            )

    async def restore_default_module_flags(self, ctx: TenantContext) -> list[ModuleFeatureFlag]:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        async with self._open_tenant_session(ctx) as session:
            await session.execute(
                self._text("DELETE FROM agency_module_flags WHERE agency_id = CAST(:aid AS uuid)"),
                {"aid": ctx.agency_id},
            )
        return await self.list_module_flags(ctx.agency_id, ctx)

    async def list_lead_keywords(self, ctx: TenantContext) -> list[LeadKeywordRule]:
        ctx.require_permission(Permission.CRM_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, category, keyword, geo_scope, created_at
                        FROM lead_keyword_rules
                        ORDER BY created_at DESC
                        """
                    )
                )
            ).mappings().all()
            return [
                LeadKeywordRule(
                    id=str(r["id"]),
                    agency_id=str(r["agency_id"]),
                    category=str(r["category"]),
                    keyword=str(r["keyword"]),
                    geo_scope=str(r["geo_scope"]),
                    created_at=_iso(r["created_at"]),
                )
                for r in rows
            ]

    async def add_lead_keyword(
        self,
        ctx: TenantContext,
        *,
        category: str,
        keyword: str,
        geo_scope: str = "France",
    ) -> LeadKeywordRule:
        ctx.require_permission(Permission.CRM_WRITE)
        clean_kw = InputSanitizer.sanitize_text(keyword, 120)
        if len(clean_kw) < 2:
            raise ValueError("Mot-clé invalide.")
        kid = f"kw-{uuid.uuid4().hex[:8]}"
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO lead_keyword_rules (id, agency_id, category, keyword, geo_scope)
                        VALUES (:id, CAST(:aid AS uuid), :cat, :kw, :geo)
                        RETURNING id, agency_id, category, keyword, geo_scope, created_at
                        """
                    ),
                    {
                        "id": kid,
                        "aid": ctx.agency_id,
                        "cat": InputSanitizer.sanitize_text(category, 32).upper() or "ACQUEREURS",
                        "kw": clean_kw,
                        "geo": InputSanitizer.sanitize_text(geo_scope, 80) or "France",
                    },
                )
            ).mappings().one()
            return LeadKeywordRule(
                id=str(row["id"]),
                agency_id=str(row["agency_id"]),
                category=str(row["category"]),
                keyword=str(row["keyword"]),
                geo_scope=str(row["geo_scope"]),
                created_at=_iso(row["created_at"]),
            )

    async def delete_lead_keyword(self, ctx: TenantContext, keyword_id: str) -> bool:
        ctx.require_permission(Permission.CRM_WRITE)
        async with self._open_tenant_session(ctx) as session:
            res = await session.execute(
                self._text("DELETE FROM lead_keyword_rules WHERE id = :kid"),
                {"kid": keyword_id},
            )
            return int(getattr(res, "rowcount", 0) or 0) > 0

    # ==========================================================================
    # 4. MONTY PRICE INDEX (CONFIG & HISTORY)
    # ==========================================================================

    async def get_price_index_config(self, ctx: TenantContext) -> PriceIndexConfig:
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        "SELECT * FROM monty_price_index_configs WHERE agency_id = CAST(:aid AS uuid)"
                    ),
                    {"aid": ctx.agency_id},
                )
            ).mappings().first()
            if row is None:
                return PriceIndexConfig(
                    agency_id=ctx.agency_id,
                    updated_at=datetime.now(timezone.utc).isoformat(),
                )
            return PriceIndexConfig(
                agency_id=str(row["agency_id"]),
                excellent_deal_pct=float(row.get("excellent_deal_pct", -15.0)),
                very_good_deal_pct=float(row["very_good_deal_pct"]),
                good_deal_pct=float(row["good_deal_pct"]),
                above_market_pct=float(row["above_market_pct"]),
                overpriced_pct=float(row["overpriced_pct"]),
                min_comparables_required=int(row["min_comparables_required"]),
                max_comparable_distance_m=int(row["max_comparable_distance_m"]),
                max_comparable_age_months=int(row["max_comparable_age_months"]),
                updated_by=str(row["updated_by"]),
                updated_at=_iso(row["updated_at"]),
            )

    async def upsert_price_index_config(self, ctx: TenantContext, cfg: PriceIndexConfig) -> PriceIndexConfig:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO monty_price_index_configs (
                            agency_id, excellent_deal_pct, very_good_deal_pct, good_deal_pct,
                            above_market_pct, overpriced_pct, min_comparables_required,
                            max_comparable_distance_m, max_comparable_age_months, updated_by, updated_at
                        )
                        VALUES (
                            CAST(:aid AS uuid), :exc, :vgd, :gd, :am, :ov, :mcomp, :mdist, :mage, :uid, now()
                        )
                        ON CONFLICT (agency_id) DO UPDATE SET
                            excellent_deal_pct = EXCLUDED.excellent_deal_pct,
                            very_good_deal_pct = EXCLUDED.very_good_deal_pct,
                            good_deal_pct = EXCLUDED.good_deal_pct,
                            above_market_pct = EXCLUDED.above_market_pct,
                            overpriced_pct = EXCLUDED.overpriced_pct,
                            min_comparables_required = EXCLUDED.min_comparables_required,
                            max_comparable_distance_m = EXCLUDED.max_comparable_distance_m,
                            max_comparable_age_months = EXCLUDED.max_comparable_age_months,
                            updated_by = EXCLUDED.updated_by,
                            updated_at = now()
                        RETURNING *
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "exc": cfg.excellent_deal_pct,
                        "vgd": cfg.very_good_deal_pct,
                        "gd": cfg.good_deal_pct,
                        "am": cfg.above_market_pct,
                        "ov": cfg.overpriced_pct,
                        "mcomp": cfg.min_comparables_required,
                        "mdist": cfg.max_comparable_distance_m,
                        "mage": cfg.max_comparable_age_months,
                        "uid": ctx.user_id,
                    },
                )
            ).mappings().one()
            return PriceIndexConfig(
                agency_id=str(row["agency_id"]),
                excellent_deal_pct=float(row.get("excellent_deal_pct", cfg.excellent_deal_pct)),
                very_good_deal_pct=float(row["very_good_deal_pct"]),
                good_deal_pct=float(row["good_deal_pct"]),
                above_market_pct=float(row["above_market_pct"]),
                overpriced_pct=float(row["overpriced_pct"]),
                min_comparables_required=int(row["min_comparables_required"]),
                max_comparable_distance_m=int(row["max_comparable_distance_m"]),
                max_comparable_age_months=int(row["max_comparable_age_months"]),
                updated_by=str(row["updated_by"]),
                updated_at=_iso(row["updated_at"]),
            )

    async def record_price_index_history(self, ctx: TenantContext, rec: PriceIndexHistoryRecord) -> None:
        if not is_valid_uuid(ctx.agency_id):
            return
        async with self._open_tenant_session(ctx) as session:
            await session.execute(
                self._text(
                    """
                    INSERT INTO monty_price_index_history (
                        id, agency_id, target_ref, title, country_code, city, property_kind,
                        asking_price_euro, estimated_market_value, gap_percent, category,
                        confidence_tier, comparables_count, evaluated_by
                    )
                    VALUES (
                        :id, CAST(:aid AS uuid), :tref, :title, :cc, :city, :pkind,
                        :ask, :est, :gap, :cat, :tier, :ccount, :ev_by
                    )
                    """
                ),
                {
                    "id": rec.id,
                    "aid": ctx.agency_id,
                    "tref": rec.target_ref,
                    "title": rec.title,
                    "cc": rec.country_code,
                    "city": rec.city,
                    "pkind": rec.property_kind,
                    "ask": rec.asking_price_euro,
                    "est": rec.estimated_market_value,
                    "gap": rec.gap_percent,
                    "cat": rec.category,
                    "tier": rec.confidence_tier,
                    "ccount": rec.comparables_count,
                    "ev_by": rec.evaluated_by,
                },
            )

    async def list_price_index_history(self, ctx: TenantContext, limit: int = 40) -> list[PriceIndexHistoryRecord]:
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, target_ref, title, country_code, city, property_kind,
                               asking_price_euro, estimated_market_value, gap_percent, category,
                               confidence_tier, comparables_count, evaluated_by, created_at
                        FROM monty_price_index_history
                        ORDER BY created_at DESC
                        LIMIT :lim
                        """
                    ),
                    {"lim": limit},
                )
            ).mappings().all()
            return [
                PriceIndexHistoryRecord(
                    id=str(r["id"]),
                    agency_id=str(r["agency_id"]),
                    target_ref=str(r["target_ref"]),
                    title=str(r["title"]),
                    country_code=str(r["country_code"]),
                    city=str(r["city"]),
                    property_kind=str(r["property_kind"]),
                    asking_price_euro=int(r["asking_price_euro"]),
                    estimated_market_value=int(r["estimated_market_value"]),
                    gap_percent=float(r["gap_percent"]),
                    category=str(r["category"]),
                    confidence_tier=str(r["confidence_tier"]),
                    comparables_count=int(r["comparables_count"]),
                    evaluated_by=str(r["evaluated_by"]),
                    created_at=_iso(r["created_at"]),
                )
                for r in rows
            ]

    # ==========================================================================
    # 5. SELLER PACK ORDERS (SERVER-SIDE STRIPE FULFILLMENT)
    # ==========================================================================

    async def create_seller_pack_order(
        self,
        *,
        order_id: str,
        dossier_id: str,
        property_id: str,
        buyer_user_id: str,
        buyer_email: str,
        amount_euro: int,
        stripe_session_id: str = "",
    ) -> dict[str, Any]:
        async with self._open_global_session() as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO seller_pack_orders (
                            order_id, dossier_id, property_id, buyer_user_id, buyer_email,
                            amount_euro, status, stripe_session_id
                        )
                        VALUES (:oid, :did, :pid, :buid, :email, :amt, 'PENDING_PAYMENT', :sid)
                        ON CONFLICT (order_id) DO UPDATE SET
                            stripe_session_id = EXCLUDED.stripe_session_id,
                            updated_at = now()
                        RETURNING *
                        """
                    ),
                    {
                        "oid": order_id,
                        "did": dossier_id,
                        "pid": property_id,
                        "buid": buyer_user_id,
                        "email": buyer_email,
                        "amt": amount_euro,
                        "sid": stripe_session_id,
                    },
                )
            ).mappings().one()
            return dict(row)

    async def mark_seller_pack_order_paid(
        self,
        *,
        order_id: str,
        stripe_session_id: str = "",
    ) -> dict[str, Any] | None:
        async with self._open_global_session() as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        UPDATE seller_pack_orders
                        SET status = 'PAID',
                            stripe_session_id = CASE WHEN :sid <> '' THEN :sid ELSE stripe_session_id END,
                            paid_at = COALESCE(paid_at, now()),
                            updated_at = now()
                        WHERE order_id = :oid
                        RETURNING *
                        """
                    ),
                    {"oid": order_id, "sid": stripe_session_id},
                )
            ).mappings().first()
            return dict(row) if row else None

    async def get_seller_pack_order(self, order_id: str) -> dict[str, Any] | None:
        async with self._open_global_session() as session:
            row = (
                await session.execute(
                    self._text("SELECT * FROM seller_pack_orders WHERE order_id = :oid"),
                    {"oid": order_id},
                )
            ).mappings().first()
            return dict(row) if row else None
