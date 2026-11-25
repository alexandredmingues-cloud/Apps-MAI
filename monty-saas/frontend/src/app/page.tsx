"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { Calculator, CheckCircle2, Database, Globe2, Lock, Newspaper, Server, Shield } from "lucide-react";
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
import type { HealthStatus, HcsfResult, MontyRole, NavSectionKey, SessionInfo } from "@/lib/types";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function MontySaasCockpitPage() {
  const [activeSection, setActiveSection] = useState<NavSectionKey>("dashboard");
  const [agencyId, setAgencyId] = useState("agency-france-reseau");
  const [role, setRole] = useState<MontyRole>("DIRECTOR");
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

  useEffect(() => {
    montyApi
      .health()
      .then((res) => {
        setHealth(res);
        setApiError(null);
      })
      .catch((err) => {
        setApiError(err instanceof Error ? err.message : "Backend API hors ligne");
      });

    const stored = loadPersistedSession();
    if (stored) {
      setSession(stored);
      setAgencyId(stored.agency_id);
      setRole(stored.role);
      montyApi.getMe().catch(() => {
        persistSession(null);
        setSession(null);
      });
    }
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
    } catch (err) {
      setAuthFormError(err instanceof Error ? err.message : "Échec d'authentification");
    } finally {
      setAuthLoading(false);
    }
  }

  function handleLogout() {
    persistSession(null);
    setSession(null);
    setAuthFormError(null);
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

  return (
    <MontyShell
      activeSection={activeSection}
      onSelectSection={setActiveSection}
      agencyId={agencyId}
      role={role}
    >
      <div className="space-y-6">
        {/* Barre d'accès rapide Site Web Public & Double Public */}
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-2xl bg-ivoryCard border border-borderSubtle px-4 py-3 text-xs">
          <span className="font-semibold text-emeraldDeep">
            Architecture Multi-Pays : 🇫🇷 France entière (Active) • 🇵🇹 Portugal (Module isolé prêt)
          </span>
          <div className="flex flex-wrap items-center gap-2">
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

        {activeSection === "actualites" ? (
          <NewsHubExperience audience="pro" role={role} initialCountry="FR" />
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
                    API FastAPI : {health ? "En ligne" : "Non démarrée"}
                  </span>
                  <span className="inline-flex items-center gap-1.5 rounded-xl bg-emeraldSurface px-3 py-2 text-xs font-medium border border-goldAntique/30">
                    <Database className="w-3.5 h-3.5 text-goldChampagne" />
                    Stockage : {health?.storage_mode === "postgresql_rls" ? "PostgreSQL RLS Actif" : "Mémoire locale (Dev)"}
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

                {health?.dev_auth_enabled !== false && (
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

            {/* Statut transparent du plan de livraison */}
            <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5">
              <div className="flex items-center gap-2 text-emeraldDeep font-display font-bold text-lg">
                <Lock className="w-5 h-5 text-goldAntique" />
                Plateforme Monty Phase 1 + Phase 2 prête au lancement ({activeSection.toUpperCase()})
              </div>
              <p className="mt-2 text-sm text-slateMuted">
                Socle multi-pays FR/PT + Site Web Public (`/site`, `/annonces`, `/publier`), Centre d&apos;Actualités Double Public (`/actualites`, `/pro/actualites`),
                6 Simulateurs (`/simulateurs`), CRM Multi-Agences avec notes privées isolées, relances automatiques, import/export CSV sécurisé et conformité RGPD complète.
              </p>
            </section>
          </>
        )}
      </div>
    </MontyShell>
  );
}
