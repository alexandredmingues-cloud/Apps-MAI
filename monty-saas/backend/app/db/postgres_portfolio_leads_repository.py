from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import date, datetime, timezone
from typing import Any, Callable
import uuid

from app.core.rbac import Permission, TenantContext
from app.core.rgpd import redact_direct_pii_for_ai
from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode
from app.domain.portfolio_and_leads_service import (
    LeadKind,
    LeadStatus,
    PortfolioProperty,
    PortfolioTransaction,
    PortfolioVisit,
    PublicInboundLead,
    TransactionStage,
    VisitStatus,
)


def _default_sql_text(sql: str) -> Any:
    from sqlalchemy import text

    return text(sql)


def _normalize_visit_status(raw: str | None) -> VisitStatus:
    val = (raw or "PLANIFIEE").strip().upper()
    if "REAL" in val or "FAIT" in val:
        return VisitStatus.REALISEE
    if "ANNUL" in val:
        return VisitStatus.ANNULEE
    return VisitStatus.PLANIFIEE


def _normalize_stage(raw: str | None) -> TransactionStage:
    val = (raw or "OFFRE").strip().upper()
    if val in ("OFFRE", "COMPROMIS", "ACTE"):
        return TransactionStage(val)
    return TransactionStage.OFFRE


class PostgresPortfolioLeadsRepository:
    """
    Persistance PostgreSQL RLS pour :
    - Portefeuille Biens (`properties`)
    - Visites (`visits`)
    - Offres / Compromis / Actes (`offers`)
    - Leads Publics Entrants RGPD (`public_inbound_leads` + `monty_submit_public_lead`)
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
    def _row_to_property(row: Any) -> PortfolioProperty:
        raw_mandate = str(row["mandate_type"] or "").strip().upper()
        mandate = "EXCLUSIF" if raw_mandate.startswith("EXCLU") or raw_mandate == "" else "SIMPLE"
        created = row["created_at"]
        created_iso = created.isoformat() if hasattr(created, "isoformat") else str(created)
        return PortfolioProperty(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]),
            title=str(row["title"]),
            sector=str(row["sector"]),
            property_type=str(row["property_type"]),
            price=int(row["price_euro"]),
            surface=int(row["surface_m2"]),
            bedrooms=int(row["bedrooms"] or 0),
            dpe=str(row["dpe"] or "C"),
            mandate=mandate,
            docs_ratio=float(row["docs_ratio"] if row.get("docs_ratio") is not None else 0.70),
            created_at=created_iso,
        )

    @staticmethod
    def _row_to_visit(row: Any) -> PortfolioVisit:
        created = row["created_at"]
        created_iso = created.isoformat() if hasattr(created, "isoformat") else str(created)
        date_label = str(row.get("date_label") or "").strip()
        if not date_label:
            vd = row.get("visit_date")
            ts = str(row.get("time_slot") or "").strip()
            date_label = f"{vd} {ts}".strip() if vd else ts or "À confirmer"
        return PortfolioVisit(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]),
            property_id=str(row["property_id"]),
            property_title=str(row.get("property_title") or ""),
            buyer_name=str(row.get("buyer_name") or "Acquéreur"),
            date_label=date_label,
            status=_normalize_visit_status(str(row.get("status") or "")),
            feedback=str(row.get("client_impressions") or ""),
            created_at=created_iso,
        )

    @staticmethod
    def _row_to_transaction(row: Any) -> PortfolioTransaction:
        created = row["created_at"]
        created_iso = created.isoformat() if hasattr(created, "isoformat") else str(created)
        nt = row.get("notary_target_date")
        notary_iso = nt.isoformat() if hasattr(nt, "isoformat") else str(nt or "")
        return PortfolioTransaction(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]),
            property_id=str(row["property_id"]),
            property_title=str(row.get("property_title") or ""),
            buyer_name=str(row.get("buyer_name") or "Acquéreur"),
            offer_euro=int(row["offered_price"]),
            stage=_normalize_stage(str(row.get("stage") or "OFFRE")),
            notary_target_date=notary_iso,
            created_at=created_iso,
        )

    @staticmethod
    def _row_to_lead(row: Any) -> PublicInboundLead:
        consent_at = row.get("rgpd_consent_at")
        created_at = row.get("created_at")
        return PublicInboundLead(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]) if row.get("agency_id") else None,
            crm_contact_id=str(row["crm_contact_id"]) if row.get("crm_contact_id") else None,
            lead_kind=LeadKind(str(row["lead_kind"])),
            country_code=CountryCode(str(row["country_code"])),
            full_name=str(row.get("full_name") or ""),
            email=str(row["email"]),
            phone=str(row.get("phone") or ""),
            city=str(row.get("city") or ""),
            budget_euro=int(row.get("budget_euro") or 0),
            min_rooms=int(row.get("min_rooms") or 1),
            search_transport_mode=str(row.get("search_transport_mode") or "TRAM"),
            max_travel_minutes=int(row.get("max_travel_minutes") or 30),
            horizon=str(row.get("horizon") or ""),
            property_title=str(row.get("property_title") or ""),
            notes=str(row.get("notes") or ""),
            rgpd_consent_granted=bool(row.get("rgpd_consent_granted", True)),
            rgpd_consent_at=consent_at.isoformat() if hasattr(consent_at, "isoformat") else str(consent_at or ""),
            status=LeadStatus(str(row.get("status") or "NEW")),
            created_at=created_at.isoformat() if hasattr(created_at, "isoformat") else str(created_at or ""),
        )

    # --- PORTEFEUILLE BIENS ---
    async def list_properties(self, ctx: TenantContext) -> list[PortfolioProperty]:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, title, sector, property_type, price_euro,
                               surface_m2, bedrooms, dpe, mandate_type, docs_ratio, created_at
                        FROM properties
                        ORDER BY created_at DESC
                        LIMIT 200
                        """
                    )
                )
            ).mappings().all()
            return [self._row_to_property(r) for r in rows]

    async def create_property(
        self,
        ctx: TenantContext,
        *,
        title: str,
        sector: str,
        property_type: str = "Appartement",
        price: int,
        surface: int,
        bedrooms: int = 2,
        dpe: str = "C",
        mandate: str = "EXCLUSIF",
        docs_ratio: float = 0.70,
    ) -> PortfolioProperty:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        clean_title = InputSanitizer.sanitize_text(title, 160)
        clean_sector = InputSanitizer.sanitize_text(sector, 100)
        if not clean_title or not clean_sector or price < 10_000 or surface < 5:
            raise ValueError("Titre, secteur, prix (>= 10 000 €) et surface (>= 5 m²) requis.")
        mandate_norm = "EXCLUSIF" if mandate.strip().upper().startswith("EXCLU") else "SIMPLE"
        ratio_clamped = max(0.0, min(1.0, float(docs_ratio)))
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO properties (
                          agency_id, assigned_agent_id, title, sector, property_type,
                          price_euro, surface_m2, bedrooms, dpe, mandate_type, docs_ratio,
                          publisher_type, publication_status
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:uid AS uuid), :title, :sector, :kind,
                          :price, :surface, :bedrooms, :dpe, :mandate, :docs_ratio,
                          'AGENCE', 'BROUILLON'
                        )
                        RETURNING id, agency_id, title, sector, property_type, price_euro,
                                  surface_m2, bedrooms, dpe, mandate_type, docs_ratio, created_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "title": clean_title,
                        "sector": clean_sector,
                        "kind": InputSanitizer.sanitize_text(property_type, 60) or "Appartement",
                        "price": price,
                        "surface": surface,
                        "bedrooms": max(0, min(30, bedrooms)),
                        "dpe": (dpe.strip().upper()[:2] or "C"),
                        "mandate": mandate_norm,
                        "docs_ratio": ratio_clamped,
                    },
                )
            ).mappings().one()
            prop = self._row_to_property(row)
            buyer_rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT full_name, sector, budget_max
                        FROM crm_contacts
                        WHERE archived = false AND is_anonymized = false AND contact_type IN ('BUYER', 'Acquéreur')
                        ORDER BY created_at DESC
                        LIMIT 40
                        """
                    )
                )
            ).mappings().all()
            matched_labels: list[str] = []
            for br in buyer_rows:
                b_budget = int(br.get("budget_max") or 0)
                b_sector = str(br.get("sector") or "").lower()
                if b_budget >= int(prop.price * 0.90) and (
                    not b_sector or b_sector in prop.sector.lower() or prop.sector.lower() in b_sector
                ):
                    matched_labels.append(str(br["full_name"]))
            await session.execute(
                self._text(
                    """
                    INSERT INTO agency_notifications (
                      agency_id, event_type, severity, title, detail, link_section, target_id
                    ) VALUES (
                      CAST(:aid AS uuid), :etype, :sev, :title, :detail, :lsec, :tid
                    )
                    """
                ),
                {
                    "aid": ctx.agency_id,
                    "etype": "BUYER_MATCH_FOUND" if matched_labels else "PROPERTY_CREATED",
                    "sev": "SUCCESS" if matched_labels else "INFO",
                    "title": f"Nouveau mandat : {prop.title}"[:200],
                    "detail": (
                        f"{prop.sector} • {prop.price:,} € • Acquéreurs compatibles : {', '.join(matched_labels[:3])}".replace(",", " ")
                        if matched_labels
                        else f"{prop.sector} • {prop.price:,} € • Prêt pour matching.".replace(",", " ")
                    )[:300],
                    "lsec": "matching" if matched_labels else "properties",
                    "tid": prop.id,
                },
            )
            return prop

    async def update_property(
        self,
        ctx: TenantContext,
        *,
        property_id: str,
        title: str | None = None,
        sector: str | None = None,
        property_type: str | None = None,
        price: int | None = None,
        surface: int | None = None,
        bedrooms: int | None = None,
        dpe: str | None = None,
        mandate: str | None = None,
        docs_ratio: float | None = None,
    ) -> PortfolioProperty:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        clean_title = InputSanitizer.sanitize_text(title, 160) if title is not None else None
        if title is not None and not clean_title:
            raise ValueError("Le titre du bien ne peut pas être vide.")
        clean_sector = InputSanitizer.sanitize_text(sector, 100) if sector is not None else None
        if sector is not None and not clean_sector:
            raise ValueError("Le secteur du bien ne peut pas être vide.")
        if price is not None and price < 10_000:
            raise ValueError("Prix minimum 10 000 €.")
        if surface is not None and surface < 5:
            raise ValueError("Surface minimale 5 m².")
        clean_type = InputSanitizer.sanitize_text(property_type, 60) if property_type is not None else None
        clean_bedrooms = max(0, min(30, bedrooms)) if bedrooms is not None else None
        clean_dpe = (dpe.strip().upper()[:2] or None) if dpe is not None else None
        clean_mandate = (
            ("EXCLUSIF" if mandate.strip().upper().startswith("EXCLU") else "SIMPLE")
            if mandate is not None and mandate.strip()
            else None
        )
        clean_ratio = max(0.0, min(1.0, float(docs_ratio))) if docs_ratio is not None else None

        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        UPDATE properties
                        SET title = COALESCE(:title, title),
                            sector = COALESCE(:sector, sector),
                            property_type = COALESCE(:kind, property_type),
                            price_euro = COALESCE(:price, price_euro),
                            surface_m2 = COALESCE(:surface, surface_m2),
                            bedrooms = COALESCE(:bedrooms, bedrooms),
                            dpe = COALESCE(:dpe, dpe),
                            mandate_type = COALESCE(:mandate, mandate_type),
                            docs_ratio = COALESCE(:docs_ratio, docs_ratio),
                            updated_at = now()
                        WHERE id = CAST(:pid AS uuid)
                        RETURNING id, agency_id, title, sector, property_type, price_euro,
                                  surface_m2, bedrooms, dpe, mandate_type, docs_ratio, created_at
                        """
                    ),
                    {
                        "pid": property_id,
                        "title": clean_title,
                        "sector": clean_sector,
                        "kind": clean_type,
                        "price": price,
                        "surface": surface,
                        "bedrooms": clean_bedrooms,
                        "dpe": clean_dpe,
                        "mandate": clean_mandate,
                        "docs_ratio": clean_ratio,
                    },
                )
            ).mappings().first()
            if row is None:
                raise ValueError("Bien introuvable dans cette agence.")
            prop = self._row_to_property(row)

            buyer_rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT full_name, sector, budget_max
                        FROM crm_contacts
                        WHERE archived = false AND is_anonymized = false AND contact_type IN ('BUYER', 'Acquéreur')
                        ORDER BY created_at DESC
                        LIMIT 40
                        """
                    )
                )
            ).mappings().all()
            matched_labels: list[str] = []
            for br in buyer_rows:
                b_budget = int(br.get("budget_max") or 0)
                b_sector = str(br.get("sector") or "").lower()
                if b_budget >= int(prop.price * 0.90) and (
                    not b_sector or b_sector in prop.sector.lower() or prop.sector.lower() in b_sector
                ):
                    matched_labels.append(str(br["full_name"]))
            await session.execute(
                self._text(
                    """
                    INSERT INTO agency_notifications (
                      agency_id, event_type, severity, title, detail, link_section, target_id
                    ) VALUES (
                      CAST(:aid AS uuid), :etype, :sev, :title, :detail, :lsec, :tid
                    )
                    """
                ),
                {
                    "aid": ctx.agency_id,
                    "etype": "BUYER_MATCH_FOUND" if matched_labels else "PROPERTY_UPDATED",
                    "sev": "SUCCESS" if matched_labels else "INFO",
                    "title": f"Mandat mis à jour : {prop.title}"[:200],
                    "detail": (
                        f"{prop.sector} • {prop.price:,} € • Acquéreurs compatibles : {', '.join(matched_labels[:3])}".replace(",", " ")
                        if matched_labels
                        else f"{prop.sector} • {prop.price:,} € • Critères actualisés.".replace(",", " ")
                    )[:300],
                    "lsec": "matching" if matched_labels else "properties",
                    "tid": prop.id,
                },
            )
            return prop

    # --- VISITES ---
    async def list_visits(self, ctx: TenantContext) -> list[PortfolioVisit]:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT v.id, v.agency_id, v.property_id,
                               COALESCE(NULLIF(v.property_title, ''), p.title, '') AS property_title,
                               v.buyer_name, v.date_label, v.visit_date, v.time_slot,
                               v.status, v.client_impressions, v.created_at
                        FROM visits v
                        LEFT JOIN properties p ON p.id = v.property_id
                        ORDER BY v.created_at DESC
                        LIMIT 200
                        """
                    )
                )
            ).mappings().all()
            return [self._row_to_visit(r) for r in rows]

    async def create_visit(
        self,
        ctx: TenantContext,
        *,
        property_id: str,
        buyer_name: str,
        date_label: str,
        feedback: str = "",
    ) -> PortfolioVisit:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        clean_buyer = InputSanitizer.sanitize_text(buyer_name, 140)
        clean_date = InputSanitizer.sanitize_text(date_label, 120)
        if not clean_buyer or not clean_date:
            raise ValueError("Nom de l'acquéreur et créneau de visite requis.")
        clean_feedback = InputSanitizer.sanitize_text(feedback, 500) or "Créneau confirmé depuis le cockpit."

        async with self._open_tenant_session(ctx) as session:
            prop = (
                await session.execute(
                    self._text("SELECT id, title FROM properties WHERE id = CAST(:pid AS uuid)"),
                    {"pid": property_id},
                )
            ).mappings().first()
            if prop is None:
                raise ValueError("Bien introuvable dans cette agence.")

            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO visits (
                          agency_id, property_id, advisor_user_id, visit_date, time_slot,
                          property_title, buyer_name, date_label, status, client_impressions
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:pid AS uuid), CAST(:uid AS uuid), CURRENT_DATE, :slot,
                          :ptitle, :buyer, :dlabel, 'PLANIFIEE', :feedback
                        )
                        RETURNING id, agency_id, property_id, property_title, buyer_name,
                                  date_label, visit_date, time_slot, status, client_impressions, created_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "pid": property_id,
                        "uid": ctx.user_id,
                        "slot": clean_date[:40],
                        "ptitle": str(prop["title"])[:180],
                        "buyer": clean_buyer,
                        "dlabel": clean_date,
                        "feedback": clean_feedback,
                    },
                )
            ).mappings().one()
            return self._row_to_visit(row)

    async def update_visit_status(
        self,
        ctx: TenantContext,
        *,
        visit_id: str,
        status: VisitStatus,
        feedback: str | None = None,
    ) -> PortfolioVisit:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        async with self._open_tenant_session(ctx) as session:
            clean_fb = InputSanitizer.sanitize_text(feedback, 500) if feedback else None
            row = (
                await session.execute(
                    self._text(
                        """
                        UPDATE visits
                        SET status = :st,
                            client_impressions = COALESCE(:fb, client_impressions),
                            updated_at = now()
                        WHERE id = CAST(:vid AS uuid)
                        RETURNING id, agency_id, property_id, property_title, buyer_name,
                                  date_label, visit_date, time_slot, status, client_impressions, created_at
                        """
                    ),
                    {"vid": visit_id, "st": status.value, "fb": clean_fb},
                )
            ).mappings().first()
            if row is None:
                raise ValueError("Visite introuvable dans cette agence.")
            return self._row_to_visit(row)

    # --- TRANSACTIONS / OFFRES ---
    async def list_transactions(self, ctx: TenantContext) -> list[PortfolioTransaction]:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT o.id, o.agency_id, o.property_id,
                               COALESCE(NULLIF(o.property_title, ''), p.title, '') AS property_title,
                               o.buyer_name, o.offered_price, o.stage, o.notary_target_date, o.created_at
                        FROM offers o
                        LEFT JOIN properties p ON p.id = o.property_id
                        ORDER BY o.created_at DESC
                        LIMIT 200
                        """
                    )
                )
            ).mappings().all()
            return [self._row_to_transaction(r) for r in rows]

    async def create_transaction(
        self,
        ctx: TenantContext,
        *,
        property_id: str,
        buyer_name: str,
        offer_euro: int,
        stage: TransactionStage = TransactionStage.OFFRE,
        notary_target_date: str = "",
    ) -> PortfolioTransaction:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        clean_buyer = InputSanitizer.sanitize_text(buyer_name, 140)
        if not clean_buyer or offer_euro < 10_000:
            raise ValueError("Acquéreur et montant d'offre (>= 10 000 €) requis.")
        target_date = date.fromisoformat(notary_target_date.strip()) if notary_target_date.strip() else date.today()

        async with self._open_tenant_session(ctx) as session:
            prop = (
                await session.execute(
                    self._text("SELECT id, title, price_euro FROM properties WHERE id = CAST(:pid AS uuid)"),
                    {"pid": property_id},
                )
            ).mappings().first()
            if prop is None:
                raise ValueError("Bien introuvable dans cette agence.")

            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO offers (
                          agency_id, property_id, advisor_user_id, asking_price, offered_price,
                          property_title, buyer_name, stage, notary_target_date, status
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:pid AS uuid), CAST(:uid AS uuid), :asking, :offered,
                          :ptitle, :buyer, :stage, :nt_date, :status_lbl
                        )
                        RETURNING id, agency_id, property_id, property_title, buyer_name,
                                  offered_price, stage, notary_target_date, created_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "pid": property_id,
                        "uid": ctx.user_id,
                        "asking": int(prop.get("price_euro") or offer_euro),
                        "offered": offer_euro,
                        "ptitle": str(prop["title"])[:180],
                        "buyer": clean_buyer,
                        "stage": stage.value,
                        "nt_date": target_date,
                        "status_lbl": f"Étape {stage.value}",
                    },
                )
            ).mappings().one()
            return self._row_to_transaction(row)

    async def update_transaction_stage(
        self,
        ctx: TenantContext,
        *,
        tx_id: str,
        stage: TransactionStage,
    ) -> PortfolioTransaction:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        UPDATE offers
                        SET stage = :st,
                            status = :status_lbl,
                            updated_at = now()
                        WHERE id = CAST(:tid AS uuid)
                        RETURNING id, agency_id, property_id, property_title, buyer_name,
                                  offered_price, stage, notary_target_date, created_at
                        """
                    ),
                    {"tid": tx_id, "st": stage.value, "status_lbl": f"Étape {stage.value}"},
                )
            ).mappings().first()
            if row is None:
                raise ValueError("Transaction introuvable dans cette agence.")
            tx = self._row_to_transaction(row)
            if stage in (TransactionStage.COMPROMIS, TransactionStage.ACTE):
                task_title = (
                    f"Ouvrir dossier notaire & Pré-État Daté L.721-2 — {tx.property_title}"
                    if stage == TransactionStage.COMPROMIS
                    else f"Préparer acte définitif & remise des clés — {tx.property_title}"
                )
                await session.execute(
                    self._text(
                        """
                        INSERT INTO follow_up_tasks (
                          agency_id, created_by_user_id, assigned_to_user_id,
                          title, contact_name, reason, channel, due_at, priority, status, auto_generated
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:uid AS uuid), CAST(:uid AS uuid),
                          :title, :cname, :reason, 'EMAIL', now() + INTERVAL '1 day', 'HIGH', 'OPEN', true
                        )
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "title": task_title[:200],
                        "cname": tx.buyer_name[:140],
                        "reason": f"Passage au stade {stage.value} ({tx.offer_euro} €) — objectif notaire {tx.notary_target_date}."[:400],
                    },
                )
                await session.execute(
                    self._text(
                        """
                        INSERT INTO agency_notifications (
                          agency_id, event_type, severity, title, detail, link_section, target_id
                        ) VALUES (
                          CAST(:aid AS uuid), :etype, 'HIGH', :title, :detail, 'documents', :tid
                        )
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "etype": f"TRANSACTION_{stage.value}",
                        "title": task_title[:200],
                        "detail": f"Acquéreur : {tx.buyer_name} • Objectif notaire : {tx.notary_target_date}."[:280],
                        "tid": tx.id,
                    },
                )
            return tx

    # --- LEADS PUBLICS ENTRANTS (RGPD + SECURITY DEFINER) ---
    async def submit_public_lead(
        self,
        *,
        agency_id: str | None = None,
        lead_kind: LeadKind,
        country_code: CountryCode = CountryCode.FR,
        full_name: str = "",
        email: str,
        phone: str = "",
        city: str = "",
        budget_euro: int = 0,
        min_rooms: int = 1,
        search_transport_mode: str = "TRAM",
        max_travel_minutes: int = 30,
        horizon: str = "",
        property_title: str = "",
        notes: str = "",
        rgpd_consent_granted: bool,
    ) -> PublicInboundLead:
        if rgpd_consent_granted is not True:
            raise ValueError("Le consentement explicite RGPD (Art. 6.1.a) est obligatoire.")
        clean_email = InputSanitizer.sanitize_text(email, 160).lower()
        if "@" not in clean_email or "." not in clean_email.split("@")[-1]:
            raise ValueError("Adresse e-mail valide obligatoire.")

        target_uuid: str | None = None
        if agency_id and agency_id.strip():
            try:
                target_uuid = str(uuid.UUID(agency_id.strip()))
            except ValueError:
                target_uuid = None

        display_name = InputSanitizer.sanitize_text(full_name, 140) or clean_email.split("@")[0]
        clean_phone = InputSanitizer.sanitize_text(phone, 40)
        clean_city = InputSanitizer.sanitize_text(city, 120)
        clean_horizon = InputSanitizer.sanitize_text(horizon, 80)
        clean_prop_title = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(property_title, 180))
        clean_notes = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(notes, 1500))
        clean_mode = InputSanitizer.sanitize_text(search_transport_mode, 24) or "TRAM"
        safe_rooms = max(1, min(30, min_rooms))
        safe_minutes = max(5, min(240, max_travel_minutes))

        async with self._sf() as session:
            async with session.begin():
                row = (
                    await session.execute(
                        self._text(
                            """
                            SELECT lead_id, agency_id, crm_contact_id, created_at
                            FROM monty_submit_public_lead(
                              CAST(:aid AS uuid),
                              :lead_kind,
                              :cc,
                              :full_name,
                              :email,
                              :phone,
                              :city,
                              :budget,
                              :rooms,
                              :mode,
                              :minutes,
                              :horizon,
                              :prop_title,
                              :notes,
                              :consent
                            )
                            """
                        ),
                        {
                            "aid": target_uuid,
                            "lead_kind": lead_kind.value,
                            "cc": country_code.value,
                            "full_name": display_name,
                            "email": clean_email,
                            "phone": clean_phone,
                            "city": clean_city,
                            "budget": max(0, budget_euro),
                            "rooms": safe_rooms,
                            "mode": clean_mode,
                            "minutes": safe_minutes,
                            "horizon": clean_horizon,
                            "prop_title": clean_prop_title,
                            "notes": clean_notes,
                            "consent": True,
                        },
                    )
                ).mappings().one()

        created_val = row.get("created_at") or datetime.now(timezone.utc)
        created_iso = created_val.isoformat() if hasattr(created_val, "isoformat") else str(created_val)
        return PublicInboundLead(
            id=str(row["lead_id"]),
            agency_id=str(row["agency_id"]) if row.get("agency_id") else None,
            crm_contact_id=str(row["crm_contact_id"]) if row.get("crm_contact_id") else None,
            lead_kind=lead_kind,
            country_code=country_code,
            full_name=display_name,
            email=clean_email,
            phone=clean_phone,
            city=clean_city,
            budget_euro=max(0, budget_euro),
            min_rooms=safe_rooms,
            search_transport_mode=clean_mode,
            max_travel_minutes=safe_minutes,
            horizon=clean_horizon,
            property_title=clean_prop_title,
            notes=clean_notes,
            rgpd_consent_granted=True,
            rgpd_consent_at=created_iso,
            status=LeadStatus.NEW,
            created_at=created_iso,
        )

    async def list_public_leads(
        self,
        ctx: TenantContext,
        *,
        status: LeadStatus | None = None,
    ) -> list[PublicInboundLead]:
        ctx.require_permission(Permission.CRM_READ)
        async with self._open_tenant_session(ctx) as session:
            if status is not None:
                rows = (
                    await session.execute(
                        self._text(
                            """
                            SELECT id, agency_id, crm_contact_id, lead_kind, country_code,
                                   full_name, email::text AS email, phone, city, budget_euro,
                                   min_rooms, search_transport_mode, max_travel_minutes,
                                   horizon, property_title, notes, rgpd_consent_granted,
                                   rgpd_consent_at, status, created_at
                            FROM public_inbound_leads
                            WHERE status = :st
                            ORDER BY created_at DESC
                            LIMIT 200
                            """
                        ),
                        {"st": status.value},
                    )
                ).mappings().all()
            else:
                rows = (
                    await session.execute(
                        self._text(
                            """
                            SELECT id, agency_id, crm_contact_id, lead_kind, country_code,
                                   full_name, email::text AS email, phone, city, budget_euro,
                                   min_rooms, search_transport_mode, max_travel_minutes,
                                   horizon, property_title, notes, rgpd_consent_granted,
                                   rgpd_consent_at, status, created_at
                            FROM public_inbound_leads
                            ORDER BY created_at DESC
                            LIMIT 200
                            """
                        )
                    )
                ).mappings().all()
            return [self._row_to_lead(r) for r in rows]

    async def update_public_lead_status(
        self,
        ctx: TenantContext,
        *,
        lead_id: str,
        status: LeadStatus,
    ) -> PublicInboundLead:
        ctx.require_permission(Permission.CRM_WRITE)
        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        UPDATE public_inbound_leads
                        SET status = :st, updated_at = now()
                        WHERE id = CAST(:lid AS uuid)
                        RETURNING id, agency_id, crm_contact_id, lead_kind, country_code,
                                  full_name, email::text AS email, phone, city, budget_euro,
                                  min_rooms, search_transport_mode, max_travel_minutes,
                                  horizon, property_title, notes, rgpd_consent_granted,
                                  rgpd_consent_at, status, created_at
                        """
                    ),
                    {"lid": lead_id, "st": status.value},
                )
            ).mappings().first()
            if row is None:
                raise ValueError("Demande entrante introuvable dans cette agence.")
            return self._row_to_lead(row)
