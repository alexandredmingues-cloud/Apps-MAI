"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import { Compass, Layers, LocateFixed, MapPin, Minus, Plus, RotateCcw } from "lucide-react";
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

type GeoPoint = { lat: number; lon: number };
type DragState = { pointerId: number; startX: number; startY: number; centerX: number; centerY: number };

const TILE_SIZE = 256;
const TILE_URL = process.env.NEXT_PUBLIC_OSM_TILE_URL || "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

function toWorldPixel({ lat, lon }: GeoPoint, zoom: number) {
  const scale = TILE_SIZE * 2 ** zoom;
  const safeLat = Math.max(-85.05112878, Math.min(85.05112878, lat));
  const sin = Math.sin((safeLat * Math.PI) / 180);
  return {
    x: ((lon + 180) / 360) * scale,
    y: (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * scale,
  };
}

function fromWorldPixel(x: number, y: number, zoom: number): GeoPoint {
  const scale = TILE_SIZE * 2 ** zoom;
  const lon = (x / scale) * 360 - 180;
  const n = Math.PI - (2 * Math.PI * y) / scale;
  const lat = (180 / Math.PI) * Math.atan(Math.sinh(n));
  return { lat, lon };
}

function tileUrl(z: number, x: number, y: number) {
  const max = 2 ** z;
  const wrappedX = ((x % max) + max) % max;
  if (y < 0 || y >= max) return "";
  return TILE_URL.replace("{z}", String(z)).replace("{x}", String(wrappedX)).replace("{y}", String(y));
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
  const points = useMemo<GeoPoint[]>(() => {
    const result = hits
      .filter((hit) => Number.isFinite(hit.listing.latitude) && Number.isFinite(hit.listing.longitude))
      .map((hit) => ({ lat: hit.listing.latitude, lon: hit.listing.longitude }));
    if (originLat !== undefined && originLon !== undefined) result.push({ lat: originLat, lon: originLon });
    else if (selectedSuggestion) result.push({ lat: selectedSuggestion.latitude, lon: selectedSuggestion.longitude });
    return result;
  }, [hits, originLat, originLon, selectedSuggestion]);

  const bounds = useMemo(() => {
    if (!points.length) return { center: { lat: 46.6, lon: 2.4 }, span: 5 };
    const lats = points.map((point) => point.lat);
    const lons = points.map((point) => point.lon);
    const centerLat = (Math.min(...lats) + Math.max(...lats)) / 2;
    const centerLon = (Math.min(...lons) + Math.max(...lons)) / 2;
    const longitudeSpan = Math.max(...lons) - Math.min(...lons);
    const latitudeSpan = Math.max(...lats) - Math.min(...lats);
    const span = Math.max(longitudeSpan, latitudeSpan / Math.max(0.2, Math.cos((centerLat * Math.PI) / 180)), 0.02);
    return { center: { lat: centerLat, lon: centerLon }, span };
  }, [points]);

  const initialZoom = Math.max(5, Math.min(17, Math.floor(Math.log2(1125 / (bounds.span * 1.6)))));
  const [center, setCenter] = useState<GeoPoint>(bounds.center);
  const [zoom, setZoom] = useState(initialZoom);
  const [userLocation, setUserLocation] = useState<GeoPoint | null>(null);
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);
  const drag = useRef<DragState | null>(null);
  const mapRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setCenter(bounds.center);
    setZoom(initialZoom);
  }, [bounds.center.lat, bounds.center.lon, initialZoom]);

  const centerPixel = toWorldPixel(center, zoom);
  const centerTileX = Math.floor(centerPixel.x / TILE_SIZE);
  const centerTileY = Math.floor(centerPixel.y / TILE_SIZE);
  const offsetX = centerPixel.x - centerTileX * TILE_SIZE;
  const offsetY = centerPixel.y - centerTileY * TILE_SIZE;
  const tiles = useMemo(() => {
    const result: { key: string; url: string; dx: number; dy: number }[] = [];
    for (let dy = -1; dy <= 1; dy += 1) {
      for (let dx = -2; dx <= 2; dx += 1) {
        const x = centerTileX + dx;
        const y = centerTileY + dy;
        const url = tileUrl(zoom, x, y);
        if (url) result.push({ key: `${zoom}/${x}/${y}`, url, dx, dy });
      }
    }
    return result;
  }, [centerTileX, centerTileY, zoom]);

  function position(point: GeoPoint) {
    const pixel = toWorldPixel(point, zoom);
    return { x: pixel.x - centerPixel.x, y: pixel.y - centerPixel.y };
  }

  function startDrag(event: React.PointerEvent<HTMLDivElement>) {
    if (event.button !== 0) return;
    const mapCenter = toWorldPixel(center, zoom);
    drag.current = { pointerId: event.pointerId, startX: event.clientX, startY: event.clientY, centerX: mapCenter.x, centerY: mapCenter.y };
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function moveDrag(event: React.PointerEvent<HTMLDivElement>) {
    const current = drag.current;
    if (!current || current.pointerId !== event.pointerId) return;
    setCenter(fromWorldPixel(current.centerX - (event.clientX - current.startX), current.centerY - (event.clientY - current.startY), zoom));
  }

  function endDrag(event: React.PointerEvent<HTMLDivElement>) {
    if (drag.current?.pointerId === event.pointerId) drag.current = null;
  }

  function changeZoom(nextZoom: number) {
    const bounded = Math.max(4, Math.min(19, nextZoom));
    setZoom(bounded);
  }

  const refLat = originLat ?? selectedSuggestion?.latitude;
  const refLon = originLon ?? selectedSuggestion?.longitude;
  const originPoint = refLat !== undefined && refLon !== undefined ? position({ lat: refLat, lon: refLon }) : null;

  return (
    <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-4 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Compass className="w-4 h-4 text-emeraldDeep" />
          <h3 className="font-display font-bold text-sm text-emeraldDeep">
            Carte OpenStreetMap synchronisée ({hits.length} bien{hits.length > 1 ? "s" : ""})
          </h3>
        </div>
        <div className="flex items-center gap-1.5 text-xs">
          {originPoint && onReverseLookup && refLat !== undefined && refLon !== undefined && (
            <button type="button" onClick={() => onReverseLookup(refLat, refLon)} className="px-2.5 py-1 rounded-lg border border-borderSubtle bg-ivoryWarm font-semibold text-emeraldDeep hover:border-emeraldDeep">
              Géocodage inverse
            </button>
          )}
          <button type="button" onClick={locateUser} disabled={locating} aria-label="Me localiser" title="Me localiser" className="p-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm hover:border-emeraldDeep disabled:opacity-50"><LocateFixed className="w-3.5 h-3.5" /></button>\n          <button type="button" onClick={() => changeZoom(zoom - 1)} aria-label="Dézoomer la carte" className="p-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm hover:border-emeraldDeep"><Minus className="w-3.5 h-3.5" /></button>
          <span className="px-2 font-mono text-[11px] text-slateMuted">Niveau {zoom}</span>
          <button type="button" onClick={() => changeZoom(zoom + 1)} aria-label="Zoomer sur la carte" className="p-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm hover:border-emeraldDeep"><Plus className="w-3.5 h-3.5" /></button>
          <button type="button" onClick={() => { setCenter(bounds.center); setZoom(initialZoom); }} aria-label="Recentrer la carte" className="p-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm hover:border-emeraldDeep"><RotateCcw className="w-3.5 h-3.5" /></button>
        </div>
      </div>

      <div
        ref={mapRef}
        role="application"
        aria-label="Carte interactive OpenStreetMap des annonces immobilières"
        className="relative h-72 sm:h-80 rounded-xl overflow-hidden border border-emeraldDeep/20 bg-[#e8eee8] cursor-grab active:cursor-grabbing select-none"
        style={{ touchAction: "none" }}
        onPointerDown={startDrag}
        onPointerMove={moveDrag}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        onLostPointerCapture={endDrag}
      >
        {tiles.map((tile) => (
          <img
            key={tile.key}
            src={tile.url}
            alt=""
            draggable={false}
            loading="lazy"
            className="absolute max-w-none pointer-events-none"
            style={{ width: TILE_SIZE, height: TILE_SIZE, left: `calc(50% + ${tile.dx * TILE_SIZE - offsetX}px)`, top: `calc(50% + ${tile.dy * TILE_SIZE - offsetY}px)` }}
          />
        ))}

        {userLocation && (() => {\n          const point = position(userLocation);\n          return (\n            <div className="absolute z-30 -translate-x-1/2 -translate-y-1/2 pointer-events-none" style={{ left: `calc(50% + ${point.x}px)`, top: `calc(50% + ${point.y}px)` }}>\n              <div className="h-5 w-5 rounded-full bg-sky-500 border-[3px] border-white shadow-lg ring-4 ring-sky-500/25" />\n              <span className="mt-1 block rounded-full bg-sky-700 px-2 py-0.5 text-center text-[10px] font-bold text-white whitespace-nowrap">Votre position</span>\n            </div>\n          );\n        })()}\n\n        {originPoint && (
          <div className="absolute z-20 -translate-x-1/2 -translate-y-1/2 pointer-events-none" style={{ left: `calc(50% + ${originPoint.x}px)`, top: `calc(50% + ${originPoint.y}px)` }}>
            <div className="w-12 h-12 rounded-full border-2 border-dashed border-goldAntique bg-goldChampagne/20 flex items-center justify-center">
              <div className="h-3.5 w-3.5 rounded-full bg-goldAntique border-2 border-white shadow" />
            </div>
            <span className="mt-1 block text-center text-[10px] font-bold bg-emeraldDeep text-goldChampagne px-2 py-0.5 rounded-full whitespace-nowrap">{originAddress}</span>
          </div>
        )}

        {hits.map((hit) => {
          const { listing } = hit;
          if (!Number.isFinite(listing.latitude) || !Number.isFinite(listing.longitude)) return null;
          const point = position({ lat: listing.latitude, lon: listing.longitude });
          const selected = selectedListingId === listing.id;
          const category = listing.price_index?.category;
          const pinColor = category === "TRES_BONNE_AFFAIRE" || category === "BONNE_AFFAIRE"
            ? "bg-emerald-700 text-white border-white"
            : category === "TROP_CHER" || category === "AU_DESSUS_DU_MARCHE"
              ? "bg-rose-700 text-white border-white"
              : "bg-emeraldDeep text-goldChampagne border-goldChampagne";
          return (
            <button
              key={listing.id}
              type="button"
              onPointerDown={(event) => event.stopPropagation()}
              onClick={() => onSelectListing(listing.id)}
              style={{ left: `calc(50% + ${point.x}px)`, top: `calc(50% + ${point.y}px)` }}
              className={`absolute z-30 -translate-x-1/2 -translate-y-1/2 px-2 py-1 rounded-full text-[11px] font-bold border shadow-md transition-transform inline-flex items-center gap-1 ${pinColor} ${selected ? "scale-110 ring-2 ring-goldAntique" : "hover:scale-105"}`}
              title={`${listing.title} (${listing.city}) — ${listing.price_euro.toLocaleString("fr-FR")} €`}
            >
              <MapPin className="w-3 h-3 shrink-0" />
              <span>{Math.round(listing.price_euro / 1000)}k€</span>
            </button>
          );
        })}

        <div className="absolute bottom-2 left-2 z-40 rounded-md bg-white/90 px-2 py-1 text-[10px] text-slate-700 shadow pointer-events-none">
          Faites glisser la carte pour explorer
        </div>
      </div>
      {locationError && <p role="status" className="text-xs text-amber-800">{locationError}</p>}\n      {locating && <p role="status" className="text-xs text-slateMuted">Localisation en cours…</p>}\n      <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] text-slateMuted">
        <span>© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer" className="underline">OpenStreetMap contributors</a></span>
        <span>Fond cartographique OSM · coordonnées WGS84</span>
      </div>

      {selectedListingId && (() => {\n        const selected = hits.find((hit) => hit.listing.id === selectedListingId)?.listing;\n        if (!selected) return null;\n        const mapUrl = `https://www.openstreetmap.org/?mlat=${selected.latitude}&mlon=${selected.longitude}#map=17/${selected.latitude}/${selected.longitude}`;\n        return (\n          <div className="rounded-xl border border-emeraldDeep/20 bg-ivoryWarm p-3 text-xs flex flex-wrap items-center justify-between gap-3">\n            <div className="min-w-0">\n              <p className="font-bold text-emeraldDeep truncate">{selected.title}</p>\n              <p className="text-slateMuted">{selected.city} · {selected.surface_m2} m² · {selected.rooms} pièce(s) · {selected.price_euro.toLocaleString("fr-FR")} €</p>\n            </div>\n            <a href={mapUrl} target="_blank" rel="noreferrer" className="shrink-0 rounded-lg border border-borderSubtle bg-white px-3 py-2 font-semibold text-emeraldDeep hover:border-emeraldDeep">Ouvrir dans OpenStreetMap ↗</a>\n          </div>\n        );\n      })()}\n\n      {selectedSuggestion && (
        <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 space-y-2 text-xs">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-bold text-emeraldDeep flex items-center gap-1.5"><Layers className="w-3.5 h-3.5 text-goldAntique" />Entités géographiques distinctes ({selectedSuggestion.source})</span>
            {selectedSuggestion.homonym_warning && <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-900 font-semibold text-[11px]">Homonyme détecté : vérifiez la commune et le code postal</span>}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2 text-[11px]">
            <div className="rounded-lg bg-white border border-borderSubtle p-2"><p className="text-slateMuted font-semibold">Adresse postale</p><p className="text-obsidianInk font-medium truncate">{selectedSuggestion.entities.postal_address}</p></div>
            <div className="rounded-lg bg-white border border-borderSubtle p-2"><p className="text-slateMuted font-semibold">Coordonnées GPS</p><p className="font-mono text-obsidianInk">{selectedSuggestion.entities.gps}</p></div>
            <div className="rounded-lg bg-white border border-borderSubtle p-2"><p className="text-slateMuted font-semibold">Bâtiment / BAN ID</p><p className="font-mono text-obsidianInk truncate">{selectedSuggestion.entities.building_ref}</p></div>
            <div className="rounded-lg bg-white border border-borderSubtle p-2"><p className="text-slateMuted font-semibold">Parcelle cadastrale</p><p className="text-obsidianInk">{selectedSuggestion.entities.cadastral_parcel}</p></div>
            <div className="rounded-lg bg-white border border-borderSubtle p-2"><p className="text-slateMuted font-semibold">Lot copropriété</p><p className="text-obsidianInk">{selectedSuggestion.entities.copro_lot}</p></div>
          </div>
        </div>
      )}
    </section>
  );
}
