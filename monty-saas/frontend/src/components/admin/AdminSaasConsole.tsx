"use client";

import React, { useEffect, useState } from "react";
import {
  CreditCard,
  History,
  Power,
  Shield,
  Sliders,
  Trash2,
  UserPlus,
  Users,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import { HealthCheckDashboard } from "./HealthCheckDashboard";
import type {
  AgencyFeatureFlags,
  AgencySubscriptionInfo,
  AgentCatalogResponse,
  AuditLogEntry,
  HealthStatus,
  MontyRole,
  SaasPlanInfo,
  TeamInviteResult,
  TeamMemberInfo,
} from "@/lib/types";

export function AdminSaasConsole({ role }: { role: MontyRole }) {
  const [members, setMembers] = useState<TeamMemberInfo[]>([]);
  const [plans, setPlans] = useState<SaasPlanInfo[]>([]);
  const [subscription, setSubscription] = useState<AgencySubscriptionInfo | null>(null);
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [agentsCat, setAgentsCat] = useState<AgentCatalogResponse | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [featureFlags, setFeatureFlags] = useState<AgencyFeatureFlags | null>(null);
  const [checkoutBusyPlan, setCheckoutBusyPlan] = useState<string | null>(null);

  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteName, setInviteName] = useState("");
  const [inviteRole, setInviteRole] = useState<MontyRole>("AGENT");
  const [lastInvite, setLastInvite] = useState<TeamInviteResult | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  async function refreshAdminData() {
    setErrorMsg(null);
    try {
      const [h, p] = await Promise.all([montyApi.health(), montyApi.listBillingPlans()]);
      setHealth(h);
      setPlans(p.plans);

      const [m, sub, cat, flags, logsRes] = await Promise.all([
        montyApi.listTeamMembers(),
        montyApi.getSubscription(),
        montyApi.getAgentsCatalog(),
        montyApi.getFeatureFlags().catch(() => null),
        montyApi.listAuditLogs(25).catch(() => ({ count: 0, logs: [] as AuditLogEntry[] })),
      ]);
      setMembers(m.members);
      setSubscription(sub);
      setAgentsCat(cat);
      setFeatureFlags(flags);
      setAuditLogs(logsRes.logs);
    } catch (err) {
      setErrorMsg(
        err instanceof Error
          ? err.message
          : "Connectez une session Directeur/Admin pour accéder à l'administration."
      );
    }
  }

  useEffect(() => {
    refreshAdminData();
  }, []);

  async function handleToggleFlag(key: keyof Omit<AgencyFeatureFlags, "agency_id" | "updated_at">) {
    if (!featureFlags) return;
    setErrorMsg(null);
    const updated = {
      auto_matching_enabled: featureFlags.auto_matching_enabled,
      ai_copywriting_enabled: featureFlags.ai_copywriting_enabled,
      rnic_free_for_sellers: featureFlags.rnic_free_for_sellers,
      public_appointment_booking: featureFlags.public_appointment_booking,
      notary_auto_task_on_compromis: featureFlags.notary_auto_task_on_compromis,
      [key]: !featureFlags[key],
    };
    try {
      const saved = await montyApi.updateFeatureFlags(updated);
      setFeatureFlags(saved);
      setStatusMessage("Paramètre fonctionnel d'agence mis à jour.");
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Permission AGENCY_MANAGE requise pour modifier les flags.");
    }
  }

  async function handleSubscribeStripe(planCode: "INDIVIDUAL" | "AGENCY" | "NETWORK") {
    setErrorMsg(null);
    setCheckoutBusyPlan(planCode);
    try {
      const origin = typeof window !== "undefined" ? window.location.origin : "https://monty-site.onrender.com";
      const res = await montyApi.createCheckoutSession({
        plan_code: planCode,
        success_url: `${origin}/?billing=success`,
        cancel_url: `${origin}/?billing=cancel`,
      });
      if (res.checkout_url && typeof window !== "undefined") {
        window.location.href = res.checkout_url;
      }
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Stripe Checkout non configuré sur le serveur.");
    } finally {
      setCheckoutBusyPlan(null);
    }
  }

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    setErrorMsg(null);
    setStatusMessage(null);
    if (!inviteEmail.includes("@")) {
      setErrorMsg("Saisissez un e-mail valide pour l'invitation.");
      return;
    }
    try {
      const inv = await montyApi.createTeamInvite({
        target_email: inviteEmail.trim(),
        target_name: inviteName.trim(),
        role: inviteRole,
      });
      setLastInvite(inv);
      setInviteEmail("");
      setInviteName("");
      setStatusMessage(`Invitation émise pour ${inv.target_email}.`);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Échec création invitation.");
    }
  }

  async function handleToggleKillSwitch() {
    if (!agentsCat) return;
    setErrorMsg(null);
    try {
      await montyApi.setAgentsKillSwitch({
        enabled: !agentsCat.kill_switch_enabled,
        reason: "Action depuis Console Administration",
      });
      await refreshAdminData();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Permission insuffisante pour le Kill-Switch.");
    }
  }

  async function handleRgpdPurge() {
    setErrorMsg(null);
    try {
      const res = await montyApi.rgpdPurgeExpired();
      setStatusMessage(`Purge RGPD effectuée : ${res.anonymized_count} fiche(s) expirée(s) anonymisée(s).`);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Échec de la purge RGPD.");
    }
  }

  return (
    <div className="space-y-6">
      <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-5 border border-goldAntique/40 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-goldChampagne font-semibold">
            Administration Agence • RBAC • Stripe SaaS • Observabilité
          </p>
          <h2 className="font-display text-2xl font-bold mt-1">
            Console Gouvernance &amp; Paramètres Multi-Agences
          </h2>
          <p className="text-xs text-ivoryWarm/80 mt-1">
            Rôle session active : <strong>{role}</strong> • Version API : {health?.version ?? "2.0.0"}
          </p>
        </div>
        <button
          type="button"
          onClick={handleRgpdPurge}
          className="inline-flex items-center gap-1.5 px-4 py-2.5 min-h-[44px] rounded-xl bg-emeraldSurface border border-goldAntique/40 text-goldChampagne text-xs font-bold"
        >
          <Trash2 className="w-4 h-4" />
          Exécuter Purge RGPD
        </button>
      </div>

      {errorMsg && (
        <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-3 text-xs text-amber-900">
          {errorMsg}
        </div>
      )}
      {statusMessage && (
        <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-4 py-3 text-xs text-emerald-900">
          {statusMessage}
        </div>
      )}

      <HealthCheckDashboard initialHealth={health} onHealthUpdate={setHealth} />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Équipe & Invitations */}
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <Users className="w-4 h-4 text-goldAntique" />
            Équipe &amp; Invitations Sécurisées ({members.length})
          </h3>
          <form onSubmit={handleInvite} className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
            <input
              type="email"
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
              placeholder="collaborateur@agence.fr"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <input
              type="text"
              value={inviteName}
              onChange={(e) => setInviteName(e.target.value)}
              placeholder="Nom complet"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <select
              value={inviteRole}
              onChange={(e) => setInviteRole(e.target.value as MontyRole)}
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="AGENT">AGENT</option>
              <option value="ASSISTANT">ASSISTANT</option>
              <option value="ADMIN">ADMIN</option>
            </select>
            <button
              type="submit"
              className="sm:col-span-3 inline-flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne font-bold"
            >
              <UserPlus className="w-3.5 h-3.5" />
              Générer code d&apos;invitation (256 bits)
            </button>
          </form>

          {lastInvite && (
            <div className="rounded-xl bg-emeraldDeep text-ivoryWarm p-3 text-xs">
              <p className="font-bold text-goldChampagne">Code invitation à transmettre :</p>
              <p className="font-mono break-all mt-1">{lastInvite.invite_code}</p>
            </div>
          )}

          <div className="space-y-2 text-xs">
            {members.map((m) => (
              <div key={m.user_id} className="flex items-center justify-between p-2.5 rounded-xl bg-ivoryWarm border border-borderSubtle">
                <div>
                  <p className="font-bold text-emeraldDeep">{m.display_name || m.email}</p>
                  <p className="text-slateMuted">{m.email}</p>
                </div>
                <span className="px-2 py-0.5 rounded bg-emeraldDeep/10 text-emeraldDeep font-bold">
                  {m.role}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Kill Switch Agents IA + Stripe */}
        <div className="space-y-5">
          {agentsCat && (
            <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
              <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
                <Shield className="w-4 h-4 text-goldAntique" />
                Gouvernance &amp; Sécurité Agents IA
              </h3>
              <div className="flex items-center justify-between p-3 rounded-xl bg-ivoryWarm border border-borderSubtle">
                <span className="flex items-center gap-1.5 font-semibold text-emeraldDeep">
                  <Shield className="w-4 h-4" />
                  Interrupteur 11 Agents IA :{" "}
                  {agentsCat.kill_switch_enabled ? "SUSPENDU" : "OPÉRATIONNEL"}
                </span>
                <button
                  type="button"
                  onClick={handleToggleKillSwitch}
                  className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg font-bold ${
                    agentsCat.kill_switch_enabled ? "bg-red-600 text-white" : "bg-emeraldDeep text-goldChampagne"
                  }`}
                >
                  <Power className="w-3.5 h-3.5" />
                  Basculer
                </button>
              </div>
            </div>
          )}

          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
            <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
              <CreditCard className="w-4 h-4 text-goldAntique" />
              Abonnement SaaS Stripe {subscription ? `(${subscription.plan_code} • ${subscription.subscription_status})` : ""}
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
              {plans.map((p) => (
                <div key={p.code} className="p-3 rounded-xl bg-ivoryWarm border border-borderSubtle space-y-2 flex flex-col justify-between">
                  <div>
                    <p className="font-bold text-emeraldDeep">{p.name}</p>
                    <p className="text-sm font-bold text-goldAntique">{p.monthly_price_euro} €/mois</p>
                    <p className="text-[11px] text-slateMuted">{p.seat_limit} siège(s)</p>
                  </div>
                  <button
                    type="button"
                    disabled={checkoutBusyPlan === p.code}
                    onClick={() => handleSubscribeStripe(p.code as "INDIVIDUAL" | "AGENCY" | "NETWORK")}
                    className="w-full py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-50"
                  >
                    {checkoutBusyPlan === p.code ? "Redirection..." : "Souscrire Stripe"}
                  </button>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Feature Flags Agence & Journal d'Audit Immuable */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <Sliders className="w-4 h-4 text-goldAntique" />
            Paramètres &amp; Automatisations d&apos;Agence (Feature Flags)
          </h3>
          {!featureFlags ? (
            <p className="text-slateMuted">Connectez une session Agence pour gérer les modules actifs.</p>
          ) : (
            <div className="space-y-2">
              {(
                [
                  ["auto_matching_enabled", "Matching automatique acquéreurs sur nouveau bien"],
                  ["ai_copywriting_enabled", "Rédaction d'annonces assistée IA"],
                  ["rnic_free_for_sellers", "Base Copropriétés RNIC gratuite pour les vendeurs vérifiés"],
                  ["public_appointment_booking", "Prise de rendez-vous visite en ligne (Annonces)"],
                  ["notary_auto_task_on_compromis", "Relance Notaire automatique au passage en Compromis / Acte"],
                ] as const
              ).map(([key, label]) => (
                <label
                  key={key}
                  className="flex items-center justify-between gap-3 p-3 rounded-xl bg-ivoryWarm border border-borderSubtle cursor-pointer"
                >
                  <span className="font-medium text-obsidianInk">{label}</span>
                  <input
                    type="checkbox"
                    checked={featureFlags[key]}
                    onChange={() => handleToggleFlag(key)}
                    className="h-4 w-4 accent-emeraldDeep"
                  />
                </label>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <History className="w-4 h-4 text-goldAntique" />
            Journal d&apos;Audit Immuable ({auditLogs.length})
          </h3>
          {auditLogs.length === 0 ? (
            <p className="text-slateMuted">Aucun événement d&apos;audit récent (ou permission AUDIT_LOGS_READ requise).</p>
          ) : (
            <div className="space-y-1.5 max-h-64 overflow-y-auto pr-1">
              {auditLogs.map((log) => (
                <div key={log.id} className="p-2.5 rounded-xl bg-ivoryWarm border border-borderSubtle">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold text-emeraldDeep">{log.action_type}</span>
                    <span className="text-[10px] font-mono text-slateMuted">{log.created_at.slice(0, 16)}</span>
                  </div>
                  <p className="text-slateMuted mt-0.5">
                    {log.actor_role} • {log.detail}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
