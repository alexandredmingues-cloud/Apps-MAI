from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import datetime, timezone
import json
from typing import Any, Callable
import uuid

from app.core.rbac import MontyRole, Permission, TenantContext
from app.core.security import InputSanitizer
from app.domain.interconnections_service import (
    AgencyFeatureFlags,
    AgencyNotificationRecord,
    ClientDocumentRecord,
    ClientFavoriteRecord,
)


def _default_sql_text(sql: str) -> Any:
    from sqlalchemy import text

    return text(sql)


class PostgresInterconnectionsRepository:
    """
    Persistance PostgreSQL RLS pour les interconnexions :
    - Notifications agence (`agency_notifications`)
    - Agenda unifié (`visits`, `follow_up_tasks`, `offers.notary_target_date`)
    - Portail Particulier / Vendeur (`client_portal_favorites`, `client_documents`, `properties`)
    - Pilotage Pro & Gouvernance (`audit_logs`, `agency_feature_flags`, KPIs live)
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

    @staticmethod
    def _require_portal_or_pro(ctx: TenantContext) -> None:
        if not (
            ctx.has_permission(Permission.BUYER_PORTAL_SELF)
            or ctx.has_permission(Permission.SELLER_PORTAL_SELF)
            or ctx.has_permission(Permission.PROPERTY_READ)
        ):
            raise PermissionError("Authentification requise pour accéder à l'espace personnel.")

    # --- NOTIFICATIONS ---
    async def list_notifications(
        self,
        ctx: TenantContext,
        *,
        unread_only: bool = False,
    ) -> list[AgencyNotificationRecord]:
        ctx.require_permission(Permission.PROPERTY_READ)
        sql = """
            SELECT id, agency_id, event_type, severity, title, detail, link_section,
                   target_id, is_read, created_at
            FROM agency_notifications
        """
        if unread_only:
            sql += " WHERE is_read = false"
        sql += " ORDER BY created_at DESC LIMIT 80"
        async with self._open_tenant_session(ctx) as session:
            rows = (await session.execute(self._text(sql))).mappings().all()
            return [
                AgencyNotificationRecord(
                    id=str(r["id"]),
                    agency_id=str(r["agency_id"]),
                    event_type=str(r["event_type"]),
                    severity=str(r["severity"]),
                    title=str(r["title"]),
                    detail=str(r.get("detail") or ""),
                    link_section=str(r.get("link_section") or "dashboard"),
                    target_id=str(r.get("target_id") or ""),
                    is_read=bool(r["is_read"]),
                    created_at=r["created_at"].isoformat() if hasattr(r["created_at"], "isoformat") else str(r["created_at"]),
                )
                for r in rows
            ]

    async def mark_notification_read(self, ctx: TenantContext, notification_id: str) -> AgencyNotificationRecord:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        UPDATE agency_notifications
                        SET is_read = true
                        WHERE id = CAST(:nid AS uuid)
                        RETURNING id, agency_id, event_type, severity, title, detail,
                                  link_section, target_id, is_read, created_at
                        """
                    ),
                    {"nid": notification_id},
                )
            ).mappings().first()
            if row is None:
                raise ValueError("Notification introuvable dans cette agence.")
            return AgencyNotificationRecord(
                id=str(row["id"]),
                agency_id=str(row["agency_id"]),
                event_type=str(row["event_type"]),
                severity=str(row["severity"]),
                title=str(row["title"]),
                detail=str(row.get("detail") or ""),
                link_section=str(row.get("link_section") or "dashboard"),
                target_id=str(row.get("target_id") or ""),
                is_read=bool(row["is_read"]),
                created_at=row["created_at"].isoformat() if hasattr(row["created_at"], "isoformat") else str(row["created_at"]),
            )

    # --- AGENDA UNIFIÉ ---
    async def get_unified_agenda(self, ctx: TenantContext) -> list[dict[str, Any]]:
        ctx.require_permission(Permission.PROPERTY_READ)
        items: list[dict[str, Any]] = []
        async with self._open_tenant_session(ctx) as session:
            v_rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT v.id, COALESCE(NULLIF(v.property_title, ''), p.title, 'Bien') AS property_title,
                               v.buyer_name, v.date_label, v.visit_date, v.status
                        FROM visits v
                        LEFT JOIN properties p ON p.id = v.property_id
                        WHERE v.status <> 'ANNULEE'
                        ORDER BY v.visit_date ASC, v.created_at DESC
                        LIMIT 40
                        """
                    )
                )
            ).mappings().all()
            for r in v_rows:
                vd = r.get("visit_date")
                items.append(
                    {
                        "id": f"visit:{r['id']}",
                        "kind": "VISITE",
                        "title": f"Visite — {r['property_title']}",
                        "subtitle": f"{r.get('buyer_name') or 'Acquéreur'} • {r.get('date_label') or ''}",
                        "date_iso": vd.isoformat() if hasattr(vd, "isoformat") else str(vd or ""),
                        "status": str(r["status"]),
                        "priority": "NORMAL",
                        "link_section": "visits",
                    }
                )

            t_rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, title, contact_name, reason, due_at, priority, status
                        FROM follow_up_tasks
                        WHERE status IN ('OPEN', 'À faire', 'En cours')
                        ORDER BY due_at ASC
                        LIMIT 40
                        """
                    )
                )
            ).mappings().all()
            for r in t_rows:
                due = r.get("due_at")
                due_iso = due.date().isoformat() if hasattr(due, "date") else str(due or "")[:10]
                items.append(
                    {
                        "id": f"task:{r['id']}",
                        "kind": "RELANCE",
                        "title": str(r["title"]),
                        "subtitle": str(r.get("reason") or r.get("contact_name") or ""),
                        "date_iso": due_iso,
                        "status": str(r["status"]),
                        "priority": str(r.get("priority") or "HIGH"),
                        "link_section": "crm",
                    }
                )

            o_rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT o.id, COALESCE(NULLIF(o.property_title, ''), p.title, 'Bien') AS property_title,
                               o.buyer_name, o.stage, o.notary_target_date, o.offered_price
                        FROM offers o
                        LEFT JOIN properties p ON p.id = o.property_id
                        WHERE o.notary_target_date IS NOT NULL
                        ORDER BY o.notary_target_date ASC
                        LIMIT 40
                        """
                    )
                )
            ).mappings().all()
            for r in o_rows:
                nt = r.get("notary_target_date")
                items.append(
                    {
                        "id": f"notary:{r['id']}",
                        "kind": "NOTAIRE",
                        "title": f"Échéance Notaire ({r['stage']}) — {r['property_title']}",
                        "subtitle": f"{r.get('buyer_name') or 'Acquéreur'} • {int(r.get('offered_price') or 0):,} €".replace(",", " "),
                        "date_iso": nt.isoformat() if hasattr(nt, "isoformat") else str(nt or ""),
                        "status": str(r["stage"]),
                        "priority": "HIGH",
                        "link_section": "transactions",
                    }
                )

        items.sort(key=lambda x: x["date_iso"] or "9999-12-31")
        return items

    # --- PORTAIL PARTICULIER / VENDEUR ---
    async def list_favorites(self, ctx: TenantContext) -> list[ClientFavoriteRecord]:
        self._require_portal_or_pro(ctx)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, user_id, listing_id, listing_snapshot, created_at
                        FROM client_portal_favorites
                        WHERE user_id = CAST(:uid AS uuid)
                        ORDER BY created_at DESC
                        LIMIT 100
                        """
                    ),
                    {"uid": ctx.user_id},
                )
            ).mappings().all()
            out: list[ClientFavoriteRecord] = []
            for r in rows:
                snap = r.get("listing_snapshot")
                if isinstance(snap, str):
                    try:
                        snap = json.loads(snap)
                    except Exception:
                        snap = {}
                out.append(
                    ClientFavoriteRecord(
                        id=str(r["id"]),
                        agency_id=str(r["agency_id"]),
                        user_id=str(r["user_id"]),
                        listing_id=str(r["listing_id"]),
                        listing_snapshot=dict(snap or {}),
                        created_at=r["created_at"].isoformat() if hasattr(r["created_at"], "isoformat") else str(r["created_at"]),
                    )
                )
            return out

    async def upsert_favorite(
        self,
        ctx: TenantContext,
        *,
        listing_id: str,
        listing_snapshot: dict[str, Any],
    ) -> ClientFavoriteRecord:
        self._require_portal_or_pro(ctx)
        clean_id = InputSanitizer.sanitize_text(listing_id, 120)
        if not clean_id:
            raise ValueError("Identifiant d'annonce obligatoire.")
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO client_portal_favorites (agency_id, user_id, listing_id, listing_snapshot)
                        VALUES (CAST(:aid AS uuid), CAST(:uid AS uuid), :lid, CAST(:snap AS jsonb))
                        ON CONFLICT (user_id, listing_id) DO UPDATE SET
                          listing_snapshot = EXCLUDED.listing_snapshot
                        RETURNING id, agency_id, user_id, listing_id, listing_snapshot, created_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "lid": clean_id,
                        "snap": json.dumps(listing_snapshot or {}),
                    },
                )
            ).mappings().one()
            snap = row.get("listing_snapshot")
            if isinstance(snap, str):
                snap = json.loads(snap)
            return ClientFavoriteRecord(
                id=str(row["id"]),
                agency_id=str(row["agency_id"]),
                user_id=str(row["user_id"]),
                listing_id=str(row["listing_id"]),
                listing_snapshot=dict(snap or {}),
                created_at=row["created_at"].isoformat() if hasattr(row["created_at"], "isoformat") else str(row["created_at"]),
            )

    async def remove_favorite(self, ctx: TenantContext, *, listing_id: str) -> bool:
        self._require_portal_or_pro(ctx)
        async with self._open_tenant_session(ctx) as session:
            res = await session.execute(
                self._text(
                    """
                    DELETE FROM client_portal_favorites
                    WHERE user_id = CAST(:uid AS uuid) AND listing_id = :lid
                    """
                ),
                {"uid": ctx.user_id, "lid": listing_id},
            )
            return (res.rowcount or 0) > 0

    async def list_client_documents(self, ctx: TenantContext) -> list[ClientDocumentRecord]:
        self._require_portal_or_pro(ctx)
        async with self._open_tenant_session(ctx) as session:
            if ctx.has_permission(Permission.PROPERTY_READ):
                rows = (
                    await session.execute(
                        self._text(
                            """
                            SELECT id, agency_id, owner_user_id, title, doc_category, file_name,
                                   mime_type, file_size_bytes, notes, status, created_at
                            FROM client_documents
                            ORDER BY created_at DESC
                            LIMIT 120
                            """
                        )
                    )
                ).mappings().all()
            else:
                rows = (
                    await session.execute(
                        self._text(
                            """
                            SELECT id, agency_id, owner_user_id, title, doc_category, file_name,
                                   mime_type, file_size_bytes, notes, status, created_at
                            FROM client_documents
                            WHERE owner_user_id = CAST(:uid AS uuid)
                            ORDER BY created_at DESC
                            LIMIT 120
                            """
                        ),
                        {"uid": ctx.user_id},
                    )
                ).mappings().all()
            return [
                ClientDocumentRecord(
                    id=str(r["id"]),
                    agency_id=str(r["agency_id"]),
                    owner_user_id=str(r["owner_user_id"]),
                    title=str(r["title"]),
                    doc_category=str(r["doc_category"]),
                    file_name=str(r["file_name"]),
                    mime_type=str(r["mime_type"]),
                    file_size_bytes=int(r["file_size_bytes"]),
                    notes=str(r.get("notes") or ""),
                    status=str(r["status"]),
                    created_at=r["created_at"].isoformat() if hasattr(r["created_at"], "isoformat") else str(r["created_at"]),
                )
                for r in rows
            ]

    async def add_client_document(
        self,
        ctx: TenantContext,
        *,
        title: str,
        doc_category: str,
        file_name: str,
        mime_type: str = "application/pdf",
        file_size_bytes: int = 0,
        notes: str = "",
    ) -> ClientDocumentRecord:
        self._require_portal_or_pro(ctx)
        clean_title = InputSanitizer.sanitize_text(title, 180)
        clean_file = InputSanitizer.sanitize_text(file_name, 200)
        if not clean_title or not clean_file:
            raise ValueError("Titre et nom de fichier obligatoires.")
        clean_cat = InputSanitizer.sanitize_text(doc_category, 48).upper() or "FINANCEMENT"
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO client_documents (
                          agency_id, owner_user_id, title, doc_category, file_name, mime_type, file_size_bytes, notes, status
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:uid AS uuid), :title, :cat, :fname, :mime, :fsize, :notes, 'DEPOSE'
                        )
                        RETURNING id, agency_id, owner_user_id, title, doc_category, file_name,
                                  mime_type, file_size_bytes, notes, status, created_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "title": clean_title,
                        "cat": clean_cat,
                        "fname": clean_file,
                        "mime": InputSanitizer.sanitize_text(mime_type, 100) or "application/pdf",
                        "fsize": max(0, int(file_size_bytes)),
                        "notes": InputSanitizer.sanitize_text(notes, 600),
                    },
                )
            ).mappings().one()
            await session.execute(
                self._text(
                    """
                    INSERT INTO agency_notifications (
                      agency_id, event_type, severity, title, detail, link_section, target_id
                    ) VALUES (
                      CAST(:aid AS uuid), 'CLIENT_DOCUMENT_UPLOADED', 'INFO', :title, :detail, 'documents', :tid
                    )
                    """
                ),
                {
                    "aid": ctx.agency_id,
                    "title": f"Nouvelle pièce client déposée : {clean_title}"[:200],
                    "detail": f"Catégorie {clean_cat} — {clean_file}"[:280],
                    "tid": str(row["id"]),
                },
            )
            return ClientDocumentRecord(
                id=str(row["id"]),
                agency_id=str(row["agency_id"]),
                owner_user_id=str(row["owner_user_id"]),
                title=str(row["title"]),
                doc_category=str(row["doc_category"]),
                file_name=str(row["file_name"]),
                mime_type=str(row["mime_type"]),
                file_size_bytes=int(row["file_size_bytes"]),
                notes=str(row.get("notes") or ""),
                status=str(row["status"]),
                created_at=row["created_at"].isoformat() if hasattr(row["created_at"], "isoformat") else str(row["created_at"]),
            )

    async def list_seller_listings(self, ctx: TenantContext) -> list[dict[str, Any]]:
        self._require_portal_or_pro(ctx)
        async with self._open_tenant_session(ctx) as session:
            if ctx.has_permission(Permission.PROPERTY_READ):
                rows = (
                    await session.execute(
                        self._text(
                            """
                            SELECT id, title, city, sector, property_type, price_euro, surface_m2, rooms,
                                   dpe, publication_status, moderation_note, created_at
                            FROM properties
                            ORDER BY created_at DESC
                            LIMIT 60
                            """
                        )
                    )
                ).mappings().all()
            else:
                rows = (
                    await session.execute(
                        self._text(
                            """
                            SELECT id, title, city, sector, property_type, price_euro, surface_m2, rooms,
                                   dpe, publication_status, moderation_note, created_at
                            FROM properties
                            WHERE seller_user_id = CAST(:uid AS uuid) OR assigned_agent_id = CAST(:uid AS uuid)
                            ORDER BY created_at DESC
                            LIMIT 60
                            """
                        ),
                        {"uid": ctx.user_id},
                    )
                ).mappings().all()
            return [
                {
                    "id": str(r["id"]),
                    "title": str(r["title"]),
                    "city": str(r.get("city") or ""),
                    "sector": str(r.get("sector") or ""),
                    "property_type": str(r.get("property_type") or "Appartement"),
                    "price_euro": int(r["price_euro"]),
                    "surface_m2": int(r["surface_m2"]),
                    "rooms": int(r.get("rooms") or 1),
                    "dpe": str(r.get("dpe") or "C"),
                    "moderation_status": str(r.get("publication_status") or "BROUILLON"),
                    "moderation_note": str(r.get("moderation_note") or ""),
                    "created_at": r["created_at"].isoformat() if hasattr(r["created_at"], "isoformat") else str(r["created_at"]),
                }
                for r in rows
            ]

    # --- PILOTAGE PRO & GOUVERNANCE ADMIN ---
    async def get_admin_overview(self, ctx: TenantContext) -> dict[str, Any]:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            props_count = int(
                (await session.execute(self._text("SELECT COUNT(*) FROM properties"))).scalar() or 0
            )
            contacts_count = int(
                (
                    await session.execute(
                        self._text("SELECT COUNT(*) FROM crm_contacts WHERE archived = false AND is_anonymized = false")
                    )
                ).scalar()
                or 0
            )
            visits_count = int(
                (
                    await session.execute(self._text("SELECT COUNT(*) FROM visits WHERE status <> 'ANNULEE'"))
                ).scalar()
                or 0
            )
            tx_row = (
                await session.execute(
                    self._text("SELECT COUNT(*) AS cnt, COALESCE(SUM(offered_price), 0) AS vol FROM offers")
                )
            ).mappings().one()
            open_tasks = int(
                (
                    await session.execute(
                        self._text("SELECT COUNT(*) FROM follow_up_tasks WHERE status IN ('OPEN', 'À faire', 'En cours')")
                    )
                ).scalar()
                or 0
            )
            new_leads = int(
                (
                    await session.execute(
                        self._text("SELECT COUNT(*) FROM public_inbound_leads WHERE status = 'NEW'")
                    )
                ).scalar()
                or 0
            )
            unread_notifs = int(
                (
                    await session.execute(
                        self._text("SELECT COUNT(*) FROM agency_notifications WHERE is_read = false")
                    )
                ).scalar()
                or 0
            )
            ai_spend = float(
                (
                    await session.execute(
                        self._text(
                            "SELECT COALESCE(SUM(cost_euro), 0.0) FROM agent_execution_logs WHERE created_at >= date_trunc('day', now())"
                        )
                    )
                ).scalar()
                or 0.0
            )
            return {
                "agency_id": ctx.agency_id,
                "properties_count": props_count,
                "active_contacts_count": contacts_count,
                "scheduled_visits_count": visits_count,
                "transactions_count": int(tx_row["cnt"] or 0),
                "pipeline_volume_euro": int(tx_row["vol"] or 0),
                "open_followups_count": open_tasks,
                "new_public_leads_count": new_leads,
                "unread_notifications_count": unread_notifs,
                "ai_daily_spend_euro": round(ai_spend, 4),
            }

    async def list_audit_logs(self, ctx: TenantContext, *, limit: int = 40) -> list[dict[str, Any]]:
        ctx.require_permission(Permission.AUDIT_LOGS_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, actor_user_id, actor_role, action_type,
                               target_type, target_id, detail, created_at
                        FROM audit_logs
                        ORDER BY created_at DESC
                        LIMIT :lim
                        """
                    ),
                    {"lim": max(1, min(120, limit))},
                )
            ).mappings().all()
            return [
                {
                    "id": str(r["id"]),
                    "agency_id": str(r["agency_id"]),
                    "actor_user_id": str(r["actor_user_id"]) if r.get("actor_user_id") else "",
                    "actor_role": str(r["actor_role"]),
                    "action_type": str(r["action_type"]),
                    "target_type": str(r.get("target_type") or ""),
                    "target_id": str(r.get("target_id") or ""),
                    "detail": str(r.get("detail") or ""),
                    "created_at": r["created_at"].isoformat() if hasattr(r["created_at"], "isoformat") else str(r["created_at"]),
                }
                for r in rows
            ]

    async def get_feature_flags(self, ctx: TenantContext) -> AgencyFeatureFlags:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        SELECT agency_id, auto_matching_enabled, ai_copywriting_enabled,
                               rnic_free_for_sellers, public_appointment_booking,
                               notary_auto_task_on_compromis, updated_at
                        FROM agency_feature_flags
                        WHERE agency_id = CAST(:aid AS uuid)
                        """
                    ),
                    {"aid": ctx.agency_id},
                )
            ).mappings().first()
            if row is None:
                return AgencyFeatureFlags(
                    agency_id=ctx.agency_id,
                    updated_at=datetime.now(timezone.utc).isoformat(),
                )
            return AgencyFeatureFlags(
                agency_id=str(row["agency_id"]),
                auto_matching_enabled=bool(row["auto_matching_enabled"]),
                ai_copywriting_enabled=bool(row["ai_copywriting_enabled"]),
                rnic_free_for_sellers=bool(row["rnic_free_for_sellers"]),
                public_appointment_booking=bool(row["public_appointment_booking"]),
                notary_auto_task_on_compromis=bool(row["notary_auto_task_on_compromis"]),
                updated_at=row["updated_at"].isoformat() if hasattr(row["updated_at"], "isoformat") else str(row["updated_at"]),
            )

    async def update_feature_flags(
        self,
        ctx: TenantContext,
        *,
        auto_matching_enabled: bool,
        ai_copywriting_enabled: bool,
        rnic_free_for_sellers: bool,
        public_appointment_booking: bool,
        notary_auto_task_on_compromis: bool,
    ) -> AgencyFeatureFlags:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO agency_feature_flags (
                          agency_id, auto_matching_enabled, ai_copywriting_enabled,
                          rnic_free_for_sellers, public_appointment_booking,
                          notary_auto_task_on_compromis, updated_by_user_id
                        ) VALUES (
                          CAST(:aid AS uuid), :m, :ai, :rnic, :pub, :notary, CAST(NULLIF(:uid, '') AS uuid)
                        )
                        ON CONFLICT (agency_id) DO UPDATE SET
                          auto_matching_enabled = EXCLUDED.auto_matching_enabled,
                          ai_copywriting_enabled = EXCLUDED.ai_copywriting_enabled,
                          rnic_free_for_sellers = EXCLUDED.rnic_free_for_sellers,
                          public_appointment_booking = EXCLUDED.public_appointment_booking,
                          notary_auto_task_on_compromis = EXCLUDED.notary_auto_task_on_compromis,
                          updated_by_user_id = EXCLUDED.updated_by_user_id,
                          updated_at = now()
                        RETURNING agency_id, auto_matching_enabled, ai_copywriting_enabled,
                                  rnic_free_for_sellers, public_appointment_booking,
                                  notary_auto_task_on_compromis, updated_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "m": bool(auto_matching_enabled),
                        "ai": bool(ai_copywriting_enabled),
                        "rnic": bool(rnic_free_for_sellers),
                        "pub": bool(public_appointment_booking),
                        "notary": bool(notary_auto_task_on_compromis),
                        "uid": ctx.user_id,
                    },
                )
            ).mappings().one()
            return AgencyFeatureFlags(
                agency_id=str(row["agency_id"]),
                auto_matching_enabled=bool(row["auto_matching_enabled"]),
                ai_copywriting_enabled=bool(row["ai_copywriting_enabled"]),
                rnic_free_for_sellers=bool(row["rnic_free_for_sellers"]),
                public_appointment_booking=bool(row["public_appointment_booking"]),
                notary_auto_task_on_compromis=bool(row["notary_auto_task_on_compromis"]),
                updated_at=row["updated_at"].isoformat() if hasattr(row["updated_at"], "isoformat") else str(row["updated_at"]),
            )
