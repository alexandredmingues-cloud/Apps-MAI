"use client";

import React, { useEffect, useState } from "react";
import {
  CreditCard,
  Database,
  Power,
  Shield,
  Trash2,
  UserPlus,
  Users,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type {
  AgencySubscriptionInfo,
  AgentCatalogResponse,
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

      const [m, sub, cat] = await Promise.all([
        montyApi.listTeamMembers(),
        montyApi.getSubscription(),
        montyApi.getAgentsCatalog(),
      ]);
      setMembers(m.members);
      setSubscription(sub);
      setAgentsCat(cat);
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

        {/* Santé Système + Kill Switch + Stripe */}
        <div className="space-y-5">
          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
            <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
              <Database className="w-4 h-4 text-goldAntique" />
              Infrastructure &amp; Sécurité Production
            </h3>
            <div className="grid grid-cols-2 gap-2.5">
              <div className="p-3 rounded-xl bg-ivoryWarm border border-borderSubtle">
                <p className="text-slateMuted">PostgreSQL RLS</p>
                <p className="font-bold text-emeraldDeep">
                  {health?.database_configured
                    ? health.database_reachable
                      ? "Connecté (SELECT 1 OK)"
                      : "Configuré (hors ligne)"
                    : "Mode Dev Mémoire"}
                </p>
              </div>
              <div className="p-3 rounded-xl bg-ivoryWarm border border-borderSubtle">
                <p className="text-slateMuted">Redis Workers</p>
                <p className="font-bold text-emeraldDeep">
                  {health?.redis_configured ? "Actif" : "Non configuré"}
                </p>
              </div>
            </div>
            {agentsCat && (
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
            )}
          </div>

          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
            <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
              <CreditCard className="w-4 h-4 text-goldAntique" />
              Abonnement SaaS Stripe {subscription ? `(${subscription.plan_code} • ${subscription.subscription_status})` : ""}
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
              {plans.map((p) => (
                <div key={p.code} className="p-3 rounded-xl bg-ivoryWarm border border-borderSubtle space-y-1">
                  <p className="font-bold text-emeraldDeep">{p.name}</p>
                  <p className="text-sm font-bold text-goldAntique">{p.monthly_price_euro} €/mois</p>
                  <p className="text-[11px] text-slateMuted">{p.seat_limit} siège(s)</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
