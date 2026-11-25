from __future__ import annotations

import json
import logging
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.core.config import load_settings  # noqa: E402
from app.core.observability import JsonLogFormatter  # noqa: E402
from app.domain.real_estate_engines import (  # noqa: E402
    DvfComparable,
    DvfValuationCriteria,
    MIN_SECTOR_SAMPLE,
    compute_dvf_valuation_report,
    validate_dvf_comparable,
)


def _sample_comparables(count: int, *, base_m2: int = 4200, year: int = 2025) -> list[DvfComparable]:
    return [
        DvfComparable(
            mutation_id=f"m-{i}",
            date_mutation=f"{year}-06-{(i % 28) + 1:02d}",
            year=year - (i % 2),
            price_euro=(base_m2 + (i % 5 - 2) * 80) * (60 + (i % 4) * 10),
            surface_m2=60 + (i % 4) * 10,
            property_type="Appartement",
            distance_meters=30 + (i % 6) * 30,
        )
        for i in range(count)
    ]


class ScoreMontyDvfV2Tests(unittest.TestCase):
    def test_iqr_outlier_exclusion_and_recency_weighting(self) -> None:
        comps = _sample_comparables(16, base_m2=4200, year=2026)
        comps.append(DvfComparable("out-low", "2025-05-01", 2025, 42_000, 70, "Appartement", 40))
        comps.append(DvfComparable("out-high", "2025-05-02", 2025, 1_960_000, 70, "Appartement", 40))

        report = compute_dvf_valuation_report(
            comparables=comps,
            property_kind="Appartement",
            surface_m2=75,
            dpe="C",
            condition_label="Bon état",
            provided_docs_ratio=0.8,
            reference_year=2026,
        )
        self.assertEqual(report.outliers_removed_count, 2)
        self.assertFalse(report.expanded_to_neighbor_sector)
        self.assertEqual(report.comparables_used_count, 16)
        self.assertGreaterEqual(report.raw_dvf_price_m2, 4000)
        self.assertLessEqual(report.raw_dvf_price_m2, 4450)
        self.assertGreaterEqual(report.confidence_index, 75)

        recency_comps = [
            DvfComparable("old-1", "2022-03-01", 2022, 360_000, 100, "Appartement", 50),
            DvfComparable("old-2", "2022-04-01", 2022, 360_000, 100, "Appartement", 50),
            DvfComparable("new-1", "2026-02-01", 2026, 500_000, 100, "Appartement", 50),
            DvfComparable("new-2", "2026-03-01", 2026, 500_000, 100, "Appartement", 50),
        ]
        rec_report = compute_dvf_valuation_report(
            comparables=recency_comps,
            property_kind="Appartement",
            surface_m2=80,
            dpe="D",
            condition_label="Bon état",
            provided_docs_ratio=0.6,
            reference_year=2026,
        )
        self.assertGreater(rec_report.raw_dvf_price_m2, 4300)
        self.assertTrue(rec_report.expanded_to_neighbor_sector)
        self.assertEqual(rec_report.min_sample_required, MIN_SECTOR_SAMPLE)

    def test_twenty_reference_properties_scoring_and_confidence(self) -> None:
        reference_cases = [
            ("Appartement", 45, "B", "Rénové", 16, 4800),
            ("Appartement", 68, "C", "Bon état", 18, 4300),
            ("Appartement", 92, "A", "Rénové / Neuf", 20, 5100),
            ("Appartement", 32, "D", "À rafraîchir", 15, 3900),
            ("Appartement", 55, "E", "Bon état", 12, 3600),
            ("Appartement", 110, "B", "Bon état", 17, 4600),
            ("Appartement", 28, "F", "À rénover", 8, 3200),
            ("Appartement", 74, "C", "Bon état", 19, 4100),
            ("Appartement", 83, "D", "À rafraîchir", 15, 3750),
            ("Appartement", 60, "G", "À rénover", 10, 3100),
            ("Maison", 125, "C", "Bon état", 16, 4400),
            ("Maison", 150, "B", "Rénové", 18, 4900),
            ("Maison", 95, "D", "Bon état", 14, 3800),
            ("Maison", 180, "A", "Neuf", 15, 5300),
            ("Maison", 110, "E", "À rafraîchir", 11, 3500),
            ("Maison", 88, "F", "À rénover", 6, 3000),
            ("Maison", 140, "C", "Bon état", 20, 4250),
            ("Maison", 105, "D", "Bon état", 15, 3950),
            ("Appartement", 50, "C", "Bon état", 4, 4050),
            ("Appartement", 77, "B", "Rénové", 22, 4700),
        ]
        self.assertEqual(len(reference_cases), 20)

        for idx, (kind, surface, dpe, cond, comp_count, base_m2) in enumerate(reference_cases):
            comps = [
                DvfComparable(
                    mutation_id=f"ref-{idx}-{j}",
                    date_mutation=f"2025-0{(j % 8) + 1}-15",
                    year=2025,
                    price_euro=base_m2 * (surface + (j % 3) * 5),
                    surface_m2=surface + (j % 3) * 5,
                    property_type=kind,
                    distance_meters=35 + (j % 5) * 25,
                )
                for j in range(comp_count)
            ]
            rep = compute_dvf_valuation_report(
                comparables=comps,
                property_kind=kind,
                surface_m2=surface,
                dpe=dpe,
                condition_label=cond,
                provided_docs_ratio=0.75,
                criteria=DvfValuationCriteria(
                    floor_and_elevator="Plain-pied" if kind == "Maison" else "Étage avec ascenseur"
                ),
                reference_year=2026,
            )
            self.assertGreater(rep.estimated_low_euro, 0)
            self.assertLessEqual(rep.estimated_low_euro, rep.estimated_mid_euro)
            self.assertLessEqual(rep.estimated_mid_euro, rep.estimated_high_euro)
            self.assertEqual(rep.expanded_to_neighbor_sector, comp_count < 15)
            self.assertTrue(0 <= rep.confidence_index <= 100)
            self.assertTrue(rep.data_date.startswith("2025-"))

    def test_dvf_validation_rejects_incoherent_rows_and_production_cors_strips_wildcard(self) -> None:
        with self.assertRaises(ValueError):
            validate_dvf_comparable(
                mutation_id="bad-1",
                year=2025,
                price_euro=15_000,
                surface_m2=200,
            )
        validate_dvf_comparable(
            mutation_id="ok-1",
            year=2025,
            price_euro=320_000,
            surface_m2=75,
        )

        with patch.dict(
            os.environ,
            {"ENVIRONMENT": "production", "ALLOWED_ORIGINS": "*,https://apps-mai-1.onrender.com/"},
            clear=False,
        ):
            s = load_settings()
            self.assertEqual(s.allowed_origins, ["https://apps-mai-1.onrender.com"])

        formatter = JsonLogFormatter()
        record = logging.LogRecord(
            name="monty.api",
            level=logging.INFO,
            pathname=__file__,
            lineno=1,
            msg="http_request",
            args=(),
            exc_info=None,
        )
        record.extra_fields = {"request_id": "abc123", "status_code": 200}
        parsed = json.loads(formatter.format(record))
        self.assertEqual(parsed["request_id"], "abc123")
        self.assertEqual(parsed["status_code"], 200)

        router_src = (BACKEND_DIR / "app" / "api" / "v1" / "router.py").read_text(encoding="utf-8")
        self.assertIn("SELECT 1", router_src)
        self.assertIn("validate_dvf_comparable", router_src)


if __name__ == "__main__":
    unittest.main()
