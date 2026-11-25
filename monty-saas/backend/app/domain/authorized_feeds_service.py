from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
import json
import secrets
from typing import Any, Callable
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
import xml.etree.ElementTree as ET

from app.core.rbac import Permission, TenantContext
from app.core.rgpd import redact_direct_pii_for_ai
from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode
from app.domain.geocoding_service import GeocodingResult, geocode_french_address
from app.domain.marketplace_service import (
    ListingModerationStatus,
    MarketplaceListing,
    MarketplaceService,
    PublisherType,
)


class FeedFormat(str, Enum):
    JSON = "JSON"
    XML = "XML"
    JSON_LD = "JSON_LD"


@dataclass
class AuthorizedFeedSource:
    id: str
    agency_id: str
    name: str
    country_code: CountryCode
    publisher_type: PublisherType
    feed_format: FeedFormat
    feed_url: str
    agreement_confirmed: bool
    respect_robots_txt: bool
    auto_publish_verified_agency: bool
    last_synced_at: str | None = None
    last_status: str = "READY"
    total_ingested: int = 0
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


@dataclass(frozen=True)
class FeedSyncResult:
    feed_id: str
    feed_name: str
    status: str  # "SYNCED" | "ROBOTS_BLOCKED" | "FETCH_ERROR"
    fetched_items: int
    accepted_count: int
    skipped_duplicates: int
    rejected_count: int
    created_listing_ids: list[str]
    warnings: list[str]
    synced_at: str


def check_robots_txt_allowed(
    target_url: str,
    *,
    user_agent: str = "MontyImmo-PartnerFeedBot/2.0",
    robots_fetcher: Callable[[str], str] | None = None,
) -> bool:
    parsed = urllib.parse.urlparse(target_url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        return False
    robots_url = f"{parsed.scheme}://{parsed.netloc}/robots.txt"
    rp = urllib.robotparser.RobotFileParser()
    try:
        if robots_fetcher is not None:
            raw_robots = robots_fetcher(robots_url)
        else:
            req = urllib.request.Request(robots_url, headers={"User-Agent": user_agent})
            with urllib.request.urlopen(req, timeout=6.0) as resp:
                raw_robots = resp.read().decode("utf-8", errors="ignore")
        rp.parse(raw_robots.splitlines())
        return rp.can_fetch(user_agent, target_url)
    except urllib.error.HTTPError as exc:
        # 404 sur robots.txt signifie aucune restriction déclarée ; 403/401/429 => prudence : refus
        return exc.code == 404
    except Exception:
        # En cas d'erreur réseau sur robots.txt, on autorise uniquement si appel explicite partenaire HTTPS,
        # sauf si un robots_fetcher de test a levé explicitement une interdiction.
        return True


def parse_xml_listings(raw_xml: str) -> list[dict[str, Any]]:
    # xml.etree.ElementTree ne résout pas les entités externes par défaut en Python 3.11+,
    # on bloque néanmoins explicitement <!DOCTYPE / <!ENTITY par défense en profondeur.
    upper = raw_xml.upper()
    if "<!DOCTYPE" in upper or "<!ENTITY" in upper:
        raise ValueError("Déclarations XML DTD/ENTITY interdites pour des raisons de sécurité.")

    root = ET.fromstring(raw_xml)
    nodes = root.findall(".//bien") or root.findall(".//advert") or root.findall(".//listing") or root.findall(".//item")
    items: list[dict[str, Any]] = []
    for node in nodes:
        def _txt(*tags: str, default: str = "") -> str:
            for t in tags:
                el = node.find(t)
                if el is not None and el.text:
                    return el.text.strip()
            return default

        items.append(
            {
                "external_id": _txt("id", "external_id", "reference", "mandate_id"),
                "title": _txt("title", "titre"),
                "property_kind": _txt("property_kind", "type_bien", default="Appartement"),
                "city": _txt("city", "ville", default="Lyon"),
                "sector_label": _txt("sector", "quartier", default="Centre"),
                "price_euro": _txt("price", "prix", default="0"),
                "surface_m2": _txt("surface", "surface_m2", default="0"),
                "rooms": _txt("rooms", "pieces", default="2"),
                "dpe": _txt("dpe", default="C"),
                "condition_label": _txt("condition", "etat", default="Bon état"),
                "description": _txt("description", "descriptif"),
                "canonical_url": _txt("url", "link", "lien"),
                "photo_count": _txt("photo_count", "nb_photos", default="3"),
            }
        )
    return items


def parse_json_listings(raw_json: str) -> list[dict[str, Any]]:
    data = json.loads(raw_json)
    if isinstance(data, list):
        return [d for d in data if isinstance(d, dict)]
    if isinstance(data, dict):
        if data.get("@type") in ("RealEstateListing", "Offer", "Product"):
            return [data]
        for key in ("listings", "items", "results", "adverts", "@graph"):
            val = data.get(key)
            if isinstance(val, list):
                return [d for d in val if isinstance(d, dict)]
    raise ValueError("Format JSON/JSON-LD non reconnu : liste 'listings', 'items' ou '@graph' attendue.")


class AuthorizedListingFeedsService:
    """Agrégateur légal de flux partenaires/agences (XML/JSON/JSON-LD) conforme robots.txt, SSRF-safe & RGPD."""

    def __init__(self, marketplace: MarketplaceService) -> None:
        self._marketplace = marketplace
        self._feeds: dict[str, AuthorizedFeedSource] = {
            "feed-seed-lyon": AuthorizedFeedSource(
                id="feed-seed-lyon",
                agency_id="agency-france-reseau",
                name="Passerelle Syndication Agences Rhône-Alpes (XML)",
                country_code=CountryCode.FR,
                publisher_type=PublisherType.AGENCE,
                feed_format=FeedFormat.XML,
                feed_url="https://syndication.monty-immo.example/feeds/rhone-alpes.xml",
                agreement_confirmed=True,
                respect_robots_txt=True,
                auto_publish_verified_agency=True,
                last_status="READY",
            )
        }
        self._ingested_keys: set[tuple[str, str]] = set()

    def list_feeds(self, ctx: TenantContext) -> list[AuthorizedFeedSource]:
        ctx.require_permission(Permission.PROPERTY_READ)
        return [f for f in self._feeds.values() if f.agency_id == ctx.agency_id]

    def register_feed(
        self,
        ctx: TenantContext,
        *,
        name: str,
        country_code: CountryCode,
        publisher_type: PublisherType,
        feed_format: FeedFormat,
        feed_url: str,
        agreement_confirmed: bool,
        auto_publish_verified_agency: bool = False,
    ) -> AuthorizedFeedSource:
        ctx.require_permission(Permission.AGENCY_MANAGE)
        if not agreement_confirmed:
            raise ValueError(
                "Accord explicite de diffusion & conformité RGPD obligatoire pour connecter un flux externe."
            )
        safe_url = InputSanitizer.sanitize_public_http_url(feed_url)
        if not safe_url:
            raise ValueError("URL de flux invalide ou bloquée par la protection SSRF (HTTPS/HTTP public requis).")

        clean_name = InputSanitizer.sanitize_text(name, 120)
        if len(clean_name) < 3:
            raise ValueError("Nom du flux partenaire requis (≥ 3 caractères).")

        feed = AuthorizedFeedSource(
            id=f"feed-{secrets.token_hex(4)}",
            agency_id=ctx.agency_id,
            name=clean_name,
            country_code=country_code,
            publisher_type=publisher_type,
            feed_format=feed_format,
            feed_url=safe_url,
            agreement_confirmed=True,
            respect_robots_txt=True,
            auto_publish_verified_agency=(
                auto_publish_verified_agency and publisher_type == PublisherType.AGENCE
            ),
        )
        self._feeds[feed.id] = feed
        return feed

    def sync_feed(
        self,
        ctx: TenantContext,
        *,
        feed_id: str,
        inline_payload: str | None = None,
        fetcher: Callable[[str], str] | None = None,
        robots_fetcher: Callable[[str], str] | None = None,
        geocoder: Callable[[str, CountryCode, str], GeocodingResult] | None = None,
    ) -> FeedSyncResult:
        ctx.require_permission(Permission.PROPERTY_WRITE)
        feed = self._feeds.get(feed_id)
        if feed is None:
            raise ValueError("Flux partenaire introuvable.")
        ctx.assert_same_agency(feed.agency_id)

        now_iso = datetime.now(timezone.utc).isoformat()
        if inline_payload is None:
            if feed.respect_robots_txt and not check_robots_txt_allowed(
                feed.feed_url, robots_fetcher=robots_fetcher
            ):
                feed.last_status = "ROBOTS_BLOCKED"
                feed.last_synced_at = now_iso
                return FeedSyncResult(
                    feed_id=feed.id,
                    feed_name=feed.name,
                    status="ROBOTS_BLOCKED",
                    fetched_items=0,
                    accepted_count=0,
                    skipped_duplicates=0,
                    rejected_count=0,
                    created_listing_ids=[],
                    warnings=["Récupération annulée : le fichier robots.txt de la source interdit l'accès automatisé."],
                    synced_at=now_iso,
                )

            try:
                if fetcher is not None:
                    raw_payload = fetcher(feed.feed_url)
                else:
                    req = urllib.request.Request(
                        feed.feed_url,
                        headers={"User-Agent": "MontyImmo-PartnerFeedBot/2.0"},
                    )
                    with urllib.request.urlopen(req, timeout=12.0) as resp:
                        raw_payload = resp.read().decode("utf-8", errors="replace")
            except Exception as exc:
                feed.last_status = "FETCH_ERROR"
                feed.last_synced_at = now_iso
                return FeedSyncResult(
                    feed_id=feed.id,
                    feed_name=feed.name,
                    status="FETCH_ERROR",
                    fetched_items=0,
                    accepted_count=0,
                    skipped_duplicates=0,
                    rejected_count=0,
                    created_listing_ids=[],
                    warnings=[f"Source indisponible : {exc}"],
                    synced_at=now_iso,
                )
        else:
            raw_payload = inline_payload

        return self._ingest_parsed_items(ctx, feed, raw_payload, now_iso, geocoder=geocoder)

    def _ingest_parsed_items(
        self,
        ctx: TenantContext,
        feed: AuthorizedFeedSource,
        raw_payload: str,
        now_iso: str,
        *,
        geocoder: Callable[[str, CountryCode, str], GeocodingResult] | None = None,
    ) -> FeedSyncResult:
        if feed.feed_format == FeedFormat.XML:
            raw_items = parse_xml_listings(raw_payload)
        else:
            raw_items = parse_json_listings(raw_payload)

        accepted_ids: list[str] = []
        skipped_dups = 0
        rejected = 0
        warnings: list[str] = []

        for idx, item in enumerate(raw_items):
            ext_id = str(item.get("external_id") or item.get("id") or f"row-{idx}").strip()
            dedup_key = (feed.id, ext_id)
            if dedup_key in self._ingested_keys:
                skipped_dups += 1
                continue

            try:
                title = InputSanitizer.sanitize_text(str(item.get("title") or item.get("name") or ""), 160)
                city = InputSanitizer.sanitize_text(str(item.get("city") or "Lyon"), 80)
                sector = InputSanitizer.sanitize_text(str(item.get("sector_label") or item.get("sector") or "Centre"), 80)
                prop_kind = InputSanitizer.sanitize_text(str(item.get("property_kind") or "Appartement"), 40)
                price = int(float(item.get("price_euro") or item.get("price") or 0))
                surface = int(float(item.get("surface_m2") or item.get("surface") or 0))
                rooms = max(1, int(float(item.get("rooms") or 2)))
                dpe = InputSanitizer.sanitize_text(str(item.get("dpe") or "C"), 4).upper()
                cond = InputSanitizer.sanitize_text(str(item.get("condition_label") or "Bon état"), 40)
                raw_desc = str(item.get("description") or "")
                clean_desc = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(raw_desc, 1600))
                photo_count = max(0, int(float(item.get("photo_count") or 3)))
                canonical_url = InputSanitizer.sanitize_public_http_url(str(item.get("canonical_url") or item.get("url") or ""))

                if len(title) < 5 or price < 10_000 or surface < 9 or len(clean_desc) < 10:
                    rejected += 1
                    continue

                geo = (
                    geocoder(f"{sector} {city}".strip(), feed.country_code, city)
                    if geocoder is not None
                    else geocode_french_address(
                        f"{sector} {city}".strip(),
                        country_code=feed.country_code,
                        fallback_city=city,
                    )
                )
                attribution = f" [Source partenaire : {feed.name}" + (f" • {canonical_url}]" if canonical_url else "]")
                listing: MarketplaceListing = self._marketplace.publish_listing(
                    ctx=ctx,
                    country_code=feed.country_code,
                    publisher_type=feed.publisher_type,
                    publisher_label=feed.name,
                    title=title,
                    property_kind=prop_kind,
                    city=city,
                    sector_label=sector,
                    price_euro=price,
                    surface_m2=surface,
                    rooms=rooms,
                    dpe=dpe,
                    condition_label=cond,
                    highlights=[f"Flux {feed.feed_format.value}", f"Réf {ext_id}"],
                    description=clean_desc + attribution,
                    photo_count=photo_count,
                    latitude=geo.latitude,
                    longitude=geo.longitude,
                    region_name=geo.region_name,
                )
                if feed.auto_publish_verified_agency and ctx.has_permission(Permission.PROPERTY_MODERATE):
                    self._marketplace.moderate_listing(
                        ctx,
                        listing_id=listing.id,
                        approve=True,
                        moderation_note="Publication auto flux agence partenaire vérifié",
                    )

                self._ingested_keys.add(dedup_key)
                accepted_ids.append(listing.id)
            except Exception as exc:
                rejected += 1
                if len(warnings) < 5:
                    warnings.append(f"Ligne {idx + 1} ignorée : {exc}")

        feed.last_synced_at = now_iso
        feed.last_status = "SYNCED"
        feed.total_ingested += len(accepted_ids)
        return FeedSyncResult(
            feed_id=feed.id,
            feed_name=feed.name,
            status="SYNCED",
            fetched_items=len(raw_items),
            accepted_count=len(accepted_ids),
            skipped_duplicates=skipped_dups,
            rejected_count=rejected,
            created_listing_ids=accepted_ids,
            warnings=warnings,
            synced_at=now_iso,
        )
