import type { CountryCode, CountryJurisdictionPack, LocaleCode } from "./types";

export const COUNTRY_PACKS: Record<CountryCode, CountryJurisdictionPack> = {
  FR: {
    country_code: "FR",
    name_fr: "France",
    name_native: "France",
    default_locale: "fr-FR",
    supported_locales: ["fr-FR", "en-GB"],
    currency_code: "EUR",
    rollout_priority: "Priorité 1 & 2 — Active (France entière)",
    regulation_jurisdiction: "FR_CODE_CIVIL_CGI_ALUR",
    credit_regulator: "HCSF (Haut Conseil de Stabilité Financière)",
    max_debt_ratio_guideline_percent: 35,
    tax_system_label: "Frais de notaire (DMTO + émoluments) & Plus-value CGI",
    energy_certificate_label: "DPE (A à G) — Loi Climat et Résilience",
    regions: [
      "National",
      "Île-de-France",
      "Auvergne-Rhône-Alpes",
      "Occitanie",
      "Nouvelle-Aquitaine",
      "Provence-Alpes-Côte d'Azur",
      "Bretagne",
      "Pays de la Loire",
      "Hauts-de-France",
      "Grand Est",
      "Normandie",
      "Bourgogne-Franche-Comté",
      "Centre-Val de Loire",
      "Corse",
    ],
    major_cities: [
      "Paris",
      "Lyon",
      "Marseille",
      "Bordeaux",
      "Toulouse",
      "Nantes",
      "Lille",
      "Nice",
      "Montpellier",
      "Strasbourg",
      "Rennes",
    ],
    official_data_sources: [
      "Banque de France",
      "Etalab / DGFiP (DVF)",
      "Notaires de France",
      "Légifrance",
      "Service-Public.fr",
      "ADEME",
    ],
  },
  PT: {
    country_code: "PT",
    name_fr: "Portugal",
    name_native: "Portugal",
    default_locale: "pt-PT",
    supported_locales: ["pt-PT", "fr-FR", "en-GB"],
    currency_code: "EUR",
    rollout_priority: "Priorité 4 — Module national isolé prêt",
    regulation_jurisdiction: "PT_CODIGO_CIVIL_CIRS_CIMT",
    credit_regulator: "Banco de Portugal (Recomendação Macroprudencial DSTI)",
    max_debt_ratio_guideline_percent: 35,
    tax_system_label: "IMT + Imposto do Selo (0,8 %) & Mais-Valias IRS",
    energy_certificate_label: "Certificado Energético ADENE (A+ a F)",
    regions: [
      "Nacional",
      "Área Metropolitana de Lisboa",
      "Norte (Porto / Braga)",
      "Centro",
      "Algarve",
      "Alentejo",
      "Região Autónoma dos Açores",
      "Região Autónoma da Madeira",
    ],
    major_cities: ["Lisboa", "Porto", "Faro", "Braga", "Coimbra", "Cascais", "Setúbal", "Funchal"],
    official_data_sources: [
      "Banco de Portugal",
      "INE Portugal",
      "Portal das Finanças / Diário da República",
      "ADENE",
    ],
  },
};

export interface UiDictionary {
  brandTagline: string;
  publicSpace: string;
  proSpace: string;
  simulators: string;
  newsHub: string;
  websiteNav: string;
  jurisdictionNotice: string;
  legalNote: string;
}

export const UI_I18N: Record<LocaleCode, UiDictionary> = {
  "fr-FR": {
    brandTagline: "L'humain avant tout. La technologie au service du conseiller immobilier.",
    publicSpace: "Espace Particulier",
    proSpace: "Espace Pro SaaS",
    simulators: "Simulateurs",
    newsHub: "Actualités",
    websiteNav: "Site Web",
    jurisdictionNotice: "Règles fiscales, crédit et sources vérifiées isolées par juridiction nationale.",
    legalNote: "Information générale indicative — ne remplace pas un conseil juridique ou fiscal officiel.",
  },
  "pt-PT": {
    brandTagline: "O humano em primeiro lugar. Tecnologia ao serviço da mediação imobiliária.",
    publicSpace: "Espaço Particular",
    proSpace: "Espaço Pro SaaS",
    simulators: "Simuladores",
    newsHub: "Notícias",
    websiteNav: "Website",
    jurisdictionNotice: "Regras fiscais, crédito e fontes oficiais isoladas por jurisdição nacional.",
    legalNote: "Informação geral indicativa — não substitui aconselhamento jurídico ou fiscal oficial.",
  },
  "en-GB": {
    brandTagline: "People first. Technology empowering the real estate advisor.",
    publicSpace: "Private Client Space",
    proSpace: "Pro SaaS Workspace",
    simulators: "Calculators",
    newsHub: "News",
    websiteNav: "Website",
    jurisdictionNotice: "Tax rules, credit limits and official sources isolated per national jurisdiction.",
    legalNote: "General indicative information — does not replace official legal or tax advice.",
  },
};
