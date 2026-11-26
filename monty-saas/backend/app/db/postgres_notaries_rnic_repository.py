from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
import json
import re
import secrets
from typing import Any, Callable
import uuid

from app.core.config import Settings
from app.core.rbac import Permission, TenantContext
from app.core.security import InputSanitizer
from app.domain.notaries_service import (
    AgencyNotaryContact,
    NotaryTransmissionRecord,
    build_notary_transmission_eml,
    dispatch_notary_email_if_configured,
)
from app.domain.rnic_copro_service import RnicPassRecord


def _default_sql_text(sql: str) -> Any:
    from sqlalchemy import text

    return text(sql)


class PostgresNotariesRnicRepository:
    """Persistance PostgreSQL RLS pour Notaires d'Agence, Transmissions Dossiers & Pass RNIC."""

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
    def _row_to_notary(row: Any) -> AgencyNotaryContact:
        email = str(row.get("email") or "").lower()
        created = row.get("created_at")
        return AgencyNotaryContact(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]),
            siret=str(row.get("siret") or ""),
            office_name=str(row["office_name"]),
            notary_name=str(row.get("notary_name") or ""),
            email=email,
            phone=str(row.get("phone") or ""),
            street_address=str(row.get("street_address") or ""),
            postal_code=str(row.get("postal_code") or ""),
            city=str(row.get("city") or ""),
            department_code=str(row.get("department_code") or ""),
            source=str(row.get("source") or "DINUM_API_GOUV"),
            email_verified=bool(row.get("email_verified", False)),
            is_official_notaire_domain=email.endswith("@notaires.fr"),
            notes=str(row.get("notes") or ""),
            created_at=created.isoformat() if hasattr(created, "isoformat") else str(created or ""),
        )

    @staticmethod
    def _row_to_transmission(row: Any) -> NotaryTransmissionRecord:
        created = row.get("created_at")
        raw_chk = row.get("checklist_items")
        if isinstance(raw_chk, str):
            try:
                chk = json.loads(raw_chk)
            except Exception:
                chk = []
        else:
            chk = list(raw_chk or [])
        return NotaryTransmissionRecord(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]),
            notary_contact_id=str(row["notary_contact_id"]) if row.get("notary_contact_id") else None,
            transaction_id=str(row["transaction_id"]) if row.get("transaction_id") else None,
            property_id=str(row["property_id"]) if row.get("property_id") else None,
            dossier_ref=str(row["dossier_ref"]),
            transmission_type=str(row["transmission_type"]),
            recipient_email=str(row.get("recipient_email") or ""),
            recipient_office=str(row["recipient_office"]),
            recipient_address=str(row.get("recipient_address") or ""),
            subject=str(row["subject"]),
            checklist_items=chk,
            eml_content=str(row["eml_content"]),
            delivery_status=str(row["delivery_status"]),
            delivery_provider_id=str(row.get("delivery_provider_id") or ""),
            created_at=created.isoformat() if hasattr(created, "isoformat") else str(created or ""),
        )

    async def list_notaries(self, ctx: TenantContext) -> list[AgencyNotaryContact]:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, siret, office_name, notary_name, email::text AS email,
                               phone, street_address, postal_code, city, department_code,
                               source, email_verified, notes, created_at
                        FROM agency_notary_contacts
                        ORDER BY created_at DESC
                        LIMIT 200
                        """
                    )
                )
            ).mappings().all()
            return [self._row_to_notary(r) for r in rows]

    async def save_notary(
        self,
        ctx: TenantContext,
        *,
        siret: str = "",
        office_name: str,
        notary_name: str = "",
        email: str = "",
        phone: str = "",
        street_address: str = "",
        postal_code: str = "",
        city: str = "",
        source: str = "DINUM_API_GOUV",
        notes: str = "",
    ) -> AgencyNotaryContact:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        clean_office = InputSanitizer.sanitize_text(office_name, 180)
        if len(clean_office) < 2:
            raise ValueError("Le nom de l'étude notariale est obligatoire.")
        clean_email = InputSanitizer.sanitize_text(email, 160).lower()
        if clean_email and ("@" not in clean_email or "." not in clean_email.split("@")[-1]):
            raise ValueError("Adresse e-mail notariale invalide.")
        clean_cp = re.sub(r"[^0-9]", "", postal_code or "")[:5]
        dept = clean_cp[:3] if clean_cp.startswith(("97", "98")) and len(clean_cp) >= 3 else clean_cp[:2]

        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO agency_notary_contacts (
                          agency_id, siret, office_name, notary_name, email, phone,
                          street_address, postal_code, city, department_code,
                          source, email_verified, notes, created_by_user_id
                        ) VALUES (
                          CAST(:aid AS uuid), :siret, :office, :notary, :email, :phone,
                          :street, :cp, :city, :dept,
                          :source, :verified, :notes, CAST(NULLIF(:uid, '') AS uuid)
                        )
                        ON CONFLICT (agency_id, office_name, postal_code) DO UPDATE SET
                          siret = COALESCE(NULLIF(EXCLUDED.siret, ''), agency_notary_contacts.siret),
                          notary_name = COALESCE(NULLIF(EXCLUDED.notary_name, ''), agency_notary_contacts.notary_name),
                          email = COALESCE(NULLIF(EXCLUDED.email::text, ''), agency_notary_contacts.email::text)::citext,
                          phone = COALESCE(NULLIF(EXCLUDED.phone, ''), agency_notary_contacts.phone),
                          street_address = COALESCE(NULLIF(EXCLUDED.street_address, ''), agency_notary_contacts.street_address),
                          city = COALESCE(NULLIF(EXCLUDED.city, ''), agency_notary_contacts.city),
                          department_code = COALESCE(NULLIF(EXCLUDED.department_code, ''), agency_notary_contacts.department_code),
                          email_verified = (COALESCE(NULLIF(EXCLUDED.email::text, ''), agency_notary_contacts.email::text) <> ''),
                          notes = COALESCE(NULLIF(EXCLUDED.notes, ''), agency_notary_contacts.notes),
                          updated_at = now()
                        RETURNING id, agency_id, siret, office_name, notary_name, email::text AS email,
                                  phone, street_address, postal_code, city, department_code,
                                  source, email_verified, notes, created_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "siret": re.sub(r"[^0-9]", "", siret or "")[:14],
                        "office": clean_office,
                        "notary": InputSanitizer.sanitize_text(notary_name, 160),
                        "email": clean_email,
                        "phone": InputSanitizer.sanitize_text(phone, 40),
                        "street": InputSanitizer.sanitize_text(street_address, 240),
                        "cp": clean_cp,
                        "city": InputSanitizer.sanitize_text(city, 100),
                        "dept": dept,
                        "source": InputSanitizer.sanitize_text(source, 40) or "DINUM_API_GOUV",
                        "verified": bool(clean_email),
                        "notes": InputSanitizer.sanitize_text(notes, 600),
                        "uid": ctx.user_id,
                    },
                )
            ).mappings().one()
            return self._row_to_notary(row)

    async def create_transmission(
        self,
        ctx: TenantContext,
        *,
        notary_contact_id: str | None = None,
        transaction_id: str | None = None,
        property_id: str | None = None,
        dossier_ref: str,
        transmission_type: str,
        recipient_email: str,
        recipient_office: str,
        recipient_address: str,
        property_title: str,
        property_address: str,
        seller_name: str,
        buyer_name: str,
        agreed_price_euro: int,
        target_date: str,
        checklist_items: list[str],
        notes: str = "",
        pre_etat_date_html: str = "",
        send_now: bool = False,
        settings: Settings | None = None,
    ) -> NotaryTransmissionRecord:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        if transmission_type not in ("OUVERTURE_DOSSIER", "SIGNATURE_COMPROMIS", "COMPLEMENT_ALUR"):
            raise ValueError("Type de transmission notariale invalide.")

        subject, eml = build_notary_transmission_eml(
            from_email=(settings.smtp_from_email if settings else "") or "dossier@monty-immobilier.fr",
            recipient_email=recipient_email,
            recipient_office=recipient_office,
            recipient_address=recipient_address,
            dossier_ref=dossier_ref,
            transmission_type=transmission_type,
            property_title=property_title,
            property_address=property_address,
            seller_name=seller_name,
            buyer_name=buyer_name,
            agreed_price_euro=agreed_price_euro,
            target_date=target_date,
            checklist_items=checklist_items,
            notes=notes,
            pre_etat_date_html=pre_etat_date_html,
        )
        if send_now and settings is not None:
            delivery_status, provider_id = dispatch_notary_email_if_configured(
                recipient_email=recipient_email,
                subject=subject,
                eml_content=eml,
                settings=settings,
            )
        else:
            delivery_status, provider_id = "READY_EML_DOWNLOAD", ""

        def _safe_uuid(val: str | None) -> str:
            if not val:
                return ""
            try:
                return str(uuid.UUID(val))
            except ValueError:
                return ""

        async with self._open_tenant_session(ctx) as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        INSERT INTO notary_transmissions (
                          agency_id, notary_contact_id, transaction_id, property_id,
                          dossier_ref, transmission_type, recipient_email, recipient_office,
                          recipient_address, subject, checklist_items, eml_content,
                          delivery_status, delivery_provider_id, created_by_user_id
                        ) VALUES (
                          CAST(:aid AS uuid),
                          CAST(NULLIF(:ncid, '') AS uuid),
                          CAST(NULLIF(:txid, '') AS uuid),
                          CAST(NULLIF(:pid, '') AS uuid),
                          :dref, :ttype, :remail, :roffice,
                          :raddr, :subj, CAST(:chk AS jsonb), :eml,
                          :dstatus, :dpid, CAST(NULLIF(:uid, '') AS uuid)
                        )
                        RETURNING id, agency_id, notary_contact_id, transaction_id, property_id,
                                  dossier_ref, transmission_type, recipient_email::text AS recipient_email,
                                  recipient_office, recipient_address, subject, checklist_items,
                                  eml_content, delivery_status, delivery_provider_id, created_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "ncid": _safe_uuid(notary_contact_id),
                        "txid": _safe_uuid(transaction_id),
                        "pid": _safe_uuid(property_id),
                        "dref": InputSanitizer.sanitize_text(dossier_ref, 80),
                        "ttype": transmission_type,
                        "remail": InputSanitizer.sanitize_text(recipient_email, 160).lower(),
                        "roffice": InputSanitizer.sanitize_text(recipient_office, 180),
                        "raddr": InputSanitizer.sanitize_text(recipient_address, 260),
                        "subj": subject[:240],
                        "chk": json.dumps([InputSanitizer.sanitize_text(c, 160) for c in checklist_items]),
                        "eml": eml,
                        "dstatus": delivery_status,
                        "dpid": provider_id[:160],
                        "uid": ctx.user_id,
                    },
                )
            ).mappings().one()
            await session.execute(
                self._text(
                    """
                    INSERT INTO agency_notifications (
                      agency_id, event_type, severity, title, detail, link_section, target_id
                    ) VALUES (
                      CAST(:aid AS uuid), 'NOTARY_DOSSIER_SENT', 'SUCCESS',
                      :title, :detail, 'documents', :tid
                    )
                    """
                ),
                {
                    "aid": ctx.agency_id,
                    "title": f"Dossier notaire préparé : {InputSanitizer.sanitize_text(dossier_ref, 80)}"[:200],
                    "detail": f"{InputSanitizer.sanitize_text(recipient_office, 120)} ({delivery_status})"[:280],
                    "tid": str(row["id"]),
                },
            )
            return self._row_to_transmission(row)

    async def list_transmissions(self, ctx: TenantContext) -> list[NotaryTransmissionRecord]:
        ctx.require_permission(Permission.PROPERTY_READ)
        async with self._open_tenant_session(ctx) as session:
            rows = (
                await session.execute(
                    self._text(
                        """
                        SELECT id, agency_id, notary_contact_id, transaction_id, property_id,
                               dossier_ref, transmission_type, recipient_email::text AS recipient_email,
                               recipient_office, recipient_address, subject, checklist_items,
                               eml_content, delivery_status, delivery_provider_id, created_at
                        FROM notary_transmissions
                        ORDER BY created_at DESC
                        LIMIT 100
                        """
                    )
                )
            ).mappings().all()
            return [self._row_to_transmission(r) for r in rows]

    # --- RNIC PASSES (Paiement Particuliers) ---
    async def issue_active_rnic_pass(
        self,
        *,
        user_id: str | None,
        buyer_email: str,
        stripe_session_id: str,
        pass_token: str = "",
        amount_euro: int = 19,
        ttl_days: int = 30,
    ) -> RnicPassRecord:
        now = datetime.now(timezone.utc)
        expires = now + timedelta(days=ttl_days)
        token_val = pass_token.strip() or f"rnic_pass_{secrets.token_urlsafe(24)}"
        uid_val = ""
        if user_id:
            try:
                uid_val = str(uuid.UUID(user_id))
            except ValueError:
                uid_val = ""

        async with self._sf() as session:
            async with session.begin():
                row = (
                    await session.execute(
                        self._text(
                            """
                            INSERT INTO rnic_access_passes (
                              user_id, buyer_email, pass_token, stripe_session_id, amount_euro, status, expires_at
                            ) VALUES (
                              CAST(NULLIF(:uid, '') AS uuid), :email, :token, :sid, :amt, 'ACTIVE', :exp
                            )
                            ON CONFLICT (stripe_session_id) DO UPDATE SET
                              status = 'ACTIVE',
                              expires_at = EXCLUDED.expires_at
                            RETURNING id, user_id, buyer_email::text AS buyer_email, pass_token,
                                      stripe_session_id, amount_euro, status, expires_at, created_at
                            """
                        ),
                        {
                            "uid": uid_val,
                            "email": InputSanitizer.sanitize_text(buyer_email, 160).lower(),
                            "token": token_val,
                            "sid": stripe_session_id,
                            "amt": amount_euro,
                            "exp": expires,
                        },
                    )
                ).mappings().one()
                return RnicPassRecord(
                    id=str(row["id"]),
                    user_id=str(row["user_id"]) if row.get("user_id") else None,
                    buyer_email=str(row.get("buyer_email") or ""),
                    pass_token=str(row["pass_token"]),
                    stripe_session_id=str(row.get("stripe_session_id") or ""),
                    amount_euro=int(row["amount_euro"]),
                    status=str(row["status"]),
                    expires_at=row["expires_at"].isoformat() if hasattr(row["expires_at"], "isoformat") else str(row["expires_at"]),
                    created_at=row["created_at"].isoformat() if hasattr(row["created_at"], "isoformat") else str(row["created_at"]),
                )

    async def has_active_rnic_pass(self, *, pass_token: str = "", user_id: str | None = None) -> bool:
        uid_val = ""
        if user_id:
            try:
                uid_val = str(uuid.UUID(user_id))
            except ValueError:
                uid_val = ""
        if not pass_token and not uid_val:
            return False

        async with self._sf() as session:
            row = (
                await session.execute(
                    self._text(
                        """
                        SELECT 1
                        FROM rnic_access_passes
                        WHERE status = 'ACTIVE'
                          AND expires_at > now()
                          AND (
                            (:tok <> '' AND pass_token = :tok)
                            OR (:uid <> '' AND user_id = CAST(:uid AS uuid))
                          )
                        LIMIT 1
                        """
                    ),
                    {"tok": pass_token.strip(), "uid": uid_val},
                )
            ).first()
            return row is not None
