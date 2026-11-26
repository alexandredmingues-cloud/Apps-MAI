"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  Bell,
  Bike,
  Building2,
  Bus,
  CalendarPlus,
  Car,
  CheckCircle2,
  Clock,
  Footprints,
  GitCompare,
  Globe2,
  Heart,
  LocateFixed,
  MapPin,
  PlusCircle,
  Search,
  Sparkles,
  TrainFront,
  TramFront,
  UserCheck,
  X,
} from "lucide-react";
import { hasInMemoryAccessToken, montyApi } from "@/lib/api-client";
import {
  buildLocalListingCopy,
  computeLocalSaleTimeline,
  findNearestCityName,
  INITIAL_LOCAL_LISTINGS,
  redactPiiText,
  resolveCityGeo,
  searchLocalListings,
} from "@/lib/marketplace-local";
import type {
  CountryCode,
  MarketplaceListing,
  MarketplaceSearchHit,
  PublisherType,
  SearchPerimeterMode,
  TransportMode,
} from "@/lib/types";
import { BuyerSearchTools } from "./BuyerSearchTools";
import { MarketplacePublishForm, type PublishFormState } from "./MarketplacePublishForm";
import { PartnerFeedsPanel } from "./PartnerFeedsPanel";

interface MarketplaceAndPublishViewProps {
  initialTab?: "search" | "publish";
  initialCountry?: CountryCode;
  showTabs?: boolean;
  audienceLabel?: string;
}

const TRANSPORT_OPTIONS: { mode: TransportMode; label: string; Icon: React.ComponentType<{ className?: string }> }[] = [
  { mode: "TRAM", label: "Tram / Métro", Icon: TramFront },
  { mode: "TRAIN", label: "Train / TER", Icon: TrainFront },
  { mode: "BUS", label: "Bus", Icon: Bus },
  { mode: "VELO", label: "Vélo", Icon: Bike },
  { mode: "PIED", label: "À pied", Icon: Footprints },
  { mode: "VOITURE", label: "Voiture", Icon: Car },
];

const QUICK_CITIES: Record<CountryCode, string[]> = {
  FR: ["Lyon", "Paris", "Montpellier", "Bordeaux", "Nantes"],
  PT: ["Lisboa", "Porto", "Faro"],
};

export function MarketplaceAndPublishView({
  initialTab = "search",
  initialCountry = "FR",
  showTabs = true,
  audienceLabel = "Particuliers & Agences",
}: MarketplaceAndPublishViewProps) {
  const [activeTab, setActiveTab] = useState<"search" | "publish" | "feeds">(initialTab);
  const [countryCode, setCountryCode] = useState<CountryCode>(initialCountry);

  // Filtres recherche mobilité
  const [originAddress, setOriginAddress] = useState(initialCountry === "PT" ? "Lisboa" : "Lyon");
  const [originCoords, setOriginCoords] = useState<{ lat: number; lon: number } | null>(null);
  const [geoNotice, setGeoNotice] = useState<string | null>(null);
  const [searchMode, setSearchMode] = useState<SearchPerimeterMode>("TRAVEL_TIME");
  const [transportMode, setTransportMode] = useState<TransportMode>("TRAM");
  const [maxMinutes, setMaxMinutes] = useState(40);
  const [maxRadiusKm, setMaxRadiusKm] = useState(25);
  const [publisherFilter, setPublisherFilter] = useState<"ALL" | PublisherType>("ALL");
  const [maxPrice, setMaxPrice] = useState<number>(900000);
  const [minSurface] = useState<number>(0);
  const [onlyWithinPerimeter, setOnlyWithinPerimeter] = useState<boolean>(false);
  const [showAlertDrawer, setShowAlertDrawer] = useState<boolean>(false);

  // Catalogue local fusionné + état API (initialisé immédiatement pour éviter 0 annonce au premier rendu)
  const [localCatalog, setLocalCatalog] = useState<MarketplaceListing[]>(INITIAL_LOCAL_LISTINGS);
  const [hits, setHits] = useState<MarketplaceSearchHit[]>(() =>
    searchLocalListings(INITIAL_LOCAL_LISTINGS, {
      country_code: initialCountry,
      origin_address: initialCountry === "PT" ? "Lisboa" : "Lyon",
      search_mode: "TRAVEL_TIME",
      transport_mode: "TRAM",
      max_radius_km: 25,
      max_travel_minutes: 40,
      max_price_euro: 900000,
      min_surface_m2: 0,
      only_within_perimeter: false,
    })
  );
  const [dataSourceMode, setDataSourceMode] = useState<"api" | "local">("local");
  const [isDemoCatalog, setIsDemoCatalog] = useState<boolean>(true);
  const [loadingSearch, setLoadingSearch] = useState(false);

  // Comparateur côte à côte (max 3 biens)
  const [compareIds, setCompareIds] = useState<string[]>([]);

  // Formulaire publication vendeur
  const [pubState, setPubState] = useState<PublishFormState>({
    pubType: "PARTICULIER",
    pubLabel: "",
    pubKind: "Appartement",
    pubCity: initialCountry === "PT" ? "Lisboa" : "Nantes",
    pubSector: "Centre / Tram",
    pubPrice: 325000,
    pubSurface: 72,
    pubRooms: 3,
    pubDpe: "B",
    pubCondition: "Bon état",
    pubHighlightsRaw: "Balcon plein sud, Tram 4 min, Faibles charges",
    pubPhotos: 6,
    pubTitle: "",
    pubDescription: "",
    wantProAccompaniment: false,
  });
  const [copyTip, setCopyTip] = useState<string | null>(null);
  const [publishNotice, setPublishNotice] = useState<string | null>(null);
  const [showPostPublishCta, setShowPostPublishCta] = useState<boolean>(false);
  const [showValuationForm, setShowValuationForm] = useState<boolean>(false);
  const [valuationEmail, setValuationEmail] = useState<string>("");
  const [valuationRgpd, setValuationRgpd] = useState<boolean>(false);
  const [valuationBusy, setValuationBusy] = useState<boolean>(false);
  const [valuationError, setValuationError] = useState<string | null>(null);
  const [valuationRequested, setValuationRequested] = useState<boolean>(false);
  const [publishBusy, setPublishBusy] = useState(false);
  const [pendingModeration, setPendingModeration] = useState<MarketplaceListing[]>([]);

  // Favoris & Demande de visite (VISIT_REQUEST RGPD -> CRM Pro)
  const [favoriteIds, setFavoriteIds] = useState<string[]>([]);
  const [visitTarget, setVisitTarget] = useState<MarketplaceListing | null>(null);
  const [visitName, setVisitName] = useState("");
  const [visitEmail, setVisitEmail] = useState("");
  const [visitPhone, setVisitPhone] = useState("");
  const [visitSlot, setVisitSlot] = useState("Samedi matin ou fin de journée en semaine");
  const [visitRgpd, setVisitRgpd] = useState(false);
  const [visitBusy, setVisitBusy] = useState(false);
  const [visitFeedback, setVisitFeedback] = useState<string | null>(null);
  const [visitError, setVisitError] = useState<string | null>(null);

  const isProView = audienceLabel.includes("Monty Pro");

  useEffect(() => {
    if (typeof window !== "undefined") {
      try {
        const raw = window.localStorage.getItem("monty_favorites_v1");
        if (raw) {
          const parsed = JSON.parse(raw) as { listing_id: string }[];
          if (Array.isArray(parsed)) {
            setFavoriteIds(parsed.map((item) => item.listing_id));
          }
        }
      } catch {
        // Ignore
      }
    }
    if (hasInMemoryAccessToken()) {
      montyApi
        .listPortalFavorites()
        .then((res) => setFavoriteIds(res.favorites.map((f) => f.listing_id)))
        .catch(() => {
          // Ignore
        });
    }
  }, []);

  async function toggleFavorite(listing: MarketplaceListing) {
    const exists = favoriteIds.includes(listing.id);
    const nextIds = exists ? favoriteIds.filter((id) => id !== listing.id) : [listing.id, ...favoriteIds];
    setFavoriteIds(nextIds);
    if (typeof window !== "undefined") {
      try {
        const raw = window.localStorage.getItem("monty_favorites_v1");
        const prev = raw ? (JSON.parse(raw) as Record<string, unknown>[]) : [];
        const filtered = Array.isArray(prev) ? prev.filter((item) => item.listing_id !== listing.id) : [];
        const nextSnapshots = exists
          ? filtered
          : [
              {
                listing_id: listing.id,
                title: listing.title,
                city: listing.city,
                price_euro: listing.price_euro,
                surface_m2: listing.surface_m2,
                rooms: listing.rooms,
              },
              ...filtered,
            ];
        window.localStorage.setItem("monty_favorites_v1", JSON.stringify(nextSnapshots));
      } catch {
        // Ignore
      }
    }
    if (hasInMemoryAccessToken()) {
      try {
        if (exists) {
          await montyApi.removePortalFavorite(listing.id);
        } else {
          await montyApi.addPortalFavorite({
            listing_id: listing.id,
            listing_snapshot: {
              title: listing.title,
              city: listing.city,
              price_euro: listing.price_euro,
              surface_m2: listing.surface_m2,
              rooms: listing.rooms,
            },
          });
        }
      } catch {
        // Ignore
      }
    }
  }

  async function handleSubmitVisitRequest(e: React.FormEvent) {
    e.preventDefault();
    if (!visitTarget) return;
    setVisitError(null);
    const cleanEmail = visitEmail.trim();
    if (!cleanEmail.includes("@") || !cleanEmail.includes(".")) {
      setVisitError("Saisissez une adresse e-mail valide.");
      return;
    }
    if (!visitRgpd) {
      setVisitError("Cochez le consentement RGPD pour transmettre votre demande de visite.");
      return;
    }
    setVisitBusy(true);
    try {
      await montyApi.submitPublicLead({
        agency_id: visitTarget.agency_id || undefined,
        lead_kind: "VISIT_REQUEST",
        country_code: countryCode,
        full_name: visitName.trim() || cleanEmail.split("@")[0],
        email: cleanEmail,
        phone: visitPhone.trim(),
        city: visitTarget.city,
        budget_euro: visitTarget.price_euro,
        property_title: visitTarget.title,
        notes: `Créneau souhaité : ${visitSlot.trim()} — Bien #${visitTarget.id}`,
        rgpd_consent_granted: true,
      });
      setVisitFeedback(`Demande de visite envoyée pour « ${visitTarget.title} ». Le conseiller de l'agence vous recontacte.`);
      setVisitTarget(null);
      setVisitName("");
      setVisitEmail("");
      setVisitPhone("");
      setVisitRgpd(false);
    } catch (err) {
      setVisitError(err instanceof Error ? err.message : "Envoi temporairement indisponible.");
    } finally {
      setVisitBusy(false);
    }
  }

  function patchPubState(patch: Partial<PublishFormState>) {
    setPubState((prev) => ({ ...prev, ...patch }));
  }

  async function loadPendingModeration() {
    if (!isProView || !hasInMemoryAccessToken()) {
      setPendingModeration([]);
      return;
    }
    try {
      const res = await montyApi.listPendingModeration();
      setPendingModeration(res.listings);
    } catch {
      setPendingModeration([]);
    }
  }

  async function handleModerationDecision(listingId: string, approve: boolean) {
    try {
      await montyApi.moderateListing(listingId, {
        approve,
        moderation_note: approve ? "Validé par modérateur agence" : "Compléments requis",
      });
      await loadPendingModeration();
      await runSearch();
    } catch {
      // Ignorer silencieusement si session expirée
    }
  }

  useEffect(() => {
    setOriginAddress(countryCode === "PT" ? "Lisboa" : "Lyon");
    setOriginCoords(null);
    setGeoNotice(null);
    patchPubState({ pubCity: countryCode === "PT" ? "Lisboa" : "Nantes" });
    setCompareIds([]);
  }, [countryCode]);

  function computeLocalHitsNow() {
    return searchLocalListings(localCatalog, {
      country_code: countryCode,
      publisher_type: publisherFilter === "ALL" ? undefined : publisherFilter,
      origin_address: originAddress,
      origin_lat: originCoords?.lat,
      origin_lon: originCoords?.lon,
      search_mode: searchMode,
      transport_mode: transportMode,
      max_radius_km: maxRadiusKm,
      max_travel_minutes: maxMinutes,
      max_price_euro: maxPrice > 0 ? maxPrice : undefined,
      min_surface_m2: minSurface,
      only_within_perimeter: onlyWithinPerimeter,
    });
  }

  async function runSearch() {
    setLoadingSearch(true);
    try {
      const resp = await montyApi.searchMarketplaceListings({
        country_code: countryCode,
        publisher_type: publisherFilter === "ALL" ? undefined : publisherFilter,
        origin_address: originAddress,
        origin_lat: originCoords?.lat,
        origin_lon: originCoords?.lon,
        search_mode: searchMode,
        transport_mode: transportMode,
        max_radius_km: maxRadiusKm,
        max_travel_minutes: maxMinutes,
        max_price_euro: maxPrice > 0 ? maxPrice : undefined,
        min_surface_m2: minSurface,
        only_within_perimeter: onlyWithinPerimeter,
      });
      if (resp.hits.length > 0) {
        setHits(resp.hits);
        setDataSourceMode("api");
        setIsDemoCatalog(resp.hits.every((h) => h.listing.id.startsWith("lst-fr-") || h.listing.id.startsWith("lst-pt-")));
      } else {
        const localHits = computeLocalHitsNow();
        if (!onlyWithinPerimeter && localHits.length > 0) {
          setHits(localHits);
          setDataSourceMode("local");
          setIsDemoCatalog(true);
        } else {
          setHits(resp.hits);
          setDataSourceMode("api");
          setIsDemoCatalog(false);
        }
      }
    } catch {
      const localHits = computeLocalHitsNow();
      setHits(localHits);
      setDataSourceMode("local");
      setIsDemoCatalog(true);
    } finally {
      setLoadingSearch(false);
    }
  }

  useEffect(() => {
    runSearch();
    loadPendingModeration();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [countryCode, publisherFilter, searchMode, transportMode, maxMinutes, maxRadiusKm, onlyWithinPerimeter, localCatalog, originCoords]);

  function handleGeolocate() {
    if (typeof window === "undefined" || !navigator.geolocation) {
      setGeoNotice("Géolocalisation non supportée par ce navigateur : choisissez une ville ci-dessous.");
      return;
    }
    setGeoNotice("Localisation en cours...");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude, longitude } = pos.coords;
        const nearest = findNearestCityName(latitude, longitude, countryCode);
        setOriginCoords({ lat: latitude, lon: longitude });
        setOriginAddress(nearest);
        setGeoNotice(`Position détectée : temps de trajet recalculés autour de ${nearest}.`);
      },
      () => {
        setGeoNotice("Autorisation refusée : sélectionnez simplement votre ville de référence ci-dessous.");
      },
      { timeout: 6000 }
    );
  }

  function handleBroadenSearch() {
    setOnlyWithinPerimeter(false);
    setMaxMinutes((m) => Math.min(180, m + 30));
    setMaxRadiusKm((r) => Math.min(250, r + 35));
    setMaxPrice((p) => (p < 900000 ? 900000 : p));
  }

  const previewTimeline = useMemo(() => {
    const geo = resolveCityGeo(pubState.pubCity, countryCode);
    return computeLocalSaleTimeline({
      priceEuro: pubState.pubPrice,
      surfaceM2: pubState.pubSurface,
      dpe: pubState.pubDpe,
      conditionLabel: pubState.pubCondition,
      refM2: geo.refM2,
      photoCount: pubState.pubPhotos,
      publisherType: pubState.pubType,
    });
  }, [
    pubState.pubCity,
    countryCode,
    pubState.pubPrice,
    pubState.pubSurface,
    pubState.pubDpe,
    pubState.pubCondition,
    pubState.pubPhotos,
    pubState.pubType,
  ]);

  function toggleCompare(listingId: string) {
    setCompareIds((prev) => {
      if (prev.includes(listingId)) return prev.filter((id) => id !== listingId);
      if (prev.length >= 3) return [...prev.slice(1), listingId];
      return [...prev, listingId];
    });
  }

  const comparedHits = useMemo(
    () => hits.filter((h) => compareIds.includes(h.listing.id)),
    [hits, compareIds]
  );

  async function handleGenerateCopy() {
    const hlList = pubState.pubHighlightsRaw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    try {
      const resp = await montyApi.assistListingCopywriting({
        country_code: countryCode,
        property_kind: pubState.pubKind,
        city: pubState.pubCity,
        sector_label: pubState.pubSector,
        surface_m2: pubState.pubSurface,
        rooms: pubState.pubRooms,
        price_euro: pubState.pubPrice,
        dpe: pubState.pubDpe,
        condition_label: pubState.pubCondition,
        highlights: hlList,
        photo_count: pubState.pubPhotos,
        estimated_sale_days: previewTimeline.status === "READY" ? previewTimeline.likely_days : undefined,
      });
      patchPubState({ pubTitle: resp.suggested_title, pubDescription: resp.suggested_description });
      setCopyTip(resp.photo_tip);
    } catch {
      const localResp = buildLocalListingCopy({
        country_code: countryCode,
        property_kind: pubState.pubKind,
        city: pubState.pubCity,
        sector_label: pubState.pubSector,
        surface_m2: pubState.pubSurface,
        rooms: pubState.pubRooms,
        price_euro: pubState.pubPrice,
        dpe: pubState.pubDpe,
        condition_label: pubState.pubCondition,
        highlights: hlList,
        photo_count: pubState.pubPhotos,
        estimated_sale_days: previewTimeline.status === "READY" ? previewTimeline.likely_days : undefined,
      });
      patchPubState({ pubTitle: localResp.suggested_title, pubDescription: localResp.suggested_description });
      setCopyTip(localResp.photo_tip);
    }
  }

  async function handlePublishListing(e: React.FormEvent) {
    e.preventDefault();
    setPublishBusy(true);
    setPublishNotice(null);
    setShowPostPublishCta(false);
    setShowValuationForm(false);
    setValuationError(null);
    setValuationRequested(false);
    const hlList = pubState.pubHighlightsRaw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    const finalTitle =
      pubState.pubTitle.trim() ||
      `${pubState.pubKind} ${pubState.pubRooms} pièces • ${pubState.pubSurface} m² — ${pubState.pubCity}`;
    const finalDesc =
      pubState.pubDescription.trim() ||
      `Bien situé à ${pubState.pubCity} (${pubState.pubSector}), ${pubState.pubSurface} m², ${pubState.pubRooms} pièces, ${pubState.pubCondition}.`;

    try {
      const created = await montyApi.publishMarketplaceListing({
        country_code: countryCode,
        publisher_type: pubState.pubType,
        publisher_label: pubState.pubLabel,
        title: finalTitle,
        property_kind: pubState.pubKind,
        city: pubState.pubCity,
        sector_label: pubState.pubSector,
        price_euro: pubState.pubPrice,
        surface_m2: pubState.pubSurface,
        rooms: pubState.pubRooms,
        dpe: pubState.pubDpe,
        condition_label: pubState.pubCondition,
        highlights: hlList,
        description: finalDesc,
        photo_count: pubState.pubPhotos,
      });
      setShowPostPublishCta(true);
      if (created.moderation_status === "A_VERIFIER") {
        setPublishNotice(
          `Annonce « ${created.title} » enregistrée en attente de modération (A_VERIFIER)${
            pubState.wantProAccompaniment ? " avec demande d'accompagnement conseiller Monty Pro" : ""
          }.`
        );
      } else {
        setLocalCatalog((prev) => [created, ...prev]);
        setOriginAddress(created.city);
        setPublishNotice(
          `Annonce « ${created.title} » publiée avec succès (délai estimé : ~${created.sale_timeline.likely_days} jours).`
        );
      }
    } catch {
      const geo = resolveCityGeo(pubState.pubCity, countryCode);
      const needsAuthHint = !hasInMemoryAccessToken();
      const localCreated: MarketplaceListing = {
        id: `usr-pub-${countryCode.toLowerCase()}-${Date.now()}`,
        country_code: countryCode,
        publisher_type: pubState.pubType,
        publisher_label:
          pubState.pubLabel.trim() ||
          (pubState.pubType === "PARTICULIER" ? "Particulier vérifié" : "Agence partenaire"),
        title: redactPiiText(finalTitle),
        property_kind: pubState.pubKind,
        city: pubState.pubCity.trim() || "Lyon",
        region_name: geo.region,
        sector_label: pubState.pubSector.trim() || pubState.pubCity,
        price_euro: pubState.pubPrice,
        surface_m2: pubState.pubSurface,
        rooms: pubState.pubRooms,
        dpe: pubState.pubDpe,
        condition_label: pubState.pubCondition,
        highlights: hlList,
        description: redactPiiText(finalDesc),
        photo_count: pubState.pubPhotos,
        latitude: geo.lat,
        longitude: geo.lon,
        moderation_status: pubState.pubType === "PARTICULIER" ? "A_VERIFIER" : "PUBLIEE",
        sale_timeline: previewTimeline,
        published_at: new Date().toISOString().slice(0, 10),
      };
      setLocalCatalog((prev) => [localCreated, ...prev]);
      setOriginAddress(localCreated.city);
      setShowPostPublishCta(true);
      setPublishNotice(
        needsAuthHint
          ? `Annonce « ${localCreated.title} » ajoutée à l'aperçu interactif${
              pubState.wantProAccompaniment ? " (demande d'accompagnement Monty Pro notée)" : ""
            }.`
          : `Annonce « ${localCreated.title} » enregistrée (masquage RGPD des PII appliqué).`
      );
    } finally {
      setPublishBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Bandeau supérieur Multi-Pays + Switch Recherche / Publication */}
      <section className="rounded-2xl bg-gradient-to-r from-emeraldDeep via-emeraldMid to-emeraldDeep text-ivoryWarm p-6 border border-goldAntique/40">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="space-y-1">
            <p className="text-xs font-bold uppercase tracking-widest text-goldChampagne">
              Monty Place de Marché • {audienceLabel}
            </p>
            <h2 className="font-display text-2xl sm:text-3xl font-bold">
              {activeTab === "search"
                ? "Recherche par temps de trajet & transparence DVF"
                : activeTab === "feeds"
                ? "Connecteur de flux partenaires autorisés (XML / JSON / JSON-LD)"
                : "Publier un bien avec aide à la rédaction & délai estimé"}
            </h2>
            <p className="text-xs sm:text-sm text-ivoryWarm/80">
              Au-delà des portails classiques : 6 modes de mobilité réels, comparateur transparent, DPE/SCE et délai
              probable de vente.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex rounded-xl bg-emeraldSurface p-1 border border-goldAntique/30">
              {(["FR", "PT"] as CountryCode[]).map((code) => (
                <button
                  key={code}
                  type="button"
                  onClick={() => setCountryCode(code)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition ${
                    countryCode === code
                      ? "bg-goldAntique text-emeraldDeep"
                      : "text-ivoryWarm/80 hover:text-ivoryWarm"
                  }`}
                >
                  {code === "FR" ? "🇫🇷 France entière" : "🇵🇹 Portugal (Isolé)"}
                </button>
              ))}
            </div>

            {showTabs && (
              <div className="inline-flex rounded-xl bg-emeraldSurface p-1 border border-goldAntique/30">
                <button
                  type="button"
                  onClick={() => setActiveTab("search")}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold inline-flex items-center gap-1.5 transition ${
                    activeTab === "search"
                      ? "bg-ivoryWarm text-emeraldDeep"
                      : "text-ivoryWarm/80 hover:text-ivoryWarm"
                  }`}
                >
                  <Search className="w-3.5 h-3.5" />
                  Annonces &amp; Trajet
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab("publish")}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold inline-flex items-center gap-1.5 transition ${
                    activeTab === "publish"
                      ? "bg-ivoryWarm text-emeraldDeep"
                      : "text-ivoryWarm/80 hover:text-ivoryWarm"
                  }`}
                >
                  <PlusCircle className="w-3.5 h-3.5" />
                  Publier un bien
                </button>
                {isProView && (
                  <button
                    type="button"
                    onClick={() => setActiveTab("feeds")}
                    className={`px-3 py-1.5 rounded-lg text-xs font-bold inline-flex items-center gap-1.5 transition ${
                      activeTab === "feeds"
                        ? "bg-ivoryWarm text-emeraldDeep"
                        : "text-ivoryWarm/80 hover:text-ivoryWarm"
                    }`}
                  >
                    <Globe2 className="w-3.5 h-3.5" />
                    Flux Partenaires
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      </section>

      {publishNotice && (
        <div className="rounded-2xl bg-emerald-50 border border-emerald-300 p-4 space-y-3 text-sm text-emerald-950">
          <div className="flex items-center gap-2 font-semibold">
            <CheckCircle2 className="w-5 h-5 text-emerald-700 shrink-0" />
            <span>{publishNotice}</span>
          </div>
          {showPostPublishCta && (
            <div className="rounded-xl bg-white border border-emerald-200 p-4 space-y-3 text-xs">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="space-y-1">
                  <p className="font-bold text-emeraldDeep text-sm flex items-center gap-1.5">
                    <Sparkles className="w-4 h-4 text-goldAntique" />
                    Votre bien est publié. Souhaitez-vous obtenir une analyse plus précise de sa valeur et de son potentiel
                    de vente ?
                  </p>
                  <p className="text-slateMuted">
                    Affinez votre prix avec les références notariées DVF ou sollicitez un avis de valeur confidentiel.
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2 shrink-0">
                  <Link
                    href="/simulateurs"
                    className="px-3.5 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid"
                  >
                    Estimer ma valeur DVF
                  </Link>
                  <button
                    type="button"
                    onClick={() => setShowValuationForm((v) => !v)}
                    className="px-3.5 py-2 rounded-xl border border-emeraldDeep text-emeraldDeep font-bold hover:bg-ivoryWarm"
                  >
                    {valuationRequested ? "Demande enregistrée ✓" : "Demander un avis conseiller"}
                  </button>
                </div>
              </div>
              {showValuationForm && !valuationRequested && (
                <div className="pt-2 border-t border-emerald-100 flex flex-col sm:flex-row sm:items-center gap-2.5">
                  <input
                    type="email"
                    value={valuationEmail}
                    onChange={(e) => setValuationEmail(e.target.value)}
                    placeholder="Votre e-mail pour recevoir l'avis confidentiel"
                    className="flex-1 rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-xs text-obsidianInk"
                  />
                  <label className="inline-flex items-center gap-1.5 text-slateMuted cursor-pointer">
                    <input
                      type="checkbox"
                      checked={valuationRgpd}
                      onChange={(e) => setValuationRgpd(e.target.checked)}
                    />
                    <span>Accord RGPD conseiller Monty</span>
                  </label>
                  <button
                    type="button"
                    disabled={valuationBusy}
                    onClick={async () => {
                      setValuationError(null);
                      const clean = valuationEmail.trim();
                      if (!clean.includes("@") || !clean.includes(".")) {
                        setValuationError("Saisissez un e-mail valide.");
                        return;
                      }
                      if (!valuationRgpd) {
                        setValuationError("Cochez l'accord RGPD.");
                        return;
                      }
                      setValuationBusy(true);
                      try {
                        await montyApi.submitPublicLead({
                          lead_kind: "SELLER_VALUATION",
                          country_code: countryCode,
                          email: clean,
                          city: pubState.pubCity,
                          budget_euro: pubState.pubPrice,
                          property_title:
                            pubState.pubTitle.trim() ||
                            `${pubState.pubKind} ${pubState.pubSurface} m² — ${pubState.pubCity}`,
                          notes: `Avis conseiller demandé après publication (${pubState.pubSector}, DPE ${pubState.pubDpe})`,
                          rgpd_consent_granted: true,
                        });
                      } catch {
                        // Fallback gracieux si API endormie
                      } finally {
                        setValuationBusy(false);
                        setValuationRequested(true);
                        setShowValuationForm(false);
                      }
                    }}
                    className="px-4 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-60"
                  >
                    {valuationBusy ? "Envoi..." : "Envoyer au conseiller"}
                  </button>
                  {valuationError && <span className="text-rose-700 font-semibold">{valuationError}</span>}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {isProView && pendingModeration.length > 0 && (
        <section className="rounded-2xl bg-amber-50 border border-amber-300 p-4 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="font-display text-sm font-bold text-amber-950">
              File de modération annonces ({pendingModeration.length} à vérifier)
            </h3>
            <span className="text-[11px] font-semibold text-amber-800">Contrôle RBAC PROPERTY_MODERATE</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {pendingModeration.map((item) => (
              <div
                key={item.id}
                className="rounded-xl bg-white border border-amber-200 p-3 flex items-center justify-between gap-3 text-xs"
              >
                <div>
                  <p className="font-bold text-obsidianInk">{item.title}</p>
                  <p className="text-slateMuted">
                    {item.city} • {item.price_euro.toLocaleString("fr-FR")} € • {item.surface_m2} m² (
                    {item.publisher_type})
                  </p>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    type="button"
                    onClick={() => handleModerationDecision(item.id, true)}
                    className="px-2.5 py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne font-bold"
                  >
                    Publier
                  </button>
                  <button
                    type="button"
                    onClick={() => handleModerationDecision(item.id, false)}
                    className="px-2.5 py-1.5 rounded-lg bg-rose-100 text-rose-900 font-bold"
                  >
                    Rejeter
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {activeTab === "feeds" ? (
        <PartnerFeedsPanel onSynced={() => runSearch()} />
      ) : activeTab === "search" ? (
        <div className="space-y-6">
          {/* Barre de critères mobilité 6 modes */}
          <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3 text-sm">
              <label className="md:col-span-4 block">
                <span className="text-xs font-bold text-slateMuted">
                  Adresse / Ville de référence (travail, gare, école...)
                </span>
                <div className="mt-1 flex gap-1.5">
                  <div className="relative flex-1">
                    <MapPin className="w-4 h-4 text-emeraldDeep absolute left-3 top-2.5" />
                    <input
                      type="text"
                      value={originAddress}
                      onChange={(e) => {
                        setOriginAddress(e.target.value);
                        setOriginCoords(null);
                      }}
                      placeholder={countryCode === "PT" ? "Lisboa, Porto, Faro..." : "Paris, Lyon, Bordeaux, Nantes..."}
                      className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm pl-9 pr-3 py-2 text-sm focus:outline-none focus:border-emeraldDeep"
                    />
                  </div>
                  <button
                    type="button"
                    onClick={handleGeolocate}
                    title="Me localiser (optionnel)"
                    className="px-3 py-2 rounded-xl border border-borderSubtle bg-ivoryWarm text-emeraldDeep hover:border-emeraldDeep text-xs font-semibold inline-flex items-center gap-1 shrink-0"
                  >
                    <LocateFixed className="w-3.5 h-3.5" />
                    <span className="hidden sm:inline">Me localiser</span>
                  </button>
                </div>
              </label>

              <label className="md:col-span-2 block">
                <span className="text-xs font-bold text-slateMuted">Périmètre</span>
                <select
                  value={searchMode}
                  onChange={(e) => setSearchMode(e.target.value as SearchPerimeterMode)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-sm"
                >
                  <option value="TRAVEL_TIME">Temps de trajet</option>
                  <option value="RADIUS_KM">Rayon (km)</option>
                </select>
              </label>

              {searchMode === "TRAVEL_TIME" ? (
                <label className="md:col-span-2 block">
                  <span className="text-xs font-bold text-slateMuted">Max trajet (min)</span>
                  <input
                    type="number"
                    min={5}
                    max={240}
                    value={maxMinutes}
                    onChange={(e) => setMaxMinutes(Number(e.target.value) || 30)}
                    className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-sm"
                  />
                </label>
              ) : (
                <label className="md:col-span-2 block">
                  <span className="text-xs font-bold text-slateMuted">Rayon max (km)</span>
                  <input
                    type="number"
                    min={1}
                    max={500}
                    value={maxRadiusKm}
                    onChange={(e) => setMaxRadiusKm(Number(e.target.value) || 15)}
                    className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-sm"
                  />
                </label>
              )}

              <label className="md:col-span-2 block">
                <span className="text-xs font-bold text-slateMuted">Budget max (€)</span>
                <input
                  type="number"
                  step={10000}
                  value={maxPrice}
                  onChange={(e) => setMaxPrice(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-sm"
                />
              </label>

              <label className="md:col-span-2 block">
                <span className="text-xs font-bold text-slateMuted">Vendeur</span>
                <select
                  value={publisherFilter}
                  onChange={(e) => setPublisherFilter(e.target.value as "ALL" | PublisherType)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-sm"
                >
                  <option value="ALL">Tous (Particuliers + Agences)</option>
                  <option value="PARTICULIER">Particuliers vérifiés</option>
                  <option value="AGENCE">Agences partenaires</option>
                </select>
              </label>
            </div>

            {/* Raccourcis villes + notice géolocalisation */}
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="text-slateMuted font-medium mr-1">Villes rapides :</span>
                {QUICK_CITIES[countryCode].map((city) => (
                  <button
                    key={city}
                    type="button"
                    onClick={() => {
                      setOriginAddress(city);
                      setOriginCoords(null);
                    }}
                    className={`px-2.5 py-1 rounded-lg border font-semibold transition ${
                      originAddress.toLowerCase() === city.toLowerCase()
                        ? "bg-emeraldDeep/10 text-emeraldDeep border-emeraldDeep"
                        : "bg-ivoryWarm text-slateMuted border-borderSubtle hover:text-obsidianInk"
                    }`}
                  >
                    {city}
                  </button>
                ))}
              </div>
              {geoNotice && <span className="text-emeraldDeep font-medium">{geoNotice}</span>}
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
              <div className="flex flex-wrap items-center gap-1.5">
                {TRANSPORT_OPTIONS.map(({ mode, label, Icon }) => {
                  const selected = transportMode === mode;
                  return (
                    <button
                      key={mode}
                      type="button"
                      onClick={() => setTransportMode(mode)}
                      className={`px-3 py-1.5 rounded-xl text-xs font-semibold border inline-flex items-center gap-1.5 transition ${
                        selected
                          ? "bg-emeraldDeep text-goldChampagne border-emeraldDeep"
                          : "bg-ivoryWarm text-obsidianInk border-borderSubtle hover:border-emeraldMid"
                      }`}
                    >
                      <Icon className="w-3.5 h-3.5" />
                      {label}
                    </button>
                  );
                })}
              </div>

              <div className="flex flex-wrap items-center gap-2.5 text-xs">
                <label className="inline-flex items-center gap-1.5 font-medium text-obsidianInk cursor-pointer">
                  <input
                    type="checkbox"
                    checked={onlyWithinPerimeter}
                    onChange={(e) => setOnlyWithinPerimeter(e.target.checked)}
                    className="rounded border-borderSubtle"
                  />
                  Uniquement dans le périmètre strict
                </label>
                <button
                  type="button"
                  onClick={() => setShowAlertDrawer((prev) => !prev)}
                  className="px-3 py-2 rounded-xl border border-goldAntique text-emeraldDeep bg-ivoryWarm font-bold hover:bg-goldChampagne/20 transition inline-flex items-center gap-1.5"
                >
                  <Bell className="w-3.5 h-3.5 text-goldAntique" />
                  Créer une alerte
                </button>
                <button
                  type="button"
                  onClick={runSearch}
                  disabled={loadingSearch}
                  className="px-4 py-2 rounded-xl bg-emeraldDeep text-ivoryWarm font-bold hover:bg-emeraldMid transition"
                >
                  {loadingSearch ? "Calcul..." : "Actualiser"}
                </button>
              </div>
            </div>
          </section>

          {isDemoCatalog && (
            <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-2.5 text-xs text-amber-950 flex flex-wrap items-center justify-between gap-2">
              <span>
                <strong>Annonces de démonstration affichées :</strong> ces biens illustrent la recherche par temps de
                trajet pendant l&apos;ouverture des flux partenaires locaux.
              </span>
              <button
                type="button"
                onClick={() => setActiveTab("publish")}
                className="font-bold underline text-emeraldDeep"
              >
                Publier un bien réel →
              </button>
            </div>
          )}

          {/* Barre récapitulative + Comparateur côte à côte */}
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slateMuted">
            <span>
              <strong className="text-emeraldDeep">{hits.length}</strong> annonce(s) classée(s) par temps de trajet depuis{" "}
              <strong className="text-obsidianInk">{originAddress}</strong> ({countryCode}) • Source :{" "}
              {dataSourceMode === "api" ? "API FastAPI live" : "Catalogue interactif"}
            </span>
            <span>Astuce : cochez jusqu&apos;à 3 biens pour ouvrir le comparateur transparent DVF / Trajet / Notaire.</span>
          </div>

          {comparedHits.length >= 2 && (
            <section className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-5 border border-goldAntique/40 space-y-3 overflow-x-auto">
              <div className="flex items-center justify-between">
                <h3 className="font-display text-lg font-bold text-goldChampagne flex items-center gap-2">
                  <GitCompare className="w-5 h-5" />
                  Comparateur Transparent Monty ({comparedHits.length}/3 biens)
                </h3>
                <button
                  type="button"
                  onClick={() => setCompareIds([])}
                  className="text-xs underline text-ivoryWarm/80 hover:text-goldChampagne"
                >
                  Effacer la comparaison
                </button>
              </div>
              <table className="w-full text-xs text-left border-collapse">
                <thead>
                  <tr className="border-b border-goldAntique/30 text-goldChampagne">
                    <th className="py-2 pr-3">Critère</th>
                    {comparedHits.map((h) => (
                      <th key={h.listing.id} className="py-2 px-3">
                        {h.listing.city} • {h.listing.title}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-ivoryWarm/10">
                  <tr>
                    <td className="py-2 pr-3 text-ivoryWarm/75">Prix affiché &amp; Prix/m²</td>
                    {comparedHits.map((h) => (
                      <td key={h.listing.id} className="py-2 px-3 font-bold">
                        {h.listing.price_euro.toLocaleString("fr-FR")} € (
                        {Math.round(h.listing.price_euro / Math.max(1, h.listing.surface_m2)).toLocaleString("fr-FR")}{" "}
                        €/m²)
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 pr-3 text-ivoryWarm/75">Mobilité depuis {originAddress}</td>
                    {comparedHits.map((h) => (
                      <td key={h.listing.id} className="py-2 px-3 text-goldChampagne font-semibold">
                        {h.transport_badge}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 pr-3 text-ivoryWarm/75">Performance &amp; État</td>
                    {comparedHits.map((h) => (
                      <td key={h.listing.id} className="py-2 px-3">
                        {countryCode === "PT" ? "CE" : "DPE"} {h.listing.dpe} • {h.listing.condition_label}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 pr-3 text-ivoryWarm/75">Délai de vente probable</td>
                    {comparedHits.map((h) => (
                      <td key={h.listing.id} className="py-2 px-3">
                        ~{h.listing.sale_timeline.likely_days} j ({h.listing.sale_timeline.low_days}–
                        {h.listing.sale_timeline.high_days} j)
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </section>
          )}

          {/* Grille d'annonces */}
          {hits.length === 0 ? (
            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-8 text-center space-y-4">
              <p className="font-display font-bold text-lg text-emeraldDeep">
                Aucun bien exact dans ce périmètre strict
              </p>
              <p className="text-sm text-slateMuted max-w-xl mx-auto">
                Élargissez le temps de trajet pour inclure les communes voisines bien desservies, ou activez une alerte
                immédiate.
              </p>
              <div className="flex flex-wrap items-center justify-center gap-3">
                <button
                  type="button"
                  onClick={handleBroadenSearch}
                  className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid"
                >
                  Élargir la recherche (+30 min / toutes villes)
                </button>
                <button
                  type="button"
                  onClick={() => setShowAlertDrawer(true)}
                  className="px-4 py-2.5 rounded-xl border border-emeraldDeep text-emeraldDeep text-xs font-bold hover:bg-ivoryWarm"
                >
                  Créer une alerte e-mail
                </button>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {hits.map((hit) => {
                const { listing } = hit;
                const pricePerM2 = Math.round(listing.price_euro / Math.max(1, listing.surface_m2));
                const isCompared = compareIds.includes(listing.id);
                const isSampleListing =
                  listing.id.startsWith("lst-fr-") || listing.id.startsWith("lst-pt-");
                return (
                  <article
                    key={listing.id}
                    className={`rounded-2xl bg-ivoryCard border p-5 flex flex-col justify-between gap-4 transition ${
                      isCompared
                        ? "border-emeraldDeep ring-2 ring-emeraldDeep/20"
                        : "border-borderSubtle hover:border-emeraldMid"
                    }`}
                  >
                    <div className="space-y-3">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span
                            className={`text-[11px] font-bold px-2.5 py-0.5 rounded-full inline-flex items-center gap-1 ${
                              listing.publisher_type === "PARTICULIER"
                                ? "bg-amber-100 text-amber-900"
                                : "bg-emeraldDeep/10 text-emeraldDeep"
                            }`}
                          >
                            {listing.publisher_type === "PARTICULIER" ? (
                              <UserCheck className="w-3 h-3" />
                            ) : (
                              <Building2 className="w-3 h-3" />
                            )}
                            {listing.publisher_label}
                          </span>
                          {isSampleListing && (
                            <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">
                              Démo
                            </span>
                          )}
                        </div>
                        <span
                          className={`text-[11px] font-bold px-2.5 py-0.5 rounded-full ${
                            hit.within_perimeter
                              ? "bg-emerald-100 text-emerald-900"
                              : "bg-slate-100 text-slate-700"
                          }`}
                        >
                          {hit.transport_badge}
                        </span>
                      </div>

                      <div>
                        <p className="text-xs font-semibold text-slateMuted">
                          {listing.city} • {listing.sector_label} ({listing.region_name})
                        </p>
                        <h3 className="font-display text-lg font-bold text-obsidianInk mt-0.5">{listing.title}</h3>
                      </div>

                      <div className="flex items-baseline justify-between rounded-xl bg-ivoryWarm border border-borderSubtle px-3.5 py-2.5">
                        <div>
                          <p className="text-xl font-display font-bold text-emeraldDeep">
                            {listing.price_euro.toLocaleString("fr-FR")} €
                          </p>
                          <p className="text-[11px] text-slateMuted">
                            {pricePerM2.toLocaleString("fr-FR")} €/m² • {listing.surface_m2} m² • {listing.rooms} p.
                          </p>
                        </div>
                        <span className="px-2 py-1 rounded-lg bg-emeraldDeep text-goldChampagne text-xs font-bold">
                          {countryCode === "PT" ? "CE" : "DPE"} {listing.dpe}
                        </span>
                      </div>

                      <p className="text-xs text-slateMuted leading-relaxed line-clamp-3">{listing.description}</p>

                      <div className="flex flex-wrap gap-1.5">
                        {listing.highlights.slice(0, 3).map((hl) => (
                          <span
                            key={hl}
                            className="text-[11px] px-2 py-0.5 rounded-md bg-ivoryWarm border border-borderSubtle text-obsidianInk"
                          >
                            {hl}
                          </span>
                        ))}
                      </div>
                    </div>

                    <div className="pt-2 border-t border-borderSubtle flex flex-wrap items-center justify-between gap-2 text-xs">
                      <span className="inline-flex items-center gap-1 text-emeraldDeep font-semibold">
                        <Clock className="w-3.5 h-3.5 text-goldAntique" />
                        ~{listing.sale_timeline.likely_days} j
                      </span>
                      <div className="flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => toggleFavorite(listing)}
                          title={favoriteIds.includes(listing.id) ? "Retirer des favoris" : "Ajouter aux favoris"}
                          className={`px-2.5 py-1.5 rounded-lg font-bold border transition inline-flex items-center gap-1 ${
                            favoriteIds.includes(listing.id)
                              ? "bg-rose-50 text-rose-700 border-rose-300"
                              : "bg-ivoryWarm text-slateMuted border-borderSubtle hover:text-rose-700"
                          }`}
                        >
                          <Heart className={`w-3.5 h-3.5 ${favoriteIds.includes(listing.id) ? "fill-rose-600 text-rose-600" : ""}`} />
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setVisitTarget(listing);
                            setVisitError(null);
                            setVisitFeedback(null);
                          }}
                          className="px-2.5 py-1.5 rounded-lg font-bold border border-goldAntique bg-goldChampagne/20 text-emeraldDeep hover:bg-goldChampagne/40 transition inline-flex items-center gap-1"
                        >
                          <CalendarPlus className="w-3.5 h-3.5" />
                          Visiter
                        </button>
                        <button
                          type="button"
                          onClick={() => toggleCompare(listing.id)}
                          className={`px-2.5 py-1.5 rounded-lg font-bold border transition inline-flex items-center gap-1 ${
                            isCompared
                              ? "bg-emeraldDeep text-goldChampagne border-emeraldDeep"
                              : "bg-ivoryWarm text-emeraldDeep border-borderSubtle hover:border-emeraldDeep"
                          }`}
                        >
                          <GitCompare className="w-3.5 h-3.5" />
                          {isCompared ? "Comparé ✓" : "Comparer"}
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}

          {visitFeedback && (
            <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-4 py-3 text-xs text-emerald-950 flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0" />
              <span>{visitFeedback}</span>
            </div>
          )}

          {visitTarget && (
            <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-5 border border-goldAntique/50 space-y-3 text-xs">
              <div className="flex items-center justify-between gap-2">
                <div>
                  <p className="text-[11px] uppercase tracking-widest text-goldChampagne font-bold">
                    Prise de Rendez-vous Directe (CRM Agence)
                  </p>
                  <h4 className="font-display text-lg font-bold">
                    Demander une visite : {visitTarget.title} ({visitTarget.city})
                  </h4>
                </div>
                <button
                  type="button"
                  onClick={() => setVisitTarget(null)}
                  className="p-1.5 rounded-lg bg-emeraldSurface hover:bg-emeraldMid"
                  aria-label="Fermer le formulaire de visite"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              {visitError && (
                <div className="rounded-lg bg-rose-900/60 border border-rose-300 px-3 py-2 text-rose-100">
                  {visitError}
                </div>
              )}
              <form onSubmit={handleSubmitVisitRequest} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
                <input
                  type="text"
                  value={visitName}
                  onChange={(e) => setVisitName(e.target.value)}
                  placeholder="Votre nom complet"
                  className="rounded-xl bg-ivoryWarm text-obsidianInk px-3 py-2"
                />
                <input
                  type="email"
                  value={visitEmail}
                  onChange={(e) => setVisitEmail(e.target.value)}
                  placeholder="Votre e-mail *"
                  className="rounded-xl bg-ivoryWarm text-obsidianInk px-3 py-2"
                />
                <input
                  type="text"
                  value={visitPhone}
                  onChange={(e) => setVisitPhone(e.target.value)}
                  placeholder="Téléphone"
                  className="rounded-xl bg-ivoryWarm text-obsidianInk px-3 py-2"
                />
                <input
                  type="text"
                  value={visitSlot}
                  onChange={(e) => setVisitSlot(e.target.value)}
                  placeholder="Créneau souhaité"
                  className="rounded-xl bg-ivoryWarm text-obsidianInk px-3 py-2"
                />
                <label className="sm:col-span-3 inline-flex items-center gap-2 text-ivoryWarm/90 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={visitRgpd}
                    onChange={(e) => setVisitRgpd(e.target.checked)}
                  />
                  <span>
                    J&apos;accepte que mes coordonnées soient transmises à l&apos;agence en charge de ce bien pour organiser la visite (Art. 6.1.a RGPD).
                  </span>
                </label>
                <button
                  type="submit"
                  disabled={visitBusy}
                  className="px-4 py-2 rounded-xl bg-goldAntique text-emeraldDeep font-bold hover:bg-goldChampagne disabled:opacity-50"
                >
                  {visitBusy ? "Envoi..." : "Confirmer la demande"}
                </button>
              </form>
            </div>
          )}

          {/* Alertes sauvegardées + module "Je ne trouve pas mon bien, faites ma recherche" */}
          <BuyerSearchTools
            countryCode={countryCode}
            originAddress={originAddress}
            transportMode={transportMode}
            maxMinutes={maxMinutes}
            maxPrice={maxPrice}
            showAlertDrawer={showAlertDrawer}
            onCloseAlertDrawer={() => setShowAlertDrawer(false)}
          />
        </div>
      ) : (
        <MarketplacePublishForm
          countryCode={countryCode}
          state={pubState}
          onChange={patchPubState}
          previewTimeline={previewTimeline}
          copyTip={copyTip}
          publishBusy={publishBusy}
          onGenerateCopy={handleGenerateCopy}
          onSubmit={handlePublishListing}
        />
      )}
    </div>
  );
}
