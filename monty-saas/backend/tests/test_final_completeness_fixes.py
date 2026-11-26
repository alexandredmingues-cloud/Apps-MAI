from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from dataclasses import replace
import importlib.util
from pathlib import Path
import sys
import types
import unittest

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import load_settings  # noqa: E402
from app.core.rbac import MontyRole, Permission, TenantContext  # noqa: E402
from app.db.postgres_portfolio_leads_repository import PostgresPortfolioLeadsRepository  # noqa: E402
from app.domain.ai_agents_service import MontyAgentsGovernanceService  # noqa: E402
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.crm_service import ContactRole, CrmService  # noqa: E402
from app.domain.interconnections_service import InterconnectionsService  # noqa: E402
from app.domain.portfolio_and_leads_service import PortfolioAndLeadsService  # noqa: E402
from app.domain.real_estate_engines import (  # noqa: E402
    BuyerMatchInput,
    PropertyMatchInput,
    compute_compatibility,
)


class _FakeResult:
    def __init__(self, rows: list[dict] | None = None) -> None:
        self._rows = rows or []

    def mappings(self) -> "_FakeResult":
        return self

    def all(self) -> list[dict]:
        return list(self._rows)

    def first(self) -> dict | None:
        return self._rows[0] if self._rows else None

    def one(self) -> dict:
        if not self._rows:
            raise AssertionError("Expected 1 row")
        return self._rows[0]


class _FakePortfolioDbSession:
    def __init__(self) -> None:
        self.notifications: list[dict] = []
        self.property_row: dict = {
            "id": "00000000-0000-0000-0000-000000000101",
            "agency_id": "11111111-1111-1111-1111-111111111111",
            "title": "T3 Lyon Part-Dieu",
            "sector": "Lyon 3e",
            "property_type": "Appartement",
            "price_euro": 490000,
            "surface_m2": 75,
            "bedrooms": 2,
            "dpe": "B",
            "mandate_type": "EXCLUSIF",
            "docs_ratio": 0.85,
            "created_at": "2026-11-25T10:00:00Z",
        }
        self.buyers: list[dict] = [
            {"full_name": "Camille Laurent", "sector": "Lyon", "budget_max": 420000}
        ]

    async def execute(self, stmt, params=None):
        sql = " ".join(str(stmt).split())
        p = params or {}
        if "UPDATE properties" in sql:
            if p.get("pid") != self.property_row["id"]:
                return _FakeResult([])
            if p.get("price") is not None:
                self.property_row["price_euro"] = int(p["price"])
            if p.get("title") is not None:
                self.property_row["title"] = str(p["title"])
            if p.get("sector") is not None:
                self.property_row["sector"] = str(p["sector"])
            return _FakeResult([dict(self.property_row)])
        if "FROM crm_contacts" in sql:
            return _FakeResult(list(self.buyers))
        if "INSERT INTO agency_notifications" in sql:
            self.notifications.append(dict(p))
            return _FakeResult([])
        return _FakeResult([])


class OpenAIProviderTests(unittest.TestCase):
    def test_openai_live_transport_and_failover_from_anthropic(self) -> None:
        svc = MontyAgentsGovernanceService()
        base = load_settings()
        settings = replace(
            base,
            anthropic_api_key="sk-ant-failing",
            anthropic_api_keys=("sk-ant-failing",),
            openai_api_key="sk-openai-live-test",
            openai_model_fast="gpt-4.1-mini",
            openai_model_complex="gpt-4.1",
        )
        ctx = TenantContext(user_id="usr-dir", agency_id="ag-openai", role=MontyRole.DIRECTOR)

        seen_calls: list[tuple[str, str, str]] = []

        def fake_anthropic(url, headers, payload, timeout):
            seen_calls.append(("anthropic", headers["x-api-key"], payload["model"]))
            return 429, {"error": "rate_limited"}

        def fake_openai(url, headers, payload, timeout):
            seen_calls.append(("openai", headers["Authorization"], payload["model"]))
            return 200, {
                "choices": [
                    {"message": {"content": "Analyse DVF : prix cohérent avec les comparables IQR."}}
                ]
            }

        log = svc.run_agent(
            ctx,
            agent_id="dvf_analyst",
            prompt_text="Evaluer un T3 à Lyon 6e sans PII",
            settings=settings,
            anthropic_transport=fake_anthropic,
            openai_transport=fake_openai,
        )
        self.assertEqual(log.model_used, "gpt-4.1")
        self.assertIn("Analyse DVF", log.output_summary)
        self.assertEqual(seen_calls[0][0], "anthropic")
        self.assertEqual(seen_calls[1], ("openai", "Bearer sk-openai-live-test", "gpt-4.1"))

    def test_openai_http_error_falls_back_honestly_to_deterministic(self) -> None:
        svc = MontyAgentsGovernanceService()
        base = load_settings()
        settings = replace(
            base,
            anthropic_api_key="",
            anthropic_api_keys=(),
            openai_api_key="sk-openai-down",
            openai_model_fast="gpt-4.1-mini",
        )
        ctx = TenantContext(user_id="usr-ag", agency_id="ag-openai-err", role=MontyRole.AGENT)

        def failing_openai(url, headers, payload, timeout):
            return 503, {"error": "unavailable"}

        log = svc.run_agent(
            ctx,
            agent_id="buyer_qualifier",
            prompt_text="Acquéreur budget 350000 EUR Lyon",
            settings=settings,
            openai_transport=failing_openai,
        )
        self.assertEqual(log.model_used, "deterministic-fallback-safe")


class PostgresBillingReadAndRbacTests(unittest.TestCase):
    def test_postgres_billing_repository_reads_agency_plan_and_defaults(self) -> None:
        added_stubs: list[str] = []
        if importlib.util.find_spec("sqlalchemy") is None:
            sa_mod = types.ModuleType("sqlalchemy")
            sa_mod.text = lambda s: s  # type: ignore[attr-defined]
            sa_ext = types.ModuleType("sqlalchemy.ext")
            sa_ext_async = types.ModuleType("sqlalchemy.ext.asyncio")
            sa_ext_async.AsyncEngine = object  # type: ignore[attr-defined]
            sa_ext_async.AsyncSession = object  # type: ignore[attr-defined]
            sa_ext_async.async_sessionmaker = object  # type: ignore[attr-defined]
            sa_ext_async.create_async_engine = lambda *a, **kw: None  # type: ignore[attr-defined]
            sa_orm = types.ModuleType("sqlalchemy.orm")
            sys.modules["sqlalchemy"] = sa_mod
            sys.modules["sqlalchemy.ext"] = sa_ext
            sys.modules["sqlalchemy.ext.asyncio"] = sa_ext_async
            sys.modules["sqlalchemy.orm"] = sa_orm
            added_stubs.extend(["sqlalchemy", "sqlalchemy.ext", "sqlalchemy.ext.asyncio", "sqlalchemy.orm"])

        try:
            from app.db.postgres_repositories import PostgresBillingRepository

            ctx = TenantContext(
                user_id="11111111-1111-1111-1111-111111111111",
                agency_id="22222222-2222-2222-2222-222222222222",
                role=MontyRole.DIRECTOR,
            )

            class _FakeSession:
                def __init__(self, row: dict | None) -> None:
                    self._row = row

                async def __aenter__(self):
                    return self

                async def __aexit__(self, exc_type, exc, tb):
                    return False

                @asynccontextmanager
                async def begin(self):
                    yield self

                async def execute(self, stmt, params=None):
                    sql = " ".join(str(stmt).split())
                    if "FROM agencies" in sql:
                        return _FakeResult([self._row] if self._row else [])
                    return _FakeResult([])

            repo_ok = PostgresBillingRepository(
                lambda: _FakeSession(  # type: ignore[arg-type]
                    {
                        "agency_id": "22222222-2222-2222-2222-222222222222",
                        "plan_code": "NETWORK",
                        "subscription_status": "ACTIVE",
                        "seat_limit": 50,
                        "stripe_customer_id": "cus_123",
                        "stripe_subscription_id": "sub_456",
                    }
                )
            )
            state = asyncio.run(repo_ok.get_agency_billing(ctx))
            self.assertEqual(state.plan_code, "NETWORK")
            self.assertEqual(state.subscription_status, "ACTIVE")
            self.assertEqual(state.seat_limit, 50)

            repo_empty = PostgresBillingRepository(lambda: _FakeSession(None))  # type: ignore[arg-type]
            fallback = asyncio.run(repo_empty.get_agency_billing(ctx))
            self.assertEqual(fallback.plan_code, "INDIVIDUAL")
            self.assertEqual(fallback.subscription_status, "TRIAL")
        finally:
            for mod_name in added_stubs:
                sys.modules.pop(mod_name, None)

    def test_billing_router_wired_to_postgres_and_rbac_enforced(self) -> None:
        billing_router_src = (BACKEND_DIR / "app" / "api" / "v1" / "billing_router.py").read_text(encoding="utf-8")
        self.assertIn("PostgresBillingRepository(sf).get_agency_billing(ctx)", billing_router_src)
        self.assertIn("Permission.BILLING_MANAGE", billing_router_src)

        agent_ctx = TenantContext(user_id="u-ag", agency_id="ag-1", role=MontyRole.AGENT)
        director_ctx = TenantContext(user_id="u-dir", agency_id="ag-1", role=MontyRole.DIRECTOR)
        with self.assertRaises(PermissionError):
            agent_ctx.require_permission(Permission.BILLING_MANAGE)
        director_ctx.require_permission(Permission.BILLING_MANAGE)


class AutoMatchingCompletenessTests(unittest.TestCase):
    def test_update_property_domain_and_postgres_emit_matching_notifications(self) -> None:
        crm = CrmService()
        inter = InterconnectionsService()
        portfolio = PortfolioAndLeadsService(crm, inter)
        director = TenantContext(user_id="usr-dir", agency_id="agency-france-reseau", role=MontyRole.DIRECTOR)

        prop = portfolio.create_property(
            director,
            title="T3 Caluire Bord de Saône",
            sector="Lyon 4e",
            property_type="Appartement",
            price=560000,
            surface=76,
            bedrooms=2,
            dpe="B",
        )
        # Baisse de prix rendant le bien compatible avec Camille Laurent (420k€ à Lyon)
        updated = portfolio.update_property(
            director,
            property_id=prop.id,
            price=405000,
            sector="Lyon",
        )
        self.assertEqual(updated.price, 405000)
        notifs = inter.list_notifications(director)
        self.assertTrue(
            any(n.target_id == prop.id and n.event_type == "BUYER_MATCH_FOUND" for n in notifs)
        )

        # Isolation multi-tenant lors de la mise à jour
        other_ctx = TenantContext(user_id="usr-other", agency_id="agency-autre", role=MontyRole.DIRECTOR)
        with self.assertRaises(PermissionError):
            portfolio.update_property(other_ctx, property_id=prop.id, price=399000)

        # Vérification PostgresPortfolioLeadsRepository.update_property
        fake_db = _FakePortfolioDbSession()

        @asynccontextmanager
        async def _fake_tenant_cm(_sf, _ctx):
            yield fake_db

        pg_repo = PostgresPortfolioLeadsRepository(
            session_factory=object(),
            tenant_session_cm=_fake_tenant_cm,
            text_fn=lambda s: s,
        )
        pg_updated = asyncio.run(
            pg_repo.update_property(
                director,
                property_id="00000000-0000-0000-0000-000000000101",
                price=410000,
            )
        )
        self.assertEqual(pg_updated.price, 410000)
        self.assertEqual(len(fake_db.notifications), 1)
        self.assertEqual(fake_db.notifications[0]["etype"], "BUYER_MATCH_FOUND")

    def test_buyer_creation_matching_and_portfolio_patch_wiring(self) -> None:
        crm = CrmService()
        inter = InterconnectionsService()
        portfolio = PortfolioAndLeadsService(crm, inter)
        director = TenantContext(user_id="usr-dir", agency_id="agency-france-reseau", role=MontyRole.DIRECTOR)

        buyer = crm.create_contact(
            director,
            full_name="Nadia Test Match",
            email="nadia.match@exemple.fr",
            role=ContactRole.BUYER,
            country_code=CountryCode.FR,
            city="Lyon",
            budget_euro=430000,
        )
        matched_props = []
        for p in portfolio.list_properties(director):
            res = compute_compatibility(
                PropertyMatchInput(
                    price=p.price,
                    estimated_works=0,
                    sector=p.sector,
                    property_type=p.property_type,
                    surface=p.surface,
                    bedrooms=p.bedrooms,
                    dpe=p.dpe,
                ),
                BuyerMatchInput(
                    budget_max=buyer.budget_euro,
                    sectors=[buyer.city] if buyer.city else [p.sector],
                    property_type=p.property_type,
                    min_surface=max(10, p.surface - 15),
                    min_rooms=max(1, p.bedrooms),
                    essential_criteria=[],
                ),
            )
            if res.score >= 65:
                matched_props.append(p.title)
        self.assertGreater(len(matched_props), 0)

        portfolio_router_src = (BACKEND_DIR / "app" / "api" / "v1" / "portfolio_router.py").read_text(encoding="utf-8")
        self.assertIn('@portfolio_router.patch("/properties/{property_id}")', portfolio_router_src)
        self.assertIn("update_property", portfolio_router_src)

        crm_router_src = (BACKEND_DIR / "app" / "api" / "v1" / "crm_router.py").read_text(encoding="utf-8")
        self.assertIn("BUYER_MATCH_FOUND", crm_router_src)


if __name__ == "__main__":
    unittest.main()
