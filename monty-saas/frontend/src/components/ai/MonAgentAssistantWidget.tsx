"use client";

import { useState } from "react";
import { Bot, MessageCircle, Send, X } from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type { MontyAIChatResponse } from "@/lib/types";

export function MonAgentAssistantWidget() {
  const [open, setOpen] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [answer, setAnswer] = useState<MontyAIChatResponse | null>(null);

  async function sendMessage(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = prompt.trim();
    if (message.length < 3 || busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await montyApi.montyAiChat({
        prompt: message,
        task_type: "CONVERSATION",
        confidential_mode: false,
      });
      setAnswer(result);
      setPrompt("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Le service est momentanément indisponible.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed bottom-5 right-5 z-[60]">
      {open && (
        <section
          aria-label="Assistant MON-AGENT"
          className="mb-3 flex h-[min(620px,75vh)] w-[min(390px,calc(100vw-2.5rem))] flex-col overflow-hidden rounded-2xl border border-goldAntique/40 bg-ivoryCard shadow-2xl"
        >
          <header className="flex items-center justify-between bg-emeraldDeep px-4 py-3 text-ivoryWarm">
            <div className="flex items-center gap-2">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-goldAntique/20 text-goldChampagne">
                <Bot className="h-5 w-5" />
              </span>
              <div>
                <p className="text-sm font-bold">MON-AGENT Assistant</p>
                <p className="text-[11px] text-ivoryWarm/70">Votre copilote immobilier</p>
              </div>
            </div>
            <button type="button" onClick={() => setOpen(false)} aria-label="Fermer l'assistant" className="rounded-lg p-2 hover:bg-white/10">
              <X className="h-4 w-4" />
            </button>
          </header>

          <div className="flex-1 space-y-3 overflow-y-auto p-4 text-sm">
            <div className="max-w-[90%] rounded-2xl rounded-tl-sm bg-ivoryWarm p-3 text-obsidianInk">
              Bonjour ! Je suis l'assistant MON-AGENT. Je peux vous aider sur vos dossiers, vos estimations, vos annonces et vos questions immobilières.
            </div>
            {answer && (
              <div className="ml-auto max-w-[95%] whitespace-pre-wrap rounded-2xl rounded-tr-sm bg-emeraldDeep p-3 text-ivoryWarm">
                {answer.response_text}
                {answer.sources.length > 0 && (
                  <p className="mt-2 border-t border-white/15 pt-2 text-[10px] text-ivoryWarm/70">
                    Sources : {answer.sources.map((source) => source.label).join(" • ")}
                  </p>
                )}
              </div>
            )}
            {busy && <p role="status" className="text-xs text-slateMuted">MON-AGENT prépare une réponse…</p>}
            {error && <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-800">{error}</p>}
          </div>

          <form onSubmit={sendMessage} className="border-t border-borderSubtle p-3">
            <label htmlFor="mon-agent-chat-prompt" className="sr-only">Votre message</label>
            <div className="flex items-end gap-2">
              <textarea
                id="mon-agent-chat-prompt"
                rows={2}
                maxLength={4000}
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                placeholder="Écrivez votre question…"
                className="min-w-0 flex-1 resize-none rounded-xl border border-borderSubtle bg-ivoryWarm p-3 text-sm text-obsidianInk outline-none focus:border-emeraldDeep"
              />
              <button type="submit" disabled={busy || prompt.trim().length < 3} aria-label="Envoyer le message" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emeraldDeep text-goldChampagne disabled:opacity-40">
                <Send className="h-4 w-4" />
              </button>
            </div>
            <p className="mt-2 text-[10px] text-slateMuted">Les réponses peuvent nécessiter une vérification. Les actions sensibles restent soumises à validation humaine.</p>
          </form>
        </section>
      )}

      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={open ? "Fermer le chatbot MON-AGENT" : "Ouvrir le chatbot MON-AGENT"}
        aria-expanded={open}
        className="ml-auto flex h-14 items-center gap-2 rounded-full bg-emeraldDeep px-5 text-sm font-bold text-goldChampagne shadow-xl ring-2 ring-goldAntique/50 transition hover:scale-[1.02]"
      >
        {open ? <X className="h-5 w-5" /> : <MessageCircle className="h-5 w-5" />}
        <span>{open ? "Fermer" : "Assistant MON-AGENT"}</span>
      </button>
    </div>
  );
}
