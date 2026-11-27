from __future__ import annotations

import asyncio
from pathlib import Path
import sys
import unittest

BACKEND_DIR = Path(__file__).resolve().parents[1]
ROOT_DIR = BACKEND_DIR.parent.parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import Settings  # noqa: E402
from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.domain.ai_agents_service import MontyAgentsGovernanceService  # noqa: E402
from app.domain.dvf_sync_service import DvfSyncService  # noqa: E402
from app.workers.redis_tasks import run_dvf_sync_job  # noqa: E402


class V2ProductionCompletenessTests(unittest.TestCase):
    def test_anthropic_key_rotation_max_tokens_timeout_and_fallback(self) -> None:
        svc = MontyAgentsGovernanceService()
        ctx = TenantContext(user_id="usr-dir", agency_id="ag-anthropic", role=MontyRole.DIRECTOR)
        settings = Settings(
            environment="production",
            database_url="",
            redis_url="",
            jwt_secret="x" * 40,
            jwt_ttl_minutes=60,
            allowed_origins=["https://apps-mai-1.onrender.com"],
            openai_api_key="",
            openai_model_fast="gpt-4.1-mini",
            openai_model_complex="gpt-4.1",
            stripe_secret_key="",
            stripe_webhook_secret="",
            s3_endpoint="",
            s3_bucket="",
            s3_access_key="",
            s3_secret_key="",
            anthropic_api_key="sk-ant-key-1",
            anthropic_api_keys=("sk-ant-key-1", "sk-ant-key-2"),
            anthropic_max_tokens=777,
            anthropic_timeout_seconds=12.5,
        )

        seen_calls: list[tuple[str, int, float]] = []

        def fake_transport(url: str, headers: dict, payload: dict, timeout: float):
            seen_calls.append((headers["x-api-key"], payload["max_tokens"], timeout))
            # La 1ère clé renvoie 429, rotation automatique vers la 2e clé -> 200 OK
            if headers["x-api-key"] == "sk-ant-key-1":
                return 429, {}
            return 200, {"content": [{"type": "text", "text": "Analyse Claude Sonnet valide."}]}

        log = svc.run_agent(
            ctx,
            agent_id="dvf_analyst",
            prompt_text="Analyse DVF secteur Lyon 3",
            settings=settings,
            anthropic_transport=fake_transport,
        )
        self.assertEqual(len(seen_calls), 2)
        self.assertEqual(seen_calls[0][1], 777)
        self.assertEqual(seen_calls[0][2], 12.5)
        self.assertIn("Analyse Claude Sonnet valide", log.output_summary)

        # Si toutes les clés Anthropic échouent, bascule propre sur deterministic-fallback-safe
        def failing_transport(url: str, headers: dict, payload: dict, timeout: float):
            return 503, {}

        fallback_log = svc.run_agent(
            ctx,
            agent_id="hcsf_coach",
            prompt_text="Calcul capacité emprunt 25 ans",
            settings=settings,
            anthropic_transport=failing_transport,
        )
        self.assertEqual(fallback_log.model_used, "deterministic-fallback-safe")

    def test_whatsapp_approved_template_429_backoff_and_opt_out_guard(self) -> None:
        svc = MontyAgentsGovernanceService()
        ctx = TenantContext(user_id="usr-ag", agency_id="ag-wa", role=MontyRole.AGENT)
        settings = Settings(
            environment="production",
            database_url="",
            redis_url="",
            jwt_secret="x" * 40,
            jwt_ttl_minutes=60,
            allowed_origins=["https://apps-mai-1.onrender.com"],
            openai_api_key="",
            openai_model_fast="gpt-4.1-mini",
            openai_model_complex="gpt-4.1",
            stripe_secret_key="",
            stripe_webhook_secret="",
            s3_endpoint="",
            s3_bucket="",
            s3_access_key="",
            s3_secret_key="",
            whatsapp_phone_number_id="123456789",
            whatsapp_access_token="wa-token",
        )

        # Template non validé par Meta => rejet immédiat
        with self.assertRaises(ValueError):
            svc.send_whatsapp_template(
                ctx,
                to_phone="33611223344",
                template_name="template_non_valide",
                body_params=["Camille"],
                settings=settings,
            )

        # Template validé avec 2 erreurs 429 suivies d'un 200 -> relance progressive vérifiée
        sleeps: list[float] = []
        attempt_counter = {"n": 0}

        def flaky_wa_transport(url: str, headers: dict, payload: dict):
            attempt_counter["n"] += 1
            if attempt_counter["n"] <= 2:
                return 429, {}, 0.0
            return 200, {"messages": [{"id": "wamid.ok"}]}, 0.0

        sent = svc.send_whatsapp_template(
            ctx,
            to_phone="33611223344",
            template_name="monty_visite_confirmation_fr",
            body_params=["Camille", "Jeudi 17h30"],
            settings=settings,
            transport=flaky_wa_transport,
            sleep_fn=sleeps.append,
        )
        self.assertEqual(sent["status"], "sent")
        self.assertEqual(sent["attempts"], 3)
        self.assertEqual(sleeps, [1.0, 2.0])

        # Si le numéro est désinscrit via STOP, l'envoi est bloqué
        svc._opted_out_phones.add("33611223344")
        with self.assertRaises(PermissionError):
            svc.send_whatsapp_template(
                ctx,
                to_phone="33611223344",
                template_name="monty_visite_confirmation_fr",
                body_params=["Camille", "Jeudi 17h30"],
                settings=settings,
                transport=flaky_wa_transport,
                sleep_fn=sleeps.append,
            )

    def test_dvf_sync_rejects_incoherent_rows_and_falls_back_to_cache(self) -> None:
        # Une panne avant toute ingestion ne doit pas produire de ventes DVF de démonstration.
        cold_sync = DvfSyncService()
        def down_fetcher(_url: str):
            raise OSError("Source DVF temporairement indisponible")
        cold_fallback = cold_sync.sync_sector_from_remote(
            sector_code="69003",
            source_url="https://api.cquest.org/dvf",
            fetcher=down_fetcher,
        )
        self.assertEqual(cold_fallback.status, "fallback_cached")
        self.assertEqual(cold_fallback.accepted_rows, 0)
        self.assertEqual(cold_fallback.data_date, "N/A")

        dvf_sync = DvfSyncService()
        raw_rows = [
            {"id_mutation": "ok-1", "date_mutation": "2026-03-10", "valeur_fonciere": 340000, "surface_reelle_bati": 70, "type_local": "Appartement"},
            {"id_mutation": "bad-price-m2", "date_mutation": "2026-03-11", "valeur_fonciere": 16000, "surface_reelle_bati": 220, "type_local": "Appartement"},
            {"id_mutation": "bad-zero", "date_mutation": "2026-03-12", "valeur_fonciere": 0, "surface_reelle_bati": 50},
        ]
        rep = dvf_sync.ingest_raw_rows(sector_code="69003", raw_rows=raw_rows, source_url="https://api.cquest.org/dvf")
        self.assertEqual(rep.status, "synced_live")
        self.assertEqual(rep.accepted_rows, 1)
        self.assertEqual(rep.rejected_rows, 2)

        # Si la source distante tombe en panne, fallback automatique sur le cache existant
        def down_fetcher(_url: str):
            raise OSError("Source DVF temporairement indisponible")

        fallback_rep = dvf_sync.sync_sector_from_remote(
            sector_code="69003",
            source_url="https://api.cquest.org/dvf",
            fetcher=down_fetcher,
        )
        self.assertEqual(fallback_rep.status, "fallback_cached")
        self.assertGreaterEqual(fallback_rep.accepted_rows, 1)

        # Job asynchrone ARQ runnable directement
        job_res = asyncio.run(run_dvf_sync_job({"dvf_service": dvf_sync}, sector_codes=["69003"]))
        self.assertEqual(job_res["status"], "dvf_sync_completed")

    def test_ops_migrations_backups_sentry_and_render_urls(self) -> None:
        mig_0005 = (BACKEND_DIR / "alembic" / "versions" / "0005_dvf_cache_and_v2_ops.sql").read_text(encoding="utf-8")
        self.assertIn("ENABLE ROW LEVEL SECURITY", mig_0005)
        self.assertIn("dvf_mutations_cache", mig_0005)
        self.assertIn("agent_execution_logs", mig_0005)

        reqs = (BACKEND_DIR / "requirements.txt").read_text(encoding="utf-8")
        self.assertIn("sentry-sdk", reqs)

        backup_sh = (BACKEND_DIR / "scripts" / "backup_postgres.sh").read_text(encoding="utf-8")
        self.assertIn("pg_dump", backup_sh)
        self.assertTrue((BACKEND_DIR / "OPS_BACKUP_ROLLBACK.md").exists())

        render_yaml = (ROOT_DIR / "render.yaml").read_text(encoding="utf-8")
        self.assertIn("https://apps-mai-1.onrender.com", render_yaml)
        self.assertIn("https://monty-saas-api.onrender.com", render_yaml)


if __name__ == "__main__":
    unittest.main()
