"use client";

import { useEffect, useState } from "react";

export type UiLocaleCode = "fr-FR" | "pt-PT" | "en-GB";

const STORAGE_KEY = "monty_ui_locale_v1";

export const SUPPORTED_LOCALES: { code: UiLocaleCode; label: string; short: string }[] = [
  { code: "fr-FR", label: "Français", short: "FR" },
  { code: "pt-PT", label: "Português", short: "PT" },
  { code: "en-GB", label: "English", short: "EN" },
];

export interface MontyUiMessages {
  tagline: string;
  navHome: string;
  navListings: string;
  navPublish: string;
  navCopro: string;
  navNews: string;
  navSimulators: string;
  navDownloadApp: string;
  navMySpace: string;
  navProSaas: string;
  navDownloadFull: string;
  navPublicSite: string;
  navPublicNewsShort: string;
  navSimulatorsShort: string;
  activeTenant: string;
  footerSummary: string;
  footerQuickAccess: string;
  footerPrivacyTitle: string;
  footerPrivacyBody: string;
  priceIndexCalculatedAt: string;
  priceIndexDisclaimerFallback: string;
}

const MESSAGES: Record<UiLocaleCode, MontyUiMessages> = {
  "fr-FR": {
    tagline: "France entière • Portugal (Prêt)",
    navHome: "Accueil",
    navListings: "Annonces",
    navPublish: "Publier",
    navCopro: "Copropriétés",
    navNews: "Actualités",
    navSimulators: "Simulateurs",
    navDownloadApp: "Télécharger l'app",
    navMySpace: "Mon Espace",
    navProSaas: "Espace Pro SaaS",
    navDownloadFull: "Télécharger l'application",
    navPublicSite: "Site Web Public",
    navPublicNewsShort: "Actus Public",
    navSimulatorsShort: "Simulateurs",
    activeTenant: "Tenant actif",
    footerSummary: "Architecture multi-pays et multilingue (France active • Portugal isolé • Europe extensible).",
    footerQuickAccess: "Accès Rapide",
    footerPrivacyTitle: "Conformité RGPD & Sécurité",
    footerPrivacyBody:
      "Aucune donnée privée d'agence, de client, de mandat ou note interne n'est accessible depuis l'espace public. Les alertes nécessitent un consentement explicite révocable (Art. 6.1.a RGPD) et les estimations restent indicatives.",
    priceIndexCalculatedAt: "Calculé le",
    priceIndexDisclaimerFallback:
      "Indicateur automatisé non contractuel établi à partir de transactions comparables et de critères déclaratifs. Ne constitue pas une expertise immobilière certifiée.",
  },
  "pt-PT": {
    tagline: "França inteira • Portugal (Pronto)",
    navHome: "Início",
    navListings: "Anúncios",
    navPublish: "Publicar",
    navCopro: "Condomínios",
    navNews: "Notícias",
    navSimulators: "Simuladores",
    navDownloadApp: "Transferir App",
    navMySpace: "O Meu Espaço",
    navProSaas: "Área Pro SaaS",
    navDownloadFull: "Transferir Aplicação",
    navPublicSite: "Site Web Público",
    navPublicNewsShort: "Notícias",
    navSimulatorsShort: "Simuladores",
    activeTenant: "Tenant ativo",
    footerSummary: "Arquitetura multipaís e multilingue (França ativa • Portugal isolado • Europa extensível).",
    footerQuickAccess: "Acesso Rápido",
    footerPrivacyTitle: "Conformidade RGPD & Segurança",
    footerPrivacyBody:
      "Nenhum dado privado de agência, cliente, mandato ou nota interna é acessível na área pública. Os alertas exigem consentimento explícito revogável (Art. 6.1.a RGPD) e as avaliações são indicativas.",
    priceIndexCalculatedAt: "Calculado em",
    priceIndexDisclaimerFallback:
      "Indicador automatizado não contratual baseado em transações comparáveis e critérios declarativos. Não constitui avaliação imobiliária certificada.",
  },
  "en-GB": {
    tagline: "All France • Portugal (Ready)",
    navHome: "Home",
    navListings: "Listings",
    navPublish: "Publish",
    navCopro: "Condominiums",
    navNews: "News",
    navSimulators: "Calculators",
    navDownloadApp: "Download App",
    navMySpace: "My Account",
    navProSaas: "Pro SaaS Workspace",
    navDownloadFull: "Download Android App",
    navPublicSite: "Public Website",
    navPublicNewsShort: "Public News",
    navSimulatorsShort: "Calculators",
    activeTenant: "Active tenant",
    footerSummary: "Multi-country & multilingual architecture (France live • Portugal isolated • Europe ready).",
    footerQuickAccess: "Quick Access",
    footerPrivacyTitle: "GDPR Compliance & Security",
    footerPrivacyBody:
      "No private agency, client, mandate or internal note data is exposed on public pages. Alerts require explicit revocable consent (GDPR Art. 6.1.a) and valuations remain indicative.",
    priceIndexCalculatedAt: "Calculated at",
    priceIndexDisclaimerFallback:
      "Non-contractual automated indicator computed from comparable transactions and declarative criteria. Does not constitute a certified real estate appraisal.",
  },
};

export function getMontyMessages(locale: UiLocaleCode): MontyUiMessages {
  return MESSAGES[locale] ?? MESSAGES["fr-FR"];
}

export function useMontyLocale() {
  const [locale, setLocaleState] = useState<UiLocaleCode>("fr-FR");

  useEffect(() => {
    try {
      const saved = window.localStorage.getItem(STORAGE_KEY) as UiLocaleCode | null;
      if (saved && (saved === "fr-FR" || saved === "pt-PT" || saved === "en-GB")) {
        setLocaleState(saved);
      }
    } catch {
      // Ignore storage access restrictions
    }
    const onSync = (ev: Event) => {
      const custom = ev as CustomEvent<UiLocaleCode>;
      if (custom.detail && MESSAGES[custom.detail]) {
        setLocaleState(custom.detail);
      }
    };
    window.addEventListener("monty-locale-change", onSync);
    return () => window.removeEventListener("monty-locale-change", onSync);
  }, []);

  const setLocale = (next: UiLocaleCode) => {
    setLocaleState(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Ignore storage access restrictions
    }
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent<UiLocaleCode>("monty-locale-change", { detail: next }));
    }
  };

  return {
    locale,
    setLocale,
    t: getMontyMessages(locale),
  };
}
