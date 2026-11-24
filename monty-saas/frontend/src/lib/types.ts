export type MontyRole =
  | "SUPER_ADMIN"
  | "DIRECTOR"
  | "ADMIN"
  | "AGENT"
  | "ASSISTANT"
  | "BUYER"
  | "SELLER";

export type NavSectionKey =
  | "dashboard"
  | "actualites"
  | "crm"
  | "buyers"
  | "sellers"
  | "properties"
  | "matching"
  | "estimation"
  | "visits"
  | "transactions"
  | "ai"
  | "marketplace"
  | "automations"
  | "documents"
  | "admin";

export type NewsCategory =
  | "TAUX_CREDIT"
  | "MARCHE_LOCAL"
  | "LOIS_FISCALITE"
  | "DPE_TRAVAUX"
  | "LOCATION_COPRO"
  | "INVESTISSEMENT"
  | "METIER_CONSEILLER";

export type AudienceScope = "BOTH" | "PRO_ONLY" | "PUBLIC_ONLY";

export type CountryCode = "FR" | "PT";
export type LocaleCode = "fr-FR" | "pt-PT";

export interface CountryJurisdictionPack {
  country_code: CountryCode;
  name_fr: string;
  name_native: string;
  default_locale: LocaleCode;
  supported_locales: string[];
  currency_code: string;
  rollout_priority: string;
  regulation_jurisdiction: string;
  credit_regulator: string;
  max_debt_ratio_guideline_percent: number;
  tax_system_label: string;
  energy_certificate_label: string;
  regions: string[];
  major_cities: string[];
  official_data_sources: string[];
}

export interface PortugalAcquisitionCostResult {
  country_code: "PT";
  property_price_euro: number;
  is_primary_residence_hpp: boolean;
  is_imt_jovem_eligible: boolean;
  imt_euro: number;
  imposto_selo_acquisition_euro: number;
  imposto_selo_mortgage_euro: number;
  notary_registry_estimate_euro: number;
  total_taxes_and_costs_euro: number;
  total_budget_euro: number;
  disclaimer: string;
}

export interface NewsSource {
  id: string;
  country_code: CountryCode;
  locale: string;
  name: string;
  domain: string;
  base_url: string;
  reliability_tier: "OFFICIELLE" | "INSTITUTIONNELLE" | "PROFESSIONNELLE";
  last_verified_at: string;
}

export interface PublicNewsArticle {
  id: string;
  slug: string;
  country_code: CountryCode;
  locale: string;
  regulation_jurisdiction: string;
  source_name: string;
  source_url: string;
  category: NewsCategory;
  city: string;
  department_code: string;
  region_name: string;
  title: string;
  seo_meta_title: string;
  seo_meta_description: string;
  public_summary: string;
  public_takeaways: string[];
  ai_dual_generated: boolean;
  legal_disclaimer: string;
  status: "PUBLISHED" | "ARCHIVED";
  published_at: string;
}

export interface ProNewsArticle extends PublicNewsArticle {
  source_id: string;
  audience_scope: AudienceScope;
  pro_analysis: string;
  pro_commercial_arguments: string[];
  pro_key_metrics: Record<string, string>;
}

export interface DualBriefingResponse {
  mode: "openai_live" | "deterministic_fallback";
  model_used: string;
  sanitized_topic: string;
  pro_analysis: string;
  pro_commercial_arguments: string[];
  public_summary: string;
  public_takeaways: string[];
  legal_disclaimer: string;
}

export interface UserNewsPreferences {
  user_id: string;
  country_code: CountryCode;
  locale: string;
  city: string;
  department_code: string;
  region_name: string;
  interests: NewsCategory[];
  notifications_enabled: boolean;
  rgpd_consent_granted: boolean;
  rgpd_consent_date: string | null;
}

export interface HealthStatus {
  status: string;
  service: string;
  environment: string;
  database_configured: boolean;
  storage_mode?: "postgresql_rls" | "in_memory_fallback";
  dev_auth_enabled?: boolean;
  redis_configured: boolean;
  openai_configured: boolean;
  stripe_configured: boolean;
}

export interface SessionInfo {
  access_token: string;
  user_id?: string;
  agency_id: string;
  agency_name?: string;
  display_name?: string;
  email?: string;
  role: MontyRole;
  permissions: string[];
}

export interface SaasPlanInfo {
  code: "INDIVIDUAL" | "AGENCY" | "NETWORK";
  name: string;
  monthly_price_euro: number;
  seat_limit: number;
  ai_monthly_quota_tokens: number;
  target_audience: string;
  features: string[];
}

export interface HcsfScenario {
  duration_years: number;
  max_borrowed_capital: number;
  max_property_price_net: number;
  is_hcsf_compliant: boolean;
}

export interface HcsfResult {
  max_monthly_debt_total: number;
  max_monthly_loan_payment: number;
  max_borrowed_capital: number;
  max_total_project_budget: number;
  max_property_price_net: number;
  estimated_notary_fees: number;
  total_credit_cost: number;
  effective_debt_ratio_percent: number;
  remainder_to_live: number;
  is_hcsf_ratio_compliant: boolean;
  is_hcsf_duration_compliant: boolean;
  duration_scenarios: HcsfScenario[];
}

export interface MonthlyPaymentResult {
  notary_fees: number;
  total_project_cost: number;
  borrowed_amount: number;
  monthly_excl_insurance: number;
  monthly_insurance: number;
  monthly_incl_insurance: number;
  total_credit_cost: number;
  debt_ratio_percent: number;
  remainder_to_live: number;
  real_monthly_housing_cost: number;
  scenario_20_years: number;
  scenario_25_years: number;
}

export interface NotaryFeesResult {
  property_price: number;
  is_neuf: boolean;
  department_code: string;
  dmto_taxes_euro: number;
  notaire_emoluments_ttc_euro: number;
  formalities_disbursements_euro: number;
  security_contribution_euro: number;
  total_notary_fees_euro: number;
  effective_rate_percent: number;
  total_acquisition_cost_euro: number;
  notes: string[];
}

export interface RentalYieldResult {
  total_investment_euro: number;
  annual_gross_rent_euro: number;
  annual_effective_rent_euro: number;
  annual_charges_euro: number;
  annual_net_rent_euro: number;
  gross_yield_percent: number;
  net_yield_percent: number;
  monthly_cashflow_euro: number;
  dpe_regulatory_alert: string;
  is_rentable_compliant_now: boolean;
}

export interface IndicativeEstimationResult {
  sector_name: string;
  base_price_m2: number;
  adjusted_price_m2: number;
  estimated_low_euro: number;
  estimated_mid_euro: number;
  estimated_high_euro: number;
  market_tension: string;
  disclaimer: string;
}

export interface CapitalGainResult {
  is_exempt: boolean;
  exemption_reason: string;
  gross_capital_gain_euro: number;
  adjusted_purchase_price_euro: number;
  ir_abatement_percent: number;
  ps_abatement_percent: number;
  taxable_gain_ir_euro: number;
  taxable_gain_ps_euro: number;
  income_tax_19_euro: number;
  social_levies_17_2_euro: number;
  surtax_euro: number;
  total_tax_euro: number;
  net_gain_after_tax_euro: number;
  disclaimer: string;
}

// Phase 2 — CRM, Relances, CSV & RGPD
export type ContactRole = "BUYER" | "SELLER" | "LEAD" | "PROSPECT" | "PARTNER";
export type ExchangeChannel = "CALL" | "EMAIL" | "VISIT" | "OFFER" | "MEETING";
export type FollowUpPriority = "HIGH" | "MEDIUM" | "LOW";

export interface CrmContact {
  id: string;
  agency_id: string;
  assigned_user_id: string;
  country_code: CountryCode;
  full_name: string;
  email: string;
  phone: string;
  role: ContactRole;
  city: string;
  budget_euro: number;
  legal_basis: string;
  consent_granted: boolean;
  retention_until: string;
  created_at: string;
  last_contact_at: string;
  has_missing_docs: boolean;
  pending_offer_days: number;
  last_visit_days_ago: number | null;
  is_anonymized: boolean;
}

export interface AdvisorPrivateNote {
  id: string;
  agency_id: string;
  contact_id: string;
  author_user_id: string;
  content: string;
  created_at: string;
}

export interface ContactExchange {
  id: string;
  agency_id: string;
  contact_id: string;
  author_user_id: string;
  channel: ExchangeChannel;
  summary: string;
  created_at: string;
}

export interface FollowUpTask {
  id: string;
  agency_id: string;
  contact_id: string;
  contact_name: string;
  assigned_user_id: string;
  title: string;
  reason: string;
  priority: FollowUpPriority;
  due_date: string;
  is_auto_generated: boolean;
  completed: boolean;
}

export interface CsvImportSummary {
  imported_count: number;
  skipped_duplicates: number;
  rejected_rows: number;
  warnings: string[];
  created_contact_ids: string[];
}

// Phase 2 — Marketplace, Mobilité 6 modes & Publication Vendeur
export type PublisherType = "PARTICULIER" | "AGENCE";
export type TransportMode = "VOITURE" | "TRAM" | "BUS" | "TRAIN" | "VELO" | "PIED";
export type SearchPerimeterMode = "SECTOR_LIST" | "RADIUS_KM" | "TRAVEL_TIME";

export interface SaleTimelineEstimate {
  status: "READY" | "INSUFFICIENT_DATA";
  low_days: number;
  likely_days: number;
  high_days: number;
  confidence_label: string;
}

export type ListingModerationStatus = "A_VERIFIER" | "PUBLIEE" | "REJETEE";

export interface MarketplaceListing {
  id: string;
  country_code: CountryCode;
  publisher_type: PublisherType;
  publisher_label: string;
  title: string;
  property_kind: string;
  city: string;
  region_name: string;
  sector_label: string;
  price_euro: number;
  surface_m2: number;
  rooms: number;
  dpe: string;
  condition_label: string;
  highlights: string[];
  description: string;
  photo_count: number;
  latitude: number;
  longitude: number;
  moderation_status?: ListingModerationStatus;
  moderation_note?: string;
  sale_timeline: SaleTimelineEstimate;
  published_at: string;
}

export interface MarketplaceSearchHit {
  listing: MarketplaceListing;
  distance_km: number;
  estimated_minutes: number;
  transport_badge: string;
  within_perimeter: boolean;
}

export interface CopywritingResponse {
  suggested_title: string;
  suggested_description: string;
  photo_tip: string;
}

