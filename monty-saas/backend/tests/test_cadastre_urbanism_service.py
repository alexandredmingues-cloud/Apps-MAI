from __future__ import annotations

import unittest
from pathlib import Path
import sys
from urllib.parse import urlparse

BACKEND_DIR = Path(__file__).resolve().parents[1]
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from app.domain.cadastre_urbanism_service import CadastreUrbanismService


class CadastreUrbanismServiceTests(unittest.TestCase):
    def test_lookup_returns_parcel_plu_and_risk_layers_with_provenance(self) -> None:
        service = CadastreUrbanismService()
        calls: list[str] = []

        def fake_transport(url: str, timeout: float):
            calls.append(url)
            parsed = urlparse(url)
            if "georisques.gouv.fr" in parsed.netloc:
                return 200, {"data": [{"libelle_risque": "Inondation"}]}
            path = parsed.path
            if path.endswith("/cadastre/parcelle"):
                return 200, {"features": [{
                    "type": "Feature",
                    "properties": {
                        "id": "340000AB0123",
                        "section": "AB",
                        "numero": "0123",
                        "code_com": "34172",
                        "nom_com": "Montpellier",
                        "contenance": 420,
                    },
                    "geometry": {"type": "Polygon", "coordinates": []},
                }]}
            if path.endswith("/gpu/zone-urba"):
                return 200, {"features": [{"type": "Feature", "properties": {"libelle": "Zone UC"}}]}
            return 200, {"features": []}

        result = service.lookup(latitude=43.6045, longitude=3.8792, transport=fake_transport)
        self.assertEqual(result["parcels"][0]["parcel_id"], "340000AB0123")
        self.assertEqual(result["parcels"][0]["area_m2"], 420)
        self.assertEqual(result["layers"]["plu_zones"]["count"], 1)
        self.assertEqual(result["layers"]["risks"]["count"], 1)
        self.assertEqual(len(calls), 8)
        self.assertIn("IGN", result["source"])
        self.assertIn("surface habitable", result["disclaimer"])

    def test_invalid_coordinates_are_rejected(self) -> None:
        service = CadastreUrbanismService()
        with self.assertRaises(ValueError):
            service.lookup(latitude=120, longitude=3)
        with self.assertRaises(ValueError):
            service.lookup(latitude=0, longitude=0)

    def test_unavailable_layer_is_reported_without_fabricating_data(self) -> None:
        service = CadastreUrbanismService()

        def failing_transport(url: str, timeout: float):
            return 503, {}

        result = service.lookup(latitude=43.6045, longitude=3.8792, transport=failing_transport)
        self.assertEqual(result["parcels"], [])
        self.assertFalse(result["layers"]["parcels"]["available"])
        self.assertTrue(result["warnings"])


if __name__ == "__main__":
    unittest.main()
