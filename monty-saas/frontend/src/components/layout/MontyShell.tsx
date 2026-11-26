"use client";

import React, { useState } from "react";
import {
  Bot,
  Building2,
  Calculator,
  CalendarCheck,
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
import type { MontyRole, NavSectionKey } from "@/lib/types";

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
  { key: "ai", label: "Intelligence artificielle", phaseBadge: "Phase 4", icon: Bot },
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
  children: React.ReactNode;
}

export function MontyShell({
  activeSection,
  onSelectSection,
  agencyId,
  role,
  children,
}: MontyShellProps) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <div className="min-h-screen flex flex-col lg:flex-row bg-ivoryWarm text-obsidianInk">
      {/* Barre latérale Desktop */}
      <aside className="hidden lg:flex lg:w-72 lg:flex-col bg-emeraldDeep text-ivoryWarm border-r border-emeraldMid shrink-0">
        <div className="px-6 py-6 border-b border-emeraldMid">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-goldAntique/20 border border-goldAntique/60 flex items-center justify-center text-goldChampagne font-display font-bold text-lg">
              M
            </div>
            <div>
              <h1 className="font-display text-lg font-bold tracking-tight text-ivoryWarm">
                MONTY SAAS
              </h1>
              <p className="text-xs text-goldChampagne/90">
                France • Portugal (Prêt)
              </p>
            </div>
          </div>
          <div className="mt-4 rounded-xl bg-emeraldMid/90 border border-goldAntique/30 px-3 py-2 text-xs">
            <div className="flex items-center justify-between text-goldChampagne">
              <span className="font-semibold">Tenant actif</span>
              <span className="inline-flex items-center gap-1 rounded bg-emeraldSurface px-1.5 py-0.5 text-[10px] font-semibold">
                <ShieldCheck className="w-3 h-3" /> {role}
              </span>
            </div>
            <p className="mt-1 text-ivoryWarm/80 truncate">{agencyId}</p>
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const active = activeSection === item.key;
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
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-emeraldDeep/60 text-ivoryWarm/70">
                  {item.phaseBadge}
                </span>
              </button>
            );
          })}
        </nav>

        <div className="p-3 border-t border-emeraldMid space-y-1.5 text-xs">
          <Link
            href="/site"
            className="w-full flex items-center gap-2 px-3 py-2 rounded-xl bg-goldAntique/15 border border-goldAntique/40 text-goldChampagne font-semibold hover:bg-goldAntique/25 transition"
          >
            <Globe2 className="w-4 h-4 shrink-0" />
            <span>Site Web Public</span>
          </Link>
          <div className="grid grid-cols-2 gap-1.5">
            <Link
              href="/actualites"
              className="text-center px-2 py-1.5 rounded-lg bg-emeraldMid/70 text-ivoryWarm/85 hover:text-goldChampagne"
            >
              Actus Public
            </Link>
            <Link
              href="/simulateurs"
              className="text-center px-2 py-1.5 rounded-lg bg-emeraldMid/70 text-ivoryWarm/85 hover:text-goldChampagne"
            >
              Simulateurs
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
            <p className="font-display font-bold text-sm">MONTY SAAS</p>
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
        <div className="lg:hidden bg-emeraldDeep text-ivoryWarm px-4 py-3 border-b border-emeraldMid grid grid-cols-2 gap-2">
          {NAV_ITEMS.map((item) => {
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
        </div>
      )}

      {/* Contenu principal */}
      <main className="flex-1 min-w-0 p-4 sm:p-6 lg:p-8 max-w-7xl mx-auto w-full">
        {children}
      </main>
    </div>
  );
}
