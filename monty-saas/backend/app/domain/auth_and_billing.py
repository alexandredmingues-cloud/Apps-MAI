from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timedelta, timezone
import re
import secrets
import uuid

from app.core.config import Settings
from app.core.rbac import ROLE_PERMISSIONS, MontyRole, Permission, TenantContext
from app.core.security import (
    InputSanitizer,
    create_access_token,
    hash_password,
    verify_password,
    verify_stripe_webhook_signature,
)
from app.domain.country_packs import CountryCode


@dataclass(frozen=True)
class SaasPlan:
    code: str
    name: str
    monthly_price_euro: int
    seat_limit: int
    ai_monthly_quota_tokens: int
    target_audience: str
    features: tuple[str, ...]


SAAS_PLANS: dict[str, SaasPlan] = {
    "INDIVIDUAL": SaasPlan(
        code="INDIVIDUAL",
        name="Monty Indépendant / Mandataire",
        monthly_price_euro=49,
        seat_limit=1,
        ai_monthly_quota_tokens=250_000,
        target_audience="Conseiller indépendant & vendeur vérifié",
        features=(
            "CRM multi-pays FR/PT + RGPD automatisé",
            "Estimation DVF & Délai de vente probable",
            "6 Simulateurs financiers HCSF / Notaire / IMT",
            "Assistant rédaction annonces sans PII",
        ),
    ),
    "AGENCY": SaasPlan(
        code="AGENCY",
        name="Monty Agence Pro",
        monthly_price_euro=149,
        seat_limit=10,
        ai_monthly_quota_tokens=1_500_000,
        target_audience="Agence immobilière jusqu'à 10 collaborateurs",
        features=(
            "Isolation PostgreSQL RLS + Notes privées cloisonnées",
            "Modération annonces & portails acquéreur / vendeur",
            "Matching acquéreurs ↔ biens + relances automatiques",
            "Import/Export CSV sécurisé & journal d'audit immuable",
        ),
    ),
    "NETWORK": SaasPlan(
        code="NETWORK",
        name="Monty Réseau & International",
        monthly_price_euro=390,
        seat_limit=50,
        ai_monthly_quota_tokens=6_000_000,
        target_audience="Groupes multi-agences France & Portugal",
        features=(
            "Multi-agences consolidé + gouvernance directeur",
            "Modules juridictionnels FR + PT isolés",
            "Files Redis RGPD & imports XLSX massifs",
            "SLA prioritaire & onboarding dédié",
        ),
    ),
}


@dataclass(frozen=True)
class AgencyRecord:
    id: str
    name: str
    slug: str
    city: str
    country_code: CountryCode
    plan_code: str = "INDIVIDUAL"
    subscription_status: str = "TRIAL"
    seat_limit: int = 1
    stripe_customer_id: str = ""
    stripe_subscription_id: str = ""


@dataclass(frozen=True)
class UserAccountRecord:
    id: str
    email: str
    password_hash: str
    display_name: str
    is_active: bool = True


@dataclass(frozen=True)
class MembershipRecord:
    agency_id: str
    user_id: str
    role: MontyRole
    status: str = "ACTIVE"


@dataclass(frozen=True)
class InviteRecord:
    id: str
    agency_id: str
    invite_code: str
    target_email: str
    target_name: str
    role: MontyRole
    invited_by_user_id: str
    expires_at: str
    used_at: str = ""


@dataclass(frozen=True)
class AuthSessionResult:
    access_token: str
    user_id: str
    agency_id: str
    agency_name: str
    display_name: str
    email: str
    role: MontyRole
    permissions: list[str]


def _slugify(value: str) -> str:
    base = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return (base[:90] or "agence-monty") + f"-{secrets.token_hex(3)}"


class AuthService:
    """Authentification réelle (scrypt + JWT), inscription agence, connexion et invitations d'équipe."""

    def __init__(self) -> None:
        self._agencies: dict[str, AgencyRecord] = {
            "agency-france-reseau": AgencyRecord(
                id="agency-france-reseau",
                name="Monty Partenaire France",
                slug="monty-partenaire-france",
                city="Lyon",
                country_code=CountryCode.FR,
                plan_code="AGENCY",
                subscription_status="ACTIVE",
                seat_limit=10,
            )
        }
        self._users_by_id: dict[str, UserAccountRecord] = {}
        self._user_ids_by_email: dict[str, str] = {}
        self._memberships: dict[tuple[str, str], MembershipRecord] = {}
        self._invites_by_code: dict[str, InviteRecord] = {}

    def register_agency_director(
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
        if clean_email in self._user_ids_by_email:
            raise ValueError("Un compte existe déjà pour cet e-mail.")

        pw_hash = hash_password(password)
        agency_id = f"agency-{uuid.uuid4().hex[:12]}"
        user_id = f"usr-{uuid.uuid4().hex[:12]}"

        agency = AgencyRecord(
            id=agency_id,
            name=clean_agency,
            slug=_slugify(clean_agency),
            city=clean_city,
            country_code=country_code,
        )
        user = UserAccountRecord(
            id=user_id,
            email=clean_email,
            password_hash=pw_hash,
            display_name=clean_name,
        )
        membership = MembershipRecord(agency_id=agency_id, user_id=user_id, role=MontyRole.DIRECTOR)

        self._agencies[agency_id] = agency
        self._users_by_id[user_id] = user
        self._user_ids_by_email[clean_email] = user_id
        self._memberships[(agency_id, user_id)] = membership

        token = create_access_token(
            subject_user_id=user_id,
            agency_id=agency_id,
            role=MontyRole.DIRECTOR.value,
            secret=settings.effective_jwt_secret,
            ttl_seconds=settings.jwt_ttl_minutes * 60,
        )
        return AuthSessionResult(
            access_token=token,
            user_id=user_id,
            agency_id=agency_id,
            agency_name=agency.name,
            display_name=user.display_name,
            email=user.email,
            role=MontyRole.DIRECTOR,
            permissions=sorted(p.value for p in ROLE_PERMISSIONS[MontyRole.DIRECTOR]),
        )

    def login(self, *, email: str, password: str, settings: Settings) -> AuthSessionResult:
        clean_email = InputSanitizer.sanitize_text(email, 160).lower()
        uid = self._user_ids_by_email.get(clean_email)
        if not uid:
            raise ValueError("Identifiants invalides.")
        user = self._users_by_id[uid]
        if not user.is_active or not verify_password(password, user.password_hash):
            raise ValueError("Identifiants invalides.")

        membership = next(
            (m for m in self._memberships.values() if m.user_id == uid and m.status == "ACTIVE"),
            None,
        )
        if membership is None:
            raise ValueError("Aucune appartenance agence active.")
        agency = self._agencies[membership.agency_id]

        token = create_access_token(
            subject_user_id=user.id,
            agency_id=agency.id,
            role=membership.role.value,
            secret=settings.effective_jwt_secret,
            ttl_seconds=settings.jwt_ttl_minutes * 60,
        )
        return AuthSessionResult(
            access_token=token,
            user_id=user.id,
            agency_id=agency.id,
            agency_name=agency.name,
            display_name=user.display_name,
            email=user.email,
            role=membership.role,
            permissions=sorted(p.value for p in ROLE_PERMISSIONS[membership.role]),
        )

    def create_invite(
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
        clean_name = InputSanitizer.sanitize_text(target_name, 120)
        if "@" not in clean_email:
            raise ValueError("Adresse e-mail cible invalide.")

        agency = self._agencies.get(ctx.agency_id)
        if agency is not None:
            active_count = sum(
                1 for m in self._memberships.values() if m.agency_id == ctx.agency_id and m.status == "ACTIVE"
            )
            if active_count >= agency.seat_limit:
                raise ValueError(
                    f"Limite de sièges atteinte pour le forfait {agency.plan_code} ({agency.seat_limit} siège(s))."
                )

        invite = InviteRecord(
            id=f"inv-{uuid.uuid4().hex[:10]}",
            agency_id=ctx.agency_id,
            invite_code=secrets.token_urlsafe(24),
            target_email=clean_email,
            target_name=clean_name or clean_email.split("@")[0],
            role=role,
            invited_by_user_id=ctx.user_id,
            expires_at=(datetime.now(timezone.utc) + timedelta(hours=ttl_hours)).isoformat(),
        )
        self._invites_by_code[invite.invite_code] = invite
        return invite

    def claim_invite(
        self,
        *,
        invite_code: str,
        password: str,
        display_name: str = "",
        settings: Settings,
    ) -> AuthSessionResult:
        invite = self._invites_by_code.get(invite_code.strip())
        if invite is None or invite.used_at:
            raise ValueError("Invitation introuvable ou déjà utilisée.")
        if datetime.fromisoformat(invite.expires_at) <= datetime.now(timezone.utc):
            raise ValueError("Invitation expirée.")

        existing_uid = self._user_ids_by_email.get(invite.target_email)
        if existing_uid is None:
            pw_hash = hash_password(password)
            uid = f"usr-{uuid.uuid4().hex[:12]}"
            user = UserAccountRecord(
                id=uid,
                email=invite.target_email,
                password_hash=pw_hash,
                display_name=InputSanitizer.sanitize_text(display_name, 120) or invite.target_name,
            )
            self._users_by_id[uid] = user
            self._user_ids_by_email[invite.target_email] = uid
        else:
            user = self._users_by_id[existing_uid]

        membership = MembershipRecord(agency_id=invite.agency_id, user_id=user.id, role=invite.role)
        self._memberships[(invite.agency_id, user.id)] = membership
        self._invites_by_code[invite.invite_code] = replace(
            invite,
            used_at=datetime.now(timezone.utc).isoformat(),
        )
        agency = self._agencies.get(invite.agency_id) or AgencyRecord(
            id=invite.agency_id,
            name="Agence Monty",
            slug=invite.agency_id,
            city="Lyon",
            country_code=CountryCode.FR,
        )
        token = create_access_token(
            subject_user_id=user.id,
            agency_id=invite.agency_id,
            role=invite.role.value,
            secret=settings.effective_jwt_secret,
            ttl_seconds=settings.jwt_ttl_minutes * 60,
        )
        return AuthSessionResult(
            access_token=token,
            user_id=user.id,
            agency_id=invite.agency_id,
            agency_name=agency.name,
            display_name=user.display_name,
            email=user.email,
            role=invite.role,
            permissions=sorted(p.value for p in ROLE_PERMISSIONS[invite.role]),
        )

    def list_team_members(self, ctx: TenantContext) -> list[dict]:
        ctx.require_permission(Permission.TEAM_MANAGE)
        out: list[dict] = []
        for m in self._memberships.values():
            if m.agency_id != ctx.agency_id and ctx.role != MontyRole.SUPER_ADMIN:
                continue
            user = self._users_by_id.get(m.user_id)
            if user:
                out.append(
                    {
                        "user_id": user.id,
                        "email": user.email,
                        "display_name": user.display_name,
                        "role": m.role.value,
                        "status": m.status,
                    }
                )
        return out


@dataclass(frozen=True)
class AgencyBillingState:
    agency_id: str
    plan_code: str
    subscription_status: str
    seat_limit: int
    stripe_customer_id: str = ""
    stripe_subscription_id: str = ""


class BillingService:
    """Gestion serveur des abonnements Stripe et idempotence des webhooks."""

    def __init__(self) -> None:
        self._billing_by_agency: dict[str, AgencyBillingState] = {}
        self._processed_events: set[str] = set()

    def list_plans(self) -> list[SaasPlan]:
        return list(SAAS_PLANS.values())

    def get_agency_billing(self, ctx: TenantContext) -> AgencyBillingState:
        return self._billing_by_agency.get(
            ctx.agency_id,
            AgencyBillingState(
                agency_id=ctx.agency_id,
                plan_code="INDIVIDUAL",
                subscription_status="TRIAL",
                seat_limit=1,
            ),
        )

    def process_stripe_webhook(
        self,
        *,
        payload_bytes: bytes,
        signature_header: str,
        webhook_secret: str,
        now_fn=None,
    ) -> dict:
        event = verify_stripe_webhook_signature(
            payload_bytes,
            signature_header,
            webhook_secret,
            now_fn=now_fn or (lambda: int(datetime.now(timezone.utc).timestamp())),
        )
        event_id = str(event.get("id", "")).strip()
        event_type = str(event.get("type", "")).strip()
        if not event_id or not event_type:
            raise ValueError("Événement Stripe incomplet (id/type requis).")

        if event_id in self._processed_events:
            return {"status": "duplicate_ignored", "event_id": event_id, "event_type": event_type}

        obj = event.get("data", {}).get("object", {})
        metadata = obj.get("metadata", {}) or {}
        agency_id = str(metadata.get("agency_id", "")).strip()
        plan_code = str(metadata.get("plan_code", "AGENCY")).strip().upper()
        if plan_code not in SAAS_PLANS:
            plan_code = "AGENCY"
        plan = SAAS_PLANS[plan_code]

        if agency_id:
            current = self._billing_by_agency.get(
                agency_id,
                AgencyBillingState(agency_id=agency_id, plan_code="INDIVIDUAL", subscription_status="TRIAL", seat_limit=1),
            )
            if event_type in ("checkout.session.completed", "invoice.payment_succeeded"):
                new_status = "ACTIVE"
            elif event_type == "invoice.payment_failed":
                new_status = "PAST_DUE"
            elif event_type == "customer.subscription.deleted":
                new_status = "CANCELED"
            else:
                new_status = current.subscription_status

            self._billing_by_agency[agency_id] = AgencyBillingState(
                agency_id=agency_id,
                plan_code=plan.code,
                subscription_status=new_status,
                seat_limit=plan.seat_limit,
                stripe_customer_id=str(obj.get("customer", current.stripe_customer_id) or ""),
                stripe_subscription_id=str(obj.get("subscription", obj.get("id", current.stripe_subscription_id)) or ""),
            )

        self._processed_events.add(event_id)
        return {
            "status": "processed",
            "event_id": event_id,
            "event_type": event_type,
            "agency_id": agency_id,
            "plan_code": plan.code,
        }
