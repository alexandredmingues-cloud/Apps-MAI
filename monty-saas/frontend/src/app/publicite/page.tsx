"use client";
/* Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code. */

import { useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, Megaphone, ShieldCheck } from "lucide-react";

const API_BASE = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8000").replace(/\/+$/, "");
type FormState = { advertiser_name: string; brand_name: string; contact_email: string; website_url: string; creative_image_url: string; target_url: string; placement: "HOME" | "SEARCH" | "CONTENT" | "PRO"; country_code: "FR" | "PT" | "GB"; city: string; headline: string; description: string; campaign_budget_euro: number; consent: boolean };
const initial: FormState = { advertiser_name: "", brand_name: "", contact_email: "", website_url: "", creative_image_url: "", target_url: "", placement: "HOME", country_code: "FR", city: "", headline: "", description: "", campaign_budget_euro: 0, consent: false };

export default function AdvertiseWithMonAgentPage() {
  const [form, setForm] = useState<FormState>(initial);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  function update<K extends keyof FormState>(key: K, value: FormState[K]) { setForm((current) => ({ ...current, [key]: value })); }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSending(true); setError("");
    try {
      const response = await fetch(`${API_BASE}/api/v1/ads/public/requests`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(form) });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(typeof data.detail === "string" ? data.detail : "Impossible d'envoyer la demande.");
      setSubmitted(true);
    } catch (e) { setError(e instanceof Error ? e.message : "Erreur réseau. Réessayez."); }
    finally { setSending(false); }
  }
  const inputClass = "w-full rounded-xl border border-borderSubtle bg-white px-3 py-2.5 text-sm text-obsidianInk outline-none focus:border-emeraldDeep";
  return <main className="min-h-screen bg-ivoryWarm text-obsidianInk"><div className="max-w-3xl mx-auto px-4 py-10 sm:py-16 space-y-8">
    <Link href="/site" className="inline-flex items-center gap-2 text-sm font-semibold text-emeraldDeep"><ArrowLeft className="h-4 w-4" /> Retour à MON-AGENT</Link>
    <header className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-7 sm:p-9 space-y-3"><div className="flex items-center gap-2 text-goldChampagne text-xs font-bold uppercase tracking-widest"><Megaphone className="h-4 w-4" /> Régie publicitaire MON-AGENT</div><h1 className="font-display text-3xl sm:text-4xl font-bold">Faites connaître votre marque aux acteurs de l'immobilier.</h1><p className="text-sm text-ivoryWarm/80 max-w-2xl">Présentez votre entreprise aux particuliers, vendeurs, acquéreurs et professionnels. Chaque campagne est vérifiée avant sa diffusion.</p></header>
    {submitted ? <section className="rounded-2xl border border-emeraldDeep/20 bg-white p-8 text-center space-y-3"><CheckCircle2 className="h-10 w-10 text-emeraldDeep mx-auto" /><h2 className="font-display text-2xl font-bold text-emeraldDeep">Demande envoyée</h2><p className="text-sm text-slateMuted">Votre demande est enregistrée en attente de vérification. Aucun paiement ni diffusion automatique n'a été déclenché. L'équipe MON-AGENT pourra vous recontacter à l'adresse indiquée.</p></section> : <form onSubmit={submit} className="rounded-2xl border border-borderSubtle bg-ivoryCard p-5 sm:p-7 space-y-5">
      <div className="grid sm:grid-cols-2 gap-4"><label className="space-y-1.5 text-sm font-semibold">Entreprise / annonceur<input required maxLength={140} className={inputClass} value={form.advertiser_name} onChange={(e) => update("advertiser_name", e.target.value)} /></label><label className="space-y-1.5 text-sm font-semibold">Marque à promouvoir<input required maxLength={120} className={inputClass} value={form.brand_name} onChange={(e) => update("brand_name", e.target.value)} /></label><label className="space-y-1.5 text-sm font-semibold">E-mail professionnel<input required type="email" maxLength={254} className={inputClass} value={form.contact_email} onChange={(e) => update("contact_email", e.target.value)} /></label><label className="space-y-1.5 text-sm font-semibold">Site internet (HTTPS)<input type="url" placeholder="https://" className={inputClass} value={form.website_url} onChange={(e) => update("website_url", e.target.value)} /></label><label className="space-y-1.5 text-sm font-semibold">Lien de destination (HTTPS)<input required type="url" placeholder="https://" className={inputClass} value={form.target_url} onChange={(e) => update("target_url", e.target.value)} /></label><label className="space-y-1.5 text-sm font-semibold">Visuel publicitaire (URL HTTPS)<input type="url" placeholder="https://..." className={inputClass} value={form.creative_image_url} onChange={(e) => update("creative_image_url", e.target.value)} /></label><label className="space-y-1.5 text-sm font-semibold">Emplacement<select className={inputClass} value={form.placement} onChange={(e) => update("placement", e.target.value as FormState["placement"])}><option value="HOME">Accueil</option><option value="SEARCH">Recherche immobilière</option><option value="CONTENT">Contenus et actualités</option><option value="PRO">Espace professionnels</option></select></label><label className="space-y-1.5 text-sm font-semibold">Pays<select className={inputClass} value={form.country_code} onChange={(e) => update("country_code", e.target.value as FormState["country_code"])}><option value="FR">France</option><option value="PT">Portugal</option><option value="GB">Royaume-Uni</option></select></label><label className="space-y-1.5 text-sm font-semibold">Ville ciblée (facultatif)<input maxLength={100} className={inputClass} value={form.city} onChange={(e) => update("city", e.target.value)} placeholder="Toutes les villes si vide" /></label><label className="space-y-1.5 text-sm font-semibold">Budget indicatif (€)<input type="number" min={0} max={10000000} className={inputClass} value={form.campaign_budget_euro} onChange={(e) => update("campaign_budget_euro", Number(e.target.value))} /></label></div>
      <label className="block space-y-1.5 text-sm font-semibold">Titre publicitaire<input required minLength={4} maxLength={100} className={inputClass} value={form.headline} onChange={(e) => update("headline", e.target.value)} placeholder="Votre message principal" /></label>
      <label className="block space-y-1.5 text-sm font-semibold">Description<textarea required minLength={10} maxLength={240} rows={3} className={inputClass} value={form.description} onChange={(e) => update("description", e.target.value)} placeholder="Décrivez votre offre en 240 caractères maximum." /></label>
      <label className="flex items-start gap-3 text-xs text-slateMuted"><input required type="checkbox" checked={form.consent} onChange={(e) => update("consent", e.target.checked)} className="mt-0.5" /><span>J'accepte que MON-AGENT traite ces informations afin d'étudier ma demande publicitaire. Je confirme être autorisé à représenter cette marque et disposer des droits sur les visuels et liens fournis. Les campagnes sont soumises à validation.</span></label>
      {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
      <button disabled={sending} className="w-full rounded-xl bg-emeraldDeep px-5 py-3.5 text-sm font-bold text-ivoryWarm hover:bg-emeraldMid disabled:opacity-60">{sending ? "Envoi..." : "Envoyer ma demande publicitaire"}</button>
      <p className="flex items-center gap-2 text-[11px] text-slateMuted"><ShieldCheck className="h-4 w-4 shrink-0" /> Demande non publiée automatiquement. Validation éditoriale et commerciale préalable.</p>
    </form>}
    <p className="text-xs text-slateMuted">MON-AGENT · Copyright © 2024–2026. Tous droits réservés sur les éléments originaux, sous réserve des licences tierces.</p>
  </div></main>;
}
