from __future__ import annotations

import sys
import unittest
from datetime import date
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.rbac import MontyRole, Permission, TenantContext  # noqa: E402
from app.core.rgpd import (  # noqa: E402
    LEGAL_BASIS_CONSENT,
    LEGAL_BASIS_MANDATE,
    build_rgpd_portability_export,
    compute_retention_until,
    is_retention_expired,
    redact_direct_pii_for_ai,
    retention_months_for,
)
from app.core.security import (  # noqa: E402
    InputSanitizer,
    SlidingWindowRateLimiter,
    create_access_token,
    decode_access_token,
    hash_password,
    verify_password,
)
from app.domain.real_estate_engines import (  # noqa: E402
    BuyerMatchInput,
    DvfComparable,
    DvfValuationCriteria,
    HcsfCapacityInput,
    PropertyMatchInput,
    SearchPerimeterMode,
    TransportMode,
    compute_compatibility,
    compute_dvf_valuation_report,
    compute_finance_metrics,
    compute_hcsf_borrowing_capacity,
    estimate_sale_timeline,
    evaluate_property_access,
)


class SecurityAndRbacTests(unittest.TestCase):
    def test_scrypt_password_hashing_and_verification(self) -> None:
        hashed = hash_password("MontyAgence2026!")
        self.assertTrue(hashed.startswith("scrypt$"))
        self.assertTrue(verify_password("MontyAgence2026!", hashed))
        self.assertFalse(verify_password("WrongPassword!", hashed))

    def test_jwt_issue_decode_tamper_and_expiry(self) -> None:
        now = 1_700_000_000
        token = create_access_token(
            subject_user_id="usr-1",
            agency_id="ag-1",
            role="DIRECTOR",
            secret="test-secret-key-at-least-32-chars",
            ttl_seconds=600,
            now_fn=lambda: now,
        )
        payload = decode_access_token(token, "test-secret-key-at-least-32-chars", now_fn=lambda: now + 10)
        self.assertEqual(payload["sub"], "usr-1")
        self.assertEqual(payload["aid"], "ag-1")
        self.assertEqual(payload["role"], "DIRECTOR")

        with self.assertRaises(ValueError):
            decode_access_token(token + "x", "test-secret-key-at-least-32-chars", now_fn=lambda: now + 10)

        with self.assertRaises(ValueError):
            decode_access_token(token, "test-secret-key-at-least-32-chars", now_fn=lambda: now + 700)

    def test_sanitizers_and_ssrf_guard(self) -> None:
        self.assertEqual(InputSanitizer.sanitize_spreadsheet_cell("=CMD()"), "'=CMD()")
        self.assertEqual(InputSanitizer.sanitize_spreadsheet_cell("+1+1"), "'+1+1")
        self.assertNotIn("<script>", InputSanitizer.sanitize_text("<script>alert(1)</script>Bonjour"))
        self.assertEqual(InputSanitizer.sanitize_public_http_url("http://127.0.0.1:8000/admin"), "")
        self.assertEqual(InputSanitizer.sanitize_public_http_url("http://169.254.169.254/latest/meta-data"), "")
        self.assertEqual(
            InputSanitizer.sanitize_public_http_url("https://api-adresse.data.gouv.fr/search/?q=Montpellier", {"api-adresse.data.gouv.fr"}),
            "https://api-adresse.data.gouv.fr/search/?q=Montpellier",
        )

    def test_rate_limiter_sliding_window(self) -> None:
        t = [100.0]
        limiter = SlidingWindowRateLimiter(clock=lambda: t[0])
        self.assertTrue(limiter.check_and_consume("u1", 2, 60).allowed)
        self.assertTrue(limiter.check_and_consume("u1", 2, 60).allowed)
        blocked = limiter.check_and_consume("u1", 2, 60)
        self.assertFalse(blocked.allowed)
        t[0] += 61.0
        self.assertTrue(limiter.check_and_consume("u1", 2, 60).allowed)

    def test_production_rate_limiter_fails_closed_without_redis(self) -> None:
        limiter = SlidingWindowRateLimiter(require_redis=True)
        decision = limiter.check_and_consume("production-api:user-1", 120, 60)
        self.assertFalse(decision.allowed)
        self.assertEqual(decision.remaining, 0)
        self.assertGreaterEqual(decision.retry_after_seconds, 1)

    def test_multi_tenant_and_private_notes_rbac(self) -> None:
        agent_a = TenantContext(user_id="usr-a1", agency_id="ag-a", role=MontyRole.AGENT)
        director_a = TenantContext(user_id="usr-dir", agency_id="ag-a", role=MontyRole.DIRECTOR)
        assistant_a = TenantContext(user_id="usr-asst", agency_id="ag-a", role=MontyRole.ASSISTANT)
        buyer_a = TenantContext(user_id="usr-buy", agency_id="ag-a", role=MontyRole.BUYER)

        with self.assertRaises(PermissionError):
            agent_a.assert_same_agency("ag-b")

        self.assertTrue(agent_a.can_read_private_note(author_user_id="usr-a1", note_agency_id="ag-a"))
        self.assertFalse(agent_a.can_read_private_note(author_user_id="usr-a2", note_agency_id="ag-a"))
        self.assertTrue(director_a.can_read_private_note(author_user_id="usr-a2", note_agency_id="ag-a"))
        self.assertFalse(assistant_a.can_read_private_note(author_user_id="usr-a1", note_agency_id="ag-a"))
        self.assertFalse(buyer_a.can_read_private_note(author_user_id="usr-buy", note_agency_id="ag-a"))
        self.assertFalse(buyer_a.has_permission(Permission.CRM_READ))


class RgpdComplianceTests(unittest.TestCase):
    def test_pii_redaction_for_ai(self) -> None:
        raw = "Appeler Claire au 06 12 34 56 78 ou claire.dupont@example.fr, IBAN FR76 3000 6000 0112 3456 7890 189."
        masked = redact_direct_pii_for_ai(raw)
        self.assertNotIn("06 12 34 56 78", masked)
        self.assertNotIn("claire.dupont@example.fr", masked)
        self.assertNotIn("FR76", masked)
        self.assertIn("[tél-masqué-rgpd]", masked)
        self.assertIn("[email-masqué-rgpd]", masked)
        self.assertIn("[iban-masqué-rgpd]", masked)

    def test_retention_periods_and_expiry(self) -> None:
        self.assertEqual(retention_months_for(LEGAL_BASIS_CONSENT), 36)
        self.assertEqual(retention_months_for(LEGAL_BASIS_MANDATE), 60)
        until = compute_retention_until(LEGAL_BASIS_MANDATE, reference_date=date(2026, 1, 15))
        self.assertEqual(until, date(2031, 1, 15))
        self.assertTrue(is_retention_expired(date(2026, 1, 1), today=date(2026, 1, 2)))
        self.assertFalse(is_retention_expired(date(2027, 1, 1), today=date(2026, 1, 2)))

    def test_portability_export_structure(self) -> None:
        doc = build_rgpd_portability_export(
            agency_id="ag-1",
            exported_by_user_id="usr-dir",
            subject_type="CRM_CONTACT",
            subject_payload={"full_name": "Claire Martin"},
        )
        self.assertIn("RGPD UE 2016/679", doc["exportStandard"])
        self.assertEqual(doc["agencyId"], "ag-1")


class RealEstateDomainParityTests(unittest.TestCase):
    def test_hcsf_borrowing_capacity_and_finance_metrics(self) -> None:
        res = compute_hcsf_borrowing_capacity(
            HcsfCapacityInput(
                monthly_net_income=6800,
                existing_monthly_credits=0,
                down_payment=95000,
                duration_years=25,
                interest_rate_percent=3.35,
                insurance_rate_percent=0.32,
                max_debt_ratio_percent=35.0,
                notary_rate_percent=7.8,
            )
        )
        self.assertEqual(res.max_monthly_debt_total, 2380)
        self.assertTrue(res.is_hcsf_ratio_compliant)
        self.assertTrue(res.is_hcsf_duration_compliant)
        self.assertGreater(res.max_property_price_net, 450000)
        self.assertEqual(len(res.duration_scenarios), 3)

        metrics = compute_finance_metrics(
            price=420000,
            down_payment=90000,
            notary_rate_percent=7.8,
            duration_years=25,
            interest_rate_percent=3.35,
            insurance_rate_percent=0.32,
            monthly_income=6800,
            existing_credits=0,
            monthly_charges=250,
        )
        self.assertEqual(metrics.notary_fees, 32760)
        self.assertLess(metrics.debt_ratio_percent, 35.0)

    def test_matching_and_geo_commute(self) -> None:
        commute = evaluate_property_access(
            origin_lat=43.6047,
            origin_lon=3.8806,
            origin_label="Gare Saint-Roch",
            prop_lat=43.6003,
            prop_lon=3.9041,
            transport_mode=TransportMode.TRAM,
            search_mode=SearchPerimeterMode.TRAVEL_TIME,
            max_radius_km=5,
            max_travel_minutes=20,
        )
        self.assertTrue(commute.within_perimeter)
        self.assertGreater(commute.distance_km, 1.5)

        match = compute_compatibility(
            PropertyMatchInput(
                price=398000,
                estimated_works=0,
                sector="Mas Drevon / Montcalm",
                property_type="Appartement",
                surface=89,
                bedrooms=3,
                dpe="B",
                exterior="Terrasse 18 m² + Jardin",
                parking="Box fermé",
                environment="Calme",
                transports="Tram L2 à 4 min",
                floor="RDC avec ascenseur",
                latitude=43.5974,
                longitude=3.8648,
            ),
            BuyerMatchInput(
                budget_max=430000,
                sectors=["Mas Drevon / Montcalm", "Ovalie"],
                property_type="Appartement",
                min_surface=80,
                min_rooms=3,
                essential_criteria=["Terrasse", "Parking", "Tram"],
            ),
        )
        self.assertGreaterEqual(match.score, 90)
        self.assertEqual(len(match.incompatible_criteria), 0)

    def test_dvf_valuation_and_sale_timeline_honesty(self) -> None:
        comps = [
            DvfComparable("m1", "2025-10-10", 2025, 360000, 90, "Appartement", 35),
            DvfComparable("m2", "2025-06-15", 2025, 410000, 100, "Appartement", 75),
            DvfComparable("m3", "2024-09-20", 2024, 312000, 80, "Appartement", 190),
        ]
        report = compute_dvf_valuation_report(
            comparables=comps,
            property_kind="Appartement",
            surface_m2=90,
            dpe="B",
            condition_label="Rénové",
            provided_docs_ratio=0.8,
            criteria=DvfValuationCriteria(
                floor_and_elevator="Dernier étage + asc.",
                view_and_exposure="Vue dégagée / parc / mer",
            ),
            reference_year=2026,
        )
        self.assertGreater(report.estimated_mid_euro, report.estimated_low_euro)
        self.assertGreater(report.estimated_high_euro, report.estimated_mid_euro)
        self.assertGreater(report.valuation_score, 40)
        self.assertIn("indicative", report.disclaimer.lower())

        insufficient = estimate_sale_timeline(
            sector_name="Inconnu",
            property_kind="Appartement",
            asking_price_euro=0,
            surface_m2=0,
            dpe="C",
            condition_label="Bon état",
        )
        self.assertEqual(insufficient.status, "INSUFFICIENT_DATA")

        ready = estimate_sale_timeline(
            sector_name="Ovalie",
            property_kind="Appartement",
            asking_price_euro=355000,
            surface_m2=90,
            dpe="B",
            condition_label="Bon état",
            photo_count=5,
            provided_docs_ratio=0.8,
            mandate_type="Exclusif",
        )
        self.assertEqual(ready.status, "READY")
        self.assertGreater(ready.likely_days, 14)


class PostgresRlsSchemaStaticTests(unittest.TestCase):
    def test_all_tenant_tables_have_rls_and_audit_immutability(self) -> None:
        sql_path = BACKEND_DIR / "alembic" / "versions" / "0001_initial_multi_tenant_rls.sql"
        sql = sql_path.read_text(encoding="utf-8")
        required_rls_tables = [
            "agencies",
            "agency_memberships",
            "agency_invites",
            "crm_contacts",
            "advisor_private_notes",
            "contact_exchanges",
            "follow_up_tasks",
            "buyer_dossiers",
            "properties",
            "dvf_valuations",
            "visits",
            "offers",
            "seller_dossiers",
            "seller_documents",
            "ai_usage_logs",
            "audit_logs",
        ]
        for table in required_rls_tables:
            self.assertIn(f"ALTER TABLE {table} ENABLE ROW LEVEL SECURITY;", sql)
            self.assertIn(f"CREATE POLICY tenant_isolation_", sql)
        self.assertIn("CREATE TRIGGER trg_audit_logs_immutable", sql)


if __name__ == "__main__":
    unittest.main()
