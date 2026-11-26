"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Database,
  Pause,
  Play,
  RefreshCw,
  Server,
  XCircle,
  Zap,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type { HealthStatus } from "@/lib/types";

type ServiceState = "healthy" | "degraded" | "offline" | "unconfigured";

interface ProbeSample {
  id: string;
  checkedAtIso: string;
  apiReachable: boolean;
  apiLatencyMs: number;
  overallStatus: "ok" | "degraded" | "offline";
}

interface HealthCheckDashboardProps {
  initialHealth?: HealthStatus | null;
  onHealthUpdate?: (health: HealthStatus | null) => void;
}

const POLL_OPTIONS = [
  { label: "5 s", value: 5000 },
  { label: "15 s", value: 15000 },
  { label: "30 s", value: 30000 },
  { label: "Manuel", value: 0 },
] as const;

function badgeClasses(state: ServiceState): string {
  switch (state) {
    case "healthy":
      return "bg-emerald-50 text-emerald-800 border-emerald-200";
    case "degraded":
      return "bg-amber-50 text-amber-800 border-amber-300";
    case "offline":
      return "bg-red-50 text-red-700 border-red-200";
    case "unconfigured":
      return "bg-stone-100 text-slateMuted border-borderSubtle";
  }
}

function StatusIcon({ state }: { state: ServiceState }) {
  switch (state) {
    case "healthy":
      return <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0" />;
    case "degraded":
      return <AlertTriangle className="w-4 h-4 text-amber-700 shrink-0" />;
    case "offline":
      return <XCircle className="w-4 h-4 text-red-600 shrink-0" />;
    case "unconfigured":
      return <Clock className="w-4 h-4 text-slateMuted shrink-0" />;
  }
}

export function HealthCheckDashboard({
  initialHealth = null,
  onHealthUpdate,
}: HealthCheckDashboardProps) {
  const [health, setHealth] = useState<HealthStatus | null>(initialHealth);
  const [apiReachable, setApiReachable] = useState<boolean>(Boolean(initialHealth));
  const [apiLatencyMs, setApiLatencyMs] = useState<number | null>(null);
  const [probeError, setProbeError] = useState<string | null>(null);
  const [isChecking, setIsChecking] = useState<boolean>(false);
  const [pollIntervalMs, setPollIntervalMs] = useState<number>(15000);
  const [isPaused, setIsPaused] = useState<boolean>(false);
  const [lastCheckedAt, setLastCheckedAt] = useState<string | null>(
    initialHealth?.checked_at || null
  );
  const [history, setHistory] = useState<ProbeSample[]>([]);

  const inFlightRef = useRef(false);
  const mountedRef = useRef(true);
  const onHealthUpdateRef = useRef(onHealthUpdate);
  onHealthUpdateRef.current = onHealthUpdate;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (initialHealth && !health) {
      setHealth(initialHealth);
      setApiReachable(true);
      if (initialHealth.checked_at) {
        setLastCheckedAt(initialHealth.checked_at);
      }
    }
  }, [initialHealth, health]);

  const runProbe = useCallback(async () => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    setIsChecking(true);

    const startedAt = typeof performance !== "undefined" ? performance.now() : Date.now();
    const checkedIso = new Date().toISOString();

    try {
      const result = await montyApi.health();
      const finishedAt = typeof performance !== "undefined" ? performance.now() : Date.now();
      const elapsedMs = Math.max(1, Math.round(finishedAt - startedAt));

      if (!mountedRef.current) return;

      setHealth(result);
      setApiReachable(true);
      setApiLatencyMs(elapsedMs);
      setProbeError(null);
      setLastCheckedAt(result.checked_at || checkedIso);
      onHealthUpdateRef.current?.(result);

      const overall: "ok" | "degraded" =
        result.status === "ok" &&
        (!result.database_configured || result.database_reachable !== false) &&
        (!result.redis_configured || result.redis_reachable !== false)
          ? "ok"
          : "degraded";

      setHistory((prev) => [
        {
          id: `${checkedIso}-${elapsedMs}`,
          checkedAtIso: result.checked_at || checkedIso,
          apiReachable: true,
          apiLatencyMs: elapsedMs,
          overallStatus: overall,
        },
        ...prev.slice(0, 7),
      ]);
    } catch (err) {
      const finishedAt = typeof performance !== "undefined" ? performance.now() : Date.now();
      const elapsedMs = Math.max(1, Math.round(finishedAt - startedAt));

      if (!mountedRef.current) return;

      const msg =
        err instanceof Error
          ? err.message
          : "Impossible de joindre l'API Monty (/api/v1/health).";

      setApiReachable(false);
      setApiLatencyMs(elapsedMs);
      setProbeError(msg);
      setLastCheckedAt(checkedIso);
      onHealthUpdateRef.current?.(null);

      setHistory((prev) => [
        {
          id: `${checkedIso}-err`,
          checkedAtIso: checkedIso,
          apiReachable: false,
          apiLatencyMs: elapsedMs,
          overallStatus: "offline",
        },
        ...prev.slice(0, 7),
      ]);
    } finally {
      inFlightRef.current = false;
      if (mountedRef.current) {
        setIsChecking(false);
      }
    }
  }, []);

  useEffect(() => {
    runProbe();
  }, [runProbe]);

  useEffect(() => {
    if (isPaused || pollIntervalMs <= 0) return;
    const timer = window.setInterval(() => {
      runProbe();
    }, pollIntervalMs);
    return () => window.clearInterval(timer);
  }, [isPaused, pollIntervalMs, runProbe]);

  const apiState: ServiceState = !apiReachable
    ? probeError
      ? "offline"
      : "unconfigured"
    : health?.status === "ok"
      ? "healthy"
      : "degraded";

  const postgresState: ServiceState = !apiReachable
    ? "offline"
    : !health?.database_configured
      ? "unconfigured"
      : health.database_reachable === true
        ? "healthy"
        : "offline";

  const redisState: ServiceState = !apiReachable
    ? "offline"
    : !health?.redis_configured
      ? "unconfigured"
      : health.redis_reachable === false
        ? "offline"
        : health.redis_reachable === true
          ? "healthy"
          : "degraded";

  const globalHeadline = !apiReachable
    ? probeError
      ? "API injoignable"
      : "Initialisation de la sonde..."
    : postgresState === "offline" || redisState === "offline" || health?.status !== "ok"
      ? "Système dégradé"
      : "Tous les services critiques sont connectés";

  const formatTime = (iso: string | null) => {
    if (!iso) return "—";
    try {
      return new Date(iso).toLocaleTimeString("fr-FR", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      });
    } catch {
      return iso.slice(11, 19);
    }
  };

  return (
    <section
      aria-label="Monitoring temps réel API, PostgreSQL et Redis"
      className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4 text-xs shadow-sm"
    >
      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <Activity className="w-4 h-4 text-goldAntique" />
            <h3 className="font-display font-bold text-base text-emeraldDeep">
              Monitoring Temps Réel — API, PostgreSQL &amp; Redis
            </h3>
            <span
              className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full border text-[11px] font-bold ${
                !apiReachable && probeError
                  ? "bg-red-50 text-red-700 border-red-200"
                  : apiState === "healthy" && postgresState !== "offline" && redisState !== "offline"
                    ? "bg-emerald-50 text-emerald-800 border-emerald-200"
                    : "bg-amber-50 text-amber-800 border-amber-300"
              }`}
            >
              <span
                className={`w-2 h-2 rounded-full ${
                  !apiReachable && probeError
                    ? "bg-red-600"
                    : !isPaused && pollIntervalMs > 0
                      ? "bg-emerald-600 animate-pulse"
                      : "bg-amber-600"
                }`}
              />
              {globalHeadline}
            </span>
          </div>
          <p className="text-[11px] text-slateMuted">
            Dernière vérification :{" "}
            <strong className="text-obsidianInk font-mono">{formatTime(lastCheckedAt)}</strong>
            {health?.environment ? ` • Environnement : ${health.environment}` : ""}
            {health?.version ? ` • API v${health.version}` : ""}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="inline-flex items-center rounded-xl border border-borderSubtle bg-ivoryWarm p-0.5">
            {POLL_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => {
                  setPollIntervalMs(opt.value);
                  if (opt.value > 0) setIsPaused(false);
                }}
                className={`px-2.5 py-1.5 rounded-lg text-[11px] font-semibold transition ${
                  pollIntervalMs === opt.value
                    ? "bg-emeraldDeep text-goldChampagne"
                    : "text-slateMuted hover:text-emeraldDeep"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>

          {pollIntervalMs > 0 && (
            <button
              type="button"
              onClick={() => setIsPaused((prev) => !prev)}
              className="inline-flex items-center gap-1 px-3 py-2 rounded-xl border border-borderSubtle bg-ivoryWarm text-emeraldDeep font-semibold hover:border-emeraldMid"
            >
              {isPaused ? <Play className="w-3.5 h-3.5" /> : <Pause className="w-3.5 h-3.5" />}
              {isPaused ? "Reprendre" : "Pause"}
            </button>
          )}

          <button
            type="button"
            onClick={runProbe}
            disabled={isChecking}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-60"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isChecking ? "animate-spin" : ""}`} />
            {isChecking ? "Sonde..." : "Tester maintenant"}
          </button>
        </div>
      </div>

      {probeError && (
        <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-xs text-red-800 flex items-start gap-2">
          <XCircle className="w-4 h-4 shrink-0 mt-0.5" />
          <div>
            <p className="font-bold">Connexion API interrompue</p>
            <p className="mt-0.5">{probeError}</p>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        {/* Carte API FastAPI */}
        <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2.5">
          <div className="flex items-center justify-between gap-2">
            <span className="inline-flex items-center gap-1.5 font-bold text-emeraldDeep text-sm">
              <Server className="w-4 h-4 text-goldAntique" />
              API FastAPI
            </span>
            <span
              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-[11px] font-bold ${badgeClasses(apiState)}`}
            >
              <StatusIcon state={apiState} />
              {apiState === "healthy"
                ? "Connectée"
                : apiState === "degraded"
                  ? "Dégradée"
                  : apiState === "offline"
                    ? "Hors ligne"
                    : "En attente"}
            </span>
          </div>
          <div className="space-y-1 text-[11px] text-slateMuted">
            <p>
              Service :{" "}
              <span className="font-mono text-obsidianInk">{health?.service ?? "monty-saas-api"}</span>
            </p>
            <p>
              Latence HTTP :{" "}
              <strong className="font-mono text-obsidianInk">
                {apiLatencyMs !== null ? `${apiLatencyMs} ms` : "—"}
              </strong>
            </p>
            <p>
              Mode stockage :{" "}
              <span className="font-semibold text-obsidianInk">
                {health?.storage_mode === "postgresql_rls"
                  ? "PostgreSQL RLS"
                  : health?.storage_mode === "in_memory_fallback"
                    ? "Mémoire (Dev)"
                    : "Inconnu"}
              </span>
            </p>
          </div>
        </div>

        {/* Carte PostgreSQL */}
        <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2.5">
          <div className="flex items-center justify-between gap-2">
            <span className="inline-flex items-center gap-1.5 font-bold text-emeraldDeep text-sm">
              <Database className="w-4 h-4 text-goldAntique" />
              PostgreSQL 16 RLS
            </span>
            <span
              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-[11px] font-bold ${badgeClasses(postgresState)}`}
            >
              <StatusIcon state={postgresState} />
              {!apiReachable
                ? "API requise"
                : !health?.database_configured
                  ? "Mode Mémoire"
                  : health.database_reachable
                    ? "Connecté"
                    : "Hors ligne"}
            </span>
          </div>
          <div className="space-y-1 text-[11px] text-slateMuted">
            <p>
              Configuration :{" "}
              <span className="font-semibold text-obsidianInk">
                {health?.database_configured ? "DATABASE_URL active" : "Non configurée (fallback dev)"}
              </span>
            </p>
            <p>
              Sonde SELECT 1 :{" "}
              <strong className="font-mono text-obsidianInk">
                {health?.database_reachable === true
                  ? health.database_latency_ms != null
                    ? `OK (${health.database_latency_ms} ms)`
                    : "OK"
                  : health?.database_reachable === false
                    ? "Échec connexion"
                    : "—"}
              </strong>
            </p>
            <p>
              Isolation multi-tenant :{" "}
              <span className="font-semibold text-obsidianInk">
                {health?.database_configured ? "Row Level Security active" : "Service mémoire"}
              </span>
            </p>
          </div>
        </div>

        {/* Carte Redis */}
        <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2.5">
          <div className="flex items-center justify-between gap-2">
            <span className="inline-flex items-center gap-1.5 font-bold text-emeraldDeep text-sm">
              <Zap className="w-4 h-4 text-goldAntique" />
              Redis &amp; Workers ARQ
            </span>
            <span
              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-[11px] font-bold ${badgeClasses(redisState)}`}
            >
              <StatusIcon state={redisState} />
              {!apiReachable
                ? "API requise"
                : !health?.redis_configured
                  ? "Non configuré"
                  : health.redis_reachable === false
                    ? "Hors ligne"
                    : health.redis_reachable === true
                      ? "Connecté"
                      : "Configuré"}
            </span>
          </div>
          <div className="space-y-1 text-[11px] text-slateMuted">
            <p>
              Configuration :{" "}
              <span className="font-semibold text-obsidianInk">
                {health?.redis_configured ? "REDIS_URL active" : "Non configurée"}
              </span>
            </p>
            <p>
              Sonde PING :{" "}
              <strong className="font-mono text-obsidianInk">
                {health?.redis_reachable === true
                  ? health.redis_latency_ms != null
                    ? `PONG (${health.redis_latency_ms} ms)`
                    : "PONG"
                  : health?.redis_reachable === false
                    ? "Échec PING"
                    : health?.redis_configured
                      ? "En attente"
                      : "—"}
              </strong>
            </p>
            <p>
              Jobs ARQ :{" "}
              <span className="font-semibold text-obsidianInk">
                CSV • Purge RGPD • DVF • Flux
              </span>
            </p>
          </div>
        </div>
      </div>

      {/* Intégrations complémentaires + historique des derniers pings */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 pt-1 border-t border-borderSubtle/70 text-[11px]">
        <div className="flex flex-wrap items-center gap-2 text-slateMuted">
          <span className="font-semibold text-emeraldDeep">Services externes :</span>
          <span
            className={`px-2 py-0.5 rounded-md border font-semibold ${
              health?.stripe_configured
                ? "bg-emerald-50 text-emerald-800 border-emerald-200"
                : "bg-stone-100 text-slateMuted border-borderSubtle"
            }`}
          >
            Stripe : {health?.stripe_configured ? "Configuré" : "Non configuré"}
          </span>
          <span
            className={`px-2 py-0.5 rounded-md border font-semibold ${
              health?.openai_configured
                ? "bg-emerald-50 text-emerald-800 border-emerald-200"
                : "bg-stone-100 text-slateMuted border-borderSubtle"
            }`}
          >
            OpenAI : {health?.openai_configured ? "Configuré" : "Fallback / Anthropic"}
          </span>
        </div>

        {history.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="text-slateMuted font-medium mr-1">Historique sondes :</span>
            {history.map((sample) => (
              <span
                key={sample.id}
                title={`${formatTime(sample.checkedAtIso)} — ${sample.apiLatencyMs} ms`}
                className={`px-2 py-0.5 rounded-md font-mono text-[10px] border ${
                  sample.overallStatus === "ok"
                    ? "bg-emerald-50 text-emerald-800 border-emerald-200"
                    : sample.overallStatus === "degraded"
                      ? "bg-amber-50 text-amber-800 border-amber-300"
                      : "bg-red-50 text-red-700 border-red-200"
                }`}
              >
                {formatTime(sample.checkedAtIso)} ({sample.apiLatencyMs}ms)
              </span>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
