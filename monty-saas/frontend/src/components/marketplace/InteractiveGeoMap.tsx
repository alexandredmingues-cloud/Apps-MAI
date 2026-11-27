"use client";

import React, { useMemo, useState } from "react";
import { Compass, Layers, MapPin, Minus, Plus, RotateCcw } from "lucide-react";
import type { AddressSuggestion, MarketplaceSearchHit } from "@/lib/types";

interface InteractiveGeoMapProps {
  hits: MarketplaceSearchHit[];
  originAddress: string;
  originLat?: number;
  originLon?: number;
  selectedListingId?: string | null;
  selectedSuggestion?: AddressSuggestion | null;
  onSelectListing: (listingId: string) => void;
  onReverseLookup?: (lat: number, lon: number) => void;
}

export function InteractiveGeoMap({
  hits,
  originAddress,
  originLat,
  originLon,
  selectedListingId,
  selectedSuggestion,
  onSelectListing,
  onReverseLookup,
}: InteractiveGeoMapProps) {
  const [zoom, setZoom] = useState(1);

  const bounds = useMemo(() => {
    const pts: { lat: number; lon: number }[] = hits.map((h) => ({
      lat: h.listing.latitude,
      lon: h.listing.longitude,
    }));
    if (originLat !== undefined && originLon !== undefined) {
      pts.push({ lat: originLat, lon: originLon });
    } else if (selectedSuggestion) {
      pts.push({ lat: selectedSuggestion.latitude, lon: selectedSuggestion.longitude });
    }
    if (pts.length === 0) {
      return { minLat: 43, maxLat: 49, minLon: -2, maxLon: 6, centerLat: 46, centerLon: 2 };
    }
    const lats = pts.map((p) => p.lat);
    const lons = pts.map((p) => p.lon);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const minLon = Math.min(...lons);
    const maxLon = Math.max(...lons);
    const centerLat = (minLat + maxLat) / 2;
    const centerLon = (minLon + maxLon) / 2;
    return { minLat, maxLat, minLon, maxLon, centerLat, centerLon };
  }, [hits, originLat, originLon, selectedSuggestion]);

  function projectPoint(lat: number, lon: number): { x: number; y: number } {
    const spanLat = Math.max(0.25, bounds.maxLat - bounds.minLat + 0.35) / zoom;
    const spanLon = Math.max(0.35, bounds.maxLon - bounds.minLon + 0.5) / zoom;
    const x = 50 + ((lon - bounds.centerLon) / spanLon) * 82;
    const y = 50 - ((lat - bounds.centerLat) / spanLat) * 76;
    return {
      x: Math.max(6, Math.min(94, x)),
      y: Math.max(8, Math.min(92, y)),
    };
  }

  const refLat = originLat ?? selectedSuggestion?.latitude;
  const refLon = originLon ?? selectedSuggestion?.longitude;
  const originPoint = refLat !== undefined && refLon !== undefined ? projectPoint(refLat, refLon) : null;

  return (
    <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-4 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Compass className="w-4 h-4 text-emeraldDeep" />
          <h3 className="font-display font-bold text-sm text-emeraldDeep">
            Carte Interactive Synchronisée ({hits.length} bien{hits.length > 1 ? "s" : ""})
          </h3>
        </div>
        <div className="flex items-center gap-1.5 text-xs">
          {originPoint && onReverseLookup && refLat !== undefined && refLon !== undefined && (
            <button
              type="button"
              onClick={() => onReverseLookup(refLat, refLon)}
              className="px-2.5 py-1 rounded-lg border border-borderSubtle bg-ivoryWarm font-semibold text-emeraldDeep hover:border-emeraldDeep"
            >
              Géocodage inverse point
            </button>
          )}
          <button
            type="button"
            onClick={() => setZoom((z) => Math.max(0.6, Number((z - 0.3).toFixed(1))))}
            aria-label="Dézoomer la carte"
            className="p-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm hover:border-emeraldDeep"
          >
            <Minus className="w-3.5 h-3.5" />
          </button>
          <span className="px-2 font-mono text-[11px] text-slateMuted">{zoom.toFixed(1)}x</span>
          <button
            type="button"
            onClick={() => setZoom((z) => Math.min(4, Number((z + 0.3).toFixed(1))))}
            aria-label="Zoomer sur la carte"
            className="p-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm hover:border-emeraldDeep"
          >
            <Plus className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={() => setZoom(1)}
            aria-label="Recentrer la carte"
            className="p-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm hover:border-emeraldDeep"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Canevas cartographique vectoriel interactif sans dépendance externe */}
      <div className="relative h-64 sm:h-72 rounded-xl overflow-hidden border border-emeraldDeep/20 bg-gradient-to-br from-[#e8f0ec] via-[#f4f1ea] to-[#e4ede8]">
        <svg className="absolute inset-0 w-full h-full opacity-40" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <pattern id="montyMapGrid" width="32" height="32" patternUnits="userSpaceOnUse">
              <path d="M 32 0 L 0 0 0 32" fill="none" stroke="#11382d" strokeWidth="0.5" strokeOpacity="0.25" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#montyMapGrid)" />
        </svg>

        {originPoint && (
          <div
            className="absolute -translate-x-1/2 -translate-y-1/2 pointer-events-none"
            style={{ left: `${originPoint.x}%`, top: `${originPoint.y}%` }}
          >
            <div className="w-28 h-28 rounded-full border-2 border-dashed border-goldAntique/70 bg-goldChampagne/10 flex items-center justify-center">
              <div className="h-3.5 w-3.5 rounded-full bg-goldAntique border-2 border-white shadow" />
            </div>
            <span className="mt-1 block text-center text-[10px] font-bold bg-emeraldDeep text-goldChampagne px-2 py-0.5 rounded-full whitespace-nowrap">
              Réf : {originAddress}
            </span>
          </div>
        )}

        {hits.map((hit) => {
          const { listing } = hit;
          const pt = projectPoint(listing.latitude, listing.longitude);
          const selected = selectedListingId === listing.id;
          const cat = listing.price_index?.category;
          const pinColor =
            cat === "TRES_BONNE_AFFAIRE" || cat === "BONNE_AFFAIRE"
              ? "bg-emerald-700 text-white border-white"
              : cat === "TROP_CHER" || cat === "AU_DESSUS_DU_MARCHE"
              ? "bg-rose-700 text-white border-white"
              : "bg-emeraldDeep text-goldChampagne border-goldChampagne";

          return (
            <button
              key={listing.id}
              type="button"
              onClick={() => onSelectListing(listing.id)}
              style={{ left: `${pt.x}%`, top: `${pt.y}%` }}
              className={`absolute -translate-x-1/2 -translate-y-1/2 px-2 py-1 rounded-full text-[11px] font-bold border shadow-md transition-transform inline-flex items-center gap-1 ${pinColor} ${
                selected ? "scale-110 ring-2 ring-goldAntique z-20" : "hover:scale-105 z-10"
              }`}
              title={`${listing.title} (${listing.city}) — ${listing.price_euro.toLocaleString("fr-FR")} €`}
            >
              <MapPin className="w-3 h-3 shrink-0" />
              <span>{Math.round(listing.price_euro / 1000)}k€</span>
            </button>
          );
        })}
      </div>

      {/* Distinction stricte des 5 entités géographiques */}
      {selectedSuggestion && (
        <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 space-y-2 text-xs">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-bold text-emeraldDeep flex items-center gap-1.5">
              <Layers className="w-3.5 h-3.5 text-goldAntique" />
              Entités Géographiques Distinctes ({selectedSuggestion.source})
            </span>
            {selectedSuggestion.homonym_warning && (
              <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-900 font-semibold text-[11px]">
                Homonyme détecté : vérifiez la commune et le code postal
              </span>
            )}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2 text-[11px]">
            <div className="rounded-lg bg-white border border-borderSubtle p-2">
              <p className="text-slateMuted font-semibold">Adresse postale</p>
              <p className="text-obsidianInk font-medium truncate">{selectedSuggestion.entities.postal_address}</p>
            </div>
            <div className="rounded-lg bg-white border border-borderSubtle p-2">
              <p className="text-slateMuted font-semibold">Coordonnées GPS</p>
              <p className="font-mono text-obsidianInk">{selectedSuggestion.entities.gps}</p>
            </div>
            <div className="rounded-lg bg-white border border-borderSubtle p-2">
              <p className="text-slateMuted font-semibold">Bâtiment / BAN ID</p>
              <p className="font-mono text-obsidianInk truncate">{selectedSuggestion.entities.building_ref}</p>
            </div>
            <div className="rounded-lg bg-white border border-borderSubtle p-2">
              <p className="text-slateMuted font-semibold">Parcelle cadastrale</p>
              <p className="text-obsidianInk">{selectedSuggestion.entities.cadastral_parcel}</p>
            </div>
            <div className="rounded-lg bg-white border border-borderSubtle p-2">
              <p className="text-slateMuted font-semibold">Lot copropriété</p>
              <p className="text-obsidianInk">{selectedSuggestion.entities.copro_lot}</p>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
