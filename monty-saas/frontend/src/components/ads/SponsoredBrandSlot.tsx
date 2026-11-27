"use client";
/* Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code. */

import { useEffect, useState } from "react";
import { ExternalLink, Megaphone } from "lucide-react";

type SponsoredAd = { id: string; brand_name: string; creative_image_url: string; target_url: string; headline: string; description: string; sponsored: true };
const API_BASE = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8000").replace(/\\/+$/, "");

export function SponsoredBrandSlot({ placement = "HOME", countryCode = "FR", city = "" }: { placement?: "HOME" | "SEARCH" | "CONTENT" | "PRO"; countryCode?: "FR" | "PT" | "GB"; city?: string }) {
  const [ads, setAds] = useState<SponsoredAd[]>([]);
  useEffect(() => {
    const query = new URLSearchParams({ placement, country_code: countryCode, city, limit: "2" });
    let active = true;
    fetch(`${API_BASE}/api/v1/ads/public?${query.toString()}`, { cache: "no-store" })
      .then((response) => response.ok ? response.json() : Promise.reject(new Error("Ads unavailable")))
      .then((data: { ads?: SponsoredAd[] }) => { if (active) setAds(data.ads ?? []); })
      .catch(() => { if (active) setAds([]); });
    return () => { active = false; };
  }, [placement, countryCode, city]);

  useEffect(() => {
    if (!ads.length) return;
    const controller = new AbortController();
    ads.forEach((ad) => {
      fetch(`${API_BASE}/api/v1/ads/public/${encodeURIComponent(ad.id)}/impression`, { method: "POST", signal: controller.signal }).catch(() => undefined);
    });
    return () => controller.abort();
  }, [ads]);

  if (!ads.length) return null;
  return (
    <aside aria-label="Publicités de marques" className="space-y-3">
      <div className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-slateMuted"><Megaphone className="h-3.5 w-3.5" /> Marques partenaires · Publicité</div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {ads.map((ad) => (
          <a key={ad.id} href={ad.target_url} target="_blank" rel="sponsored noopener noreferrer" onClick={() => { fetch(`${API_BASE}/api/v1/ads/public/${encodeURIComponent(ad.id)}/click`, { method: "POST", keepalive: true }).catch(() => undefined); }} className="group flex min-h-28 overflow-hidden rounded-xl border border-borderSubtle bg-ivoryCard hover:border-goldAntique transition">
            {ad.creative_image_url && <img src={ad.creative_image_url} alt={`Publicité ${ad.brand_name}`} loading="lazy" className="w-28 sm:w-36 object-cover" />}
            <span className="flex-1 p-4 min-w-0">
              <span className="block text-[10px] uppercase tracking-wider font-bold text-goldAntique">Sponsorisé · {ad.brand_name}</span>
              <span className="mt-1 block font-display font-bold text-emeraldDeep">{ad.headline}</span>
              <span className="mt-1 block text-xs text-slateMuted line-clamp-2">{ad.description}</span>
              <span className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-emeraldDeep">En savoir plus <ExternalLink className="h-3 w-3" /></span>
            </span>
          </a>
        ))}
      </div>
    </aside>
  );
}
