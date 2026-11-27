from __future__ import annotations

from types import SimpleNamespace

import httpx
import pytest
from fastapi import HTTPException, Request

from app.core.turnstile import VERIFY_URL, verify_turnstile


def _request() -> Request:
    return Request({
        "type": "http",
        "asgi": {"version": "3.0"},
        "http_version": "1.1",
        "method": "POST",
        "scheme": "https",
        "path": "/auth/login",
        "raw_path": b"/auth/login",
        "query_string": b"",
        "headers": [],
        "client": ("203.0.113.10", 12345),
        "server": ("mon-agent.fr", 443),
    })


def _settings(*, secret: str = "test-secret", production: bool = True, hosts: tuple[str, ...] = ("mon-agent.fr",)):
    return SimpleNamespace(
        turnstile_secret_key=secret,
        turnstile_allowed_hostnames=hosts,
        is_production=production,
    )


@pytest.mark.asyncio
async def test_production_fails_closed_when_secret_missing():
    with pytest.raises(HTTPException) as exc:
        await verify_turnstile("token", _request(), _settings(secret=""))
    assert exc.value.status_code == 503


@pytest.mark.asyncio
async def test_production_fails_closed_when_allowed_hostnames_missing():
    with pytest.raises(HTTPException) as exc:
        await verify_turnstile("token", _request(), _settings(hosts=()))
    assert exc.value.status_code == 503


@pytest.mark.asyncio
async def test_development_can_run_without_turnstile_secret():
    settings = _settings(secret="", production=False, hosts=())
    await verify_turnstile("", _request(), settings)


@pytest.mark.asyncio
async def test_empty_token_is_rejected_before_network_call():
    with pytest.raises(HTTPException) as exc:
        await verify_turnstile("", _request(), _settings())
    assert exc.value.status_code == 400


@pytest.mark.asyncio
async def test_successful_token_must_match_allowed_hostname(monkeypatch):
    calls = {}

    class FakeResponse:
        def raise_for_status(self):
            return None

        def json(self):
            return {"success": True, "hostname": "MON-AGENT.FR"}

    class FakeClient:
        def __init__(self, **kwargs):
            calls["timeout"] = kwargs["timeout"]

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def post(self, url, data):
            calls["url"] = url
            calls["data"] = data
            return FakeResponse()

    monkeypatch.setattr("app.core.turnstile.httpx.AsyncClient", FakeClient)
    await verify_turnstile("valid-token", _request(), _settings())
    assert calls["url"] == VERIFY_URL
    assert calls["data"]["response"] == "valid-token"
    assert calls["data"]["remoteip"] == "203.0.113.10"


@pytest.mark.asyncio
async def test_successful_token_from_unapproved_hostname_is_rejected(monkeypatch):
    class FakeResponse:
        def raise_for_status(self):
            return None

        def json(self):
            return {"success": True, "hostname": "attacker.example"}

    class FakeClient:
        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def post(self, url, data):
            return FakeResponse()

    monkeypatch.setattr("app.core.turnstile.httpx.AsyncClient", FakeClient)
    with pytest.raises(HTTPException) as exc:
        await verify_turnstile("valid-token", _request(), _settings())
    assert exc.value.status_code == 400


@pytest.mark.asyncio
async def test_cloudflare_outage_fails_closed(monkeypatch):
    class FakeClient:
        def __init__(self, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def post(self, url, data):
            raise httpx.ConnectError("unavailable")

    monkeypatch.setattr("app.core.turnstile.httpx.AsyncClient", FakeClient)
    with pytest.raises(HTTPException) as exc:
        await verify_turnstile("valid-token", _request(), _settings())
    assert exc.value.status_code == 503
