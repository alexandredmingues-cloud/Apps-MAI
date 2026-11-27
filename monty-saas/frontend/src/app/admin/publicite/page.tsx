"use client";
/* Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code. */

import { useCallback, useEffect, useState } from "react";
import { CheckCircle2, RefreshCw, ShieldCheck, XCircle } from "lucide-react";
import { loadPersistedSession } from "@/lib/api-client";

const API_BASE = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8000").replace(/\/+$/, "");
type Campaign = { id: string; advertiser_name: string; brand_name: string; contact_email: string; website_url: string; creative_image_url: string; target_url: string; placement: string; country_code: string; city: string; headline: string; description: string; campaign_budget_euro: number; status: string; starts_at: string | null; ends_at: string | null; impressions: number; clicks: number; created_at: string };

export default function AdminAdvertisingPage() {
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(async (accessToken: string) => {
    setLoading(true); setError("");
    try {
      const response = await fetch(`${API_BASE}/api/v1/ads/admin/requests`, { headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store" });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(typeof data.detail === "string" ? data.detail : "Accès administrateur refusé.");
      setCampaigns(data.requests ?? []);
    } catch (e) { setError(e instanceof Error ? e.message : "Erreur de chargement."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => {
    const session = loadPersistedSession();
    if (!session?.access_token) { setError("Connectez-vous avec un compte administrateur plateforme."); setLoading(false); return; }
    setToken(session.access_token);
    void load(session.access_token);
  }, [load]);
  async function review(id: string, status: "APPROVED" | "REJECTED" | "PAUSED") {
    if (!token) return;
    setBusy(id); setError("");
    try {
      const response = await fetch(`${API_BASE}/api/v1/ads/admin/${encodeURIComponent(id)}/review`, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` }, body: JSON.stringify({ status }) });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(typeof data.detail === "string" ? data.detail : "La modération a échoué.");
      await load(token);
    } catch (e) { setError(e instanceof Error ? e.message : "Erreur de modération."); }
    finally { setBusy(null); }
  }
  return <main className="min-h-screen bg-ivoryWarm text-obsidianInk"><div className="max-w-6xl mx-auto px-4 py-10 space-y-6">
    <header className="flex flex-wrap items-center justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-widest text-goldAntique">MON-AGENT · Administration plateforme</p><h1 className="font-display text-3xl font-bold text-emeraldDeep">Modération publicitaire</h1><p className="text-sm text-slateMuted mt-1">Examinez les marques, liens et créations avant toute diffusion.</p></div><button onClick={() => token && void load(token)} disabled={!token || loading} className="inline-flex items-center gap-2 rounded-xl border border-borderSubtle bg-white px-4 py-2 text-sm font-semibold text-emeraldDeep disabled:opacity-50"><RefreshCw className="h-4 w-4" /> Actualiser</button></header>
    {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
    {loading ? <p className="text-sm text-slateMuted">Chargement des campagnes…</p> : campaigns.length === 0 ? <div className="rounded-2xl border border-borderSubtle bg-white p-8 text-center text-sm text-slateMuted">Aucune demande publicitaire.</div> : <div className="space-y-4">{campaigns.map((ad) => <article key={ad.id} className="rounded-2xl border border-borderSubtle bg-white p-5 space-y-4"><div className="flex flex-wrap items-start justify-between gap-3"><div><div className="flex flex-wrap items-center gap-2"><h2 className="font-display text-xl font-bold text-emeraldDeep">{ad.brand_name}</h2><span className="rounded-full bg-ivoryWarm px-2.5 py-1 text-[10px] font-bold">{ad.status}</span><span className="text-[10px] text-slateMuted">{ad.placement} · {ad.country_code}{ad.city ? ` · ${ad.city}` : ""}</span></div><p className="text-sm font-semibold mt-1">{ad.headline}</p><p className="text-sm text-slateMuted mt-1">{ad.description}</p></div><div className="text-right text-xs text-slateMuted"><p>{ad.impressions.toLocaleString("fr-FR")} impressions</p><p>{ad.clicks.toLocaleString("fr-FR")} clics</p><p>Budget déclaré : {ad.campaign_budget_euro.toLocaleString("fr-FR")} €</p></div></div><div className="flex flex-wrap gap-3 text-xs"><span>Annonceur : <b>{ad.advertiser_name}</b></span><a className="underline text-emeraldDeep" href={`mailto:${ad.contact_email}`}>{ad.contact_email}</a><a className="underline text-emeraldDeep" href={ad.target_url} target="_blank" rel="noreferrer">Destination ↗</a>{ad.website_url && <a className="underline text-emeraldDeep" href={ad.website_url} target="_blank" rel="noreferrer">Site marque ↗</a>}</div>{ad.creative_image_url && <img src={ad.creative_image_url} alt={`Visuel proposé par ${ad.brand_name}`} className="max-h-40 max-w-xs rounded-lg object-contain" />}{ad.status === "PENDING_REVIEW" || ad.status === "PAUSED" ? <div className="flex flex-wrap gap-2"><button disabled={busy === ad.id} onClick={() => void review(ad.id, "APPROVED")} className="inline-flex items-center gap-2 rounded-lg bg-emeraldDeep px-3 py-2 text-xs font-bold text-white disabled:opacity-50"><CheckCircle2 className="h-4 w-4" /> Approuver et diffuser</button><button disabled={busy === ad.id} onClick={() => void review(ad.id, "REJECTED")} className="inline-flex items-center gap-2 rounded-lg border border-rose-200 px-3 py-2 text-xs font-bold text-rose-700 disabled:opacity-50"><XCircle className="h-4 w-4" /> Refuser</button></div> : ad.status === "APPROVED" ? <button disabled={busy === ad.id} onClick={() => void review(ad.id, "PAUSED")} className="rounded-lg border border-borderSubtle px-3 py-2 text-xs font-bold text-emeraldDeep disabled:opacity-50">Mettre en pause</button> : null}</article>)}</div>}
    <p className="flex items-center gap-2 text-[11px] text-slateMuted"><ShieldCheck className="h-4 w-4" /> Accès protégé par le rôle SUPER_ADMIN côté API. Les campagnes approuvées sont publiées sur les emplacements autorisés.</p>
  </div></main>;
}
