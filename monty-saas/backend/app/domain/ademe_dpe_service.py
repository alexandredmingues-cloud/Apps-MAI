from __future__ import annotations

from dataclasses import dataclass
import json
import time
from typing import Any, Callable
import urllib.error
import urllib.parse
import urllib.request

from app.core.security import InputSanitizer


ADEME_DPE_DATASET_URL = (
    "https://data.ademe.fr/data-fair/api/v1/datasets/dpe-v2-logements-existants/lines"
)


@dataclass(frozen=True)
class AdemeDpeRecord:
    dpe_number: str
    dpe_label: str
    ges_label: str
    conso_kwh_m2_an: float | None
    emission_ges_kg_m2_an: float | None
    city: str
    postal_code: str
    surface_m2: float | None
    construction_year: int | None
    issued_date: str
    source: str = "ADEME Open Data (dpe-v2-logements-existants)"


class AdemeDpeService:
    """Connecteur officiel ADEME Open Data DPE avec cache mémoire TTL et fallback gracieux."""

    CACHE_TTL_SECONDS = 1800

    def __init__(self) -> None:
        self._cache: dict[str, tuple[float, list[AdemeDpeRecord]]] = {}

    def lookup(
        self,
        *,
        postal_code: str = "",
        city: str = "",
        dpe_number: str = "",
        limit: int = 5,
        transport: Callable[[str, float], tuple[int, dict[str, Any]]] | None = None,
    ) -> dict[str, Any]:
        clean_cp = InputSanitizer.sanitize_text(postal_code, 10)
        clean_city = InputSanitizer.sanitize_text(city, 80)
        clean_num = InputSanitizer.sanitize_text(dpe_number, 32).upper()
        if not (clean_cp or clean_city or clean_num):
            raise ValueError("Code postal, ville ou numéro ADEME requis.")

        cache_key = f"{clean_num}|{clean_cp}|{clean_city.lower()}|{limit}"
        cached = self._cache.get(cache_key)
        if cached and (time.monotonic() - cached[0]) <= self.CACHE_TTL_SECONDS:
            return {
                "source": "ADEME Open Data",
                "cached": True,
                "count": len(cached[1]),
                "records": [r.__dict__ for r in cached[1]],
            }

        qs_parts: list[str] = []
        if clean_num:
            qs_parts.append(f'N°DPE:"{clean_num}"')
        if clean_cp:
            qs_parts.append(f'Code_postal_(BAN):"{clean_cp}"')
        if clean_city:
            qs_parts.append(f'Nom__commune_(BAN):"{clean_city}"')
        params = urllib.parse.urlencode(
            {
                "qs": " AND ".join(qs_parts),
                "size": max(1, min(15, int(limit))),
                "select": (
                    "N°DPE,Etiquette_DPE,Etiquette_GES,Conso_5_usages_par_m²_é_primaire,"
                    "Emission_GES_5_usages_par_m²,Nom__commune_(BAN),Code_postal_(BAN),"
                    "Surface_habitable_logement,Année_construction,Date_établissement_DPE"
                ),
            }
        )
        url = f"{ADEME_DPE_DATASET_URL}?{params}"
        getter = transport or self._http_get_json
        try:
            code, payload = getter(url, 8.0)
            if code >= 400:
                return {
                    "source": "ADEME Open Data",
                    "cached": False,
                    "reachable": False,
                    "count": 0,
                    "records": [],
                    "warning": f"API ADEME indisponible (HTTP {code}).",
                }
            rows = payload.get("results") or []
            records = [
                AdemeDpeRecord(
                    dpe_number=str(row.get("N°DPE") or ""),
                    dpe_label=str(row.get("Etiquette_DPE") or "N/C")[:1].upper(),
                    ges_label=str(row.get("Etiquette_GES") or "N/C")[:1].upper(),
                    conso_kwh_m2_an=(
                        float(row["Conso_5_usages_par_m²_é_primaire"])
                        if row.get("Conso_5_usages_par_m²_é_primaire") is not None
                        else None
                    ),
                    emission_ges_kg_m2_an=(
                        float(row["Emission_GES_5_usages_par_m²"])
                        if row.get("Emission_GES_5_usages_par_m²") is not None
                        else None
                    ),
                    city=str(row.get("Nom__commune_(BAN)") or clean_city),
                    postal_code=str(row.get("Code_postal_(BAN)") or clean_cp),
                    surface_m2=(
                        float(row["Surface_habitable_logement"])
                        if row.get("Surface_habitable_logement") is not None
                        else None
                    ),
                    construction_year=(
                        int(row["Année_construction"])
                        if row.get("Année_construction") is not None
                        else None
                    ),
                    issued_date=str(row.get("Date_établissement_DPE") or ""),
                )
                for row in rows
                if isinstance(row, dict)
            ]
            self._cache[cache_key] = (time.monotonic(), records)
            return {
                "source": "ADEME Open Data",
                "cached": False,
                "reachable": True,
                "count": len(records),
                "records": [r.__dict__ for r in records],
            }
        except Exception:
            return {
                "source": "ADEME Open Data",
                "cached": False,
                "reachable": False,
                "count": 0,
                "records": [],
                "warning": "Service ADEME temporairement injoignable.",
            }

    @staticmethod
    def _http_get_json(url: str, timeout_s: float) -> tuple[int, dict[str, Any]]:
        req = urllib.request.Request(
            url,
            headers={"Accept": "application/json", "User-Agent": "MontyImmo/3.0"},
            method="GET",
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout_s) as resp:
                raw = resp.read().decode("utf-8")
                return int(getattr(resp, "status", 200)), json.loads(raw) if raw else {}
        except urllib.error.HTTPError as err:
            return int(err.code), {}


ademe_dpe_service = AdemeDpeService()
