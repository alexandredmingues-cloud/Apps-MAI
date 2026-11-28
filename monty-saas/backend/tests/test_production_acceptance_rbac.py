from __future__ import annotations

import pytest

from app.core.rbac import MontyRole, Permission, TenantContext


def test_private_individual_roles_cannot_access_agency_crm_or_admin_controls():
    buyer = TenantContext("buyer-1", "agency-1", MontyRole.BUYER)
    seller = TenantContext("seller-1", "agency-1", MontyRole.SELLER)

    for ctx in (buyer, seller):
        assert not ctx.has_permission(Permission.CRM_READ)
        assert not ctx.has_permission(Permission.CRM_WRITE)
        assert not ctx.has_permission(Permission.AGENCY_MANAGE)
        assert not ctx.has_permission(Permission.BILLING_MANAGE)
        assert not ctx.has_permission(Permission.TEAM_MANAGE)
        assert ctx.has_permission(
            Permission.BUYER_PORTAL_SELF if ctx.role == MontyRole.BUYER else Permission.SELLER_PORTAL_SELF
        )


def test_agent_and_admin_permissions_are_limited_to_their_responsibilities():
    agent = TenantContext("agent-1", "agency-1", MontyRole.AGENT)
    admin = TenantContext("admin-1", "agency-1", MontyRole.ADMIN)

    assert agent.has_permission(Permission.CRM_READ)
    assert agent.has_permission(Permission.CRM_WRITE)
    assert not agent.has_permission(Permission.BILLING_MANAGE)
    assert not agent.has_permission(Permission.TEAM_MANAGE)
    assert not agent.has_permission(Permission.PROPERTY_MODERATE)
    assert not agent.has_permission(Permission.CRM_DELETE_RGPD)

    assert admin.has_permission(Permission.TEAM_MANAGE)
    assert not admin.has_permission(Permission.AGENCY_MANAGE)
    assert not admin.has_permission(Permission.BILLING_MANAGE)
    assert not admin.has_permission(Permission.PLATFORM_ADMIN)


def test_director_is_agency_admin_but_not_platform_admin():
    director = TenantContext("director-1", "agency-1", MontyRole.DIRECTOR)
    assert director.has_permission(Permission.AGENCY_MANAGE)
    assert director.has_permission(Permission.BILLING_MANAGE)
    assert director.has_permission(Permission.TEAM_MANAGE)
    assert not director.has_permission(Permission.PLATFORM_ADMIN)


def test_cross_agency_access_is_denied_and_same_agency_is_allowed():
    agent = TenantContext("agent-1", "agency-1", MontyRole.AGENT)
    agent.assert_same_agency("agency-1")
    with pytest.raises(PermissionError, match="inter-agences"):
        agent.assert_same_agency("agency-2")
    with pytest.raises(PermissionError):
        TenantContext("agent-2", "", MontyRole.AGENT).assert_same_agency("agency-1")


def test_private_notes_are_owner_only_for_agents_and_agency_wide_for_directors():
    owner = TenantContext("agent-1", "agency-1", MontyRole.AGENT)
    peer = TenantContext("agent-2", "agency-1", MontyRole.AGENT)
    director = TenantContext("director-1", "agency-1", MontyRole.DIRECTOR)

    assert owner.can_access_private_notes("agent-1")
    assert not peer.can_access_private_notes("agent-1")
    assert director.can_access_private_notes("agent-1")


def test_super_admin_has_platform_scope():
    super_admin = TenantContext("platform-1", "platform", MontyRole.SUPER_ADMIN)
    assert super_admin.has_permission(Permission.PLATFORM_ADMIN)
    assert super_admin.has_permission(Permission.BILLING_MANAGE)
    assert super_admin.has_permission(Permission.CRM_DELETE_RGPD)


def test_assistant_has_crm_but_not_billing_or_rgpd_delete():
    assistant = TenantContext("asst-1", "agency-1", MontyRole.ASSISTANT)
    assert assistant.has_permission(Permission.CRM_READ)
    assert assistant.has_permission(Permission.CRM_WRITE)
    assert assistant.has_permission(Permission.PROPERTY_READ)
    assert not assistant.has_permission(Permission.BILLING_MANAGE)
    assert not assistant.has_permission(Permission.TEAM_MANAGE)
    assert not assistant.has_permission(Permission.CRM_DELETE_RGPD)
    assert not assistant.has_permission(Permission.PRIVATE_NOTES_READ)
    assert not assistant.has_permission(Permission.PLATFORM_ADMIN)


def test_every_monty_role_is_mapped_in_role_permissions():
    from app.core.rbac import ROLE_PERMISSIONS
    for role in MontyRole:
        assert role in ROLE_PERMISSIONS, f"ROLE_PERMISSIONS manque pour {role}"
