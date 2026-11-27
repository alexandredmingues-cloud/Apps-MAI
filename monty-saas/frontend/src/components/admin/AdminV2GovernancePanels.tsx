"use client";

import React, { useEffect, useState } from "react";
import {
  Activity,
  Bot,
  CheckCircle2,
  KeyRound,
  RotateCcw,
  SlidersHorizontal,
  Smartphone,
  TrendingUp,
  UploadCloud,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type {
  MobileAppRelease,
  ModuleFeatureFlag,
  ModuleFlagHistoryEntry,
  ModuleFlagStatus,
  MontyAIEngineConfig,
  MontyAIMetricsSummary,
  PriceIndexConfig,
  PriceIndexHistoryRecord,
} from "@/lib/types";

interface AdminV2GovernancePanelsProps {
  onStatusMessage: (msg: string) => void;
  onErrorMessage: (msg: string) => void;
}

export function AdminV2GovernancePanels({
  onStatusMessage,
  onErrorMessage,
}: AdminV2GovernancePanelsProps) {
  const [subTab, setSubTab] = useState<"modules" | "monty_ai" | "mobile_apk" | "price_index">("modules");

  // Module flags
  const [moduleFlags, setModuleFlags] = useState<ModuleFeatureFlag[]>([]);
  const [flagHistory, setFlagHistory] = useState<ModuleFlagHistoryEntry[]>([]);

  // Monty AI Governance
  const [engines, setEngines] = useState<MontyAIEngineConfig[]>([]);
  const [aiMetrics, setAiMetrics] = useState<MontyAIMetricsSummary | null>(null);
  const [providerName, setProviderName] = useState("openai");
  const [providerKey, setProviderKey] = useState("");
  const [localUrl, setLocalUrl] = useState("");

  // Mobile APK
  const [releases, setReleases] = useState<MobileAppRelease[]>([]);
  const [totalDownloads, setTotalDownloads] = useState(0);
  const [apkFile, setApkFile] = useState<File | null>(null);
  const [apkVersionName, setApkVersionName] = useState("1.1.0");
  const [apkVersionCode, setApkVersionCode] = useState(2);
  const [apkNotes, setApkNotes] = useState("Mise à jour Monty Mobile avec Monty Price Index et Monty AI.");
  const [apkUploading, setApkUploading] = useState(false);

  // Price Index Config & History
  const [piConfig, setPiConfig] = useState<PriceIndexConfig | null>(null);
  const [piHistory, setPiHistory] = useState<PriceIndexHistoryRecord[]>([]);

  async function refreshAll() {
    try {
      const [mfRes, histRes, engRes, metRes, relRes, cfgRes, piHistRes] = await Promise.all([
        montyApi.adminListModuleFlags().catch(() => ({ count: 0, modules: [] as ModuleFeatureFlag[] })),
        montyApi.adminModuleFlagHistory().catch(() => ({ count: 0, history: [] as ModuleFlagHistoryEntry[] })),
        montyApi.adminListAiEngines().catch(() => ({ count: 0, engines: [] as MontyAIEngineConfig[] })),
        montyApi.adminMontyAiMetrics().catch(() => null),
        montyApi.adminListMobileReleases().catch(() => ({ count: 0, total_downloads: 0, releases: [] as MobileAppRelease[] })),
        montyApi.adminGetPriceIndexConfig().catch(() => null),
        montyApi.getPriceIndexHistory(20).catch(() => ({ count: 0, history: [] as PriceIndexHistoryRecord[] })),
      ]);
      setModuleFlags(mfRes.modules);
      setFlagHistory(histRes.history);
      setEngines(engRes.engines);
      setAiMetrics(metRes);
      setReleases(relRes.releases);
      setTotalDownloads(relRes.total_downloads);
      setPiConfig(cfgRes);
      setPiHistory(piHistRes.history);
    } catch {
      // Handled gracefully
    }
  }

  useEffect(() => {
    refreshAll();
  }, []);

  async function handleUpdateModuleFlag(mod: ModuleFeatureFlag, status: ModuleFlagStatus, visible: boolean) {
    try {
      const updated = await montyApi.adminUpdateModuleFlag(mod.module_key, { status, visible });
      setModuleFlags((prev) => prev.map((m) => (m.module_key === updated.module_key ? updated : m)));
      onStatusMessage(`Module « ${updated.label} » mis à jour (${updated.status}, visible=${updated.visible}).`);
      const h = await montyApi.adminModuleFlagHistory().catch(() => null);
      if (h) setFlagHistory(h.history);
    } catch (err) {
      onErrorMessage(err instanceof Error ? err.message : "Mise à jour module refusée.");
    }
  }

  async function handleRestoreModules() {
    try {
      const res = await montyApi.adminRestoreModuleFlags();
      setModuleFlags(res.modules);
      onStatusMessage("Visibilité et statuts des modules restaurés par défaut.");
    } catch (err) {
      onErrorMessage(err instanceof Error ? err.message : "Restauration refusée.");
    }
  }

  async function handleToggleEngine(
    eng: MontyAIEngineConfig,
    patch: { enabled?: boolean; is_default?: boolean; is_fallback?: boolean }
  ) {
    try {
      await montyApi.adminUpdateAiEngine(eng.engine_id, patch);
      await refreshAll();
      onStatusMessage(`Moteur « ${eng.display_label} » mis à jour.`);
    } catch (err) {
      onErrorMessage(err instanceof Error ? err.message : "Modification moteur refusée.");
    }
  }

  async function handleSaveProviderKey(e: React.FormEvent) {
    e.preventDefault();
    try {
      const res = await montyApi.adminConfigureAiProviderKey({
        provider: providerName,
        api_key: providerKey.trim() || undefined,
        local_base_url: localUrl.trim() || undefined,
      });
      setProviderKey("");
      await refreshAll();
      onStatusMessage(`Fournisseur ${res.provider} configuré (${res.masked_value}).`);
    } catch (err) {
      onErrorMessage(err instanceof Error ? err.message : "Impossible d'enregistrer la clé serveur.");
    }
  }

  async function handleUploadApk(e: React.FormEvent) {
    e.preventDefault();
    if (!apkFile) {
      onErrorMessage("Sélectionnez un fichier .apk valide.");
      return;
    }
    setApkUploading(true);
    try {
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
          const raw = String(reader.result || "");
          const comma = raw.indexOf(",");
          resolve(comma >= 0 ? raw.slice(comma + 1) : raw);
        };
        reader.onerror = () => reject(new Error("Lecture du fichier APK impossible."));
        reader.readAsDataURL(apkFile);
      });
      const rel = await montyApi.adminUploadMobileApk({
        file_name: apkFile.name,
        content_base64: base64,
        version_name: apkVersionName.trim(),
        version_code: apkVersionCode,
        release_notes: apkNotes.trim(),
        publish_now: true,
        mark_recommended: true,
      });
      setApkFile(null);
      await refreshAll();
      onStatusMessage(`APK v${rel.version_name} validé (SHA-256 ${rel.sha256.slice(0, 12)}…) et publié.`);
    } catch (err) {
      onErrorMessage(err instanceof Error ? err.message : "Échec upload APK.");
    } finally {
      setApkUploading(false);
    }
  }

  async function handleUpdateRelease(rel: MobileAppRelease, patch: { is_published?: boolean; is_recommended?: boolean }) {
    try {
      await montyApi.adminUpdateMobileRelease(rel.id, patch);
      await refreshAll();
      onStatusMessage(`Version v${rel.version_name} mise à jour.`);
    } catch (err) {
      onErrorMessage(err instanceof Error ? err.message : "Mise à jour version refusée.");
    }
  }

  async function handleSavePriceIndexConfig(e: React.FormEvent) {
    e.preventDefault();
    if (!piConfig) return;
    try {
      const updated = await montyApi.adminUpdatePriceIndexConfig({
        excellent_deal_pct: piConfig.excellent_deal_pct ?? -15,
        very_good_deal_pct: piConfig.very_good_deal_pct,
        good_deal_pct: piConfig.good_deal_pct,
        above_market_pct: piConfig.above_market_pct,
        overpriced_pct: piConfig.overpriced_pct,
        min_comparables_required: piConfig.min_comparables_required,
        max_comparable_distance_m: piConfig.max_comparable_distance_m,
        max_comparable_age_months: piConfig.max_comparable_age_months,
      });
      setPiConfig(updated);
      onStatusMessage("Seuils Monty Price Index (6 paliers) enregistrés.");
    } catch (err) {
      onErrorMessage(err instanceof Error ? err.message : "Validation des seuils échouée.");
    }
  }

  return (
    <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-5 text-xs">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-borderSubtle pb-3">
        <div>
          <h3 className="font-display font-bold text-base text-emeraldDeep">
            Gouvernance Avancée V2 (Modules • Monty AI • APK Android • Monty Price Index)
          </h3>
          <p className="text-slateMuted">
            Pilotage centralisé sans exposition de secrets, avec traçabilité et garde-fous de disponibilité.
          </p>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {(
            [
              ["modules", "Modules & Visibilité", SlidersHorizontal],
              ["monty_ai", "Monty AI Core", Bot],
              ["mobile_apk", "App Mobile APK", Smartphone],
              ["price_index", "Monty Price Index", TrendingUp],
            ] as const
          ).map(([key, label, Icon]) => (
            <button
              key={key}
              type="button"
              onClick={() => setSubTab(key)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-xl font-bold transition ${
                subTab === key
                  ? "bg-emeraldDeep text-goldChampagne"
                  : "bg-ivoryWarm border border-borderSubtle text-obsidianInk hover:border-emeraldMid"
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {label}
            </button>
          ))}
        </div>
      </div>

      {subTab === "modules" && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-slateMuted">
              Contrôle des modules affichés dans le cockpit. Les modules critiques (Dashboard, Administration) restent
              protégés.
            </p>
            <button
              type="button"
              onClick={handleRestoreModules}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-emeraldDeep text-emeraldDeep font-bold hover:bg-ivoryWarm"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Restaurer valeurs par défaut
            </button>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2.5">
            {moduleFlags.map((mod) => (
              <div key={mod.module_key} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-bold text-emeraldDeep">{mod.label}</span>
                  {mod.critical && (
                    <span className="px-2 py-0.5 rounded bg-emeraldDeep/10 text-emeraldDeep text-[10px] font-bold">
                      Critique
                    </span>
                  )}
                </div>
                <div className="flex items-center justify-between gap-2">
                  <select
                    value={mod.status}
                    disabled={mod.critical}
                    onChange={(e) => handleUpdateModuleFlag(mod, e.target.value as ModuleFlagStatus, mod.visible)}
                    className="rounded-lg border border-borderSubtle bg-white px-2 py-1 text-[11px] font-semibold"
                  >
                    <option value="DISPONIBLE">DISPONIBLE</option>
                    <option value="EN_TEST">EN_TEST</option>
                    <option value="EN_DEVELOPPEMENT">EN_DEVELOPPEMENT</option>
                  </select>
                  <label className="inline-flex items-center gap-1.5 font-semibold cursor-pointer">
                    <input
                      type="checkbox"
                      checked={mod.visible}
                      disabled={mod.critical}
                      onChange={(e) => handleUpdateModuleFlag(mod, mod.status, e.target.checked)}
                      className="accent-emeraldDeep"
                    />
                    Visible
                  </label>
                </div>
              </div>
            ))}
          </div>
          {flagHistory.length > 0 && (
            <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 space-y-1 max-h-36 overflow-y-auto">
              <p className="font-bold text-emeraldDeep">Historique des modifications ({flagHistory.length})</p>
              {flagHistory.slice(0, 6).map((h) => (
                <p key={h.id} className="text-[11px] text-slateMuted">
                  {h.updated_at.slice(0, 16)} — <strong>{h.module_key}</strong> → {h.status} (visible:{" "}
                  {String(h.visible)}) par {h.updated_by}
                </p>
              ))}
            </div>
          )}
        </div>
      )}

      {subTab === "monty_ai" && (
        <div className="space-y-4">
          <div className="rounded-xl bg-amber-50/80 border border-amber-200 px-3 py-2 text-[11px] text-amber-950">
            Gouvernance stricte (fail-closed) : la modification des moteurs par défaut et l&apos;enregistrement des clés API / URLs locales sont réservés aux identifiants listés dans <code className="font-mono">PLATFORM_ADMIN_USER_IDS</code>.
          </div>
          {aiMetrics && (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3">
                <p className="text-slateMuted">Requêtes Monty AI</p>
                <p className="text-lg font-bold text-emeraldDeep">{aiMetrics.total_requests}</p>
              </div>
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3">
                <p className="text-slateMuted">Latence moyenne</p>
                <p className="text-lg font-bold text-emeraldDeep">{aiMetrics.avg_latency_ms} ms</p>
              </div>
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3">
                <p className="text-slateMuted">Taux de fallback</p>
                <p className="text-lg font-bold text-emeraldDeep">{aiMetrics.fallback_rate_percent} %</p>
              </div>
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3">
                <p className="text-slateMuted">Coût estimé jour</p>
                <p className="text-lg font-bold text-goldAntique">{aiMetrics.daily_spend_euro.toFixed(4)} €</p>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
            {engines.map((eng) => (
              <div key={eng.engine_id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <p className="font-bold text-emeraldDeep">{eng.display_label}</p>
                    <p className="text-[11px] text-slateMuted">
                      {eng.tier === "local" ? "Open-Source Local" : "Externe Serveur"} • {eng.model_name} •{" "}
                      {eng.configured ? "Configuré ✓" : "Non configuré"}
                    </p>
                  </div>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-white border border-borderSubtle">
                    {eng.masked_api_key || "—"}
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => handleToggleEngine(eng, { enabled: !eng.enabled })}
                    className={`px-2.5 py-1 rounded-lg font-bold ${
                      eng.enabled ? "bg-emeraldDeep text-goldChampagne" : "bg-white border border-borderSubtle text-slateMuted"
                    }`}
                  >
                    {eng.enabled ? "Activé" : "Désactivé"}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleToggleEngine(eng, { is_default: true })}
                    className={`px-2.5 py-1 rounded-lg font-bold ${
                      eng.is_default ? "bg-goldAntique text-emeraldDeep" : "bg-white border border-borderSubtle"
                    }`}
                  >
                    {eng.is_default ? "Moteur par défaut ★" : "Définir défaut"}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleToggleEngine(eng, { is_fallback: !eng.is_fallback })}
                    className={`px-2.5 py-1 rounded-lg font-bold ${
                      eng.is_fallback ? "bg-amber-100 text-amber-950" : "bg-white border border-borderSubtle"
                    }`}
                  >
                    {eng.is_fallback ? "Secours actif" : "Secours"}
                  </button>
                </div>
              </div>
            ))}
          </div>

          <form onSubmit={handleSaveProviderKey} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3.5 grid grid-cols-1 sm:grid-cols-4 gap-2 items-center">
            <div className="flex items-center gap-1.5 font-bold text-emeraldDeep">
              <KeyRound className="w-4 h-4 text-goldAntique" />
              Clé serveur / URL Ollama
            </div>
            <select
              value={providerName}
              onChange={(e) => setProviderName(e.target.value)}
              className="rounded-lg border border-borderSubtle bg-white px-2.5 py-2"
            >
              <option value="openai">OpenAI</option>
              <option value="anthropic">Anthropic</option>
              <option value="gemini">Google Gemini</option>
              <option value="mistral">Mistral AI</option>
              <option value="deepseek">DeepSeek</option>
              <option value="ollama">Ollama Local (URL)</option>
            </select>
            {providerName === "ollama" ? (
              <input
                type="url"
                value={localUrl}
                onChange={(e) => setLocalUrl(e.target.value)}
                placeholder="http://localhost:11434"
                className="rounded-lg border border-borderSubtle bg-white px-2.5 py-2"
              />
            ) : (
              <input
                type="password"
                value={providerKey}
                onChange={(e) => setProviderKey(e.target.value)}
                placeholder="Clé API chiffrée côté serveur"
                className="rounded-lg border border-borderSubtle bg-white px-2.5 py-2"
              />
            )}
            <button type="submit" className="px-3 py-2 rounded-lg bg-emeraldDeep text-goldChampagne font-bold">
              Enregistrer côté serveur
            </button>
          </form>
        </div>
      )}

      {subTab === "mobile_apk" && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-slateMuted">
              Total téléchargements vérifiés : <strong className="text-emeraldDeep">{totalDownloads}</strong>
            </p>
          </div>
          <form onSubmit={handleUploadApk} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 grid grid-cols-1 sm:grid-cols-4 gap-2.5">
            <input
              type="file"
              accept=".apk"
              onChange={(e) => setApkFile(e.target.files?.[0] ?? null)}
              className="sm:col-span-2 rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
            />
            <input
              type="text"
              value={apkVersionName}
              onChange={(e) => setApkVersionName(e.target.value)}
              placeholder="Version (ex: 1.1.0)"
              className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
            />
            <input
              type="number"
              min={1}
              value={apkVersionCode}
              onChange={(e) => setApkVersionCode(Number(e.target.value) || 1)}
              placeholder="Code version"
              className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
            />
            <input
              type="text"
              value={apkNotes}
              onChange={(e) => setApkNotes(e.target.value)}
              placeholder="Notes de version"
              className="sm:col-span-3 rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
            />
            <button
              type="submit"
              disabled={apkUploading}
              className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-emeraldDeep text-goldChampagne font-bold disabled:opacity-50"
            >
              <UploadCloud className="w-4 h-4" />
              {apkUploading ? "Validation ZIP/SHA256..." : "Publier nouvel APK"}
            </button>
          </form>

          <div className="space-y-2">
            {releases.map((rel) => (
              <div
                key={rel.id}
                className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 flex flex-wrap items-center justify-between gap-2"
              >
                <div>
                  <p className="font-bold text-emeraldDeep">
                    v{rel.version_name} ({rel.file_name}) • {rel.file_size_mb} Mo
                  </p>
                  <p className="text-[11px] font-mono text-slateMuted">
                    SHA-256 : {rel.sha256.slice(0, 20)}… • {rel.download_count} téléchargement(s)
                  </p>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  <button
                    type="button"
                    onClick={() => handleUpdateRelease(rel, { is_published: !rel.is_published })}
                    className={`px-2.5 py-1 rounded-lg font-bold ${
                      rel.is_published ? "bg-emerald-100 text-emerald-900" : "bg-white border border-borderSubtle"
                    }`}
                  >
                    {rel.is_published ? "Publiée ✓" : "Brouillon"}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleUpdateRelease(rel, { is_recommended: true })}
                    className={`px-2.5 py-1 rounded-lg font-bold ${
                      rel.is_recommended ? "bg-goldAntique text-emeraldDeep" : "bg-white border border-borderSubtle"
                    }`}
                  >
                    {rel.is_recommended ? "Recommandée ★" : "Recommander"}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {subTab === "price_index" && (
        <div className="space-y-4">
          {piConfig && (
            <form onSubmit={handleSavePriceIndexConfig} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 grid grid-cols-2 sm:grid-cols-4 gap-3">
              <label className="block">
                <span className="text-[11px] font-semibold text-slateMuted">Excellente affaire (&lt; %)</span>
                <input
                  type="number"
                  step="0.5"
                  value={piConfig.excellent_deal_pct ?? -15}
                  onChange={(e) => setPiConfig({ ...piConfig, excellent_deal_pct: Number(e.target.value) })}
                  className="mt-1 w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold text-slateMuted">Très bonne affaire (&lt; %)</span>
                <input
                  type="number"
                  step="0.5"
                  value={piConfig.very_good_deal_pct}
                  onChange={(e) => setPiConfig({ ...piConfig, very_good_deal_pct: Number(e.target.value) })}
                  className="mt-1 w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold text-slateMuted">Bonne affaire (&lt; %)</span>
                <input
                  type="number"
                  step="0.5"
                  value={piConfig.good_deal_pct}
                  onChange={(e) => setPiConfig({ ...piConfig, good_deal_pct: Number(e.target.value) })}
                  className="mt-1 w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold text-slateMuted">Au-dessus marché (&gt; %)</span>
                <input
                  type="number"
                  step="0.5"
                  value={piConfig.above_market_pct}
                  onChange={(e) => setPiConfig({ ...piConfig, above_market_pct: Number(e.target.value) })}
                  className="mt-1 w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold text-slateMuted">Trop cher (&gt; %)</span>
                <input
                  type="number"
                  step="0.5"
                  value={piConfig.overpriced_pct}
                  onChange={(e) => setPiConfig({ ...piConfig, overpriced_pct: Number(e.target.value) })}
                  className="mt-1 w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold text-slateMuted">Comparables min.</span>
                <input
                  type="number"
                  min={1}
                  max={25}
                  value={piConfig.min_comparables_required}
                  onChange={(e) => setPiConfig({ ...piConfig, min_comparables_required: Number(e.target.value) || 3 })}
                  className="mt-1 w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold text-slateMuted">Distance max (m)</span>
                <input
                  type="number"
                  step={100}
                  value={piConfig.max_comparable_distance_m}
                  onChange={(e) => setPiConfig({ ...piConfig, max_comparable_distance_m: Number(e.target.value) || 2500 })}
                  className="mt-1 w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
              </label>
              <label className="block">
                <span className="text-[11px] font-semibold text-slateMuted">Âge comparables (mois)</span>
                <input
                  type="number"
                  value={piConfig.max_comparable_age_months}
                  onChange={(e) => setPiConfig({ ...piConfig, max_comparable_age_months: Number(e.target.value) || 24 })}
                  className="mt-1 w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
              </label>
              <div className="flex items-end">
                <button
                  type="submit"
                  className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-emeraldDeep text-goldChampagne font-bold"
                >
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  Enregistrer seuils
                </button>
              </div>
            </form>
          )}

          <div className="space-y-1.5">
            <p className="font-bold text-emeraldDeep flex items-center gap-1.5">
              <Activity className="w-3.5 h-3.5 text-goldAntique" />
              Historique des analyses Monty Price Index ({piHistory.length})
            </p>
            {piHistory.length === 0 ? (
              <p className="text-slateMuted">Aucune analyse enregistrée sur cette session.</p>
            ) : (
              <div className="space-y-1.5 max-h-48 overflow-y-auto">
                {piHistory.map((item) => (
                  <div
                    key={item.id}
                    className="rounded-xl bg-ivoryWarm border border-borderSubtle px-3 py-2 flex flex-wrap items-center justify-between gap-2"
                  >
                    <span>
                      <strong className="text-emeraldDeep">{item.title || item.city}</strong> ({item.city}) —{" "}
                      {item.asking_price_euro.toLocaleString("fr-FR")} € vs{" "}
                      {item.estimated_market_value.toLocaleString("fr-FR")} €
                    </span>
                    <span className="font-bold text-emeraldDeep">
                      {item.category} ({item.gap_percent > 0 ? `+${item.gap_percent}%` : `${item.gap_percent}%`})
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
