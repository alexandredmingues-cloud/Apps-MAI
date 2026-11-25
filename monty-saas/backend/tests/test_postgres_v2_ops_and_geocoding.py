from __future__ import annotations

from contextlib import asynccontextmanager
import json
from pathlib import Path
import sys
import unittest

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.db.postgres_v2_ops_repository import PostgresV2OpsRepository  # noqa: E402
from app.domain.ai_agents_service import AgentExecutionLog  # noqa: E402
from app.domain.authorized_feeds_service import (  # noqa: E402
    AuthorizedListingFeedsService,
    FeedFormat,
)
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.geocoding_service import (  # noqa: E402
    GeocodingResult,
    clear_geocode_cache,
    geocode_french_address,
)
from app.domain.marketplace_service import MarketplaceService, PublisherType  # noqa: E402
from app.domain.real_estate_engines import DvfComparable  # noqa: E402
from app.workers.redis_tasks import run_authorized_feeds_sync_job  # noqa: E402


class _FakeResult:
    def __init__(self, rows: list[dict] | None = None, scalar_val=None) -> None:
        self._rows = rows or []
        self._scalar = scalar_val

    def mappings(self) -> "_FakeResult":
        return self

    def all(self) -> list:
        return list(self._rows)

    def first(self):
        return self._rows[0] if self._rows else None

    def one(self):
        if not self._rows:
            raise AssertionError("Expected 1 row")
        return self._rows[0]

    def scalar(self):
        return self._scalar


class _FakeAsyncStore:
    def __init__(self) -> None:
        self.kill_switch_enabled = False
        self.kill_switch_reason = ""
        self.agent_logs: list[dict] = []
        self.scout_proposals: dict[str, dict] = {}
        self.pre_etat_dossiers: list[dict] = []
        self.whatsapp_events: list[dict] = []
        self.dvf_cache: dict[str, dict] = {}
        self.feeds: dict[str, dict] = {}
        self.feed_items: set[tuple[str, str]] = set()
        self.tenant_configs: list[tuple[str, str]] = []
        self.seq = 0

    def next_id(self, prefix: str) -> str:
        self.seq += 1
        return f"{prefix}-{self.seq}"


class _FakeSession:
    def __init__(self, store: _FakeAsyncStore, agency_ctx: str = "") -> None:
        self._store = store
        self._agency_ctx = agency_ctx

    @asynccontextmanager
    async def begin(self):
        yield self

    async def execute(self, stmt, params=None):
        sql = " ".join(str(stmt).split())
        p = params or {}

        if "INSERT INTO pre_etat_date_dossiers" in sql:
            did = self._store.next_id("ped")
            self._store.pre_etat_dossiers.insert(
                0,
                {
                    "id": did,
                    "agency_id": p["aid"],
                    "dossier_ref": p["dref"],
                    "document_version": p["ver"],
                    "export_allowed": p["allowed"],
                    "result_json": json.loads(p["result"]),
                    "created_at": "2026-11-25T10:00:00Z",
                },
            )
            return _FakeResult([{"id": did, 0: did}])

        if "FROM pre_etat_date_dossiers" in sql:
            rows = [d for d in self._store.pre_etat_dossiers if d["agency_id"] == self._agency_ctx]
            return _FakeResult(rows)

        if "SELECT kill_switch_enabled, kill_switch_reason FROM agent_governance_state" in sql:
            return _FakeResult(
                [{"kill_switch_enabled": self._store.kill_switch_enabled, "kill_switch_reason": self._store.kill_switch_reason}]
            )

        if "INSERT INTO agent_governance_state" in sql:
            self._store.kill_switch_enabled = bool(p["enabled"])
            self._store.kill_switch_reason = str(p["reason"])
            return _FakeResult()

        if "SELECT COALESCE(SUM(cost_euro), 0.0) FROM agent_execution_logs" in sql:
            total = sum(r["cost_euro"] for r in self._store.agent_logs if r["agency_id"] == p["aid"])
            return _FakeResult(scalar_val=total)

        if "INSERT INTO agent_execution_logs" in sql:
            lid = self._store.next_id("alog")
            row = {
                "id": lid,
                "agency_id": p["aid"],
                "actor_user_id": p["uid"],
                "agent_id": p["agent_id"],
                "model_used": p["model_used"],
                "sanitized_input": p["sanitized_input"],
                "output_summary": p["output_summary"],
                "duration_ms": p["duration_ms"],
                "cost_euro": p["cost_euro"],
                "cache_hit": p["cache_hit"],
                "created_at": "2026-11-25T10:05:00Z",
            }
            self._store.agent_logs.insert(0, row)
            return _FakeResult([row])

        if "FROM agent_execution_logs" in sql:
            rows = [r for r in self._store.agent_logs if r["agency_id"] == self._agency_ctx][: p.get("lim", 30)]
            return _FakeResult(rows)

        if "INSERT INTO scout_proposals" in sql:
            sid = self._store.next_id("sct")
            row = {
                "id": sid,
                "agency_id": p["aid"],
                "created_by_user_id": p["uid"],
                "target_contact_name": p["target"],
                "channel": p["channel"],
                "legal_basis": p["basis"],
                "opt_out_keyword": "STOP",
                "message_draft": p["draft"],
                "status": "A_VALIDER",
                "reviewed_by_user_id": None,
                "created_at": "2026-11-25T10:10:00Z",
                "reviewed_at": None,
            }
            self._store.scout_proposals[sid] = row
            return _FakeResult([row])

        if "UPDATE scout_proposals" in sql:
            row = self._store.scout_proposals.get(p["pid"])
            if row is None or row["agency_id"] != self._agency_ctx:
                return _FakeResult([])
            row["status"] = p["status"]
            row["reviewed_by_user_id"] = p["uid"]
            row["reviewed_at"] = "2026-11-25T10:11:00Z"
            return _FakeResult([row])

        if "FROM scout_proposals" in sql:
            rows = [r for r in self._store.scout_proposals.values() if r["agency_id"] == self._agency_ctx]
            return _FakeResult(rows)

        if "INSERT INTO whatsapp_consent_events" in sql:
            wid = self._store.next_id("wa")
            self._store.whatsapp_events.insert(
                0,
                {
                    "id": wid,
                    "phone_masked": p["masked"],
                    "phone_hash": p["phash"],
                    "direction": p["direction"],
                    "action": p["action"],
                    "keyword_matched": p["kw"],
                },
            )
            return _FakeResult([{0: wid}])

        if "FROM whatsapp_consent_events" in sql:
            for ev in self._store.whatsapp_events:
                if ev["phone_hash"] == p["ph"] and ev["action"] in ("OPT_OUT", "OPT_IN"):
                    return _FakeResult([{0: ev["action"]}])
            return _FakeResult([])

        if "INSERT INTO dvf_mutations_cache" in sql:
            self._store.dvf_cache[p["mutation_id"]] = dict(p)
            return _FakeResult()

        if "FROM dvf_mutations_cache GROUP BY sector_code" in sql:
            grouped: dict[str, int] = {}
            for item in self._store.dvf_cache.values():
                grouped[item["sector_code"]] = grouped.get(item["sector_code"], 0) + 1
            return _FakeResult(
                [
                    {"sector_code": sec, "cnt": cnt, "last_sync": "2026-11-25T10:15:00Z"}
                    for sec, cnt in sorted(grouped.items())
                ]
            )

        if "INSERT INTO authorized_listing_feeds" in sql:
            fid = self._store.next_id("feed")
            row = {
                "id": fid,
                "agency_id": p["aid"],
                "name": p["name"],
                "country_code": p["cc"],
                "publisher_type": p["pub_type"],
                "feed_format": p["fmt"],
                "feed_url": p["url"],
                "agreement_confirmed": True,
                "respect_robots_txt": True,
                "auto_publish_verified_agency": bool(p["auto_pub"]),
                "last_synced_at": None,
                "last_status": "READY",
                "total_ingested": 0,
                "created_at": "2026-11-25T10:20:00Z",
            }
            self._store.feeds[fid] = row
            return _FakeResult([row])

        if "FROM authorized_listing_feeds WHERE id =" in sql:
            row = self._store.feeds.get(p["fid"])
            if row is None or row["agency_id"] != self._agency_ctx:
                return _FakeResult([])
            return _FakeResult([row])

        if "FROM authorized_listing_feeds ORDER BY" in sql:
            rows = [r for r in self._store.feeds.values() if r["agency_id"] == self._agency_ctx]
            return _FakeResult(rows)

        if "SELECT external_id FROM authorized_feed_items" in sql:
            rows = [{0: ext} for (fid, ext) in self._store.feed_items if fid == p["fid"]]
            return _FakeResult(rows)

        if "INSERT INTO authorized_feed_items" in sql:
            self._store.feed_items.add((p["fid"], p["ext_id"]))
            return _FakeResult()

        if "UPDATE authorized_listing_feeds" in sql:
            row = self._store.feeds.get(p["fid"])
            if row is not None and row["agency_id"] == self._agency_ctx:
                row["last_status"] = p["st"]
                row["last_synced_at"] = "2026-11-25T10:25:00Z"
                row["total_ingested"] += int(p["added"])
            return _FakeResult()

        raise AssertionError(f"Unhandled fake SQL: {sql}")


class _FakeSessionFactory:
    def __init__(self, store: _FakeAsyncStore) -> None:
        self.store = store

    @asynccontextmanager
    async def __call__(self):
        yield _FakeSession(self.store)


class BanGeocodingTests(unittest.TestCase):
    def setUp(self) -> None:
        clear_geocode_cache()

    def test_ban_geojson_resolution_and_caching_and_fallback(self) -> None:
        calls = 0

        def fake_ban_fetch(url: str) -> dict:
            nonlocal calls
            calls += 1
            self.assertIn("api-adresse.data.gouv.fr/search/", url)
            return {
                "features": [
                    {
                        "geometry": {"coordinates": [4.8592, 45.7605]},
                        "properties": {
                            "label": "5 Place Charles Béraudier 69003 Lyon",
                            "city": "Lyon",
                            "postcode": "69003",
                            "context": "69, Rhône, Auvergne-Rhône-Alpes",
                        },
                    }
                ]
            }

        res1 = geocode_french_address(
            "5 Place Charles Béraudier Lyon",
            country_code=CountryCode.FR,
            fallback_city="Lyon",
            fetcher=fake_ban_fetch,
        )
        self.assertEqual(res1.source, "ban_api_adresse")
        self.assertAlmostEqual(res1.latitude, 45.7605, places=4)
        self.assertAlmostEqual(res1.longitude, 4.8592, places=4)
        self.assertEqual(res1.postcode, "69003")
        self.assertEqual(res1.region_name, "Auvergne-Rhône-Alpes")

        # 2e appel identique servi depuis le cache sans nouvel appel réseau
        res2 = geocode_french_address(
            "5 Place Charles Béraudier Lyon",
            country_code=CountryCode.FR,
            fallback_city="Lyon",
            fetcher=fake_ban_fetch,
        )
        self.assertEqual(calls, 1)
        self.assertEqual(res2, res1)

        # Erreur réseau => repli gracieux sur centroïde ville
        def failing_fetch(_url: str) -> dict:
            raise TimeoutError("BAN timeout")

        fb = geocode_french_address(
            "Quartier Saint-Jean Bordeaux",
            country_code=CountryCode.FR,
            fallback_city="Bordeaux",
            fetcher=failing_fetch,
        )
        self.assertEqual(fb.source, "fallback_city_centroid")
        self.assertAlmostEqual(fb.latitude, 44.8378, places=3)


class PostgresV2OpsRepositoryTests(unittest.IsolatedAsyncioTestCase):
    async def test_full_v2_postgres_persistence_and_rls_isolation(self) -> None:
        store = _FakeAsyncStore()
        sf = _FakeSessionFactory(store)

        @asynccontextmanager
        async def fake_tenant_cm(_sf, ctx: TenantContext):
            store.tenant_configs.append((ctx.agency_id, ctx.user_id))
            yield _FakeSession(store, agency_ctx=ctx.agency_id)

        repo = PostgresV2OpsRepository(sf, tenant_session_cm=fake_tenant_cm, text_fn=lambda s: s)
        dir_a = TenantContext(user_id="usr-a", agency_id="ag-a", role=MontyRole.DIRECTOR)
        dir_b = TenantContext(user_id="usr-b", agency_id="ag-b", role=MontyRole.DIRECTOR)

        # 1. Pré-État Daté persisté sous RLS
        ped_id = await repo.save_pre_etat_dossier(
            dir_a,
            dossier_ref="PED-2026-01",
            document_version="Monty-L721-2-v2.0",
            export_allowed=True,
            payload_dict={"dossier_ref": "PED-2026-01"},
            result_dict={"export_allowed": True},
        )
        self.assertTrue(ped_id.startswith("ped-"))
        self.assertEqual(len(await repo.list_pre_etat_dossiers(dir_a)), 1)
        self.assertEqual(len(await repo.list_pre_etat_dossiers(dir_b)), 0)

        # 2. Kill-switch & logs agents
        await repo.set_kill_switch_state(dir_a, enabled=True, reason="Maintenance Anthropic")
        ks_on, ks_reason = await repo.get_kill_switch_state()
        self.assertTrue(ks_on)
        self.assertEqual(ks_reason, "Maintenance Anthropic")

        saved_log = await repo.record_agent_log(
            dir_a,
            AgentExecutionLog(
                id="tmp",
                agency_id=dir_a.agency_id,
                actor_user_id=dir_a.user_id,
                agent_id="dvf_analyst",
                agent_name="Analyste DVF & Score Monty",
                model_used="claude-3-5-sonnet",
                sanitized_input="Analyser Lyon 3e",
                output_summary="Fourchette 4950 €/m2",
                duration_ms=145,
                cost_euro=0.018,
                cache_hit=False,
                created_at="2026-11-25T10:00:00Z",
            ),
        )
        self.assertTrue(saved_log.id.startswith("alog-"))
        self.assertAlmostEqual(await repo.get_daily_spend(dir_a), 0.018, places=4)
        self.assertEqual(len(await repo.list_agent_logs(dir_a)), 1)
        self.assertEqual(len(await repo.list_agent_logs(dir_b)), 0)

        # 3. Monty Scout CRUD + isolation inter-agences
        prop = await repo.create_scout_proposal(
            dir_a,
            target_contact_name="Camille",
            channel="WHATSAPP",
            legal_basis="INTERET_LEGITIME_PROSPECTION_B2C",
            message_draft="Bonjour Camille, un bien vient de correspondre à votre recherche.",
        )
        self.assertIn("STOP", prop.message_draft)
        with self.assertRaises(ValueError):
            await repo.review_scout_proposal(dir_b, proposal_id=prop.id, approve=True)
        approved = await repo.review_scout_proposal(dir_a, proposal_id=prop.id, approve=True)
        self.assertEqual(approved.status, "VALIDEE")

        # 4. WhatsApp STOP persistant par hash téléphone
        self.assertFalse(await repo.is_phone_opted_out("+33612345678"))
        await repo.record_whatsapp_event(
            phone="+33612345678", direction="INBOUND", action="OPT_OUT", keyword_matched="STOP"
        )
        self.assertTrue(await repo.is_phone_opted_out("+33612345678"))

        # 5. DVF Cache
        inserted = await repo.upsert_dvf_comparables(
            "69003",
            [DvfComparable("m-1", "2026-05-10", 2026, 350000, 70, "Appartement", 80)],
        )
        self.assertEqual(inserted, 1)
        dvf_status = await repo.list_dvf_cache_status()
        self.assertEqual(dvf_status["sectors_cached"], ["69003"])
        self.assertEqual(dvf_status["total_comparables_cached"], 1)

        # 6. Flux partenaires + déduplication persistante + BAN geocoding
        feed = await repo.register_feed(
            dir_a,
            name="Syndication Orpi Partner",
            country_code=CountryCode.FR,
            publisher_type=PublisherType.AGENCE,
            feed_format=FeedFormat.JSON,
            feed_url="https://partner.example.com/feed.json",
            agreement_confirmed=True,
            auto_publish_verified_agency=True,
        )
        market = MarketplaceService()
        moderated_ids: list[str] = []

        async def fake_publish(ctx, **kwargs):
            return market.publish_listing(ctx=ctx, **kwargs)

        async def fake_moderate(ctx, *, listing_id: str, approve: bool, moderation_note: str = ""):
            moderated_ids.append(listing_id)
            return market.moderate_listing(ctx, listing_id=listing_id, approve=approve, moderation_note=moderation_note)

        payload = json.dumps(
            {
                "listings": [
                    {
                        "external_id": "EXT-99",
                        "title": "T3 lumineux Croix-Rousse",
                        "city": "Lyon",
                        "sector_label": "Croix-Rousse",
                        "price_euro": 395000,
                        "surface_m2": 75,
                        "rooms": 3,
                        "description": "Bel appartement rénové proche métro sans vis-à-vis.",
                    }
                ]
            }
        )
        sync1 = await repo.sync_feed_to_postgres(
            dir_a,
            feed_id=feed.id,
            inline_payload=payload,
            geocoder=lambda q, cc, city: GeocodingResult(45.7745, 4.8321, q, city, "Auvergne-Rhône-Alpes", 4950, "69004", "ban_api_adresse"),
            publish_fn=fake_publish,
            moderate_fn=fake_moderate,
        )
        self.assertEqual(sync1.accepted_count, 1)
        self.assertEqual(len(moderated_ids), 1)
        self.assertAlmostEqual(market._listings[0].latitude, 45.7745, places=4)

        # 2e sync identique => 100% dédupliquée via authorized_feed_items
        sync2 = await repo.sync_feed_to_postgres(
            dir_a,
            feed_id=feed.id,
            inline_payload=payload,
            geocoder=lambda q, cc, city: GeocodingResult(45.7745, 4.8321, q, city, "Auvergne-Rhône-Alpes", 4950, "69004", "ban_api_adresse"),
            publish_fn=fake_publish,
            moderate_fn=fake_moderate,
        )
        self.assertEqual(sync2.accepted_count, 0)
        self.assertEqual(sync2.skipped_duplicates, 1)

    async def test_scheduled_authorized_feeds_worker_job(self) -> None:
        market = MarketplaceService()
        feeds = AuthorizedListingFeedsService(market)
        director = TenantContext(user_id="usr-d", agency_id="ag-cron", role=MontyRole.DIRECTOR)
        feeds.register_feed(
            director,
            name="Flux Cron Agence",
            country_code=CountryCode.FR,
            publisher_type=PublisherType.AGENCE,
            feed_format=FeedFormat.JSON,
            feed_url="https://partner.example.org/cron.json",
            agreement_confirmed=True,
        )
        report = await run_authorized_feeds_sync_job(
            {
                "feeds_service": feeds,
                "robots_fetcher": lambda _u: "User-agent: *\nAllow: /",
                "fetcher": lambda _u: json.dumps(
                    {
                        "listings": [
                            {
                                "external_id": "CRON-1",
                                "title": "Maison familiale jardin clos",
                                "city": "Bordeaux",
                                "price_euro": 510000,
                                "surface_m2": 110,
                                "rooms": 5,
                                "description": "Maison lumineuse proche tram avec beau jardin arboré.",
                            }
                        ]
                    }
                ),
            }
        )
        self.assertEqual(report["status"], "authorized_feeds_sync_completed")
        self.assertEqual(report["feeds_processed"], 1)
        self.assertEqual(report["reports"][0]["accepted_count"], 1)


class RouterAndMigrationWiringTests(unittest.TestCase):
    def test_migration_0007_and_routers_wired(self) -> None:
        sql0007 = (BACKEND_DIR / "alembic" / "versions" / "0007_v2_dedup_and_kill_switch.sql").read_text(encoding="utf-8")
        self.assertIn("CREATE TABLE IF NOT EXISTS authorized_feed_items", sql0007)
        self.assertIn("FORCE ROW LEVEL SECURITY", sql0007)
        self.assertIn("CREATE TABLE IF NOT EXISTS agent_governance_state", sql0007)

        legal_router = (BACKEND_DIR / "app" / "api" / "v1" / "legal_agents_router.py").read_text(encoding="utf-8")
        self.assertIn("PostgresV2OpsRepository", legal_router)
        self.assertIn("save_pre_etat_dossier", legal_router)
        self.assertIn("is_phone_opted_out", legal_router)

        market_router = (BACKEND_DIR / "app" / "api" / "v1" / "marketplace_router.py").read_text(encoding="utf-8")
        self.assertIn("geocode_french_address", market_router)
        self.assertIn("PostgresV2OpsRepository", market_router)


if __name__ == "__main__":
    unittest.main()
