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


def test_private_notes_are_owner_only_for_agents_and_agency_scoped():
    agent_a = TenantContext("agent-a", "agency-1", MontyRole.AGENT)
    agent_b = TenantContext("agent-b", "agency-1", MontyRole.AGENT)
    director = TenantContext("director-1", "agency-1", MontyRole.DIRECTOR)
    other_agency = TenantContext("director-2", "agency-2", MontyRole.DIRECTOR)

    assert agent_a.can_read_private_note("agent-a", "agency-1")
    assert not agent_a.can_read_private_note("agent-b", "agency-1")
    assert not agent_b.can_read_private_note("agent-a", "agency-1")
    assert director.can_read_private_note("agent-a", "agency-1")
    assert not other_agency.can_read_private_note("agent-a", "agency-1")


def test_super_admin_override_is_explicit_and_scoped_to_the_platform_role():
    super_admin = TenantContext("platform-1", "platform", MontyRole.SUPER_ADMIN)
    super_admin.assert_same_agency("agency-999")
    assert super_admin.has_permission(Permission.PLATFORM_ADMIN)
    assert super_admin.can_read_private_note("any-user", "agency-999")
