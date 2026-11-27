"use client";

import React, { useState } from "react";
import { AlertCircle, CheckCircle2, Info, SearchCheck, ShieldCheck, TrendingDown, TrendingUp } from "lucide-react";
import { montyApi } from "@/lib/api-client";
import { useMontyLocale } from "@/lib/i18n";
import type { AdemeDpeRecord, MontyPriceIndexResult, PriceIndexCategory } from "@/lib/types";

interface MontyPriceGaugeProps {
  result: MontyPriceIndexResult;
  compact?: boolean;
  onAskAiExplain?: (result: MontyPriceIndexResult) => void;
}

const CATEGORY_STYLE: Record<
  PriceIndexCategory,
  { bg: string; text: string; border: string; badgeBg: string; dot: string }
> = {
  EXCELLENTE_AFFAIRE: {
    bg: "bg-emerald-100/80",
    text: "text-emerald-950",
    border: "border-emerald-500",
    badgeBg: "bg-emerald-800 text-goldChampagne",
    dot: "#065f46",
  },
  TRES_BONNE_AFFAIRE: {
    bg: "bg-emerald-50",
    text: "text-emerald-950",
    border: "border-emerald-300",
    badgeBg: "bg-emerald-700 text-white",
    dot: "#047857",
  },
  BONNE_AFFAIRE: {
    bg: "bg-teal-50",
    text: "text-teal-950",
    border: "border-teal-300",
    badgeBg: "bg-teal-700 text-white",
    dot: "#0f766e",
  },
  PRIX_COHERENT: {
    bg: "bg-amber-50/70",
    text: "text-amber-950",
    border: "border-amber-300",
    badgeBg: "bg-emeraldDeep text-goldChampagne",
    dot: "#b45309",
  },
  AU_DESSUS_DU_MARCHE: {
    bg: "bg-orange-50",
    text: "text-orange-950",
    border: "border-orange-300",
    badgeBg: "bg-orange-600 text-white",
    dot: "#c2410c",
  },
  TROP_CHER: {
    bg: "bg-rose-50",
    text: "text-rose-950",
    border: "border-rose-300",
    badgeBg: "bg-rose-700 text-white",
    dot: "#be123c",
  },
  DONNEES_INSUFFISANTES: {
    bg: "bg-slate-50",
    text: "text-slate-700",
    border: "border-slate-200",
    badgeBg: "bg-slate-200 text-slate-700",
    dot: "#64748b",
  },
};

const ZONES: { key: PriceIndexCategory; label: string; colorClass: string }[] = [
  { key: "EXCELLENTE_AFFAIRE", label: "Excellente affaire", colorClass: "bg-emerald-800" },
  { key: "TRES_BONNE_AFFAIRE", label: "Très bonne affaire", colorClass: "bg-emerald-600" },
  { key: "BONNE_AFFAIRE", label: "Bonne affaire", colorClass: "bg-teal-500" },
  { key: "PRIX_COHERENT", label: "Prix cohérent", colorClass: "bg-amber-400" },
  { key: "AU_DESSUS_DU_MARCHE", label: "Au-dessus marché", colorClass: "bg-orange-500" },
  { key: "TROP_CHER", label: "Trop cher", colorClass: "bg-rose-600" },
];

export function MontyPriceGauge({ result, compact = false, onAskAiExplain }: MontyPriceGaugeProps) {
  const { locale, t } = useMontyLocale();
  const [expanded, setExpanded] = useState(!compact);
  const [hoveredZone, setHoveredZone] = useState<PriceIndexCategory | null>(null);
  const [ademeOpen, setAdemeOpen] = useState(false);
  const [ademeQuery, setAdemeQuery] = useState("");
  const [ademeLoading, setAdemeLoading] = useState(false);
  const [ademeRecords, setAdemeRecords] = useState<AdemeDpeRecord[]>([]);
  const [ademeWarning, setAdemeWarning] = useState<string | null>(null);

  async function handleLookupAdeme(e: React.FormEvent) {
    e.preventDefault();
    const raw = ademeQuery.trim();
    if (!raw) return;
    setAdemeLoading(true);
    setAdemeWarning(null);
    try {
      const isCp = /^\d{5}$/.test(raw);
      const isDpeNum = /^[0-9A-Z]{10,}$/i.test(raw);
      const res = await montyApi.lookupAdemeDpe({
        postal_code: isCp ? raw : undefined,
        dpe_number: isDpeNum ? raw : undefined,
        city: !isCp && !isDpeNum ? raw : undefined,
        limit: 4,
      });
      setAdemeRecords(res.records ?? []);
      if (res.warning) setAdemeWarning(res.warning);
      else if ((res.records ?? []).length === 0) setAdemeWarning("Aucun DPE public trouvé pour ce critère.");
    } catch (err) {
      setAdemeWarning(err instanceof Error ? err.message : "Vérification ADEME indisponible.");
    } finally {
      setAdemeLoading(false);
    }
  }

  if (!result.display_badge || result.category === "DONNEES_INSUFFISANTES") {
    if (compact) return null;
    return (
      <div className="rounded-xl bg-slate-50 border border-slate-200 px-3.5 py-2.5 text-xs text-slate-700 flex items-center gap-2">
        <Info className="w-4 h-4 shrink-0 text-slate-500" />
        <span>
          Monty Price Index non affiché : données comparables insuffisantes ({result.comparables.comparables_retained_count}{" "}
          références).
        </span>
      </div>
    );
  }

  const style = CATEGORY_STYLE[result.category] ?? CATEGORY_STYLE.PRIX_COHERENT;
  // Map gap_percent [-25, +25] -> [4%, 96%] on horizontal 6-zone gauge
  const clampedGap = Math.max(-25, Math.min(25, result.gap_percent));
  const markerPercent = Math.round(((clampedGap + 25) / 50) * 92 + 4);

  const confidenceBadge =
    result.confidence_tier === "HIGH"
      ? "bg-emerald-100 text-emerald-900 border-emerald-300"
      : result.confidence_tier === "MEDIUM"
      ? "bg-amber-100 text-amber-900 border-amber-300"
      : "bg-slate-100 text-slate-700 border-slate-300";

  const activeZoneDesc = (zone: PriceIndexCategory | null) => {
    const th = result.thresholds;
    const exc = Math.abs(th.excellent_deal_pct ?? -15);
    switch (zone) {
      case "EXCELLENTE_AFFAIRE":
        return `Prix < -${exc} % sous la valeur estimée`;
      case "TRES_BONNE_AFFAIRE":
        return `Entre -${exc} % et -${Math.abs(th.very_good_deal_pct)} % sous la valeur estimée`;
      case "BONNE_AFFAIRE":
        return `Entre -${Math.abs(th.very_good_deal_pct)} % et -${Math.abs(th.good_deal_pct)} % sous le marché`;
      case "PRIX_COHERENT":
        return `Zone d'équilibre (-${Math.abs(th.good_deal_pct)} % à +${th.above_market_pct} %)`;
      case "AU_DESSUS_DU_MARCHE":
        return `Entre +${th.above_market_pct} % et +${th.overpriced_pct} % au-dessus du marché`;
      case "TROP_CHER":
        return `Prix supérieur de plus de +${th.overpriced_pct} % à l'estimation`;
      default:
        return null;
    }
  };

  if (compact && !expanded) {
    return (
      <div className="space-y-1.5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => setExpanded(true)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold ${style.badgeBg} hover:opacity-95 transition`}
            title="Cliquer pour ouvrir la jauge Monty Price Index"
          >
            {result.gap_percent <= 0 ? (
              <TrendingDown className="w-3 h-3" />
            ) : (
              <TrendingUp className="w-3 h-3" />
            )}
            <span>{result.category_label}</span>
            <span className="opacity-90 font-mono">
              ({result.gap_percent > 0 ? `+${result.gap_percent}` : result.gap_percent}%)
            </span>
          </button>
          <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${confidenceBadge}`}>
            {result.confidence_label}
          </span>
        </div>
        {/* Mini barre 5 zones */}
        <button
          type="button"
          onClick={() => setExpanded(true)}
          aria-label="Afficher le détail Monty Price Index"
          className="w-full relative h-2 rounded-full overflow-hidden flex bg-slate-200"
        >
          {ZONES.map((z) => (
            <span key={z.key} className={`flex-1 h-full ${z.colorClass} opacity-85`} />
          ))}
          <span
            className="absolute top-0 bottom-0 w-1.5 bg-obsidianInk ring-2 ring-white rounded-full -translate-x-1/2"
            style={{ left: `${markerPercent}%` }}
          />
        </button>
      </div>
    );
  }

  return (
    <div className={`rounded-2xl border p-4 space-y-3 text-xs ${style.bg} ${style.border}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="space-y-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full font-bold text-[11px] ${style.badgeBg}`}>
              {result.gap_percent <= 0 ? (
                <TrendingDown className="w-3.5 h-3.5" />
              ) : (
                <TrendingUp className="w-3.5 h-3.5" />
              )}
              {result.category_label}
            </span>
            <span className={`px-2 py-0.5 rounded-full border text-[10px] font-bold inline-flex items-center gap-1 ${confidenceBadge}`}>
              <ShieldCheck className="w-3 h-3" />
              {result.confidence_label} ({result.reliability_score}/100)
            </span>
          </div>
          <p className={`font-bold text-sm pt-1 ${style.text}`}>
            Monty Price Index : {result.gap_percent > 0 ? `+${result.gap_percent}%` : `${result.gap_percent}%`} (
            {result.gap_euro > 0
              ? `+${result.gap_euro.toLocaleString("fr-FR")} €`
              : `${result.gap_euro.toLocaleString("fr-FR")} €`}{" "}
            vs estimation {result.estimated_market_value.toLocaleString("fr-FR")} €)
          </p>
        </div>

        {compact && (
          <button
            type="button"
            onClick={() => setExpanded(false)}
            className="text-[11px] font-bold underline text-obsidianInk/80 hover:text-obsidianInk"
          >
            Réduire
          </button>
        )}
      </div>

      {/* Jauge interactive 5 zones */}
      <div className="space-y-1.5">
        <div className="relative pt-2 pb-1">
          <div className="h-3.5 rounded-full overflow-hidden flex shadow-inner border border-black/10">
            {ZONES.map((z) => {
              const active = result.category === z.key || hoveredZone === z.key;
              return (
                <button
                  key={z.key}
                  type="button"
                  onMouseEnter={() => setHoveredZone(z.key)}
                  onMouseLeave={() => setHoveredZone(null)}
                  onClick={() => setHoveredZone((prev) => (prev === z.key ? null : z.key))}
                  title={z.label}
                  className={`flex-1 h-full transition-opacity ${z.colorClass} ${
                    active ? "opacity-100" : "opacity-65"
                  }`}
                />
              );
            })}
          </div>
          {/* Curseur position du bien */}
          <div
            className="absolute top-0.5 -translate-x-1/2 flex flex-col items-center pointer-events-none"
            style={{ left: `${markerPercent}%` }}
          >
            <span className="h-5 w-2 rounded-full bg-obsidianInk border-2 border-white shadow" />
          </div>
        </div>
        <div className="flex justify-between text-[10px] font-semibold text-obsidianInk/75">
          <span>Excellente affaire</span>
          <span>Prix cohérent</span>
          <span>Trop cher</span>
        </div>
        {hoveredZone && (
          <p className="text-[11px] font-semibold text-emeraldDeep bg-white/80 rounded-lg px-2.5 py-1 border border-black/10">
            {activeZoneDesc(hoveredZone)}
          </p>
        )}
      </div>

      {/* Chiffres clés & comparables */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[11px]">
        <div className="rounded-xl bg-white/85 border border-black/10 p-2.5">
          <p className="text-slateMuted">Prix demandé</p>
          <p className="font-bold text-obsidianInk">{result.asking_price_euro.toLocaleString("fr-FR")} €</p>
          <p className="text-slateMuted">{result.asking_price_m2.toLocaleString("fr-FR")} €/m²</p>
        </div>
        <div className="rounded-xl bg-white/85 border border-black/10 p-2.5">
          <p className="text-slateMuted">Valeur estimée</p>
          <p className="font-bold text-emeraldDeep">{result.estimated_market_value.toLocaleString("fr-FR")} €</p>
          <p className="text-slateMuted">{result.estimated_price_m2.toLocaleString("fr-FR")} €/m²</p>
        </div>
        <div className="rounded-xl bg-white/85 border border-black/10 p-2.5">
          <p className="text-slateMuted">Fourchette marché</p>
          <p className="font-bold text-obsidianInk">
            {result.price_range_low.toLocaleString("fr-FR")} – {result.price_range_high.toLocaleString("fr-FR")} €
          </p>
          <p className="text-slateMuted">Dispersion CV {(result.comparables.dispersion_cv * 100).toFixed(1)} %</p>
        </div>
        <div className="rounded-xl bg-white/85 border border-black/10 p-2.5">
          <p className="text-slateMuted">Comparables retenus</p>
          <p className="font-bold text-emeraldDeep">
            {result.comparables.comparables_retained_count} / {result.comparables.comparables_found_count}
          </p>
          <p className="text-slateMuted">{result.comparables.outliers_removed_count} aberrants exclus</p>
        </div>
      </div>

      {result.adjustment_factors.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {result.adjustment_factors.map((adj) => (
            <span
              key={`${adj.factor}-${adj.impact}`}
              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-white/90 border border-black/10 text-[11px] font-medium text-obsidianInk"
            >
              <CheckCircle2 className="w-3 h-3 text-emeraldDeep" />
              {adj.factor} : <strong>{adj.impact}</strong>
            </span>
          ))}
        </div>
      )}

      <p className="text-[11px] text-obsidianInk/90 leading-relaxed bg-white/75 rounded-xl p-2.5 border border-black/10">
        {result.explanation_grounded}
      </p>

      <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] text-slateMuted">
        <span className="inline-flex items-center gap-1">
          <AlertCircle className="w-3 h-3" />
          Source : {result.comparables.source_label} • Données {result.comparables.data_date || "locales"}
          {result.calculated_at
            ? ` • ${t.priceIndexCalculatedAt} ${new Date(result.calculated_at).toLocaleDateString(locale)}`
            : ""}
        </span>
        <div className="flex flex-wrap items-center gap-1.5">
          <button
            type="button"
            onClick={() => setAdemeOpen((v) => !v)}
            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border border-emeraldDeep/30 bg-white text-emeraldDeep font-bold hover:bg-ivoryWarm"
          >
            <SearchCheck className="w-3 h-3" />
            Vérifier DPE ADEME
          </button>
          {onAskAiExplain && (
            <button
              type="button"
              onClick={() => onAskAiExplain(result)}
              className="px-2.5 py-1 rounded-lg bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid"
            >
              Demander un argumentaire Monty AI
            </button>
          )}
        </div>
      </div>

      {ademeOpen && (
        <div className="rounded-xl bg-white/90 border border-black/10 p-3 space-y-2 text-[11px]">
          <form onSubmit={handleLookupAdeme} className="flex flex-wrap gap-2">
            <input
              type="text"
              value={ademeQuery}
              onChange={(e) => setAdemeQuery(e.target.value)}
              placeholder="N° DPE (13 car.), code postal (34000) ou ville..."
              className="flex-1 min-w-[180px] rounded-lg border border-borderSubtle px-2.5 py-1.5 text-xs"
            />
            <button
              type="submit"
              disabled={ademeLoading}
              className="px-3 py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne font-bold disabled:opacity-50"
            >
              {ademeLoading ? "Recherche..." : "Interroger ADEME"}
            </button>
          </form>
          {ademeWarning && <p className="text-amber-800 font-medium">{ademeWarning}</p>}
          {ademeRecords.length > 0 && (
            <div className="space-y-1">
              {ademeRecords.map((rec) => (
                <div
                  key={`${rec.dpe_number}-${rec.issued_date}`}
                  className="rounded-lg bg-ivoryWarm px-2.5 py-1.5 border border-borderSubtle flex flex-wrap justify-between gap-2"
                >
                  <span>
                    <strong>DPE {rec.dpe_label}</strong> / GES {rec.ges_label} • {rec.city} ({rec.postal_code})
                    {rec.surface_m2 ? ` • ${rec.surface_m2} m²` : ""}
                  </span>
                  <span className="font-mono text-slateMuted">
                    {rec.dpe_number || rec.issued_date}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <p className="text-[10px] text-slateMuted/90 leading-snug border-t border-black/10 pt-2">
        {result.estimation_disclaimer || t.priceIndexDisclaimerFallback}
      </p>
    </div>
  );
}
