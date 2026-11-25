from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Any, Callable
import urllib.error
import urllib.parse
import urllib.request

from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode
from app.domain.marketplace_service import _resolve_city_geo


@dataclass(frozen=True)
class GeocodingResult:
    latitude: float
    longitude: float
    label: str
    city: str
    region_name: str
    ref_price_m2: int
    postcode: str
    source: str  # "ban_api_adresse" | "fallback_city_centroid"


_GEOCODE_CACHE: dict[tuple[str, str], GeocodingResult] = {}
_MAX_CACHE_ENTRIES = 512


def clear_geocode_cache() -> None:
    _GEOCODE_CACHE.clear()


def _extract_region_from_context(context: str, default_region: str) -> str:
    # BAN context format: "69, Rhône, Auvergne-Rhône-Alpes"
    parts = [p.strip() for p in context.split(",") if p.strip()]
    if len(parts) >= 3:
        return parts[-1]
    return default_region


def geocode_french_address(
    query: str,
    *,
    country_code: CountryCode = CountryCode.FR,
    fallback_city: str = "",
    fetcher: Callable[[str], dict[str, Any]] | None = None,
    timeout_seconds: float = 2.5,
) -> GeocodingResult:
    """
    Géocode une adresse/secteur via la Base Adresse Nationale (BAN) `api-adresse.data.gouv.fr`
    avec cache mémoire et repli gracieux sur le centroïde ville si hors-ligne ou hors France.
    """
    clean_query = InputSanitizer.sanitize_text(query, 180)
    clean_fallback = InputSanitizer.sanitize_text(fallback_city or clean_query, 80) or "Lyon"
    fb_lat, fb_lon, fb_region, fb_ref_m2 = _resolve_city_geo(clean_fallback, country_code)

    cache_key = (country_code.value, (clean_query or clean_fallback).lower())
    if cache_key in _GEOCODE_CACHE:
        return _GEOCODE_CACHE[cache_key]

    if country_code != CountryCode.FR or len(clean_query) < 2:
        res = GeocodingResult(
            latitude=fb_lat,
            longitude=fb_lon,
            label=clean_fallback,
            city=clean_fallback,
            region_name=fb_region,
            ref_price_m2=fb_ref_m2,
            postcode="",
            source="fallback_city_centroid",
        )
        return res

    encoded_q = urllib.parse.quote(clean_query)
    url = f"https://api-adresse.data.gouv.fr/search/?q={encoded_q}&limit=1"
    try:
        if fetcher is not None:
            payload = fetcher(url)
        else:
            req = urllib.request.Request(
                url,
                method="GET",
                headers={"Accept": "application/json", "User-Agent": "MontyImmo-Geocoder/2.0"},
            )
            with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
                payload = json.loads(resp.read().decode("utf-8"))

        features = payload.get("features", []) if isinstance(payload, dict) else []
        if features and isinstance(features[0], dict):
            feat = features[0]
            geom = feat.get("geometry", {}) or {}
            coords = geom.get("coordinates", [])
            props = feat.get("properties", {}) or {}
            if isinstance(coords, list) and len(coords) >= 2:
                lon = float(coords[0])
                lat = float(coords[1])
                if -90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0:
                    resolved_city = InputSanitizer.sanitize_text(str(props.get("city") or clean_fallback), 80) or clean_fallback
                    _, _, city_region, city_ref_m2 = _resolve_city_geo(resolved_city, country_code)
                    region = _extract_region_from_context(str(props.get("context") or ""), city_region)
                    result = GeocodingResult(
                        latitude=round(lat, 6),
                        longitude=round(lon, 6),
                        label=InputSanitizer.sanitize_text(str(props.get("label") or clean_query), 180) or clean_query,
                        city=resolved_city,
                        region_name=region,
                        ref_price_m2=city_ref_m2,
                        postcode=InputSanitizer.sanitize_text(str(props.get("postcode") or ""), 12),
                        source="ban_api_adresse",
                    )
                    if len(_GEOCODE_CACHE) >= _MAX_CACHE_ENTRIES:
                        _GEOCODE_CACHE.clear()
                    _GEOCODE_CACHE[cache_key] = result
                    return result
    except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError, OSError, TypeError):
        pass

    fallback_res = GeocodingResult(
        latitude=fb_lat,
        longitude=fb_lon,
        label=clean_fallback,
        city=clean_fallback,
        region_name=fb_region,
        ref_price_m2=fb_ref_m2,
        postcode="",
        source="fallback_city_centroid",
    )
    if len(_GEOCODE_CACHE) >= _MAX_CACHE_ENTRIES:
        _GEOCODE_CACHE.clear()
    _GEOCODE_CACHE[cache_key] = fallback_res
    return fallback_res
