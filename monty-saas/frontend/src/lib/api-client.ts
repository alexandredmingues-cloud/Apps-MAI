import type {
  AdvisorPrivateNote,
  AgencySubscriptionInfo,
  AgentCatalogResponse,
  AgentExecutionLog,
  AudienceScope,
  AuthorizedFeedSource,
  CapitalGainResult,
  ContactExchange,
  ContactRole,
  CopywritingResponse,
  CountryCode,
  CountryJurisdictionPack,
  CrmContact,
  CsvImportSummary,
  DualBriefingResponse,
  DvfComparableInput,
  DvfValuationResult,
  ExchangeChannel,
  FeedFormat,
  FeedSyncResult,
  FollowUpTask,
  HealthStatus,
  HcsfResult,
  IndicativeEstimationResult,
  KillSwitchResult,
  LeadKind,
  LeadStatus,
  MarketplaceListing,
  MarketplaceSearchHit,
  MatchingCalculationResult,
  MonthlyPaymentResult,
  MontyRole,
  NewsCategory,
  NewsSource,
  NotaryFeesResult,
  PortfolioProperty,
  PortfolioTransaction,
  PortfolioVisit,
  PortugalAcquisitionCostResult,
  PreEtatDateComputeResult,
  PreEtatDateExportResult,
  PreEtatDatePayload,
  ProNewsArticle,
  PublicInboundLead,
  PublicNewsArticle,
  PublisherType,
  RentalYieldResult,
  SaasPlanInfo,
  SaleTimelineEstimate,
  ScoutProposal,
  SearchPerimeterMode,
  SessionInfo,
  TeamInviteResult,
  TeamMemberInfo,
  TransactionStage,
  TransportMode,
  UserNewsPreferences,
  VisitStatus,
} from "./types";

const API_BASE = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8000").replace(/\/+$/, "");
const SESSION_STORAGE_KEY = "monty_saas_session_v2";

let inMemoryToken: string | null = null;

export function setInMemoryAccessToken(token: string | null): void {
  inMemoryToken = token;
}

export function persistSession(session: SessionInfo | null): void {
  inMemoryToken = session?.access_token ?? null;
  if (typeof window === "undefined") return;
  try {
    if (session) {
      window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    } else {
      window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
    }
  } catch {
    // Ignore storage quota / private mode errors
  }
}

export function loadPersistedSession(): SessionInfo | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SessionInfo;
    if (parsed?.access_token) {
      inMemoryToken = parsed.access_token;
      return parsed;
    }
  } catch {
    // Ignore malformed storage entry
  }
  return null;
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
  getMe: () =>
    requestJson<{ user_id: string; agency_id: string; role: MontyRole; permissions: string[] }>("/api/v1/auth/me"),
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
    origin_lat?: number;
    origin_lon?: number;
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
    if (params.origin_lat !== undefined) q.set("origin_lat", String(params.origin_lat));
    if (params.origin_lon !== undefined) q.set("origin_lon", String(params.origin_lon));
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

  // V2 — Équipe, Abonnement, DVF Score Monty, Matching, Pré-État Daté L.721-2 & 11 Agents
  listTeamMembers: () =>
    requestJson<{ count: number; members: TeamMemberInfo[] }>("/api/v1/team/members"),

  createTeamInvite: (payload: { target_email: string; target_name?: string; role: MontyRole }) =>
    requestJson<TeamInviteResult>("/api/v1/team/invites", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  getSubscription: () => requestJson<AgencySubscriptionInfo>("/api/v1/billing/subscription"),

  createCheckoutSession: (payload: {
    plan_code: "INDIVIDUAL" | "AGENCY" | "NETWORK";
    success_url: string;
    cancel_url: string;
  }) =>
    requestJson<{ checkout_session_id: string; checkout_url: string; plan_code: string }>(
      "/api/v1/billing/checkout-session",
      { method: "POST", body: JSON.stringify(payload) }
    ),

  calculateDvf: (payload: {
    comparables: DvfComparableInput[];
    property_kind: string;
    surface_m2: number;
    dpe: string;
    condition_label: string;
    provided_docs_ratio: number;
    floor_and_elevator?: string;
    view_and_exposure?: string;
    exterior_type?: string;
    parking_type?: string;
    environment_calm?: string;
  }) =>
    requestJson<DvfValuationResult>("/api/v1/calculators/dvf", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  calculateSaleTimeline: (payload: {
    sector_name: string;
    property_kind: string;
    asking_price_euro: number;
    surface_m2: number;
    dpe: string;
    condition_label: string;
    dvf_price_m2: number;
    dvf_count: number;
    photo_count: number;
    provided_docs_ratio: number;
    mandate_type?: string;
  }) =>
    requestJson<SaleTimelineEstimate & { warnings?: string[] }>("/api/v1/calculators/sale-timeline", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  calculateMatching: (payload: {
    price: number;
    estimated_works?: number;
    sector: string;
    property_type: string;
    surface: number;
    bedrooms: number;
    dpe: string;
    exterior?: string;
    parking?: string;
    environment?: string;
    transports?: string;
    floor?: string;
    latitude?: number;
    longitude?: number;
    buyer_budget_max: number;
    buyer_sectors: string[];
    buyer_property_type: string;
    buyer_min_surface: number;
    buyer_min_rooms: number;
    buyer_essential_criteria?: string[];
    search_mode?: SearchPerimeterMode;
    search_center_lat?: number;
    search_center_lon?: number;
    search_center_address?: string;
    search_radius_km?: number;
    search_max_travel_minutes?: number;
    search_transport_mode?: TransportMode;
  }) =>
    requestJson<MatchingCalculationResult>("/api/v1/calculators/matching", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  computePreEtatDate: (payload: PreEtatDatePayload) =>
    requestJson<PreEtatDateComputeResult>("/api/v1/pre-etat-date/compute", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  exportPreEtatDateHtml: (payload: PreEtatDatePayload) =>
    requestJson<PreEtatDateExportResult>("/api/v1/pre-etat-date/export", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  getAgentsCatalog: () => requestJson<AgentCatalogResponse>("/api/v1/agents/catalog"),

  runAgent: (payload: { agent_id: string; prompt_text: string }) =>
    requestJson<AgentExecutionLog>("/api/v1/agents/run", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  getAgentLogs: () => requestJson<{ count: number; logs: AgentExecutionLog[] }>("/api/v1/agents/logs"),

  setAgentsKillSwitch: (payload: { enabled: boolean; reason?: string }) =>
    requestJson<KillSwitchResult>("/api/v1/agents/kill-switch", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  listScoutProposals: () =>
    requestJson<{ count: number; proposals: ScoutProposal[] }>("/api/v1/agents/scout/proposals"),

  createScoutProposal: (payload: {
    target_contact_name: string;
    channel: "WHATSAPP" | "EMAIL" | "SMS";
    legal_basis: string;
    message_draft: string;
  }) =>
    requestJson<ScoutProposal>("/api/v1/agents/scout/proposals", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  decideScoutProposal: (proposalId: string, approve: boolean) =>
    requestJson<ScoutProposal>(`/api/v1/agents/scout/proposals/${proposalId}/decision`, {
      method: "POST",
      body: JSON.stringify({ approve }),
    }),

  // Flux partenaires autorisés (XML / JSON / JSON-LD)
  listAuthorizedFeeds: () =>
    requestJson<{ count: number; feeds: AuthorizedFeedSource[] }>("/api/v1/marketplace/feeds"),

  registerAuthorizedFeed: (payload: {
    name: string;
    country_code: CountryCode;
    publisher_type: PublisherType;
    feed_format: FeedFormat;
    feed_url: string;
    agreement_confirmed: boolean;
    auto_publish_verified_agency?: boolean;
  }) =>
    requestJson<AuthorizedFeedSource>("/api/v1/marketplace/feeds", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  syncAuthorizedFeed: (feedId: string, inlinePayload?: string) =>
    requestJson<FeedSyncResult>(`/api/v1/marketplace/feeds/${feedId}/sync`, {
      method: "POST",
      body: JSON.stringify(inlinePayload ? { inline_payload: inlinePayload } : {}),
    }),

  // Captation Leads Publics RGPD -> CRM Pro
  submitPublicLead: (payload: {
    agency_id?: string;
    lead_kind: LeadKind;
    country_code: CountryCode;
    full_name?: string;
    email: string;
    phone?: string;
    city?: string;
    budget_euro?: number;
    min_rooms?: number;
    search_transport_mode?: string;
    max_travel_minutes?: number;
    horizon?: string;
    property_title?: string;
    notes?: string;
    rgpd_consent_granted: boolean;
  }) =>
    requestJson<PublicInboundLead>(
      "/api/v1/marketplace/public-leads",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  listPublicLeads: (status?: LeadStatus) => {
    const q = status ? `?status=${encodeURIComponent(status)}` : "";
    return requestJson<{ count: number; leads: PublicInboundLead[] }>(`/api/v1/crm/public-leads${q}`);
  },

  updatePublicLeadStatus: (leadId: string, status: LeadStatus) =>
    requestJson<PublicInboundLead>(`/api/v1/crm/public-leads/${leadId}`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),

  // Phase 3 — Portefeuille Pro (Biens, Visites & Transactions persistés PostgreSQL RLS)
  listPortfolioProperties: () =>
    requestJson<{ count: number; properties: PortfolioProperty[] }>("/api/v1/portfolio/properties"),

  createPortfolioProperty: (payload: {
    title: string;
    sector: string;
    property_type: string;
    price: number;
    surface: number;
    bedrooms: number;
    dpe: string;
    mandate: "EXCLUSIF" | "SIMPLE";
    docs_ratio?: number;
  }) =>
    requestJson<PortfolioProperty>("/api/v1/portfolio/properties", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  listPortfolioVisits: () =>
    requestJson<{ count: number; visits: PortfolioVisit[] }>("/api/v1/portfolio/visits"),

  createPortfolioVisit: (payload: {
    property_id: string;
    buyer_name: string;
    date_label: string;
    feedback?: string;
  }) =>
    requestJson<PortfolioVisit>("/api/v1/portfolio/visits", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updatePortfolioVisitStatus: (visitId: string, payload: { status: VisitStatus; feedback?: string }) =>
    requestJson<PortfolioVisit>(`/api/v1/portfolio/visits/${visitId}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    }),

  listPortfolioTransactions: () =>
    requestJson<{ count: number; transactions: PortfolioTransaction[] }>("/api/v1/portfolio/transactions"),

  createPortfolioTransaction: (payload: {
    property_id: string;
    buyer_name: string;
    offer_euro: number;
    stage?: TransactionStage;
    notary_target_date?: string;
  }) =>
    requestJson<PortfolioTransaction>("/api/v1/portfolio/transactions", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  updatePortfolioTransactionStage: (txId: string, stage: TransactionStage) =>
    requestJson<PortfolioTransaction>(`/api/v1/portfolio/transactions/${txId}`, {
      method: "PATCH",
      body: JSON.stringify({ stage }),
    }),
};



