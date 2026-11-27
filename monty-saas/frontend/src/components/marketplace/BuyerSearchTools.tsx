"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { Bell, Calculator, CheckCircle2, SearchCheck, Send, Trash2 } from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type { CountryCode, TransportMode } from "@/lib/types";

export interface SavedSearchAlert {
  id: string;
  countryCode: CountryCode;
  originAddress: string;
  transportMode: TransportMode;
  maxMinutes: number;
  maxPrice: number;
  email: string;
  createdAt: string;
}

const ALERTS_STORAGE_KEY = "monty_saved_search_alerts_v1";

interface BuyerSearchToolsProps {
  countryCode: CountryCode;
  originAddress: string;
  transportMode: TransportMode;
  maxMinutes: number;
  maxPrice: number;
  showAlertDrawer: boolean;
  onCloseAlertDrawer: () => void;
}

export function BuyerSearchTools({
  countryCode,
  originAddress,
  transportMode,
  maxMinutes,
  maxPrice,
  showAlertDrawer,
  onCloseAlertDrawer,
}: BuyerSearchToolsProps) {
  const [savedAlerts, setSavedAlerts] = useState<SavedSearchAlert[]>([]);
  const [alertEmail, setAlertEmail] = useState("");
  const [alertRgpd, setAlertRgpd] = useState(false);
  const [alertBusy, setAlertBusy] = useState(false);
  const [alertFeedback, setAlertFeedback] = useState<string | null>(null);

  // Concierge lead capture : "Je ne trouve pas mon bien, faites ma recherche"
  const [conciergeCity, setConciergeCity] = useState(originAddress);
  const [conciergeBudget, setConciergeBudget] = useState(maxPrice);
  const [conciergeRooms, setConciergeRooms] = useState(3);
  const [conciergeDelay, setConciergeDelay] = useState("Sous 3 mois");
  const [conciergeNotes, setConciergeNotes] = useState("");
  const [conciergeEmail, setConciergeEmail] = useState("");
  const [conciergeRgpd, setConciergeRgpd] = useState(false);
  const [conciergeBusy, setConciergeBusy] = useState(false);
  const [conciergeError, setConciergeError] = useState<string | null>(null);
  const [conciergeSent, setConciergeSent] = useState(false);

  useEffect(() => {
    setConciergeCity(originAddress);
  }, [originAddress]);

  useEffect(() => {
    setConciergeBudget(maxPrice);
  }, [maxPrice]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      const raw = window.localStorage.getItem(ALERTS_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as SavedSearchAlert[];
        if (Array.isArray(parsed)) setSavedAlerts(parsed);
      }
    } catch {
      // Ignorer stockage local indisponible
    }
  }, []);

  function persistAlerts(next: SavedSearchAlert[]) {
    setSavedAlerts(next);
    if (typeof window !== "undefined") {
      try {
        window.localStorage.setItem(ALERTS_STORAGE_KEY, JSON.stringify(next));
      } catch {
        // Ignorer quota
      }
    }
  }

  async function handleSaveAlert(e: React.FormEvent) {
    e.preventDefault();
    const cleanEmail = alertEmail.trim();
    if (!cleanEmail.includes("@") || !cleanEmail.includes(".")) {
      setAlertFeedback("Veuillez saisir une adresse e-mail valide pour recevoir votre alerte.");
      return;
    }
    if (!alertRgpd) {
      setAlertFeedback("Cochez la case pour autoriser l'enregistrement et le traitement de votre recherche.");
      return;
    }
    setAlertBusy(true);
    let syncedRemote = false;
    let leadId = `alt-${Date.now()}`;
    try {
      const lead = await montyApi.submitPublicLead({
        lead_kind: "SEARCH_ALERT",
        country_code: countryCode,
        email: cleanEmail,
        city: originAddress,
        budget_euro: maxPrice,
        search_transport_mode: transportMode,
        max_travel_minutes: maxMinutes,
        notes: `Alerte recherche : ${originAddress} • ≤ ${maxMinutes} min (${transportMode}) • ≤ ${maxPrice.toLocaleString("fr-FR")} €`,
        rgpd_consent_granted: true,
      });
      leadId = lead.id;
      syncedRemote = true;
    } catch {
      // Fallback local si API endormie
    } finally {
      setAlertBusy(false);
    }

    const created: SavedSearchAlert = {
      id: leadId,
      countryCode,
      originAddress,
      transportMode,
      maxMinutes,
      maxPrice,
      email: cleanEmail,
      createdAt: new Date().toISOString().slice(0, 10),
    };
    persistAlerts([created, ...savedAlerts].slice(0, 8));
    setAlertEmail("");
    setAlertFeedback(
      syncedRemote
        ? `Recherche enregistrée et transmise à un conseiller MON-AGENT. Les notifications automatiques ne sont pas encore activées.`
        : `Recherche conservée sur cet appareil uniquement. Aucune notification automatique ne sera envoyée tant que la synchronisation serveur n'est pas disponible.`
    );
  }

  function handleDeleteAlert(id: string) {
    persistAlerts(savedAlerts.filter((a) => a.id !== id));
  }

  async function handleSubmitConcierge(e: React.FormEvent) {
    e.preventDefault();
    setConciergeError(null);
    if (!conciergeCity.trim() || conciergeBudget < 20_000) {
      setConciergeError("Indiquez un secteur recherché et un budget réaliste (≥ 20 000 €).");
      return;
    }
    if (!conciergeEmail.trim().includes("@")) {
      setConciergeError("Renseignez un e-mail de contact pour recevoir les propositions ciblées.");
      return;
    }
    if (!conciergeRgpd) {
      setConciergeError("Le consentement explicite RGPD est requis pour transmettre votre recherche.");
      return;
    }
    setConciergeBusy(true);
    try {
      await montyApi.submitPublicLead({
        lead_kind: "BUYER_CONCIERGE",
        country_code: countryCode,
        email: conciergeEmail.trim(),
        city: conciergeCity.trim(),
        budget_euro: conciergeBudget,
        min_rooms: conciergeRooms,
        search_transport_mode: transportMode,
        max_travel_minutes: maxMinutes,
        horizon: conciergeDelay,
        notes: conciergeNotes.trim(),
        rgpd_consent_granted: true,
      });
      setConciergeSent(true);
    } catch {
      setConciergeError("La demande n'a pas pu être transmise au serveur. Rien n'a été enregistré : réessayez lorsque la connexion sera rétablie.");
    } finally {
      setConciergeBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      {showAlertDrawer && (
        <section className="rounded-2xl bg-ivoryCard border border-goldAntique/50 p-5 space-y-3 shadow-sm">
          <div className="flex items-center justify-between gap-2">
            <h3 className="font-display text-base font-bold text-emeraldDeep flex items-center gap-2">
              <Bell className="w-4 h-4 text-goldAntique" />
              Enregistrer cette recherche ({originAddress} • ≤ {maxMinutes} min {transportMode})
            </h3>
            <button
              type="button"
              onClick={onCloseAlertDrawer}
              className="text-xs font-semibold text-slateMuted hover:text-obsidianInk"
            >
              Fermer
            </button>
          </div>
          <form onSubmit={handleSaveAlert} className="grid grid-cols-1 sm:grid-cols-12 gap-3 items-end text-xs">
            <label className="sm:col-span-5 block">
              <span className="font-semibold text-slateMuted">E-mail de réception</span>
              <input
                type="email"
                value={alertEmail}
                onChange={(e) => setAlertEmail(e.target.value)}
                placeholder="vous@exemple.fr"
                className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-sm"
              />
            </label>
            <label className="sm:col-span-5 flex items-center gap-2 pb-2 text-slateMuted cursor-pointer">
              <input
                type="checkbox"
                checked={alertRgpd}
                onChange={(e) => setAlertRgpd(e.target.checked)}
                className="rounded border-borderSubtle"
              />
              <span>J&apos;autorise MON-AGENT à enregistrer ces critères et à transmettre ma demande à un conseiller. Les notifications automatiques ne sont pas garanties.</span>
            </label>
            <button
              type="submit"
              disabled={alertBusy}
              className="sm:col-span-2 px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-60 transition"
            >
              {alertBusy ? "Enregistrement..." : "Enregistrer"}
            </button>
          </form>
          {alertFeedback && (
            <p className="text-xs font-medium text-emeraldDeep flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              {alertFeedback}
            </p>
          )}
          {savedAlerts.length > 0 && (
            <div className="pt-2 border-t border-borderSubtle space-y-1.5">
              <p className="text-[11px] font-bold uppercase tracking-wider text-slateMuted">
                Mes recherches enregistrées ({savedAlerts.length})
              </p>
              <div className="flex flex-wrap gap-2">
                {savedAlerts.map((alt) => (
                  <span
                    key={alt.id}
                    className="inline-flex items-center gap-2 rounded-lg bg-ivoryWarm border border-borderSubtle px-2.5 py-1 text-[11px]"
                  >
                    <span>
                      <strong>{alt.originAddress}</strong> • ≤ {alt.maxMinutes} min {alt.transportMode} • ≤{" "}
                      {alt.maxPrice.toLocaleString("fr-FR")} €
                    </span>
                    <button
                      type="button"
                      onClick={() => handleDeleteAlert(alt.id)}
                      aria-label="Supprimer cette recherche"
                      className="text-slateMuted hover:text-rose-700"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </span>
                ))}
              </div>
            </div>
          )}
        </section>
      )}

      {/* Module "Je ne trouve pas mon bien, faites ma recherche" */}
      <section className="rounded-2xl bg-gradient-to-br from-emeraldDeep to-emeraldMid text-ivoryWarm p-6 border border-goldAntique/40 space-y-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div className="space-y-1">
            <p className="text-xs font-bold uppercase tracking-widest text-goldChampagne flex items-center gap-1.5">
              <SearchCheck className="w-4 h-4" />
              Chasse &amp; Accompagnement Acquéreur
            </p>
            <h3 className="font-display text-xl font-bold">
              Je ne trouve pas mon bien, faites ma recherche
            </h3>
            <p className="text-xs text-ivoryWarm/80 max-w-2xl">
              Confiez vos critères réels (temps de trajet, budget validé, nombre de pièces) à un conseiller Monty Pro :
              nous activons le matching sur les biens off-market et les nouvelles entrées.
            </p>
          </div>
          <Link
            href="/simulateurs"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-emeraldSurface border border-goldAntique/40 text-xs font-semibold text-goldChampagne hover:bg-emeraldMid shrink-0"
          >
            <Calculator className="w-3.5 h-3.5" />
            Valider ma capacité HCSF
          </Link>
        </div>

        {conciergeSent ? (
          <div className="rounded-xl bg-emeraldSurface border border-goldAntique/50 p-4 space-y-2 text-xs">
            <p className="font-bold text-goldChampagne flex items-center gap-1.5 text-sm">
              <CheckCircle2 className="w-4 h-4" />
              Demande enregistrée et transmise au CRM MON-AGENT Pro
            </p>
            <p className="text-ivoryWarm/85">
              Critères transmis : <strong>{conciergeCity}</strong> • {conciergeRooms}+ pièces • budget max{" "}
              <strong>{conciergeBudget.toLocaleString("fr-FR")} €</strong> ({conciergeDelay}). Un récapitulatif sera
adressé à <strong>{conciergeEmail}</strong> après étude de votre demande par un conseiller.
            </p>
            <button
              type="button"
              onClick={() => setConciergeSent(false)}
              className="text-goldChampagne underline font-semibold"
            >
              Modifier mes critères de recherche
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmitConcierge} className="space-y-3 text-xs">
            {conciergeError && (
              <div className="rounded-xl bg-red-900/40 border border-red-300/60 px-3 py-2 text-amber-100">
                {conciergeError}
              </div>
            )}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
              <label className="block">
                <span className="font-semibold text-ivoryWarm/85">Ville / Axe trajet cible</span>
                <input
                  type="text"
                  value={conciergeCity}
                  onChange={(e) => setConciergeCity(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-goldAntique/30 bg-ivoryWarm text-obsidianInk px-3 py-2 text-sm"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-ivoryWarm/85">Budget maximal (€)</span>
                <input
                  type="number"
                  step={10000}
                  value={conciergeBudget}
                  onChange={(e) => setConciergeBudget(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-goldAntique/30 bg-ivoryWarm text-obsidianInk px-3 py-2 text-sm"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-ivoryWarm/85">Pièces minimum</span>
                <input
                  type="number"
                  min={1}
                  max={15}
                  value={conciergeRooms}
                  onChange={(e) => setConciergeRooms(Number(e.target.value) || 1)}
                  className="mt-1 w-full rounded-xl border border-goldAntique/30 bg-ivoryWarm text-obsidianInk px-3 py-2 text-sm"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-ivoryWarm/85">Horizon d&apos;achat</span>
                <select
                  value={conciergeDelay}
                  onChange={(e) => setConciergeDelay(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-goldAntique/30 bg-ivoryWarm text-obsidianInk px-3 py-2 text-sm"
                >
                  <option value="Immédiat (< 1 mois)">Immédiat (&lt; 1 mois)</option>
                  <option value="Sous 3 mois">Sous 3 mois</option>
                  <option value="3 à 6 mois">3 à 6 mois</option>
                </select>
              </label>
              <label className="block">
                <span className="font-semibold text-ivoryWarm/85">Votre e-mail</span>
                <input
                  type="email"
                  value={conciergeEmail}
                  onChange={(e) => setConciergeEmail(e.target.value)}
                  placeholder="nom@exemple.fr"
                  className="mt-1 w-full rounded-xl border border-goldAntique/30 bg-ivoryWarm text-obsidianInk px-3 py-2 text-sm"
                />
              </label>
            </div>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex-1 space-y-2">
                <input
                  type="text"
                  value={conciergeNotes}
                  onChange={(e) => setConciergeNotes(e.target.value)}
                  placeholder="Précisions utiles : jardin, ascenseur, proche école ou gare TER, DPE min C..."
                  className="w-full rounded-xl border border-goldAntique/30 bg-ivoryWarm text-obsidianInk px-3 py-2 text-sm"
                />
                <label className="inline-flex items-center gap-2 text-ivoryWarm/80 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={conciergeRgpd}
                    onChange={(e) => setConciergeRgpd(e.target.checked)}
                    className="rounded border-goldAntique/40"
                  />
                  <span>
                    Consentement RGPD : j&apos;autorise uniquement les conseillers Monty à me recontacter pour cette
                    recherche.
                  </span>
                </label>
              </div>
              <button
                type="submit"
                disabled={conciergeBusy}
                className="inline-flex items-center justify-center gap-2 px-5 py-3 rounded-xl bg-goldAntique text-emeraldDeep font-bold text-sm hover:bg-goldChampagne disabled:opacity-60 transition shrink-0"
              >
                <Send className="w-4 h-4" />
                {conciergeBusy ? "Transmission..." : "Lancer ma recherche guidée"}
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  );
}
