import type {
  AdemeDpeLookupResponse,
  AddressSuggestion,
  AdminOverview,
  AdvisorPrivateNote,
  AgencyFeatureFlags,
  AgencyNotaryContact,
  AgencyNotification,
  AgencySubscriptionInfo,
  AgentCatalogResponse,
  AgentExecutionLog,
  AudienceScope,
  AuditLogEntry,
  AuthorizedFeedSource,
  CapitalGainResult,
  ClientDocument,
  ClientFavorite,
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
  LatestMobileReleaseResponse,
  LeadKeywordRule,
  LeadKind,
  LeadStatus,
  MarketplaceListing,
  MarketplaceSearchHit,
  MatchingCalculationResult,
  MobileAppRelease,
  ModuleFeatureFlag,
  ModuleFlagHistoryEntry,
  MonthlyPaymentResult,
  MontyAIChatResponse,
  MontyAIConversationTurn,
  MontyAIEngineConfig,
  MontyAIMemoryEntry,
  MontyAIMetricsSummary,
  MontyAIPendingAction,
  MontyAITaskType,
  MontyPriceIndexResult,
  MontyRole,
  NewsCategory,
  NewsSource,
  NotaryFeesResult,
  NotarySearchResponse,
  NotaryTransmissionRecord,
  NotaryTransmissionType,
  PortfolioProperty,
  PortfolioTransaction,
  PortfolioVisit,
  PortugalAcquisitionCostResult,
  PreEtatDateComputeResult,
  PreEtatDateExportResult,
  PreEtatDatePayload,
  PriceIndexConfig,
  PriceIndexHistoryRecord,
  ProNewsArticle,
  PublicInboundLead,
  PublicNewsArticle,
  PublisherType,
  RentalYieldResult,
  RnicAccessStatus,
  RnicSearchResponse,
  SaasPlanInfo,
  SaleTimelineEstimate,
  ScoutProposal,
  SearchPerimeterMode,
  SellerListingSummary,
  SessionInfo,
  TeamInviteResult,
  TeamMemberInfo,
  TransactionStage,
  TransportMode,
  UnifiedAgendaItem,
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
    price_index_category?: string;
    sort_by?: string;
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
    if (params.price_index_category) q.set("price_index_category", params.price_index_category);
    if (params.sort_by) q.set("sort_by", params.sort_by);
    return requestJson<{ count: number; hits: MarketplaceSearchHit[] }>(
      `/api/v1/marketplace/listings?${q.toString()}`,
      {},
      false
    );
  },

  geoAutocomplete: (q: string, countryCode: CountryCode = "FR", limit = 6) =>
    requestJson<{ query: string; country_code: string; count: number; suggestions: AddressSuggestion[] }>(
      `/api/v1/marketplace/geo/autocomplete?q=${encodeURIComponent(q)}&country_code=${countryCode}&limit=${limit}`,
      {},
      false
    ),

  geoReverse: (lat: number, lon: number, countryCode: CountryCode = "FR") =>
    requestJson<AddressSuggestion>(
      `/api/v1/marketplace/geo/reverse?lat=${lat}&lon=${lon}&country_code=${countryCode}`,
      {},
      false
    ),

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

  // Inscription Particulier (Acquéreur / Vendeur) sans créer d'agence
  registerClient: (payload: {
    email: string;
    password: string;
    display_name: string;
    role: "BUYER" | "SELLER";
    country_code?: CountryCode;
  }) =>
    requestJson<SessionInfo>(
      "/api/v1/auth/register-client",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  // Annuaire Officiel des Notaires de France & Transmissions Dossiers Compromis
  searchFrenchNotaries: (params: { query?: string; department_code?: string; postal_code?: string; per_page?: number; page?: number }) => {
    const q = new URLSearchParams();
    if (params.query) q.set("query", params.query);
    if (params.department_code) q.set("department_code", params.department_code);
    if (params.postal_code) q.set("postal_code", params.postal_code);
    if (params.per_page) q.set("per_page", String(params.per_page));
    if (params.page) q.set("page", String(params.page));
    return requestJson<NotarySearchResponse>(`/api/v1/notaries/search?${q.toString()}`, {}, false);
  },

  listAgencyNotaries: () =>
    requestJson<{ count: number; notaries: AgencyNotaryContact[] }>("/api/v1/notaries/book"),

  saveAgencyNotary: (payload: {
    siret?: string;
    office_name: string;
    notary_name?: string;
    email?: string;
    phone?: string;
    email_verified?: boolean;
    street_address?: string;
    postal_code?: string;
    city?: string;
    source?: string;
    notes?: string;
  }) =>
    requestJson<AgencyNotaryContact>("/api/v1/notaries/book", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  listNotaryTransmissions: () =>
    requestJson<{ count: number; transmissions: NotaryTransmissionRecord[] }>("/api/v1/notaries/transmissions"),

  createNotaryTransmission: (payload: {
    notary_contact_id?: string | null;
    transaction_id?: string | null;
    property_id?: string | null;
    dossier_ref: string;
    transmission_type: NotaryTransmissionType;
    recipient_email?: string;
    recipient_office: string;
    recipient_address?: string;
    property_title: string;
    property_address?: string;
    seller_name?: string;
    buyer_name?: string;
    agreed_price_euro: number;
    target_date?: string;
    checklist_items?: string[];
    notes?: string;
    pre_etat_date_html?: string;
    send_now?: boolean;
  }) =>
    requestJson<NotaryTransmissionRecord>("/api/v1/notaries/transmissions", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  downloadNotaryTransmissionEml: async (transmissionId: string): Promise<{ filename: string; content: string }> => {
    const headers = new Headers();
    if (inMemoryToken) {
      headers.set("Authorization", `Bearer ${inMemoryToken}`);
    }
    const res = await fetch(`${API_BASE}/api/v1/notaries/transmissions/${encodeURIComponent(transmissionId)}/eml`, {
      method: "GET",
      headers,
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => res.statusText);
      throw new Error(`API ${res.status}: ${detail}`);
    }
    const content = await res.text();
    const disp = res.headers.get("Content-Disposition") ?? "";
    const match = disp.match(/filename="?([^"]+)"?/i);
    return {
      filename: match?.[1] ?? `notaire-${transmissionId}.eml`,
      content,
    };
  },

  // Base Nationale des Copropriétés (RNIC ANAH — Gratuit Agences/Vendeurs, Payant Particuliers)
  getRnicAccessStatus: (rnicPassToken?: string) => {
    const q = rnicPassToken ? `?rnic_pass_token=${encodeURIComponent(rnicPassToken)}` : "";
    return requestJson<RnicAccessStatus>(`/api/v1/rnic/access-status${q}`);
  },

  searchRnicCoproprietes: (params: {
    postal_code?: string;
    commune?: string;
    immatriculation?: string;
    address_query?: string;
    page_size?: number;
    rnic_pass_token?: string;
  }) => {
    const q = new URLSearchParams();
    if (params.postal_code) q.set("postal_code", params.postal_code);
    if (params.commune) q.set("commune", params.commune);
    if (params.immatriculation) q.set("immatriculation", params.immatriculation);
    if (params.address_query) q.set("address_query", params.address_query);
    if (params.page_size) q.set("page_size", String(params.page_size));
    if (params.rnic_pass_token) q.set("rnic_pass_token", params.rnic_pass_token);
    return requestJson<RnicSearchResponse>(`/api/v1/rnic/search?${q.toString()}`);
  },

  createRnicCheckoutPass: (payload: { buyer_email: string; success_url: string; cancel_url: string }) =>
    requestJson<{
      checkout_session_id: string;
      checkout_url: string;
      pass_token: string;
      amount_euro: number;
    }>("/api/v1/billing/rnic-pass", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  verifyRnicPass: (params: { pass_token?: string; session_id?: string }) => {
    const q = new URLSearchParams();
    if (params.pass_token) q.set("pass_token", params.pass_token);
    if (params.session_id) q.set("session_id", params.session_id);
    return requestJson<{ active: boolean; pass_token: string; expires_at?: string }>(
      `/api/v1/billing/rnic-pass/verify?${q.toString()}`
    );
  },

  // Notifications Agence, Agenda Unifié, Portail Particulier & Gouvernance Admin
  listNotifications: (unreadOnly = false) =>
    requestJson<{ count: number; unread_count: number; notifications: AgencyNotification[] }>(
      `/api/v1/notifications${unreadOnly ? "?unread_only=true" : ""}`
    ),

  markNotificationRead: (notifId: string) =>
    requestJson<AgencyNotification>(`/api/v1/notifications/${encodeURIComponent(notifId)}/read`, {
      method: "POST",
    }),

  getUnifiedAgenda: () =>
    requestJson<{ count: number; items: UnifiedAgendaItem[] }>("/api/v1/agenda"),

  listPortalFavorites: () =>
    requestJson<{ count: number; favorites: ClientFavorite[] }>("/api/v1/portal/favorites"),

  addPortalFavorite: (payload: { listing_id: string; listing_snapshot?: Record<string, unknown> }) =>
    requestJson<ClientFavorite>("/api/v1/portal/favorites", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  removePortalFavorite: (listingId: string) =>
    requestJson<{ removed: boolean; listing_id: string }>(
      `/api/v1/portal/favorites/${encodeURIComponent(listingId)}`,
      { method: "DELETE" }
    ),

  listPortalDocuments: () =>
    requestJson<{ count: number; documents: ClientDocument[] }>("/api/v1/portal/documents"),

  addPortalDocument: (payload: {
    title: string;
    doc_category: string;
    file_name: string;
    mime_type?: string;
    file_size_bytes?: number;
    notes?: string;
  }) =>
    requestJson<ClientDocument>("/api/v1/portal/documents", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  listSellerListings: () =>
    requestJson<{ count: number; listings: SellerListingSummary[] }>("/api/v1/portal/seller-listings"),

  getAdminOverview: () => requestJson<AdminOverview>("/api/v1/admin/overview"),

  listAuditLogs: (limit = 40) =>
    requestJson<{ count: number; logs: AuditLogEntry[] }>(`/api/v1/admin/audit-logs?limit=${limit}`),

  getFeatureFlags: () => requestJson<AgencyFeatureFlags>("/api/v1/admin/feature-flags"),

  updateFeatureFlags: (payload: {
    auto_matching_enabled: boolean;
    ai_copywriting_enabled: boolean;
    rnic_free_for_sellers: boolean;
    public_appointment_booking: boolean;
    notary_auto_task_on_compromis: boolean;
  }) =>
    requestJson<AgencyFeatureFlags>("/api/v1/admin/feature-flags", {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  // Monty Leads : Mots-clés
  listLeadKeywords: () =>
    requestJson<{ count: number; keywords: LeadKeywordRule[] }>("/api/v1/crm/leads/keywords"),

  createLeadKeyword: (payload: { category: string; keyword: string; geo_scope: string }) =>
    requestJson<LeadKeywordRule>("/api/v1/crm/leads/keywords", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  deleteLeadKeyword: (keywordId: string) =>
    requestJson<{ removed: boolean; keyword_id: string }>(
      `/api/v1/crm/leads/keywords/${encodeURIComponent(keywordId)}`,
      { method: "DELETE" }
    ),

  // Module Feature Flags centralisés
  getActiveModuleFlags: (agencyId = "agency-france-reseau") =>
    requestJson<{ agency_id: string; modules: ModuleFeatureFlag[] }>(
      `/api/v1/module-flags/active?agency_id=${encodeURIComponent(agencyId)}`,
      {},
      false
    ),

  adminListModuleFlags: () =>
    requestJson<{ count: number; modules: ModuleFeatureFlag[] }>("/api/v1/admin/module-flags"),

  adminUpdateModuleFlag: (moduleKey: string, payload: { status: string; visible: boolean }) =>
    requestJson<ModuleFeatureFlag>(`/api/v1/admin/module-flags/${encodeURIComponent(moduleKey)}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  adminRestoreModuleFlags: () =>
    requestJson<{ count: number; modules: ModuleFeatureFlag[] }>("/api/v1/admin/module-flags/restore", {
      method: "POST",
    }),

  adminModuleFlagHistory: () =>
    requestJson<{ count: number; history: ModuleFlagHistoryEntry[] }>("/api/v1/admin/module-flags/history"),

  // Phase 27 — Monty AI Core
  montyAiChat: (payload: {
    prompt: string;
    task_type?: MontyAITaskType;
    confidential_mode?: boolean;
    context_payload?: Record<string, unknown>;
  }) =>
    requestJson<MontyAIChatResponse>("/api/v1/monty-ai/chat", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  listMontyAiConversations: (limit = 25) =>
    requestJson<{ count: number; conversations: MontyAIConversationTurn[] }>(
      `/api/v1/monty-ai/conversations?limit=${limit}`
    ),

  listMontyAiMemories: () =>
    requestJson<{ count: number; memories: MontyAIMemoryEntry[] }>("/api/v1/monty-ai/memories"),

  upsertMontyAiMemory: (payload: {
    memory_id?: string | null;
    scope: "USER" | "AGENCY";
    title: string;
    content: string;
    source_ref?: string;
  }) =>
    requestJson<MontyAIMemoryEntry>("/api/v1/monty-ai/memories", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  deleteMontyAiMemory: (memoryId: string) =>
    requestJson<{ removed: boolean; memory_id: string }>(
      `/api/v1/monty-ai/memories/${encodeURIComponent(memoryId)}`,
      { method: "DELETE" }
    ),

  listMontyAiActions: () =>
    requestJson<{ count: number; actions: MontyAIPendingAction[] }>("/api/v1/monty-ai/actions"),

  decideMontyAiAction: (actionId: string, approve: boolean) =>
    requestJson<MontyAIPendingAction>(`/api/v1/monty-ai/actions/${encodeURIComponent(actionId)}/decide`, {
      method: "POST",
      body: JSON.stringify({ approve }),
    }),

  adminListAiEngines: () =>
    requestJson<{ count: number; engines: MontyAIEngineConfig[] }>("/api/v1/monty-ai/admin/engines"),

  adminUpdateAiEngine: (
    engineId: string,
    payload: {
      enabled?: boolean;
      is_default?: boolean;
      is_fallback?: boolean;
      priority?: number;
      model_name?: string;
    }
  ) =>
    requestJson<MontyAIEngineConfig>(`/api/v1/monty-ai/admin/engines/${encodeURIComponent(engineId)}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  adminConfigureAiProviderKey: (payload: { provider: string; api_key?: string; local_base_url?: string }) =>
    requestJson<{ provider: string; configured: boolean; masked_value: string }>(
      "/api/v1/monty-ai/admin/providers/key",
      { method: "POST", body: JSON.stringify(payload) }
    ),

  adminMontyAiMetrics: () => requestJson<MontyAIMetricsSummary>("/api/v1/monty-ai/admin/metrics"),

  // Phase 28 — Mobile APK Releases
  getLatestMobileRelease: () =>
    requestJson<LatestMobileReleaseResponse>("/api/v1/mobile/releases/latest", {}, false),

  listPublicMobileReleases: () =>
    requestJson<{ count: number; releases: MobileAppRelease[] }>("/api/v1/mobile/releases", {}, false),

  getApkDownloadUrl: (releaseId: string) =>
    `${API_BASE}/api/v1/mobile/releases/${encodeURIComponent(releaseId)}/download`,

  adminListMobileReleases: () =>
    requestJson<{ count: number; total_downloads: number; releases: MobileAppRelease[] }>(
      "/api/v1/admin/mobile/releases"
    ),

  adminUploadMobileApk: (payload: {
    file_name: string;
    content_base64: string;
    version_name: string;
    version_code: number;
    min_android_sdk?: number;
    release_notes?: string;
    publish_now?: boolean;
    mark_recommended?: boolean;
  }) =>
    requestJson<MobileAppRelease>("/api/v1/admin/mobile/releases/upload", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  adminUpdateMobileRelease: (
    releaseId: string,
    payload: { is_published?: boolean; is_recommended?: boolean; release_notes?: string }
  ) =>
    requestJson<MobileAppRelease>(`/api/v1/admin/mobile/releases/${encodeURIComponent(releaseId)}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    }),

  // Phase 29 — Monty Price Index & ADEME Open Data DPE
  lookupAdemeDpe: (params: { postal_code?: string; city?: string; dpe_number?: string; limit?: number }) => {
    const q = new URLSearchParams();
    if (params.postal_code) q.set("postal_code", params.postal_code);
    if (params.city) q.set("city", params.city);
    if (params.dpe_number) q.set("dpe_number", params.dpe_number);
    if (params.limit) q.set("limit", String(params.limit));
    return requestJson<AdemeDpeLookupResponse>(`/api/v1/price-index/ademe-dpe?${q.toString()}`, {}, false);
  },

  createSellerPackCheckout: (payload: {
    dossier_id: string;
    property_id?: string;
    order_id?: string;
    buyer_email: string;
    success_url: string;
    cancel_url: string;
  }) =>
    requestJson<{
      order_id: string;
      checkout_session_id: string;
      checkout_url: string;
      amount_euro: number;
      status: string;
    }>("/api/v1/billing/seller-pack/checkout", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  verifySellerPackOrder: (params: { order_id: string; session_id?: string }) => {
    const q = new URLSearchParams({ order_id: params.order_id });
    if (params.session_id) q.set("session_id", params.session_id);
    return requestJson<{ order_id: string; paid: boolean; status: string; dossier_id: string }>(
      `/api/v1/billing/seller-pack/verify?${q.toString()}`
    );
  },

  evaluatePriceIndex: (payload: {
    agency_id?: string;
    target_ref?: string;
    title?: string;
    country_code?: CountryCode;
    city: string;
    sector_label?: string;
    postal_code?: string;
    property_kind?: string;
    asking_price_euro: number;
    surface_m2: number;
    dpe?: string;
    condition_label?: string;
    has_exterior?: boolean;
    has_parking?: boolean;
    estimated_works_euro?: number;
    comparables?: DvfComparableInput[];
  }) =>
    requestJson<MontyPriceIndexResult>(
      "/api/v1/price-index/evaluate",
      { method: "POST", body: JSON.stringify(payload) },
      false
    ),

  getPriceIndexHistory: (limit = 40) =>
    requestJson<{ count: number; history: PriceIndexHistoryRecord[] }>(
      `/api/v1/price-index/history?limit=${limit}`
    ),

  adminGetPriceIndexConfig: () => requestJson<PriceIndexConfig>("/api/v1/admin/price-index/config"),

  adminUpdatePriceIndexConfig: (payload: {
    excellent_deal_pct?: number;
    very_good_deal_pct: number;
    good_deal_pct: number;
    above_market_pct: number;
    overpriced_pct: number;
    min_comparables_required: number;
    max_comparable_distance_m: number;
    max_comparable_age_months: number;
  }) =>
    requestJson<PriceIndexConfig>("/api/v1/admin/price-index/config", {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
};




