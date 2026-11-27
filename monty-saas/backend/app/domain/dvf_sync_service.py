from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import json
from typing import Any, Callable
import urllib.error
import urllib.request

from app.domain.real_estate_engines import DvfComparable, validate_dvf_comparable


@dataclass(frozen=True)
class DvfSyncReport:
    sector_code: str
    status: str  # "synced_live" | "fallback_cached"
    fetched_rows: int
    accepted_rows: int
    rejected_rows: int
    last_synced_at: str
    data_date: str
    source_url: str


class DvfSyncService:
    """Import DVF planifié avec validation Pydantic/métier, cache et fallback résilient."""

    def __init__(self) -> None:
        # Aucune vente fictive préchargée : le cache est alimenté uniquement par une ingestion DVF validée.
        self._cache_by_sector: dict[str, list[DvfComparable]] = {}
        self._last_sync_iso_by_sector: dict[str, str] = {}

    def ingest_raw_rows(
        self,
        *,
        sector_code: str,
        raw_rows: list[dict[str, Any]],
        source_url: str,
    ) -> DvfSyncReport:
        accepted: list[DvfComparable] = []
        rejected = 0
        for idx, row in enumerate(raw_rows):
            try:
                mut_id = str(row.get("id_mutation") or row.get("mutation_id") or f"dvf-{sector_code}-{idx}").strip()
                date_mut = str(row.get("date_mutation") or "")[:10]
                year = int(row.get("year") or (date_mut[:4] if len(date_mut) >= 4 else 0))
                price_euro = int(float(row.get("valeur_fonciere") or row.get("price_euro") or 0))
                surface_m2 = int(float(row.get("surface_reelle_bati") or row.get("surface_m2") or 0))
                prop_type = str(row.get("type_local") or row.get("property_type") or "Appartement").strip()
                dist_m = int(row.get("distance_meters") or 120)

                validate_dvf_comparable(
                    mutation_id=mut_id,
                    year=year,
                    price_euro=price_euro,
                    surface_m2=surface_m2,
                )
                accepted.append(
                    DvfComparable(
                        mutation_id=mut_id,
                        date_mutation=date_mut or f"{year}-01-01",
                        year=year,
                        price_euro=price_euro,
                        surface_m2=surface_m2,
                        property_type=prop_type,
                        distance_meters=dist_m,
                    )
                )
            except (TypeError, ValueError):
                rejected += 1

        now_iso = datetime.now(timezone.utc).isoformat()
        if accepted:
            self._cache_by_sector[sector_code] = accepted
            self._last_sync_iso_by_sector[sector_code] = now_iso
            latest_date = max(c.date_mutation for c in accepted)
            return DvfSyncReport(
                sector_code=sector_code,
                status="synced_live",
                fetched_rows=len(raw_rows),
                accepted_rows=len(accepted),
                rejected_rows=rejected,
                last_synced_at=now_iso,
                data_date=latest_date,
                source_url=source_url,
            )

        cached = self._cache_by_sector.get(sector_code, [])
        latest_cached = max((c.date_mutation for c in cached), default="N/A")
        return DvfSyncReport(
            sector_code=sector_code,
            status="fallback_cached",
            fetched_rows=len(raw_rows),
            accepted_rows=len(cached),
            rejected_rows=rejected,
            last_synced_at=self._last_sync_iso_by_sector.get(sector_code, now_iso),
            data_date=latest_cached,
            source_url=source_url,
        )

    def sync_sector_from_remote(
        self,
        *,
        sector_code: str,
        source_url: str,
        fetcher: Callable[[str], list[dict[str, Any]]] | None = None,
    ) -> DvfSyncReport:
        url = f"{source_url.rstrip('/')}?code_commune={sector_code}"
        try:
            if fetcher is not None:
                rows = fetcher(url)
            else:
                req = urllib.request.Request(url, method="GET", headers={"Accept": "application/json"})
                with urllib.request.urlopen(req, timeout=12.0) as resp:
                    payload = json.loads(resp.read().decode("utf-8"))
                rows = payload.get("resultats", payload.get("results", [])) if isinstance(payload, dict) else []
            return self.ingest_raw_rows(sector_code=sector_code, raw_rows=rows, source_url=url)
        except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError, OSError):
            cached = self._cache_by_sector.get(sector_code, [])
            now_iso = datetime.now(timezone.utc).isoformat()
            return DvfSyncReport(
                sector_code=sector_code,
                status="fallback_cached",
                fetched_rows=0,
                accepted_rows=len(cached),
                rejected_rows=0,
                last_synced_at=self._last_sync_iso_by_sector.get(sector_code, now_iso),
                data_date=max((c.date_mutation for c in cached), default="N/A"),
                source_url=url,
            )

    def get_cached_comparables(self, sector_code: str) -> list[DvfComparable]:
        return list(self._cache_by_sector.get(sector_code, []))

    def get_cache_status(self) -> dict[str, Any]:
        return {
            "sectors_cached": sorted(self._cache_by_sector.keys()),
            "total_comparables_cached": sum(len(v) for v in self._cache_by_sector.values()),
            "last_synced_at_by_sector": dict(self._last_sync_iso_by_sector),
        }
