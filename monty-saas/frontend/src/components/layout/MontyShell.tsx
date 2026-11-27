"use client";

import React, { useState } from "react";
import {
  Bot,
  Building2,
  Calculator,
  CalendarCheck,
  Download,
  FileText,
  FolderKanban,
  GitCompareArrows,
  Globe2,
  Handshake,
  LayoutDashboard,
  Menu,
  Newspaper,
  Settings,
  ShieldCheck,
  Store,
  UserCheck,
  Users,
  Workflow,
  X,
} from "lucide-react";
import Link from "next/link";
import { SUPPORTED_LOCALES, useMontyLocale, type UiLocaleCode } from "@/lib/i18n";
import type { ModuleFeatureFlag, MontyRole, NavSectionKey } from "@/lib/types";

interface NavItem {
  key: NavSectionKey;
  label: string;
  phaseBadge: string;
  icon: React.ComponentType<{ className?: string }>;
}

const NAV_ITEMS: NavItem[] = [
  { key: "dashboard", label: "Dashboard", phaseBadge: "Phase 1", icon: LayoutDashboard },
  { key: "actualites", label: "Veille & Actus Pro", phaseBadge: "Double Public", icon: Newspaper },
  { key: "crm", label: "CRM", phaseBadge: "Phase 2", icon: Users },
  { key: "buyers", label: "Acquéreurs", phaseBadge: "Phase 3", icon: UserCheck },
  { key: "sellers", label: "Vendeurs", phaseBadge: "Phase 3", icon: FolderKanban },
  { key: "properties", label: "Biens", phaseBadge: "Phase 3", icon: Building2 },
  { key: "matching", label: "Matching", phaseBadge: "Phase 4", icon: GitCompareArrows },
  { key: "estimation", label: "Estimation & Outils", phaseBadge: "6 Simulateurs", icon: Calculator },
  { key: "visits", label: "Visites", phaseBadge: "Phase 3", icon: CalendarCheck },
  { key: "transactions", label: "Transactions", phaseBadge: "Phase 3", icon: Handshake },
  { key: "ai", label: "Assistant MON-AGENT", phaseBadge: "Phase 4", icon: Bot },
  { key: "marketplace", label: "Marketplace", phaseBadge: "Phase 5", icon: Store },
  { key: "automations", label: "Automatisations", phaseBadge: "Phase 2", icon: Workflow },
  { key: "documents", label: "Documents", phaseBadge: "Phase 3", icon: FileText },
  { key: "admin", label: "Administration", phaseBadge: "Phase 1/5", icon: Settings },
];

interface MontyShellProps {
  activeSection: NavSectionKey;
  onSelectSection: (key: NavSectionKey) => void;
  agencyId: string;
  role: MontyRole | "VISITEUR";
  activeModuleFlags?: ModuleFeatureFlag[];
  children: React.ReactNode;
}

export function MontyShell({
  activeSection,
  onSelectSection,
  agencyId,
  role,
  activeModuleFlags,
  children,
}: MontyShellProps) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { locale, setLocale, t } = useMontyLocale();

  const flagMap = new Map((activeModuleFlags ?? []).map((f) => [f.module_key, f]));
  const canSeeAdministration = ["SUPER_ADMIN", "DIRECTOR", "ADMIN"].includes(role);
  const visibleNavItems = NAV_ITEMS.filter((item) => {
    if (item.key === "dashboard") return true;
    if (item.key === "admin") return canSeeAdministration;
    const flag = flagMap.get(item.key);
    return flag ? flag.visible : true;
  });

  return (
    <div className="min-h-screen flex flex-col lg:flex-row bg-ivoryWarm text-obsidianInk">
      {/* Barre latérale Desktop */}
      <aside className="hidden lg:flex lg:sticky lg:top-0 lg:h-screen lg:w-72 lg:flex-col bg-emeraldDeep text-ivoryWarm border-r border-emeraldMid shrink-0 shadow-xl shadow-emeraldDeep/10">
        <div className="px-6 py-6 border-b border-emeraldMid">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-goldAntique/20 border border-goldAntique/60 flex items-center justify-center text-goldChampagne font-display font-bold text-lg">
              M
            </div>
            <div>
              <h1 className="font-display text-lg font-bold tracking-tight text-ivoryWarm">
                MON-AGENT PRO
              </h1>
              <p className="text-xs text-goldChampagne/90">
                France • Portugal (Prêt)
              </p>
            </div>
          </div>
          <div className="mt-4 rounded-xl bg-emeraldMid/90 border border-goldAntique/30 px-3 py-2 text-xs">
            <div className="flex items-center justify-between text-goldChampagne">
              <span className="font-semibold">{t.activeTenant}</span>
              <span className="inline-flex items-center gap-1 rounded bg-emeraldSurface px-1.5 py-0.5 text-[10px] font-semibold">
                <ShieldCheck className="w-3 h-3" /> {role}
              </span>
            </div>
            <p className="mt-1 text-ivoryWarm/80 truncate">{agencyId}</p>
            <div className="mt-2 flex items-center gap-1">
              {SUPPORTED_LOCALES.map((loc) => (
                <button
                  key={loc.code}
                  type="button"
                  onClick={() => setLocale(loc.code as UiLocaleCode)}
                  className={`flex-1 py-0.5 rounded text-[10px] font-bold transition ${
                    locale === loc.code
                      ? "bg-goldAntique text-emeraldDeep"
                      : "bg-emeraldDeep/60 text-ivoryWarm/75 hover:text-goldChampagne"
                  }`}
                >
                  {loc.short}
                </button>
              ))}
            </div>
          </div>
        </div>

        <nav aria-label="Navigation principale professionnelle" className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
          {visibleNavItems.map((item) => {
            const Icon = item.icon;
            const active = activeSection === item.key;
            const flag = flagMap.get(item.key);
            const badgeText =
              flag?.status === "EN_DEVELOPPEMENT"
                ? "En dév."
                : flag?.status === "EN_TEST"
                ? "En test"
                : item.phaseBadge;
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => onSelectSection(item.key)}
                className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-sm transition ${
                  active
                    ? "bg-emeraldSurface text-goldChampagne font-semibold border border-goldAntique/40"
                    : "text-ivoryWarm/80 hover:bg-emeraldMid/70 hover:text-ivoryWarm"
                }`}
              >
                <span className="flex items-center gap-2.5">
                  <Icon className="w-4 h-4 shrink-0" />
                  <span>{item.label}</span>
                </span>
                <span
                  className={`text-[10px] px-1.5 py-0.5 rounded ${
                    flag?.status === "EN_TEST"
                      ? "bg-amber-500/20 text-amber-200"
                      : flag?.status === "EN_DEVELOPPEMENT"
                      ? "bg-slate-600/60 text-slate-200"
                      : "bg-emeraldDeep/60 text-ivoryWarm/70"
                  }`}
                >
                  {badgeText}
                </span>
              </button>
            );
          })}
        </nav>

        <div className="p-3 border-t border-emeraldMid space-y-1.5 text-xs">
          <Link
            href="/telecharger"
            className="w-full flex items-center gap-2 px-3 py-2 rounded-xl bg-goldAntique text-emeraldDeep font-bold hover:bg-goldChampagne transition"
          >
            <Download className="w-4 h-4 shrink-0" />
            <span>{t.navDownloadFull}</span>
          </Link>
          <Link
            href="/site"
            className="w-full flex items-center gap-2 px-3 py-2 rounded-xl bg-goldAntique/15 border border-goldAntique/40 text-goldChampagne font-semibold hover:bg-goldAntique/25 transition"
          >
            <Globe2 className="w-4 h-4 shrink-0" />
            <span>{t.navPublicSite}</span>
          </Link>
          <div className="grid grid-cols-2 gap-1.5">
            <Link
              href="/actualites"
              className="text-center px-2 py-1.5 rounded-lg bg-emeraldMid/70 text-ivoryWarm/85 hover:text-goldChampagne"
            >
              {t.navPublicNewsShort}
            </Link>
            <Link
              href="/simulateurs"
              className="text-center px-2 py-1.5 rounded-lg bg-emeraldMid/70 text-ivoryWarm/85 hover:text-goldChampagne"
            >
              {t.navSimulatorsShort}
            </Link>
          </div>
        </div>
      </aside>

      {/* En-tête mobile */}
      <header className="lg:hidden sticky top-0 z-30 bg-emeraldDeep text-ivoryWarm px-4 py-3 flex items-center justify-between border-b border-emeraldMid">
        <div className="flex items-center gap-2.5">
          <div className="h-8 w-8 rounded-lg bg-goldAntique/20 border border-goldAntique/60 flex items-center justify-center text-goldChampagne font-display font-bold">
            M
          </div>
          <div>
            <p className="font-display font-bold text-sm">MON-AGENT PRO</p>
            <p className="text-[11px] text-goldChampagne">{role} • {agencyId}</p>
          </div>
        </div>
        <button
          type="button"
          aria-label="Ouvrir le menu de navigation"
          onClick={() => setMobileMenuOpen((v) => !v)}
          className="p-2 rounded-lg bg-emeraldMid text-ivoryWarm"
        >
          {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </header>

      {mobileMenuOpen && (
        <nav aria-label="Navigation mobile" className="lg:hidden bg-emeraldDeep text-ivoryWarm px-4 py-3 border-b border-emeraldMid grid grid-cols-2 gap-2">
          {visibleNavItems.map((item) => {
            const Icon = item.icon;
            const active = activeSection === item.key;
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => {
                  onSelectSection(item.key);
                  setMobileMenuOpen(false);
                }}
                className={`flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-left ${
                  active ? "bg-emeraldSurface text-goldChampagne font-semibold" : "bg-emeraldMid/50 text-ivoryWarm/85"
                }`}
              >
                <Icon className="w-4 h-4 shrink-0" />
                <span className="truncate">{item.label}</span>
              </button>
            );
          })}
          <Link
            href="/telecharger"
            onClick={() => setMobileMenuOpen(false)}
            className="col-span-2 flex items-center justify-center gap-2 px-3 py-2 rounded-lg text-xs font-bold bg-goldAntique text-emeraldDeep"
          >
            <Download className="w-4 h-4 shrink-0" />
            <span>Télécharger l&apos;application (APK)</span>
          </Link>
        </nav>
      )}

      {/* Contenu principal */}
      <main className="flex-1 min-w-0 p-4 sm:p-6 lg:p-8 max-w-[1600px] mx-auto w-full">
        {children}
      </main>
    </div>
  );
}
