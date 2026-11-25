import type {
  CopywritingResponse,
  CountryCode,
  MarketplaceListing,
  MarketplaceSearchHit,
  PublisherType,
  SaleTimelineEstimate,
  SearchPerimeterMode,
  TransportMode,
} from "./types";

interface CityGeoInfo {
  lat: number;
  lon: number;
  country: CountryCode;
  region: string;
  refM2: number;
}

export const CITY_GEO_MAP: Record<string, CityGeoInfo> = {
  paris: { lat: 48.8566, lon: 2.3522, country: "FR", region: "Île-de-France", refM2: 9650 },
  lyon: { lat: 45.764, lon: 4.8357, country: "FR", region: "Auvergne-Rhône-Alpes", refM2: 4950 },
  bordeaux: { lat: 44.8378, lon: -0.5792, country: "FR", region: "Nouvelle-Aquitaine", refM2: 4550 },
  nantes: { lat: 47.2184, lon: -1.5536, country: "FR", region: "Pays de la Loire", refM2: 3850 },
  toulouse: { lat: 43.6047, lon: 1.4442, country: "FR", region: "Occitanie", refM2: 3650 },
  marseille: { lat: 43.2965, lon: 5.3698, country: "FR", region: "Provence-Alpes-Côte d'Azur", refM2: 3550 },
  montpellier: { lat: 43.6108, lon: 3.8767, country: "FR", region: "Occitanie", refM2: 4150 },
  lille: { lat: 50.6292, lon: 3.0573, country: "FR", region: "Hauts-de-France", refM2: 3700 },
  nice: { lat: 43.7102, lon: 7.262, country: "FR", region: "Provence-Alpes-Côte d'Azur", refM2: 5250 },
  strasbourg: { lat: 48.5734, lon: 7.7521, country: "FR", region: "Grand Est", refM2: 3800 },
  rennes: { lat: 48.1173, lon: -1.6778, country: "FR", region: "Bretagne", refM2: 4100 },
  lisboa: { lat: 38.7223, lon: -9.1393, country: "PT", region: "Área Metropolitana de Lisboa", refM2: 5200 },
  porto: { lat: 41.1579, lon: -8.6291, country: "PT", region: "Norte (Porto / Braga)", refM2: 3650 },
  faro: { lat: 37.0194, lon: -7.9304, country: "PT", region: "Algarve", refM2: 3400 },
};

const TRANSPORT_SPEED_KMH: Record<TransportMode, number> = {
  VOITURE: 34,
  TRAM: 19,
  BUS: 16,
  TRAIN: 52,
  VELO: 15,
  PIED: 4.8,
};

const TRANSPORT_LABELS: Record<TransportMode, string> = {
  VOITURE: "Voiture",
  TRAM: "Tramway / Métro",
  BUS: "Bus",
  TRAIN: "Train / TER",
  VELO: "Vélo",
  PIED: "À pied",
};

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function resolveCityGeo(cityOrAddress: string, countryCode: CountryCode): CityGeoInfo {
  const lowered = cityOrAddress.toLowerCase();
  for (const [key, info] of Object.entries(CITY_GEO_MAP)) {
    if (info.country === countryCode && lowered.includes(key)) {
      return info;
    }
  }
  return countryCode === "PT" ? CITY_GEO_MAP.lisboa : CITY_GEO_MAP.lyon;
}

export function findNearestCityName(lat: number, lon: number, countryCode: CountryCode): string {
  let bestKey = countryCode === "PT" ? "lisboa" : "lyon";
  let bestDist = Number.POSITIVE_INFINITY;
  for (const [key, info] of Object.entries(CITY_GEO_MAP)) {
    if (info.country !== countryCode) continue;
    const d = haversineKm(lat, lon, info.lat, info.lon);
    if (d < bestDist) {
      bestDist = d;
      bestKey = key;
    }
  }
  return bestKey.charAt(0).toUpperCase() + bestKey.slice(1);
}

export function computeLocalSaleTimeline(params: {
  priceEuro: number;
  surfaceM2: number;
  dpe: string;
  conditionLabel: string;
  refM2: number;
  photoCount: number;
  publisherType: PublisherType;
}): SaleTimelineEstimate {
  if (params.surfaceM2 <= 0 || params.priceEuro <= 0) {
    return {
      status: "INSUFFICIENT_DATA",
      low_days: 0,
      likely_days: 0,
      high_days: 0,
      confidence_label: "Données insuffisantes",
    };
  }
  const askingM2 = params.priceEuro / Math.max(1, params.surfaceM2);
  const gapPct = ((askingM2 - params.refM2) / Math.max(1, params.refM2)) * 100;
  let baseDays = 46;
  if (gapPct > 12) baseDays += 38;
  else if (gapPct > 5) baseDays += 18;
  else if (gapPct < -4) baseDays -= 10;

  const dpeUpper = params.dpe.toUpperCase();
  if (dpeUpper === "A" || dpeUpper === "B") baseDays -= 7;
  else if (dpeUpper === "F" || dpeUpper === "G") baseDays += 24;

  if (params.photoCount >= 6) baseDays -= 5;
  else if (params.photoCount < 3) baseDays += 11;

  if (params.publisherType === "AGENCE") baseDays -= 4;

  const likely = Math.max(21, Math.min(180, Math.round(baseDays)));
  return {
    status: "READY",
    low_days: Math.max(14, likely - 12),
    likely_days: likely,
    high_days: likely + 18,
    confidence_label: params.photoCount >= 5 ? "Fiabilité bonne (repères DVF)" : "Fiabilité indicative",
  };
}

export function redactPiiText(raw: string): string {
  return raw
    .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, "[email-masqué-rgpd]")
    .replace(/(?:\+?\d[\d .\-]{7,}\d)/g, "[tél-masqué-rgpd]");
}

export function buildLocalListingCopy(params: {
  country_code: CountryCode;
  property_kind: string;
  city: string;
  sector_label: string;
  surface_m2: number;
  rooms: number;
  price_euro: number;
  dpe: string;
  condition_label: string;
  highlights: string[];
  photo_count: number;
  estimated_sale_days?: number;
}): CopywritingResponse {
  const cleanCity = redactPiiText(params.city.trim()) || (params.country_code === "PT" ? "Lisboa" : "Lyon");
  const cleanSector = redactPiiText(params.sector_label.trim()) || cleanCity;
  const cleanHl = params.highlights.map((h) => redactPiiText(h.trim())).filter(Boolean);

  if (params.country_code === "PT") {
    return {
      suggested_title: `${params.property_kind} T${Math.max(1, params.rooms - 1)} • ${cleanCity} (${cleanSector}) — ${params.surface_m2} m² • CE ${params.dpe.toUpperCase()}`,
      suggested_description:
        `Localizado em ${cleanCity} (${cleanSector}), este ${params.property_kind.toLowerCase()} de ${params.surface_m2} m² ` +
        `(${params.rooms} divisões) apresenta estado « ${params.condition_label} » e Certificado Energético ${params.dpe.toUpperCase()}. ` +
        (cleanHl.length > 0 ? `Destaques: ${cleanHl.join(", ")}. ` : "") +
        `Preço pedido : ${params.price_euro.toLocaleString("fr-FR")} €. Dossier com ${params.photo_count} foto(s).`,
      photo_tip:
        params.photo_count >= 5
          ? "Reportage completo (≥ 5 fotos): acelera os contactos qualificados."
          : `Adicione mais ${5 - params.photo_count} foto(s) para aumentar a visibilidade.`,
    };
  }

  return {
    suggested_title: `${params.property_kind} ${params.rooms} pièces • ${params.surface_m2} m² — ${cleanCity} (${cleanSector}) • DPE ${params.dpe.toUpperCase()}`,
    suggested_description:
      `Situé à ${cleanCity} (${cleanSector}), découvrez ce bien (${params.property_kind.toLowerCase()}) de ${params.surface_m2} m² ` +
      `comprenant ${params.rooms} pièces, en état « ${params.condition_label} » (classe énergétique ${params.dpe.toUpperCase()}). ` +
      (cleanHl.length > 0 ? `Atouts principaux : ${cleanHl.join(", ")}. ` : "") +
      `Prix affiché : ${params.price_euro.toLocaleString("fr-FR")} €. ` +
      `Reportage visuel : ${params.photo_count} photo(s) vérifiée(s).` +
      (params.estimated_sale_days ? ` Délai de vente indicatif estimé sur ce positionnement : ~${params.estimated_sale_days} jours.` : ""),
    photo_tip:
      params.photo_count >= 5
        ? "Reportage photo complet (≥ 5 photos) : maximise la visibilité et raccourcit le délai estimé."
        : `Ajoutez encore ${5 - params.photo_count} photo(s) lumineuse(s) pour optimiser le délai de commercialisation.`,
  };
}

export const INITIAL_LOCAL_LISTINGS: MarketplaceListing[] = [
  {
    id: "lst-fr-1",
    country_code: "FR",
    publisher_type: "AGENCE",
    publisher_label: "Monty Partenaire Lyon",
    title: "Appartement T3 lumineux avec loggia",
    property_kind: "Appartement",
    city: "Lyon",
    region_name: "Auvergne-Rhône-Alpes",
    sector_label: "Croix-Rousse / Métro C",
    price_euro: 365000,
    surface_m2: 74,
    rooms: 3,
    dpe: "B",
    condition_label: "Rénové / Excellent état",
    highlights: ["Tram/Métro 4 min", "Loggia Sud", "DPE B"],
    description:
      "Situé à Lyon (Croix-Rousse / Métro C), appartement T3 de 74 m² traversant, DPE B, calme et lumineux avec loggia plein sud.",
    photo_count: 8,
    latitude: 45.774,
    longitude: 4.832,
    sale_timeline: { status: "READY", low_days: 26, likely_days: 38, high_days: 56, confidence_label: "Fiabilité bonne (repères DVF)" },
    published_at: "2026-11-24",
  },
  {
    id: "lst-fr-2",
    country_code: "FR",
    publisher_type: "PARTICULIER",
    publisher_label: "Particulier vérifié",
    title: "Appartement T2 traversant proche gare TGV",
    property_kind: "Appartement",
    city: "Lyon",
    region_name: "Auvergne-Rhône-Alpes",
    sector_label: "Part-Dieu",
    price_euro: 242000,
    surface_m2: 49,
    rooms: 2,
    dpe: "C",
    condition_label: "Bon état",
    highlights: ["Gare TGV 6 min à pied", "Cave", "Faibles charges"],
    description:
      "À 6 minutes à pied de la gare Lyon Part-Dieu, T2 traversant de 49 m² bien agencé, faibles charges et cave privative.",
    photo_count: 6,
    latitude: 45.7605,
    longitude: 4.8592,
    sale_timeline: { status: "READY", low_days: 30, likely_days: 42, high_days: 60, confidence_label: "Fiabilité bonne (repères DVF)" },
    published_at: "2026-11-24",
  },
  {
    id: "lst-fr-3",
    country_code: "FR",
    publisher_type: "AGENCE",
    publisher_label: "Monty Partenaire Île-de-France",
    title: "Appartement familial T4 balcon sans vis-à-vis",
    property_kind: "Appartement",
    city: "Paris",
    region_name: "Île-de-France",
    sector_label: "15e / Convention",
    price_euro: 790000,
    surface_m2: 84,
    rooms: 4,
    dpe: "C",
    condition_label: "Bon état",
    highlights: ["Balcon", "Ascenseur", "Métro 3 min"],
    description:
      "Paris 15e Convention : 4 pièces familial de 84 m² au 4e étage avec ascenseur, balcon filant et plan optimisé sans perte d'espace.",
    photo_count: 9,
    latitude: 48.8373,
    longitude: 2.2963,
    sale_timeline: { status: "READY", low_days: 28, likely_days: 40, high_days: 58, confidence_label: "Fiabilité bonne (repères DVF)" },
    published_at: "2026-11-24",
  },
  {
    id: "lst-fr-4",
    country_code: "FR",
    publisher_type: "PARTICULIER",
    publisher_label: "Particulier vérifié",
    title: "Maison de ville avec jardin clos",
    property_kind: "Maison",
    city: "Bordeaux",
    region_name: "Nouvelle-Aquitaine",
    sector_label: "Nansouty / Tram B",
    price_euro: 495000,
    surface_m2: 112,
    rooms: 5,
    dpe: "C",
    condition_label: "Bon état",
    highlights: ["Jardin 90 m²", "Tram 7 min", "Garage vélo"],
    description:
      "Quartier Nansouty à Bordeaux, maison pierre 5 pièces de 112 m² avec jardin arboré de 90 m² sans vis-à-vis et local vélo.",
    photo_count: 7,
    latitude: 44.8235,
    longitude: -0.5734,
    sale_timeline: { status: "READY", low_days: 31, likely_days: 43, high_days: 61, confidence_label: "Fiabilité bonne (repères DVF)" },
    published_at: "2026-11-24",
  },
  {
    id: "lst-fr-5",
    country_code: "FR",
    publisher_type: "AGENCE",
    publisher_label: "Monty Partenaire Occitanie",
    title: "T3 terrasse plein sud Port Marianne",
    property_kind: "Appartement",
    city: "Montpellier",
    region_name: "Occitanie",
    sector_label: "Port Marianne",
    price_euro: 378000,
    surface_m2: 73,
    rooms: 3,
    dpe: "B",
    condition_label: "Récent / Neuf",
    highlights: ["Terrasse 18 m²", "Tram L1/L3 2 min", "Parking box"],
    description:
      "Port Marianne à Montpellier : T3 récent de 73 m² avec terrasse exposée Sud de 18 m², tram L1/L3 à 2 minutes et box fermé.",
    photo_count: 8,
    latitude: 43.6003,
    longitude: 3.9041,
    sale_timeline: { status: "READY", low_days: 24, likely_days: 36, high_days: 54, confidence_label: "Fiabilité bonne (repères DVF)" },
    published_at: "2026-11-24",
  },
  {
    id: "lst-fr-6",
    country_code: "FR",
    publisher_type: "PARTICULIER",
    publisher_label: "Particulier vérifié",
    title: "Maison familiale proche TER Baillargues",
    property_kind: "Maison",
    city: "Montpellier",
    region_name: "Occitanie",
    sector_label: "Baillargues (Gare TER)",
    price_euro: 429000,
    surface_m2: 108,
    rooms: 5,
    dpe: "C",
    condition_label: "Bon état",
    highlights: ["TER 12 min centre", "Jardin", "Calme"],
    description:
      "À Baillargues (12 min TER du centre de Montpellier), maison familiale 5 pièces de 108 m² avec jardin clos et secteur scolaire calme.",
    photo_count: 5,
    latitude: 43.6606,
    longitude: 4.0132,
    sale_timeline: { status: "READY", low_days: 32, likely_days: 44, high_days: 62, confidence_label: "Fiabilité bonne (repères DVF)" },
    published_at: "2026-11-24",
  },
  {
    id: "lst-pt-1",
    country_code: "PT",
    publisher_type: "AGENCE",
    publisher_label: "Monty Partner Lisboa",
    title: "Apartamento T2 remodelado com varanda",
    property_kind: "Appartement",
    city: "Lisboa",
    region_name: "Área Metropolitana de Lisboa",
    sector_label: "Avenidas Novas / Metro",
    price_euro: 445000,
    surface_m2: 82,
    rooms: 3,
    dpe: "B",
    condition_label: "Rénové",
    highlights: ["Metro 3 min", "Varanda", "Certificado B"],
    description:
      "Localizado em Lisboa (Avenidas Novas), apartamento T2 de 82 m² totalmente remodelado com varanda, a 3 minutos do metro.",
    photo_count: 8,
    latitude: 38.7369,
    longitude: -9.1486,
    sale_timeline: { status: "READY", low_days: 28, likely_days: 40, high_days: 58, confidence_label: "Fiabilidade boa (referências PT)" },
    published_at: "2026-11-24",
  },
  {
    id: "lst-pt-2",
    country_code: "PT",
    publisher_type: "PARTICULIER",
    publisher_label: "Proprietário verificado",
    title: "T2 luminoso perto da Casa da Música",
    property_kind: "Appartement",
    city: "Porto",
    region_name: "Norte (Porto / Braga)",
    sector_label: "Boavista",
    price_euro: 295000,
    surface_m2: 76,
    rooms: 3,
    dpe: "C",
    condition_label: "Bon état",
    highlights: ["Metro Boavista", "Garagem", "Elevador"],
    description:
      "Porto Boavista : apartamento T2 luminoso de 76 m² perto da Casa da Música com lugar de garagem e elevador.",
    photo_count: 6,
    latitude: 41.1588,
    longitude: -8.6307,
    sale_timeline: { status: "READY", low_days: 30, likely_days: 42, high_days: 60, confidence_label: "Fiabilidade boa (referências PT)" },
    published_at: "2026-11-24",
  },
];

export function searchLocalListings(
  listings: MarketplaceListing[],
  params: {
    country_code: CountryCode;
    publisher_type?: PublisherType;
    origin_address: string;
    search_mode: SearchPerimeterMode;
    transport_mode: TransportMode;
    max_radius_km: number;
    max_travel_minutes: number;
    max_price_euro?: number;
    min_surface_m2?: number;
    only_within_perimeter: boolean;
  }
): MarketplaceSearchHit[] {
  const origin = resolveCityGeo(params.origin_address, params.country_code);
  const speed = TRANSPORT_SPEED_KMH[params.transport_mode] ?? 19;

  const hits: MarketplaceSearchHit[] = [];
  for (const item of listings) {
    if (item.country_code !== params.country_code) continue;
    if (params.publisher_type && item.publisher_type !== params.publisher_type) continue;
    if (params.max_price_euro && item.price_euro > params.max_price_euro) continue;
    if (params.min_surface_m2 && item.surface_m2 < params.min_surface_m2) continue;

    const rawDist = haversineKm(origin.lat, origin.lon, item.latitude, item.longitude);
    const roadDist = Math.round(rawDist * 1.18 * 10) / 10;
    const estMinutes = Math.max(3, Math.round((roadDist / speed) * 60 + 2));

    const within =
      params.search_mode === "RADIUS_KM"
        ? roadDist <= params.max_radius_km
        : estMinutes <= params.max_travel_minutes;

    if (params.only_within_perimeter && !within) continue;

    hits.push({
      listing: item,
      distance_km: roadDist,
      estimated_minutes: estMinutes,
      transport_badge: `${TRANSPORT_LABELS[params.transport_mode]} ~${estMinutes} min (${roadDist} km)`,
      within_perimeter: within,
    });
  }

  hits.sort((a, b) => {
    if (a.within_perimeter !== b.within_perimeter) return a.within_perimeter ? -1 : 1;
    if (a.estimated_minutes !== b.estimated_minutes) return a.estimated_minutes - b.estimated_minutes;
    return a.listing.price_euro - b.listing.price_euro;
  });
  return hits;
}
