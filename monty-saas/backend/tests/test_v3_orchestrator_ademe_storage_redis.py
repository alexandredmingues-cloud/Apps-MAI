from __future__ import annotations

import asyncio
from dataclasses import replace
import hashlib
import hmac
import json
import os
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import patch

BACKEND_DIR = pathlib.Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import load_settings  # noqa: E402
from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.core.security import SlidingWindowRateLimiter  # noqa: E402
from app.core.storage import ObjectStorageAdapter  # noqa: E402
from app.domain.ademe_dpe_service import AdemeDpeService  # noqa: E402
from app.domain.monty_ai_core import (  # noqa: E402
    MontyAICore,
    MontyAITaskType,
    is_platform_admin,
    validate_local_llm_url,
)
from app.domain.monty_price_index_service import (  # noqa: E402
    MontyPriceEngine,
    PriceIndexCategory,
)
from app.domain.real_estate_engines import DvfComparable  # noqa: E402


class _FakeRedisPipeline:
    def __init__(self, store: dict[str, dict[str, float]]) -> None:
        self._store = store
        self._ops: list[tuple[str, tuple]] = []

    def zremrangebyscore(self, key: str, min_score: float | str, max_score: float) -> "_FakeRedisPipeline":
        self._ops.append(("zremrangebyscore", (key, min_score, max_score)))
        return self

    def zcard(self, key: str) -> "_FakeRedisPipeline":
        self._ops.append(("zcard", (key,)))
        return self

    def zadd(self, key: str, mapping: dict[str, float]) -> "_FakeRedisPipeline":
        self._ops.append(("zadd", (key, mapping)))
        return self

    def expire(self, key: str, ttl: int) -> "_FakeRedisPipeline":
        self._ops.append(("expire", (key, ttl)))
        return self

    def execute(self) -> list:
        out: list = []
        for op, args in self._ops:
            if op == "zremrangebyscore":
                key, _min_s, max_s = args
                bucket = self._store.get(key, {})
                before = len(bucket)
                self._store[key] = {m: s for m, s in bucket.items() if s > float(max_s)}
                out.append(before - len(self._store[key]))
            elif op == "zcard":
                key = args[0]
                out.append(len(self._store.get(key, {})))
            elif op == "zadd":
                key, mapping = args
                bucket = self._store.setdefault(key, {})
                for member, score in mapping.items():
                    bucket[str(member)] = float(score)
                out.append(len(mapping))
            elif op == "expire":
                out.append(True)
        return out


class _FakeRedisClient:
    def __init__(self, *, fail: bool = False) -> None:
        self._fail = fail
        self._store: dict[str, dict[str, float]] = {}

    def pipeline(self) -> _FakeRedisPipeline:
        if self._fail:
            raise ConnectionError("Redis unavailable")
        return _FakeRedisPipeline(self._store)

    def zrem(self, key: str, member: str) -> int:
        bucket = self._store.get(key, {})
        return 1 if bucket.pop(str(member), None) is not None else 0

    def zrange(self, key: str, start: int, end: int, *, withscores: bool = False) -> list:
        items = sorted(self._store.get(key, {}).items(), key=lambda kv: kv[1])
        stop = None if end == -1 else end + 1
        sliced = items[start:stop]
        if withscores:
            return [(m, s) for m, s in sliced]
        return [m for m, _s in sliced]


class RedisRateLimiterAndStorageTests(unittest.TestCase):
    def test_redis_rate_limiter_enforces_limit_and_falls_back_gracefully(self) -> None:
        fake = _FakeRedisClient()
        limiter = SlidingWindowRateLimiter(redis_client=fake)

        self.assertTrue(limiter.check_and_consume("user-1", max_requests=2, window_seconds=60).allowed)
        self.assertTrue(limiter.check_and_consume("user-1", max_requests=2, window_seconds=60).allowed)
        third = limiter.check_and_consume("user-1", max_requests=2, window_seconds=60)
        self.assertFalse(third.allowed)
        self.assertEqual(third.remaining, 0)

        # Connection failure degrades smoothly to in-memory sliding window
        broken = SlidingWindowRateLimiter(redis_client=_FakeRedisClient(fail=True))
        self.assertTrue(broken.check_and_consume("user-2", max_requests=2, window_seconds=60).allowed)
        self.assertTrue(broken.check_and_consume("user-2", max_requests=2, window_seconds=60).allowed)
        self.assertFalse(broken.check_and_consume("user-2", max_requests=2, window_seconds=60).allowed)

    def test_object_storage_adapter_local_fallback_and_supabase_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            base = load_settings()
            settings = replace(
                base,
                supabase_url="",
                supabase_service_role_key="",
                s3_bucket="",
                s3_endpoint="",
            )
            adapter = ObjectStorageAdapter(settings, local_root=pathlib.Path(tmpdir))
            put_res = adapter.put_bytes(
                object_key="releases/monty-v1.apk",
                raw_bytes=b"PK\x03\x04monty-apk",
                content_type="application/vnd.android.package-archive",
            )
            self.assertEqual(put_res.backend, "local")
            self.assertEqual(
                pathlib.Path(put_res.local_path).read_bytes(),
                b"PK\x03\x04monty-apk",
            )
            with self.assertRaises(ValueError):
                adapter.put_bytes(object_key="../escape.apk", raw_bytes=b"x")

            # S3 presigned URL generation when S3 credentials are configured
            s3_settings = replace(
                base,
                supabase_url="",
                supabase_service_role_key="",
                s3_bucket="monty-vault",
                s3_endpoint="https://s3.eu-west-3.amazonaws.com",
                s3_access_key="AKIA_TEST",
                s3_secret_key="secret_key_for_sigv4_unit_test",
                s3_region="eu-west-3",
            )
            s3_adapter = ObjectStorageAdapter(s3_settings, local_root=pathlib.Path(tmpdir))
            presigned = s3_adapter.issue_signed_url(object_key="docs/compromis.pdf", expires_in_seconds=300)
            self.assertEqual(presigned.backend, "s3")
            self.assertIn("X-Amz-Signature=", presigned.signed_url)
            self.assertIn("monty-vault", presigned.signed_url)


class OrchestratorHardeningAndAdemePriceIndexTests(unittest.TestCase):
    def test_ssrf_guard_blocks_metadata_and_private_hosts_by_default(self) -> None:
        with self.assertRaises(ValueError):
            validate_local_llm_url("http://169.254.169.254/latest/meta-data/", allow_private=True)
        with self.assertRaises(ValueError):
            validate_local_llm_url("http://127.0.0.1:11434", allow_private=False)
        with self.assertRaises(ValueError):
            validate_local_llm_url("ftp://ollama.example.com", allow_private=False)
        self.assertEqual(
            validate_local_llm_url("http://127.0.0.1:11434/", allow_private=True),
            "http://127.0.0.1:11434",
        )
        self.assertEqual(
            validate_local_llm_url("https://llm.monty-agency.fr", allow_private=False),
            "https://llm.monty-agency.fr",
        )

    def test_platform_admin_is_strictly_fail_closed(self) -> None:
        base = load_settings()
        super_ctx = TenantContext(user_id="usr-admin-1", agency_id="ag-1", role=MontyRole.SUPER_ADMIN)
        dir_ctx = TenantContext(user_id="usr-dir-1", agency_id="ag-1", role=MontyRole.DIRECTOR)

        # Empty allowlist -> even SUPER_ADMIN rejected
        empty_settings = replace(base, platform_admin_user_ids=())
        self.assertFalse(is_platform_admin(super_ctx, empty_settings))
        self.assertFalse(is_platform_admin(dir_ctx, empty_settings))

        # Allowlisted user_id with DIRECTOR/SUPER_ADMIN accepted
        scoped = replace(base, platform_admin_user_ids=("usr-admin-1",))
        self.assertTrue(is_platform_admin(super_ctx, scoped))
        self.assertFalse(is_platform_admin(dir_ctx, scoped))

    def test_semantic_cache_and_provider_comparison_metrics(self) -> None:
        core = MontyAICore()
        base = load_settings()
        settings = replace(base, openai_api_key="sk-test-cache")
        ctx = TenantContext(user_id="usr-ag-cache", agency_id="ag-cache", role=MontyRole.AGENT)
        calls = 0

        def fake_transport(url: str, headers: dict, payload: dict, timeout: float):
            nonlocal calls
            calls += 1
            return 200, {"choices": [{"message": {"content": "Analyse cache Monty AI."}}]}

        first = core.chat(
            ctx,
            prompt="Analyse du quartier Antigone Montpellier",
            task_type=MontyAITaskType.CONVERSATION,
            settings=settings,
            transport=fake_transport,
        )
        second = core.chat(
            ctx,
            prompt="Analyse du quartier Antigone Montpellier",
            task_type=MontyAITaskType.CONVERSATION,
            settings=settings,
            transport=fake_transport,
        )
        self.assertEqual(calls, 1)
        self.assertFalse(first["cache_hit"])
        self.assertTrue(second["cache_hit"])

        metrics = core.evaluation_manager.metrics_summary(ctx.agency_id)
        self.assertIn("provider_comparison", metrics)
        self.assertGreaterEqual(metrics["cache_hits"], 1)

    def test_ademe_dpe_lookup_caching_and_unreachable_fallback(self) -> None:
        svc = AdemeDpeService()
        calls = 0

        def fake_ademe(url: str, timeout: float):
            nonlocal calls
            calls += 1
            return 200, {
                "results": [
                    {
                        "N°DPE": "2434E0012345X",
                        "Etiquette_DPE": "B",
                        "Etiquette_GES": "A",
                        "Conso_5_usages_par_m²_é_primaire": 82.0,
                        "Emission_GES_5_usages_par_m²": 3.2,
                        "Nom__commune_(BAN)": "Montpellier",
                        "Code_postal_(BAN)": "34000",
                        "Surface_habitable_logement": 78.0,
                        "Année_construction": 2018,
                        "Date_établissement_DPE": "2025-09-14",
                    }
                ]
            }

        res1 = svc.lookup(postal_code="34000", transport=fake_ademe)
        res2 = svc.lookup(postal_code="34000", transport=fake_ademe)
        self.assertEqual(calls, 1)
        self.assertFalse(res1["cached"])
        self.assertTrue(res2["cached"])
        self.assertEqual(res2["records"][0]["dpe_label"], "B")

        def broken_ademe(url: str, timeout: float):
            raise RuntimeError("ADEME timeout")

        res_down = svc.lookup(city="Castelnau-le-Lez", transport=broken_ademe)
        self.assertFalse(res_down["reachable"])
        self.assertEqual(res_down["count"], 0)
        self.assertIn("warning", res_down)

    def test_six_tier_price_index_excellente_affaire_and_disclaimer(self) -> None:
        engine = MontyPriceEngine()
        comps = [
            DvfComparable("m1", "2026-02-01", 2026, 300000, 75, "Appartement", 150),
            DvfComparable("m2", "2026-03-01", 2026, 304000, 76, "Appartement", 200),
            DvfComparable("m3", "2026-04-01", 2026, 296000, 74, "Appartement", 260),
            DvfComparable("m4", "2026-05-01", 2026, 308000, 77, "Appartement", 300),
        ]
        res = engine.evaluate_property(
            city="Montpellier",
            property_kind="Appartement",
            asking_price_euro=235000,
            surface_m2=75,
            dpe="D",
            condition_label="Bon état",
            explicit_comparables=comps,
        )
        self.assertTrue(res["display_badge"])
        self.assertEqual(res["category"], PriceIndexCategory.EXCELLENTE_AFFAIRE.value)
        self.assertTrue(res["calculated_at"])
        self.assertIn("expertise immobilière certifiée", res["estimation_disclaimer"])
        self.assertIn("excellent_deal_pct", res["thresholds"])


class AuthFirebaseExchangeAndBillingTests(unittest.TestCase):
    def test_verify_firebase_token_enforces_project_and_fail_closed_claims(self) -> None:
        try:
            from app.api.v1.router import _resolve_firebase_role, _verify_firebase_token_via_google
        except Exception as exc:
            self.skipTest(f"FastAPI/SQLAlchemy non installés dans cet environnement Python minimal : {exc}")

        base = load_settings()
        settings = replace(base, firebase_project_id="monty-prod-123")

        def valid_transport(url: str, timeout: float):
            return 200, {
                "sub": "firebase-uid-999",
                "email": "claire@monty.fr",
                "email_verified": "true",
                "name": "Claire Particulier",
                "aud": "monty-prod-123",
                "exp": "9999999999",
            }

        claims = _verify_firebase_token_via_google("header.payload.sig", settings=settings, transport=valid_transport)
        self.assertEqual(claims["sub"], "firebase-uid-999")
        # Unprivileged user requesting DIRECTOR still gets BUYER (fail-closed)
        self.assertEqual(_resolve_firebase_role(claims, MontyRole.DIRECTOR), MontyRole.BUYER)
        # Verified advisor claim unlocks AGENT
        self.assertEqual(
            _resolve_firebase_role({"advisor": True}, MontyRole.DIRECTOR),
            MontyRole.AGENT,
        )
        # Mismatched project aud rejected
        with self.assertRaises(ValueError):
            _verify_firebase_token_via_google(
                "header.payload.sig",
                settings=settings,
                transport=lambda u, t: (200, {"sub": "u1", "aud": "other-project", "exp": "9999999999"}),
            )


if __name__ == "__main__":
    unittest.main()
