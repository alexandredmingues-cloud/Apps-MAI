"use client";

import React, { useEffect, useMemo, useState } from "react";
import {
  Bike,
  Building2,
  Bus,
  Calculator,
  Car,
  CheckCircle2,
  Clock,
  Footprints,
  GitCompare,
  Globe2,
  Home,
  MapPin,
  PlusCircle,
  Search,
  ShieldCheck,
  Sparkles,
  TrainFront,
  TramFront,
  UserCheck,
} from "lucide-react";
import { hasInMemoryAccessToken, montyApi } from "@/lib/api-client";
import { PartnerFeedsPanel } from "./PartnerFeedsPanel";
import {
  buildLocalListingCopy,
  computeLocalSaleTimeline,
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
  const [searchMode, setSearchMode] = useState<SearchPerimeterMode>("TRAVEL_TIME");
  const [transportMode, setTransportMode] = useState<TransportMode>("TRAM");
  const [maxMinutes, setMaxMinutes] = useState(40);
  const [maxRadiusKm, setMaxRadiusKm] = useState(25);
  const [publisherFilter, setPublisherFilter] = useState<"ALL" | PublisherType>("ALL");
  const [maxPrice, setMaxPrice] = useState<number>(900000);
  const [minSurface, setMinSurface] = useState<number>(0);
  const [onlyWithinPerimeter, setOnlyWithinPerimeter] = useState<boolean>(false);

  // Catalogue local fusionné + état API
  const [localCatalog, setLocalCatalog] = useState<MarketplaceListing[]>(INITIAL_LOCAL_LISTINGS);
  const [hits, setHits] = useState<MarketplaceSearchHit[]>([]);
  const [dataSourceMode, setDataSourceMode] = useState<"api" | "local">("local");
  const [loadingSearch, setLoadingSearch] = useState(false);

  // Comparateur côte à côte (max 3 biens) — avantage concurrentiel majeur vs Leboncoin/SeLoger
  const [compareIds, setCompareIds] = useState<string[]>([]);

  // Formulaire publication vendeur
  const [pubType, setPubType] = useState<PublisherType>("PARTICULIER");
  const [pubLabel, setPubLabel] = useState("");
  const [pubKind, setPubKind] = useState("Appartement");
  const [pubCity, setPubCity] = useState(initialCountry === "PT" ? "Lisboa" : "Nantes");
  const [pubSector, setPubSector] = useState("Centre / Tram");
  const [pubPrice, setPubPrice] = useState(325000);
  const [pubSurface, setPubSurface] = useState(72);
  const [pubRooms, setPubRooms] = useState(3);
  const [pubDpe, setPubDpe] = useState("B");
  const [pubCondition, setPubCondition] = useState("Bon état");
  const [pubHighlightsRaw, setPubHighlightsRaw] = useState("Balcon plein sud, Tram 4 min, Faibles charges");
  const [pubPhotos, setPubPhotos] = useState(6);
  const [pubTitle, setPubTitle] = useState("");
  const [pubDescription, setPubDescription] = useState("");
  const [copyTip, setCopyTip] = useState<string | null>(null);
  const [publishNotice, setPublishNotice] = useState<string | null>(null);
  const [publishBusy, setPublishBusy] = useState(false);
  const [pendingModeration, setPendingModeration] = useState<MarketplaceListing[]>([]);

  const isProView = audienceLabel.includes("Monty Pro");

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
    setPubCity(countryCode === "PT" ? "Lisboa" : "Nantes");
    setCompareIds([]);
  }, [countryCode]);

  async function runSearch() {
    setLoadingSearch(true);
    try {
      const resp = await montyApi.searchMarketplaceListings({
        country_code: countryCode,
        publisher_type: publisherFilter === "ALL" ? undefined : publisherFilter,
        origin_address: originAddress,
        search_mode: searchMode,
        transport_mode: transportMode,
        max_radius_km: maxRadiusKm,
        max_travel_minutes: maxMinutes,
        max_price_euro: maxPrice > 0 ? maxPrice : undefined,
        min_surface_m2: minSurface,
        only_within_perimeter: onlyWithinPerimeter,
      });
      setHits(resp.hits);
      setDataSourceMode("api");
    } catch {
      const localHits = searchLocalListings(localCatalog, {
        country_code: countryCode,
        publisher_type: publisherFilter === "ALL" ? undefined : publisherFilter,
        origin_address: originAddress,
        search_mode: searchMode,
        transport_mode: transportMode,
        max_radius_km: maxRadiusKm,
        max_travel_minutes: maxMinutes,
        max_price_euro: maxPrice > 0 ? maxPrice : undefined,
        min_surface_m2: minSurface,
        only_within_perimeter: onlyWithinPerimeter,
      });
      setHits(localHits);
      setDataSourceMode("local");
    } finally {
      setLoadingSearch(false);
    }
  }

  useEffect(() => {
    runSearch();
    loadPendingModeration();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [countryCode, publisherFilter, searchMode, transportMode, maxMinutes, maxRadiusKm, onlyWithinPerimeter, localCatalog]);

  const previewTimeline = useMemo(() => {
    const geo = resolveCityGeo(pubCity, countryCode);
    return computeLocalSaleTimeline({
      priceEuro: pubPrice,
      surfaceM2: pubSurface,
      dpe: pubDpe,
      conditionLabel: pubCondition,
      refM2: geo.refM2,
      photoCount: pubPhotos,
      publisherType: pubType,
    });
  }, [pubCity, countryCode, pubPrice, pubSurface, pubDpe, pubCondition, pubPhotos, pubType]);

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
    const hlList = pubHighlightsRaw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    try {
      const resp = await montyApi.assistListingCopywriting({
        country_code: countryCode,
        property_kind: pubKind,
        city: pubCity,
        sector_label: pubSector,
        surface_m2: pubSurface,
        rooms: pubRooms,
        price_euro: pubPrice,
        dpe: pubDpe,
        condition_label: pubCondition,
        highlights: hlList,
        photo_count: pubPhotos,
        estimated_sale_days: previewTimeline.status === "READY" ? previewTimeline.likely_days : undefined,
      });
      setPubTitle(resp.suggested_title);
      setPubDescription(resp.suggested_description);
      setCopyTip(resp.photo_tip);
    } catch {
      const localResp = buildLocalListingCopy({
        country_code: countryCode,
        property_kind: pubKind,
        city: pubCity,
        sector_label: pubSector,
        surface_m2: pubSurface,
        rooms: pubRooms,
        price_euro: pubPrice,
        dpe: pubDpe,
        condition_label: pubCondition,
        highlights: hlList,
        photo_count: pubPhotos,
        estimated_sale_days: previewTimeline.status === "READY" ? previewTimeline.likely_days : undefined,
      });
      setPubTitle(localResp.suggested_title);
      setPubDescription(localResp.suggested_description);
      setCopyTip(localResp.photo_tip);
    }
  }

  async function handlePublishListing(e: React.FormEvent) {
    e.preventDefault();
    setPublishBusy(true);
    setPublishNotice(null);
    const hlList = pubHighlightsRaw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    const finalTitle =
      pubTitle.trim() || `${pubKind} ${pubRooms} pièces • ${pubSurface} m² — ${pubCity}`;
    const finalDesc =
      pubDescription.trim() ||
      `Bien situé à ${pubCity} (${pubSector}), ${pubSurface} m², ${pubRooms} pièces, ${pubCondition}.`;

    try {
      const created = await montyApi.publishMarketplaceListing({
        country_code: countryCode,
        publisher_type: pubType,
        publisher_label: pubLabel,
        title: finalTitle,
        property_kind: pubKind,
        city: pubCity,
        sector_label: pubSector,
        price_euro: pubPrice,
        surface_m2: pubSurface,
        rooms: pubRooms,
        dpe: pubDpe,
        condition_label: pubCondition,
        highlights: hlList,
        description: finalDesc,
        photo_count: pubPhotos,
      });
      if (created.moderation_status === "A_VERIFIER") {
        setPublishNotice(
          `Annonce « ${created.title} » enregistrée en attente de modération (A_VERIFIER) pour protéger la qualité du catalogue.`
        );
      } else {
        setLocalCatalog((prev) => [created, ...prev]);
        setOriginAddress(created.city);
        setPublishNotice(
          `Annonce « ${created.title} » publiée avec succès (délai estimé : ~${created.sale_timeline.likely_days} jours).`
        );
        setActiveTab("search");
      }
    } catch {
      const geo = resolveCityGeo(pubCity, countryCode);
      const needsAuthHint = !hasInMemoryAccessToken();
      const localCreated: MarketplaceListing = {
        id: `lst-${countryCode.toLowerCase()}-${Date.now()}`,
        country_code: countryCode,
        publisher_type: pubType,
        publisher_label:
          pubLabel.trim() || (pubType === "PARTICULIER" ? "Particulier vérifié" : "Agence partenaire"),
        title: redactPiiText(finalTitle),
        property_kind: pubKind,
        city: pubCity.trim() || "Lyon",
        region_name: geo.region,
        sector_label: pubSector.trim() || pubCity,
        price_euro: pubPrice,
        surface_m2: pubSurface,
        rooms: pubRooms,
        dpe: pubDpe,
        condition_label: pubCondition,
        highlights: hlList,
        description: redactPiiText(finalDesc),
        photo_count: pubPhotos,
        latitude: geo.lat,
        longitude: geo.lon,
        moderation_status: pubType === "PARTICULIER" ? "A_VERIFIER" : "PUBLIEE",
        sale_timeline: previewTimeline,
        published_at: new Date().toISOString().slice(0, 10),
      };
      setLocalCatalog((prev) => [localCreated, ...prev]);
      setOriginAddress(localCreated.city);
      setPublishNotice(
        needsAuthHint
          ? `Prévisualisation locale créée pour « ${localCreated.title} ». Connectez-vous au Cockpit SaaS ou créez un compte pour soumettre officiellement l'annonce à la modération serveur.`
          : `Annonce « ${localCreated.title} » ajoutée au catalogue interactif (masquage RGPD des PII appliqué).`
      );
      setActiveTab("search");
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
              Au-delà des portails classiques : 6 modes de mobilité réels, comparateur transparent, DPE/SCE et délai probable de vente.
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
        <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-4 py-3 text-sm text-emerald-950 flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0" />
          <span>{publishNotice}</span>
        </div>
      )}

      {isProView && pendingModeration.length > 0 && (
        <section className="rounded-2xl bg-amber-50 border border-amber-300 p-4 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="font-display text-sm font-bold text-amber-950">
              File de modération annonces ({pendingModeration.length} à vérifier)
            </h3>
            <span className="text-[11px] font-semibold text-amber-800">
              Contrôle RBAC PROPERTY_MODERATE
            </span>
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
                    {item.city} • {item.price_euro.toLocaleString("fr-FR")} € • {item.surface_m2} m² ({item.publisher_type})
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
        <PartnerFeedsPanel onSynced={() => handleSearch()} />
      ) : activeTab === "search" ? (
        <div className="space-y-6">
          {/* Barre de critères mobilité 6 modes */}
          <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3 text-sm">
              <label className="md:col-span-4 block">
                <span className="text-xs font-bold text-slateMuted">
                  Adresse / Ville de référence (travail, gare, école...)
                </span>
                <div className="mt-1 relative">
                  <MapPin className="w-4 h-4 text-emeraldDeep absolute left-3 top-2.5" />
                  <input
                    type="text"
                    value={originAddress}
                    onChange={(e) => setOriginAddress(e.target.value)}
                    placeholder={countryCode === "PT" ? "Lisboa, Porto, Faro..." : "Paris, Lyon, Bordeaux, Nantes..."}
                    className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm pl-9 pr-3 py-2 text-sm focus:outline-none focus:border-emeraldDeep"
                  />
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

              <div className="flex items-center gap-3 text-xs">
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
                  onClick={runSearch}
                  disabled={loadingSearch}
                  className="px-4 py-2 rounded-xl bg-emeraldDeep text-ivoryWarm font-bold hover:bg-emeraldMid transition"
                >
                  {loadingSearch ? "Calcul..." : "Actualiser"}
                </button>
              </div>
            </div>
          </section>

          {/* Barre récapitulative + Comparateur côte à côte */}
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slateMuted">
            <span>
              <strong className="text-emeraldDeep">{hits.length}</strong> annonce(s) classée(s) par temps de trajet depuis{" "}
              <strong className="text-obsidianInk">{originAddress}</strong> ({countryCode}) • Source :{" "}
              {dataSourceMode === "api" ? "API FastAPI live" : "Moteur local paritaire"}
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
                        {Math.round(h.listing.price_euro / Math.max(1, h.listing.surface_m2)).toLocaleString("fr-FR")} €/m²)
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
            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-8 text-center space-y-2">
              <p className="font-display font-bold text-lg text-emeraldDeep">
                Aucun bien dans ce périmètre strict
              </p>
              <p className="text-sm text-slateMuted">
                Décochez « Uniquement dans le périmètre strict » ou augmentez le temps de trajet / rayon pour voir les villes voisines.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {hits.map((hit) => {
                const { listing } = hit;
                const pricePerM2 = Math.round(listing.price_euro / Math.max(1, listing.surface_m2));
                const isCompared = compareIds.includes(listing.id);
                return (
                  <article
                    key={listing.id}
                    className={`rounded-2xl bg-ivoryCard border p-5 flex flex-col justify-between gap-4 transition ${
                      isCompared ? "border-emeraldDeep ring-2 ring-emeraldDeep/20" : "border-borderSubtle hover:border-emeraldMid"
                    }`}
                  >
                    <div className="space-y-3">
                      <div className="flex items-start justify-between gap-2">
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
                        <h3 className="font-display text-lg font-bold text-obsidianInk mt-0.5">
                          {listing.title}
                        </h3>
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

                      <p className="text-xs text-slateMuted leading-relaxed line-clamp-3">
                        {listing.description}
                      </p>

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

                    <div className="pt-2 border-t border-borderSubtle flex items-center justify-between gap-2 text-xs">
                      <span className="inline-flex items-center gap-1 text-emeraldDeep font-semibold">
                        <Clock className="w-3.5 h-3.5 text-goldAntique" />
                        Délai estimé : ~{listing.sale_timeline.likely_days} j
                      </span>
                      <button
                        type="button"
                        onClick={() => toggleCompare(listing.id)}
                        className={`px-3 py-1.5 rounded-lg font-bold border transition inline-flex items-center gap-1 ${
                          isCompared
                            ? "bg-emeraldDeep text-goldChampagne border-emeraldDeep"
                            : "bg-ivoryWarm text-emeraldDeep border-borderSubtle hover:border-emeraldDeep"
                        }`}
                      >
                        <GitCompare className="w-3.5 h-3.5" />
                        {isCompared ? "Comparé ✓" : "Comparer"}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </div>
      ) : (
        /* Onglet Publication Vendeur + Aide à la rédaction & Délai estimé */
        <form onSubmit={handlePublishListing} className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div className="lg:col-span-7 rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
            <h3 className="font-display text-xl font-bold text-emeraldDeep flex items-center gap-2">
              <Home className="w-5 h-5 text-goldAntique" />
              Caractéristiques du bien ({countryCode === "FR" ? "France" : "Portugal"})
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Statut du vendeur</span>
                <select
                  value={pubType}
                  onChange={(e) => setPubType(e.target.value as PublisherType)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                >
                  <option value="PARTICULIER">Particulier vendeur</option>
                  <option value="AGENCE">Agence partenaire</option>
                </select>
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Nom public affiché (optionnel)</span>
                <input
                  type="text"
                  value={pubLabel}
                  onChange={(e) => setPubLabel(e.target.value)}
                  placeholder={pubType === "PARTICULIER" ? "Particulier vérifié" : "Agence Monty Partenaire"}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Type de bien</span>
                <select
                  value={pubKind}
                  onChange={(e) => setPubKind(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                >
                  <option value="Appartement">Appartement</option>
                  <option value="Maison">Maison</option>
                  <option value="Studio">Studio</option>
                  <option value="Loft">Loft</option>
                </select>
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Ville</span>
                <input
                  type="text"
                  required
                  value={pubCity}
                  onChange={(e) => setPubCity(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Quartier / Transports</span>
                <input
                  type="text"
                  value={pubSector}
                  onChange={(e) => setPubSector(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Prix demandé (€)</span>
                <input
                  type="number"
                  min={10000}
                  step={1000}
                  value={pubPrice}
                  onChange={(e) => setPubPrice(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Surface (m²)</span>
                <input
                  type="number"
                  min={9}
                  value={pubSurface}
                  onChange={(e) => setPubSurface(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Pièces</span>
                <input
                  type="number"
                  min={1}
                  max={25}
                  value={pubRooms}
                  onChange={(e) => setPubRooms(Number(e.target.value) || 1)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">
                  {countryCode === "PT" ? "Certificado Energético (ADENE)" : "Classe DPE"}
                </span>
                <select
                  value={pubDpe}
                  onChange={(e) => setPubDpe(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                >
                  {["A", "B", "C", "D", "E", "F", "G"].map((d) => (
                    <option key={d} value={d}>
                      Classe {d}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block">
                <span className="text-xs font-bold text-slateMuted">Nombre de photos</span>
                <input
                  type="number"
                  min={0}
                  max={30}
                  value={pubPhotos}
                  onChange={(e) => setPubPhotos(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
            </div>

            <label className="block text-sm">
              <span className="text-xs font-bold text-slateMuted">
                Points forts (séparés par des virgules)
              </span>
              <input
                type="text"
                value={pubHighlightsRaw}
                onChange={(e) => setPubHighlightsRaw(e.target.value)}
                className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
            </label>

            <div className="pt-1 flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={handleGenerateCopy}
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-goldAntique text-emeraldDeep font-bold text-xs hover:bg-goldChampagne transition"
              >
                <Sparkles className="w-4 h-4" />
                Générer titre &amp; texte d&apos;annonce (sans PII)
              </button>
              {copyTip && <span className="text-xs text-emeraldDeep font-medium">{copyTip}</span>}
            </div>

            <label className="block text-sm">
              <span className="text-xs font-bold text-slateMuted">Titre de l&apos;annonce</span>
              <input
                type="text"
                value={pubTitle}
                onChange={(e) => setPubTitle(e.target.value)}
                placeholder="Cliquez sur « Générer titre & texte » ou rédigez votre titre..."
                className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
            </label>

            <label className="block text-sm">
              <span className="text-xs font-bold text-slateMuted">Description complète</span>
              <textarea
                rows={4}
                value={pubDescription}
                onChange={(e) => setPubDescription(e.target.value)}
                placeholder="Description détaillée du bien (e-mails/téléphones automatiquement masqués pour votre sécurité RGPD)..."
                className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
            </label>

            <button
              type="submit"
              disabled={publishBusy}
              className="w-full sm:w-auto px-6 py-3 rounded-xl bg-emeraldDeep text-goldChampagne font-bold text-sm hover:bg-emeraldMid transition"
            >
              {publishBusy ? "Publication..." : "Publier sur Monty Immobilier"}
            </button>
          </div>

          {/* Panneau latéral Estimation Délai + Sécurité RGPD */}
          <aside className="lg:col-span-5 space-y-4">
            <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-6 border border-goldAntique/40 space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-goldChampagne">
                  Analyse avant mise en ligne
                </span>
                <Calculator className="w-5 h-5 text-goldChampagne" />
              </div>
              <div>
                <p className="text-xs text-ivoryWarm/75">Prix au m² demandé</p>
                <p className="text-2xl font-display font-bold text-goldChampagne">
                  {pubSurface > 0
                    ? `${Math.round(pubPrice / pubSurface).toLocaleString("fr-FR")} €/m²`
                    : "—"}
                </p>
              </div>
              <div className="rounded-xl bg-emeraldSurface border border-goldAntique/30 p-4 space-y-1">
                <p className="text-xs text-ivoryWarm/80">Délai de vente indicatif estimé</p>
                {previewTimeline.status === "READY" ? (
                  <>
                    <p className="text-xl font-bold text-ivoryWarm">
                      ~{previewTimeline.likely_days} jours{" "}
                      <span className="text-xs font-normal text-goldChampagne">
                        ({previewTimeline.low_days} à {previewTimeline.high_days} j)
                      </span>
                    </p>
                    <p className="text-[11px] text-ivoryWarm/70">{previewTimeline.confidence_label}</p>
                  </>
                ) : (
                  <p className="text-xs text-amber-200">{previewTimeline.confidence_label}</p>
                )}
              </div>
            </div>

            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-2 text-xs text-slateMuted">
              <p className="font-bold text-emeraldDeep flex items-center gap-1.5 text-sm">
                <ShieldCheck className="w-4 h-4 text-goldAntique" />
                Protection Anti-Démarchage &amp; RGPD
              </p>
              <p className="leading-relaxed">
                Les numéros de téléphone et adresses e-mail saisis par erreur dans les champs publics sont automatiquement
                neutralisés avant publication afin d&apos;éviter le spam et la collecte sauvage observée sur les portails généralistes.
              </p>
            </div>
          </aside>
        </form>
      )}
    </div>
  );
}
