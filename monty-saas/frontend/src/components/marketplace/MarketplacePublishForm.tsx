"use client";

import React, { useEffect, useState } from "react";
import {
  Building2,
  Calculator,
  Camera,
  CheckCircle2,
  Clock,
  FileCheck2,
  Home,
  ShieldCheck,
  Sparkles,
  Trash2,
  UserCheck,
} from "lucide-react";
import type { CountryCode, PublisherType, SaleTimelineEstimate } from "@/lib/types";

export interface SelectedPhotoPreview {
  id: string;
  name: string;
  url: string;
}

export interface PublishFormState {
  pubType: PublisherType;
  pubLabel: string;
  pubKind: string;
  pubCity: string;
  pubSector: string;
  pubPrice: number;
  pubSurface: number;
  pubRooms: number;
  pubDpe: string;
  pubCondition: string;
  pubHighlightsRaw: string;
  pubPhotos: number;
  pubTitle: string;
  pubDescription: string;
  wantProAccompaniment: boolean;
}

interface MarketplacePublishFormProps {
  countryCode: CountryCode;
  state: PublishFormState;
  onChange: (patch: Partial<PublishFormState>) => void;
  previewTimeline: SaleTimelineEstimate;
  copyTip: string | null;
  publishBusy: boolean;
  onGenerateCopy: () => void;
  onSubmit: (e: React.FormEvent) => void;
}

export function MarketplacePublishForm({
  countryCode,
  state,
  onChange,
  previewTimeline,
  copyTip,
  publishBusy,
  onGenerateCopy,
  onSubmit,
}: MarketplacePublishFormProps) {
  const [photos, setPhotos] = useState<SelectedPhotoPreview[]>([]);
  const [diagChecklist, setDiagChecklist] = useState({
    dpe: true,
    carrez: true,
    technical: false,
    copro: false,
  });

  useEffect(() => {
    return () => {
      photos.forEach((p) => URL.revokeObjectURL(p.url));
    };
  }, [photos]);

  function handleSelectPhotos(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []).slice(0, 12);
    if (files.length === 0) return;
    const added: SelectedPhotoPreview[] = files.map((file, idx) => ({
      id: `ph-${Date.now()}-${idx}`,
      name: file.name,
      url: URL.createObjectURL(file),
    }));
    const next = [...photos, ...added].slice(0, 12);
    setPhotos(next);
    onChange({ pubPhotos: Math.max(next.length, state.pubPhotos) });
    e.target.value = "";
  }

  function handleRemovePhoto(id: string) {
    const target = photos.find((p) => p.id === id);
    if (target) URL.revokeObjectURL(target.url);
    const next = photos.filter((p) => p.id !== id);
    setPhotos(next);
    onChange({ pubPhotos: next.length });
  }

  const completedChecklistCount = Object.values(diagChecklist).filter(Boolean).length;
  const readinessPct = Math.round((completedChecklistCount / 4) * 100);
  const pricePerM2 = state.pubSurface > 0 ? Math.round(state.pubPrice / state.pubSurface) : 0;
  const previewTitle =
    state.pubTitle.trim() ||
    `${state.pubKind} ${state.pubRooms} pièces • ${state.pubSurface} m² — ${state.pubCity}`;
  const previewDesc =
    state.pubDescription.trim() ||
    `Bien situé à ${state.pubCity} (${state.pubSector}), ${state.pubSurface} m², ${state.pubRooms} pièces, ${state.pubCondition}.`;
  const previewHighlights = state.pubHighlightsRaw
    .split(",")
    .map((h) => h.trim())
    .filter(Boolean)
    .slice(0, 3);

  return (
    <form onSubmit={onSubmit} className="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <div className="lg:col-span-7 rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-display text-xl font-bold text-emeraldDeep flex items-center gap-2">
            <Home className="w-5 h-5 text-goldAntique" />
            Caractéristiques du bien ({countryCode === "FR" ? "France" : "Portugal"})
          </h3>
          <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-emeraldDeep/10 text-emeraldDeep">
            Dossier technique : {readinessPct} %
          </span>
        </div>

        {/* Choix explicite du mode de publication */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <button
            type="button"
            onClick={() => onChange({ wantProAccompaniment: false })}
            className={`text-left rounded-xl border p-3.5 transition ${
              !state.wantProAccompaniment
                ? "bg-emeraldDeep text-ivoryWarm border-emeraldDeep"
                : "bg-ivoryWarm text-obsidianInk border-borderSubtle hover:border-emeraldMid"
            }`}
          >
            <p className="font-bold flex items-center gap-1.5">
              <UserCheck className="w-4 h-4 text-goldChampagne" />
              Publication publique gratuite
            </p>
            <p className={`mt-1 ${!state.wantProAccompaniment ? "text-ivoryWarm/80" : "text-slateMuted"}`}>
              Diffusion directe entre particuliers / agences après vérification anti-spam.
            </p>
          </button>

          <button
            type="button"
            onClick={() => onChange({ wantProAccompaniment: true })}
            className={`text-left rounded-xl border p-3.5 transition ${
              state.wantProAccompaniment
                ? "bg-emeraldDeep text-ivoryWarm border-emeraldDeep"
                : "bg-ivoryWarm text-obsidianInk border-borderSubtle hover:border-emeraldMid"
            }`}
          >
            <p className="font-bold flex items-center gap-1.5">
              <Building2 className="w-4 h-4 text-goldChampagne" />
              Accompagnement Conseiller Monty Pro
            </p>
            <p className={`mt-1 ${state.wantProAccompaniment ? "text-ivoryWarm/80" : "text-slateMuted"}`}>
              Publication + estimation DVF affinée + mise en relation avec acquéreurs financés.
            </p>
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
          <label className="block">
            <span className="text-xs font-bold text-slateMuted">Statut du vendeur</span>
            <select
              value={state.pubType}
              onChange={(e) => onChange({ pubType: e.target.value as PublisherType })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="PARTICULIER">Particulier vendeur</option>
              <option value="AGENCE">Agence partenaire</option>
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">Nom public affiché (optionnel)</span>
            <input
              type="text"
              value={state.pubLabel}
              onChange={(e) => onChange({ pubLabel: e.target.value })}
              placeholder={state.pubType === "PARTICULIER" ? "Particulier vérifié" : "Agence Monty Partenaire"}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">Type de bien</span>
            <select
              value={state.pubKind}
              onChange={(e) => onChange({ pubKind: e.target.value })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="Appartement">Appartement</option>
              <option value="Maison">Maison</option>
              <option value="Studio">Studio</option>
              <option value="Loft">Loft</option>
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">Ville</span>
            <input
              type="text"
              required
              value={state.pubCity}
              onChange={(e) => onChange({ pubCity: e.target.value })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">Quartier / Transports</span>
            <input
              type="text"
              value={state.pubSector}
              onChange={(e) => onChange({ pubSector: e.target.value })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">Prix demandé (€)</span>
            <input
              type="number"
              min={10000}
              step={1000}
              value={state.pubPrice}
              onChange={(e) => onChange({ pubPrice: Number(e.target.value) || 0 })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">Surface (m²)</span>
            <input
              type="number"
              min={9}
              value={state.pubSurface}
              onChange={(e) => onChange({ pubSurface: Number(e.target.value) || 0 })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">Pièces</span>
            <input
              type="number"
              min={1}
              max={25}
              value={state.pubRooms}
              onChange={(e) => onChange({ pubRooms: Number(e.target.value) || 1 })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">
              {countryCode === "PT" ? "Certificado Energético (ADENE)" : "Classe DPE"}
            </span>
            <select
              value={state.pubDpe}
              onChange={(e) => onChange({ pubDpe: e.target.value })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              {["A", "B", "C", "D", "E", "F", "G"].map((d) => (
                <option key={d} value={d}>
                  Classe {d}
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-bold text-slateMuted">État général</span>
            <select
              value={state.pubCondition}
              onChange={(e) => onChange({ pubCondition: e.target.value })}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              <option value="Récent / Neuf">Récent / Neuf</option>
              <option value="Rénové / Excellent état">Rénové / Excellent état</option>
              <option value="Bon état">Bon état</option>
              <option value="À rafraîchir">À rafraîchir</option>
              <option value="À rénover">À rénover</option>
            </select>
          </label>
        </div>

        {/* Sélection réelle de photos + miniatures */}
        <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Camera className="w-4 h-4 text-emeraldDeep" />
              <span className="text-xs font-bold text-emeraldDeep">
                Photos du bien ({photos.length > 0 ? `${photos.length} sélectionnée(s)` : `${state.pubPhotos} déclarée(s)`})
              </span>
            </div>
            <label className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne text-xs font-bold cursor-pointer hover:bg-emeraldMid">
              <Camera className="w-3.5 h-3.5" />
              Ajouter des photos
              <input
                type="file"
                accept="image/*"
                multiple
                onChange={handleSelectPhotos}
                className="sr-only"
              />
            </label>
          </div>
          {photos.length > 0 ? (
            <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
              {photos.map((photo) => (
                <div key={photo.id} className="relative rounded-lg overflow-hidden border border-borderSubtle aspect-square bg-white">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={photo.url} alt={photo.name} className="w-full h-full object-cover" />
                  <button
                    type="button"
                    onClick={() => handleRemovePhoto(photo.id)}
                    aria-label={`Retirer ${photo.name}`}
                    className="absolute top-1 right-1 p-1 rounded-full bg-black/65 text-white hover:bg-rose-700"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-[11px] text-slateMuted">
              Ajoutez vos photos (salon, cuisine, vue extérieure) pour enrichir la prévisualisation et réduire le délai estimé.
            </p>
          )}
        </div>

        {/* Checklist Diagnostics & DPE */}
        <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2 text-xs">
          <p className="font-bold text-emeraldDeep flex items-center gap-1.5">
            <FileCheck2 className="w-4 h-4 text-goldAntique" />
            Checklist diagnostics &amp; dossier vendeur ({completedChecklistCount}/4 prêts)
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-obsidianInk">
            {[
              { key: "dpe" as const, label: countryCode === "PT" ? "Certificado Energético ADENE" : "DPE / Audit énergétique à jour" },
              { key: "carrez" as const, label: "Attestation surface (Loi Carrez)" },
              { key: "technical" as const, label: "Diagnostics techniques (Élec, Gaz, Plomb)" },
              { key: "copro" as const, label: "Pièces copropriété (PV AG, charges)" },
            ].map((item) => (
              <label key={item.key} className="inline-flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={diagChecklist[item.key]}
                  onChange={(e) => setDiagChecklist((prev) => ({ ...prev, [item.key]: e.target.checked }))}
                  className="rounded border-borderSubtle"
                />
                <span>{item.label}</span>
              </label>
            ))}
          </div>
        </div>

        <label className="block text-sm">
          <span className="text-xs font-bold text-slateMuted">
            Points forts (séparés par des virgules)
          </span>
          <input
            type="text"
            value={state.pubHighlightsRaw}
            onChange={(e) => onChange({ pubHighlightsRaw: e.target.value })}
            className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
          />
        </label>

        <div className="pt-1 flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={onGenerateCopy}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-goldAntique text-emeraldDeep font-bold text-xs hover:bg-goldChampagne transition"
          >
            <Sparkles className="w-4 h-4" />
            Générer titre &amp; texte d&apos;annonce (sans PII)
          </button>
          {copyTip && <span className="text-xs text-emeraldDeep font-medium">{copyTip}</span>}
        </div>

        <label className="block text-sm">
          <span className="text-xs font-bold text-slateMuted">Titre de l&apos;annonce</span>
          <input
            type="text"
            value={state.pubTitle}
            onChange={(e) => onChange({ pubTitle: e.target.value })}
            placeholder="Cliquez sur « Générer titre & texte » ou rédigez votre titre..."
            className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
          />
        </label>

        <label className="block text-sm">
          <span className="text-xs font-bold text-slateMuted">Description complète</span>
          <textarea
            rows={4}
            value={state.pubDescription}
            onChange={(e) => onChange({ pubDescription: e.target.value })}
            placeholder="Description détaillée du bien (e-mails/téléphones automatiquement masqués pour votre sécurité RGPD)..."
            className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
          />
        </label>

        <button
          type="submit"
          disabled={publishBusy}
          className="w-full sm:w-auto px-6 py-3 rounded-xl bg-emeraldDeep text-goldChampagne font-bold text-sm hover:bg-emeraldMid transition disabled:opacity-50"
        >
          {publishBusy ? "Publication en cours..." : "Publier mon annonce sur Monty"}
        </button>
      </div>

      {/* Panneau latéral : Aperçu en direct + Analyse Délai + Sécurité RGPD */}
      <aside className="lg:col-span-5 space-y-4">
        <div className="rounded-2xl bg-ivoryCard border border-goldAntique/50 p-5 space-y-3 shadow-sm">
          <div className="flex items-center justify-between text-xs">
            <span className="font-bold uppercase tracking-wider text-emeraldDeep">
              Aperçu en direct de votre annonce
            </span>
            <span className="px-2 py-0.5 rounded-full bg-emeraldDeep/10 text-emeraldDeep font-semibold">
              {state.wantProAccompaniment ? "Suivi Monty Pro" : "Publication autonome"}
            </span>
          </div>

          {photos.length > 0 && (
            <div className="rounded-xl overflow-hidden border border-borderSubtle h-40 bg-ivoryWarm">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={photos[0].url} alt={previewTitle} className="w-full h-full object-cover" />
            </div>
          )}

          <div>
            <p className="text-xs font-semibold text-slateMuted">
              {state.pubCity} • {state.pubSector}
            </p>
            <h4 className="font-display text-lg font-bold text-obsidianInk mt-0.5">{previewTitle}</h4>
          </div>

          <div className="flex items-baseline justify-between rounded-xl bg-ivoryWarm border border-borderSubtle px-3.5 py-2.5">
            <div>
              <p className="text-xl font-display font-bold text-emeraldDeep">
                {state.pubPrice.toLocaleString("fr-FR")} €
              </p>
              <p className="text-[11px] text-slateMuted">
                {pricePerM2 > 0 ? `${pricePerM2.toLocaleString("fr-FR")} €/m²` : "—"} • {state.pubSurface} m² •{" "}
                {state.pubRooms} p.
              </p>
            </div>
            <span className="px-2 py-1 rounded-lg bg-emeraldDeep text-goldChampagne text-xs font-bold">
              {countryCode === "PT" ? "CE" : "DPE"} {state.pubDpe}
            </span>
          </div>

          <p className="text-xs text-slateMuted leading-relaxed line-clamp-3">{previewDesc}</p>

          {previewHighlights.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {previewHighlights.map((hl) => (
                <span key={hl} className="text-[11px] px-2 py-0.5 rounded-md bg-ivoryWarm border border-borderSubtle">
                  {hl}
                </span>
              ))}
            </div>
          )}

          <div className="pt-2 border-t border-borderSubtle flex items-center justify-between text-xs text-emeraldDeep font-semibold">
            <span className="inline-flex items-center gap-1">
              <Clock className="w-3.5 h-3.5 text-goldAntique" />
              {previewTimeline.status === "READY"
                ? `Délai estimé : ~${previewTimeline.likely_days} j`
                : "Délai en attente"}
            </span>
            <span className="inline-flex items-center gap-1 text-slateMuted">
              <CheckCircle2 className="w-3.5 h-3.5 text-emeraldDeep" />
              Dossier {readinessPct} %
            </span>
          </div>
        </div>

        <div className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-5 border border-goldAntique/40 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-goldChampagne">
              Analyse avant mise en ligne
            </span>
            <Calculator className="w-5 h-5 text-goldChampagne" />
          </div>
          <div className="rounded-xl bg-emeraldSurface border border-goldAntique/30 p-3.5 space-y-1">
            <p className="text-xs text-ivoryWarm/80">Délai de vente indicatif estimé</p>
            {previewTimeline.status === "READY" ? (
              <>
                <p className="text-lg font-bold text-ivoryWarm">
                  ~{previewTimeline.likely_days} jours{" "}
                  <span className="text-xs font-normal text-goldChampagne">
                    ({previewTimeline.low_days} à {previewTimeline.high_days} j)
                  </span>
                </p>
                <p className="text-[11px] text-ivoryWarm/70">{previewTimeline.confidence_label}</p>
              </>
            ) : (
              <p className="text-xs text-amber-200">{previewTimeline.confidence_label}</p>
            )}
          </div>
        </div>

        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-2 text-xs text-slateMuted">
          <p className="font-bold text-emeraldDeep flex items-center gap-1.5 text-sm">
            <ShieldCheck className="w-4 h-4 text-goldAntique" />
            Protection Anti-Démarchage &amp; RGPD
          </p>
          <p className="leading-relaxed">
            Les numéros de téléphone et adresses e-mail saisis par erreur dans les champs publics sont automatiquement
            neutralisés avant publication afin d&apos;éviter le spam et la collecte sauvage observée sur les portails
            généralistes.
          </p>
        </div>
      </aside>
    </form>
  );
}
