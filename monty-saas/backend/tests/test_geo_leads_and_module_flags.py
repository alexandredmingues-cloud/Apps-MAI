from __future__ import annotations

import pathlib
import sys
import unittest

BACKEND_DIR = pathlib.Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.geocoding_service import (  # noqa: E402
    autocomplete_address,
    clear_geocode_cache,
    reverse_geocode,
)
from app.domain.interconnections_service import InterconnectionsService  # noqa: E402
from app.domain.portfolio_and_leads_service import (  # noqa: E402
    LeadKind,
    PortfolioAndLeadsService,
    compute_lead_qualification,
)


class GeoLeadsAndModuleFlagsTests(unittest.TestCase):
    def setUp(self) -> None:
        clear_geocode_cache()

    def test_autocomplete_ban_homonym_warning_and_five_entity_distinction(self) -> None:
        def fake_ban(_url: str) -> dict:
            return {
                "features": [
                    {
                        "geometry": {"coordinates": [4.8357, 45.7640]},
                        "properties": {
                            "label": "12 Rue de la République 69002 Lyon",
                            "housenumber": "12",
                            "street": "Rue de la République",
                            "postcode": "69002",
                            "city": "Lyon",
                            "context": "69, Rhône, Auvergne-Rhône-Alpes",
                            "id": "69382_7840_00012",
                            "type": "housenumber",
                            "score": 0.94,
                        },
                    },
                    {
                        "geometry": {"coordinates": [3.8772, 43.6108]},
                        "properties": {
                            "label": "12 Rue de la République 34000 Montpellier",
                            "housenumber": "12",
                            "street": "Rue de la République",
                            "postcode": "34000",
                            "city": "Montpellier",
                            "context": "34, Hérault, Occitanie",
                            "id": "34172_5550_00012",
                            "type": "housenumber",
                            "score": 0.89,
                        },
                    },
                ]
            }

        suggestions = autocomplete_address("12 Rue de la République", country_code=CountryCode.FR, fetcher=fake_ban)
        self.assertEqual(len(suggestions), 2)
        self.assertTrue(suggestions[0].homonym_warning)
        self.assertEqual(suggestions[0].city, "Lyon")
        self.assertEqual(suggestions[0].entities.postal_address, "12 Rue de la République 69002 Lyon")
        self.assertEqual(suggestions[0].entities.building_ref, "69382_7840_00012")
        self.assertIn("cadastre", suggestions[0].entities.cadastral_parcel)
        self.assertIn("rnic", suggestions[0].entities.copro_lot)

    def test_portugal_autocomplete_and_reverse_geocoding(self) -> None:
        pt_suggestions = autocomplete_address("Lisboa", country_code=CountryCode.PT)
        self.assertGreaterEqual(len(pt_suggestions), 1)
        self.assertEqual(pt_suggestions[0].country_code, "PT")
        self.assertEqual(pt_suggestions[0].city, "Lisboa")

        def fake_reverse(_url: str) -> dict:
            return {
                "features": [
                    {
                        "geometry": {"coordinates": [3.8767, 43.6109]},
                        "properties": {
                            "label": "Place de la Comédie 34000 Montpellier",
                            "street": "Place de la Comédie",
                            "postcode": "34000",
                            "city": "Montpellier",
                            "context": "34, Hérault, Occitanie",
                            "id": "34172_1234",
                            "type": "street",
                            "score": 0.91,
                        },
                    }
                ]
            }

        rev = reverse_geocode(43.6109, 3.8767, country_code=CountryCode.FR, fetcher=fake_reverse)
        self.assertEqual(rev.source, "ban_reverse")
        self.assertEqual(rev.city, "Montpellier")

        pt_rev = reverse_geocode(38.7223, -9.1393, country_code=CountryCode.PT)
        self.assertEqual(pt_rev.source, "gps_coordinates_only")
        self.assertIn("38.72230", pt_rev.label)

    def test_monty_leads_keyword_rules_and_qualification_scoring(self) -> None:
        svc = PortfolioAndLeadsService()
        ctx = TenantContext(user_id="usr-dir", agency_id="agency-france-reseau", role=MontyRole.DIRECTOR)

        rule = svc.add_lead_keyword(
            ctx,
            category="INVESTISSEURS",
            keyword="piscine",
            geo_scope="Occitanie",
        )
        rules = svc.list_lead_keywords(ctx)
        self.assertTrue(any(r.id == rule.id and r.keyword == "piscine" for r in rules))

        lead = svc.submit_public_lead(
            agency_id="agency-france-reseau",
            lead_kind=LeadKind.VISIT_REQUEST,
            full_name="Thomas Prospect",
            email="thomas.prospect@exemple.fr",
            phone="+33 6 11 22 33 44",
            city="Montpellier",
            budget_euro=450_000,
            horizon="Immédiat 1 mois",
            property_title="Villa avec piscine et terrasse",
            notes="Demande de visite rapide avec estimation",
            rgpd_consent_granted=True,
        )
        qual = compute_lead_qualification(lead, rules)
        self.assertGreaterEqual(qual.score, 85)
        self.assertLessEqual(qual.score, 100)
        self.assertEqual(qual.intent_stage, "VISITE")
        self.assertGreaterEqual(len(qual.matched_keywords), 2)

        deleted = svc.delete_lead_keyword(ctx, rule.id)
        self.assertTrue(deleted)

    def test_centralized_module_feature_flags_and_critical_protection(self) -> None:
        inter = InterconnectionsService()
        dir_ctx = TenantContext(user_id="usr-dir", agency_id="agency-france-reseau", role=MontyRole.DIRECTOR)
        agent_ctx = TenantContext(user_id="usr-ag", agency_id="agency-france-reseau", role=MontyRole.AGENT)

        flags = inter.list_module_flags("agency-france-reseau")
        keys = {f.module_key for f in flags}
        self.assertIn("dashboard", keys)
        self.assertIn("admin", keys)
        self.assertIn("telecharger", keys)

        # Agent cannot mutate module flags
        with self.assertRaises(PermissionError):
            inter.update_module_flag(agent_ctx, module_key="copro", status="EN_TEST", visible=True)

        # Critical dashboard/admin modules cannot be hidden
        with self.assertRaises(ValueError):
            inter.update_module_flag(dir_ctx, module_key="dashboard", status="DISPONIBLE", visible=False)
        with self.assertRaises(ValueError):
            inter.update_module_flag(dir_ctx, module_key="admin", status="DISPONIBLE", visible=False)

        # Non-critical modules can transition & restore cleanly with audit history
        updated = inter.update_module_flag(dir_ctx, module_key="news", status="EN_TEST", visible=False)
        self.assertEqual(updated.status, "EN_TEST")
        self.assertFalse(updated.visible)

        restored = inter.restore_default_module_flags(dir_ctx)
        news_restored = next(f for f in restored if f.module_key == "news")
        self.assertEqual(news_restored.status, "DISPONIBLE")
        self.assertTrue(news_restored.visible)

        hist = inter.list_module_flag_history(dir_ctx)
        self.assertGreaterEqual(len(hist), 2)


if __name__ == "__main__":
    unittest.main()
