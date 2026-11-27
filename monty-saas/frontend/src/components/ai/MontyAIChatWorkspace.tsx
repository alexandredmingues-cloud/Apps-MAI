"use client";

import React, { useEffect, useState } from "react";
import {
  Bot,
  CheckCircle2,
  FileUp,
  Lock,
  MessageSquare,
  Plus,
  Send,
  ShieldCheck,
  Sparkles,
  Trash2,
  Wrench,
  XCircle,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type {
  MontyAIChatResponse,
  MontyAIConversationTurn,
  MontyAIMemoryEntry,
  MontyAIPendingAction,
  MontyAITaskType,
} from "@/lib/types";

const TASK_OPTIONS: { key: MontyAITaskType; label: string; hint: string }[] = [
  { key: "CONVERSATION", label: "Assistant général", hint: "Questions métier, suivi client, conseil" },
  { key: "QUICK_SUMMARY", label: "Résumé rapide", hint: "Synthèse d'échange ou compte-rendu" },
  { key: "DOCUMENT_ANALYSIS", label: "Analyse document", hint: "PV AG, DPE, compromis ou fichier texte" },
  { key: "WRITING", label: "Rédaction pro", hint: "Annonce, e-mail client ou notaire" },
  { key: "PRICE_EXPLANATION", label: "Analyse Prix / DVF", hint: "Argumentaire Monty Price Index" },
  { key: "COMPLEX_REASONING", label: "Raisonnement complexe", hint: "Stratégie négociation & fiscalité" },
];

export function MontyAIChatWorkspace() {
  const [taskType, setTaskType] = useState<MontyAITaskType>("CONVERSATION");
  const [confidentialMode, setConfidentialMode] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [attachedFileName, setAttachedFileName] = useState<string | null>(null);
  const [attachedContent, setAttachedContent] = useState<string>("");

  const [lastResponse, setLastResponse] = useState<MontyAIChatResponse | null>(null);
  const [conversations, setConversations] = useState<MontyAIConversationTurn[]>([]);
  const [memories, setMemories] = useState<MontyAIMemoryEntry[]>([]);
  const [pendingActions, setPendingActions] = useState<MontyAIPendingAction[]>([]);

  // Ajout mémoire RGPD
  const [memScope, setMemScope] = useState<"USER" | "AGENCY">("AGENCY");
  const [memTitle, setMemTitle] = useState("");
  const [memContent, setMemContent] = useState("");

  const [busy, setBusy] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  async function refreshAll() {
    try {
      const [convRes, memRes, actRes] = await Promise.all([
        montyApi.listMontyAiConversations(20),
        montyApi.listMontyAiMemories(),
        montyApi.listMontyAiActions(),
      ]);
      setConversations(convRes.conversations);
      setMemories(memRes.memories);
      setPendingActions(actRes.actions);
    } catch (err) {
      setErrorMsg(
        err instanceof Error ? err.message : "Connectez votre compte MON-AGENT pour dialoguer avec Monty."
      );
    }
  }

  useEffect(() => {
    refreshAll();
  }, []);

  async function handleFileUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 512_000) {
      setErrorMsg("Fichier limité à 500 Ko pour l'analyse textuelle directe.");
      return;
    }
    const text = await file.text().catch(() => "");
    setAttachedFileName(file.name);
    setAttachedContent(text.slice(0, 6000));
    setTaskType("DOCUMENT_ANALYSIS");
  }

  async function handleSendPrompt(e: React.FormEvent) {
    e.preventDefault();
    if (prompt.trim().length < 3) {
      setErrorMsg("Saisissez une demande d'au moins 3 caractères.");
      return;
    }
    setErrorMsg(null);
    setBusy(true);
    try {
      const res = await montyApi.montyAiChat({
        prompt: prompt.trim(),
        task_type: taskType,
        confidential_mode: confidentialMode,
        context_payload: attachedContent
          ? { attachment_name: attachedFileName, document_excerpt: attachedContent }
          : undefined,
      });
      setLastResponse(res);
      setPrompt("");
      await refreshAll();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Erreur de réponse de Monty.");
    } finally {
      setBusy(false);
    }
  }

  async function handleSaveMemory(e: React.FormEvent) {
    e.preventDefault();
    if (memTitle.trim().length < 2 || memContent.trim().length < 4) {
      setErrorMsg("Renseignez un titre et un contenu de mémoire valides.");
      return;
    }
    setErrorMsg(null);
    try {
      await montyApi.upsertMontyAiMemory({
        scope: memScope,
        title: memTitle.trim(),
        content: memContent.trim(),
        source_ref: "workspace-user",
      });
      setMemTitle("");
      setMemContent("");
      await refreshAll();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Impossible d'enregistrer la mémoire.");
    }
  }

  async function handleDeleteMemory(id: string) {
    setErrorMsg(null);
    try {
      await montyApi.deleteMontyAiMemory(id);
      await refreshAll();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Suppression refusée.");
    }
  }

  async function handleDecideAction(id: string, approve: boolean) {
    setErrorMsg(null);
    try {
      await montyApi.decideMontyAiAction(id, approve);
      await refreshAll();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Impossible de traiter la validation.");
    }
  }

  return (
    <div className="space-y-6">
      <div className="rounded-2xl bg-gradient-to-r from-emeraldDeep to-emeraldMid text-ivoryWarm p-5 border border-goldAntique/40 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-goldChampagne font-semibold">
            MON-AGENT • VOTRE COPILOTE IMMOBILIER
          </p>
          <h2 className="font-display text-2xl font-bold mt-1 flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-goldChampagne" />
            Monty
          </h2>
          <p className="text-xs text-ivoryWarm/80 mt-1">
            Une seule intelligence unifiée : routage automatique sécurisé (LLM locaux open-source + moteurs externes
            chiffrés), zéro clé API utilisateur requise.
          </p>
        </div>

        <label className="inline-flex items-center gap-2 px-3.5 py-2.5 rounded-xl bg-emeraldSurface border border-goldAntique/40 text-xs font-bold cursor-pointer shrink-0">
          <input
            type="checkbox"
            checked={confidentialMode}
            onChange={(e) => setConfidentialMode(e.target.checked)}
            className="accent-goldChampagne"
          />
          <Lock className="w-3.5 h-3.5 text-goldChampagne" />
          <span>Mode confidentiel (moteurs locaux uniquement)</span>
        </label>
      </div>

      {errorMsg && (
        <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-3 text-xs text-amber-900">
          {errorMsg}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Colonne principale : Dialogue unifié */}
        <div className="lg:col-span-2 space-y-4">
          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
            <div className="flex flex-wrap gap-1.5">
              {TASK_OPTIONS.map((t) => (
                <button
                  key={t.key}
                  type="button"
                  onClick={() => setTaskType(t.key)}
                  title={t.hint}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold border transition ${
                    taskType === t.key
                      ? "bg-emeraldDeep text-goldChampagne border-emeraldDeep"
                      : "bg-ivoryWarm text-obsidianInk border-borderSubtle hover:border-emeraldMid"
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </div>

            <div className="space-y-2">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-slateMuted">Pour commencer avec Monty</p>
              <div className="flex flex-wrap gap-2">
                {[
                  { label: "Estimer un bien", prompt: "Aide-moi à préparer une estimation immobilière argumentée avec les comparables disponibles.", task: "PRICE_EXPLANATION" as MontyAITaskType },
                  { label: "Analyser un DPE", prompt: "Aide-moi à analyser ce DPE, ses conséquences et les points à vérifier.", task: "DOCUMENT_ANALYSIS" as MontyAITaskType },
                  { label: "Rédiger une annonce", prompt: "Rédige une annonce immobilière attractive, précise et conforme, sans inventer de caractéristiques.", task: "WRITING" as MontyAITaskType },
                  { label: "Préparer une visite", prompt: "Prépare une checklist de visite immobilière et les questions utiles à poser.", task: "CONVERSATION" as MontyAITaskType },
                ].map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    onClick={() => { setPrompt(item.prompt); setTaskType(item.task); }}
                    className="rounded-full border border-borderSubtle bg-ivoryWarm px-3 py-1.5 text-[11px] font-semibold text-emeraldDeep hover:border-emeraldDeep hover:bg-white"
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            <form onSubmit={handleSendPrompt} className="space-y-3 text-xs">
              <textarea
                rows={4}
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="Bonjour, je suis Monty. Comment puis-je vous aider dans votre projet immobilier ?"
                className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm p-3 text-sm focus:outline-none focus:border-emeraldDeep"
              />
              <div className="flex flex-wrap items-center justify-between gap-2">
                <label className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl border border-borderSubtle bg-ivoryWarm text-emeraldDeep font-semibold cursor-pointer hover:border-emeraldDeep">
                  <FileUp className="w-4 h-4" />
                  <span>{attachedFileName ? `Pièce jointe : ${attachedFileName}` : "Joindre un texte / CSV (.txt, .csv, .md)"}</span>
                  <input type="file" accept=".txt,.csv,.md,.json" onChange={handleFileUpload} className="hidden" />
                </label>

                <button
                  type="submit"
                  disabled={busy}
                  className="inline-flex items-center gap-1.5 px-4 py-2.5 min-h-[44px] rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-50"
                >
                  <Send className="w-4 h-4" />
                  {busy ? "Monty réfléchit..." : "Envoyer à Monty"}
                </button>
              </div>
            </form>

            {lastResponse && (
              <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-4 space-y-2.5 text-xs">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-bold text-goldChampagne flex items-center gap-1.5">
                    <Bot className="w-4 h-4" />
                    {lastResponse.assistant_name}
                  </span>
                  <div className="flex flex-wrap items-center gap-1.5 text-[10px]">
                    <span className="px-2 py-0.5 rounded-full bg-emeraldSurface border border-goldAntique/30">
                      {lastResponse.execution_mode === "LOCAL_OPEN_SOURCE"
                        ? "Moteur Local Open-Source"
                        : lastResponse.execution_mode === "EXTERNAL_PROVIDER"
                        ? "Orchestration Chiffrée"
                        : "Mode Outils Résilient"}
                    </span>
                    <span className="px-2 py-0.5 rounded-full bg-emeraldSurface">
                      {lastResponse.latency_ms} ms
                    </span>
                  </div>
                </div>
                <div className="whitespace-pre-wrap text-ivoryWarm/95 leading-relaxed">
                  {lastResponse.response_text}
                </div>
                {lastResponse.tools_invoked.length > 0 && (
                  <div className="flex flex-wrap items-center gap-1.5 pt-1">
                    <Wrench className="w-3.5 h-3.5 text-goldChampagne" />
                    {lastResponse.tools_invoked.map((tool) => (
                      <span
                        key={tool}
                        className="px-2 py-0.5 rounded bg-emeraldSurface text-goldChampagne font-mono text-[10px]"
                      >
                        {tool}
                      </span>
                    ))}
                  </div>
                )}
                {lastResponse.sources.length > 0 && (
                  <p className="text-[11px] text-ivoryWarm/75">
                    Sources : {lastResponse.sources.map((s) => s.label).join(" • ")}
                  </p>
                )}
              </div>
            )}
          </div>

          {/* Historique des échanges */}
          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3">
            <h3 className="font-display font-bold text-sm text-emeraldDeep flex items-center gap-2">
              <MessageSquare className="w-4 h-4 text-goldAntique" />
              Historique avec Monty ({conversations.length})
            </h3>
            {conversations.length === 0 ? (
              <p className="text-xs text-slateMuted">Aucun échange enregistré sur cette session.</p>
            ) : (
              <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1 text-xs">
                {conversations.map((c) => (
                  <div key={c.id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 space-y-1">
                    <div className="flex items-center justify-between text-[10px] text-slateMuted">
                      <span className="font-bold text-emeraldDeep">{c.task_type}</span>
                      <span>{c.created_at.slice(0, 16).replace("T", " ")}</span>
                    </div>
                    <p className="font-semibold text-obsidianInk">{c.sanitized_prompt}</p>
                    <p className="text-slateMuted line-clamp-3 whitespace-pre-wrap">{c.response_text}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Colonne latérale : Actions à valider + Mémoire RGPD */}
        <div className="space-y-4">
          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
            <h3 className="font-display font-bold text-sm text-emeraldDeep flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-goldAntique" />
              Actions à valider ({pendingActions.filter((a) => a.status === "A_VALIDER").length})
            </h3>
            {pendingActions.length === 0 ? (
              <p className="text-slateMuted">Aucune action en attente de validation humaine.</p>
            ) : (
              <div className="space-y-2 max-h-56 overflow-y-auto">
                {pendingActions.map((act) => (
                  <div key={act.id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 space-y-1.5">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-bold text-emeraldDeep">{act.title}</span>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          act.status === "VALIDEE"
                            ? "bg-emerald-100 text-emerald-900"
                            : act.status === "REJETEE"
                            ? "bg-rose-100 text-rose-900"
                            : "bg-amber-100 text-amber-900"
                        }`}
                      >
                        {act.status}
                      </span>
                    </div>
                    <p className="text-slateMuted">{act.summary}</p>
                    {act.status === "A_VALIDER" && (
                      <div className="flex gap-1.5 pt-1">
                        <button
                          type="button"
                          onClick={() => handleDecideAction(act.id, true)}
                          className="flex-1 inline-flex items-center justify-center gap-1 py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne font-bold"
                        >
                          <CheckCircle2 className="w-3.5 h-3.5" /> Valider
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDecideAction(act.id, false)}
                          className="inline-flex items-center justify-center gap-1 px-2.5 py-1.5 rounded-lg border border-rose-300 text-rose-700 font-semibold"
                        >
                          <XCircle className="w-3.5 h-3.5" /> Rejeter
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
            <h3 className="font-display font-bold text-sm text-emeraldDeep">
              Mémoire Contextuelle RGPD ({memories.length})
            </h3>
            <form onSubmit={handleSaveMemory} className="space-y-2 border-b border-borderSubtle pb-3">
              <div className="grid grid-cols-3 gap-1.5">
                <select
                  value={memScope}
                  onChange={(e) => setMemScope(e.target.value as "USER" | "AGENCY")}
                  className="rounded-lg border border-borderSubtle bg-ivoryWarm px-2 py-1.5"
                >
                  <option value="AGENCY">Agence</option>
                  <option value="USER">Perso</option>
                </select>
                <input
                  type="text"
                  value={memTitle}
                  onChange={(e) => setMemTitle(e.target.value)}
                  placeholder="Titre mémo"
                  className="col-span-2 rounded-lg border border-borderSubtle bg-ivoryWarm px-2.5 py-1.5"
                />
              </div>
              <textarea
                rows={2}
                value={memContent}
                onChange={(e) => setMemContent(e.target.value)}
                placeholder="Préférence agence, secteur clé, consignes..."
                className="w-full rounded-lg border border-borderSubtle bg-ivoryWarm p-2"
              />
              <button
                type="submit"
                className="w-full inline-flex items-center justify-center gap-1 py-2 rounded-lg bg-emeraldDeep text-goldChampagne font-bold"
              >
                <Plus className="w-3.5 h-3.5" /> Mémoriser (cloisonné RGPD)
              </button>
            </form>

            <div className="space-y-2 max-h-56 overflow-y-auto">
              {memories.map((m) => (
                <div key={m.id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-2.5 flex items-start justify-between gap-2">
                  <div>
                    <p className="font-bold text-emeraldDeep">
                      [{m.scope}] {m.title}
                    </p>
                    <p className="text-slateMuted mt-0.5">{m.content}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleDeleteMemory(m.id)}
                    aria-label="Supprimer cette mémoire RGPD"
                    className="p-1.5 rounded-lg text-rose-700 hover:bg-rose-50 shrink-0"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
