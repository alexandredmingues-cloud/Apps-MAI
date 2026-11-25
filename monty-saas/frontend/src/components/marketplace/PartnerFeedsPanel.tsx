"use client";

import React, { useEffect, useState } from "react";
import { CheckCircle2, Globe2, Link2, RefreshCw, ShieldCheck } from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type {
  AuthorizedFeedSource,
  CountryCode,
  FeedFormat,
  FeedSyncResult,
  PublisherType,
} from "@/lib/types";

const SAMPLE_XML_SNIPPET = `<?xml version="1.0" encoding="utf-8"?>
<biens>
  <bien>
    <id>SYN-2026-01</id>
    <titre>Appartement T3 Terrasse Calme Lyon 6e</titre>
    <type_bien>Appartement</type_bien>
    <ville>Lyon</ville>
    <quartier>Foch / Parc Tête d'Or</quartier>
    <prix>475000</prix>
    <surface_m2>82</surface_m2>
    <pieces>3</pieces>
    <dpe>B</dpe>
    <description>T3 lumineux traversant avec terrasse plein sud et garage fermé.</description>
    <url>https://partenaire.exemple.fr/annonces/syn-2026-01</url>
  </bien>
</biens>`;

export function PartnerFeedsPanel({ onSynced }: { onSynced?: () => void }) {
  const [feeds, setFeeds] = useState<AuthorizedFeedSource[]>([]);
  const [name, setName] = useState("");
  const [countryCode, setCountryCode] = useState<CountryCode>("FR");
  const [publisherType, setPublisherType] = useState<PublisherType>("AGENCE");
  const [feedFormat, setFeedFormat] = useState<FeedFormat>("XML");
  const [feedUrl, setFeedUrl] = useState("https://syndication.partenaire.fr/export.xml");
  const [agreementConfirmed, setAgreementConfirmed] = useState(false);
  const [autoPublish, setAutoPublish] = useState(true);

  const [selectedFeedId, setSelectedFeedId] = useState("");
  const [inlinePayload, setInlinePayload] = useState(SAMPLE_XML_SNIPPET);
  const [lastSync, setLastSync] = useState<FeedSyncResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  async function refreshFeeds() {
    setErrorMsg(null);
    try {
      const res = await montyApi.listAuthorizedFeeds();
      setFeeds(res.feeds);
      if (res.feeds.length > 0 && !selectedFeedId) {
        setSelectedFeedId(res.feeds[0].id);
      }
    } catch (err) {
      setErrorMsg(
        err instanceof Error
          ? err.message
          : "Connectez une session Pro pour gérer les flux partenaires autorisés."
      );
    }
  }

  useEffect(() => {
    refreshFeeds();
  }, []);

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    setErrorMsg(null);
    if (name.trim().length < 3) {
      setErrorMsg("Saisissez le nom du partenaire ou de l'agence (≥ 3 caractères).");
      return;
    }
    if (!agreementConfirmed) {
      setErrorMsg("L'accord de diffusion et la conformité RGPD doivent être confirmés.");
      return;
    }
    setLoading(true);
    try {
      const created = await montyApi.registerAuthorizedFeed({
        name: name.trim(),
        country_code: countryCode,
        publisher_type: publisherType,
        feed_format: feedFormat,
        feed_url: feedUrl.trim(),
        agreement_confirmed: agreementConfirmed,
        auto_publish_verified_agency: autoPublish,
      });
      setName("");
      setSelectedFeedId(created.id);
      await refreshFeeds();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Impossible d'enregistrer ce flux.");
    } finally {
      setLoading(false);
    }
  }

  async function handleSync(useInline: boolean) {
    if (!selectedFeedId) return;
    setErrorMsg(null);
    setLoading(true);
    try {
      const res = await montyApi.syncAuthorizedFeed(
        selectedFeedId,
        useInline ? inlinePayload : undefined
      );
      setLastSync(res);
      await refreshFeeds();
      onSynced?.();
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Échec de synchronisation du flux.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-2">
        <h3 className="font-display font-bold text-lg text-emeraldDeep flex items-center gap-2">
          <Globe2 className="w-5 h-5 text-goldAntique" />
          Connecteur de Flux Partenaires Autorisés (XML / JSON / JSON-LD)
        </h3>
        <p className="text-xs text-slateMuted">
          Centralisez légalement des annonces <strong>Agences (syndication Apimo/Hektor/Ubiflow)</strong> et{" "}
          <strong>Particuliers ayant donné leur accord</strong> : vérification SSRF, respect strict de{" "}
          <code>robots.txt</code>, masquage PII RGPD et déduplication automatique.
        </p>
      </div>

      {errorMsg && (
        <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-3 text-xs text-amber-900">
          {errorMsg}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <form onSubmit={handleRegister} className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
          <h4 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <Link2 className="w-4 h-4 text-goldAntique" />
            Déclarer un flux officiel / partenaire
          </h4>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Nom du flux (ex: Passerelle Agence Rhône)"
            className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
          />
          <div className="grid grid-cols-3 gap-2">
            <select
              value={countryCode}
              onChange={(e) => setCountryCode(e.target.value as CountryCode)}
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="FR">🇫🇷 France</option>
              <option value="PT">🇵🇹 Portugal</option>
            </select>
            <select
              value={publisherType}
              onChange={(e) => setPublisherType(e.target.value as PublisherType)}
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="AGENCE">Pro / Agence</option>
              <option value="PARTICULIER">Particulier autorisé</option>
            </select>
            <select
              value={feedFormat}
              onChange={(e) => setFeedFormat(e.target.value as FeedFormat)}
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="XML">XML Syndication</option>
              <option value="JSON">API JSON</option>
              <option value="JSON_LD">Schema.org JSON-LD</option>
            </select>
          </div>
          <input
            type="url"
            value={feedUrl}
            onChange={(e) => setFeedUrl(e.target.value)}
            placeholder="https://partenaire.exemple.fr/flux.xml"
            className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
          />
          <label className="flex items-start gap-2 p-2.5 rounded-xl bg-ivoryWarm border border-borderSubtle cursor-pointer">
            <input
              type="checkbox"
              checked={agreementConfirmed}
              onChange={(e) => setAgreementConfirmed(e.target.checked)}
              className="mt-0.5"
            />
            <span>
              Je confirme disposer d&apos;un accord de rediffusion écrit et du consentement RGPD de la source (aucune collecte non autorisée).
            </span>
          </label>
          {publisherType === "AGENCE" && (
            <label className="flex items-center gap-2 text-slateMuted">
              <input
                type="checkbox"
                checked={autoPublish}
                onChange={(e) => setAutoPublish(e.target.checked)}
              />
              <span>Publier automatiquement les mandats agences vérifiés</span>
            </label>
          )}
          <button
            type="submit"
            disabled={loading}
            className="w-full px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-50"
          >
            Enregistrer ce flux autorisé
          </button>
        </form>

        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
          <h4 className="font-display font-bold text-base text-emeraldDeep flex items-center gap-2">
            <RefreshCw className="w-4 h-4 text-goldAntique" />
            Synchroniser / Importer un fichier XML ou JSON
          </h4>
          <select
            value={selectedFeedId}
            onChange={(e) => setSelectedFeedId(e.target.value)}
            className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
          >
            {feeds.map((f) => (
              <option key={f.id} value={f.id}>
                {f.name} ({f.country_code} • {f.feed_format} • {f.total_ingested} ingérées)
              </option>
            ))}
          </select>
          <textarea
            rows={6}
            value={inlinePayload}
            onChange={(e) => setInlinePayload(e.target.value)}
            className="w-full font-mono text-[11px] rounded-xl border border-borderSubtle bg-ivoryWarm p-3"
          />
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={loading || !selectedFeedId}
              onClick={() => handleSync(true)}
              className="flex-1 px-3 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne font-bold disabled:opacity-40"
            >
              Importer ce flux XML/JSON
            </button>
            <button
              type="button"
              disabled={loading || !selectedFeedId}
              onClick={() => handleSync(false)}
              className="px-3 py-2.5 rounded-xl border border-emeraldDeep text-emeraldDeep font-bold disabled:opacity-40"
            >
              Sync URL distante (robots.txt)
            </button>
          </div>

          {lastSync && (
            <div className="rounded-xl bg-emerald-50 border border-emerald-300 p-3 space-y-1 text-emerald-950">
              <p className="font-bold flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-emerald-700" />
                {lastSync.feed_name} — Statut : {lastSync.status}
              </p>
              <p>
                Acceptées : <strong>{lastSync.accepted_count}</strong> • Doublons ignorés : {lastSync.skipped_duplicates} • Rejetées : {lastSync.rejected_count}
              </p>
              {lastSync.warnings.length > 0 && (
                <p className="text-[11px] text-amber-900">{lastSync.warnings.join(" | ")}</p>
              )}
            </div>
          )}

          <p className="text-[11px] text-slateMuted flex items-center gap-1">
            <ShieldCheck className="w-3.5 h-3.5 text-emeraldDeep" />
            Protection SSRF, respect robots.txt et masquage automatique des PII appliqués sur chaque annonce.
          </p>
        </div>
      </div>
    </div>
  );
}
