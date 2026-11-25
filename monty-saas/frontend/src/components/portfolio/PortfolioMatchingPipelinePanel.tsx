"use client";

import React, { useEffect, useState } from "react";
import {
  Building2,
  CalendarCheck,
  CheckCircle2,
  GitCompareArrows,
  Handshake,
  ShieldCheck,
  TrendingUp,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type {
  DvfComparableInput,
  DvfValuationResult,
  MatchingCalculationResult,
  SaleTimelineEstimate,
} from "@/lib/types";

type PipelineTab = "properties" | "matching" | "visits" | "transactions";

interface PortfolioProperty {
  id: string;
  title: string;
  sector: string;
  property_type: string;
  price: number;
  surface: number;
  bedrooms: number;
  dpe: string;
  mandate: "EXCLUSIF" | "SIMPLE";
  docs_ratio: number;
}

interface VisitEntry {
  id: string;
  propertyTitle: string;
  buyerName: string;
  dateLabel: string;
  status: "PLANIFIEE" | "REALISEE";
  feedback: string;
}

interface TransactionEntry {
  id: string;
  propertyTitle: string;
  buyerName: string;
  offerEuro: number;
  stage: "OFFRE" | "COMPROMIS" | "ACTE";
  notaryTargetDate: string;
}

const SAMPLE_PROPERTIES: PortfolioProperty[] = [
  {
    id: "prop-lyon-t3",
    title: "T3 Lumineux Terrasse Part-Dieu",
    sector: "Lyon 3e Part-Dieu",
    property_type: "Appartement",
    price: 418000,
    surface: 78,
    bedrooms: 2,
    dpe: "B",
    mandate: "EXCLUSIF",
    docs_ratio: 0.85,
  },
  {
    id: "prop-montpellier-maison",
    title: "Maison Contemporaine Jardin Beaux-Arts",
    sector: "Montpellier Beaux-Arts",
    property_type: "Maison",
    price: 595000,
    surface: 132,
    bedrooms: 4,
    dpe: "C",
    mandate: "EXCLUSIF",
    docs_ratio: 0.72,
  },
];

const SAMPLE_DVF_COMPARABLES: DvfComparableInput[] = Array.from({ length: 16 }, (_, idx) => ({
  mutation_id: `dvf-2025-${idx + 1}`,
  date_mutation: `2025-0${(idx % 9) + 1}-14`,
  year: 2025,
  price_euro: (5150 + (idx % 4) * 140) * (72 + (idx % 3) * 6),
  surface_m2: 72 + (idx % 3) * 6,
  property_type: "Appartement",
  distance_meters: 45 + idx * 18,
}));

export function PortfolioMatchingPipelinePanel({
  initialTab = "properties",
}: {
  initialTab?: PipelineTab;
}) {
  const [tab, setTab] = useState<PipelineTab>(initialTab);
  const [properties] = useState<PortfolioProperty[]>(SAMPLE_PROPERTIES);
  const [selectedPropId, setSelectedPropId] = useState(SAMPLE_PROPERTIES[0].id);

  // DVF V2 & Délai de vente
  const [dvfResult, setDvfResult] = useState<DvfValuationResult | null>(null);
  const [timelineResult, setTimelineResult] = useState<(SaleTimelineEstimate & { warnings?: string[] }) | null>(null);

  // Matching Acquéreur ↔ Bien
  const [buyerBudget, setBuyerBudget] = useState(440000);
  const [buyerMinSurface, setBuyerMinSurface] = useState(65);
  const [buyerMinRooms, setBuyerMinRooms] = useState(2);
  const [matchResult, setMatchResult] = useState<MatchingCalculationResult | null>(null);

  // Visites & Transactions
  const [visits, setVisits] = useState<VisitEntry[]>([
    {
      id: "vis-1",
      propertyTitle: "T3 Lumineux Terrasse Part-Dieu",
      buyerName: "Camille Laurent",
      dateLabel: "Jeudi 17h30",
      status: "PLANIFIEE",
      feedback: "Financement HCSF vérifié à 31,4 %.",
    },
  ]);
  const [newVisitBuyer, setNewVisitBuyer] = useState("");
  const [newVisitDate, setNewVisitDate] = useState("");

  const [transactions, setTransactions] = useState<TransactionEntry[]>([
    {
      id: "tx-1",
      propertyTitle: "Maison Contemporaine Jardin Beaux-Arts",
      buyerName: "Marc & Claire V.",
      offerEuro: 582000,
      stage: "COMPROMIS",
      notaryTargetDate: "2026-07-10",
    },
  ]);

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    setTab(initialTab);
  }, [initialTab]);

  const selectedProp = properties.find((p) => p.id === selectedPropId) ?? properties[0];

  async function handleRunScoreMonty() {
    setErrorMsg(null);
    setLoading(true);
    try {
      const dvf = await montyApi.calculateDvf({
        comparables: SAMPLE_DVF_COMPARABLES,
        property_kind: selectedProp.property_type,
        surface_m2: selectedProp.surface,
        dpe: selectedProp.dpe,
        condition_label: "Bon état",
        provided_docs_ratio: selectedProp.docs_ratio,
      });
      setDvfResult(dvf);
      const tl = await montyApi.calculateSaleTimeline({
        sector_name: selectedProp.sector,
        property_kind: selectedProp.property_type,
        asking_price_euro: selectedProp.price,
        surface_m2: selectedProp.surface,
        dpe: selectedProp.dpe,
        condition_label: "Bon état",
        dvf_price_m2: dvf.adjusted_price_m2,
        dvf_count: dvf.comparables_used_count,
        photo_count: 10,
        provided_docs_ratio: selectedProp.docs_ratio,
        mandate_type: selectedProp.mandate,
      });
      setTimelineResult(tl);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Connectez une session Pro pour calculer le Score Monty DVF.");
    } finally {
      setLoading(false);
    }
  }

  async function handleRunMatching() {
    setErrorMsg(null);
    setLoading(true);
    try {
      const res = await montyApi.calculateMatching({
        price: selectedProp.price,
        sector: selectedProp.sector,
        property_type: selectedProp.property_type,
        surface: selectedProp.surface,
        bedrooms: selectedProp.bedrooms,
        dpe: selectedProp.dpe,
        buyer_budget_max: buyerBudget,
        buyer_sectors: [selectedProp.sector],
        buyer_property_type: selectedProp.property_type,
        buyer_min_surface: buyerMinSurface,
        buyer_min_rooms: buyerMinRooms,
        buyer_essential_criteria: ["DPE <= C"],
      });
      setMatchResult(res);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Connectez une session Pro pour exécuter le Matching.");
    } finally {
      setLoading(false);
    }
  }

  function handleAddVisit(e: React.FormEvent) {
    e.preventDefault();
    if (!newVisitBuyer.trim() || !newVisitDate.trim()) return;
    setVisits((prev) => [
      {
        id: `vis-${Date.now()}`,
        propertyTitle: selectedProp.title,
        buyerName: newVisitBuyer.trim(),
        dateLabel: newVisitDate.trim(),
        status: "PLANIFIEE",
        feedback: "Créneau confirmé depuis le cockpit.",
      },
      ...prev,
    ]);
    setNewVisitBuyer("");
    setNewVisitDate("");
  }

  return (
    <div className="space-y-6">
      <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-5 border border-goldAntique/40 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-goldChampagne font-semibold">
            Portefeuille Biens • Score Monty DVF V2 • Matching • Visites • Transactions
          </p>
          <h2 className="font-display text-2xl font-bold mt-1">
            Pilotage Commercial &amp; Pipeline Mandats
          </h2>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          {(
            [
              ["properties", "Biens & Score Monty", Building2],
              ["matching", "Matching", GitCompareArrows],
              ["visits", "Visites", CalendarCheck],
              ["transactions", "Transactions", Handshake],
            ] as const
          ).map(([key, label, Icon]) => (
            <button
              key={key}
              type="button"
              onClick={() => setTab(key)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 min-h-[40px] rounded-xl font-semibold ${
                tab === key ? "bg-goldChampagne text-emeraldDeep" : "bg-emeraldSurface text-ivoryWarm"
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {label}
            </button>
          ))}
        </div>
      </div>

      {errorMsg && (
        <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-3 text-xs text-amber-900">
          {errorMsg}
        </div>
      )}

      {/* Sélecteur de bien actif */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {properties.map((p) => (
          <button
            key={p.id}
            type="button"
            onClick={() => setSelectedPropId(p.id)}
            className={`text-left p-4 rounded-2xl border transition ${
              selectedProp.id === p.id
                ? "bg-ivoryCard border-emeraldDeep ring-2 ring-emeraldDeep/20"
                : "bg-ivoryCard border-borderSubtle hover:border-emeraldMid"
            }`}
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-display font-bold text-sm text-emeraldDeep">{p.title}</span>
              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-emeraldDeep/10 text-emeraldDeep">
                Mandat {p.mandate}
              </span>
            </div>
            <p className="text-xs text-slateMuted mt-1">
              {p.sector} • {p.surface} m² • {p.bedrooms} ch. • DPE {p.dpe} •{" "}
              <strong className="text-obsidianInk">{p.price.toLocaleString("fr-FR")} €</strong>
            </p>
          </button>
        ))}
      </div>

      {tab === "properties" && (
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-goldAntique" />
              Score Monty DVF V2 (IQR + Pondération Ancienneté + Indice de Confiance)
            </h3>
            <button
              type="button"
              disabled={loading}
              onClick={handleRunScoreMonty}
              className="px-4 py-2.5 min-h-[44px] rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid disabled:opacity-50"
            >
              {loading ? "Analyse DVF..." : "Calculer Score Monty DVF V2"}
            </button>
          </div>

          {dvfResult && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
                <div className="rounded-xl bg-emeraldDeep text-ivoryWarm p-3.5">
                  <p className="text-ivoryWarm/70">Estimation médiane</p>
                  <p className="text-lg font-bold text-goldChampagne">
                    {dvfResult.estimated_mid_euro.toLocaleString("fr-FR")} €
                  </p>
                  <p className="text-[11px] text-ivoryWarm/70">
                    {dvfResult.estimated_low_euro.toLocaleString("fr-FR")} – {dvfResult.estimated_high_euro.toLocaleString("fr-FR")} €
                  </p>
                </div>
                <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3.5">
                  <p className="text-slateMuted">Indice de confiance</p>
                  <p className="text-lg font-bold text-emeraldDeep">{dvfResult.confidence_index} / 100</p>
                  <p className="text-[11px] text-slateMuted">{dvfResult.confidence_label}</p>
                </div>
                <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3.5">
                  <p className="text-slateMuted">Échantillon DVF retenu</p>
                  <p className="text-lg font-bold text-emeraldDeep">{dvfResult.comparables_used_count} ventes</p>
                  <p className="text-[11px] text-slateMuted">
                    {dvfResult.outliers_removed_count} aberrantes exclues (IQR)
                  </p>
                </div>
                <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3.5">
                  <p className="text-slateMuted">Fraîcheur DVF</p>
                  <p className="text-sm font-bold text-emeraldDeep">{dvfResult.data_date}</p>
                  <p className="text-[11px] text-slateMuted">
                    {dvfResult.expanded_to_neighbor_sector ? "Secteur élargi (< 15)" : "Secteur direct (≥ 15)"}
                  </p>
                </div>
              </div>
              {timelineResult && (
                <p className="text-xs rounded-xl bg-ivoryWarm border border-borderSubtle p-3">
                  <strong>Délai de vente estimé :</strong> {timelineResult.low_days} à {timelineResult.high_days} jours (probable {timelineResult.likely_days} j — {timelineResult.confidence_label}).
                </p>
              )}
            </div>
          )}
        </div>
      )}

      {tab === "matching" && (
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <GitCompareArrows className="w-4 h-4 text-goldAntique" />
            Moteur Matching Acquéreur ↔ {selectedProp.title}
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
            <label className="block">
              <span className="font-semibold text-slateMuted">Budget max acquéreur (€)</span>
              <input
                type="number"
                value={buyerBudget}
                onChange={(e) => setBuyerBudget(Number(e.target.value) || 0)}
                className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
            </label>
            <label className="block">
              <span className="font-semibold text-slateMuted">Surface min (m²)</span>
              <input
                type="number"
                value={buyerMinSurface}
                onChange={(e) => setBuyerMinSurface(Number(e.target.value) || 0)}
                className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
            </label>
            <label className="block">
              <span className="font-semibold text-slateMuted">Chambres min</span>
              <input
                type="number"
                value={buyerMinRooms}
                onChange={(e) => setBuyerMinRooms(Number(e.target.value) || 0)}
                className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
            </label>
          </div>
          <button
            type="button"
            disabled={loading}
            onClick={handleRunMatching}
            className="px-4 py-2.5 min-h-[44px] rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid disabled:opacity-50"
          >
            {loading ? "Scoring..." : "Calculer compatibilité API"}
          </button>

          {matchResult && (
            <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2 text-xs">
              <p className="text-lg font-bold text-emeraldDeep">Score de compatibilité : {matchResult.score} / 100</p>
              <p className="text-emerald-800">
                <strong>Critères validés :</strong> {matchResult.matched_criteria.join(" • ") || "Aucun"}
              </p>
              {matchResult.incompatible_criteria.length > 0 && (
                <p className="text-red-800">
                  <strong>Points de vigilance :</strong> {matchResult.incompatible_criteria.join(" • ")}
                </p>
              )}
            </div>
          )}
        </div>
      )}

      {tab === "visits" && (
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <CalendarCheck className="w-4 h-4 text-goldAntique" />
            Agenda &amp; Comptes-Rendus de Visites
          </h3>
          <form onSubmit={handleAddVisit} className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
            <input
              type="text"
              value={newVisitBuyer}
              onChange={(e) => setNewVisitBuyer(e.target.value)}
              placeholder="Nom de l'acquéreur"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <input
              type="text"
              value={newVisitDate}
              onChange={(e) => setNewVisitDate(e.target.value)}
              placeholder="Date / créneau (ex: Vendredi 14h)"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <button type="submit" className="px-3 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold">
              Planifier visite sur ce bien
            </button>
          </form>
          <div className="space-y-2 text-xs">
            {visits.map((v) => (
              <div key={v.id} className="flex flex-wrap items-center justify-between gap-2 p-3 rounded-xl bg-ivoryWarm border border-borderSubtle">
                <div>
                  <p className="font-bold text-emeraldDeep">{v.propertyTitle} — {v.buyerName}</p>
                  <p className="text-slateMuted">{v.dateLabel} • {v.feedback}</p>
                </div>
                <span className="px-2.5 py-1 rounded-full bg-emeraldDeep/10 text-emeraldDeep font-semibold">
                  {v.status}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === "transactions" && (
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-goldAntique" />
            Suivi Offres, Compromis &amp; Actes Notariés
          </h3>
          <div className="space-y-2.5 text-xs">
            {transactions.map((t) => (
              <div key={t.id} className="p-3.5 rounded-xl bg-ivoryWarm border border-borderSubtle flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-bold text-emeraldDeep">{t.propertyTitle}</p>
                  <p className="text-slateMuted">
                    Acquéreur : {t.buyerName} • Offre acceptée : <strong>{t.offerEuro.toLocaleString("fr-FR")} €</strong> • Objectif notaire : {t.notaryTargetDate}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {(["OFFRE", "COMPROMIS", "ACTE"] as const).map((st) => (
                    <button
                      key={st}
                      type="button"
                      onClick={() =>
                        setTransactions((prev) =>
                          prev.map((item) => (item.id === t.id ? { ...item, stage: st } : item))
                        )
                      }
                      className={`px-2.5 py-1 rounded-lg font-bold ${
                        t.stage === st ? "bg-emeraldDeep text-goldChampagne" : "bg-white border border-borderSubtle"
                      }`}
                    >
                      {t.stage === st && <CheckCircle2 className="w-3 h-3 inline mr-1" />}
                      {st}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
