from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class MontyRole(str, Enum):
    SUPER_ADMIN = "SUPER_ADMIN"
    DIRECTOR = "DIRECTOR"
    ADMIN = "ADMIN"
    AGENT = "AGENT"
    ASSISTANT = "ASSISTANT"
    BUYER = "BUYER"
    SELLER = "SELLER"


class Permission(str, Enum):
    PLATFORM_ADMIN = "platform:admin"
    AGENCY_MANAGE = "agency:manage"
    BILLING_MANAGE = "billing:manage"
    TEAM_MANAGE = "team:manage"
    AUDIT_LOGS_READ = "audit_logs:read"
    CRM_READ = "crm:read"
    CRM_WRITE = "crm:write"
    CRM_DELETE_RGPD = "crm:delete_rgpd"
    PRIVATE_NOTES_READ = "private_notes:read"
    PRIVATE_NOTES_WRITE = "private_notes:write"
    PROPERTY_READ = "property:read"
    PROPERTY_WRITE = "property:write"
    PROPERTY_MODERATE = "property:moderate"
    MATCHING_RUN = "matching:run"
    ESTIMATION_RUN = "estimation:run"
    FINANCE_SIMULATE = "finance:simulate"
    AI_ASSISTANT_PRO = "ai:pro"
    AI_ASSISTANT_CLIENT = "ai:client"
    BUYER_PORTAL_SELF = "portal:buyer_self"
    SELLER_PORTAL_SELF = "portal:seller_self"
    NEWS_PRO_READ = "news:pro_read"
    NEWS_ADMIN = "news:admin"


ROLE_PERMISSIONS: dict[MontyRole, frozenset[Permission]] = {
    MontyRole.SUPER_ADMIN: frozenset(Permission),
    MontyRole.DIRECTOR: frozenset({
        Permission.AGENCY_MANAGE,
        Permission.BILLING_MANAGE,
        Permission.TEAM_MANAGE,
        Permission.AUDIT_LOGS_READ,
        Permission.CRM_READ,
        Permission.CRM_WRITE,
        Permission.CRM_DELETE_RGPD,
        Permission.PRIVATE_NOTES_READ,
        Permission.PRIVATE_NOTES_WRITE,
        Permission.PROPERTY_READ,
        Permission.PROPERTY_WRITE,
        Permission.PROPERTY_MODERATE,
        Permission.MATCHING_RUN,
        Permission.ESTIMATION_RUN,
        Permission.FINANCE_SIMULATE,
        Permission.AI_ASSISTANT_PRO,
        Permission.NEWS_PRO_READ,
        Permission.NEWS_ADMIN,
    }),
    MontyRole.ADMIN: frozenset({
        Permission.TEAM_MANAGE,
        Permission.AUDIT_LOGS_READ,
        Permission.CRM_READ,
        Permission.CRM_WRITE,
        Permission.CRM_DELETE_RGPD,
        Permission.PRIVATE_NOTES_READ,
        Permission.PRIVATE_NOTES_WRITE,
        Permission.PROPERTY_READ,
        Permission.PROPERTY_WRITE,
        Permission.PROPERTY_MODERATE,
        Permission.MATCHING_RUN,
        Permission.ESTIMATION_RUN,
        Permission.FINANCE_SIMULATE,
        Permission.AI_ASSISTANT_PRO,
        Permission.NEWS_PRO_READ,
        Permission.NEWS_ADMIN,
    }),
    MontyRole.AGENT: frozenset({
        Permission.CRM_READ,
        Permission.CRM_WRITE,
        Permission.PRIVATE_NOTES_READ,
        Permission.PRIVATE_NOTES_WRITE,
        Permission.PROPERTY_READ,
        Permission.PROPERTY_WRITE,
        Permission.MATCHING_RUN,
        Permission.ESTIMATION_RUN,
        Permission.FINANCE_SIMULATE,
        Permission.AI_ASSISTANT_PRO,
        Permission.NEWS_PRO_READ,
    }),
    MontyRole.ASSISTANT: frozenset({
        Permission.CRM_READ,
        Permission.CRM_WRITE,
        Permission.PROPERTY_READ,
        Permission.PROPERTY_WRITE,
        Permission.FINANCE_SIMULATE,
        Permission.NEWS_PRO_READ,
    }),
    MontyRole.BUYER: frozenset({
        Permission.FINANCE_SIMULATE,
        Permission.AI_ASSISTANT_CLIENT,
        Permission.BUYER_PORTAL_SELF,
    }),
    MontyRole.SELLER: frozenset({
        Permission.AI_ASSISTANT_CLIENT,
        Permission.SELLER_PORTAL_SELF,
    }),
}


@dataclass(frozen=True)
class TenantContext:
    user_id: str
    agency_id: str
    role: MontyRole

    def has_permission(self, permission: Permission) -> bool:
        return permission in ROLE_PERMISSIONS.get(self.role, frozenset())

    def require_permission(self, permission: Permission) -> None:
        if not self.has_permission(permission):
            raise PermissionError(
                f"Permission refusée ({permission.value}) pour le rôle {self.role.value}."
            )

    def assert_same_agency(self, resource_agency_id: str) -> None:
        if self.role == MontyRole.SUPER_ADMIN:
            return
        if not self.agency_id or self.agency_id != resource_agency_id:
            raise PermissionError("Isolation multi-agence : accès inter-agences interdit.")

    def can_read_private_note(self, author_user_id: str, note_agency_id: str) -> bool:
        if self.role == MontyRole.SUPER_ADMIN:
            return True
        if self.agency_id != note_agency_id:
            return False
        if not self.has_permission(Permission.PRIVATE_NOTES_READ):
            return False
        if self.role in (MontyRole.DIRECTOR, MontyRole.ADMIN):
            return True
        return self.user_id == author_user_id
