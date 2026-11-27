"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  Bell,
  Building2,
  CheckCircle2,
  FileText,
  Heart,
  Home,
  LogOut,
  Plus,
  Trash2,
  Upload,
  UserCheck,
} from "lucide-react";
import { NotariesAndRnicPanel } from "@/components/legal/NotariesAndRnicPanel";
import { TurnstileWidget } from "@/components/security/TurnstileWidget";
import type { SavedSearchAlert } from "@/components/marketplace/BuyerSearchTools";
import { loadPersistedSession, montyApi, persistSession } from "@/lib/api-client";
import type {
  ClientDocument,
  ClientFavorite,
  CountryCode,
  SellerListingSummary,
  SessionInfo,
} from "@/lib/types";

const FAVORITES_STORAGE_KEY = "monty_favorites_v1";
const ALERTS_STORAGE_KEY = "monty_saved_search_alerts_v1";
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export interface LocalFavoriteSnapshot {
  listing_id: string;
  title?: string;
  city?: string;
  price_euro?: number;
  surface_m2?: number;
  rooms?: number;
}

export function ClientPortalView() {
  const [session, setSession] = useState<SessionInfo | null>(null);
  const [authMode, setAuthMode] = useState<"login" | "register">("login");
  const [authRole, setAuthRole] = useState<"BUYER" | "SELLER">("BUYER");
  const [authCountry, setAuthCountry] = useState<CountryCode>("FR");
  const [authName, setAuthName] = useState("");
  const [authEmail, setAuthEmail] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [captchaToken, setCaptchaToken] = useState("");
  const [captchaReset, setCaptchaReset] = useState(0);

  const [activeTab, setActiveTab] = useState<"favorites" | "alerts" | "documents" | "seller" | "rnic">("favorites");
  const [favorites, setFavorites] = useState<ClientFavorite[]>([]);
  const [localFavorites, setLocalFavorites] = useState<LocalFavoriteSnapshot[]>([]);
  const [alerts, setAlerts] = useState<SavedSearchAlert[]>([]);
  const [documents, setDocuments] = useState<ClientDocument[]>([]);
  const [sellerListings, setSellerListings] = useState<SellerListingSummary[]>([]);

  // Ajout document au coffre
  const [docTitle, setDocTitle] = useState("");
  const [docCategory, setDocCategory] = useState("FINANCEMENT");
  const [docFileName, setDocFileName] = useState("");
  const [docMime, setDocMime] = useState("application/pdf");
  const [docSize, setDocSize] = useState(0);
  const [docNotes, setDocNotes] = useState("");
  const [docBusy, setDocBusy] = useState(false);
  const [portalNotice, setPortalNotice] = useState<string | null>(null);
  const [portalError, setPortalError] = useState<string | null>(null);

  const loadLocalData = useCallback(() => {
    if (typeof window === "undefined") return;
    try {
      const rawFav = window.localStorage.getItem(FAVORITES_STORAGE_KEY);
      if (rawFav) {
        const parsedFav = JSON.parse(rawFav) as LocalFavoriteSnapshot[];
        if (Array.isArray(parsedFav)) setLocalFavorites(parsedFav);
      }
      const rawAlerts = window.localStorage.getItem(ALERTS_STORAGE_KEY);
      if (rawAlerts) {
        const parsedAlerts = JSON.parse(rawAlerts) as SavedSearchAlert[];
        if (Array.isArray(parsedAlerts)) setAlerts(parsedAlerts);
      }
    } catch {
      // Ignorer stockage indisponible
    }
  }, []);

  const refreshAuthenticatedPortal = useCallback(async () => {
    try {
      const [favRes, docRes, sellRes] = await Promise.all([
        montyApi.listPortalFavorites(),
        montyApi.listPortalDocuments(),
        montyApi.listSellerListings(),
      ]);
      setFavorites(favRes.favorites);
      setDocuments(docRes.documents);
      setSellerListings(sellRes.listings);
    } catch {
      // Session expirée ou API hors ligne
    }
  }, []);

  useEffect(() => {
    loadLocalData();
    const stored = loadPersistedSession();
    if (stored) {
      setSession(stored);
      refreshAuthenticatedPortal();
    }
  }, [loadLocalData, refreshAuthenticatedPortal]);

  async function handleAuthSubmit(e: React.FormEvent) {
    e.preventDefault();
    setAuthError(null);
    const cleanEmail = authEmail.trim();
    if (!EMAIL_RE.test(cleanEmail)) {
      setAuthError("Veuillez saisir une adresse e-mail valide.");
      return;
    }
    if (authPassword.length < 8) {
      setAuthError("Le mot de passe doit contenir au moins 8 caractères.");
      return;
    }
    if (!captchaToken) {
      setAuthError("Veuillez valider le contrôle anti-robot avant de continuer.");
      return;
    }
    if (authMode === "register" && authName.trim().length < 2) {
      setAuthError("Saisissez votre nom complet (au moins 2 caractères).");
      return;
    }

    setAuthBusy(true);
    try {
      const issued =
        authMode === "register"
          ? await montyApi.registerClient({
              captcha_token: captchaToken,
              email: cleanEmail,
              password: authPassword,
              display_name: authName.trim(),
              role: authRole,
              country_code: authCountry,
            })
          : await montyApi.login({ email: cleanEmail, password: authPassword, captcha_token: captchaToken });

      persistSession(issued);
      setSession(issued);
      setPortalNotice(`Connecté en tant que ${issued.display_name || issued.email} (${issued.role}).`);
      await refreshAuthenticatedPortal();
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : "Échec de connexion au portail particulier.");
    } finally {
      setAuthBusy(false);
      setCaptchaToken("");
      setCaptchaReset((value) => value + 1);
    }
  }

  function handleLogout() {
    persistSession(null);
    setSession(null);
    setFavorites([]);
    setDocuments([]);
    setSellerListings([]);
    setPortalNotice("Vous êtes déconnecté.");
  }

  async function handleRemoveFavorite(listingId: string) {
    setPortalError(null);
    const nextLocal = localFavorites.filter((f) => f.listing_id !== listingId);
    setLocalFavorites(nextLocal);
    if (typeof window !== "undefined") {
      try {
        window.localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(nextLocal));
      } catch {
        // Ignore
      }
    }
    if (session) {
      try {
        await montyApi.removePortalFavorite(listingId);
        setFavorites((prev) => prev.filter((f) => f.listing_id !== listingId));
      } catch {
        // Ignore
      }
    }
  }

  function handleDeleteAlert(alertId: string) {
    const next = alerts.filter((a) => a.id !== alertId);
    setAlerts(next);
    if (typeof window !== "undefined") {
      try {
        window.localStorage.setItem(ALERTS_STORAGE_KEY, JSON.stringify(next));
      } catch {
        // Ignore
      }
    }
  }

  async function handleAddDocument(e: React.FormEvent) {
    e.preventDefault();
    setPortalError(null);
    setPortalNotice(null);
    if (!session) {
      setPortalError("Connectez-vous pour enregistrer un document dans votre coffre sécurisé.");
      return;
    }
    if (!docTitle.trim() || !docFileName.trim()) {
      setPortalError("Indiquez un libellé de document et un nom de fichier.");
      return;
    }
    setDocBusy(true);
    try {
      const created = await montyApi.addPortalDocument({
        title: docTitle.trim(),
        doc_category: docCategory,
        file_name: docFileName.trim(),
        mime_type: docMime || "application/pdf",
        file_size_bytes: docSize,
        notes: docNotes.trim(),
      });
      setDocuments((prev) => [created, ...prev]);
      setDocTitle("");
      setDocFileName("");
      setDocNotes("");
      setDocSize(0);
      setPortalNotice(`Pièce « ${created.title} » indexée dans votre coffre.`);
    } catch (err) {
      setPortalError(err instanceof Error ? err.message : "Erreur d'enregistrement documentaire.");
    } finally {
      setDocBusy(false);
    }
  }

  const mergedFavorites =
    favorites.length > 0
      ? favorites.map((f) => ({
          listing_id: f.listing_id,
          title: String(f.listing_snapshot?.title || f.listing_id),
          city: String(f.listing_snapshot?.city || ""),
          price_euro: Number(f.listing_snapshot?.price_euro || 0),
          surface_m2: Number(f.listing_snapshot?.surface_m2 || 0),
          rooms: Number(f.listing_snapshot?.rooms || 0),
        }))
      : localFavorites;

  return (
    <div className="space-y-6">
      <section className="rounded-2xl bg-gradient-to-r from-emeraldDeep to-emeraldMid text-ivoryWarm p-6 border border-goldAntique/40">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="space-y-1">
            <p className="text-xs font-bold uppercase tracking-widest text-goldChampagne">
              Portail Particulier &amp; Vendeur • Monty Immobilier
            </p>
            <h1 className="font-display text-2xl sm:text-3xl font-bold">
              Mon Espace Acquéreur / Vendeur
            </h1>
            <p className="text-xs sm:text-sm text-ivoryWarm/85">
              Retrouvez vos biens favoris, alertes de temps de trajet, pièces justificatives (financement / ALUR) et l&apos;accès
              à la Base Nationale des Copropriétés (gratuit vendeurs &amp; agences).
            </p>
          </div>
          {session && (
            <div className="flex items-center gap-3 rounded-xl bg-emeraldSurface border border-goldAntique/40 px-4 py-2.5 text-xs">
              <div>
                <p className="font-bold text-goldChampagne">{session.display_name || session.email}</p>
                <p className="text-ivoryWarm/80">Compte {session.role}</p>
              </div>
              <button
                type="button"
                onClick={handleLogout}
                className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-ivoryWarm/10 hover:bg-ivoryWarm/20 font-semibold"
              >
                <LogOut className="w-3.5 h-3.5" />
                Déconnexion
              </button>
            </div>
          )}
        </div>
      </section>

      {portalError && (
        <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-3 text-xs text-amber-950">
          {portalError}
        </div>
      )}
      {portalNotice && (
        <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-4 py-3 text-xs text-emerald-950 flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0" />
          <span>{portalNotice}</span>
        </div>
      )}

      {!session && (
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
              <UserCheck className="w-5 h-5 text-goldAntique" />
              Connexion ou Inscription Particulier / Vendeur
            </h2>
            <div className="inline-flex rounded-xl bg-ivoryWarm p-1 border border-borderSubtle text-xs">
              <button
                type="button"
                onClick={() => {
                  setAuthMode("login");
                  setAuthError(null);
                }}
                className={`px-3 py-1.5 rounded-lg font-bold ${
                  authMode === "login" ? "bg-emeraldDeep text-goldChampagne" : "text-slateMuted"
                }`}
              >
                Connexion
              </button>
              <button
                type="button"
                onClick={() => {
                  setAuthMode("register");
                  setAuthError(null);
                }}
                className={`px-3 py-1.5 rounded-lg font-bold ${
                  authMode === "register" ? "bg-emeraldDeep text-goldChampagne" : "text-slateMuted"
                }`}
              >
                Créer un compte
              </button>
            </div>
          </div>

          {authError && (
            <div className="rounded-xl bg-red-50 border border-red-300 px-3 py-2 text-xs text-red-900">
              {authError}
            </div>
          )}

          <form onSubmit={handleAuthSubmit} noValidate className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
            {authMode === "register" && (
              <>
                <input
                  type="text"
                  value={authName}
                  onChange={(e) => setAuthName(e.target.value)}
                  placeholder="Nom complet"
                  className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5"
                />
                <select
                  value={authRole}
                  onChange={(e) => setAuthRole(e.target.value as "BUYER" | "SELLER")}
                  className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5 font-semibold"
                >
                  <option value="BUYER">Profil Acquéreur / Particulier</option>
                  <option value="SELLER">Profil Vendeur (RNIC Gratuit)</option>
                </select>
                <select
                  value={authCountry}
                  onChange={(e) => setAuthCountry(e.target.value as CountryCode)}
                  className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5"
                >
                  <option value="FR">🇫🇷 France</option>
                  <option value="PT">🇵🇹 Portugal</option>
                </select>
              </>
            )}
            <input
              type="email"
              value={authEmail}
              onChange={(e) => setAuthEmail(e.target.value)}
              placeholder="Votre e-mail"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5"
            />
            <input
              type="password"
              value={authPassword}
              onChange={(e) => setAuthPassword(e.target.value)}
              placeholder="Mot de passe (≥ 8 caractères)"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2.5"
            />
            <div className="sm:col-span-2 lg:col-span-4">
              <TurnstileWidget onVerify={setCaptchaToken} resetKey={captchaReset} />
            </div>
            <button
              type="submit"
              disabled={authBusy || !captchaToken}
              className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-50"
            >
              {authBusy ? "Validation..." : authMode === "register" ? "Créer mon compte" : "Me connecter"}
            </button>
          </form>
        </section>
      )}

      {/* Navigation du Portail */}
      <div className="flex flex-wrap gap-2 text-xs">
        {[
          { id: "favorites", label: `Mes Favoris (${mergedFavorites.length})`, Icon: Heart },
          { id: "alerts", label: `Mes Alertes (${alerts.length})`, Icon: Bell },
          { id: "documents", label: `Coffre Documentaire (${documents.length})`, Icon: FileText },
          { id: "seller", label: `Mes Annonces Vendeur (${sellerListings.length})`, Icon: Home },
          { id: "rnic", label: "Copropriétés RNIC", Icon: Building2 },
        ].map(({ id, label, Icon }) => (
          <button
            key={id}
            type="button"
            onClick={() => setActiveTab(id as typeof activeTab)}
            className={`px-3.5 py-2.5 rounded-xl font-bold inline-flex items-center gap-1.5 transition ${
              activeTab === id
                ? "bg-emeraldDeep text-goldChampagne"
                : "bg-ivoryCard border border-borderSubtle text-obsidianInk hover:border-emeraldDeep"
            }`}
          >
            <Icon className="w-4 h-4" />
            {label}
          </button>
        ))}
      </div>

      {activeTab === "favorites" && (
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
              <Heart className="w-5 h-5 text-goldAntique" />
              Mes Biens Favoris
            </h2>
            <Link href="/annonces" className="text-xs font-bold text-emeraldDeep underline">
              Explorer les annonces →
            </Link>
          </div>
          {mergedFavorites.length === 0 ? (
            <p className="text-xs text-slateMuted">
              Aucun bien en favori pour l&apos;instant. Cliquez sur « ♥ Favori » sur n&apos;importe quelle annonce pour le
              sauvegarder ici.
            </p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 text-xs">
              {mergedFavorites.map((fav) => (
                <div key={fav.listing_id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="font-bold text-emeraldDeep text-sm">{fav.title}</p>
                      <p className="text-slateMuted">{fav.city || "France / Portugal"}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveFavorite(fav.listing_id)}
                      className="p-1.5 rounded-lg text-rose-700 hover:bg-rose-50"
                      title="Retirer des favoris"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                  {fav.price_euro ? (
                    <p className="font-bold text-obsidianInk">
                      {fav.price_euro.toLocaleString("fr-FR")} € • {fav.surface_m2 || "?"} m² • {fav.rooms || "?"} p.
                    </p>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {activeTab === "alerts" && (
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
              <Bell className="w-5 h-5 text-goldAntique" />
              Mes Alertes Temps de Trajet
            </h2>
            <Link href="/annonces" className="text-xs font-bold text-emeraldDeep underline">
              Créer une nouvelle alerte →
            </Link>
          </div>
          {alerts.length === 0 ? (
            <p className="text-xs text-slateMuted">
              Aucune alerte active enregistrée depuis cet appareil. Créez-en une depuis la page Annonces &amp; Trajet.
            </p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
              {alerts.map((al) => (
                <div key={al.id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 flex items-center justify-between gap-3">
                  <div>
                    <p className="font-bold text-emeraldDeep">
                      {al.originAddress} • ≤ {al.maxMinutes} min ({al.transportMode})
                    </p>
                    <p className="text-slateMuted">
                      Budget ≤ {al.maxPrice.toLocaleString("fr-FR")} € • {al.email} • {al.createdAt}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleDeleteAlert(al.id)}
                    className="p-1.5 rounded-lg text-rose-700 hover:bg-rose-50"
                    title="Supprimer cette alerte"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {activeTab === "documents" && (
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <h2 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
            <FileText className="w-5 h-5 text-goldAntique" />
            Mon Coffre Documentaire (Financement, Identité, Diagnostics)
          </h2>
          <form onSubmit={handleAddDocument} className="grid grid-cols-1 sm:grid-cols-4 gap-2 text-xs rounded-xl bg-ivoryWarm border border-borderSubtle p-4">
            <input
              type="text"
              value={docTitle}
              onChange={(e) => setDocTitle(e.target.value)}
              placeholder="Intitulé (ex: Accord de principe bancaire)"
              className="sm:col-span-2 rounded-lg border border-borderSubtle bg-white px-3 py-2"
            />
            <select
              value={docCategory}
              onChange={(e) => setDocCategory(e.target.value)}
              className="rounded-lg border border-borderSubtle bg-white px-3 py-2"
            >
              <option value="FINANCEMENT">Financement / Simulation</option>
              <option value="IDENTITE">Pièce d&apos;identité</option>
              <option value="DIAGNOSTICS">Diagnostics DDT</option>
              <option value="COPROPRIETE">Copropriété / Pré-État Daté</option>
              <option value="AUTRE">Autre pièce</option>
            </select>
            <label className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-emeraldDeep text-emeraldDeep font-bold cursor-pointer bg-white">
              <Upload className="w-3.5 h-3.5" />
              Choisir un fichier
              <input
                type="file"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    setDocFileName(file.name);
                    setDocMime(file.type || "application/pdf");
                    setDocSize(file.size);
                    if (!docTitle.trim()) setDocTitle(file.name.replace(/\.[^.]+$/, ""));
                  }
                }}
              />
            </label>
            <input
              type="text"
              value={docFileName}
              onChange={(e) => setDocFileName(e.target.value)}
              placeholder="Nom du fichier (ex: attestation-pret.pdf)"
              className="sm:col-span-2 rounded-lg border border-borderSubtle bg-white px-3 py-2"
            />
            <input
              type="text"
              value={docNotes}
              onChange={(e) => setDocNotes(e.target.value)}
              placeholder="Note / commentaire pour votre conseiller"
              className="rounded-lg border border-borderSubtle bg-white px-3 py-2"
            />
            <button
              type="submit"
              disabled={docBusy}
              className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-emeraldDeep text-goldChampagne font-bold disabled:opacity-50"
            >
              <Plus className="w-3.5 h-3.5" />
              {docBusy ? "Indexation..." : "Ajouter au coffre"}
            </button>
          </form>

          {documents.length === 0 ? (
            <p className="text-xs text-slateMuted">
              {session
                ? "Aucune pièce enregistrée dans votre coffre documentaire."
                : "Connectez-vous pour enregistrer et retrouver vos pièces justificatives."}
            </p>
          ) : (
            <div className="space-y-2 text-xs">
              {documents.map((doc) => (
                <div key={doc.id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3.5 flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="font-bold text-emeraldDeep">{doc.title}</p>
                    <p className="text-slateMuted">
                      {doc.doc_category} • {doc.file_name} ({Math.max(1, Math.round(doc.file_size_bytes / 1024))} Ko)
                      {doc.notes ? ` • ${doc.notes}` : ""}
                    </p>
                  </div>
                  <span className="text-[11px] text-slateMuted">{doc.created_at.slice(0, 10)}</span>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {activeTab === "seller" && (
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
              <Home className="w-5 h-5 text-goldAntique" />
              Mes Annonces Vendeur &amp; Statut de Modération
            </h2>
            <Link
              href="/publier"
              className="px-3.5 py-2 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid"
            >
              + Publier un nouveau bien
            </Link>
          </div>
          {sellerListings.length === 0 ? (
            <p className="text-xs text-slateMuted">
              {session
                ? "Aucune annonce liée à votre compte vendeur pour le moment."
                : "Connectez-vous avec votre compte Vendeur pour suivre la modération de vos annonces."}
            </p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
              {sellerListings.map((sl) => (
                <div key={sl.id} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-1.5">
                  <div className="flex items-center justify-between gap-2">
                    <p className="font-bold text-emeraldDeep text-sm">{sl.title}</p>
                    <span className="px-2 py-0.5 rounded-full bg-emeraldDeep text-goldChampagne text-[10px] font-bold">
                      {sl.moderation_status}
                    </span>
                  </div>
                  <p className="text-slateMuted">
                    {sl.city} ({sl.sector}) • {sl.price_euro.toLocaleString("fr-FR")} € • {sl.surface_m2} m² • DPE {sl.dpe}
                  </p>
                  {sl.moderation_note && <p className="text-amber-900">{sl.moderation_note}</p>}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {activeTab === "rnic" && <NotariesAndRnicPanel mode="public" initialTab="rnic" />}
    </div>
  );
}
