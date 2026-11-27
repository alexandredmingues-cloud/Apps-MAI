"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { Bell, Calculator, CalendarDays, CheckCircle2, Database, Download, Globe2, Newspaper, Server, Shield, TrendingUp } from "lucide-react";
import { LogOut } from "lucide-react";
import { AdminSaasConsole } from "@/components/admin/AdminSaasConsole";
import { AgentsAndScoutPanel } from "@/components/ai/AgentsAndScoutPanel";
import { RealEstateSimulatorsSuite } from "@/components/calculators/RealEstateSimulatorsSuite";
import { CrmCockpitPanel } from "@/components/crm/CrmCockpitPanel";
import { MontyShell } from "@/components/layout/MontyShell";
import { PreEtatDateWorkspace } from "@/components/legal/PreEtatDateWorkspace";
import { MarketplaceAndPublishView } from "@/components/marketplace/MarketplaceAndPublishView";
import { NewsHubExperience } from "@/components/news/NewsHubExperience";
import { PortfolioMatchingPipelinePanel } from "@/components/portfolio/PortfolioMatchingPipelinePanel";
import { loadPersistedSession, montyApi, persistSession } from "@/lib/api-client";
import type {
  AdminOverview,
  AgencyNotification,
  HealthStatus,
  HcsfResult,
  ModuleFeatureFlag,
  MontyRole,
  NavSectionKey,
  SessionInfo,
  UnifiedAgendaItem,
} from "@/lib/types";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function MontySaasCockpitPage() {
  const [activeSection, setActiveSection] = useState<NavSectionKey>("dashboard");
  const [agencyId, setAgencyId] = useState("agency-france-reseau");
  const [role, setRole] = useState<MontyRole>("BUYER");
  const [session, setSession] = useState<SessionInfo | null>(null);
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);

  const [income, setIncome] = useState(6800);
  const [downPayment, setDownPayment] = useState(95000);
  const [durationYears, setDurationYears] = useState(25);
  const [hcsfResult, setHcsfResult] = useState<HcsfResult | null>(null);
  const [calcLoading, setCalcLoading] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [authFormError, setAuthFormError] = useState<string | null>(null);
  const [authMode, setAuthMode] = useState<"login" | "register">("login");
  const [authEmail, setAuthEmail] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [authAgencyName, setAuthAgencyName] = useState("");
  const [authDisplayName, setAuthDisplayName] = useState("");
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [notifications, setNotifications] = useState<AgencyNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [agendaItems, setAgendaItems] = useState<UnifiedAgendaItem[]>([]);
  const [moduleFlags, setModuleFlags] = useState<ModuleFeatureFlag[]>([]);

  async function refreshDashboardWidgets() {
    try {
      const [ov, notifRes, agRes] = await Promise.all([
        montyApi.getAdminOverview(),
        montyApi.listNotifications(),
        montyApi.getUnifiedAgenda(),
      ]);
      setOverview(ov);
      setNotifications(notifRes.notifications);
      setUnreadCount(notifRes.unread_count);
      setAgendaItems(agRes.items);
    } catch {
      // Rôle particulier ou API hors ligne : widgets Pro masqués
      setOverview(null);
    }
  }

  useEffect(() => {
    montyApi
      .health()
      .then((res) => {
        setHealth(res);
        setApiError(null);
      })
      .catch(() => {
        setHealth(null);
        setApiError("API backend en veille ou non configurée : mode démonstration local sécurisé actif.");
      });

    const stored = loadPersistedSession();
    if (stored) {
      setSession(stored);
      setAgencyId(stored.agency_id);
      setRole(stored.role);
      montyApi
        .getMe()
        .then((me) => {
          const verifiedSession: SessionInfo = {
            ...stored,
            user_id: me.user_id,
            agency_id: me.agency_id,
            role: me.role,
            permissions: me.permissions,
          };
          persistSession(verifiedSession);
          setSession(verifiedSession);
          setAgencyId(me.agency_id);
          setRole(me.role);
          refreshDashboardWidgets();
        })
        .catch(() => {
          persistSession(null);
          setSession(null);
          setRole("BUYER");
        });
    }

    montyApi
      .getActiveModuleFlags(stored?.agency_id ?? agencyId)
      .then((res) => setModuleFlags(res.modules))
      .catch(() => {});
  }, []);

  async function handleConnectDevSession(selectedRole: MontyRole) {
    setApiError(null);
    setAuthFormError(null);
    setAuthLoading(true);
    try {
      const issued = await montyApi.issueDevSession({
        user_id: `usr-${selectedRole.toLowerCase()}`,
        agency_id: agencyId,
        role: selectedRole,
      });
      persistSession(issued);
      setSession(issued);
      setRole(issued.role);
      refreshDashboardWidgets();
    } catch (err) {
      setApiError(err instanceof Error ? err.message : "Erreur de connexion API");
    } finally {
      setAuthLoading(false);
    }
  }

  async function handleRealAuthSubmit(e: React.FormEvent) {
    e.preventDefault();
    setApiError(null);
    setAuthFormError(null);

    const trimmedEmail = authEmail.trim();
    if (!EMAIL_RE.test(trimmedEmail)) {
      setAuthFormError("Veuillez saisir une adresse e-mail professionnelle valide.");
      return;
    }
    if (authPassword.length < 8) {
      setAuthFormError("Le mot de passe doit contenir au moins 8 caractères.");
      return;
    }
    if (authMode === "register") {
      if (authAgencyName.trim().length < 2) {
        setAuthFormError("Le nom de l'agence doit contenir au moins 2 caractères.");
        return;
      }
      if (authDisplayName.trim().length < 2) {
        setAuthFormError("Votre nom complet doit contenir au moins 2 caractères.");
        return;
      }
    }

    setAuthLoading(true);
    try {
      const issued =
        authMode === "register"
          ? await montyApi.registerAgency({
              agency_name: authAgencyName.trim(),
              email: trimmedEmail,
              password: authPassword,
              display_name: authDisplayName.trim(),
            })
          : await montyApi.login({
              email: trimmedEmail,
              password: authPassword,
            });
      persistSession(issued);
      setSession(issued);
      setAgencyId(issued.agency_id);
      setRole(issued.role);
      refreshDashboardWidgets();
    } catch (err) {
      setAuthFormError(err instanceof Error ? err.message : "Échec d'authentification");
    } finally {
      setAuthLoading(false);
    }
  }

  function handleLogout() {
    persistSession(null);
    setSession(null);
    setRole("BUYER");
    setOverview(null);
    setNotifications([]);
    setUnreadCount(0);
    setAgendaItems([]);
    setAuthFormError(null);
    setActiveSection("dashboard");
  }

  async function handleMarkNotificationRead(notif: AgencyNotification) {
    try {
      const updated = await montyApi.markNotificationRead(notif.id);
      setNotifications((prev) => prev.map((n) => (n.id === notif.id ? updated : n)));
      setUnreadCount((c) => Math.max(0, c - (notif.is_read ? 0 : 1)));
    } catch (err) {
      setApiError(err instanceof Error ? err.message : "Impossible de marquer la notification comme lue.");
    }
  }

  async function handleRunHcsf() {
    if (income <= 0 || durationYears < 5 || durationYears > 30) {
      setApiError("Vérifiez les revenus (> 0 €) et la durée du prêt (5 à 30 ans).");
      return;
    }
    setCalcLoading(true);
    setApiError(null);
    try {
      const res = await montyApi.calculateHcsf({
        monthly_net_income: income,
        existing_monthly_credits: 0,
        down_payment: downPayment,
        duration_years: durationYears,
        interest_rate_percent: 3.35,
        insurance_rate_percent: 0.32,
        notary_rate_percent: 7.8,
        is_vefa_neuf: false,
      });
      setHcsfResult(res);
    } catch (err) {
      setApiError(err instanceof Error ? err.message : "Connectez une session API pour exécuter le calcul");
    } finally {
      setCalcLoading(false);
    }
  }

  const isProSession = Boolean(
    session && ["SUPER_ADMIN", "DIRECTOR", "ADMIN", "AGENT", "ASSISTANT"].includes(session.role)
  );
  const proRestrictedSections: NavSectionKey[] = [
    "crm",
    "buyers",
    "sellers",
    "properties",
    "matching",
    "visits",
    "transactions",
    "ai",
    "automations",
    "documents",
    "admin",
  ];
  const canAccessAgencyAdmin = Boolean(
    session && ["SUPER_ADMIN", "DIRECTOR", "ADMIN"].includes(session.role)
  );
  const requiresAdminAuth = activeSection === "admin" && !canAccessAgencyAdmin;
  const requiresProAuth =
    proRestrictedSections.includes(activeSection) && activeSection !== "admin" && !isProSession;

  return (
    <MontyShell
      activeSection={activeSection}
      onSelectSection={setActiveSection}
      agencyId={session ? session.agency_id : "Session non connectée"}
      role={session ? session.role : "VISITEUR"}
      activeModuleFlags={moduleFlags}
    >
      <div className="space-y-6">
        {/* Barre d'accès rapide Site Web Public & Double Public */}
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-2xl bg-ivoryCard border border-borderSubtle px-4 py-3 text-xs">
          <span className="font-semibold text-emeraldDeep">
            Architecture Multi-Pays : 🇫🇷 France entière (Active) • 🇵🇹 Portugal (Module isolé prêt)
          </span>
          <div className="flex flex-wrap items-center gap-2">
            <Link
              href="/telecharger"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-goldAntique text-emeraldDeep font-bold hover:bg-goldChampagne"
            >
              <Download className="w-3.5 h-3.5" />
              App Mobile APK
            </Link>
            <Link
              href="/site"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne font-semibold hover:bg-emeraldMid"
            >
              <Globe2 className="w-3.5 h-3.5" />
              Site Web Public
            </Link>
            <Link
              href="/actualites"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm text-emeraldDeep font-semibold hover:border-emeraldMid"
            >
              <Newspaper className="w-3.5 h-3.5" />
              Espace Particulier
            </Link>
            <Link
              href="/simulateurs"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-borderSubtle bg-ivoryWarm text-emeraldDeep font-semibold hover:border-emeraldMid"
            >
              <Calculator className="w-3.5 h-3.5" />
              6 Simulateurs
            </Link>
          </div>
        </div>

        {requiresAdminAuth ? (
          <section className="rounded-2xl bg-ivoryCard border border-amber-300 p-6 space-y-4" role="alert">
            <div className="flex items-center gap-2 text-emeraldDeep font-display font-bold text-xl">
              <Shield className="w-5 h-5 text-amber-700" />
              Accès administrateur requis
            </div>
            <p className="text-sm text-slateMuted">
              La console d’administration est réservée aux comptes Super Admin, Directeur et Administrateur.
              Connectez-vous avec un compte habilité. Les autorisations sont également contrôlées par l’API.
            </p>
            <button
              type="button"
              onClick={() => setActiveSection("dashboard")}
              className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid"
            >
              Retour au tableau de bord
            </button>
          </section>
        ) : requiresProAuth ? (
          <section className="rounded-2xl bg-ivoryCard border border-amber-300 p-6 space-y-4">
            <div className="flex items-center gap-2 text-emeraldDeep font-display font-bold text-xl">
              <Shield className="w-5 h-5 text-amber-700" />
              Session Monty Pro requise pour le module « {activeSection.toUpperCase()} »
            </div>
            <p className="text-sm text-slateMuted">
              {session
                ? `Votre compte connecté (${session.role}) appartient à l'espace Particulier/Vendeur et n'a pas accès au CRM, Pipeline, Pré-État Daté ou Administration d'agence.`
                : "Connectez un compte agence Monty Pro (Directeur, Admin, Agent ou Assistant) depuis le Dashboard pour accéder aux données cloisonnées par agence (PostgreSQL RLS)."}
            </p>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => setActiveSection("dashboard")}
                className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid"
              >
                Aller à la connexion Agence (Dashboard)
              </button>
              <Link
                href="/mon-compte"
                className="px-4 py-2.5 rounded-xl border border-emeraldDeep text-emeraldDeep text-xs font-bold hover:bg-ivoryWarm"
              >
                Ouvrir mon Espace Particulier
              </Link>
            </div>
          </section>
        ) : activeSection === "actualites" ? (
          <NewsHubExperience audience={isProSession ? "pro" : "public"} role={role} initialCountry="FR" />
        ) : activeSection === "ai" ? (
          <AgentsAndScoutPanel role={role} />
        ) : activeSection === "estimation" ? (
          <RealEstateSimulatorsSuite initialCountry="FR" />
        ) : activeSection === "documents" ? (
          <PreEtatDateWorkspace />
        ) : activeSection === "admin" ? (
          <AdminSaasConsole role={role} />
        ) : activeSection === "crm" || activeSection === "buyers" || activeSection === "sellers" || activeSection === "automations" ? (
          <CrmCockpitPanel
            agencyId={agencyId}
            role={role}
            initialRoleFilter={
              activeSection === "buyers" ? "BUYER" : activeSection === "sellers" ? "SELLER" : "ALL"
            }
            initialTab={activeSection === "automations" ? "followups" : "contacts"}
          />
        ) : activeSection === "properties" || activeSection === "matching" || activeSection === "visits" || activeSection === "transactions" ? (
          <PortfolioMatchingPipelinePanel initialTab={activeSection} />
        ) : activeSection === "marketplace" ? (
          <MarketplaceAndPublishView
            initialTab="search"
            initialCountry="FR"
            showTabs={true}
            audienceLabel="Monty Pro • Catalogue Agences & Mobilité"
          />
        ) : (
          <>
            {/* En-tête Premium */}
            <section className="rounded-2xl bg-gradient-to-r from-emeraldDeep to-emeraldMid text-ivoryWarm p-6 border border-goldAntique/40 shadow-sm">
              <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-widest text-goldChampagne">
                    Phase 1 + Centre d&apos;Information &amp; Site Web — Socle SaaS Multi-Agences
                  </p>
                  <h2 className="mt-1 font-display text-2xl sm:text-3xl font-bold">
                    Monty Immobilier • Cockpit Web Pro
                  </h2>
                  <p className="mt-1 text-sm text-ivoryWarm/80">
                    « L&apos;humain avant tout. La technologie au service du conseiller immobilier. »
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center gap-1.5 rounded-xl bg-emeraldSurface px-3 py-2 text-xs font-medium border border-goldAntique/30">
                    <Server className="w-3.5 h-3.5 text-goldChampagne" />
                    API FastAPI : {health ? "En ligne" : "Mode local / réveil"}
                  </span>
                  <span className="inline-flex items-center gap-1.5 rounded-xl bg-emeraldSurface px-3 py-2 text-xs font-medium border border-goldAntique/30">
                    <Database className="w-3.5 h-3.5 text-goldChampagne" />
                    Stockage : {health?.storage_mode === "postgresql_rls" ? "PostgreSQL RLS Actif" : health ? "Mémoire locale (Dev)" : "Catalogue local"}
                  </span>
                </div>
              </div>
            </section>

            {apiError && (
              <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-3 text-sm text-amber-900">
                {apiError}
              </div>
            )}

            {/* Authentification & Contexte Multi-Tenant */}
            <section className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
                    <Shield className="w-5 h-5 text-goldAntique" />
                    Authentification Agence &amp; RBAC
                  </h3>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-emeraldDeep/10 text-emeraldDeep">
                      {session ? "Session JWT active" : "Déconnecté"}
                    </span>
                    {session && (
                      <button
                        type="button"
                        onClick={handleLogout}
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border border-borderSubtle text-xs font-semibold text-obsidianInk hover:border-red-400 hover:text-red-700"
                      >
                        <LogOut className="w-3.5 h-3.5" />
                        Déconnexion
                      </button>
                    )}
                  </div>
                </div>

                <form onSubmit={handleRealAuthSubmit} noValidate className="space-y-2.5 text-sm border-b border-borderSubtle pb-4">
                  {authFormError && (
                    <div className="rounded-xl bg-red-50 border border-red-300 px-3 py-2 text-xs text-red-900">
                      {authFormError}
                    </div>
                  )}
                  <div className="flex gap-2 text-xs">
                    <button
                      type="button"
                      onClick={() => {
                        setAuthMode("login");
                        setAuthFormError(null);
                      }}
                      className={`px-3 py-2 rounded-lg font-semibold min-h-[40px] ${
                        authMode === "login" ? "bg-emeraldDeep text-goldChampagne" : "bg-ivoryWarm text-obsidianInk"
                      }`}
                    >
                      Connexion
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setAuthMode("register");
                        setAuthFormError(null);
                      }}
                      className={`px-3 py-2 rounded-lg font-semibold min-h-[40px] ${
                        authMode === "register" ? "bg-emeraldDeep text-goldChampagne" : "bg-ivoryWarm text-obsidianInk"
                      }`}
                    >
                      Créer une agence
                    </button>
                  </div>

                  {authMode === "register" && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      <input
                        type="text"
                        required
                        value={authAgencyName}
                        onChange={(e) => setAuthAgencyName(e.target.value)}
                        placeholder="Nom de l'agence"
                        className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5 text-xs"
                      />
                      <input
                        type="text"
                        required
                        value={authDisplayName}
                        onChange={(e) => setAuthDisplayName(e.target.value)}
                        placeholder="Votre nom complet"
                        className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5 text-xs"
                      />
                    </div>
                  )}

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <input
                      type="email"
                      required
                      value={authEmail}
                      onChange={(e) => setAuthEmail(e.target.value)}
                      placeholder="E-mail professionnel"
                      className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5 text-xs"
                    />
                    <input
                      type="password"
                      required
                      minLength={8}
                      value={authPassword}
                      onChange={(e) => setAuthPassword(e.target.value)}
                      placeholder="Mot de passe (≥ 8 caractères)"
                      className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5 text-xs"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={authLoading}
                    className="w-full sm:w-auto px-4 py-2.5 min-h-[44px] rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid disabled:opacity-50"
                  >
                    {authLoading
                      ? "Connexion en cours..."
                      : authMode === "register"
                      ? "Créer mon espace Agence (Directeur)"
                      : "Se connecter"}
                  </button>
                </form>

                {health?.dev_auth_enabled === true && (
                  <div className="space-y-3 text-sm">
                    <label className="block">
                      <span className="text-xs font-semibold text-slateMuted">Identifiant Agence (Dev Opt-In)</span>
                      <input
                        type="text"
                        value={agencyId}
                        onChange={(e) => setAgencyId(e.target.value)}
                        className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-sm focus:outline-none focus:border-emeraldDeep"
                      />
                    </label>

                    <div>
                      <span className="text-xs font-semibold text-slateMuted">Tester un rôle RBAC (ENABLE_DEV_AUTH=true)</span>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {(["DIRECTOR", "AGENT", "ASSISTANT", "BUYER", "SELLER"] as MontyRole[]).map((r) => (
                          <button
                            key={r}
                            type="button"
                            onClick={() => handleConnectDevSession(r)}
                            className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition ${
                              role === r && session
                                ? "bg-emeraldDeep text-goldChampagne border-emeraldDeep"
                                : "bg-ivoryWarm text-obsidianInk border-borderSubtle hover:border-emeraldMid"
                            }`}
                          >
                            {r}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>
                )}

                {session && (
                  <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 text-xs space-y-1">
                    <p className="font-semibold text-emeraldDeep flex items-center gap-1.5">
                      <CheckCircle2 className="w-4 h-4 text-emerald-700" />
                      Permissions actives ({session.permissions.length})
                    </p>
                    <p className="text-slateMuted break-words">
                      {session.permissions.join(" • ")}
                    </p>
                  </div>
                )}
              </div>

              {/* Calculateur HCSF connecté à l'API Python v1 */}
              <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
                <h3 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
                  <Calculator className="w-5 h-5 text-goldAntique" />
                  Moteur HCSF 35% (API FastAPI /api/v1/calculators/hcsf)
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
                  <label className="block">
                    <span className="text-xs font-semibold text-slateMuted">Revenus nets/mois (€)</span>
                    <input
                      type="number"
                      value={income}
                      onChange={(e) => setIncome(Number(e.target.value) || 0)}
                      className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs font-semibold text-slateMuted">Apport (€)</span>
                    <input
                      type="number"
                      value={downPayment}
                      onChange={(e) => setDownPayment(Number(e.target.value) || 0)}
                      className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs font-semibold text-slateMuted">Durée (ans)</span>
                    <input
                      type="number"
                      min={5}
                      max={27}
                      value={durationYears}
                      onChange={(e) => setDurationYears(Number(e.target.value) || 25)}
                      className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
                    />
                  </label>
                </div>
                <button
                  type="button"
                  disabled={calcLoading}
                  onClick={handleRunHcsf}
                  className="w-full sm:w-auto px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid transition disabled:opacity-50"
                >
                  {calcLoading ? "Calcul en cours..." : "Calculer via Backend FastAPI"}
                </button>

                {hcsfResult && (
                  <div className="rounded-xl bg-emeraldDeep text-ivoryWarm p-4 grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                    <div>
                      <p className="text-ivoryWarm/70">Prix bien max net</p>
                      <p className="text-base font-bold text-goldChampagne">
                        {hcsfResult.max_property_price_net.toLocaleString("fr-FR")} €
                      </p>
                    </div>
                    <div>
                      <p className="text-ivoryWarm/70">Capital empruntable</p>
                      <p className="text-base font-bold">
                        {hcsfResult.max_borrowed_capital.toLocaleString("fr-FR")} €
                      </p>
                    </div>
                    <div>
                      <p className="text-ivoryWarm/70">Mensualité max HCSF</p>
                      <p className="text-base font-bold">
                        {hcsfResult.max_monthly_loan_payment.toLocaleString("fr-FR")} € / mois
                      </p>
                    </div>
                  </div>
                )}
              </div>
            </section>

            {/* KPI Temps Réel + Notifications Interconnectées + Agenda Unifié */}
            {overview && (
              <section className="space-y-4">
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 text-xs">
                  <div className="rounded-xl bg-ivoryCard border border-borderSubtle p-3.5">
                    <p className="text-slateMuted">Biens actifs</p>
                    <p className="text-xl font-display font-bold text-emeraldDeep">{overview.properties_count}</p>
                  </div>
                  <div className="rounded-xl bg-ivoryCard border border-borderSubtle p-3.5">
                    <p className="text-slateMuted">Contacts CRM</p>
                    <p className="text-xl font-display font-bold text-emeraldDeep">{overview.active_contacts_count}</p>
                  </div>
                  <div className="rounded-xl bg-ivoryCard border border-borderSubtle p-3.5">
                    <p className="text-slateMuted">Visites prévues</p>
                    <p className="text-xl font-display font-bold text-emeraldDeep">{overview.scheduled_visits_count}</p>
                  </div>
                  <div className="rounded-xl bg-ivoryCard border border-borderSubtle p-3.5">
                    <p className="text-slateMuted">Pipeline</p>
                    <p className="text-lg font-display font-bold text-goldAntique">
                      {overview.pipeline_volume_euro.toLocaleString("fr-FR")} €
                    </p>
                  </div>
                  <div className="rounded-xl bg-ivoryCard border border-borderSubtle p-3.5">
                    <p className="text-slateMuted">Relances ouvertes</p>
                    <p className="text-xl font-display font-bold text-emeraldDeep">{overview.open_followups_count}</p>
                  </div>
                  <div className="rounded-xl bg-ivoryCard border border-borderSubtle p-3.5">
                    <p className="text-slateMuted">Nouveaux leads web</p>
                    <p className="text-xl font-display font-bold text-emeraldDeep">{overview.new_public_leads_count}</p>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
                    <div className="flex items-center justify-between">
                      <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
                        <Bell className="w-4 h-4 text-goldAntique" />
                        Notifications Agence ({unreadCount} non lue{unreadCount > 1 ? "s" : ""})
                      </h3>
                      <button
                        type="button"
                        onClick={refreshDashboardWidgets}
                        className="text-emeraldDeep font-semibold hover:underline"
                      >
                        Actualiser
                      </button>
                    </div>
                    {notifications.length === 0 ? (
                      <p className="text-slateMuted">Aucune alerte interne pour le moment.</p>
                    ) : (
                      <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                        {notifications.slice(0, 8).map((n) => (
                          <div
                            key={n.id}
                            className={`rounded-xl p-3 border flex items-start justify-between gap-2 ${
                              n.is_read ? "bg-ivoryWarm border-borderSubtle" : "bg-amber-50 border-amber-300"
                            }`}
                          >
                            <div className="space-y-0.5">
                              <p className="font-bold text-emeraldDeep">{n.title}</p>
                              <p className="text-slateMuted">{n.detail}</p>
                            </div>
                            <div className="flex items-center gap-1.5 shrink-0">
                              <button
                                type="button"
                                onClick={() => {
                                  if (!n.is_read) handleMarkNotificationRead(n);
                                  setActiveSection((n.link_section as NavSectionKey) || "dashboard");
                                }}
                                className="px-2 py-1 rounded-lg bg-emeraldDeep text-goldChampagne font-bold"
                              >
                                Ouvrir
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
                    <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
                      <CalendarDays className="w-4 h-4 text-goldAntique" />
                      Agenda Unifié (Visites • Relances • Notaires)
                    </h3>
                    {agendaItems.length === 0 ? (
                      <p className="text-slateMuted">Aucune échéance planifiée à venir.</p>
                    ) : (
                      <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                        {agendaItems.slice(0, 8).map((ag) => (
                          <button
                            key={ag.id}
                            type="button"
                            onClick={() => setActiveSection((ag.link_section as NavSectionKey) || "visits")}
                            className="w-full text-left rounded-xl p-3 bg-ivoryWarm border border-borderSubtle hover:border-emeraldDeep transition flex items-center justify-between gap-2"
                          >
                            <div>
                              <p className="font-bold text-obsidianInk">{ag.title}</p>
                              <p className="text-slateMuted">{ag.subtitle}</p>
                            </div>
                            <div className="text-right shrink-0">
                              <span className="px-2 py-0.5 rounded bg-emeraldDeep/10 text-emeraldDeep font-bold text-[10px]">
                                {ag.kind}
                              </span>
                              <p className="text-[11px] font-mono text-slateMuted mt-1">{ag.date_iso}</p>
                            </div>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </section>
            )}

            {/* Statut transparent du plan de livraison */}
            <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <div className="flex items-center gap-2 text-emeraldDeep font-display font-bold text-lg">
                  <TrendingUp className="w-5 h-5 text-goldAntique" />
                  Plateforme Monty Complète — Particuliers, Pro, Notaires, RNIC, Monty AI &amp; APK
                </div>
                <p className="mt-1.5 text-sm text-slateMuted">
                  Socle multi-pays FR/PT + Site Web Public (`/site`, `/annonces`, `/publier`, `/coproprietes`, `/telecharger`, `/mon-compte`),
                  Monty Price Index, Monty AI Core multi-moteurs, Annuaire Notaires &amp; RNIC, CRM Multi-Agences et Pipeline Transactions.
                </p>
              </div>
              <Link
                href="/telecharger"
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid shrink-0"
              >
                <Download className="w-4 h-4" />
                Télécharger l&apos;APK Android
              </Link>
            </section>
          </>
        )}
      </div>
    </MontyShell>
  );
}
