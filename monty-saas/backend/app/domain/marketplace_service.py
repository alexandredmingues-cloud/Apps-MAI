from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import date
from enum import Enum
import uuid

from app.core.rbac import MontyRole, Permission, TenantContext
from app.core.rgpd import redact_direct_pii_for_ai
from app.core.security import InputSanitizer
from app.domain.country_packs import CountryCode, get_country_pack
from app.domain.real_estate_engines import (
    SaleTimelineEstimate,
    SearchPerimeterMode,
    TransportMode,
    estimate_sale_timeline,
    evaluate_property_access,
)


class PublisherType(str, Enum):
    PARTICULIER = "PARTICULIER"
    AGENCE = "AGENCE"


class ListingModerationStatus(str, Enum):
    A_VERIFIER = "A_VERIFIER"
    PUBLIEE = "PUBLIEE"
    REJETEE = "REJETEE"


CITY_COORDINATES: dict[str, tuple[float, float, CountryCode, str, int]] = {
    "paris": (48.8566, 2.3522, CountryCode.FR, "Île-de-France", 9650),
    "lyon": (45.7640, 4.8357, CountryCode.FR, "Auvergne-Rhône-Alpes", 4950),
    "bordeaux": (44.8378, -0.5792, CountryCode.FR, "Nouvelle-Aquitaine", 4550),
    "nantes": (47.2184, -1.5536, CountryCode.FR, "Pays de la Loire", 3850),
    "toulouse": (43.6047, 1.4442, CountryCode.FR, "Occitanie", 3650),
    "marseille": (43.2965, 5.3698, CountryCode.FR, "Provence-Alpes-Côte d'Azur", 3550),
    "montpellier": (43.6108, 3.8767, CountryCode.FR, "Occitanie", 4150),
    "lille": (50.6292, 3.0573, CountryCode.FR, "Hauts-de-France", 3700),
    "nice": (43.7102, 7.2620, CountryCode.FR, "Provence-Alpes-Côte d'Azur", 5250),
    "strasbourg": (48.5734, 7.7521, CountryCode.FR, "Grand Est", 3800),
    "rennes": (48.1173, -1.6778, CountryCode.FR, "Bretagne", 4100),
    "lisboa": (38.7223, -9.1393, CountryCode.PT, "Área Metropolitana de Lisboa", 5200),
    "porto": (41.1579, -8.6291, CountryCode.PT, "Norte (Porto / Braga)", 3650),
    "faro": (37.0194, -7.9304, CountryCode.PT, "Algarve", 3400),
}


@dataclass(frozen=True)
class MarketplaceListing:
    id: str
    country_code: CountryCode
    publisher_type: PublisherType
    publisher_label: str
    title: str
    property_kind: str
    city: str
    region_name: str
    sector_label: str
    price_euro: int
    surface_m2: int
    rooms: int
    dpe: str
    condition_label: str
    highlights: tuple[str, ...]
    description: str
    photo_count: int
    latitude: float
    longitude: float
    sale_timeline: SaleTimelineEstimate
    published_at: str
    moderation_status: ListingModerationStatus = ListingModerationStatus.PUBLIEE
    agency_id: str = "agency-france-reseau"
    author_user_id: str = ""
    moderation_note: str = ""


@dataclass(frozen=True)
class MarketplaceSearchHit:
    listing: MarketplaceListing
    distance_km: float
    estimated_minutes: int
    transport_badge: str
    within_perimeter: bool


def _resolve_city_geo(city_or_address: str, country_code: CountryCode) -> tuple[float, float, str, int]:
    lowered = city_or_address.lower()
    for key, (lat, lon, c_code, region, ref_m2) in CITY_COORDINATES.items():
        if c_code == country_code and key in lowered:
            return lat, lon, region, ref_m2
    if country_code == CountryCode.PT:
        return 38.7223, -9.1393, "Área Metropolitana de Lisboa", 5200
    return 45.7640, 4.8357, "Auvergne-Rhône-Alpes", 4950


def generate_listing_copy(
    *,
    country_code: CountryCode,
    property_kind: str,
    city: str,
    sector_label: str,
    surface_m2: int,
    rooms: int,
    price_euro: int,
    dpe: str,
    condition_label: str,
    highlights: list[str],
    photo_count: int,
    estimated_sale_days: int | None = None,
) -> dict:
    pack = get_country_pack(country_code)
    clean_city = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(city, 80)) or pack.major_cities[0]
    clean_sector = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(sector_label, 100)) or clean_city
    clean_highlights = [redact_direct_pii_for_ai(InputSanitizer.sanitize_text(h, 80)) for h in highlights if h.strip()]

    if country_code == CountryCode.PT:
        title = f"{property_kind} T{max(1, rooms - 1)} • {clean_city} ({clean_sector}) — {surface_m2} m² • CE {dpe.upper()}"
        body = (
            f"Localizado em {clean_city} ({clean_sector}), este {property_kind.lower()} de {surface_m2} m² "
            f"({rooms} divisões) apresenta estado « {condition_label} » e Certificado Energético {dpe.upper()}. "
            + (f"Destaques: {', '.join(clean_highlights)}. " if clean_highlights else "")
            + f"Preço pedido : {price_euro:,} €. ".replace(",", " ")
            + f"Dossier com {photo_count} foto(s)."
        )
    else:
        title = f"{property_kind} {rooms} pièces • {surface_m2} m² — {clean_city} ({clean_sector}) • DPE {dpe.upper()}"
        body = (
            f"Situé à {clean_city} ({clean_sector}), découvrez ce bien ({property_kind.lower()}) de {surface_m2} m² "
            f"comprenant {rooms} pièces, en état « {condition_label} » (classe énergétique {dpe.upper()}). "
            + (f"Atouts principaux : {', '.join(clean_highlights)}. " if clean_highlights else "")
            + f"Prix affiché : {price_euro:,} €. ".replace(",", " ")
            + f"Reportage visuel : {photo_count} photo(s) vérifiée(s)."
        )
        if estimated_sale_days:
            body += f" Délai de vente indicatif estimé sur ce positionnement : ~{estimated_sale_days} jours."

    return {
        "suggested_title": title,
        "suggested_description": body,
        "photo_tip": (
            "Reportage photo complet (≥ 5 photos) : maximise la visibilité et raccourcit le délai estimé."
            if photo_count >= 5
            else f"Ajoutez encore {5 - photo_count} photo(s) lumineuse(s) pour optimiser le délai de commercialisation."
        ),
    }


class MarketplaceService:
    """Place de marché multi-pays (Particuliers + Agences) avec modération, mobilité 6 modes et RBAC."""

    def __init__(self) -> None:
        self._listings: list[MarketplaceListing] = self._seed_listings()

    def _seed_listings(self) -> list[MarketplaceListing]:
        seeds = [
            ("lst-fr-1", CountryCode.FR, PublisherType.AGENCE, "Monty Partenaire Lyon", "Appartement T3 lumineux avec loggia", "Appartement", "Lyon", "Auvergne-Rhône-Alpes", "Croix-Rousse / Métro C", 365000, 74, 3, "B", "Rénové / Excellent état", ("Tram/Métro 4 min", "Loggia Sud", "DPE B"), 8, 45.7740, 4.8320, 4950),
            ("lst-fr-2", CountryCode.FR, PublisherType.PARTICULIER, "Particulier vérifié", "Appartement T2 traversant proche gare", "Appartement", "Lyon", "Auvergne-Rhône-Alpes", "Part-Dieu", 242000, 49, 2, "C", "Bon état", ("Gare TGV 6 min à pied", "Cave", "Faibles charges"), 6, 45.7605, 4.8592, 4950),
            ("lst-fr-3", CountryCode.FR, PublisherType.AGENCE, "Monty Partenaire Île-de-France", "Appartement familial T4 balcon sans vis-à-vis", "Appartement", "Paris", "Île-de-France", "15e / Convention", 790000, 84, 4, "C", "Bon état", ("Balcon", "Ascenseur", "Métro 3 min"), 9, 48.8373, 2.2963, 9650),
            ("lst-fr-4", CountryCode.FR, PublisherType.PARTICULIER, "Particulier vérifié", "Maison de ville avec jardin clos", "Maison", "Bordeaux", "Nouvelle-Aquitaine", "Nansouty / Tram B", 495000, 112, 5, "C", "Bon état", ("Jardin 90 m²", "Tram 7 min", "Garage vélo"), 7, 44.8235, -0.5734, 5100),
            ("lst-fr-5", CountryCode.FR, PublisherType.AGENCE, "Monty Partenaire Occitanie", "T3 terrasse plein sud Port Marianne", "Appartement", "Montpellier", "Occitanie", "Port Marianne", 378000, 73, 3, "B", "Récent / Neuf", ("Terrasse 18 m²", "Tram L1/L3 2 min", "Parking box"), 8, 43.6003, 3.9041, 5150),
            ("lst-fr-6", CountryCode.FR, PublisherType.PARTICULIER, "Particulier vérifié", "Maison familiale proche TER Baillargues", "Maison", "Montpellier", "Occitanie", "Baillargues (Gare TER)", 429000, 108, 5, "C", "Bon état", ("TER 12 min centre", "Jardin", "Calme"), 5, 43.6606, 4.0132, 4150),
            ("lst-pt-1", CountryCode.PT, PublisherType.AGENCE, "Monty Partner Lisboa", "Apartamento T2 remodelado com varanda", "Appartement", "Lisboa", "Área Metropolitana de Lisboa", "Avenidas Novas / Metro", 445000, 82, 3, "B", "Rénové", ("Metro 3 min", "Varanda", "Certificado B"), 8, 38.7369, -9.1486, 5200),
            ("lst-pt-2", CountryCode.PT, PublisherType.PARTICULIER, "Proprietário verificado", "T2 luminoso perto da Casa da Música", "Appartement", "Porto", "Norte (Porto / Braga)", "Boavista", 295000, 76, 3, "C", "Bon état", ("Metro Boavista", "Garagem", "Elevador"), 6, 41.1588, -8.6307, 3650),
        ]

        out: list[MarketplaceListing] = []
        for (lid, c_code, p_type, p_label, title, kind, city, region, sector, price, surf, rooms, dpe, cond, hl, photos, lat, lon, ref_m2) in seeds:
            timeline = estimate_sale_timeline(
                sector_name=sector,
                property_kind=kind,
                asking_price_euro=price,
                surface_m2=surf,
                dpe=dpe,
                condition_label=cond,
                dvf_price_m2=ref_m2,
                dvf_count=6,
                photo_count=photos,
                provided_docs_ratio=0.8 if p_type == PublisherType.AGENCE else 0.55,
                mandate_type="Exclusif" if p_type == PublisherType.AGENCE else "",
            )
            out.append(
                MarketplaceListing(
                    id=lid,
                    country_code=c_code,
                    publisher_type=p_type,
                    publisher_label=p_label,
                    title=title,
                    property_kind=kind,
                    city=city,
                    region_name=region,
                    sector_label=sector,
                    price_euro=price,
                    surface_m2=surf,
                    rooms=rooms,
                    dpe=dpe,
                    condition_label=cond,
                    highlights=hl,
                    description=generate_listing_copy(
                        country_code=c_code,
                        property_kind=kind,
                        city=city,
                        sector_label=sector,
                        surface_m2=surf,
                        rooms=rooms,
                        price_euro=price,
                        dpe=dpe,
                        condition_label=cond,
                        highlights=list(hl),
                        photo_count=photos,
                        estimated_sale_days=timeline.likely_days if timeline.status == "READY" else None,
                    )["suggested_description"],
                    photo_count=photos,
                    latitude=lat,
                    longitude=lon,
                    sale_timeline=timeline,
                    published_at="2026-11-24",
                    moderation_status=ListingModerationStatus.PUBLIEE,
                    agency_id="agency-france-reseau",
                )
            )
        return out

    def search_listings(
        self,
        *,
        country_code: CountryCode = CountryCode.FR,
        publisher_type: PublisherType | None = None,
        origin_address: str = "Lyon",
        origin_lat: float | None = None,
        origin_lon: float | None = None,
        search_mode: SearchPerimeterMode = SearchPerimeterMode.TRAVEL_TIME,
        transport_mode: TransportMode = TransportMode.TRAM,
        max_radius_km: int = 15,
        max_travel_minutes: int = 30,
        max_price_euro: int | None = None,
        min_surface_m2: int = 0,
        only_within_perimeter: bool = True,
    ) -> list[MarketplaceSearchHit]:
        resolved_lat, resolved_lon, _, _ = _resolve_city_geo(origin_address, country_code)
        o_lat = origin_lat if origin_lat is not None else resolved_lat
        o_lon = origin_lon if origin_lon is not None else resolved_lon

        hits: list[MarketplaceSearchHit] = []
        for item in self._listings:
            # Règle V2 : seules les annonces approuvées (PUBLIEE) sont visibles sur la marketplace publique
            if item.moderation_status != ListingModerationStatus.PUBLIEE:
                continue
            if item.country_code != country_code:
                continue
            if publisher_type and item.publisher_type != publisher_type:
                continue
            if max_price_euro and item.price_euro > max_price_euro:
                continue
            if item.surface_m2 < min_surface_m2:
                continue

            est = evaluate_property_access(
                origin_lat=o_lat,
                origin_lon=o_lon,
                origin_label=origin_address or item.city,
                prop_lat=item.latitude,
                prop_lon=item.longitude,
                transport_mode=transport_mode,
                search_mode=search_mode,
                max_radius_km=max_radius_km,
                max_travel_minutes=max_travel_minutes,
            )
            if only_within_perimeter and not est.within_perimeter:
                continue
            hits.append(
                MarketplaceSearchHit(
                    listing=item,
                    distance_km=est.distance_km,
                    estimated_minutes=est.estimated_minutes,
                    transport_badge=est.badge_label,
                    within_perimeter=est.within_perimeter,
                )
            )
        hits.sort(key=lambda h: (not h.within_perimeter, h.estimated_minutes, h.listing.price_euro))
        return hits

    def publish_listing(
        self,
        *,
        country_code: CountryCode,
        publisher_type: PublisherType,
        publisher_label: str,
        title: str,
        property_kind: str,
        city: str,
        sector_label: str,
        price_euro: int,
        surface_m2: int,
        rooms: int,
        dpe: str,
        condition_label: str,
        highlights: list[str],
        description: str,
        photo_count: int = 3,
        latitude: float | None = None,
        longitude: float | None = None,
        region_name: str | None = None,
        ctx: TenantContext | None = None,
    ) -> MarketplaceListing:
        if ctx is not None:
            if not (ctx.has_permission(Permission.PROPERTY_WRITE) or ctx.has_permission(Permission.SELLER_PORTAL_SELF)):
                raise PermissionError("Permission refusée pour publier une annonce immobilière.")
            if publisher_type == PublisherType.AGENCE and not ctx.has_permission(Permission.PROPERTY_WRITE):
                raise PermissionError("Seul un membre d'agence autorisé peut publier au nom d'une agence.")

        get_country_pack(country_code)
        fb_lat, fb_lon, fb_region, ref_m2 = _resolve_city_geo(city, country_code)
        lat = latitude if latitude is not None else fb_lat
        lon = longitude if longitude is not None else fb_lon
        resolved_region = region_name or fb_region
        safe_title = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(title, 180))
        safe_desc = redact_direct_pii_for_ai(InputSanitizer.sanitize_text(description, 2000))
        if not safe_title or price_euro < 10_000 or surface_m2 < 9:
            raise ValueError("Titre, prix (>= 10 000 €) et surface (>= 9 m²) obligatoires.")

        # Règle V2 : toute annonce Particulier (ou rôle vendeur/acquéreur) passe en file de modération A_VERIFIER
        if publisher_type == PublisherType.PARTICULIER or (
            ctx is not None and ctx.role in (MontyRole.SELLER, MontyRole.BUYER)
        ):
            initial_status = ListingModerationStatus.A_VERIFIER
        else:
            initial_status = ListingModerationStatus.PUBLIEE

        timeline = estimate_sale_timeline(
            sector_name=sector_label or city,
            property_kind=property_kind,
            asking_price_euro=price_euro,
            surface_m2=surface_m2,
            dpe=dpe,
            condition_label=condition_label,
            dvf_price_m2=ref_m2,
            dvf_count=3,
            photo_count=photo_count,
            provided_docs_ratio=0.75 if publisher_type == PublisherType.AGENCE else 0.5,
            mandate_type="Exclusif" if publisher_type == PublisherType.AGENCE else "",
        )
        listing = MarketplaceListing(
            id=f"lst-{country_code.value.lower()}-{uuid.uuid4().hex[:8]}",
            country_code=country_code,
            publisher_type=publisher_type,
            publisher_label=InputSanitizer.sanitize_text(publisher_label, 100) or (
                "Particulier vérifié" if publisher_type == PublisherType.PARTICULIER else "Agence partenaire"
            ),
            title=safe_title,
            property_kind=InputSanitizer.sanitize_text(property_kind, 40) or "Appartement",
            city=InputSanitizer.sanitize_text(city, 80),
            region_name=resolved_region,
            sector_label=InputSanitizer.sanitize_text(sector_label, 100) or city,
            price_euro=price_euro,
            surface_m2=surface_m2,
            rooms=max(1, rooms),
            dpe=dpe.strip().upper()[:2],
            condition_label=InputSanitizer.sanitize_text(condition_label, 60) or "Bon état",
            highlights=tuple(InputSanitizer.sanitize_text(h, 60) for h in highlights if h.strip()),
            description=safe_desc,
            photo_count=max(0, photo_count),
            latitude=lat,
            longitude=lon,
            sale_timeline=timeline,
            published_at=date.today().isoformat(),
            moderation_status=initial_status,
            agency_id=ctx.agency_id if ctx is not None else "agency-france-reseau",
            author_user_id=ctx.user_id if ctx is not None else "",
        )
        self._listings.insert(0, listing)
        return listing

    def list_pending_moderation(self, ctx: TenantContext) -> list[MarketplaceListing]:
        ctx.require_permission(Permission.PROPERTY_MODERATE)
        return [
            item
            for item in self._listings
            if item.moderation_status == ListingModerationStatus.A_VERIFIER
            and (ctx.role == MontyRole.SUPER_ADMIN or item.agency_id == ctx.agency_id)
        ]

    def moderate_listing(
        self,
        ctx: TenantContext,
        *,
        listing_id: str,
        approve: bool,
        moderation_note: str = "",
    ) -> MarketplaceListing:
        ctx.require_permission(Permission.PROPERTY_MODERATE)
        for idx, item in enumerate(self._listings):
            if item.id == listing_id:
                ctx.assert_same_agency(item.agency_id)
                updated = replace(
                    item,
                    moderation_status=(
                        ListingModerationStatus.PUBLIEE if approve else ListingModerationStatus.REJETEE
                    ),
                    moderation_note=InputSanitizer.sanitize_text(moderation_note, 500),
                )
                self._listings[idx] = updated
                return updated
        raise ValueError("Annonce introuvable pour modération.")
