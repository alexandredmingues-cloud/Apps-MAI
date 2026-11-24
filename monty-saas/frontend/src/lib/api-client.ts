import type {
  AdvisorPrivateNote,
  AudienceScope,
  CapitalGainResult,
  ContactExchange,
  ContactRole,
  CopywritingResponse,
  CountryCode,
  CountryJurisdictionPack,
  CrmContact,
  CsvImportSummary,
  DualBriefingResponse,
  ExchangeChannel,
  FollowUpTask,
  HealthStatus,
  HcsfResult,
  IndicativeEstimationResult,
  MarketplaceListing,
  MarketplaceSearchHit,
  MonthlyPaymentResult,
  MontyRole,
  NewsCategory,
  NewsSource,
  NotaryFeesResult,
  PortugalAcquisitionCostResult,
  ProNewsArticle,
  PublicNewsArticle,
  PublisherType,
  RentalYieldResult,
  SaasPlanInfo,
  SearchPerimeterMode,
  SessionInfo,
  TransportMode,
  UserNewsPreferences,
} from "./types";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8000";

let inMemoryToken: string | null = null;

export function setInMemoryAccessToken(token: string | null): void {
  inMemoryToken = token;
}

export function hasInMemoryAccessToken(): boolean {
  return Boolean(inMemoryToken);
}

async function requestJson<T>(path: string, init: RequestInit = {}, includeAuth = true): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  if (includeAuth && inMemoryToken) {
    headers.set("Authorization", `Bearer ${inMemoryToken}`);
  }
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (!res.ok) {
    const detail = await res.text().catch(() => res.statusText);
    throw new Error(`API ${res.status}: ${detail}`);
  }
  return (await res.json()) as T;
}

export const montyApi = {
  health: () => requestJson<HealthStatus>("/api/v1/health", {}, false),
  issueDevSession: (payload: { user_id: string; agency_id: string; role: MontyRole }) =>
    requestJson<SessionInfo>(
      "/api/v1/auth/dev-session",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),
  registerAgency: (payload: {
    agency_name: string;
    email: string;
    password: string;
    display_name: string;
    city?: string;
    country_code?: CountryCode;
  }) =>
    requestJson<SessionInfo>(
      "/api/v1/auth/register",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),
  login: (payload: { email: string; password: string }) =>
    requestJson<SessionInfo>(
      "/api/v1/auth/login",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),
  listBillingPlans: () => requestJson<{ plans: SaasPlanInfo[] }>("/api/v1/billing/plans", {}, false),
  listCountryPacks: () =>
    requestJson<{ packs: CountryJurisdictionPack[] }>("/api/v1/public/country-packs", {}, false),

  listPublicSources: (countryCode?: CountryCode) =>
    requestJson<{ sources: NewsSource[] }>(
      `/api/v1/public/sources${countryCode ? `?country_code=${countryCode}` : ""}`,
      {},
      false
    ),

  listPublicNews: (params: {
    country_code: CountryCode;
    category?: NewsCategory;
    region_name?: string;
    city?: string;
    query?: string;
    include_archived?: boolean;
  }) => {
    const q = new URLSearchParams({ country_code: params.country_code });
    if (params.category) q.set("category", params.category);
    if (params.region_name) q.set("region_name", params.region_name);
    if (params.city) q.set("city", params.city);
    if (params.query) q.set("query", params.query);
    if (params.include_archived) q.set("include_archived", "true");
    return requestJson<{ country_code: CountryCode; count: number; articles: PublicNewsArticle[] }>(
      `/api/v1/public/news?${q.toString()}`,
      {},
      false
    );
  },

  listProNews: (params: {
    country_code: CountryCode;
    category?: NewsCategory;
    region_name?: string;
    city?: string;
    query?: string;
    include_archived?: boolean;
  }) => {
    const q = new URLSearchParams({ country_code: params.country_code });
    if (params.category) q.set("category", params.category);
    if (params.region_name) q.set("region_name", params.region_name);
    if (params.city) q.set("city", params.city);
    if (params.query) q.set("query", params.query);
    if (params.include_archived) q.set("include_archived", "true");
    return requestJson<{ country_code: CountryCode; count: number; articles: ProNewsArticle[] }>(
      `/api/v1/pro/news?${q.toString()}`
    );
  },

  generateDualBrief: (payload: {
    topic: string;
    source_excerpt: string;
    location_label: string;
    complex_mode?: boolean;
  }) =>
    requestJson<DualBriefingResponse>("/api/v1/pro/news/ai-dual-brief", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updatePreferences: (payload: {
    country_code: CountryCode;
    locale: string;
    city: string;
    department_code: string;
    region_name: string;
    interests: NewsCategory[];
    notifications_enabled: boolean;
    rgpd_consent_granted: boolean;
  }) =>
    requestJson<UserNewsPreferences>("/api/v1/news/preferences", {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  adminCreateSource: (payload: {
    country_code: CountryCode;
    name: string;
    domain: string;
    base_url: string;
    reliability_tier: string;
  }) =>
    requestJson<NewsSource>("/api/v1/admin/news/sources", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  adminCreateArticle: (payload: {
    source_id: string;
    source_url: string;
    country_code: CountryCode;
    category: NewsCategory;
    audience_scope: AudienceScope;
    title: string;
    region_name: string;
    city: string;
    public_summary: string;
    public_takeaways: string[];
    pro_analysis: string;
    pro_commercial_arguments: string[];
  }) =>
    requestJson<ProNewsArticle>("/api/v1/admin/news/articles", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  // Calculateurs publics (FR + PT)
  calculatePublicMonthlyPayment: (payload: {
    price: number;
    down_payment: number;
    notary_rate_percent: number;
    duration_years: number;
    interest_rate_percent: number;
    insurance_rate_percent: number;
    monthly_income: number;
    existing_credits: number;
    monthly_charges: number;
  }) =>
    requestJson<MonthlyPaymentResult>(
      "/api/v1/public/calculators/monthly-payment",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  calculateHcsf: (payload: {
    monthly_net_income: number;
    existing_monthly_credits: number;
    down_payment: number;
    duration_years: number;
    interest_rate_percent: number;
    insurance_rate_percent: number;
    notary_rate_percent: number;
    is_vefa_neuf: boolean;
  }) =>
    requestJson<HcsfResult>(
      "/api/v1/public/calculators/hcsf",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  calculateNotaryFees: (payload: {
    property_price: number;
    is_neuf: boolean;
    department_code: string;
    use_increased_dmto_if_applicable: boolean;
  }) =>
    requestJson<NotaryFeesResult>(
      "/api/v1/public/calculators/notary-fees",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  calculateRentalYield: (payload: {
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
  }) =>
    requestJson<RentalYieldResult>(
      "/api/v1/public/calculators/rental-yield",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  calculateIndicativeEstimation: (payload: {
    sector_name: string;
    property_kind: string;
    surface_m2: number;
    dpe: string;
    condition_label: string;
    has_terrace_or_garden: boolean;
    has_parking: boolean;
  }) =>
    requestJson<IndicativeEstimationResult>(
      "/api/v1/public/calculators/estimation",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  calculateCapitalGain: (payload: {
    purchase_price: number;
    sale_price: number;
    holding_years: number;
    is_primary_residence: boolean;
    use_flat_acquisition_7_5_pct: boolean;
    use_flat_works_15_pct: boolean;
  }) =>
    requestJson<CapitalGainResult>(
      "/api/v1/public/calculators/capital-gain",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  calculatePortugalAcquisition: (payload: {
    property_price_euro: number;
    loan_amount_euro: number;
    is_primary_residence_hpp: boolean;
    is_imt_jovem_eligible: boolean;
  }) =>
    requestJson<PortugalAcquisitionCostResult>(
      "/api/v1/public/calculators/pt/acquisition-costs",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  // Phase 2 — CRM, Notes privées isolées, Relances auto, CSV & RGPD
  listCrmContacts: (params?: { role?: ContactRole; country_code?: CountryCode; query?: string }) => {
    const q = new URLSearchParams();
    if (params?.role) q.set("role", params.role);
    if (params?.country_code) q.set("country_code", params.country_code);
    if (params?.query) q.set("query", params.query);
    const qs = q.toString();
    return requestJson<{ count: number; contacts: CrmContact[] }>(
      `/api/v1/crm/contacts${qs ? `?${qs}` : ""}`
    );
  },

  createCrmContact: (payload: {
    full_name: string;
    email?: string;
    phone?: string;
    role: ContactRole;
    country_code: CountryCode;
    city?: string;
    budget_euro?: number;
    legal_basis?: string;
    consent_granted?: boolean;
  }) =>
    requestJson<CrmContact>("/api/v1/crm/contacts", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  getPrivateNotes: (contactId: string) =>
    requestJson<{ notes: AdvisorPrivateNote[] }>(`/api/v1/crm/contacts/${contactId}/private-notes`),

  addPrivateNote: (contactId: string, content: string) =>
    requestJson<AdvisorPrivateNote>(`/api/v1/crm/contacts/${contactId}/private-notes`, {
      method: "POST",
      body: JSON.stringify({ content }),
    }),

  addExchange: (contactId: string, payload: { channel: ExchangeChannel; summary: string }) =>
    requestJson<ContactExchange>(`/api/v1/crm/contacts/${contactId}/exchanges`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  getAutoFollowups: () => requestJson<{ count: number; tasks: FollowUpTask[] }>("/api/v1/crm/followups/auto"),

  completeFollowup: (taskId: string) =>
    requestJson<FollowUpTask>(`/api/v1/crm/followups/${taskId}/complete`, { method: "POST" }),

  importCsv: (payload: { csv_text: string; default_country: CountryCode }) =>
    requestJson<CsvImportSummary>("/api/v1/crm/import-csv", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  exportCsvText: async () => {
    const headers = new Headers();
    if (inMemoryToken) {
      headers.set("Authorization", `Bearer ${inMemoryToken}`);
    }
    const res = await fetch(`${API_BASE}/api/v1/crm/export-csv`, { headers });
    if (!res.ok) throw new Error(`API ${res.status}`);
    return res.text();
  },

  rgpdPortabilityExport: (contactId: string) =>
    requestJson<Record<string, unknown>>(`/api/v1/crm/contacts/${contactId}/rgpd-export`),

  rgpdPurgeExpired: () =>
    requestJson<{ anonymized_count: number }>("/api/v1/crm/rgpd/purge-expired", { method: "POST" }),

  // Phase 2 — Marketplace Public & Pro, Mobilité 6 modes & Rédaction IA vendeur
  searchMarketplaceListings: (params: {
    country_code: CountryCode;
    publisher_type?: PublisherType;
    origin_address?: string;
    search_mode?: SearchPerimeterMode;
    transport_mode?: TransportMode;
    max_radius_km?: number;
    max_travel_minutes?: number;
    max_price_euro?: number;
    min_surface_m2?: number;
    only_within_perimeter?: boolean;
  }) => {
    const q = new URLSearchParams({ country_code: params.country_code });
    if (params.publisher_type) q.set("publisher_type", params.publisher_type);
    if (params.origin_address) q.set("origin_address", params.origin_address);
    if (params.search_mode) q.set("search_mode", params.search_mode);
    if (params.transport_mode) q.set("transport_mode", params.transport_mode);
    if (params.max_radius_km !== undefined) q.set("max_radius_km", String(params.max_radius_km));
    if (params.max_travel_minutes !== undefined) q.set("max_travel_minutes", String(params.max_travel_minutes));
    if (params.max_price_euro !== undefined) q.set("max_price_euro", String(params.max_price_euro));
    if (params.min_surface_m2 !== undefined) q.set("min_surface_m2", String(params.min_surface_m2));
    if (params.only_within_perimeter !== undefined) {
      q.set("only_within_perimeter", String(params.only_within_perimeter));
    }
    return requestJson<{ count: number; hits: MarketplaceSearchHit[] }>(
      `/api/v1/marketplace/listings?${q.toString()}`,
      {},
      false
    );
  },

  assistListingCopywriting: (payload: {
    country_code: CountryCode;
    property_kind: string;
    city: string;
    sector_label: string;
    surface_m2: number;
    rooms: number;
    price_euro: number;
    dpe: string;
    condition_label: string;
    highlights: string[];
    photo_count: number;
    estimated_sale_days?: number;
  }) =>
    requestJson<CopywritingResponse>("/api/v1/marketplace/ad-copy-assistant", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  publishMarketplaceListing: (payload: {
    country_code: CountryCode;
    publisher_type: PublisherType;
    publisher_label: string;
    title: string;
    property_kind: string;
    city: string;
    sector_label: string;
    price_euro: number;
    surface_m2: number;
    rooms: number;
    dpe: string;
    condition_label: string;
    highlights: string[];
    description: string;
    photo_count: number;
  }) =>
    requestJson<MarketplaceListing>("/api/v1/marketplace/listings", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  listPendingModeration: () =>
    requestJson<{ count: number; listings: MarketplaceListing[] }>("/api/v1/marketplace/moderation/pending"),

  moderateListing: (listingId: string, payload: { approve: boolean; moderation_note?: string }) =>
    requestJson<MarketplaceListing>(`/api/v1/marketplace/listings/${listingId}/moderate`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
};
