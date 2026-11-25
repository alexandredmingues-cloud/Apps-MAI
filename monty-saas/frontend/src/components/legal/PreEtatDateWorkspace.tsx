"use client";

import React, { useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Download,
  FileCheck2,
  Lock,
  Plus,
  Printer,
  ShieldAlert,
  Trash2,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type { CoproLotPayload, PreEtatDateComputeResult, PreEtatDatePayload } from "@/lib/types";

const INITIAL_LOTS: CoproLotPayload[] = [
  { lot_number: "14", nature: "Appartement T3", tantiemes_generaux: 420, confidence: 0.96, validated: true },
  { lot_number: "42", nature: "Cave", tantiemes_generaux: 14, confidence: 0.72, validated: false },
  { lot_number: "88", nature: "Parking sous-sol", tantiemes_generaux: 31, confidence: 0.94, validated: true },
];

export function PreEtatDateWorkspace() {
  const [dossierRef, setDossierRef] = useState("DOS-2026-LYON-14");
  const [sellerName, setSellerName] = useState("Camille & Thomas Delacroix");
  const [propertyAddress, setPropertyAddress] = useState("14 Rue Garibaldi, 69006 Lyon");
  const [syndicName, setSyndicName] = useState("Syndic Rhône & Saône");
  const [totalTantiemes, setTotalTantiemes] = useState(10000);
  const [lots, setLots] = useState<CoproLotPayload[]>(INITIAL_LOTS);

  const [annualBudget, setAnnualBudget] = useState(64000);
  const [chargesN1, setChargesN1] = useState(2976);
  const [chargesN2, setChargesN2] = useState(2810);
  const [worksVoted, setWorksVoted] = useState(950);
  const [sellerUnpaid, setSellerUnpaid] = useState(0);
  const [alurBalance, setAlurBalance] = useState(980);
  const [quarterCalled, setQuarterCalled] = useState(744);
  const [saleDate, setSaleDate] = useState("2026-06-15");
  const [customSyndicFee, setCustomSyndicFee] = useState(420);

  // Confiance extraction PDF (simulation réaliste modifiable)
  const [budgetConfidence, setBudgetConfidence] = useState(0.76);
  const [validatedFields, setValidatedFields] = useState<string[]>([]);

  // Checklist L. 721-2 CCH (8 pièces obligatoires)
  const [checklist, setChecklist] = useState({
    has_fiche_synthetique: true,
    has_reglement_copro_edd: true,
    has_pv_ag_3_years: true,
    has_carnet_entretien: true,
    has_dtg_or_pppt: true,
    has_financial_annex: true,
    has_pending_procedures_statement: true,
    has_alur_works_fund_statement: true,
  });

  const [result, setResult] = useState<PreEtatDateComputeResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  function buildPayload(): PreEtatDatePayload {
    return {
      dossier_ref: dossierRef.trim(),
      seller_name: sellerName.trim(),
      property_address: propertyAddress.trim(),
      syndic_name: syndicName.trim(),
      total_tantiemes_copro: totalTantiemes,
      lots,
      annual_budget_copro_euro: annualBudget,
      charges_n_minus_1_lot_euro: chargesN1,
      charges_n_minus_2_lot_euro: chargesN2,
      non_budget_works_voted_euro: worksVoted,
      seller_unpaid_charges_euro: sellerUnpaid,
      works_fund_alur_balance_euro: alurBalance,
      current_quarter_called_euro: quarterCalled,
      sale_date: saleDate,
      quarter_start_date: "2026-04-01",
      quarter_end_date: "2026-06-30",
      custom_syndic_fee_euro: customSyndicFee,
      ...checklist,
      confidence_map: {
        annual_budget_copro_euro: budgetConfidence,
      },
      validated_fields: validatedFields,
    };
  }

  async function handleCompute() {
    setErrorMsg(null);
    setSuccessMsg(null);
    if (!dossierRef.trim() || !sellerName.trim() || lots.length === 0) {
      setErrorMsg("Renseignez la référence dossier, le vendeur et au moins un lot.");
      return;
    }
    setLoading(true);
    try {
      const computed = await montyApi.computePreEtatDate(buildPayload());
      setResult(computed);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Connectez une session Pro pour calculer le Pré-État Daté.");
    } finally {
      setLoading(false);
    }
  }

  async function handleExportHtml(printMode: boolean) {
    setErrorMsg(null);
    setSuccessMsg(null);
    setExporting(true);
    try {
      const exported = await montyApi.exportPreEtatDateHtml(buildPayload());
      if (printMode && typeof window !== "undefined") {
        const w = window.open("", "_blank");
        if (w) {
          w.document.write(exported.html_content);
          w.document.close();
          w.focus();
          w.print();
        }
      } else if (typeof window !== "undefined") {
        const blob = new Blob([exported.html_content], { type: "text/html;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = exported.filename;
        link.click();
        URL.revokeObjectURL(url);
      }
      setSuccessMsg(`Export ${exported.document_version} généré (${exported.generated_at_iso}).`);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Export bloqué : vérifiez les champs douteux et la checklist L.721-2.");
    } finally {
      setExporting(false);
    }
  }

  function toggleFieldValidation(key: string) {
    if (key.startsWith("lot:")) {
      const lotNum = key.replace("lot:", "");
      setLots((prev) =>
        prev.map((l) => (l.lot_number === lotNum ? { ...l, validated: !l.validated } : l))
      );
      return;
    }
    setValidatedFields((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]
    );
  }

  return (
    <div className="space-y-6">
      <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-5 border border-goldAntique/40 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-goldChampagne font-semibold">
            Conformité Loi ALUR • Art. L. 721-2 CCH
          </p>
          <h2 className="font-display text-2xl font-bold mt-1">
            Pré-État Daté Multi-Lots &amp; Contrôle Documentaire
          </h2>
          <p className="text-xs text-ivoryWarm/80 mt-1">
            Calcul automatique des tantièmes, prorata temporis (Art. 6-2), fonds ALUR et blocage automatique si pièce manquante ou champ &lt; 80 % non validé.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={loading}
            onClick={handleCompute}
            className="px-4 py-2.5 min-h-[44px] rounded-xl bg-goldChampagne text-emeraldDeep font-bold text-xs hover:opacity-90 disabled:opacity-50"
          >
            {loading ? "Calcul en cours..." : "Calculer & Contrôler"}
          </button>
          <button
            type="button"
            disabled={exporting || !result?.export_allowed}
            onClick={() => handleExportHtml(false)}
            className="inline-flex items-center gap-1.5 px-3 py-2.5 min-h-[44px] rounded-xl bg-emeraldSurface border border-goldAntique/40 text-ivoryWarm font-semibold text-xs disabled:opacity-40"
          >
            <Download className="w-3.5 h-3.5" />
            Exporter HTML/PDF
          </button>
          <button
            type="button"
            disabled={exporting || !result?.export_allowed}
            onClick={() => handleExportHtml(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2.5 min-h-[44px] rounded-xl bg-emeraldSurface border border-goldAntique/40 text-ivoryWarm font-semibold text-xs disabled:opacity-40"
          >
            <Printer className="w-3.5 h-3.5" />
            Imprimer A4
          </button>
        </div>
      </div>

      {errorMsg && (
        <div className="rounded-xl bg-red-50 border border-red-300 px-4 py-3 text-xs text-red-900 flex items-center gap-2">
          <ShieldAlert className="w-4 h-4 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}
      {successMsg && (
        <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-4 py-3 text-xs text-emerald-900 flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Colonne 1 & 2 : Saisie Dossier, Lots & Finances */}
        <div className="lg:col-span-2 space-y-5">
          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
            <h3 className="font-display font-bold text-base text-emeraldDeep">
              1. Identification Copropriété &amp; Lots
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <label className="block">
                <span className="font-semibold text-slateMuted">Référence dossier</span>
                <input
                  type="text"
                  value={dossierRef}
                  onChange={(e) => setDossierRef(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Vendeur</span>
                <input
                  type="text"
                  value={sellerName}
                  onChange={(e) => setSellerName(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block sm:col-span-2">
                <span className="font-semibold text-slateMuted">Adresse du bien</span>
                <input
                  type="text"
                  value={propertyAddress}
                  onChange={(e) => setPropertyAddress(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Syndic</span>
                <input
                  type="text"
                  value={syndicName}
                  onChange={(e) => setSyndicName(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Tantièmes totaux immeuble</span>
                <input
                  type="number"
                  min={1}
                  value={totalTantiemes}
                  onChange={(e) => setTotalTantiemes(Number(e.target.value) || 1)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-emeraldDeep">Lots cédés</span>
                <button
                  type="button"
                  onClick={() =>
                    setLots((prev) => [
                      ...prev,
                      { lot_number: String(prev.length + 101), nature: "Lot annexe", tantiemes_generaux: 10, confidence: 0.95, validated: true },
                    ])
                  }
                  className="inline-flex items-center gap-1 text-xs font-semibold text-emeraldDeep hover:underline"
                >
                  <Plus className="w-3.5 h-3.5" /> Ajouter un lot
                </button>
              </div>
              {lots.map((lot, idx) => (
                <div key={`${lot.lot_number}-${idx}`} className="grid grid-cols-12 gap-2 items-center text-xs bg-ivoryWarm p-2.5 rounded-xl border border-borderSubtle">
                  <input
                    type="text"
                    value={lot.lot_number}
                    onChange={(e) =>
                      setLots((p) => p.map((item, i) => (i === idx ? { ...item, lot_number: e.target.value } : item)))
                    }
                    placeholder="N°"
                    className="col-span-2 rounded-lg border border-borderSubtle bg-white px-2 py-1.5"
                  />
                  <input
                    type="text"
                    value={lot.nature}
                    onChange={(e) =>
                      setLots((p) => p.map((item, i) => (i === idx ? { ...item, nature: e.target.value } : item)))
                    }
                    placeholder="Nature"
                    className="col-span-5 rounded-lg border border-borderSubtle bg-white px-2 py-1.5"
                  />
                  <input
                    type="number"
                    min={1}
                    value={lot.tantiemes_generaux}
                    onChange={(e) =>
                      setLots((p) =>
                        p.map((item, i) => (i === idx ? { ...item, tantiemes_generaux: Number(e.target.value) || 1 } : item))
                      )
                    }
                    className="col-span-3 rounded-lg border border-borderSubtle bg-white px-2 py-1.5"
                  />
                  <button
                    type="button"
                    disabled={lots.length <= 1}
                    onClick={() => setLots((p) => p.filter((_, i) => i !== idx))}
                    aria-label="Supprimer le lot"
                    className="col-span-2 flex justify-center text-red-700 disabled:opacity-30"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
            <h3 className="font-display font-bold text-base text-emeraldDeep">
              2. Données Financières Copropriété &amp; Prorata
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
              <label className="block">
                <span className="font-semibold text-slateMuted">Budget annuel copro (€)</span>
                <input
                  type="number"
                  value={annualBudget}
                  onChange={(e) => setAnnualBudget(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Charges lot N-1 (€)</span>
                <input
                  type="number"
                  value={chargesN1}
                  onChange={(e) => setChargesN1(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Charges lot N-2 (€)</span>
                <input
                  type="number"
                  value={chargesN2}
                  onChange={(e) => setChargesN2(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Fonds travaux ALUR (€)</span>
                <input
                  type="number"
                  value={alurBalance}
                  onChange={(e) => setAlurBalance(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Travaux votés non appelés (€)</span>
                <input
                  type="number"
                  value={worksVoted}
                  onChange={(e) => setWorksVoted(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Impayés vendeur (€)</span>
                <input
                  type="number"
                  value={sellerUnpaid}
                  onChange={(e) => setSellerUnpaid(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Appel trimestre en cours (€)</span>
                <input
                  type="number"
                  value={quarterCalled}
                  onChange={(e) => setQuarterCalled(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block">
                <span className="font-semibold text-slateMuted">Date acte prévue</span>
                <input
                  type="date"
                  value={saleDate}
                  onChange={(e) => setSaleDate(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block col-span-2">
                <span className="font-semibold text-slateMuted">Honoraires syndic économisés (personnalisable €)</span>
                <input
                  type="number"
                  value={customSyndicFee}
                  onChange={(e) => setCustomSyndicFee(Number(e.target.value) || 0)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
              <label className="block col-span-2">
                <span className="font-semibold text-slateMuted">Confiance OCR Budget (test seuil 80 %)</span>
                <input
                  type="number"
                  step="0.05"
                  min={0.1}
                  max={1}
                  value={budgetConfidence}
                  onChange={(e) => setBudgetConfidence(Number(e.target.value) || 0.76)}
                  className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                />
              </label>
            </div>
          </div>
        </div>

        {/* Colonne 3 : Checklist Art. L. 721-2 CCH */}
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 h-fit">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <FileCheck2 className="w-4 h-4 text-goldAntique" />
            Checklist Art. L. 721-2 CCH
          </h3>
          <p className="text-[11px] text-slateMuted">
            Toute pièce manquante bloque réglementairement l&apos;export final du Pré-État Daté.
          </p>
          <div className="space-y-2 text-xs">
            {(
              [
                ["has_fiche_synthetique", "Fiche synthétique copropriété"],
                ["has_reglement_copro_edd", "Règlement de copro & EDD"],
                ["has_pv_ag_3_years", "PV des 3 dernières AG"],
                ["has_carnet_entretien", "Carnet d'entretien"],
                ["has_dtg_or_pppt", "DTG / PPPT"],
                ["has_financial_annex", "État financier charges N-1 / N-2"],
                ["has_pending_procedures_statement", "État procédures en cours"],
                ["has_alur_works_fund_statement", "Attestation fonds travaux ALUR"],
              ] as const
            ).map(([key, label]) => (
              <label key={key} className="flex items-center gap-2 p-2 rounded-xl bg-ivoryWarm border border-borderSubtle cursor-pointer">
                <input
                  type="checkbox"
                  checked={checklist[key]}
                  onChange={(e) => setChecklist((c) => ({ ...c, [key]: e.target.checked }))}
                  className="rounded border-emeraldDeep text-emeraldDeep"
                />
                <span>{label}</span>
              </label>
            ))}
          </div>
        </div>
      </div>

      {/* Résultat calcul + champs douteux */}
      {result && (
        <div className="space-y-4">
          {!result.export_allowed && (
            <div className="rounded-2xl bg-amber-50 border border-amber-400 p-4 space-y-2 text-xs text-amber-950">
              <p className="font-bold flex items-center gap-1.5 text-sm">
                <Lock className="w-4 h-4 text-amber-700" />
                Export bloqué jusqu&apos;à validation :
              </p>
              <ul className="list-disc pl-5 space-y-1">
                {result.blocking_reasons.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-4">
              <p className="text-[11px] text-ivoryWarm/70">Quote-part tantièmes</p>
              <p className="text-xl font-bold text-goldChampagne">{result.tantiemes_share_percent.toFixed(2)} %</p>
              <p className="text-[11px] text-ivoryWarm/70 mt-0.5">{result.total_lot_tantiemes} tantièmes</p>
            </div>
            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-4">
              <p className="text-[11px] text-slateMuted">Remboursement prorata acquéreur</p>
              <p className="text-xl font-bold text-emeraldDeep">{result.buyer_quarter_reimbursement_euro.toFixed(2)} €</p>
              <p className="text-[11px] text-slateMuted mt-0.5">
                {result.prorata_days_seller} / {result.prorata_days_total_quarter} j vendeur
              </p>
            </div>
            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-4">
              <p className="text-[11px] text-slateMuted">Solde net estimé vendeur</p>
              <p className="text-xl font-bold text-emeraldDeep">{result.seller_net_balance_due_euro.toFixed(2)} €</p>
              <p className="text-[11px] text-slateMuted mt-0.5">Fonds ALUR : {result.works_fund_alur_attached_euro.toFixed(2)} €</p>
            </div>
            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-4">
              <p className="text-[11px] text-slateMuted">Économie syndic indicative</p>
              <p className="text-xl font-bold text-emeraldDeep">{result.estimated_syndic_savings_euro.toFixed(0)} € TTC</p>
              <p className="text-[10px] text-slateMuted mt-0.5">Étude tarifs syndics 2019</p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3">
              <h4 className="font-display font-bold text-sm text-emeraldDeep">
                Contrôle de Confiance Extraction (&lt; 80 % = validation manuelle obligatoire)
              </h4>
              <div className="space-y-2 text-xs">
                {result.extracted_fields.map((f) => (
                  <div
                    key={f.field_key}
                    className={`flex items-center justify-between gap-2 p-2.5 rounded-xl border ${
                      f.is_doubtful && !f.validated
                        ? "bg-amber-50 border-amber-400 text-amber-950"
                        : "bg-ivoryWarm border-borderSubtle"
                    }`}
                  >
                    <div>
                      <p className="font-semibold flex items-center gap-1.5">
                        {f.is_doubtful && <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />}
                        {f.label} : <span className="text-emeraldDeep">{f.value_preview}</span>
                      </p>
                      <p className="text-[11px] text-slateMuted">Confiance : {Math.round(f.confidence * 100)} %</p>
                    </div>
                    {f.is_doubtful && (
                      <button
                        type="button"
                        onClick={() => {
                          toggleFieldValidation(f.field_key);
                        }}
                        className={`px-3 py-1.5 rounded-lg font-bold text-[11px] ${
                          f.validated
                            ? "bg-emeraldDeep text-goldChampagne"
                            : "bg-amber-500 text-white hover:bg-amber-600"
                        }`}
                      >
                        {f.validated ? "Validé ✓" : "Valider ce champ"}
                      </button>
                    )}
                  </div>
                ))}
              </div>
              <p className="text-[11px] text-slateMuted">
                Après validation d&apos;un champ douteux, cliquez sur « Calculer &amp; Contrôler » pour débloquer l&apos;export.
              </p>
            </div>

            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3">
              <h4 className="font-display font-bold text-sm text-emeraldDeep">
                Détail étape par étape ({result.document_version})
              </h4>
              <ul className="space-y-2 text-xs text-obsidianInk">
                {result.calculation_steps.map((s, idx) => (
                  <li key={idx} className="p-2 rounded-xl bg-ivoryWarm border border-borderSubtle">
                    {s}
                  </li>
                ))}
              </ul>
              <p className="text-[11px] text-slateMuted italic">{result.savings_source_note}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
