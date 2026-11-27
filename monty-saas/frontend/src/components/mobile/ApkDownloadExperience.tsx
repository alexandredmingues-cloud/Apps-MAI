"use client";

import React, { useEffect, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Copy,
  Download,
  HardDriveDownload,
  Layers,
  ShieldCheck,
  Smartphone,
  Sparkles,
} from "lucide-react";
import { montyApi } from "@/lib/api-client";
import type { MobileAppRelease } from "@/lib/types";

export function ApkDownloadExperience() {
  const [release, setRelease] = useState<MobileAppRelease | null>(null);
  const [history, setHistory] = useState<MobileAppRelease[]>([]);
  const [available, setAvailable] = useState<boolean>(false);
  const [statusMessage, setStatusMessage] = useState<string>("Vérification du package Android en cours...");
  const [copiedHash, setCopiedHash] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    Promise.all([montyApi.getLatestMobileRelease(), montyApi.listPublicMobileReleases()])
      .then(([latest, all]) => {
        setAvailable(latest.available);
        setRelease(latest.release);
        setStatusMessage(latest.message);
        setHistory(all.releases);
      })
      .catch(() => {
        setAvailable(false);
        setStatusMessage("Service de distribution APK temporairement inaccessible.");
      })
      .finally(() => setLoading(false));
  }, []);

  function handleCopySha256() {
    if (!release?.sha256 || typeof navigator === "undefined" || !navigator.clipboard) return;
    navigator.clipboard.writeText(release.sha256).then(() => {
      setCopiedHash(true);
      setTimeout(() => setCopiedHash(false), 2500);
    });
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-10">
      {/* Hero téléchargement mobile */}
      <section className="rounded-3xl bg-gradient-to-br from-emeraldDeep via-emeraldMid to-emeraldDeep text-ivoryWarm p-6 sm:p-10 border border-goldAntique/40 grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        <div className="lg:col-span-7 space-y-5">
          <div className="flex items-center gap-3">
            <img
              src="/icon-monty.svg"
              alt="Logo Monty Immobilier"
              className="h-14 w-14 rounded-2xl border border-goldAntique/60 shadow-lg"
            />
            <div>
              <span className="text-xs font-bold uppercase tracking-widest text-goldChampagne">
                Application Mobile Android Officielle
              </span>
              <h1 className="font-display text-3xl sm:text-4xl font-bold leading-tight">
                Télécharger l&apos;application Monty
              </h1>
            </div>
          </div>

          <p className="text-sm sm:text-base text-ivoryWarm/85 leading-relaxed">
            Emportez tout l&apos;écosystème Monty sur le terrain : recherche par temps de trajet, Monty Price Index,
            fiches copropriétés RNIC, estimation DVF, CRM agence hors-ligne et alertes temps réel.
          </p>

          {loading ? (
            <div className="rounded-2xl bg-emeraldSurface/80 border border-goldAntique/30 p-4 text-xs">
              Vérification de l&apos;intégrité SHA-256 de l&apos;APK sur le serveur...
            </div>
          ) : available && release ? (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center gap-3">
                <a
                  href={montyApi.getApkDownloadUrl(release.id)}
                  download={release.file_name}
                  className="inline-flex items-center justify-center gap-2.5 px-6 py-4 min-h-[52px] rounded-2xl bg-goldAntique text-emeraldDeep font-display font-bold text-base hover:bg-goldChampagne transition shadow-lg"
                >
                  <Download className="w-5 h-5" />
                  Télécharger l&apos;APK (v{release.version_name})
                </a>
                <span className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-emeraldSurface border border-goldAntique/40 text-xs font-semibold text-goldChampagne">
                  <ShieldCheck className="w-4 h-4" />
                  APK signé &amp; vérifié SHA-256
                </span>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                <div className="rounded-xl bg-emeraldSurface/80 border border-goldAntique/20 p-3">
                  <p className="text-ivoryWarm/70">Version actuelle</p>
                  <p className="font-bold text-goldChampagne text-sm mt-0.5">v{release.version_name}</p>
                </div>
                <div className="rounded-xl bg-emeraldSurface/80 border border-goldAntique/20 p-3">
                  <p className="text-ivoryWarm/70">Taille du fichier</p>
                  <p className="font-bold text-ivoryWarm text-sm mt-0.5">{release.file_size_mb} Mo</p>
                </div>
                <div className="rounded-xl bg-emeraldSurface/80 border border-goldAntique/20 p-3">
                  <p className="text-ivoryWarm/70">Android requis</p>
                  <p className="font-bold text-ivoryWarm text-sm mt-0.5">{release.min_android_label}</p>
                </div>
                <div className="rounded-xl bg-emeraldSurface/80 border border-goldAntique/20 p-3">
                  <p className="text-ivoryWarm/70">Dernière mise à jour</p>
                  <p className="font-bold text-ivoryWarm text-sm mt-0.5">{release.updated_at.slice(0, 10)}</p>
                </div>
              </div>

              <div className="rounded-xl bg-emeraldSurface/90 border border-goldAntique/30 p-3 text-xs flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <span className="text-goldChampagne font-bold">Empreinte SHA-256 : </span>
                  <span className="font-mono text-[11px] break-all text-ivoryWarm/90">{release.sha256}</span>
                </div>
                <button
                  type="button"
                  onClick={handleCopySha256}
                  className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne font-bold shrink-0"
                >
                  <Copy className="w-3.5 h-3.5" />
                  {copiedHash ? "Copié ✓" : "Copier"}
                </button>
              </div>
            </div>
          ) : (
            <div className="rounded-2xl bg-amber-950/50 border border-amber-300/60 p-4 text-xs text-amber-100 flex items-start gap-2.5">
              <AlertTriangle className="w-5 h-5 text-amber-300 shrink-0 mt-0.5" />
              <div>
                <p className="font-bold text-sm">APK momentanément indisponible</p>
                <p className="mt-1">{statusMessage}</p>
              </div>
            </div>
          )}
        </div>

        {/* Aperçu maquette application Android */}
        <div className="lg:col-span-5 flex justify-center">
          <div className="w-64 rounded-[2.2rem] bg-obsidianInk border-4 border-goldAntique/60 p-3.5 shadow-2xl space-y-3">
            <div className="h-1.5 w-14 mx-auto rounded-full bg-ivoryWarm/20" />
            <div className="rounded-2xl bg-ivoryWarm text-obsidianInk p-3.5 space-y-3 text-xs">
              <div className="flex items-center justify-between">
                <span className="font-display font-bold text-emeraldDeep">MONTY MOBILE</span>
                <span className="px-2 py-0.5 rounded-full bg-emeraldDeep text-goldChampagne text-[10px] font-bold">
                  Kotlin Compose
                </span>
              </div>
              <div className="rounded-xl bg-emeraldDeep text-ivoryWarm p-3 space-y-1">
                <p className="text-[10px] text-goldChampagne font-semibold">MONTY PRICE INDEX</p>
                <p className="font-bold text-sm">T3 Lyon Part-Dieu • -8,4%</p>
                <span className="inline-block px-2 py-0.5 rounded bg-teal-600 text-white text-[10px] font-bold">
                  Bonne affaire • Fiabilité élevée
                </span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-[11px]">
                <div className="rounded-lg bg-ivoryCard border border-borderSubtle p-2">
                  <p className="text-slateMuted">Visites jour</p>
                  <p className="font-bold text-emeraldDeep">4 planifiées</p>
                </div>
                <div className="rounded-lg bg-ivoryCard border border-borderSubtle p-2">
                  <p className="text-slateMuted">Copros RNIC</p>
                  <p className="font-bold text-emeraldDeep">Accès direct</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Fonctionnalités clés + Guide d'installation */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <h2 className="font-display text-xl font-bold text-emeraldDeep flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-goldAntique" />
            Fonctionnalités embarquées dans Monty Android
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            {[
              ["Estimation DVF & Délai", "Calcul immédiat en visite avec bornes de confiance et prix/m²."],
              ["CRM & Fiches Terrain", "Contacts, comptes-rendus de visite et notes privées synchronisées."],
              ["Copropriétés & Notaires", "Consultation RNIC France entière et suivi des dossiers compromis."],
              ["Alertes & Portail Client", "Suivi transparent pour particuliers acheteurs, vendeurs et conseillers."],
            ].map(([title, desc]) => (
              <div key={title} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3.5 space-y-1">
                <p className="font-bold text-emeraldDeep flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-700" />
                  {title}
                </p>
                <p className="text-slateMuted leading-relaxed">{desc}</p>
              </div>
            ))}
          </div>
          {release?.release_notes && (
            <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3.5 text-xs">
              <p className="font-bold text-emeraldDeep mb-1">Notes de version (v{release.version_name})</p>
              <p className="text-slateMuted">{release.release_notes}</p>
            </div>
          )}
        </section>

        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <h2 className="font-display text-xl font-bold text-emeraldDeep flex items-center gap-2">
            <Smartphone className="w-5 h-5 text-goldAntique" />
            Instructions d&apos;installation sur Android
          </h2>
          <ol className="space-y-3 text-xs text-obsidianInk">
            <li className="flex items-start gap-3">
              <span className="h-6 w-6 rounded-full bg-emeraldDeep text-goldChampagne font-bold flex items-center justify-center shrink-0">
                1
              </span>
              <div>
                <p className="font-bold">Téléchargez le fichier `.apk` officiel</p>
                <p className="text-slateMuted">
                  Appuyez sur « Télécharger l&apos;APK » depuis Chrome ou votre navigateur Android.
                </p>
              </div>
            </li>
            <li className="flex items-start gap-3">
              <span className="h-6 w-6 rounded-full bg-emeraldDeep text-goldChampagne font-bold flex items-center justify-center shrink-0">
                2
              </span>
              <div>
                <p className="font-bold">Autorisez l&apos;installation d&apos;applications inconnues pour votre navigateur</p>
                <p className="text-slateMuted">
                  Android vous proposera automatiquement d&apos;ouvrir Paramètres → « Autoriser cette source » (installation hors Play Store).
                </p>
              </div>
            </li>
            <li className="flex items-start gap-3">
              <span className="h-6 w-6 rounded-full bg-emeraldDeep text-goldChampagne font-bold flex items-center justify-center shrink-0">
                3
              </span>
              <div>
                <p className="font-bold">Installez puis vérifiez l&apos;empreinte SHA-256 si besoin</p>
                <p className="text-slateMuted">
                  Appuyez sur « Installer », puis connectez-vous avec votre compte Monty Particulier ou Pro.
                </p>
              </div>
            </li>
          </ol>

          {history.length > 0 && (
            <div className="pt-3 border-t border-borderSubtle space-y-2 text-xs">
              <p className="font-bold text-emeraldDeep flex items-center gap-1.5">
                <Layers className="w-3.5 h-3.5 text-goldAntique" />
                Versions publiées ({history.length})
              </p>
              <div className="space-y-1.5">
                {history.slice(0, 4).map((r) => (
                  <div
                    key={r.id}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-ivoryWarm border border-borderSubtle px-3 py-2"
                  >
                    <div>
                      <span className="font-bold text-emeraldDeep">v{r.version_name}</span>{" "}
                      <span className="text-slateMuted">• {r.file_size_mb} Mo • {r.updated_at.slice(0, 10)}</span>
                    </div>
                    <a
                      href={montyApi.getApkDownloadUrl(r.id)}
                      download={r.file_name}
                      className="inline-flex items-center gap-1 font-bold text-emeraldDeep hover:underline"
                    >
                      <HardDriveDownload className="w-3.5 h-3.5" />
                      APK
                    </a>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
