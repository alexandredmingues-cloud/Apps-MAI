"use client";

import React, { useEffect, useState } from "react";
import {
  Building2,
  CalendarCheck,
  CheckCircle2,
  GitCompareArrows,
  Handshake,
  Plus,
  ShieldCheck,
  TrendingUp,
} from "lucide-react";
import { hasInMemoryAccessToken, montyApi } from "@/lib/api-client";
import type {
  DvfComparableInput,
  DvfValuationResult,
  MatchingCalculationResult,
  PortfolioProperty,
  PortfolioTransaction,
  PortfolioVisit,
  SaleTimelineEstimate,
  TransactionStage,
  VisitStatus,
} from "@/lib/types";

type PipelineTab = "properties" | "matching" | "visits" | "transactions";

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

const SAMPLE_VISITS: PortfolioVisit[] = [
  {
    id: "vis-1",
    property_id: "prop-lyon-t3",
    property_title: "T3 Lumineux Terrasse Part-Dieu",
    buyer_name: "Camille Laurent",
    date_label: "Jeudi 17h30",
    status: "PLANIFIEE",
    feedback: "Financement HCSF vérifié à 31,4 %.",
  },
];

const SAMPLE_TRANSACTIONS: PortfolioTransaction[] = [
  {
    id: "tx-1",
    property_id: "prop-montpellier-maison",
    property_title: "Maison Contemporaine Jardin Beaux-Arts",
    buyer_name: "Marc & Claire V.",
    offer_euro: 582000,
    stage: "COMPROMIS",
    notary_target_date: "2026-12-20",
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
  const [storageMode, setStorageMode] = useState<"api" | "local">("local");
  const [properties, setProperties] = useState<PortfolioProperty[]>(SAMPLE_PROPERTIES);
  const [selectedPropId, setSelectedPropId] = useState<string>(SAMPLE_PROPERTIES[0].id);

  // Création nouveau bien
  const [showAddProperty, setShowAddProperty] = useState(false);
  const [newPropTitle, setNewPropTitle] = useState("");
  const [newPropSector, setNewPropSector] = useState("Lyon 6e");
  const [newPropKind, setNewPropKind] = useState("Appartement");
  const [newPropPrice, setNewPropPrice] = useState(395000);
  const [newPropSurface, setNewPropSurface] = useState(74);
  const [newPropBedrooms, setNewPropBedrooms] = useState(2);
  const [newPropDpe, setNewPropDpe] = useState("C");
  const [newPropMandate, setNewPropMandate] = useState<"EXCLUSIF" | "SIMPLE">("EXCLUSIF");

  // DVF V2 & Délai de vente
  const [dvfResult, setDvfResult] = useState<DvfValuationResult | null>(null);
  const [timelineResult, setTimelineResult] = useState<(SaleTimelineEstimate & { warnings?: string[] }) | null>(null);

  // Matching Acquéreur ↔ Bien
  const [buyerBudget, setBuyerBudget] = useState(440000);
  const [buyerMinSurface, setBuyerMinSurface] = useState(65);
  const [buyerMinRooms, setBuyerMinRooms] = useState(2);
  const [matchResult, setMatchResult] = useState<MatchingCalculationResult | null>(null);

  // Visites & Transactions
  const [visits, setVisits] = useState<PortfolioVisit[]>(SAMPLE_VISITS);
  const [newVisitBuyer, setNewVisitBuyer] = useState("");
  const [newVisitDate, setNewVisitDate] = useState("");

  const [transactions, setTransactions] = useState<PortfolioTransaction[]>(SAMPLE_TRANSACTIONS);
  const [showAddTx, setShowAddTx] = useState(false);
  const [newTxBuyer, setNewTxBuyer] = useState("");
  const [newTxOffer, setNewTxOffer] = useState(390000);
  const [newTxDate, setNewTxDate] = useState("2026-12-28");

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [infoMsg, setInfoMsg] = useState<string | null>(null);

  useEffect(() => {
    setTab(initialTab);
  }, [initialTab]);

  async function refreshPortfolio() {
    if (!hasInMemoryAccessToken()) {
      setStorageMode("local");
      return;
    }
    try {
      const [pRes, vRes, tRes] = await Promise.all([
        montyApi.listPortfolioProperties(),
        montyApi.listPortfolioVisits(),
        montyApi.listPortfolioTransactions(),
      ]);
      setProperties(pRes.properties);
      if (pRes.properties.length > 0 && !pRes.properties.some((p) => p.id === selectedPropId)) {
        setSelectedPropId(pRes.properties[0].id);
      }
      setVisits(vRes.visits);
      setTransactions(tRes.transactions);
      setStorageMode("api");
    } catch {
      setStorageMode("local");
    }
  }

  useEffect(() => {
    refreshPortfolio();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const selectedProp = properties.find((p) => p.id === selectedPropId) ?? properties[0] ?? null;

  async function handleCreateProperty(e: React.FormEvent) {
    e.preventDefault();
    setErrorMsg(null);
    setInfoMsg(null);
    if (newPropTitle.trim().length < 3 || !newPropSector.trim() || newPropPrice < 10_000 || newPropSurface < 5) {
      setErrorMsg("Renseignez un titre (≥ 3 car.), un secteur, un prix (≥ 10 000 €) et une surface (≥ 5 m²).");
      return;
    }
    setLoading(true);
    try {
      if (hasInMemoryAccessToken()) {
        const created = await montyApi.createPortfolioProperty({
          title: newPropTitle.trim(),
          sector: newPropSector.trim(),
          property_type: newPropKind,
          price: newPropPrice,
          surface: newPropSurface,
          bedrooms: newPropBedrooms,
          dpe: newPropDpe,
          mandate: newPropMandate,
          docs_ratio: 0.75,
        });
        setProperties((prev) => [created, ...prev]);
        setSelectedPropId(created.id);
        setStorageMode("api");
        setInfoMsg(`Bien « ${created.title} » enregistré en base PostgreSQL RLS.`);
      } else {
        throw new Error("no-token");
      }
    } catch {
      const localCreated: PortfolioProperty = {
        id: `prop-${Date.now()}`,
        title: newPropTitle.trim(),
        sector: newPropSector.trim(),
        property_type: newPropKind,
        price: newPropPrice,
        surface: newPropSurface,
        bedrooms: newPropBedrooms,
        dpe: newPropDpe,
        mandate: newPropMandate,
        docs_ratio: 0.75,
      };
      setProperties((prev) => [localCreated, ...prev]);
      setSelectedPropId(localCreated.id);
      setInfoMsg(`Bien « ${localCreated.title} » ajouté en mode local temporaire.`);
    } finally {
      setLoading(false);
      setNewPropTitle("");
      setShowAddProperty(false);
    }
  }

  async function handleRunScoreMonty() {
    if (!selectedProp) return;
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
    if (!selectedProp) return;
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

  async function handleAddVisit(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedProp || !newVisitBuyer.trim() || !newVisitDate.trim()) return;
    setErrorMsg(null);
    setInfoMsg(null);
    try {
      if (hasInMemoryAccessToken()) {
        const created = await montyApi.createPortfolioVisit({
          property_id: selectedProp.id,
          buyer_name: newVisitBuyer.trim(),
          date_label: newVisitDate.trim(),
          feedback: "Créneau confirmé depuis le cockpit.",
        });
        setVisits((prev) => [created, ...prev]);
        setStorageMode("api");
        setInfoMsg(`Visite enregistrée pour ${created.buyer_name}.`);
      } else {
        throw new Error("no-token");
      }
    } catch {
      setVisits((prev) => [
        {
          id: `vis-${Date.now()}`,
          property_id: selectedProp.id,
          property_title: selectedProp.title,
          buyer_name: newVisitBuyer.trim(),
          date_label: newVisitDate.trim(),
          status: "PLANIFIEE",
          feedback: "Créneau confirmé depuis le cockpit.",
        },
        ...prev,
      ]);
    }
    setNewVisitBuyer("");
    setNewVisitDate("");
  }

  async function handleToggleVisitStatus(visit: PortfolioVisit) {
    const nextStatus: VisitStatus = visit.status === "PLANIFIEE" ? "REALISEE" : "PLANIFIEE";
    setVisits((prev) => prev.map((v) => (v.id === visit.id ? { ...v, status: nextStatus } : v)));
    if (!hasInMemoryAccessToken()) return;
    try {
      const updated = await montyApi.updatePortfolioVisitStatus(visit.id, { status: nextStatus });
      setVisits((prev) => prev.map((v) => (v.id === visit.id ? updated : v)));
    } catch {
      // Conserver l'état optimiste local si offline
    }
  }

  async function handleCreateTransaction(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedProp || !newTxBuyer.trim() || newTxOffer < 10_000) return;
    setErrorMsg(null);
    setInfoMsg(null);
    try {
      if (hasInMemoryAccessToken()) {
        const created = await montyApi.createPortfolioTransaction({
          property_id: selectedProp.id,
          buyer_name: newTxBuyer.trim(),
          offer_euro: newTxOffer,
          stage: "OFFRE",
          notary_target_date: newTxDate,
        });
        setTransactions((prev) => [created, ...prev]);
        setStorageMode("api");
        setInfoMsg(`Transaction créée sur « ${created.property_title} ».`);
      } else {
        throw new Error("no-token");
      }
    } catch {
      setTransactions((prev) => [
        {
          id: `tx-${Date.now()}`,
          property_id: selectedProp.id,
          property_title: selectedProp.title,
          buyer_name: newTxBuyer.trim(),
          offer_euro: newTxOffer,
          stage: "OFFRE",
          notary_target_date: newTxDate,
        },
        ...prev,
      ]);
    }
    setNewTxBuyer("");
    setShowAddTx(false);
  }

  async function handleUpdateStage(tx: PortfolioTransaction, stage: TransactionStage) {
    setTransactions((prev) => prev.map((item) => (item.id === tx.id ? { ...item, stage } : item)));
    if (!hasInMemoryAccessToken()) return;
    try {
      const updated = await montyApi.updatePortfolioTransactionStage(tx.id, stage);
      setTransactions((prev) => prev.map((item) => (item.id === tx.id ? updated : item)));
    } catch {
      // Conserver état local si API non joignable
    }
  }

  return (
    <div className="space-y-6">
      <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-5 border border-goldAntique/40 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-goldChampagne font-semibold">
            Portefeuille Biens • Score Monty DVF V2 • Matching • Visites • Transactions (
            {storageMode === "api" ? "Persistance PostgreSQL RLS" : "Mode local démo"})
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

      {infoMsg && (
        <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-4 py-3 text-xs text-emerald-950 flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0" />
          <span>{infoMsg}</span>
        </div>
      )}

      {/* Sélecteur de bien actif + bouton ajout mandat */}
      <div className="space-y-3">
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs font-bold uppercase tracking-wider text-slateMuted">
            Portefeuille actif ({properties.length} bien{properties.length > 1 ? "s" : ""})
          </p>
          <button
            type="button"
            onClick={() => setShowAddProperty((v) => !v)}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid"
          >
            <Plus className="w-3.5 h-3.5" />
            Ajouter un bien / mandat
          </button>
        </div>

        {showAddProperty && (
          <form
            onSubmit={handleCreateProperty}
            className="rounded-2xl bg-ivoryCard border border-goldAntique/40 p-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs"
          >
            <input
              type="text"
              value={newPropTitle}
              onChange={(e) => setNewPropTitle(e.target.value)}
              placeholder="Titre (ex: T3 Terrasse Confluence)"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 sm:col-span-2"
            />
            <input
              type="text"
              value={newPropSector}
              onChange={(e) => setNewPropSector(e.target.value)}
              placeholder="Secteur"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <select
              value={newPropKind}
              onChange={(e) => setNewPropKind(e.target.value)}
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="Appartement">Appartement</option>
              <option value="Maison">Maison</option>
            </select>
            <input
              type="number"
              value={newPropPrice}
              onChange={(e) => setNewPropPrice(Number(e.target.value) || 0)}
              placeholder="Prix (€)"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <input
              type="number"
              value={newPropSurface}
              onChange={(e) => setNewPropSurface(Number(e.target.value) || 0)}
              placeholder="Surface (m²)"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <input
              type="number"
              min={0}
              max={20}
              value={newPropBedrooms}
              onChange={(e) => setNewPropBedrooms(Number(e.target.value) || 0)}
              placeholder="Chambres"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <div className="flex gap-2">
              <select
                value={newPropDpe}
                onChange={(e) => setNewPropDpe(e.target.value)}
                className="w-20 rounded-xl border border-borderSubtle bg-ivoryWarm px-2 py-2"
              >
                {["A", "B", "C", "D", "E", "F", "G"].map((d) => (
                  <option key={d} value={d}>
                    DPE {d}
                  </option>
                ))}
              </select>
              <select
                value={newPropMandate}
                onChange={(e) => setNewPropMandate(e.target.value as "EXCLUSIF" | "SIMPLE")}
                className="flex-1 rounded-xl border border-borderSubtle bg-ivoryWarm px-2 py-2"
              >
                <option value="EXCLUSIF">Mandat Exclusif</option>
                <option value="SIMPLE">Mandat Simple</option>
              </select>
            </div>
            <div className="sm:col-span-2 lg:col-span-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowAddProperty(false)}
                className="px-3 py-2 rounded-xl border border-borderSubtle font-semibold"
              >
                Annuler
              </button>
              <button
                type="submit"
                disabled={loading}
                className="px-4 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold disabled:opacity-50"
              >
                Enregistrer ce bien
              </button>
            </div>
          </form>
        )}

        {properties.length === 0 ? (
          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 text-xs text-slateMuted">
            Aucun bien enregistré dans ce portefeuille d&apos;agence. Ajoutez votre premier mandat ci-dessus.
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {properties.map((p) => (
              <button
                key={p.id}
                type="button"
                onClick={() => setSelectedPropId(p.id)}
                className={`text-left p-4 rounded-2xl border transition ${
                  selectedProp?.id === p.id
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
        )}
      </div>

      {tab === "properties" && selectedProp && (
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-goldAntique" />
              Score Monty DVF V2 — {selectedProp.title}
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

      {tab === "matching" && selectedProp && (
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
            Agenda &amp; Comptes-Rendus de Visites ({visits.length})
          </h3>
          {selectedProp && (
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
          )}
          <div className="space-y-2 text-xs">
            {visits.map((v) => (
              <div key={v.id} className="flex flex-wrap items-center justify-between gap-2 p-3 rounded-xl bg-ivoryWarm border border-borderSubtle">
                <div>
                  <p className="font-bold text-emeraldDeep">{v.property_title} — {v.buyer_name}</p>
                  <p className="text-slateMuted">{v.date_label} • {v.feedback}</p>
                </div>
                <button
                  type="button"
                  onClick={() => handleToggleVisitStatus(v)}
                  className={`px-3 py-1 rounded-full font-semibold transition ${
                    v.status === "REALISEE"
                      ? "bg-emerald-100 text-emerald-900"
                      : "bg-emeraldDeep/10 text-emeraldDeep hover:bg-emeraldDeep/20"
                  }`}
                >
                  {v.status === "REALISEE" ? "REALISEE ✓" : `${v.status} (cliquer si réalisée)`}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === "transactions" && (
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-goldAntique" />
              Suivi Offres, Compromis &amp; Actes Notariés ({transactions.length})
            </h3>
            {selectedProp && (
              <button
                type="button"
                onClick={() => setShowAddTx((v) => !v)}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold"
              >
                <Plus className="w-3.5 h-3.5" />
                Nouvelle offre / transaction
              </button>
            )}
          </div>
          {showAddTx && selectedProp && (
            <form
              onSubmit={handleCreateTransaction}
              className="grid grid-cols-1 sm:grid-cols-4 gap-2 text-xs p-3 rounded-xl bg-ivoryWarm border border-borderSubtle"
            >
              <input
                type="text"
                value={newTxBuyer}
                onChange={(e) => setNewTxBuyer(e.target.value)}
                placeholder="Acquéreur (ex: Claire Moreau)"
                className="rounded-xl border border-borderSubtle bg-white px-3 py-2"
              />
              <input
                type="number"
                value={newTxOffer}
                onChange={(e) => setNewTxOffer(Number(e.target.value) || 0)}
                placeholder="Offre (€)"
                className="rounded-xl border border-borderSubtle bg-white px-3 py-2"
              />
              <input
                type="date"
                value={newTxDate}
                onChange={(e) => setNewTxDate(e.target.value)}
                className="rounded-xl border border-borderSubtle bg-white px-3 py-2"
              />
              <button type="submit" className="px-3 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold">
                Enregistrer l&apos;offre
              </button>
            </form>
          )}
          <div className="space-y-2.5 text-xs">
            {transactions.map((t) => (
              <div key={t.id} className="p-3.5 rounded-xl bg-ivoryWarm border border-borderSubtle flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-bold text-emeraldDeep">{t.property_title}</p>
                  <p className="text-slateMuted">
                    Acquéreur : {t.buyer_name} • Offre : <strong>{t.offer_euro.toLocaleString("fr-FR")} €</strong> • Objectif notaire : {t.notary_target_date || "À fixer"}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {(["OFFRE", "COMPROMIS", "ACTE"] as const).map((st) => (
                    <button
                      key={st}
                      type="button"
                      onClick={() => handleUpdateStage(t, st)}
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
