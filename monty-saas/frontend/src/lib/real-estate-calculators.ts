import type {
  CapitalGainResult,
  HcsfResult,
  IndicativeEstimationResult,
  MonthlyPaymentResult,
  NotaryFeesResult,
  PortugalAcquisitionCostResult,
  RentalYieldResult,
} from "./types";

// Miroir déterministe 1:1 des moteurs Python (app/domain/real_estate_engines.py & country_packs.py)
// Utilisé comme repli transparent si l'API FastAPI n'est pas encore démarrée en local.

export function computeMonthlyPaymentLocal(params: {
  price: number;
  down_payment: number;
  notary_rate_percent: number;
  duration_years: number;
  interest_rate_percent: number;
  insurance_rate_percent: number;
  monthly_income: number;
  existing_credits: number;
  monthly_charges: number;
}): MonthlyPaymentResult {
  const notary_fees = Math.round(params.price * (params.notary_rate_percent / 100));
  const total_project_cost = params.price + notary_fees;
  const borrowed_amount = Math.max(0, total_project_cost - params.down_payment);
  const mRate = params.interest_rate_percent / 100 / 12;
  const months = params.duration_years * 12;

  const monthly_excl =
    borrowed_amount <= 0
      ? 0
      : mRate === 0
      ? borrowed_amount / months
      : (borrowed_amount * mRate) / (1 - Math.pow(1 + mRate, -months));
  const monthly_ins = (borrowed_amount * (params.insurance_rate_percent / 100)) / 12;
  const monthly_incl = monthly_excl + monthly_ins;
  const total_credit_cost = Math.max(0, monthly_incl * months - borrowed_amount);
  const totalDebt = monthly_incl + params.existing_credits;
  const debt_ratio_percent =
    params.monthly_income > 0 ? Math.round((totalDebt / params.monthly_income) * 1000) / 10 : 0;

  const scenario = (yrs: number) => {
    if (borrowed_amount <= 0) return 0;
    const m = yrs * 12;
    const base = mRate === 0 ? borrowed_amount / m : (borrowed_amount * mRate) / (1 - Math.pow(1 + mRate, -m));
    return Math.round(base + monthly_ins);
  };

  return {
    notary_fees,
    total_project_cost,
    borrowed_amount,
    monthly_excl_insurance: Math.round(monthly_excl),
    monthly_insurance: Math.round(monthly_ins),
    monthly_incl_insurance: Math.round(monthly_incl),
    total_credit_cost: Math.round(total_credit_cost),
    debt_ratio_percent,
    remainder_to_live: Math.round(params.monthly_income - totalDebt - params.monthly_charges),
    real_monthly_housing_cost: Math.round(monthly_incl + params.monthly_charges),
    scenario_20_years: scenario(20),
    scenario_25_years: scenario(25),
  };
}

export function computeHcsfLocal(params: {
  monthly_net_income: number;
  existing_monthly_credits: number;
  down_payment: number;
  duration_years: number;
  interest_rate_percent: number;
  insurance_rate_percent: number;
  notary_rate_percent: number;
  is_vefa_neuf: boolean;
}): HcsfResult {
  const income = Math.max(0, params.monthly_net_income);
  const existing = Math.max(0, params.existing_monthly_credits);
  const down = Math.max(0, params.down_payment);
  const years = Math.min(30, Math.max(5, params.duration_years));
  const maxDebtTotal = income * 0.35;
  const maxLoanPmt = Math.max(0, maxDebtTotal - existing);
  const mInt = params.interest_rate_percent / 100 / 12;
  const mIns = params.insurance_rate_percent / 100 / 12;

  const capFor = (dYears: number): [number, number] => {
    const m = dYears * 12;
    const cFactor = mInt === 0 ? 1 / m : mInt / (1 - Math.pow(1 + mInt, -m));
    const totalF = cFactor + mIns;
    const borrowed = maxLoanPmt <= 0 || totalF <= 0 ? 0 : Math.round(maxLoanPmt / totalF);
    const totalB = borrowed + down;
    const netPrice = totalB <= 0 ? 0 : Math.round(totalB / (1 + params.notary_rate_percent / 100));
    return [borrowed, netPrice];
  };

  const [max_borrowed_capital, max_property_price_net] = capFor(years);
  const max_total_project_budget = max_borrowed_capital + down;
  const actualPmt = Math.round(maxLoanPmt);
  const effRatio = income > 0 ? Math.round(((actualPmt + existing) / income) * 1000) / 10 : 0;

  return {
    max_monthly_debt_total: Math.round(maxDebtTotal),
    max_monthly_loan_payment: actualPmt,
    max_borrowed_capital,
    max_total_project_budget,
    max_property_price_net,
    estimated_notary_fees: Math.max(0, max_total_project_budget - max_property_price_net),
    total_credit_cost:
      max_borrowed_capital <= 0 ? 0 : Math.round(Math.max(0, maxLoanPmt * years * 12 - max_borrowed_capital)),
    effective_debt_ratio_percent: effRatio,
    remainder_to_live: income - existing - actualPmt,
    is_hcsf_ratio_compliant: effRatio <= 35.5,
    is_hcsf_duration_compliant: params.is_vefa_neuf ? years <= 27 : years <= 25,
    duration_scenarios: [15, 20, 25].map((d) => {
      const [b, n] = capFor(d);
      return { duration_years: d, max_borrowed_capital: b, max_property_price_net: n, is_hcsf_compliant: d <= 25 };
    }),
  };
}

const INCREASED_DMTO_DEPTS = new Set([
  "34", "75", "92", "93", "94", "69", "33", "31", "44", "59", "06", "13", "83", "35", "78", "91",
]);

export function computeNotaryFeesLocal(params: {
  property_price: number;
  is_neuf: boolean;
  department_code: string;
  use_increased_dmto_if_applicable: boolean;
}): NotaryFeesResult {
  const price = Math.max(0, params.property_price);
  const dept = params.department_code.trim().toUpperCase().slice(0, 3);
  const t1 = Math.min(price, 6500) * 0.0387;
  const t2 = Math.max(0, Math.min(price, 17000) - 6500) * 0.01596;
  const t3 = Math.max(0, Math.min(price, 60000) - 17000) * 0.01064;
  const t4 = Math.max(0, price - 60000) * 0.00799;
  const emol = Math.round((t1 + t2 + t3 + t4) * 1.2);

  const raised = params.use_increased_dmto_if_applicable && INCREASED_DMTO_DEPTS.has(dept);
  const dmtoRate = params.is_neuf ? 0.00715 : raised ? 0.063185 : 0.0580665;
  const dmto = Math.round(price * dmtoRate);
  const formalities = 1200;
  const csi = Math.max(15, Math.round(price * 0.001));
  const total = dmto + emol + formalities + csi;

  return {
    property_price: price,
    is_neuf: params.is_neuf,
    department_code: dept,
    dmto_taxes_euro: dmto,
    notaire_emoluments_ttc_euro: emol,
    formalities_disbursements_euro: formalities,
    security_contribution_euro: csi,
    total_notary_fees_euro: total,
    effective_rate_percent: price > 0 ? Math.round((total / price) * 1000) / 10 : 0,
    total_acquisition_cost_euro: price + total,
    notes: [
      params.is_neuf
        ? "Neuf / VEFA : taxe de publicité foncière réduite à 0,715 %."
        : raised
        ? `Département ${dept} : taux DMTO intégrant la hausse LF 2025 (+0,5 pt hors primo-accédant exonéré).`
        : `Département ${dept || "standard"} : barème DMTO classique (5,81 %).`,
    ],
  };
}

export function computeRentalYieldLocal(params: {
  purchase_price: number;
  notary_fees: number;
  works_cost: number;
  monthly_rent_hc: number;
  vacancy_months_per_year: number;
  non_recoverable_charges_year: number;
  property_tax_year: number;
  management_fee_percent: number;
  insurance_pno_gli_year: number;
  monthly_loan_payment: number;
  dpe: string;
}): RentalYieldResult {
  const total_inv = Math.max(1, params.purchase_price + params.notary_fees + params.works_cost);
  const annual_gross = params.monthly_rent_hc * 12;
  const annual_effective = Math.round(params.monthly_rent_hc * Math.max(0, 12 - params.vacancy_months_per_year));
  const mgmt = Math.round(annual_effective * (params.management_fee_percent / 100));
  const annual_charges =
    params.non_recoverable_charges_year + params.property_tax_year + mgmt + params.insurance_pno_gli_year;
  const annual_net = annual_effective - annual_charges;
  const dpeClean = params.dpe.trim().toUpperCase();

  return {
    total_investment_euro: total_inv,
    annual_gross_rent_euro: annual_gross,
    annual_effective_rent_euro: annual_effective,
    annual_charges_euro: annual_charges,
    annual_net_rent_euro: annual_net,
    gross_yield_percent: Math.round((annual_gross / total_inv) * 10000) / 100,
    net_yield_percent: Math.round((annual_net / total_inv) * 10000) / 100,
    monthly_cashflow_euro: Math.round(annual_net / 12 - params.monthly_loan_payment),
    dpe_regulatory_alert:
      dpeClean === "G"
        ? "Alerte réglementaire (France) : DPE G interdit à la location depuis 2025."
        : dpeClean === "F"
        ? "Vigilance (France) : loyer gelé et interdiction prévue en 2028."
        : `Étiquette ${dpeClean} conforme pour la mise en location.`,
    is_rentable_compliant_now: dpeClean !== "G",
  };
}

export const FRENCH_SECTOR_BENCHMARKS: Array<{ name: string; apt: number; house: number; tension: string }> = [
  { name: "Paris (Île-de-France)", apt: 9650, house: 11200, tension: "Très forte" },
  { name: "Lyon (Auvergne-Rhône-Alpes)", apt: 4950, house: 5600, tension: "Forte" },
  { name: "Bordeaux (Nouvelle-Aquitaine)", apt: 4550, house: 5100, tension: "Forte" },
  { name: "Nice (PACA)", apt: 5250, house: 6100, tension: "Très forte" },
  { name: "Nantes (Pays de la Loire)", apt: 3850, house: 4400, tension: "Forte" },
  { name: "Rennes (Bretagne)", apt: 4100, house: 4600, tension: "Très forte" },
  { name: "Strasbourg (Grand Est)", apt: 3800, house: 4200, tension: "Forte" },
  { name: "Lille (Hauts-de-France)", apt: 3700, house: 4050, tension: "Forte" },
  { name: "Toulouse (Occitanie)", apt: 3650, house: 4150, tension: "Forte" },
  { name: "Marseille (PACA)", apt: 3550, house: 4200, tension: "Modérée à forte" },
  { name: "Montpellier (Occitanie)", apt: 4150, house: 4750, tension: "Forte" },
];

export function computeIndicativeEstimationLocal(params: {
  sector_name: string;
  property_kind: string;
  surface_m2: number;
  dpe: string;
  condition_label: string;
  has_terrace_or_garden: boolean;
  has_parking: boolean;
}): IndicativeEstimationResult {
  const sec =
    FRENCH_SECTOR_BENCHMARKS.find((s) => s.name === params.sector_name) ?? FRENCH_SECTOR_BENCHMARKS[1];
  const base = params.property_kind === "Maison" ? sec.house : sec.apt;
  const dpeMap: Record<string, number> = { A: 0.05, B: 0.05, C: 0.02, D: 0, E: -0.04, F: -0.09, G: -0.14 };
  let delta = dpeMap[params.dpe.toUpperCase()] ?? 0;
  if (params.condition_label.includes("Rénové")) delta += 0.06;
  else if (params.condition_label.includes("Rafraîchir")) delta -= 0.07;
  else if (params.condition_label.includes("Rénover")) delta -= 0.15;
  if (params.has_terrace_or_garden) delta += 0.05;
  if (params.has_parking) delta += 0.03;

  const adj = Math.round(base * (1 + delta));
  const mid = Math.round((adj * params.surface_m2) / 500) * 500;
  return {
    sector_name: sec.name,
    base_price_m2: base,
    adjusted_price_m2: adj,
    estimated_low_euro: Math.round((mid * 0.93) / 500) * 500,
    estimated_mid_euro: mid,
    estimated_high_euro: Math.round((mid * 1.07) / 500) * 500,
    market_tension: sec.tension,
    disclaimer:
      "Fourchette indicative nationale basée sur les repères Monty. Ne remplace pas une expertise locale sur comparables DVF.",
  };
}

export function computeCapitalGainLocal(params: {
  purchase_price: number;
  sale_price: number;
  holding_years: number;
  is_primary_residence: boolean;
  use_flat_acquisition_7_5_pct: boolean;
  use_flat_works_15_pct: boolean;
}): CapitalGainResult {
  const disclaimer =
    "Calcul indicatif basé sur le barème général CGI des plus-values immobilières. À valider avec votre notaire.";
  const acq = params.use_flat_acquisition_7_5_pct ? Math.round(params.purchase_price * 0.075) : 0;
  const works = params.use_flat_works_15_pct && params.holding_years > 5 ? Math.round(params.purchase_price * 0.15) : 0;
  const adjustedBuy = params.purchase_price + acq + works;
  const gross = Math.max(0, params.sale_price - adjustedBuy);

  if (params.is_primary_residence || gross === 0) {
    return {
      is_exempt: true,
      exemption_reason: params.is_primary_residence
        ? "Exonération totale : cession de résidence principale (Art. 150 U II-1° CGI)."
        : "Aucune plus-value taxable après majoration.",
      gross_capital_gain_euro: gross,
      adjusted_purchase_price_euro: adjustedBuy,
      ir_abatement_percent: 100,
      ps_abatement_percent: 100,
      taxable_gain_ir_euro: 0,
      taxable_gain_ps_euro: 0,
      income_tax_19_euro: 0,
      social_levies_17_2_euro: 0,
      surtax_euro: 0,
      total_tax_euro: 0,
      net_gain_after_tax_euro: gross,
      disclaimer,
    };
  }

  const y = params.holding_years;
  const irAb = y < 6 ? 0 : y >= 22 ? 100 : (y - 5) * 6;
  const psAb =
    y < 6 ? 0 : y >= 30 ? 100 : y <= 21 ? Math.round((y - 5) * 1.65 * 100) / 100 : y === 22 ? 28 : 28 + (y - 22) * 9;
  const taxIrBase = Math.round(gross * (1 - irAb / 100));
  const taxPsBase = Math.round(gross * (1 - psAb / 100));
  const irTax = Math.round(taxIrBase * 0.19);
  const psTax = Math.round(taxPsBase * 0.172);
  const surtax = taxIrBase > 50_000 ? Math.round(taxIrBase * 0.02) : 0;
  const totalTax = irTax + psTax + surtax;

  return {
    is_exempt: y >= 30 && totalTax === 0,
    exemption_reason: y >= 22 ? "Exonération IR acquise (≥ 22 ans), PS dégressifs jusqu'à 30 ans." : "",
    gross_capital_gain_euro: gross,
    adjusted_purchase_price_euro: adjustedBuy,
    ir_abatement_percent: irAb,
    ps_abatement_percent: psAb,
    taxable_gain_ir_euro: taxIrBase,
    taxable_gain_ps_euro: taxPsBase,
    income_tax_19_euro: irTax,
    social_levies_17_2_euro: psTax,
    surtax_euro: surtax,
    total_tax_euro: totalTax,
    net_gain_after_tax_euro: Math.max(0, gross - totalTax),
    disclaimer,
  };
}

export function computePortugalAcquisitionLocal(params: {
  property_price_euro: number;
  loan_amount_euro: number;
  is_primary_residence_hpp: boolean;
  is_imt_jovem_eligible: boolean;
}): PortugalAcquisitionCostResult {
  const p = Math.max(0, params.property_price_euro);
  let imt = 0;
  let seloAcq = 0;
  if (params.is_primary_residence_hpp && params.is_imt_jovem_eligible && p <= 316_772) {
    imt = 0;
    seloAcq = 0;
  } else {
    if (params.is_primary_residence_hpp) {
      if (p <= 101_917) imt = 0;
      else if (p <= 139_412) imt = Math.round(p * 0.02 - 2038.34);
      else if (p <= 190_086) imt = Math.round(p * 0.05 - 6220.7);
      else if (p <= 316_772) imt = Math.round(p * 0.07 - 10022.42);
      else if (p <= 633_453) imt = Math.round(p * 0.08 - 13190.14);
      else imt = Math.round(p * 0.06);
    } else {
      if (p <= 101_917) imt = Math.round(p * 0.01);
      else if (p <= 139_412) imt = Math.round(p * 0.02 - 1019.17);
      else if (p <= 190_086) imt = Math.round(p * 0.05 - 5201.53);
      else if (p <= 316_772) imt = Math.round(p * 0.07 - 9003.25);
      else imt = Math.round(p * 0.08 - 12170.97);
    }
    if (params.is_primary_residence_hpp && params.is_imt_jovem_eligible && p > 316_772) {
      imt = Math.max(0, Math.round((p - 316_772) * 0.08));
      seloAcq = Math.round((p - 316_772) * 0.008);
    } else {
      seloAcq = Math.round(p * 0.008);
    }
  }
  const seloLoan = Math.round(Math.max(0, params.loan_amount_euro) * 0.006);
  const notaryReg = 950;
  const totalTaxes = Math.max(0, imt) + seloAcq + seloLoan + notaryReg;
  return {
    country_code: "PT",
    property_price_euro: p,
    is_primary_residence_hpp: params.is_primary_residence_hpp,
    is_imt_jovem_eligible: params.is_imt_jovem_eligible,
    imt_euro: Math.max(0, imt),
    imposto_selo_acquisition_euro: seloAcq,
    imposto_selo_mortgage_euro: seloLoan,
    notary_registry_estimate_euro: notaryReg,
    total_taxes_and_costs_euro: totalTaxes,
    total_budget_euro: p + totalTaxes,
    disclaimer: "Simulação indicativa (Portugal continental — CIMT & Imposto do Selo). Validar no Portal das Finanças.",
  };
}
