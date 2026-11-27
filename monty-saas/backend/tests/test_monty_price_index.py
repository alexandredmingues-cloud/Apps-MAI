from __future__ import annotations

import pathlib
import sys
import unittest

BACKEND_DIR = pathlib.Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.rbac import MontyRole, TenantContext  # noqa: E402
from app.domain.country_packs import CountryCode  # noqa: E402
from app.domain.monty_price_index_service import (  # noqa: E402
    MontyPriceEngine,
    PriceConfidenceTier,
    PriceIndexCategory,
)
from app.domain.real_estate_engines import DvfComparable  # noqa: E402


def _make_comps(prices_per_m2: list[int], surface: int = 80, kind: str = "Appartement") -> list[DvfComparable]:
    return [
        DvfComparable(
            mutation_id=f"cmp-{idx}",
            date_mutation=f"2026-0{(idx % 6) + 1}-15",
            year=2026,
            price_euro=pm2 * surface,
            surface_m2=surface,
            property_type=kind,
            distance_meters=250 + idx * 90,
        )
        for idx, pm2 in enumerate(prices_per_m2)
    ]


class MontyPriceIndexTests(unittest.TestCase):
    def test_all_five_price_categories_classified_accurately(self) -> None:
        engine = MontyPriceEngine()
        # Baseline comparables ~ 4000 EUR/m² on 100 m² (DPE D = 0%, Bon état = 0%) => est_value = 400_000 €
        comps = _make_comps([3950, 4000, 4020, 4030, 4000], surface=100)

        cases = [
            (340_000, PriceIndexCategory.TRES_BONNE_AFFAIRE),   # -15.0%
            (372_000, PriceIndexCategory.BONNE_AFFAIRE),        # -7.0%
            (402_000, PriceIndexCategory.PRIX_COHERENT),        # +0.5%
            (430_000, PriceIndexCategory.AU_DESSUS_DU_MARCHE),  # +7.5%
            (468_000, PriceIndexCategory.TROP_CHER),            # +17.0%
        ]
        for asking, expected_cat in cases:
            res = engine.evaluate_property(
                title=f"Bien test {asking}",
                city="Lyon",
                property_kind="Appartement",
                asking_price_euro=asking,
                surface_m2=100,
                dpe="D",
                condition_label="Bon état",
                explicit_comparables=comps,
                record_history=True,
            )
            self.assertTrue(res["display_badge"])
            self.assertEqual(res["category"], expected_cat.value)
            self.assertEqual(res["comparables"]["comparables_retained_count"], 5)
            formatted_est = f"{res['estimated_market_value']:,}".replace(",", " ")
            self.assertIn(formatted_est, res["explanation_grounded"])

    def test_iqr_outlier_removal_protects_market_estimate(self) -> None:
        engine = MontyPriceEngine()
        # 5 normal comparables around 4000 €/m² + 1 extreme aberration at 18000 €/m²
        comps = _make_comps([3900, 4000, 4020, 4050, 4030, 18000], surface=80)
        res = engine.evaluate_property(
            title="Test IQR",
            city="Lyon",
            property_kind="Appartement",
            asking_price_euro=320_000,
            surface_m2=80,
            dpe="D",
            condition_label="Bon état",
            explicit_comparables=comps,
        )
        self.assertTrue(res["display_badge"])
        self.assertGreaterEqual(res["comparables"]["outliers_removed_count"], 1)
        self.assertLess(res["estimated_price_m2"], 4300)

    def test_insufficient_comparables_hides_badge_honestly(self) -> None:
        engine = MontyPriceEngine()
        # Only 1 comparable (< default min_comparables_required=3) and unknown rural city
        comps = _make_comps([2200], surface=90)
        res = engine.evaluate_property(
            title="Hameau Isolé",
            city="Saint-Inconnu-Sur-Lot",
            property_kind="Maison",
            asking_price_euro=198_000,
            surface_m2=90,
            explicit_comparables=comps,
        )
        self.assertFalse(res["display_badge"])
        self.assertEqual(res["category"], PriceIndexCategory.DONNEES_INSUFFISANTES.value)
        self.assertEqual(res["confidence_tier"], PriceConfidenceTier.INSUFFICIENT.value)
        self.assertIn("minimum requis", res["explanation_grounded"])

    def test_fr_and_pt_auto_reference_benchmarks(self) -> None:
        engine = MontyPriceEngine()
        fr_res = engine.evaluate_property(
            title="T3 Antigone",
            city="Montpellier",
            sector_label="Antigone",
            property_kind="Appartement",
            asking_price_euro=325_000,
            surface_m2=75,
            dpe="B",
        )
        self.assertTrue(fr_res["display_badge"])
        self.assertGreater(fr_res["estimated_market_value"], 150_000)

        pt_res = engine.evaluate_property(
            title="Apartamento T2 Lisboa",
            country_code=CountryCode.PT,
            city="Lisboa",
            property_kind="Appartement",
            asking_price_euro=410_000,
            surface_m2=80,
            dpe="C",
        )
        self.assertTrue(pt_res["display_badge"])
        self.assertIn("Portugal", pt_res["comparables"]["source_label"])

    def test_admin_threshold_validation_and_tenant_history_isolation(self) -> None:
        engine = MontyPriceEngine()
        dir_a = TenantContext(user_id="u-a", agency_id="ag-a", role=MontyRole.DIRECTOR)
        dir_b = TenantContext(user_id="u-b", agency_id="ag-b", role=MontyRole.DIRECTOR)
        agent_a = TenantContext(user_id="u-ag", agency_id="ag-a", role=MontyRole.AGENT)

        with self.assertRaises(PermissionError):
            engine.update_config(
                agent_a,
                very_good_deal_pct=-12.0,
                good_deal_pct=-6.0,
                above_market_pct=6.0,
                overpriced_pct=12.0,
                min_comparables_required=3,
                max_comparable_distance_m=2500,
                max_comparable_age_months=24,
            )

        with self.assertRaises(ValueError):
            engine.update_config(
                dir_a,
                very_good_deal_pct=-4.0,
                good_deal_pct=-8.0,  # non-increasing!
                above_market_pct=5.0,
                overpriced_pct=10.0,
                min_comparables_required=3,
                max_comparable_distance_m=2500,
                max_comparable_age_months=24,
            )

        cfg = engine.update_config(
            dir_a,
            very_good_deal_pct=-12.0,
            good_deal_pct=-6.0,
            above_market_pct=6.0,
            overpriced_pct=12.0,
            min_comparables_required=4,
            max_comparable_distance_m=2000,
            max_comparable_age_months=18,
        )
        self.assertEqual(cfg.min_comparables_required, 4)

        engine.evaluate_property(
            agency_id="ag-a",
            user_id="u-a",
            title="Lot Agence A",
            city="Lyon",
            asking_price_euro=390_000,
            surface_m2=80,
            record_history=True,
        )
        self.assertEqual(len(engine.list_history(dir_a)), 1)
        self.assertEqual(len(engine.list_history(dir_b)), 0)


if __name__ == "__main__":
    unittest.main()
