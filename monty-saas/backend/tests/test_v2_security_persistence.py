from __future__ import annotations

import asyncio
from datetime import date, timedelta
import hashlib
import hmac
import json
import os
from pathlib import Path
import sys
import time
import unittest
from unittest.mock import patch

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import load_settings  # noqa: E402
from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.core.security import create_access_token, decode_access_token  # noqa: E402
from app.api.v1.crm_router import ensure_crm_persistence  # noqa: E402
from app.domain.auth_and_billing import AuthService, BillingService  # noqa: E402
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.crm_service import ContactRole, CrmService  # noqa: E402
from app.domain.marketplace_service import (  # noqa: E402
    ListingModerationStatus,
    MarketplaceService,
    PublisherType,
)
from app.workers.redis_tasks import run_csv_excel_import_job, run_rgpd_retention_sweep  # noqa: E402


class V2SecurityAndConfigTests(unittest.TestCase):
    def test_production_crm_refuses_non_persistent_fallback(self) -> None:
        with self.assertRaises(Exception) as caught:
            ensure_crm_persistence(None, is_production=True)
        self.assertEqual(getattr(caught.exception, "status_code", None), 503)
        ensure_crm_persistence(None, is_production=False)
        ensure_crm_persistence(object(), is_production=True)

    def test_ephemeral_dev_jwt_secret_is_stable_across_load_settings_calls(self) -> None:
        with patch.dict(os.environ, {"ENVIRONMENT": "development", "JWT_SECRET": ""}, clear=False):
            s1 = load_settings()
            s2 = load_settings()
            self.assertEqual(s1.effective_jwt_secret, s2.effective_jwt_secret)
            token = create_access_token(
                subject_user_id="usr-1",
                agency_id="agency-1",
                role="DIRECTOR",
                secret=s1.effective_jwt_secret,
            )
            decoded = decode_access_token(token, s2.effective_jwt_secret)
            self.assertEqual(decoded["sub"], "usr-1")
            self.assertEqual(decoded["aid"], "agency-1")

    def test_production_jwt_and_fail_closed_dev_auth(self) -> None:
        with patch.dict(
            os.environ,
            {"ENVIRONMENT": "development", "JWT_SECRET": "", "ENABLE_DEV_AUTH": ""},
            clear=False,
        ):
            self.assertFalse(load_settings().can_issue_dev_session)

        with patch.dict(
            os.environ,
            {"ENVIRONMENT": "development", "ENABLE_DEV_AUTH": "true"},
            clear=False,
        ):
            self.assertTrue(load_settings().can_issue_dev_session)

        with patch.dict(
            os.environ,
            {"ENVIRONMENT": "production", "JWT_SECRET": "short-secret", "ENABLE_DEV_AUTH": "true"},
            clear=False,
        ):
            prod = load_settings()
            self.assertFalse(prod.can_issue_dev_session)
            with self.assertRaises(RuntimeError):
                _ = prod.effective_jwt_secret

        with patch.dict(
            os.environ,
            {"ENVIRONMENT": "production", "JWT_SECRET": "x" * 40, "ENABLE_DEV_AUTH": "false"},
            clear=False,
        ):
            prod_ok = load_settings()
            self.assertFalse(prod_ok.can_issue_dev_session)
            self.assertEqual(prod_ok.effective_jwt_secret, "x" * 40)


class V2AuthAndInvitesTests(unittest.TestCase):
    def test_register_login_and_team_invites(self) -> None:
        auth = AuthService()
        settings = load_settings()
        reg = auth.register_agency_director(
            agency_name="Agence Lyon Presqu'île",
            email="dir@lyon-immo.fr",
            password="MontySecurePassword2026!",
            display_name="Claire Moreau",
            city="Lyon",
            country_code=CountryCode.FR,
            settings=settings,
        )
        self.assertEqual(reg.role, MontyRole.DIRECTOR)
        decoded = decode_access_token(reg.access_token, settings.effective_jwt_secret)
        self.assertEqual(decoded["aid"], reg.agency_id)

        with self.assertRaises(ValueError):
            auth.login(email="dir@lyon-immo.fr", password="WrongPassword!", settings=settings)

        logged = auth.login(email="dir@lyon-immo.fr", password="MontySecurePassword2026!", settings=settings)
        self.assertEqual(logged.user_id, reg.user_id)

        # Par défaut le forfait INDIVIDUAL n'a que 1 siège : testons avec un contexte agence 10 sièges
        director_ctx = TenantContext(
            user_id="usr-dir",
            agency_id="agency-france-reseau",
            role=MontyRole.DIRECTOR,
        )
        invite = auth.create_invite(
            director_ctx,
            target_email="agent.nouveau@monty.fr",
            target_name="Lucas Bernard",
            role=MontyRole.AGENT,
        )
        with self.assertRaises(PermissionError):
            auth.create_invite(
                director_ctx,
                target_email="hacker@monty.fr",
                target_name="X",
                role=MontyRole.SUPER_ADMIN,
            )

        claimed = auth.claim_invite(
            invite_code=invite.invite_code,
            password="AgentPassword2026!",
            settings=settings,
        )
        self.assertEqual(claimed.role, MontyRole.AGENT)
        self.assertEqual(claimed.agency_id, "agency-france-reseau")
        # Réutilisation impossible
        with self.assertRaises(ValueError):
            auth.claim_invite(invite_code=invite.invite_code, password="AgentPassword2026!", settings=settings)


class V2MarketplaceModerationTests(unittest.TestCase):
    def test_seller_listing_requires_moderation_before_public_visibility(self) -> None:
        market = MarketplaceService()
        seller_ctx = TenantContext(user_id="usr-seller", agency_id="agency-france-reseau", role=MontyRole.SELLER)
        agent_ctx = TenantContext(user_id="usr-agent", agency_id="agency-france-reseau", role=MontyRole.AGENT)
        director_ctx = TenantContext(user_id="usr-dir", agency_id="agency-france-reseau", role=MontyRole.DIRECTOR)
        other_director = TenantContext(user_id="usr-other", agency_id="agency-concurrent", role=MontyRole.DIRECTOR)

        submitted = market.publish_listing(
            ctx=seller_ctx,
            country_code=CountryCode.FR,
            publisher_type=PublisherType.PARTICULIER,
            publisher_label="Vendeur Lyon",
            title="Appartement T3 Lumineux Confluence",
            property_kind="Appartement",
            city="Lyon",
            sector_label="Confluence",
            price_euro=345000,
            surface_m2=68,
            rooms=3,
            dpe="B",
            condition_label="Bon état",
            highlights=["Terrasse", "Tram"],
            description="Bel appartement lumineux proche tramway et commerces.",
            photo_count=6,
        )
        self.assertEqual(submitted.moderation_status, ListingModerationStatus.A_VERIFIER)

        # Invisible en recherche publique tant qu'il n'est pas approuvé
        public_ids = {h.listing.id for h in market.search_listings(country_code=CountryCode.FR, origin_address="Lyon")}
        self.assertNotIn(submitted.id, public_ids)

        # Un simple AGENT ne peut pas modérer
        with self.assertRaises(PermissionError):
            market.moderate_listing(agent_ctx, listing_id=submitted.id, approve=True)

        # Une autre agence ne peut pas modérer
        with self.assertRaises(PermissionError):
            market.moderate_listing(other_director, listing_id=submitted.id, approve=True)

        pending = market.list_pending_moderation(director_ctx)
        self.assertTrue(any(item.id == submitted.id for item in pending))

        approved = market.moderate_listing(
            director_ctx,
            listing_id=submitted.id,
            approve=True,
            moderation_note="Dossier et DPE vérifiés",
        )
        self.assertEqual(approved.moderation_status, ListingModerationStatus.PUBLIEE)
        public_ids_after = {
            h.listing.id for h in market.search_listings(country_code=CountryCode.FR, origin_address="Lyon")
        }
        self.assertIn(submitted.id, public_ids_after)


class V2StripeBillingAndWorkersTests(unittest.TestCase):
    def test_stripe_webhook_signature_and_idempotency(self) -> None:
        billing = BillingService()
        secret = "whsec_monty_test_secret_123456"
        now_ts = int(time.time())
        event = {
            "id": "evt_monty_001",
            "type": "checkout.session.completed",
            "data": {
                "object": {
                    "id": "cs_test_1",
                    "customer": "cus_monty_1",
                    "subscription": "sub_monty_1",
                    "metadata": {"agency_id": "agency-france-reseau", "plan_code": "NETWORK"},
                }
            },
        }
        payload_bytes = json.dumps(event).encode("utf-8")
        mac = hmac.new(secret.encode("utf-8"), f"{now_ts}.".encode("ascii") + payload_bytes, hashlib.sha256).hexdigest()
        sig_header = f"t={now_ts},v1={mac}"

        # Signature invalide refusée
        with self.assertRaises(ValueError):
            billing.process_stripe_webhook(
                payload_bytes=payload_bytes,
                signature_header=f"t={now_ts},v1=deadbeef",
                webhook_secret=secret,
                now_fn=lambda: now_ts,
            )

        res1 = billing.process_stripe_webhook(
            payload_bytes=payload_bytes,
            signature_header=sig_header,
            webhook_secret=secret,
            now_fn=lambda: now_ts,
        )
        self.assertEqual(res1["status"], "processed")

        state = billing.get_agency_billing(
            TenantContext(user_id="u1", agency_id="agency-france-reseau", role=MontyRole.DIRECTOR)
        )
        self.assertEqual(state.plan_code, "NETWORK")
        self.assertEqual(state.subscription_status, "ACTIVE")
        self.assertEqual(state.seat_limit, 50)

        # Replay exact -> ignoré de façon idempotente
        res2 = billing.process_stripe_webhook(
            payload_bytes=payload_bytes,
            signature_header=sig_header,
            webhook_secret=secret,
            now_fn=lambda: now_ts,
        )
        self.assertEqual(res2["status"], "duplicate_ignored")

    def test_redis_worker_tasks_real_execution(self) -> None:
        crm = CrmService()
        director = TenantContext(user_id="usr-director", agency_id="agency-france-reseau", role=MontyRole.DIRECTOR)
        crm.create_contact(
            director,
            full_name="Contact Expiré Worker",
            email="expire@exemple.fr",
            role=ContactRole.PROSPECT,
            country_code=CountryCode.FR,
            retention_until=(date.today() - timedelta(days=3)).isoformat(),
        )
        csv_text = (
            "nom;email;telephone;ville;budget;role;pays\n"
            "Sofia Costa;sofia@exemplo.pt;+351910001122;Lisboa;410000;BUYER;PT\n"
            "Camille Laurent;camille.laurent@exemple.fr;0642189011;Lyon;420000;BUYER;FR\n"
        )
        import_res = asyncio.run(
            run_csv_excel_import_job(
                {"crm_service": crm},
                agency_id="agency-france-reseau",
                actor_user_id="usr-director",
                csv_text=csv_text,
                default_country="FR",
            )
        )
        self.assertEqual(import_res["status"], "completed")
        self.assertEqual(import_res["imported_count"], 1)
        self.assertEqual(import_res["skipped_duplicates"], 1)

        sweep_res = asyncio.run(run_rgpd_retention_sweep({"crm_service": crm}))
        self.assertEqual(sweep_res["status"], "sweep_completed")
        self.assertEqual(sweep_res["anonymized_count"], 1)

    def test_v2_sql_and_router_security_guards(self) -> None:
        sql_0004 = (BACKEND_DIR / "alembic" / "versions" / "0004_v2_crm_marketplace_sync.sql").read_text(encoding="utf-8")
        self.assertIn("public_marketplace_read_published", sql_0004)
        self.assertIn("monty_auth_register_agency", sql_0004)
        self.assertIn("monty_auth_lookup_login", sql_0004)
        self.assertIn("monty_auth_claim_invite", sql_0004)
        self.assertIn("is_anonymized", sql_0004)

        market_router = (BACKEND_DIR / "app" / "api" / "v1" / "marketplace_router.py").read_text(encoding="utf-8")
        self.assertIn("Depends(get_current_tenant)", market_router)
        self.assertIn("Permission.PROPERTY_MODERATE", market_router)

        main_router = (BACKEND_DIR / "app" / "api" / "v1" / "router.py").read_text(encoding="utf-8")
        self.assertIn("settings.can_issue_dev_session", main_router)
        self.assertIn("MontyRole.SUPER_ADMIN", main_router)


if __name__ == "__main__":
    unittest.main()
