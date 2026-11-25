from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from pathlib import Path
import sys
import unittest

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.db.postgres_portfolio_leads_repository import PostgresPortfolioLeadsRepository  # noqa: E402
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.crm_service import CrmService  # noqa: E402
from app.domain.portfolio_and_leads_service import (  # noqa: E402
    LeadKind,
    LeadStatus,
    PortfolioAndLeadsService,
    TransactionStage,
    VisitStatus,
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


class _FakeStore:
    def __init__(self) -> None:
        self.properties: list[dict] = []
        self.visits: list[dict] = []
        self.offers: list[dict] = []
        self.public_leads: list[dict] = []
        self.tenant_contexts: list[str] = []
        self.seq = 0

    def next_uuid(self) -> str:
        self.seq += 1
        return f"00000000-0000-0000-0000-{self.seq:012d}"


class _FakeSession:
    def __init__(self, store: _FakeStore, agency_ctx: str = "") -> None:
        self._store = store
        self._agency_ctx = agency_ctx

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False

    @asynccontextmanager
    async def begin(self):
        yield self

    async def execute(self, stmt, params=None):
        sql = " ".join(str(stmt).split())
        p = params or {}

        if "INSERT INTO properties" in sql:
            pid = self._store.next_uuid()
            row = {
                "id": pid,
                "agency_id": p["aid"],
                "title": p["title"],
                "sector": p["sector"],
                "property_type": p["kind"],
                "price_euro": p["price"],
                "surface_m2": p["surface"],
                "bedrooms": p["bedrooms"],
                "dpe": p["dpe"],
                "mandate_type": p["mandate"],
                "docs_ratio": p["docs_ratio"],
                "created_at": "2026-11-25T10:00:00Z",
            }
            self._store.properties.insert(0, row)
            return _FakeResult([row])

        if "SELECT id, title FROM properties WHERE id =" in sql:
            matches = [r for r in self._store.properties if r["id"] == p["pid"] and r["agency_id"] == self._agency_ctx]
            return _FakeResult(matches)

        if "SELECT id, title, price_euro FROM properties WHERE id =" in sql:
            matches = [r for r in self._store.properties if r["id"] == p["pid"] and r["agency_id"] == self._agency_ctx]
            return _FakeResult(matches)

        if "FROM properties" in sql:
            rows = [r for r in self._store.properties if r["agency_id"] == self._agency_ctx]
            return _FakeResult(rows)

        if "INSERT INTO visits" in sql:
            vid = self._store.next_uuid()
            row = {
                "id": vid,
                "agency_id": p["aid"],
                "property_id": p["pid"],
                "property_title": p["ptitle"],
                "buyer_name": p["buyer"],
                "date_label": p["dlabel"],
                "visit_date": "2026-11-25",
                "time_slot": p["slot"],
                "status": "PLANIFIEE",
                "client_impressions": p["feedback"],
                "created_at": "2026-11-25T10:05:00Z",
            }
            self._store.visits.insert(0, row)
            return _FakeResult([row])

        if "UPDATE visits" in sql:
            for r in self._store.visits:
                if r["id"] == p["vid"] and r["agency_id"] == self._agency_ctx:
                    r["status"] = p["st"]
                    if p.get("fb"):
                        r["client_impressions"] = p["fb"]
                    return _FakeResult([r])
            return _FakeResult([])

        if "FROM visits v" in sql:
            rows = [r for r in self._store.visits if r["agency_id"] == self._agency_ctx]
            return _FakeResult(rows)

        if "INSERT INTO offers" in sql:
            oid = self._store.next_uuid()
            row = {
                "id": oid,
                "agency_id": p["aid"],
                "property_id": p["pid"],
                "property_title": p["ptitle"],
                "buyer_name": p["buyer"],
                "offered_price": p["offered"],
                "stage": p["stage"],
                "notary_target_date": p["nt_date"],
                "created_at": "2026-11-25T10:10:00Z",
            }
            self._store.offers.insert(0, row)
            return _FakeResult([row])

        if "UPDATE offers" in sql:
            for r in self._store.offers:
                if r["id"] == p["tid"] and r["agency_id"] == self._agency_ctx:
                    r["stage"] = p["st"]
                    return _FakeResult([r])
            return _FakeResult([])

        if "FROM offers o" in sql:
            rows = [r for r in self._store.offers if r["agency_id"] == self._agency_ctx]
            return _FakeResult(rows)

        if "FROM monty_submit_public_lead" in sql:
            lid = self._store.next_uuid()
            cid = self._store.next_uuid()
            aid = p.get("aid") or "11111111-1111-1111-1111-111111111111"
            lead_row = {
                "id": lid,
                "agency_id": aid,
                "crm_contact_id": cid,
                "lead_kind": p["lead_kind"],
                "country_code": p["cc"],
                "full_name": p["full_name"],
                "email": p["email"],
                "phone": p["phone"],
                "city": p["city"],
                "budget_euro": p["budget"],
                "min_rooms": p["rooms"],
                "search_transport_mode": p["mode"],
                "max_travel_minutes": p["minutes"],
                "horizon": p["horizon"],
                "property_title": p["prop_title"],
                "notes": p["notes"],
                "rgpd_consent_granted": True,
                "rgpd_consent_at": "2026-11-25T10:15:00Z",
                "status": "NEW",
                "created_at": "2026-11-25T10:15:00Z",
            }
            self._store.public_leads.insert(0, lead_row)
            return _FakeResult(
                [{"lead_id": lid, "agency_id": aid, "crm_contact_id": cid, "created_at": "2026-11-25T10:15:00Z"}]
            )

        if "UPDATE public_inbound_leads" in sql:
            for r in self._store.public_leads:
                if r["id"] == p["lid"] and r["agency_id"] == self._agency_ctx:
                    r["status"] = p["st"]
                    return _FakeResult([r])
            return _FakeResult([])

        if "FROM public_inbound_leads" in sql:
            rows = [r for r in self._store.public_leads if r["agency_id"] == self._agency_ctx]
            if "WHERE status = :st" in sql:
                rows = [r for r in rows if r["status"] == p["st"]]
            return _FakeResult(rows)

        return _FakeResult([])


class PortfolioAndPublicLeadsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.crm = CrmService()
        self.svc = PortfolioAndLeadsService(self.crm)
        self.dir_a = TenantContext(user_id="usr-director", agency_id="agency-france-reseau", role=MontyRole.DIRECTOR)
        self.dir_b = TenantContext(user_id="usr-b", agency_id="agency-autre", role=MontyRole.DIRECTOR)
        self.buyer = TenantContext(user_id="usr-buy", agency_id="agency-france-reseau", role=MontyRole.BUYER)

    def test_migration_0008_enforces_rls_and_rgpd_consent(self) -> None:
        sql_path = BACKEND_DIR / "alembic" / "versions" / "0008_portfolio_pipeline_and_public_leads.sql"
        sql = sql_path.read_text(encoding="utf-8")
        self.assertIn("ALTER TABLE public_inbound_leads ENABLE ROW LEVEL SECURITY;", sql)
        self.assertIn("ALTER TABLE public_inbound_leads FORCE ROW LEVEL SECURITY;", sql)
        self.assertIn("CREATE POLICY tenant_isolation_public_inbound_leads ON public_inbound_leads", sql)
        self.assertIn("CREATE OR REPLACE FUNCTION monty_submit_public_lead(", sql)
        self.assertIn("ALTER COLUMN buyer_dossier_id DROP NOT NULL", sql)
        self.assertIn("CHECK (rgpd_consent_granted = true)", sql)

    def test_portfolio_crud_and_tenant_isolation(self) -> None:
        created = self.svc.create_property(
            self.dir_a,
            title="T4 Haussmannien Bellecour",
            sector="Lyon 2e",
            property_type="Appartement",
            price=640000,
            surface=105,
            bedrooms=3,
            dpe="C",
            mandate="EXCLUSIF",
        )
        self.assertTrue(any(p.id == created.id for p in self.svc.list_properties(self.dir_a)))
        self.assertEqual(self.svc.list_properties(self.dir_b), [])

        visit = self.svc.create_visit(
            self.dir_a,
            property_id=created.id,
            buyer_name="Sophie Bernard",
            date_label="Samedi 10h30",
        )
        updated_visit = self.svc.update_visit_status(
            self.dir_a, visit_id=visit.id, status=VisitStatus.REALISEE, feedback="Très intéressée."
        )
        self.assertEqual(updated_visit.status, VisitStatus.REALISEE)

        tx = self.svc.create_transaction(
            self.dir_a,
            property_id=created.id,
            buyer_name="Sophie Bernard",
            offer_euro=625000,
            stage=TransactionStage.OFFRE,
            notary_target_date="2027-01-15",
        )
        tx_comp = self.svc.update_transaction_stage(
            self.dir_a, tx_id=tx.id, stage=TransactionStage.COMPROMIS
        )
        self.assertEqual(tx_comp.stage, TransactionStage.COMPROMIS)

        with self.assertRaises(PermissionError):
            self.svc.create_visit(
                self.dir_b,
                property_id=created.id,
                buyer_name="Intrusion",
                date_label="Lundi",
            )
        with self.assertRaises(PermissionError):
            self.svc.list_properties(self.buyer)

    def test_public_lead_rgpd_consent_and_crm_bridge(self) -> None:
        with self.assertRaises(ValueError):
            self.svc.submit_public_lead(
                lead_kind=LeadKind.BUYER_CONCIERGE,
                email="client@exemple.fr",
                city="Nantes",
                budget_euro=350000,
                rgpd_consent_granted=False,
            )

        lead = self.svc.submit_public_lead(
            lead_kind=LeadKind.BUYER_CONCIERGE,
            country_code=CountryCode.FR,
            email="marie.dubois@exemple.fr",
            city="Nantes",
            budget_euro=390000,
            min_rooms=3,
            horizon="Sous 3 mois",
            notes="Proche tram, merci d'appeler au 06 12 34 56 78",
            rgpd_consent_granted=True,
        )
        self.assertIsNotNone(lead.crm_contact_id)
        self.assertNotIn("06 12 34 56 78", lead.notes)
        self.assertIn("[tél-masqué-rgpd]", lead.notes)

        agency_leads = self.svc.list_public_leads(self.dir_a)
        self.assertEqual(len(agency_leads), 1)
        self.assertEqual(self.svc.list_public_leads(self.dir_b), [])

        crm_contacts = self.crm.list_contacts(self.dir_a, query="marie.dubois@exemple.fr")
        self.assertEqual(len(crm_contacts), 1)

        updated = self.svc.update_public_lead_status(
            self.dir_a, lead_id=lead.id, status=LeadStatus.CONTACTED
        )
        self.assertEqual(updated.status, LeadStatus.CONTACTED)

    def test_postgres_repository_rls_and_security_definer(self) -> None:
        store = _FakeStore()

        def sf():
            return _FakeSession(store)

        @asynccontextmanager
        async def tenant_cm(_sf, ctx: TenantContext):
            store.tenant_contexts.append(ctx.agency_id)
            yield _FakeSession(store, agency_ctx=ctx.agency_id)

        repo = PostgresPortfolioLeadsRepository(sf, tenant_session_cm=tenant_cm, text_fn=lambda s: s)
        aid_a = "11111111-1111-1111-1111-111111111111"
        aid_b = "22222222-2222-2222-2222-222222222222"
        ctx_a = TenantContext(user_id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", agency_id=aid_a, role=MontyRole.DIRECTOR)
        ctx_b = TenantContext(user_id="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", agency_id=aid_b, role=MontyRole.DIRECTOR)

        async def scenario():
            prop = await repo.create_property(
                ctx_a,
                title="T3 Persistant",
                sector="Lyon 6e",
                property_type="Appartement",
                price=450000,
                surface=80,
            )
            self.assertEqual(len(await repo.list_properties(ctx_a)), 1)
            self.assertEqual(len(await repo.list_properties(ctx_b)), 0)

            vis = await repo.create_visit(ctx_a, property_id=prop.id, buyer_name="Lucas", date_label="Mardi 18h")
            await repo.update_visit_status(ctx_a, visit_id=vis.id, status=VisitStatus.REALISEE)
            self.assertEqual((await repo.list_visits(ctx_a))[0].status, VisitStatus.REALISEE)

            tx = await repo.create_transaction(ctx_a, property_id=prop.id, buyer_name="Lucas", offer_euro=440000)
            await repo.update_transaction_stage(ctx_a, tx_id=tx.id, stage=TransactionStage.ACTE)
            self.assertEqual((await repo.list_transactions(ctx_a))[0].stage, TransactionStage.ACTE)

            lead = await repo.submit_public_lead(
                agency_id=aid_a,
                lead_kind=LeadKind.SELLER_VALUATION,
                email="vendeur@exemple.fr",
                city="Lyon",
                property_title="Maison 120 m²",
                rgpd_consent_granted=True,
            )
            self.assertEqual(lead.agency_id, aid_a)
            self.assertEqual(len(await repo.list_public_leads(ctx_a)), 1)
            self.assertEqual(len(await repo.list_public_leads(ctx_b)), 0)

        asyncio.run(scenario())
        self.assertIn(aid_a, store.tenant_contexts)


if __name__ == "__main__":
    unittest.main()
