from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import math
from typing import Any, Callable
import urllib.error
import urllib.parse
import urllib.request


class CadastreUrbanismService:
    """Proxy prudent vers les services publics IGN/GPU/Géorisques."""

    BASE = "https://apicarto.ign.fr/api"
    LAYERS = {
        "parcels": ("/cadastre/parcelle", "Parcelles cadastrales"),
        "plu_zones": ("/gpu/zone-urba", "Zonage PLU/PLUi"),
        "prescriptions_surface": ("/gpu/prescription-surf", "Prescriptions surfaciques"),
        "prescriptions_linear": ("/gpu/prescription-lin", "Prescriptions linéaires"),
        "prescriptions_point": ("/gpu/prescription-pct", "Prescriptions ponctuelles"),
        "servitudes": ("/gpu/assiette-sup-s", "Servitudes d'utilité publique"),
        "plu_documents": ("/gpu/document", "Documents d'urbanisme"),
    }
    DISCLAIMER = (
        "Informations publiques indicatives. La contenance cadastrale n'est ni une surface habitable "
        "ni un mesurage contradictoire. Le zonage et les servitudes ne constituent pas une autorisation "
        "d'urbanisme ni une confirmation de constructibilité ; consulter les documents opposables "
        "et le service urbanisme compétent."
    )

    def __init__(self) -> None:
        self._transport: Callable[[str, float], tuple[int, dict[str, Any]]] = self._http_get_json

    def lookup(
        self,
        *,
        latitude: float,
        longitude: float,
        transport: Callable[[str, float], tuple[int, dict[str, Any]]] | None = None,
    ) -> dict[str, Any]:
        lat, lon = float(latitude), float(longitude)
        if not (math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180):
            raise ValueError("Coordonnées GPS invalides.")
        if lat == 0 and lon == 0:
            raise ValueError("Coordonnées GPS invalides.")
        geom = json.dumps({"type": "Point", "coordinates": [lon, lat]}, separators=(",", ":"))
        encoded = urllib.parse.urlencode({"geom": geom})
        getter = transport or self._transport

        results: dict[str, Any] = {}
        warnings: list[str] = []

        def fetch_layer(key: str, path: str) -> tuple[str, dict[str, Any]]:
            url = f"{self.BASE}{path}?{encoded}"
            try:
                status, payload = getter(url, 12.0)
                if status >= 400:
                    return key, {"available": False, "count": 0, "features": [], "status_code": status}
                features = payload.get("features") or []
                if not isinstance(features, list):
                    features = []
                # Limiter la réponse et ne garder que les entités GeoJSON valides.
                clean = [f for f in features[:50] if isinstance(f, dict)]
                return key, {"available": True, "count": len(clean), "features": clean}
            except Exception:
                return key, {"available": False, "count": 0, "features": []}

        with ThreadPoolExecutor(max_workers=7) as pool:
            futures = [pool.submit(fetch_layer, key, path) for key, (path, _) in self.LAYERS.items()]
            for future in as_completed(futures):
                key, value = future.result()
                results[key] = value

        for key, (_, label) in self.LAYERS.items():
            if not results.get(key, {}).get("available"):
                warnings.append(f"{label} indisponible au moment de la recherche.")

        # Géorisques est interrogé séparément : sa couverture et son service peuvent différer.
        risk_url = (
            "https://www.georisques.gouv.fr/api/v1/gaspar/risques?"
            + urllib.parse.urlencode({"latlon": f"{lon:.6f},{lat:.6f}"})
        )
        try:
            status, payload = getter(risk_url, 12.0)
            risks = payload.get("data") or [] if status < 400 else []
            results["risks"] = {
                "available": status < 400,
                "count": len(risks) if isinstance(risks, list) else 0,
                "items": risks[:100] if isinstance(risks, list) else [],
            }
            if status >= 400:
                warnings.append("Géorisques indisponible au moment de la recherche.")
        except Exception:
            results["risks"] = {"available": False, "count": 0, "items": []}
            warnings.append("Géorisques indisponible au moment de la recherche.")

        parcels = results.get("parcels", {}).get("features", [])
        parcel_summaries = []
        for feature in parcels:
            props = feature.get("properties") or {}
            if not isinstance(props, dict):
                props = {}
            parcel_summaries.append({
                "parcel_id": props.get("id") or props.get("idu") or "",
                "section": props.get("section") or "",
                "number": props.get("numero") or "",
                "commune_code": props.get("code_com") or props.get("commune") or "",
                "commune_name": props.get("nom_com") or "",
                "area_m2": props.get("contenance"),
                "geometry": feature.get("geometry"),
            })

        return {
            "source": "IGN API Carto / Géoportail de l'Urbanisme / Géorisques",
            "coordinates": {"latitude": lat, "longitude": lon},
            "parcels": parcel_summaries,
            "layers": results,
            "warnings": warnings,
            "disclaimer": self.DISCLAIMER,
        }

    @staticmethod
    def _http_get_json(url: str, timeout_s: float) -> tuple[int, dict[str, Any]]:
        request = urllib.request.Request(
            url,
            headers={"Accept": "application/geo+json, application/json", "User-Agent": "MON-AGENT/1.0"},
            method="GET",
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout_s) as response:
                raw = response.read().decode("utf-8")
                return int(getattr(response, "status", 200)), json.loads(raw) if raw else {}
        except urllib.error.HTTPError as error:
            return int(error.code), {}


cadastre_urbanism_service = CadastreUrbanismService()
