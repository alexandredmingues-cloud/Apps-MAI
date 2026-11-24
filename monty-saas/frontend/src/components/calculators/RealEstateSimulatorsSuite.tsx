"use client";

import React, { useState } from "react";
import {
  AlertTriangle,
  Building2,
  Calculator,
  CheckCircle2,
  Coins,
  FileSpreadsheet,
  Globe2,
  Home,
  Percent,
  TrendingUp,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import { COUNTRY_PACKS } from "@/lib/country-packs";
import {
  FRENCH_SECTOR_BENCHMARKS,
  computeCapitalGainLocal,
  computeHcsfLocal,
  computeIndicativeEstimationLocal,
  computeMonthlyPaymentLocal,
  computeNotaryFeesLocal,
  computePortugalAcquisitionLocal,
  computeRentalYieldLocal,
} from "@/lib/real-estate-calculators";
import type {
  CapitalGainResult,
  CountryCode,
  HcsfResult,
  IndicativeEstimationResult,
  MonthlyPaymentResult,
  NotaryFeesResult,
  PortugalAcquisitionCostResult,
  RentalYieldResult,
} from "@/lib/types";

type SimTab = "monthly" | "hcsf" | "notary" | "yield" | "estimation" | "capital_gain";

interface Props {
  initialCountry?: CountryCode;
}

export function RealEstateSimulatorsSuite({ initialCountry = "FR" }: Props) {
  const [country, setCountry] = useState<CountryCode>(initialCountry);
  const [tab, setTab] = useState<SimTab>("monthly");
  const [sourceBadge, setSourceBadge] = useState<"API FastAPI" | "Moteur Déterministe Local">("Moteur Déterministe Local");

  // 1. Mensualités
  const [price, setPrice] = useState(310000);
  const [downPayment, setDownPayment] = useState(45000);
  const [ratePct, setRatePct] = useState(3.35);
  const [insPct, setInsPct] = useState(0.32);
  const [years, setYears] = useState(25);
  const [monthlyIncome, setMonthlyIncome] = useState(5400);
  const [monthlyRes, setMonthlyRes] = useState<MonthlyPaymentResult>(() =>
    computeMonthlyPaymentLocal({
      price: 310000,
      down_payment: 45000,
      notary_rate_percent: 7.8,
      duration_years: 25,
      interest_rate_percent: 3.35,
      insurance_rate_percent: 0.32,
      monthly_income: 5400,
      existing_credits: 0,
      monthly_charges: 180,
    })
  );

  // 2. HCSF 35%
  const [hcsfIncome, setHcsfIncome] = useState(6400);
  const [hcsfExisting, setHcsfExisting] = useState(0);
  const [hcsfDown, setHcsfDown] = useState(75000);
  const [hcsfYears, setHcsfYears] = useState(25);
  const [hcsfNeuf, setHcsfNeuf] = useState(false);
  const [hcsfRes, setHcsfRes] = useState<HcsfResult>(() =>
    computeHcsfLocal({
      monthly_net_income: 6400,
      existing_monthly_credits: 0,
      down_payment: 75000,
      duration_years: 25,
      interest_rate_percent: 3.35,
      insurance_rate_percent: 0.32,
      notary_rate_percent: 7.8,
      is_vefa_neuf: false,
    })
  );

  // 3. Frais de notaire FR / IMT PT
  const [notaryPrice, setNotaryPrice] = useState(320000);
  const [notaryNeuf, setNotaryNeuf] = useState(false);
  const [notaryDept, setNotaryDept] = useState("75");
  const [notaryRes, setNotaryRes] = useState<NotaryFeesResult>(() =>
    computeNotaryFeesLocal({
      property_price: 320000,
      is_neuf: false,
      department_code: "75",
      use_increased_dmto_if_applicable: true,
    })
  );

  const [ptPrice, setPtPrice] = useState(290000);
  const [ptLoan, setPtLoan] = useState(230000);
  const [ptHpp, setPtHpp] = useState(true);
  const [ptJovem, setPtJovem] = useState(true);
  const [ptRes, setPtRes] = useState<PortugalAcquisitionCostResult>(() =>
    computePortugalAcquisitionLocal({
      property_price_euro: 290000,
      loan_amount_euro: 230000,
      is_primary_residence_hpp: true,
      is_imt_jovem_eligible: true,
    })
  );

  // 4. Rendement locatif
  const [rentPrice, setRentPrice] = useState(195000);
  const [rentWorks, setRentWorks] = useState(12000);
  const [rentMonthly, setRentMonthly] = useState(980);
  const [rentLoan, setRentLoan] = useState(840);
  const [rentDpe, setRentDpe] = useState("C");
  const [yieldRes, setYieldRes] = useState<RentalYieldResult>(() =>
    computeRentalYieldLocal({
      purchase_price: 195000,
      notary_fees: 15200,
      works_cost: 12000,
      monthly_rent_hc: 980,
      vacancy_months_per_year: 0.5,
      non_recoverable_charges_year: 650,
      property_tax_year: 950,
      management_fee_percent: 6.5,
      insurance_pno_gli_year: 390,
      monthly_loan_payment: 840,
      dpe: "C",
    })
  );

  // 5. Estimation indicative
  const [estSector, setEstSector] = useState(FRENCH_SECTOR_BENCHMARKS[1].name);
  const [estKind, setEstKind] = useState("Appartement");
  const [estSurface, setEstSurface] = useState(68);
  const [estDpe, setEstDpe] = useState("C");
  const [estCond, setEstCond] = useState("Bon état");
  const [estExt, setEstExt] = useState(true);
  const [estRes, setEstRes] = useState<IndicativeEstimationResult>(() =>
    computeIndicativeEstimationLocal({
      sector_name: FRENCH_SECTOR_BENCHMARKS[1].name,
      property_kind: "Appartement",
      surface_m2: 68,
      dpe: "C",
      condition_label: "Bon état",
      has_terrace_or_garden: true,
      has_parking: true,
    })
  );

  // 6. Plus-value immobilière
  const [pvBuy, setPvBuy] = useState(210000);
  const [pvSell, setPvSell] = useState(330000);
  const [pvYears, setPvYears] = useState(11);
  const [pvPrimary, setPvPrimary] = useState(false);
  const [pvRes, setPvRes] = useState<CapitalGainResult>(() =>
    computeCapitalGainLocal({
      purchase_price: 210000,
      sale_price: 330000,
      holding_years: 11,
      is_primary_residence: false,
      use_flat_acquisition_7_5_pct: true,
      use_flat_works_15_pct: true,
    })
  );

  async function runMonthly() {
    const payload = {
      price,
      down_payment: downPayment,
      notary_rate_percent: country === "PT" ? 4.5 : 7.8,
      duration_years: years,
      interest_rate_percent: ratePct,
      insurance_rate_percent: insPct,
      monthly_income: monthlyIncome,
      existing_credits: 0,
      monthly_charges: 180,
    };
    try {
      const apiRes = await montyApi.calculatePublicMonthlyPayment(payload);
      setMonthlyRes(apiRes);
      setSourceBadge("API FastAPI");
    } catch {
      setMonthlyRes(computeMonthlyPaymentLocal(payload));
      setSourceBadge("Moteur Déterministe Local");
    }
  }

  async function runHcsf() {
    const payload = {
      monthly_net_income: hcsfIncome,
      existing_monthly_credits: hcsfExisting,
      down_payment: hcsfDown,
      duration_years: hcsfYears,
      interest_rate_percent: ratePct,
      insurance_rate_percent: insPct,
      notary_rate_percent: hcsfNeuf ? 2.8 : 7.8,
      is_vefa_neuf: hcsfNeuf,
    };
    try {
      const apiRes = await montyApi.calculateHcsf(payload);
      setHcsfRes(apiRes);
      setSourceBadge("API FastAPI");
    } catch {
      setHcsfRes(computeHcsfLocal(payload));
      setSourceBadge("Moteur Déterministe Local");
    }
  }

  async function runNotaryOrImt() {
    if (country === "PT") {
      const payload = {
        property_price_euro: ptPrice,
        loan_amount_euro: ptLoan,
        is_primary_residence_hpp: ptHpp,
        is_imt_jovem_eligible: ptJovem,
      };
      try {
        const apiRes = await montyApi.calculatePortugalAcquisition(payload);
        setPtRes(apiRes);
        setSourceBadge("API FastAPI");
      } catch {
        setPtRes(computePortugalAcquisitionLocal(payload));
        setSourceBadge("Moteur Déterministe Local");
      }
      return;
    }

    const payload = {
      property_price: notaryPrice,
      is_neuf: notaryNeuf,
      department_code: notaryDept,
      use_increased_dmto_if_applicable: true,
    };
    try {
      const apiRes = await montyApi.calculateNotaryFees(payload);
      setNotaryRes(apiRes);
      setSourceBadge("API FastAPI");
    } catch {
      setNotaryRes(computeNotaryFeesLocal(payload));
      setSourceBadge("Moteur Déterministe Local");
    }
  }

  async function runYield() {
    const payload = {
      purchase_price: rentPrice,
      notary_fees: Math.round(rentPrice * 0.078),
      works_cost: rentWorks,
      monthly_rent_hc: rentMonthly,
      vacancy_months_per_year: 0.5,
      non_recoverable_charges_year: 650,
      property_tax_year: 950,
      management_fee_percent: 6.5,
      insurance_pno_gli_year: 390,
      monthly_loan_payment: rentLoan,
      dpe: rentDpe,
    };
    try {
      const apiRes = await montyApi.calculateRentalYield(payload);
      setYieldRes(apiRes);
      setSourceBadge("API FastAPI");
    } catch {
      setYieldRes(computeRentalYieldLocal(payload));
      setSourceBadge("Moteur Déterministe Local");
    }
  }

  async function runEstimation() {
    const payload = {
      sector_name: estSector,
      property_kind: estKind,
      surface_m2: estSurface,
      dpe: estDpe,
      condition_label: estCond,
      has_terrace_or_garden: estExt,
      has_parking: true,
    };
    try {
      const apiRes = await montyApi.calculateIndicativeEstimation(payload);
      setEstRes(apiRes);
      setSourceBadge("API FastAPI");
    } catch {
      setEstRes(computeIndicativeEstimationLocal(payload));
      setSourceBadge("Moteur Déterministe Local");
    }
  }

  async function runCapitalGain() {
    const payload = {
      purchase_price: pvBuy,
      sale_price: pvSell,
      holding_years: pvYears,
      is_primary_residence: pvPrimary,
      use_flat_acquisition_7_5_pct: true,
      use_flat_works_15_pct: true,
    };
    try {
      const apiRes = await montyApi.calculateCapitalGain(payload);
      setPvRes(apiRes);
      setSourceBadge("API FastAPI");
    } catch {
      setPvRes(computeCapitalGainLocal(payload));
      setSourceBadge("Moteur Déterministe Local");
    }
  }

  const pack = COUNTRY_PACKS[country];

  const tabs: Array<{ id: SimTab; label: string; icon: React.ComponentType<{ className?: string }> }> = [
    { id: "monthly", label: "1. Mensualités", icon: Calculator },
    { id: "hcsf", label: country === "PT" ? "2. Capacidade (DSTI)" : "2. Capacité HCSF 35 %", icon: Percent },
    { id: "notary", label: country === "PT" ? "3. IMT & Imposto Selo (PT)" : "3. Frais de notaire", icon: FileSpreadsheet },
    { id: "yield", label: "4. Rendement locatif", icon: TrendingUp },
    { id: "estimation", label: "5. Estimation indicative", icon: Home },
    { id: "capital_gain", label: "6. Plus-value immobilière", icon: Coins },
  ];

  return (
    <div className="space-y-6">
      {/* En-tête & Sélecteur de Juridiction Isolée */}
      <div className="rounded-2xl bg-gradient-to-r from-emeraldDeep to-emeraldMid text-ivoryWarm p-5 border border-goldAntique/40 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-goldChampagne font-semibold flex items-center gap-1.5">
            <Globe2 className="w-3.5 h-3.5" />
            Juridiction active : {pack.name_fr} ({pack.country_code}) • {pack.regulation_jurisdiction}
          </p>
          <h2 className="mt-1 font-display text-2xl font-bold">
            6 Simulateurs Immobiliers Monty
          </h2>
          <p className="mt-1 text-xs text-ivoryWarm/80">
            Régulateur crédit : {pack.credit_regulator} • Fiscalité : {pack.tax_system_label}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {(["FR", "PT"] as CountryCode[]).map((code) => (
            <button
              key={code}
              type="button"
              onClick={() => setCountry(code)}
              className={`px-3 py-2 rounded-xl text-xs font-semibold border transition ${
                country === code
                  ? "bg-goldAntique text-emeraldDeep border-goldChampagne"
                  : "bg-emeraldSurface/80 text-ivoryWarm border-goldAntique/30 hover:bg-emeraldMid"
              }`}
            >
              {code === "FR" ? "🇫🇷 France (Active)" : "🇵🇹 Portugal (Isolé P4)"}
            </button>
          ))}
          <span className="px-2.5 py-1.5 rounded-lg bg-emeraldDeep/70 text-[11px] text-goldChampagne border border-goldAntique/30">
            Calcul : {sourceBadge}
          </span>
        </div>
      </div>

      {/* Onglets des 6 simulateurs */}
      <div className="flex flex-wrap gap-2">
        {tabs.map((item) => {
          const Icon = item.icon;
          const active = tab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setTab(item.id)}
              className={`inline-flex items-center gap-2 px-3.5 py-2.5 rounded-xl text-xs sm:text-sm font-semibold border transition ${
                active
                  ? "bg-emeraldDeep text-goldChampagne border-emeraldDeep shadow-sm"
                  : "bg-ivoryCard text-obsidianInk border-borderSubtle hover:border-emeraldMid"
              }`}
            >
              <Icon className="w-4 h-4" />
              {item.label}
            </button>
          );
        })}
      </div>

      {/* Contenu du simulateur actif */}
      <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 sm:p-6 space-y-5">
        {tab === "monthly" && (
          <div className="space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep">
              Simulateur de mensualités, assurance &amp; reste à vivre
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-6 gap-3 text-sm">
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Prix du bien (€)</span>
                <input type="number" value={price} onChange={(e) => setPrice(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Apport (€)</span>
                <input type="number" value={downPayment} onChange={(e) => setDownPayment(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Durée (ans)</span>
                <input type="number" min={5} max={30} value={years} onChange={(e) => setYears(Number(e.target.value) || 25)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Taux nominal (%)</span>
                <input type="number" step="0.05" value={ratePct} onChange={(e) => setRatePct(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Assurance (%)</span>
                <input type="number" step="0.02" value={insPct} onChange={(e) => setInsPct(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Revenus nets/mois (€)</span>
                <input type="number" value={monthlyIncome} onChange={(e) => setMonthlyIncome(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
            </div>
            <button type="button" onClick={runMonthly} className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid">
              Recalculer les mensualités
            </button>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 rounded-xl bg-emeraldDeep text-ivoryWarm p-4 text-xs">
              <div><p className="text-ivoryWarm/70">Mensualité (ass. incl.)</p><p className="text-lg font-bold text-goldChampagne">{monthlyRes.monthly_incl_insurance.toLocaleString("fr-FR")} €/mois</p></div>
              <div><p className="text-ivoryWarm/70">Capital emprunté</p><p className="text-lg font-bold">{monthlyRes.borrowed_amount.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Taux d&apos;endettement</p><p className="text-lg font-bold">{monthlyRes.debt_ratio_percent} %</p></div>
              <div><p className="text-ivoryWarm/70">Coût total du crédit</p><p className="text-lg font-bold">{monthlyRes.total_credit_cost.toLocaleString("fr-FR")} €</p></div>
            </div>
          </div>
        )}

        {tab === "hcsf" && (
          <div className="space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep">
              {country === "PT"
                ? "Capacidade de Endividamento (Portugal — Referência DSTI 35 %)"
                : "Capacité d'emprunt maximale (Règle HCSF 35 % France)"}
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-sm">
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Revenus nets foyer/mois (€)</span>
                <input type="number" value={hcsfIncome} onChange={(e) => setHcsfIncome(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Crédits en cours/mois (€)</span>
                <input type="number" value={hcsfExisting} onChange={(e) => setHcsfExisting(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Apport mobilisable (€)</span>
                <input type="number" value={hcsfDown} onChange={(e) => setHcsfDown(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Durée cible (ans)</span>
                <input type="number" min={5} max={30} value={hcsfYears} onChange={(e) => setHcsfYears(Number(e.target.value) || 25)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
            </div>
            <label className="inline-flex items-center gap-2 text-xs font-semibold text-slateMuted">
              <input type="checkbox" checked={hcsfNeuf} onChange={(e) => setHcsfNeuf(e.target.checked)} />
              Achat dans le neuf / VEFA (frais réduits)
            </label>
            <div>
              <button type="button" onClick={runHcsf} className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid">
                Calculer ma capacité maximale
              </button>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 rounded-xl bg-emeraldDeep text-ivoryWarm p-4 text-xs">
              <div><p className="text-ivoryWarm/70">Prix bien max net</p><p className="text-lg font-bold text-goldChampagne">{hcsfRes.max_property_price_net.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Capital empruntable</p><p className="text-lg font-bold">{hcsfRes.max_borrowed_capital.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Mensualité plafond</p><p className="text-lg font-bold">{hcsfRes.max_monthly_loan_payment.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Conformité 35 %</p><p className="text-lg font-bold">{hcsfRes.is_hcsf_ratio_compliant ? "Conforme" : "Hors plafond"}</p></div>
            </div>
          </div>
        )}

        {tab === "notary" && country === "FR" && (
          <div className="space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep">
              Frais de notaire France (DMTO départementaux, émoluments &amp; débours)
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Prix d&apos;acquisition (€)</span>
                <input type="number" value={notaryPrice} onChange={(e) => setNotaryPrice(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Département (ex: 75, 69, 33, 34, 44...)</span>
                <input type="text" maxLength={3} value={notaryDept} onChange={(e) => setNotaryDept(e.target.value)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="flex items-end gap-2 pb-2 text-xs font-semibold text-slateMuted">
                <input type="checkbox" checked={notaryNeuf} onChange={(e) => setNotaryNeuf(e.target.checked)} />
                Bien Neuf / VEFA (TPF réduite 0,715 %)
              </label>
            </div>
            <button type="button" onClick={runNotaryOrImt} className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid">
              Calculer les frais d&apos;acte France
            </button>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 rounded-xl bg-emeraldDeep text-ivoryWarm p-4 text-xs">
              <div><p className="text-ivoryWarm/70">Total frais de notaire</p><p className="text-lg font-bold text-goldChampagne">{notaryRes.total_notary_fees_euro.toLocaleString("fr-FR")} € ({notaryRes.effective_rate_percent} %)</p></div>
              <div><p className="text-ivoryWarm/70">Droits fiscaux (DMTO)</p><p className="text-lg font-bold">{notaryRes.dmto_taxes_euro.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Émoluments notaire TTC</p><p className="text-lg font-bold">{notaryRes.notaire_emoluments_ttc_euro.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Coût global acte en main</p><p className="text-lg font-bold">{notaryRes.total_acquisition_cost_euro.toLocaleString("fr-FR")} €</p></div>
            </div>
            <p className="text-xs text-slateMuted">{notaryRes.notes.join(" ")}</p>
          </div>
        )}

        {tab === "notary" && country === "PT" && (
          <div className="space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep">
              Custos de Aquisição em Portugal — IMT + Imposto do Selo (Módulo PT Isolado)
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-sm">
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Preço do imóvel (€)</span>
                <input type="number" value={ptPrice} onChange={(e) => setPtPrice(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Montante do crédito (€)</span>
                <input type="number" value={ptLoan} onChange={(e) => setPtLoan(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="flex items-end gap-2 pb-2 text-xs font-semibold text-slateMuted">
                <input type="checkbox" checked={ptHpp} onChange={(e) => setPtHpp(e.target.checked)} />
                Habitação Própria Permanente (HPP)
              </label>
              <label className="flex items-end gap-2 pb-2 text-xs font-semibold text-slateMuted">
                <input type="checkbox" checked={ptJovem} onChange={(e) => setPtJovem(e.target.checked)} />
                Elegível IMT Jovem (≤ 35 anos)
              </label>
            </div>
            <button type="button" onClick={runNotaryOrImt} className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid">
              Calcular IMT e Imposto do Selo (Portugal)
            </button>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 rounded-xl bg-emeraldDeep text-ivoryWarm p-4 text-xs">
              <div><p className="text-ivoryWarm/70">IMT estimado</p><p className="text-lg font-bold text-goldChampagne">{ptRes.imt_euro.toLocaleString("pt-PT")} €</p></div>
              <div><p className="text-ivoryWarm/70">IS Aquisição (0,8 %)</p><p className="text-lg font-bold">{ptRes.imposto_selo_acquisition_euro.toLocaleString("pt-PT")} €</p></div>
              <div><p className="text-ivoryWarm/70">IS Crédito (0,6 %)</p><p className="text-lg font-bold">{ptRes.imposto_selo_mortgage_euro.toLocaleString("pt-PT")} €</p></div>
              <div><p className="text-ivoryWarm/70">Orçamento total</p><p className="text-lg font-bold">{ptRes.total_budget_euro.toLocaleString("pt-PT")} €</p></div>
            </div>
            <p className="text-xs text-slateMuted">{ptRes.disclaimer}</p>
          </div>
        )}

        {tab === "yield" && (
          <div className="space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep">
              Simulateur de rendement locatif (brut, net de charges &amp; cash-flow)
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-5 gap-3 text-sm">
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Prix d&apos;achat (€)</span>
                <input type="number" value={rentPrice} onChange={(e) => setRentPrice(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Travaux (€)</span>
                <input type="number" value={rentWorks} onChange={(e) => setRentWorks(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Loyer mensuel HC (€)</span>
                <input type="number" value={rentMonthly} onChange={(e) => setRentMonthly(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Mensualité crédit (€)</span>
                <input type="number" value={rentLoan} onChange={(e) => setRentLoan(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">{country === "PT" ? "Classe ADENE" : "Classe DPE"}</span>
                <select value={rentDpe} onChange={(e) => setRentDpe(e.target.value)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2">
                  {["A", "B", "C", "D", "E", "F", "G"].map((d) => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
              </label>
            </div>
            <button type="button" onClick={runYield} className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid">
              Calculer la rentabilité
            </button>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 rounded-xl bg-emeraldDeep text-ivoryWarm p-4 text-xs">
              <div><p className="text-ivoryWarm/70">Rendement brut</p><p className="text-lg font-bold">{yieldRes.gross_yield_percent} %</p></div>
              <div><p className="text-ivoryWarm/70">Rendement net</p><p className="text-lg font-bold text-goldChampagne">{yieldRes.net_yield_percent} %</p></div>
              <div><p className="text-ivoryWarm/70">Cash-flow mensuel</p><p className="text-lg font-bold">{yieldRes.monthly_cashflow_euro >= 0 ? `+${yieldRes.monthly_cashflow_euro}` : yieldRes.monthly_cashflow_euro} €/mois</p></div>
              <div><p className="text-ivoryWarm/70">Statut locatif</p><p className="text-sm font-bold">{yieldRes.is_rentable_compliant_now ? "Conforme" : "Travaux requis"}</p></div>
            </div>
            <p className={`text-xs flex items-center gap-1.5 font-medium ${yieldRes.is_rentable_compliant_now ? "text-emeraldDeep" : "text-red-700"}`}>
              {yieldRes.is_rentable_compliant_now ? <CheckCircle2 className="w-4 h-4" /> : <AlertTriangle className="w-4 h-4" />}
              {yieldRes.dpe_regulatory_alert}
            </p>
          </div>
        )}

        {tab === "estimation" && (
          <div className="space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
              <Building2 className="w-5 h-5 text-goldAntique" />
              Estimation immobilière indicative (France multi-villes &amp; critères)
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-5 gap-3 text-sm">
              <label className="block sm:col-span-2">
                <span className="text-xs font-semibold text-slateMuted">Ville / Métropole de référence</span>
                <select value={estSector} onChange={(e) => setEstSector(e.target.value)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2">
                  {FRENCH_SECTOR_BENCHMARKS.map((s) => (
                    <option key={s.name} value={s.name}>{s.name}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Type</span>
                <select value={estKind} onChange={(e) => setEstKind(e.target.value)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2">
                  <option value="Appartement">Appartement</option>
                  <option value="Maison">Maison</option>
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Surface (m²)</span>
                <input type="number" min={9} max={600} value={estSurface} onChange={(e) => setEstSurface(Number(e.target.value) || 20)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">DPE &amp; État</span>
                <select value={estDpe} onChange={(e) => setEstDpe(e.target.value)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2">
                  {["A", "B", "C", "D", "E", "F", "G"].map((d) => (
                    <option key={d} value={d}>DPE {d}</option>
                  ))}
                </select>
              </label>
            </div>
            <div className="flex flex-wrap items-center gap-4 text-xs text-slateMuted">
              <select value={estCond} onChange={(e) => setEstCond(e.target.value)} className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-sm text-obsidianInk">
                <option value="Rénové / Neuf">Rénové / Neuf (+6 %)</option>
                <option value="Bon état">Bon état (référence)</option>
                <option value="À rafraîchir">À rafraîchir (-7 %)</option>
                <option value="À rénover">À rénover (-15 %)</option>
              </select>
              <label className="inline-flex items-center gap-2 font-semibold">
                <input type="checkbox" checked={estExt} onChange={(e) => setEstExt(e.target.checked)} />
                Terrasse / Jardin privatif
              </label>
            </div>
            <button type="button" onClick={runEstimation} className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid">
              Estimer la fourchette indicative
            </button>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 rounded-xl bg-emeraldDeep text-ivoryWarm p-4 text-xs">
              <div><p className="text-ivoryWarm/70">Fourchette basse</p><p className="text-base font-bold">{estRes.estimated_low_euro.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Valeur centrale</p><p className="text-lg font-bold text-goldChampagne">{estRes.estimated_mid_euro.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Fourchette haute</p><p className="text-base font-bold">{estRes.estimated_high_euro.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Prix ajusté / m²</p><p className="text-base font-bold">{estRes.adjusted_price_m2.toLocaleString("fr-FR")} €/m²</p></div>
            </div>
            <p className="text-xs text-slateMuted">{estRes.disclaimer}</p>
          </div>
        )}

        {tab === "capital_gain" && (
          <div className="space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep">
              Calcul de plus-value immobilière indicative (CGI France : IR 22 ans / PS 30 ans)
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-sm">
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Prix d&apos;achat initial (€)</span>
                <input type="number" value={pvBuy} onChange={(e) => setPvBuy(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Prix de revente estimé (€)</span>
                <input type="number" value={pvSell} onChange={(e) => setPvSell(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slateMuted">Durée de détention (ans)</span>
                <input type="number" min={0} max={60} value={pvYears} onChange={(e) => setPvYears(Number(e.target.value) || 0)} className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2" />
              </label>
              <label className="flex items-end gap-2 pb-2 text-xs font-semibold text-slateMuted">
                <input type="checkbox" checked={pvPrimary} onChange={(e) => setPvPrimary(e.target.checked)} />
                Résidence principale (exonérée)
              </label>
            </div>
            <button type="button" onClick={runCapitalGain} className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid">
              Simuler l&apos;impôt sur la plus-value
            </button>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 rounded-xl bg-emeraldDeep text-ivoryWarm p-4 text-xs">
              <div><p className="text-ivoryWarm/70">Plus-value brute</p><p className="text-base font-bold">{pvRes.gross_capital_gain_euro.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Impôt total estimé</p><p className="text-lg font-bold text-amber-300">{pvRes.total_tax_euro.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Gain net après impôt</p><p className="text-lg font-bold text-goldChampagne">{pvRes.net_gain_after_tax_euro.toLocaleString("fr-FR")} €</p></div>
              <div><p className="text-ivoryWarm/70">Abattements IR / PS</p><p className="text-base font-bold">{pvRes.ir_abatement_percent}% / {pvRes.ps_abatement_percent}%</p></div>
            </div>
            {pvRes.exemption_reason && <p className="text-xs font-semibold text-emeraldDeep">{pvRes.exemption_reason}</p>}
            <p className="text-xs text-slateMuted">{pvRes.disclaimer}</p>
          </div>
        )}
      </div>
    </div>
  );
}
