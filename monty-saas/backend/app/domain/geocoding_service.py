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


@dataclass(frozen=True)
class GeoEntitiesDistinction:
    postal_address: str
    gps: str
    building_ref: str
    cadastral_parcel: str
    copro_lot: str


@dataclass(frozen=True)
class AddressSuggestion:
    label: str
    housenumber: str
    street: str
    postcode: str
    city: str
    department_code: str
    region_name: str
    country_code: str
    latitude: float
    longitude: float
    ban_id: str
    ban_type: str
    score: float
    source: str
    homonym_warning: bool
    entities: GeoEntitiesDistinction


_PORTUGAL_REFERENCE_CITIES: tuple[tuple[str, str, str, float, float], ...] = (
    ("Lisboa", "1100-148", "Área Metropolitana de Lisboa", 38.7223, -9.1393),
    ("Porto", "4000-007", "Norte", 41.1579, -8.6291),
    ("Faro", "8000-111", "Algarve", 37.0194, -7.9304),
    ("Cascais", "2750-310", "Área Metropolitana de Lisboa", 38.6979, -9.4215),
    ("Braga", "4700-319", "Norte", 41.5454, -8.4265),
    ("Coimbra", "3000-213", "Centro", 40.2033, -8.4103),
    ("Funchal", "9000-039", "Região Autónoma da Madeira", 32.6669, -16.9241),
)


def _build_entities_distinction(
    *,
    label: str,
    lat: float,
    lon: float,
    ban_id: str,
    ban_type: str,
) -> GeoEntitiesDistinction:
    return GeoEntitiesDistinction(
        postal_address=label,
        gps=f"{round(lat, 6)}, {round(lon, 6)}",
        building_ref=ban_id if ban_type in ("housenumber", "street") and ban_id else "non_rattache",
        cadastral_parcel="non_resolu_par_ban_requiert_cadastre_ign",
        copro_lot="non_resolu_voir_rnic_ou_edd",
    )


def autocomplete_address(
    query: str,
    *,
    country_code: CountryCode = CountryCode.FR,
    limit: int = 6,
    fetcher: Callable[[str], dict[str, Any]] | None = None,
    timeout_seconds: float = 2.5,
) -> list[AddressSuggestion]:
    """
    Autocomplétion d'adresse multi-propositions (BAN France + format international PT),
    avec détection d'homonymes et séparation explicite Adresse / GPS / Bâtiment / Parcelle / Lot.
    """
    clean_query = InputSanitizer.sanitize_text(query, 180)
    bounded_limit = max(1, min(10, limit))
    if len(clean_query) < 2:
        return []

    if country_code == CountryCode.PT:
        q_low = clean_query.lower()
        matches: list[AddressSuggestion] = []
        for city_name, cp, region, lat, lon in _PORTUGAL_REFERENCE_CITIES:
            if q_low in city_name.lower() or q_low in cp.lower() or q_low in region.lower() or "," in clean_query:
                lbl = f"{clean_query}, {cp} {city_name}, Portugal" if city_name.lower() not in q_low else f"{clean_query}, {cp}, Portugal"
                matches.append(
                    AddressSuggestion(
                        label=lbl,
                        housenumber="",
                        street=clean_query,
                        postcode=cp,
                        city=city_name,
                        department_code="PT",
                        region_name=region,
                        country_code="PT",
                        latitude=lat,
                        longitude=lon,
                        ban_id="",
                        ban_type="locality",
                        score=0.85,
                        source="pt_reference_directory",
                        homonym_warning=False,
                        entities=_build_entities_distinction(
                            label=lbl, lat=lat, lon=lon, ban_id="", ban_type="locality"
                        ),
                    )
                )
        return matches[:bounded_limit]

    encoded_q = urllib.parse.quote(clean_query)
    url = f"https://api-adresse.data.gouv.fr/search/?q={encoded_q}&limit={bounded_limit}"
    suggestions: list[AddressSuggestion] = []
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
        city_set: set[str] = set()
        raw_items: list[tuple[dict[str, Any], float, float]] = []
        for feat in features[:bounded_limit]:
            if not isinstance(feat, dict):
                continue
            geom = feat.get("geometry", {}) or {}
            coords = geom.get("coordinates", [])
            props = feat.get("properties", {}) or {}
            if not (isinstance(coords, list) and len(coords) >= 2):
                continue
            lon = float(coords[0])
            lat = float(coords[1])
            if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
                continue
            c_name = str(props.get("city") or "").strip().lower()
            if c_name:
                city_set.add(c_name)
            raw_items.append((props, lat, lon))

        has_homonyms = len(city_set) > 1
        for props, lat, lon in raw_items:
            city_val = InputSanitizer.sanitize_text(str(props.get("city") or ""), 80) or "France"
            _, _, default_reg, _ = _resolve_city_geo(city_val, CountryCode.FR)
            postcode_val = InputSanitizer.sanitize_text(str(props.get("postcode") or ""), 12)
            dept_val = postcode_val[:2] if len(postcode_val) >= 2 else ""
            label_val = InputSanitizer.sanitize_text(str(props.get("label") or clean_query), 180) or clean_query
            ban_id_val = InputSanitizer.sanitize_text(str(props.get("id") or ""), 64)
            ban_type_val = InputSanitizer.sanitize_text(str(props.get("type") or "street"), 32)
            suggestions.append(
                AddressSuggestion(
                    label=label_val,
                    housenumber=InputSanitizer.sanitize_text(str(props.get("housenumber") or ""), 16),
                    street=InputSanitizer.sanitize_text(str(props.get("street") or props.get("name") or ""), 120),
                    postcode=postcode_val,
                    city=city_val,
                    department_code=dept_val,
                    region_name=_extract_region_from_context(str(props.get("context") or ""), default_reg),
                    country_code="FR",
                    latitude=round(lat, 6),
                    longitude=round(lon, 6),
                    ban_id=ban_id_val,
                    ban_type=ban_type_val,
                    score=round(float(props.get("score") or 0.7), 3),
                    source="ban_api_adresse",
                    homonym_warning=has_homonyms,
                    entities=_build_entities_distinction(
                        label=label_val,
                        lat=lat,
                        lon=lon,
                        ban_id=ban_id_val,
                        ban_type=ban_type_val,
                    ),
                )
            )
    except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError, OSError, TypeError):
        pass

    if not suggestions:
        fb = geocode_french_address(clean_query, country_code=country_code, fallback_city=clean_query)
        suggestions.append(
            AddressSuggestion(
                label=fb.label,
                housenumber="",
                street=clean_query,
                postcode=fb.postcode,
                city=fb.city,
                department_code=fb.postcode[:2] if len(fb.postcode) >= 2 else "",
                region_name=fb.region_name,
                country_code=country_code.value,
                latitude=fb.latitude,
                longitude=fb.longitude,
                ban_id="",
                ban_type="fallback_centroid",
                score=0.5,
                source=fb.source,
                homonym_warning=False,
                entities=_build_entities_distinction(
                    label=fb.label,
                    lat=fb.latitude,
                    lon=fb.longitude,
                    ban_id="",
                    ban_type="fallback_centroid",
                ),
            )
        )
    return suggestions


def reverse_geocode(
    latitude: float,
    longitude: float,
    *,
    country_code: CountryCode = CountryCode.FR,
    fetcher: Callable[[str], dict[str, Any]] | None = None,
    timeout_seconds: float = 2.5,
) -> AddressSuggestion:
    """Géocodage inversé officiel (BAN France / repli GPS structuré) avec séparation Adresse/GPS/Parcelle."""
    if not (-90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0):
        raise ValueError("Coordonnées GPS hors bornes valides.")

    if country_code == CountryCode.FR:
        url = f"https://api-adresse.data.gouv.fr/reverse/?lon={longitude:.6f}&lat={latitude:.6f}"
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
                props = features[0].get("properties", {}) or {}
                city_val = InputSanitizer.sanitize_text(str(props.get("city") or "France"), 80) or "France"
                _, _, default_reg, _ = _resolve_city_geo(city_val, CountryCode.FR)
                postcode_val = InputSanitizer.sanitize_text(str(props.get("postcode") or ""), 12)
                label_val = InputSanitizer.sanitize_text(str(props.get("label") or city_val), 180) or city_val
                ban_id_val = InputSanitizer.sanitize_text(str(props.get("id") or ""), 64)
                ban_type_val = InputSanitizer.sanitize_text(str(props.get("type") or "housenumber"), 32)
                return AddressSuggestion(
                    label=label_val,
                    housenumber=InputSanitizer.sanitize_text(str(props.get("housenumber") or ""), 16),
                    street=InputSanitizer.sanitize_text(str(props.get("street") or props.get("name") or ""), 120),
                    postcode=postcode_val,
                    city=city_val,
                    department_code=postcode_val[:2] if len(postcode_val) >= 2 else "",
                    region_name=_extract_region_from_context(str(props.get("context") or ""), default_reg),
                    country_code="FR",
                    latitude=round(latitude, 6),
                    longitude=round(longitude, 6),
                    ban_id=ban_id_val,
                    ban_type=ban_type_val,
                    score=round(float(props.get("score") or 0.9), 3),
                    source="ban_reverse",
                    homonym_warning=False,
                    entities=_build_entities_distinction(
                        label=label_val,
                        lat=latitude,
                        lon=longitude,
                        ban_id=ban_id_val,
                        ban_type=ban_type_val,
                    ),
                )
        except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError, OSError, TypeError):
            pass

    fallback_lbl = f"Position GPS ({latitude:.5f}, {longitude:.5f})"
    return AddressSuggestion(
        label=fallback_lbl,
        housenumber="",
        street="",
        postcode="",
        city="Point GPS",
        department_code="",
        region_name=country_code.value,
        country_code=country_code.value,
        latitude=round(latitude, 6),
        longitude=round(longitude, 6),
        ban_id="",
        ban_type="gps_point",
        score=0.5,
        source="gps_coordinates_only",
        homonym_warning=False,
        entities=_build_entities_distinction(
            label=fallback_lbl,
            lat=latitude,
            lon=longitude,
            ban_id="",
            ban_type="gps_point",
        ),
    )
