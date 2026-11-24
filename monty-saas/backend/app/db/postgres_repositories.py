from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
import json
import re
import secrets
import uuid

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.core.config import Settings
from app.core.rbac import ROLE_PERMISSIONS, MontyRole, Permission, TenantContext
from app.core.rgpd import (
    LEGAL_BASIS_CONSENT,
    build_rgpd_portability_export,
    compute_retention_until,
    is_retention_expired,
    redact_direct_pii_for_ai,
)
from app.core.security import (
    InputSanitizer,
    create_access_token,
    hash_password,
    verify_password,
)
from app.db.session import tenant_db_session
from app.domain.auth_and_billing import AuthSessionResult, InviteRecord, SAAS_PLANS
from app.domain.country_packs import CountryCode, get_country_pack
from app.domain.crm_service import (
    AdvisorPrivateNote,
    ContactExchange,
    ContactRole,
    CrmContact,
    ExchangeChannel,
    FollowUpPriority,
    FollowUpTask,
)
from app.domain.marketplace_service import (
    ListingModerationStatus,
    MarketplaceListing,
    MarketplaceSearchHit,
    PublisherType,
    _resolve_city_geo,
)
from app.domain.real_estate_engines import (
    SearchPerimeterMode,
    TransportMode,
    estimate_sale_timeline,
    evaluate_property_access,
)


def _slugify(value: str) -> str:
    base = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return (base[:90] or "agence-monty") + f"-{secrets.token_hex(3)}"


def _normalize_phone_last9(phone: str) -> str:
    digits = "".join(ch for ch in phone if ch.isdigit())
    return digits[-9:] if len(digits) >= 9 else digits


async def write_audit_log(
    session: AsyncSession,
    *,
    agency_id: str,
    actor_user_id: str | None,
    actor_role: str,
    action_type: str,
    target_type: str,
    target_id: str,
    detail: str,
) -> None:
    await session.execute(
        text(
            """
            INSERT INTO audit_logs (agency_id, actor_user_id, actor_role, action_type, target_type, target_id, detail)
            VALUES (CAST(:agency_id AS uuid), CAST(NULLIF(:actor_user_id, '') AS uuid), :actor_role, :action_type, :target_type, :target_id, :detail)
            """
        ),
        {
            "agency_id": agency_id,
            "actor_user_id": actor_user_id or "",
            "actor_role": actor_role,
            "action_type": action_type,
            "target_type": target_type,
            "target_id": target_id,
            "detail": InputSanitizer.sanitize_text(detail, 500),
        },
    )


class PostgresAuthRepository:
    """Authentification & gestion d'équipe persistées dans PostgreSQL avec fonctions SECURITY DEFINER + RLS."""

    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._sf = session_factory

    async def register_agency_director(
        self,
        *,
        agency_name: str,
        email: str,
        password: str,
        display_name: str,
        city: str = "Lyon",
        country_code: CountryCode = CountryCode.FR,
        settings: Settings,
    ) -> AuthSessionResult:
        clean_email = InputSanitizer.sanitize_text(email, 160).lower()
        clean_agency = InputSanitizer.sanitize_text(agency_name, 120)
        clean_name = InputSanitizer.sanitize_text(display_name, 120)
        clean_city = InputSanitizer.sanitize_text(city, 80) or ("Lisboa" if country_code == CountryCode.PT else "Lyon")
        if "@" not in clean_email or not clean_agency or not clean_name:
            raise ValueError("Nom d'agence, nom complet et e-mail valide obligatoires.")
        pw_hash = hash_password(password)

        async with self._sf() as session:
            async with session.begin():
                existing = (
                    await session.execute(text("SELECT 1 FROM users WHERE email = :email"), {"email": clean_email})
                ).first()
                if existing is not None:
                    raise ValueError("Un compte existe déjà pour cet e-mail.")
                row = (
                    await session.execute(
                        text(
                            """
                            SELECT user_id::text, agency_id::text, role
                            FROM monty_auth_register_agency(
                              :agency_name, :agency_slug, :city, :country_code, :email, :password_hash, :display_name
                            )
                            """
                        ),
                        {
                            "agency_name": clean_agency,
                            "agency_slug": _slugify(clean_agency),
                            "city": clean_city,
                            "country_code": country_code.value,
                            "email": clean_email,
                            "password_hash": pw_hash,
                            "display_name": clean_name,
                        },
                    )
                ).mappings().one()

                await session.execute(
                    text("SELECT set_config('app.current_agency_id', :aid, true)"),
                    {"aid": row["agency_id"]},
                )
                await session.execute(
                    text("SELECT set_config('app.current_user_id', :uid, true)"),
                    {"uid": row["user_id"]},
                )
                await session.execute(
                    text("SELECT set_config('app.current_role', 'DIRECTOR', true)"),
                )
                await write_audit_log(
                    session,
                    agency_id=row["agency_id"],
                    actor_user_id=row["user_id"],
                    actor_role="DIRECTOR",
                    action_type="AGENCY_REGISTERED",
                    target_type="AGENCY",
                    target_id=row["agency_id"],
                    detail=f"Création agence {clean_agency} ({country_code.value})",
                )

        token = create_access_token(
            subject_user_id=row["user_id"],
            agency_id=row["agency_id"],
            role=MontyRole.DIRECTOR.value,
            secret=settings.effective_jwt_secret,
            ttl_seconds=settings.jwt_ttl_minutes * 60,
        )
        return AuthSessionResult(
            access_token=token,
            user_id=row["user_id"],
            agency_id=row["agency_id"],
            agency_name=clean_agency,
            display_name=clean_name,
            email=clean_email,
            role=MontyRole.DIRECTOR,
            permissions=sorted(p.value for p in ROLE_PERMISSIONS[MontyRole.DIRECTOR]),
        )

    async def login(self, *, email: str, password: str, settings: Settings) -> AuthSessionResult:
        clean_email = InputSanitizer.sanitize_text(email, 160).lower()
        async with self._sf() as session:
            async with session.begin():
                row = (
                    await session.execute(
                        text(
                            """
                            SELECT user_id::text, email, display_name, password_hash, is_active, is_super_admin, agency_id::text, role
                            FROM monty_auth_lookup_login(:email)
                            """
                        ),
                        {"email": clean_email},
                    )
                ).mappings().first()
                if row is None or not row["is_active"] or not verify_password(password, row["password_hash"]):
                    raise ValueError("Identifiants invalides.")

                await session.execute(
                    text("UPDATE users SET last_login_at = now() WHERE id = CAST(:uid AS uuid)"),
                    {"uid": row["user_id"]},
                )
                role_enum = MontyRole.SUPER_ADMIN if row["is_super_admin"] else MontyRole(row["role"])

        token = create_access_token(
            subject_user_id=row["user_id"],
            agency_id=row["agency_id"],
            role=role_enum.value,
            secret=settings.effective_jwt_secret,
            ttl_seconds=settings.jwt_ttl_minutes * 60,
        )
        return AuthSessionResult(
            access_token=token,
            user_id=row["user_id"],
            agency_id=row["agency_id"],
            agency_name="Agence Monty",
            display_name=row["display_name"],
            email=row["email"],
            role=role_enum,
            permissions=sorted(p.value for p in ROLE_PERMISSIONS[role_enum]),
        )

    async def create_invite(
        self,
        ctx: TenantContext,
        *,
        target_email: str,
        target_name: str,
        role: MontyRole,
        ttl_hours: int = 72,
    ) -> InviteRecord:
        ctx.require_permission(Permission.TEAM_MANAGE)
        if role == MontyRole.SUPER_ADMIN:
            raise PermissionError("Impossible d'inviter un rôle SUPER_ADMIN.")
        clean_email = InputSanitizer.sanitize_text(target_email, 160).lower()
        clean_name = InputSanitizer.sanitize_text(target_name, 120) or clean_email.split("@")[0]
        if "@" not in clean_email:
            raise ValueError("Adresse e-mail cible invalide.")

        invite_code = secrets.token_urlsafe(24)
        expires_at = datetime.now(timezone.utc) + timedelta(hours=ttl_hours)

        async with tenant_db_session(self._sf, ctx) as session:
            agency_row = (
                await session.execute(
                    text("SELECT seat_limit, plan_code FROM agencies WHERE id = CAST(:aid AS uuid)"),
                    {"aid": ctx.agency_id},
                )
            ).mappings().one()
            active_count = (
                await session.execute(
                    text(
                        "SELECT count(*) FROM agency_memberships WHERE agency_id = CAST(:aid AS uuid) AND status = 'ACTIVE'"
                    ),
                    {"aid": ctx.agency_id},
                )
            ).scalar_one()
            if int(active_count) >= int(agency_row["seat_limit"]):
                raise ValueError(
                    f"Limite de sièges atteinte pour le forfait {agency_row['plan_code']} ({agency_row['seat_limit']})."
                )
            inv_row = (
                await session.execute(
                    text(
                        """
                        INSERT INTO agency_invites (agency_id, invite_code, target_email, target_name, role, invited_by_user_id, expires_at)
                        VALUES (CAST(:aid AS uuid), :code, :email, :name, :role, CAST(:actor AS uuid), :expires_at)
                        RETURNING id::text, expires_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "code": invite_code,
                        "email": clean_email,
                        "name": clean_name,
                        "role": role.value,
                        "actor": ctx.user_id,
                        "expires_at": expires_at,
                    },
                )
            ).mappings().one()
            await write_audit_log(
                session,
                agency_id=ctx.agency_id,
                actor_user_id=ctx.user_id,
                actor_role=ctx.role.value,
                action_type="TEAM_INVITE_CREATED",
                target_type="INVITE",
                target_id=inv_row["id"],
                detail=f"Invitation {role.value} pour {clean_email}",
            )

        return InviteRecord(
            id=inv_row["id"],
            agency_id=ctx.agency_id,
            invite_code=invite_code,
            target_email=clean_email,
            target_name=clean_name,
            role=role,
            invited_by_user_id=ctx.user_id,
            expires_at=expires_at.isoformat(),
        )

    async def claim_invite(
        self,
        *,
        invite_code: str,
        password: str,
        display_name: str = "",
        settings: Settings,
    ) -> AuthSessionResult:
        pw_hash = hash_password(password)
        clean_name = InputSanitizer.sanitize_text(display_name, 120)
        async with self._sf() as session:
            async with session.begin():
                row = (
                    await session.execute(
                        text(
                            """
                            SELECT user_id::text, agency_id::text, role, email
                            FROM monty_auth_claim_invite(:code, :pw_hash, :display_name)
                            """
                        ),
                        {"code": invite_code.strip(), "pw_hash": pw_hash, "display_name": clean_name},
                    )
                ).mappings().one()
        role_enum = MontyRole(row["role"])
        token = create_access_token(
            subject_user_id=row["user_id"],
            agency_id=row["agency_id"],
            role=role_enum.value,
            secret=settings.effective_jwt_secret,
            ttl_seconds=settings.jwt_ttl_minutes * 60,
        )
        return AuthSessionResult(
            access_token=token,
            user_id=row["user_id"],
            agency_id=row["agency_id"],
            agency_name="Agence Monty",
            display_name=clean_name or row["email"],
            email=row["email"],
            role=role_enum,
            permissions=sorted(p.value for p in ROLE_PERMISSIONS[role_enum]),
        )

    async def list_team_members(self, ctx: TenantContext) -> list[dict]:
        ctx.require_permission(Permission.TEAM_MANAGE)
        async with tenant_db_session(self._sf, ctx) as session:
            rows = (
                await session.execute(
                    text(
                        """
                        SELECT m.user_id::text, u.email::text, u.display_name, m.role, m.status
                        FROM agency_memberships m
                        JOIN users u ON u.id = m.user_id
                        ORDER BY m.created_at ASC
                        """
                    )
                )
            ).mappings().all()
            return [dict(r) for r in rows]


class PostgresCrmRepository:
    """Source de vérité PostgreSQL 16 RLS pour CRM, notes privées cloisonnées, échanges, relances et RGPD."""

    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._sf = session_factory

    @staticmethod
    def _row_to_contact(row) -> CrmContact:
        raw_role = row["contact_type"]
        role_map = {
            "Acquéreur": ContactRole.BUYER,
            "Vendeur": ContactRole.SELLER,
            "Partenaire": ContactRole.PARTNER,
        }
        try:
            role_val = ContactRole(raw_role)
        except ValueError:
            role_val = role_map.get(raw_role, ContactRole.LEAD)
        return CrmContact(
            id=str(row["id"]),
            agency_id=str(row["agency_id"]),
            assigned_user_id=str(row["assigned_to_user_id"]),
            country_code=CountryCode(row["country_code"]),
            full_name=row["full_name"],
            email=row["email"] or "",
            phone=row["phone"] or "",
            role=role_val,
            city=row["sector"] or "",
            budget_euro=int(row["budget_max"] or 0),
            legal_basis=row["rgpd_legal_basis"],
            consent_granted=bool(row["rgpd_consent_granted"]),
            retention_until=row["rgpd_retention_until"].isoformat() if row["rgpd_retention_until"] else "",
            created_at=row["created_at"].date().isoformat(),
            last_contact_at=(row["last_contacted_at"] or row["created_at"]).date().isoformat(),
            has_missing_docs=bool(row["has_missing_docs"]),
            pending_offer_days=int(row["pending_offer_days"] or 0),
            last_visit_days_ago=row["last_visit_days_ago"],
            is_anonymized=bool(row["is_anonymized"]),
        )

    async def list_contacts(
        self,
        ctx: TenantContext,
        *,
        role: ContactRole | None = None,
        country_code: CountryCode | None = None,
        query: str = "",
    ) -> list[CrmContact]:
        ctx.require_permission(Permission.CRM_READ)
        clauses = ["archived = false"]
        params: dict[str, object] = {}
        if role is not None:
            clauses.append("contact_type = :role")
            params["role"] = role.value
        if country_code is not None:
            clauses.append("country_code = :country_code")
            params["country_code"] = country_code.value
        q = query.strip()
        if q:
            clauses.append("(full_name ILIKE :q OR email ILIKE :q OR sector ILIKE :q)")
            params["q"] = f"%{q}%"

        sql = f"""
            SELECT id, agency_id, assigned_to_user_id, country_code, full_name, email::text, phone,
                   contact_type, sector, budget_max, rgpd_legal_basis, rgpd_consent_granted,
                   rgpd_retention_until, created_at, last_contacted_at,
                   has_missing_docs, pending_offer_days, last_visit_days_ago, is_anonymized
            FROM crm_contacts
            WHERE {' AND '.join(clauses)}
            ORDER BY COALESCE(last_contacted_at, created_at) DESC
        """
        async with tenant_db_session(self._sf, ctx) as session:
            rows = (await session.execute(text(sql), params)).mappings().all()
            return [self._row_to_contact(r) for r in rows]

    async def create_contact(
        self,
        ctx: TenantContext,
        *,
        full_name: str,
        email: str = "",
        phone: str = "",
        role: ContactRole = ContactRole.LEAD,
        country_code: CountryCode = CountryCode.FR,
        city: str = "",
        budget_euro: int = 0,
        legal_basis: str = LEGAL_BASIS_CONSENT,
        consent_granted: bool = True,
        retention_until: str | None = None,
    ) -> CrmContact:
        ctx.require_permission(Permission.CRM_WRITE)
        pack = get_country_pack(country_code)
        safe_name = InputSanitizer.sanitize_text(full_name, 140)
        if not safe_name:
            raise ValueError("Le nom du contact est obligatoire.")
        safe_email = InputSanitizer.sanitize_text(email, 160).lower()
        safe_phone = InputSanitizer.sanitize_text(phone, 40)
        safe_city = InputSanitizer.sanitize_text(city, 100)
        safe_basis = InputSanitizer.sanitize_text(legal_basis, 100) or LEGAL_BASIS_CONSENT
        ret_date = date.fromisoformat(retention_until) if retention_until else compute_retention_until(safe_basis)

        async with tenant_db_session(self._sf, ctx) as session:
            row = (
                await session.execute(
                    text(
                        """
                        INSERT INTO crm_contacts (
                          agency_id, created_by_user_id, assigned_to_user_id, country_code,
                          full_name, email, phone, phone_normalized_last9, contact_type,
                          sector, budget_max, rgpd_consent_granted, rgpd_consent_date,
                          rgpd_legal_basis, rgpd_retention_until, last_contacted_at
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:uid AS uuid), CAST(:uid AS uuid), :country_code,
                          :full_name, :email, :phone, :phone_last9, :contact_type,
                          :sector, :budget_max, :consent, CURRENT_DATE,
                          :legal_basis, :ret_until, now()
                        )
                        RETURNING id, agency_id, assigned_to_user_id, country_code, full_name, email::text, phone,
                                  contact_type, sector, budget_max, rgpd_legal_basis, rgpd_consent_granted,
                                  rgpd_retention_until, created_at, last_contacted_at,
                                  has_missing_docs, pending_offer_days, last_visit_days_ago, is_anonymized
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "country_code": pack.country_code.value,
                        "full_name": safe_name,
                        "email": safe_email,
                        "phone": safe_phone,
                        "phone_last9": _normalize_phone_last9(safe_phone),
                        "contact_type": role.value,
                        "sector": safe_city,
                        "budget_max": max(0, int(budget_euro)),
                        "consent": consent_granted,
                        "legal_basis": safe_basis,
                        "ret_until": ret_date,
                    },
                )
            ).mappings().one()
            await write_audit_log(
                session,
                agency_id=ctx.agency_id,
                actor_user_id=ctx.user_id,
                actor_role=ctx.role.value,
                action_type="CRM_CONTACT_CREATED",
                target_type="CRM_CONTACT",
                target_id=str(row["id"]),
                detail=f"Création contact {safe_name} ({pack.country_code.value})",
            )
            return self._row_to_contact(row)

    async def list_private_notes(self, ctx: TenantContext, *, contact_id: str) -> list[AdvisorPrivateNote]:
        ctx.require_permission(Permission.PRIVATE_NOTES_READ)
        async with tenant_db_session(self._sf, ctx) as session:
            # Vérifie que le contact existe dans le tenant courant (RLS bloque les autres agences)
            c_row = (
                await session.execute(
                    text("SELECT id, agency_id FROM crm_contacts WHERE id = CAST(:cid AS uuid)"),
                    {"cid": contact_id},
                )
            ).mappings().first()
            if c_row is None:
                raise PermissionError("Contact introuvable ou hors périmètre agence.")
            rows = (
                await session.execute(
                    text(
                        """
                        SELECT id::text, agency_id::text, entity_id::text AS contact_id, author_user_id::text, content, created_at
                        FROM advisor_private_notes
                        WHERE entity_type = 'CRM_CONTACT' AND entity_id = CAST(:cid AS uuid)
                        ORDER BY created_at DESC
                        """
                    ),
                    {"cid": contact_id},
                )
            ).mappings().all()
            return [
                AdvisorPrivateNote(
                    id=r["id"],
                    agency_id=r["agency_id"],
                    contact_id=r["contact_id"],
                    author_user_id=r["author_user_id"],
                    content=r["content"],
                    created_at=r["created_at"].isoformat(),
                )
                for r in rows
                if ctx.can_read_private_note(author_user_id=r["author_user_id"], note_agency_id=r["agency_id"])
            ]

    async def add_private_note(self, ctx: TenantContext, *, contact_id: str, content: str) -> AdvisorPrivateNote:
        ctx.require_permission(Permission.PRIVATE_NOTES_WRITE)
        safe_content = InputSanitizer.sanitize_text(content, 2000)
        if not safe_content:
            raise ValueError("Contenu de note privée obligatoire.")
        async with tenant_db_session(self._sf, ctx) as session:
            c_row = (
                await session.execute(
                    text("SELECT id, agency_id::text FROM crm_contacts WHERE id = CAST(:cid AS uuid)"),
                    {"cid": contact_id},
                )
            ).mappings().first()
            if c_row is None:
                raise PermissionError("Contact introuvable ou hors périmètre agence.")
            row = (
                await session.execute(
                    text(
                        """
                        INSERT INTO advisor_private_notes (agency_id, entity_type, entity_id, author_user_id, content)
                        VALUES (CAST(:aid AS uuid), 'CRM_CONTACT', CAST(:cid AS uuid), CAST(:uid AS uuid), :content)
                        RETURNING id::text, agency_id::text, entity_id::text AS contact_id, author_user_id::text, content, created_at
                        """
                    ),
                    {
                        "aid": c_row["agency_id"],
                        "cid": contact_id,
                        "uid": ctx.user_id,
                        "content": safe_content,
                    },
                )
            ).mappings().one()
            return AdvisorPrivateNote(
                id=row["id"],
                agency_id=row["agency_id"],
                contact_id=row["contact_id"],
                author_user_id=row["author_user_id"],
                content=row["content"],
                created_at=row["created_at"].isoformat(),
            )

    async def log_exchange(
        self,
        ctx: TenantContext,
        *,
        contact_id: str,
        channel: ExchangeChannel,
        summary: str,
    ) -> ContactExchange:
        ctx.require_permission(Permission.CRM_WRITE)
        safe_summary = InputSanitizer.sanitize_text(summary, 1500)
        if not safe_summary:
            raise ValueError("Résumé d'échange obligatoire.")
        async with tenant_db_session(self._sf, ctx) as session:
            c_row = (
                await session.execute(
                    text("SELECT id, agency_id::text FROM crm_contacts WHERE id = CAST(:cid AS uuid)"),
                    {"cid": contact_id},
                )
            ).mappings().first()
            if c_row is None:
                raise PermissionError("Contact introuvable ou hors périmètre agence.")
            row = (
                await session.execute(
                    text(
                        """
                        INSERT INTO contact_exchanges (agency_id, contact_id, author_user_id, channel, summary)
                        VALUES (CAST(:aid AS uuid), CAST(:cid AS uuid), CAST(:uid AS uuid), :channel, :summary)
                        RETURNING id::text, agency_id::text, contact_id::text, author_user_id::text, channel, summary, occurred_at
                        """
                    ),
                    {
                        "aid": c_row["agency_id"],
                        "cid": contact_id,
                        "uid": ctx.user_id,
                        "channel": channel.value,
                        "summary": safe_summary,
                    },
                )
            ).mappings().one()
            await session.execute(
                text("UPDATE crm_contacts SET last_contacted_at = now(), updated_at = now() WHERE id = CAST(:cid AS uuid)"),
                {"cid": contact_id},
            )
            return ContactExchange(
                id=row["id"],
                agency_id=row["agency_id"],
                contact_id=row["contact_id"],
                author_user_id=row["author_user_id"],
                channel=ExchangeChannel(row["channel"]),
                summary=row["summary"],
                created_at=row["occurred_at"].isoformat(),
            )

    async def generate_automatic_followups(self, ctx: TenantContext) -> list[FollowUpTask]:
        contacts = await self.list_contacts(ctx)
        ref_today = date.today()
        generated: list[FollowUpTask] = []
        async with tenant_db_session(self._sf, ctx) as session:
            for c in contacts:
                if c.is_anonymized:
                    continue
                candidates: list[tuple[str, str, FollowUpPriority, date]] = []
                if c.last_visit_days_ago is not None and c.last_visit_days_ago >= 3:
                    candidates.append(
                        (
                            f"Débriefing visite J+{c.last_visit_days_ago} — {c.full_name}",
                            "Visite réalisée sans retour enregistré depuis ≥ 3 jours.",
                            FollowUpPriority.HIGH,
                            ref_today,
                        )
                    )
                if c.pending_offer_days >= 7:
                    candidates.append(
                        (
                            f"Relance offre / décision J+{c.pending_offer_days} — {c.full_name}",
                            "Proposition ou négociation ouverte depuis ≥ 7 jours.",
                            FollowUpPriority.HIGH,
                            ref_today,
                        )
                    )
                if c.has_missing_docs:
                    candidates.append(
                        (
                            f"Pièces réglementaires à compléter — {c.full_name}",
                            "Diagnostics, DPE/SCE ou pièces pré-contractuelles manquantes.",
                            FollowUpPriority.MEDIUM,
                            ref_today + timedelta(days=2),
                        )
                    )
                if is_retention_expired(c.retention_until, ref_today):
                    candidates.append(
                        (
                            f"Purge RGPD requise — {c.full_name}",
                            f"Durée de conservation expirée ({c.retention_until}).",
                            FollowUpPriority.HIGH,
                            ref_today,
                        )
                    )

                for title, reason, priority, due_dt in candidates:
                    existing = (
                        await session.execute(
                            text(
                                """
                                SELECT id::text, status
                                FROM follow_up_tasks
                                WHERE contact_id = CAST(:cid AS uuid) AND title = :title
                                LIMIT 1
                                """
                            ),
                            {"cid": c.id, "title": title},
                        )
                    ).mappings().first()
                    if existing is None:
                        ins = (
                            await session.execute(
                                text(
                                    """
                                    INSERT INTO follow_up_tasks (
                                      agency_id, contact_id, contact_name, created_by_user_id, assigned_to_user_id,
                                      title, reason, channel, due_at, priority, status, auto_generated
                                    ) VALUES (
                                      CAST(:aid AS uuid), CAST(:cid AS uuid), :cname, CAST(:uid AS uuid), CAST(:assigned AS uuid),
                                      :title, :reason, 'CALL', :due_at, :priority, 'OPEN', true
                                    ) RETURNING id::text, status
                                    """
                                ),
                                {
                                    "aid": c.agency_id,
                                    "cid": c.id,
                                    "cname": c.full_name,
                                    "uid": ctx.user_id,
                                    "assigned": c.assigned_user_id,
                                    "title": title,
                                    "reason": reason,
                                    "due_at": datetime.combine(due_dt, datetime.min.time(), tzinfo=timezone.utc),
                                    "priority": priority.value,
                                },
                            )
                        ).mappings().one()
                        task_id = ins["id"]
                        completed = False
                    else:
                        task_id = existing["id"]
                        completed = existing["status"] in ("COMPLETED", "Terminée")

                    generated.append(
                        FollowUpTask(
                            id=task_id,
                            agency_id=c.agency_id,
                            contact_id=c.id,
                            contact_name=c.full_name,
                            assigned_user_id=c.assigned_user_id,
                            title=title,
                            reason=reason,
                            priority=priority,
                            due_date=due_dt.isoformat(),
                            is_auto_generated=True,
                            completed=completed,
                        )
                    )
        return generated

    async def complete_task(self, ctx: TenantContext, task_id: str) -> FollowUpTask:
        ctx.require_permission(Permission.CRM_WRITE)
        async with tenant_db_session(self._sf, ctx) as session:
            row = (
                await session.execute(
                    text(
                        """
                        UPDATE follow_up_tasks
                        SET status = 'COMPLETED', updated_at = now()
                        WHERE id = CAST(:tid AS uuid)
                        RETURNING id::text, agency_id::text, contact_id::text, contact_name, assigned_to_user_id::text,
                                  title, reason, priority, due_at, auto_generated
                        """
                    ),
                    {"tid": task_id},
                )
            ).mappings().first()
            if row is None:
                raise PermissionError("Tâche introuvable ou hors périmètre agence.")
            return FollowUpTask(
                id=row["id"],
                agency_id=row["agency_id"],
                contact_id=row["contact_id"] or "",
                contact_name=row["contact_name"],
                assigned_user_id=row["assigned_to_user_id"],
                title=row["title"],
                reason=row["reason"],
                priority=FollowUpPriority(row["priority"]) if row["priority"] in ("HIGH", "MEDIUM", "LOW") else FollowUpPriority.MEDIUM,
                due_date=row["due_at"].date().isoformat(),
                is_auto_generated=bool(row["auto_generated"]),
                completed=True,
            )

    async def export_contact_rgpd(self, ctx: TenantContext, *, contact_id: str) -> dict:
        ctx.require_permission(Permission.CRM_READ)
        async with tenant_db_session(self._sf, ctx) as session:
            c_row = (
                await session.execute(
                    text(
                        """
                        SELECT id, agency_id, assigned_to_user_id, country_code, full_name, email::text, phone,
                               contact_type, sector, budget_max, rgpd_legal_basis, rgpd_consent_granted,
                               rgpd_retention_until, created_at, last_contacted_at,
                               has_missing_docs, pending_offer_days, last_visit_days_ago, is_anonymized
                        FROM crm_contacts
                        WHERE id = CAST(:cid AS uuid)
                        """
                    ),
                    {"cid": contact_id},
                )
            ).mappings().first()
            if c_row is None:
                raise PermissionError("Contact introuvable ou hors périmètre agence.")
            contact = self._row_to_contact(c_row)
            exchanges = (
                await session.execute(
                    text(
                        """
                        SELECT id::text, channel, summary, occurred_at::text AS created_at
                        FROM contact_exchanges
                        WHERE contact_id = CAST(:cid AS uuid)
                        ORDER BY occurred_at DESC
                        """
                    ),
                    {"cid": contact_id},
                )
            ).mappings().all()
            tasks = (
                await session.execute(
                    text(
                        """
                        SELECT id::text, title, status, due_at::date::text AS due_date
                        FROM follow_up_tasks
                        WHERE contact_id = CAST(:cid AS uuid)
                        """
                    ),
                    {"cid": contact_id},
                )
            ).mappings().all()
            await write_audit_log(
                session,
                agency_id=contact.agency_id,
                actor_user_id=ctx.user_id,
                actor_role=ctx.role.value,
                action_type="RGPD_PORTABILITY_EXPORT",
                target_type="CRM_CONTACT",
                target_id=contact.id,
                detail="Export portabilité Art. 15/20 (notes privées exclues)",
            )
            return build_rgpd_portability_export(
                agency_id=contact.agency_id,
                exported_by_user_id=ctx.user_id,
                subject_type="CRM_CONTACT",
                subject_payload={
                    "id": contact.id,
                    "country_code": contact.country_code.value,
                    "full_name": contact.full_name,
                    "email": contact.email,
                    "phone": contact.phone,
                    "role": contact.role.value,
                    "city": contact.city,
                    "budget_euro": contact.budget_euro,
                    "legal_basis": contact.legal_basis,
                    "retention_until": contact.retention_until,
                },
                related_exchanges=[dict(e) for e in exchanges],
                related_tasks=[dict(t) for t in tasks],
            )

    async def execute_rgpd_purge(self, ctx: TenantContext, *, today: date | None = None) -> int:
        ctx.require_permission(Permission.CRM_DELETE_RGPD)
        ref_today = today or date.today()
        async with tenant_db_session(self._sf, ctx) as session:
            purged_ids = (
                await session.execute(
                    text(
                        """
                        UPDATE crm_contacts
                        SET full_name = '[Contact anonymisé RGPD]',
                            first_name = '',
                            last_name = '',
                            email = '',
                            phone = '',
                            phone_normalized_last9 = '',
                            postal_address = '',
                            is_anonymized = true,
                            updated_at = now()
                        WHERE is_anonymized = false
                          AND rgpd_retention_until IS NOT NULL
                          AND rgpd_retention_until < :ref_today
                        RETURNING id::text
                        """
                    ),
                    {"ref_today": ref_today},
                )
            ).scalars().all()
            if purged_ids:
                await session.execute(
                    text(
                        """
                        DELETE FROM advisor_private_notes
                        WHERE entity_type = 'CRM_CONTACT' AND entity_id::text = ANY(:ids)
                        """
                    ),
                    {"ids": list(purged_ids)},
                )
                await write_audit_log(
                    session,
                    agency_id=ctx.agency_id,
                    actor_user_id=ctx.user_id,
                    actor_role=ctx.role.value,
                    action_type="RGPD_PURGE_EXECUTED",
                    target_type="CRM_CONTACT_BATCH",
                    target_id=str(len(purged_ids)),
                    detail=f"{len(purged_ids)} contact(s) anonymisé(s) après expiration RGPD",
                )
            return len(purged_ids)


class PostgresMarketplaceRepository:
    """Persistance PostgreSQL RLS pour Marketplace (annonces publiées + file de modération)."""

    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._sf = session_factory

    @staticmethod
    def _row_to_listing(row) -> MarketplaceListing:
        highlights = tuple(row["highlights"] or [])
        timeline = estimate_sale_timeline(
            sector_name=row["sector"] or row["city"],
            property_kind=row["property_type"],
            asking_price_euro=int(row["price_euro"]),
            surface_m2=int(row["surface_m2"]),
            dpe=row["dpe"],
            condition_label=row["general_condition"] or "Bon état",
            dvf_price_m2=int(row["price_euro"] / max(1, int(row["surface_m2"]))),
            dvf_count=3,
            photo_count=int(row["photo_count"] or 0),
            provided_docs_ratio=0.75 if row["publisher_type"] == "AGENCE" else 0.5,
            mandate_type=row["mandate_type"] or "",
        )
        return MarketplaceListing(
            id=str(row["id"]),
            country_code=CountryCode(row["country_code"]),
            publisher_type=PublisherType(row["publisher_type"]),
            publisher_label="Particulier vérifié" if row["publisher_type"] == "PARTICULIER" else "Agence partenaire",
            title=row["title"],
            property_kind=row["property_type"],
            city=row["city"] or row["sector"],
            region_name=row["region_name"] or "",
            sector_label=row["sector"],
            price_euro=int(row["price_euro"]),
            surface_m2=int(row["surface_m2"]),
            rooms=int(row["rooms"] or 1),
            dpe=row["dpe"],
            condition_label=row["general_condition"] or "Bon état",
            highlights=highlights,
            description=row["description"],
            photo_count=int(row["photo_count"] or 0),
            latitude=float(row["latitude"] or 45.7640),
            longitude=float(row["longitude"] or 4.8357),
            sale_timeline=timeline,
            published_at=row["created_at"].date().isoformat(),
            moderation_status=ListingModerationStatus(row["publication_status"]),
            agency_id=str(row["agency_id"]),
            author_user_id=str(row["seller_user_id"] or row["assigned_agent_id"] or ""),
            moderation_note=row["moderation_note"] or "",
        )

    async def search_listings(
        self,
        *,
        country_code: CountryCode = CountryCode.FR,
        publisher_type: PublisherType | None = None,
        origin_address: str = "Lyon",
        search_mode: SearchPerimeterMode = SearchPerimeterMode.TRAVEL_TIME,
        transport_mode: TransportMode = TransportMode.TRAM,
        max_radius_km: int = 15,
        max_travel_minutes: int = 30,
        max_price_euro: int | None = None,
        min_surface_m2: int = 0,
        only_within_perimeter: bool = True,
    ) -> list[MarketplaceSearchHit]:
        resolved_lat, resolved_lon, _, _ = _resolve_city_geo(origin_address, country_code)
        clauses = ["publication_status = 'PUBLIEE'", "country_code = :cc"]
        params: dict[str, object] = {"cc": country_code.value, "min_surf": min_surface_m2}
        clauses.append("surface_m2 >= :min_surf")
        if publisher_type is not None:
            clauses.append("publisher_type = :pub_type")
            params["pub_type"] = publisher_type.value
        if max_price_euro is not None:
            clauses.append("price_euro <= :max_price")
            params["max_price"] = max_price_euro

        async with self._sf() as session:
            async with session.begin():
                rows = (
                    await session.execute(
                        text(
                            f"""
                            SELECT id, agency_id, assigned_agent_id, seller_user_id, country_code,
                                   publisher_type, publication_status, title, property_type,
                                   city, region_name, sector, price_euro, surface_m2, rooms,
                                   dpe, general_condition, highlights, description, photo_count,
                                   latitude, longitude, mandate_type, moderation_note, created_at
                            FROM properties
                            WHERE {' AND '.join(clauses)}
                            ORDER BY created_at DESC
                            LIMIT 200
                            """
                        ),
                        params,
                    )
                ).mappings().all()

        hits: list[MarketplaceSearchHit] = []
        for row in rows:
            item = self._row_to_listing(row)
            est = evaluate_property_access(
                origin_lat=resolved_lat,
                origin_lon=resolved_lon,
                origin_label=origin_address or item.city,
                prop_lat=item.latitude,
                prop_lon=item.longitude,
                transport_mode=transport_mode,
                search_mode=search_mode,
                max_radius_km=max_radius_km,
                max_travel_minutes=max_travel_minutes,
            )
            if only_within_perimeter and not est.within_perimeter:
                continue
            hits.append(
                MarketplaceSearchHit(
                    listing=item,
                    distance_km=est.distance_km,
                    estimated_minutes=est.estimated_minutes,
                    transport_badge=est.badge_label,
                    within_perimeter=est.within_perimeter,
                )
            )
        hits.sort(key=lambda h: (not h.within_perimeter, h.estimated_minutes, h.listing.price_euro))
        return hits

    async def publish_listing(
        self,
        ctx: TenantContext,
        *,
        country_code: CountryCode,
        publisher_type: PublisherType,
        publisher_label: str,
        title: str,
        property_kind: str,
        city: str,
        sector_label: str,
        price_euro: int,
        surface_m2: int,
        rooms: int,
        dpe: str,
        condition_label: str,
        highlights: list[str],
        description: str,
        photo_count: int = 3,
    ) -> MarketplaceListing:
        if not (ctx.has_permission(Permission.PROPERTY_WRITE) or ctx.has_permission(Permission.SELLER_PORTAL_SELF)):
            raise PermissionError("Permission refusée pour publier une annonce immobilière.")
        if publisher_type == PublisherType.AGENCE and not ctx.has_permission(Permission.PROPERTY_WRITE):
            raise PermissionError("Seul un conseiller/directeur d'agence peut publier en mode AGENCE.")

        get_country_pack(country_code)
        lat, lon, region_name, ref_m2 = _resolve_city_geo(city, country_code)
        safe_title = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(title, 180))
        safe_desc = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(description, 2000))
        if not safe_title or price_euro < 10_000 or surface_m2 < 9:
            raise ValueError("Titre, prix (>= 10 000 €) et surface (>= 9 m²) obligatoires.")

        initial_status = (
            ListingModerationStatus.A_VERIFIER
            if (publisher_type == PublisherType.PARTICULIER or ctx.role in (MontyRole.SELLER, MontyRole.BUYER))
            else ListingModerationStatus.PUBLIEE
        )
        timeline = estimate_sale_timeline(
            sector_name=sector_label or city,
            property_kind=property_kind,
            asking_price_euro=price_euro,
            surface_m2=surface_m2,
            dpe=dpe,
            condition_label=condition_label,
            dvf_price_m2=ref_m2,
            dvf_count=3,
            photo_count=photo_count,
            provided_docs_ratio=0.75 if publisher_type == PublisherType.AGENCE else 0.5,
            mandate_type="Exclusif" if publisher_type == PublisherType.AGENCE else "",
        )
        clean_highlights = [InputSanitizer.sanitize_text(h, 60) for h in highlights if h.strip()]

        async with tenant_db_session(self._sf, ctx) as session:
            row = (
                await session.execute(
                    text(
                        """
                        INSERT INTO properties (
                          agency_id, assigned_agent_id, seller_user_id, country_code, city, region_name,
                          title, sector, property_type, price_euro, surface_m2, rooms, dpe,
                          general_condition, highlights, description, photo_count,
                          publisher_type, publication_status, latitude, longitude,
                          sale_delay_min_days, sale_delay_likely_days, sale_delay_max_days
                        ) VALUES (
                          CAST(:aid AS uuid), CAST(:uid AS uuid),
                          CASE WHEN :pub_type = 'PARTICULIER' THEN CAST(:uid AS uuid) ELSE NULL END,
                          :cc, :city, :region_name,
                          :title, :sector, :kind, :price, :surface, :rooms, :dpe,
                          :cond, CAST(:highlights AS jsonb), :description, :photos,
                          :pub_type, :status, :lat, :lon,
                          :d_min, :d_likely, :d_max
                        )
                        RETURNING id, agency_id, assigned_agent_id, seller_user_id, country_code,
                                  publisher_type, publication_status, title, property_type,
                                  city, region_name, sector, price_euro, surface_m2, rooms,
                                  dpe, general_condition, highlights, description, photo_count,
                                  latitude, longitude, mandate_type, moderation_note, created_at
                        """
                    ),
                    {
                        "aid": ctx.agency_id,
                        "uid": ctx.user_id,
                        "cc": country_code.value,
                        "city": InputSanitizer.sanitize_text(city, 80),
                        "region_name": region_name,
                        "title": safe_title,
                        "sector": InputSanitizer.sanitize_text(sector_label, 100) or city,
                        "kind": InputSanitizer.sanitize_text(property_kind, 40) or "Appartement",
                        "price": price_euro,
                        "surface": surface_m2,
                        "rooms": max(1, rooms),
                        "dpe": dpe.strip().upper()[:2],
                        "cond": InputSanitizer.sanitize_text(condition_label, 60) or "Bon état",
                        "highlights": json.dumps(clean_highlights),
                        "description": safe_desc,
                        "photos": max(0, photo_count),
                        "pub_type": publisher_type.value,
                        "status": initial_status.value,
                        "lat": lat,
                        "lon": lon,
                        "d_min": timeline.low_days,
                        "d_likely": timeline.likely_days,
                        "d_max": timeline.high_days,
                    },
                )
            ).mappings().one()
            await write_audit_log(
                session,
                agency_id=ctx.agency_id,
                actor_user_id=ctx.user_id,
                actor_role=ctx.role.value,
                action_type="LISTING_SUBMITTED",
                target_type="PROPERTY",
                target_id=str(row["id"]),
                detail=f"Annonce {safe_title} ({initial_status.value})",
            )
            return self._row_to_listing(row)

    async def list_pending_moderation(self, ctx: TenantContext) -> list[MarketplaceListing]:
        ctx.require_permission(Permission.PROPERTY_MODERATE)
        async with tenant_db_session(self._sf, ctx) as session:
            rows = (
                await session.execute(
                    text(
                        """
                        SELECT id, agency_id, assigned_agent_id, seller_user_id, country_code,
                               publisher_type, publication_status, title, property_type,
                               city, region_name, sector, price_euro, surface_m2, rooms,
                               dpe, general_condition, highlights, description, photo_count,
                               latitude, longitude, mandate_type, moderation_note, created_at
                        FROM properties
                        WHERE publication_status = 'A_VERIFIER'
                        ORDER BY created_at DESC
                        """
                    )
                )
            ).mappings().all()
            return [self._row_to_listing(r) for r in rows]

    async def moderate_listing(
        self,
        ctx: TenantContext,
        *,
        listing_id: str,
        approve: bool,
        moderation_note: str = "",
    ) -> MarketplaceListing:
        ctx.require_permission(Permission.PROPERTY_MODERATE)
        new_status = ListingModerationStatus.PUBLIEE if approve else ListingModerationStatus.REJETEE
        clean_note = InputSanitizer.sanitize_text(moderation_note, 500)
        async with tenant_db_session(self._sf, ctx) as session:
            row = (
                await session.execute(
                    text(
                        """
                        UPDATE properties
                        SET publication_status = :status,
                            moderation_note = :note,
                            moderated_by_user_id = CAST(:uid AS uuid),
                            moderated_at = now(),
                            updated_at = now()
                        WHERE id = CAST(:lid AS uuid)
                        RETURNING id, agency_id, assigned_agent_id, seller_user_id, country_code,
                                  publisher_type, publication_status, title, property_type,
                                  city, region_name, sector, price_euro, surface_m2, rooms,
                                  dpe, general_condition, highlights, description, photo_count,
                                  latitude, longitude, mandate_type, moderation_note, created_at
                        """
                    ),
                    {"lid": listing_id, "status": new_status.value, "note": clean_note, "uid": ctx.user_id},
                )
            ).mappings().first()
            if row is None:
                raise PermissionError("Annonce introuvable ou hors périmètre agence.")
            await write_audit_log(
                session,
                agency_id=ctx.agency_id,
                actor_user_id=ctx.user_id,
                actor_role=ctx.role.value,
                action_type="LISTING_MODERATED",
                target_type="PROPERTY",
                target_id=listing_id,
                detail=f"Statut -> {new_status.value} ({clean_note})",
            )
            return self._row_to_listing(row)


class PostgresBillingRepository:
    """Persistance PostgreSQL des webhooks Stripe (idempotence atomique + mise à jour abonnement agence)."""

    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._sf = session_factory

    async def record_webhook_and_update_agency(
        self,
        *,
        event_id: str,
        event_type: str,
        agency_id: str,
        plan_code: str,
        subscription_status: str,
        customer_id: str,
        subscription_id: str,
    ) -> bool:
        plan = SAAS_PLANS.get(plan_code, SAAS_PLANS["AGENCY"])
        async with self._sf() as session:
            async with session.begin():
                inserted = (
                    await session.execute(
                        text(
                            """
                            INSERT INTO stripe_webhook_events (event_id, event_type, agency_id)
                            VALUES (:eid, :etype, CAST(NULLIF(:aid, '') AS uuid))
                            ON CONFLICT (event_id) DO NOTHING
                            RETURNING event_id
                            """
                        ),
                        {"eid": event_id, "etype": event_type, "aid": agency_id},
                    )
                ).first()
                if inserted is None:
                    return False

                if agency_id:
                    await session.execute(
                        text("SELECT set_config('app.current_agency_id', :aid, true)"),
                        {"aid": agency_id},
                    )
                    await session.execute(
                        text("SELECT set_config('app.current_role', 'DIRECTOR', true)")
                    )
                    await session.execute(
                        text(
                            """
                            UPDATE agencies
                            SET plan_code = :plan_code,
                                subscription_status = :sub_status,
                                seat_limit = :seat_limit,
                                ai_monthly_quota_tokens = :ai_quota,
                                stripe_customer_id = COALESCE(NULLIF(:cust_id, ''), stripe_customer_id),
                                stripe_subscription_id = COALESCE(NULLIF(:sub_id, ''), stripe_subscription_id),
                                updated_at = now()
                            WHERE id = CAST(:aid AS uuid)
                            """
                        ),
                        {
                            "aid": agency_id,
                            "plan_code": plan.code,
                            "sub_status": subscription_status,
                            "seat_limit": plan.seat_limit,
                            "ai_quota": plan.ai_monthly_quota_tokens,
                            "cust_id": customer_id,
                            "sub_id": subscription_id,
                        },
                    )
                    await write_audit_log(
                        session,
                        agency_id=agency_id,
                        actor_user_id=None,
                        actor_role="STRIPE_WEBHOOK",
                        action_type="SUBSCRIPTION_UPDATED",
                        target_type="AGENCY",
                        target_id=agency_id,
                        detail=f"{event_type} -> {plan.code} ({subscription_status})",
                    )
                return True
