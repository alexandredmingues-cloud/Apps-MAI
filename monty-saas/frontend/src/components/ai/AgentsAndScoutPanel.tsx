"use client";

import React, { useEffect, useState } from "react";
import {
  Activity,
  AlertOctagon,
  Bot,
  CheckCircle2,
  Cpu,
  MessageSquare,
  Power,
  Send,
  ShieldCheck,
  Sparkles,
  XCircle,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type {
  AgentCatalogResponse,
  AgentExecutionLog,
  MontyRole,
  ScoutProposal,
} from "@/lib/types";
import { MontyAIChatWorkspace } from "./MontyAIChatWorkspace";

interface AgentsAndScoutPanelProps {
  role: MontyRole;
}

export function AgentsAndScoutPanel({ role }: AgentsAndScoutPanelProps) {
  const [aiTab, setAiTab] = useState<"unified" | "agents">("unified");
  const [catalog, setCatalog] = useState<AgentCatalogResponse | null>(null);
  const [selectedAgentId, setSelectedAgentId] = useState("scout_prospection");
  const [promptText, setPromptText] = useState(
    "Prépare un suivi vendeur T3 Lyon Part-Dieu 415 000 € avec argumentaire DVF et rappel RGPD."
  );
  const [lastLog, setLastLog] = useState<AgentExecutionLog | null>(null);
  const [logs, setLogs] = useState<AgentExecutionLog[]>([]);
  const [proposals, setProposals] = useState<ScoutProposal[]>([]);

  const [newTarget, setNewTarget] = useState("");
  const [newChannel, setNewChannel] = useState<"WHATSAPP" | "EMAIL" | "SMS">("WHATSAPP");
  const [newDraft, setNewDraft] = useState("");
  const [killReason, setKillReason] = useState("Maintenance / Contrôle conformité");

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const canManageKillSwitch = role === "DIRECTOR" || role === "ADMIN" || role === "SUPER_ADMIN";

  async function refreshAll() {
    setErrorMsg(null);
    try {
      const [cat, logsRes, scoutRes] = await Promise.all([
        montyApi.getAgentsCatalog(),
        montyApi.getAgentLogs(),
        montyApi.listScoutProposals(),
      ]);
      setCatalog(cat);
      setLogs(logsRes.logs);
      setProposals(scoutRes.proposals);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Connectez une session Pro pour piloter les 11 agents.");
    }
  }

  useEffect(() => {
    refreshAll();
  }, []);

  async function handleRunAgent(e: React.FormEvent) {
    e.preventDefault();
    setErrorMsg(null);
    if (promptText.trim().length < 4) {
      setErrorMsg("Saisissez une instruction d'au moins 4 caractères.");
      return;
    }
    setLoading(true);
    try {
      const log = await montyApi.runAgent({ agent_id: selectedAgentId, prompt_text: promptText.trim() });
      setLastLog(log);
      await refreshAll();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Erreur d'exécution agent");
    } finally {
      setLoading(false);
    }
  }

  async function handleToggleKillSwitch() {
    if (!catalog) return;
    setErrorMsg(null);
    try {
      await montyApi.setAgentsKillSwitch({
        enabled: !catalog.kill_switch_enabled,
        reason: killReason,
      });
      await refreshAll();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Action réservée aux Directeurs/Administrateurs.");
    }
  }

  async function handleCreateProposal(e: React.FormEvent) {
    e.preventDefault();
    setErrorMsg(null);
    if (newTarget.trim().length < 2 || newDraft.trim().length < 10) {
      setErrorMsg("Indiquez un contact et un message d'au moins 10 caractères.");
      return;
    }
    try {
      await montyApi.createScoutProposal({
        target_contact_name: newTarget.trim(),
        channel: newChannel,
        legal_basis: "INTERET_LEGITIME_PROSPECTION_B2C",
        message_draft: newDraft.trim(),
      });
      setNewTarget("");
      setNewDraft("");
      await refreshAll();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Impossible de créer la proposition Scout.");
    }
  }

  async function handleScoutDecision(id: string, approve: boolean) {
    setErrorMsg(null);
    try {
      await montyApi.decideScoutProposal(id, approve);
      await refreshAll();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Erreur validation Monty Scout.");
    }
  }

  return (
    <div className="space-y-6">
      <div className="inline-flex rounded-2xl bg-ivoryCard border border-borderSubtle p-1 text-xs">
        <button
          type="button"
          onClick={() => setAiTab("unified")}
          className={`inline-flex items-center gap-1.5 px-4 py-2 rounded-xl font-bold transition ${
            aiTab === "unified" ? "bg-emeraldDeep text-goldChampagne" : "text-slateMuted hover:text-obsidianInk"
          }`}
        >
          <Sparkles className="w-3.5 h-3.5" />
          Discuter avec Monty
        </button>
        <button
          type="button"
          onClick={() => setAiTab("agents")}
          className={`inline-flex items-center gap-1.5 px-4 py-2 rounded-xl font-bold transition ${
            aiTab === "agents" ? "bg-emeraldDeep text-goldChampagne" : "text-slateMuted hover:text-obsidianInk"
          }`}
        >
          <Bot className="w-3.5 h-3.5" />
          11 Agents spécialisés &amp; Monty Scout
        </button>
      </div>

      {aiTab === "unified" ? (
        <MontyAIChatWorkspace />
      ) : (
        <>
          {/* En-tête Gouvernance IA + Kill-Switch */}
      <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-5 border border-goldAntique/40 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-goldChampagne font-semibold">
            Gouvernance IA • 11 Agents Autonomes + Monty Scout + WhatsApp Meta
          </p>
          <h2 className="font-display text-2xl font-bold mt-1">
            Orchestration Agents IA &amp; Validation Humaine 1-Clic
          </h2>
          <p className="text-xs text-ivoryWarm/80 mt-1">
            Anonymisation PII avant modèle, cache SHA-256, plafond appels/minute, budget quotidien et interrupteur d&apos;arrêt global Admin.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {catalog && (
            <div className="rounded-xl bg-emeraldSurface border border-goldAntique/30 px-3 py-2 text-xs">
              <span className="text-ivoryWarm/70">Budget jour : </span>
              <strong className="text-goldChampagne">
                {catalog.daily_spend_euro.toFixed(3)} € / {catalog.daily_budget_euro.toFixed(2)} €
              </strong>
            </div>
          )}
          {canManageKillSwitch && catalog && (
            <button
              type="button"
              onClick={handleToggleKillSwitch}
              className={`inline-flex items-center gap-1.5 px-4 py-2.5 min-h-[44px] rounded-xl text-xs font-bold transition ${
                catalog.kill_switch_enabled
                  ? "bg-red-600 text-white hover:bg-red-700"
                  : "bg-goldChampagne text-emeraldDeep hover:opacity-90"
              }`}
            >
              <Power className="w-4 h-4" />
              {catalog.kill_switch_enabled ? "Kill-Switch ACTIF (Réactiver)" : "Activer Kill-Switch IA"}
            </button>
          )}
        </div>
      </div>

      {catalog?.kill_switch_enabled && (
        <div className="rounded-xl bg-red-50 border border-red-400 px-4 py-3 text-xs text-red-900 flex items-center gap-2">
          <AlertOctagon className="w-4 h-4 shrink-0 text-red-700" />
          <span>
            <strong>Interrupteur d&apos;arrêt global activé :</strong> {catalog.kill_switch_reason || "Exécutions IA suspendues"}.
          </span>
        </div>
      )}

      {errorMsg && (
        <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-3 text-xs text-amber-900">
          {errorMsg}
        </div>
      )}

      {/* Catalogue des 11 Agents + Exécuteur */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <Cpu className="w-4 h-4 text-goldAntique" />
            Catalogue des 11 Agents Spécialisés
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            {(catalog?.agents ?? []).map((ag) => {
              const active = selectedAgentId === ag.agent_id;
              return (
                <button
                  key={ag.agent_id}
                  type="button"
                  onClick={() => setSelectedAgentId(ag.agent_id)}
                  className={`text-left p-3 rounded-xl border transition ${
                    active
                      ? "bg-emeraldDeep text-ivoryWarm border-emeraldDeep"
                      : "bg-ivoryWarm border-borderSubtle hover:border-emeraldMid"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2 text-xs">
                    <span className="font-bold flex items-center gap-1.5">
                      <Bot className="w-3.5 h-3.5 text-goldChampagne shrink-0" />
                      {ag.name}
                    </span>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-goldAntique/20 text-goldChampagne font-semibold uppercase">
                      {ag.model_tier}
                    </span>
                  </div>
                  <p className={`text-[11px] mt-1 ${active ? "text-ivoryWarm/80" : "text-slateMuted"}`}>
                    {ag.description}
                  </p>
                </button>
              );
            })}
          </div>

          <form onSubmit={handleRunAgent} className="space-y-3 pt-2 border-t border-borderSubtle">
            <label className="block text-xs">
              <span className="font-semibold text-slateMuted">Instruction métier (les PII e-mail/téléphone sont automatiquement masquées)</span>
              <textarea
                rows={3}
                value={promptText}
                onChange={(e) => setPromptText(e.target.value)}
                className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm p-3 text-xs"
              />
            </label>
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="submit"
                disabled={loading || Boolean(catalog?.kill_switch_enabled)}
                className="inline-flex items-center gap-1.5 px-4 py-2.5 min-h-[44px] rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid disabled:opacity-40"
              >
                <Send className="w-3.5 h-3.5" />
                {loading ? "Exécution..." : "Exécuter l'agent"}
              </button>
              {canManageKillSwitch && (
                <input
                  type="text"
                  value={killReason}
                  onChange={(e) => setKillReason(e.target.value)}
                  placeholder="Motif kill-switch admin"
                  className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-xs flex-1 min-w-[200px]"
                />
              )}
            </div>
          </form>

          {lastLog && (
            <div className="rounded-xl bg-emeraldDeep text-ivoryWarm p-4 text-xs space-y-1.5">
              <div className="flex flex-wrap items-center justify-between gap-2 text-goldChampagne font-semibold">
                <span>{lastLog.agent_name} ({lastLog.model_used})</span>
                <span>
                  {lastLog.cache_hit ? "CACHE HIT (0 €)" : `${lastLog.cost_euro.toFixed(4)} €`} • {lastLog.duration_ms} ms
                </span>
              </div>
              <p className="text-ivoryWarm/90">{lastLog.output_summary}</p>
              <p className="text-[11px] text-ivoryWarm/65">Prompt anonymisé : {lastLog.sanitized_input}</p>
            </div>
          )}
        </div>

        {/* Monty Scout Mode Proposition -> 1-clic */}
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <MessageSquare className="w-4 h-4 text-goldAntique" />
            Monty Scout • Validation 1-Clic
          </h3>
          <p className="text-[11px] text-slateMuted">
            Chaque message préparé par Monty Scout inclut la base légale RGPD et le mot-clé de désinscription <strong>STOP</strong>, validé par un conseiller avant envoi WhatsApp/Email/SMS.
          </p>

          <form onSubmit={handleCreateProposal} className="space-y-2 text-xs border-b border-borderSubtle pb-3">
            <input
              type="text"
              value={newTarget}
              onChange={(e) => setNewTarget(e.target.value)}
              placeholder="Contact destinataire"
              className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <select
              value={newChannel}
              onChange={(e) => setNewChannel(e.target.value as "WHATSAPP" | "EMAIL" | "SMS")}
              className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="WHATSAPP">WhatsApp Meta Cloud API</option>
              <option value="EMAIL">E-mail</option>
              <option value="SMS">SMS</option>
            </select>
            <textarea
              rows={2}
              value={newDraft}
              onChange={(e) => setNewDraft(e.target.value)}
              placeholder="Brouillon d'approche (mention STOP ajoutée automatiquement)"
              className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm p-2.5"
            />
            <button
              type="submit"
              className="w-full px-3 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid"
            >
              Préparer une proposition Scout
            </button>
          </form>

          <div className="space-y-2.5 max-h-[420px] overflow-y-auto">
            {proposals.map((p) => (
              <div key={p.id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 text-xs space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold text-emeraldDeep">{p.target_contact_name}</span>
                  <span
                    className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                      p.status === "VALIDEE"
                        ? "bg-emerald-100 text-emerald-900"
                        : p.status === "REJETEE"
                        ? "bg-red-100 text-red-900"
                        : "bg-amber-100 text-amber-900"
                    }`}
                  >
                    {p.status}
                  </span>
                </div>
                <p className="text-[11px] text-obsidianInk">{p.message_draft}</p>
                <p className="text-[10px] text-slateMuted flex items-center gap-1">
                  <ShieldCheck className="w-3 h-3" /> {p.channel} • {p.legal_basis} • Opt-out : {p.opt_out_keyword}
                </p>
                {p.status === "A_VALIDER" && (
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => handleScoutDecision(p.id, true)}
                      className="flex-1 inline-flex items-center justify-center gap-1 px-2.5 py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne font-bold"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" /> Valider 1-clic
                    </button>
                    <button
                      type="button"
                      onClick={() => handleScoutDecision(p.id, false)}
                      className="inline-flex items-center justify-center gap-1 px-2.5 py-1.5 rounded-lg border border-red-300 text-red-700 font-semibold"
                    >
                      <XCircle className="w-3.5 h-3.5" /> Rejeter
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Journal d'audit des appels Agents */}
      <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3">
        <h3 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
          <Activity className="w-4 h-4 text-goldAntique" />
          Journal des Exécutions Agents ({logs.length})
        </h3>
        {logs.length === 0 ? (
          <p className="text-xs text-slateMuted">Aucune exécution enregistrée sur cette session.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className="border-b border-borderSubtle text-left text-slateMuted">
                  <th className="py-2 pr-3">Agent</th>
                  <th className="py-2 pr-3">Modèle</th>
                  <th className="py-2 pr-3">Durée</th>
                  <th className="py-2 pr-3">Coût</th>
                  <th className="py-2">Entrée anonymisée</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((l) => (
                  <tr key={l.id} className="border-b border-borderSubtle/60">
                    <td className="py-2 pr-3 font-semibold text-emeraldDeep">{l.agent_name}</td>
                    <td className="py-2 pr-3">{l.model_used}</td>
                    <td className="py-2 pr-3">{l.duration_ms} ms</td>
                    <td className="py-2 pr-3">{l.cache_hit ? "Cache 0 €" : `${l.cost_euro.toFixed(4)} €`}</td>
                    <td className="py-2 text-slateMuted truncate max-w-[320px]">{l.sanitized_input}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
        </>
      )}
    </div>
  );
}
