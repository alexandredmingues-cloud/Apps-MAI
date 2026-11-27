from __future__ import annotations

import pytest
from fastapi import HTTPException

from app.api.v1.billing_router import _is_paid_eur_checkout_session
from app.api.v1.deps import _is_rls_safe_database_role, ensure_production_persistence


def _session(**overrides):
    data = {
        "id": "cs_test_monagent_123",
        "mode": "payment",
        "payment_status": "paid",
        "currency": "eur",
        "amount_total": 2900,
    }
    data.update(overrides)
    return data


def test_accepts_only_exact_paid_eur_one_time_checkout() -> None:
    assert _is_paid_eur_checkout_session(_session(), 29)


@pytest.mark.parametrize(
    ("session", "expected_amount"),
    [
        (_session(payment_status="unpaid"), 29),
        (_session(mode="subscription"), 29),
        (_session(currency="usd"), 29),
        (_session(amount_total=2800), 29),
        (_session(amount_total=None), 29),
        (_session(), 0),
        (_session(), -1),
        (_session(amount_total="not-a-number"), 29),
    ],
)
def test_rejects_unpaid_wrong_mode_currency_or_amount(session, expected_amount) -> None:
    assert not _is_paid_eur_checkout_session(session, expected_amount)


@pytest.mark.parametrize(
    ("role_state", "expected"),
    [
        ({"rolsuper": False, "rolbypassrls": False}, True),
        ({"rolsuper": True, "rolbypassrls": False}, False),
        ({"rolsuper": False, "rolbypassrls": True}, False),
        ({"rolsuper": True, "rolbypassrls": True}, False),
        (None, False),
        ({}, False),
    ],
)
def test_production_database_role_must_not_bypass_rls(role_state, expected) -> None:
    assert _is_rls_safe_database_role(role_state) is expected



def test_production_persistence_guard_rejects_missing_database() -> None:
    with pytest.raises(HTTPException) as exc:
        ensure_production_persistence(None, is_production=True, service_name="RNIC")
    assert exc.value.status_code == 503
    assert exc.value.headers == {"Retry-After": "60"}


def test_production_persistence_guard_allows_configured_database() -> None:
    marker = object()
    ensure_production_persistence(marker, is_production=True, service_name="RNIC")


def test_production_persistence_guard_keeps_local_development_fallback() -> None:
    ensure_production_persistence(None, is_production=False, service_name="RNIC")
