"use client";

import React from "react";
import Link from "next/link";
import { Briefcase, Building2, Calculator, Download, Globe2, Home, Newspaper, PlusCircle, ShieldCheck, UserCircle } from "lucide-react";
import { SUPPORTED_LOCALES, useMontyLocale, type UiLocaleCode } from "@/lib/i18n";

interface PublicWebHeaderProps {
  activePath: "site" | "annonces" | "publier" | "actualites" | "simulateurs" | "coproprietes" | "telecharger" | "mon-compte";
}

export function PublicWebHeader({ activePath }: PublicWebHeaderProps) {
  const { locale, setLocale, t } = useMontyLocale();

  return (
    <header className="sticky top-0 z-40 bg-emeraldDeep/95 backdrop-blur border-b border-goldAntique/30 text-ivoryWarm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        <Link href="/site" className="flex items-center gap-3 shrink-0">
          <div className="h-9 w-9 rounded-xl bg-goldAntique/20 border border-goldAntique/60 flex items-center justify-center text-goldChampagne font-display font-bold text-lg">
            M
          </div>
          <div>
            <p className="font-display font-bold text-base tracking-tight leading-none">
              MON-AGENT
            </p>
            <p className="text-[11px] text-goldChampagne/90 mt-0.5">
              {t.tagline}
            </p>
          </div>
        </Link>

        <nav className="hidden xl:flex items-center gap-1 text-xs font-medium">
          <Link
            href="/site"
            className={`px-2.5 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "site"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Globe2 className="w-3.5 h-3.5" />
            {t.navHome}
          </Link>
          <Link
            href="/annonces"
            className={`px-2.5 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "annonces"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Home className="w-3.5 h-3.5" />
            {t.navListings}
          </Link>
          <Link
            href="/publier"
            className={`px-2.5 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "publier"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <PlusCircle className="w-3.5 h-3.5" />
            {t.navPublish}
          </Link>
          <Link
            href="/coproprietes"
            className={`px-2.5 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "coproprietes"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Building2 className="w-3.5 h-3.5" />
            {t.navCopro}
          </Link>
          <Link
            href="/actualites"
            className={`px-2.5 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "actualites"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Newspaper className="w-3.5 h-3.5" />
            {t.navNews}
          </Link>
          <Link
            href="/simulateurs"
            className={`px-2.5 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "simulateurs"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Calculator className="w-3.5 h-3.5" />
            {t.navSimulators}
          </Link>
          <Link
            href="/telecharger"
            className={`px-2.5 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "telecharger"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Download className="w-3.5 h-3.5" />
            {t.navDownloadApp}
          </Link>
        </nav>

        <div className="flex items-center gap-2">
          <div
            role="group"
            aria-label="Language selector"
            className="hidden sm:inline-flex items-center rounded-xl border border-goldAntique/35 bg-emeraldMid/70 p-0.5 text-[10px] font-bold"
          >
            {SUPPORTED_LOCALES.map((loc) => (
              <button
                key={loc.code}
                type="button"
                onClick={() => setLocale(loc.code as UiLocaleCode)}
                className={`px-2 py-1 rounded-lg transition ${
                  locale === loc.code ? "bg-goldAntique text-emeraldDeep" : "text-ivoryWarm/80 hover:text-goldChampagne"
                }`}
              >
                {loc.short}
              </button>
            ))}
          </div>
          <Link
            href="/mon-compte"
            className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold border transition ${
              activePath === "mon-compte"
                ? "bg-emeraldSurface text-goldChampagne border-goldAntique"
                : "border-goldAntique/40 text-ivoryWarm hover:bg-emeraldMid"
            }`}
          >
            <UserCircle className="w-4 h-4" />
            <span className="hidden sm:inline">{t.navMySpace}</span>
          </Link>
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-goldAntique text-emeraldDeep text-xs sm:text-sm font-bold hover:bg-goldChampagne transition"
          >
            <Briefcase className="w-4 h-4" />
            {t.navProSaas}
          </Link>
        </div>
      </div>

      {/* Navigation mobile compacte */}
      <div className="xl:hidden px-4 pb-2.5 flex items-center gap-2 overflow-x-auto text-xs font-semibold whitespace-nowrap">
        <Link href="/site" className={`px-3 py-1.5 rounded-lg ${activePath === "site" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          {t.navHome}
        </Link>
        <Link href="/annonces" className={`px-3 py-1.5 rounded-lg ${activePath === "annonces" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          {t.navListings}
        </Link>
        <Link href="/publier" className={`px-3 py-1.5 rounded-lg ${activePath === "publier" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          {t.navPublish}
        </Link>
        <Link href="/coproprietes" className={`px-3 py-1.5 rounded-lg ${activePath === "coproprietes" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          {t.navCopro}
        </Link>
        <Link href="/actualites" className={`px-3 py-1.5 rounded-lg ${activePath === "actualites" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          {t.navNews}
        </Link>
        <Link href="/simulateurs" className={`px-3 py-1.5 rounded-lg ${activePath === "simulateurs" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          {t.navSimulators}
        </Link>
        <Link href="/telecharger" className={`px-3 py-1.5 rounded-lg inline-flex items-center gap-1 ${activePath === "telecharger" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          <Download className="w-3 h-3" />
          APK
        </Link>
        <Link href="/mon-compte" className={`px-3 py-1.5 rounded-lg ${activePath === "mon-compte" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          {t.navMySpace}
        </Link>
      </div>
    </header>
  );
}

export function PublicWebFooter() {
  const { t } = useMontyLocale();
  return (
    <footer className="bg-emeraldDeep text-ivoryWarm/85 border-t border-goldAntique/30 mt-14">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 grid grid-cols-1 md:grid-cols-3 gap-8 text-xs">
        <div className="space-y-2">
          <p className="font-display text-base font-bold text-ivoryWarm">MON-AGENT</p>
          <p className="text-goldChampagne">« L&apos;humain avant tout. La technologie au service de l'immobilier. »</p>
          <p className="text-ivoryWarm/70">{t.footerSummary}</p>
        </div>
        <div className="space-y-2">
          <p className="font-bold text-ivoryWarm uppercase tracking-wider">{t.footerQuickAccess}</p>
          <ul className="space-y-1.5">
            <li><Link href="/site" className="hover:text-goldChampagne">{t.navHome}</Link></li>
            <li><Link href="/annonces" className="hover:text-goldChampagne">{t.navListings}</Link></li>
            <li><Link href="/publier" className="hover:text-goldChampagne">{t.navPublish}</Link></li>
            <li><Link href="/coproprietes" className="hover:text-goldChampagne">{t.navCopro}</Link></li>
            <li><Link href="/mon-compte" className="hover:text-goldChampagne">{t.navMySpace}</Link></li>
            <li><Link href="/actualites" className="hover:text-goldChampagne">{t.navNews}</Link></li>
            <li><Link href="/simulateurs" className="hover:text-goldChampagne">{t.navSimulators}</Link></li>
            <li><Link href="/telecharger" className="hover:text-goldChampagne">{t.navDownloadFull} (APK)</Link></li>
            <li><Link href="/" className="hover:text-goldChampagne">{t.navProSaas}</Link></li>
          </ul>
        </div>
        <div className="space-y-2">
          <p className="font-bold text-ivoryWarm uppercase tracking-wider flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-goldChampagne" />
            {t.footerPrivacyTitle}
          </p>
          <p className="text-ivoryWarm/70 leading-relaxed">{t.footerPrivacyBody}</p>
        </div>
      </div>
    </footer>
  );
}
