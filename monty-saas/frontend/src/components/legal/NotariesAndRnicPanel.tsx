"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  Building2,
  CheckCircle2,
  Download,
  ExternalLink,
  FileText,
  Lock,
  Mail,
  MapPin,
  Plus,
  Scale,
  Search,
  Send,
  ShieldCheck,
  Sparkles,
  Unlock,
} from "lucide-react";
import { hasInMemoryAccessToken, montyApi } from "@/lib/api-client";
import type {
  AgencyNotaryContact,
  NotaryTransmissionRecord,
  NotaryTransmissionType,
  OfficialNotaryOffice,
  RnicAccessStatus,
  RnicCoproRecord,
} from "@/lib/types";

const RNIC_PASS_STORAGE_KEY = "monty_rnic_pass_token_v1";

const DEFAULT_CHECKLIST = [
  "Titre de propriété & attestation de propriété",
  "Dossier de Diagnostics Techniques (DDT complet)",
  "Pré-État Daté & annexes Loi ALUR (Art. L.721-2 CCH)",
  "Justificatifs d'identité & état civil vendeur / acquéreur",
  "Offre d'achat contresignée & plan de financement",
];

export interface PreFilledNotaryDossier {
  dossierRef?: string;
  propertyTitle?: string;
  propertyAddress?: string;
  sellerName?: string;
  buyerName?: string;
  priceEuro?: number;
  targetDate?: string;
  transactionId?: string;
  propertyId?: string;
  preEtatHtml?: string;
}

interface NotariesAndRnicPanelProps {
  mode?: "pro" | "public";
  initialTab?: "notaries" | "rnic";
  onPrefillPreEtat?: (copro: RnicCoproRecord) => void;
  prefillDossier?: PreFilledNotaryDossier;
}

export function NotariesAndRnicPanel({
  mode = "pro",
  initialTab = "notaries",
  onPrefillPreEtat,
  prefillDossier,
}: NotariesAndRnicPanelProps) {
  const [activeTab, setActiveTab] = useState<"notaries" | "rnic">(initialTab);

  // --- État Annuaire Notaires de France ---
  const [notaryQuery, setNotaryQuery] = useState("");
  const [notaryPostal, setNotaryPostal] = useState("");
  const [notaryDept, setNotaryDept] = useState("");
  const [notarySearchBusy, setNotarySearchBusy] = useState(false);
  const [notarySearchStatus, setNotarySearchStatus] = useState<string | null>(null);
  const [notaryNotice, setNotaryNotice] = useState<string | null>(null);
  const [officialOffices, setOfficialOffices] = useState<OfficialNotaryOffice[]>([]);
  const [notaryPage, setNotaryPage] = useState(1);
  const [notaryTotalPages, setNotaryTotalPages] = useState(0);
  const [notaryTotalResults, setNotaryTotalResults] = useState(0);

  // --- Carnet Agence & Formulaire Sauvegarde Notaire ---
  const [agencyNotaries, setAgencyNotaries] = useState<AgencyNotaryContact[]>([]);
  const [bookOfficeName, setBookOfficeName] = useState("");
  const [bookNotaryName, setBookNotaryName] = useState("");
  const [bookEmail, setBookEmail] = useState("");
  const [bookPhone, setBookPhone] = useState("");
  const [bookEmailVerified, setBookEmailVerified] = useState(false);
  const [bookStreet, setBookStreet] = useState("");
  const [bookPostal, setBookPostal] = useState("");
  const [bookCity, setBookCity] = useState("");
  const [bookSiret, setBookSiret] = useState("");
  const [bookNotes, setBookNotes] = useState("");
  const [bookBusy, setBookBusy] = useState(false);

  // --- Transmission Dossier Notaire ---
  const [transmissions, setTransmissions] = useState<NotaryTransmissionRecord[]>([]);
  const [selectedNotaryId, setSelectedNotaryId] = useState<string | null>(null);
  const [dossierRef, setDossierRef] = useState(prefillDossier?.dossierRef || "COMP-2026-001");
  const [transmissionType, setTransmissionType] = useState<NotaryTransmissionType>("OUVERTURE_DOSSIER");
  const [recipientOffice, setRecipientOffice] = useState("");
  const [recipientEmail, setRecipientEmail] = useState("");
  const [recipientAddress, setRecipientAddress] = useState("");
  const [propertyTitle, setPropertyTitle] = useState(prefillDossier?.propertyTitle || "");
  const [propertyAddress, setPropertyAddress] = useState(prefillDossier?.propertyAddress || "");
  const [sellerName, setSellerName] = useState(prefillDossier?.sellerName || "");
  const [buyerName, setBuyerName] = useState(prefillDossier?.buyerName || "");
  const [agreedPriceEuro, setAgreedPriceEuro] = useState<number>(prefillDossier?.priceEuro ?? 320000);
  const [targetDate, setTargetDate] = useState(prefillDossier?.targetDate || "");
  const [checklistItems, setChecklistItems] = useState<string[]>(DEFAULT_CHECKLIST);
  const [txNotes, setTxNotes] = useState("");
  const [sendNow, setSendNow] = useState(false);
  const [txBusy, setTxBusy] = useState(false);

  const [panelError, setPanelError] = useState<string | null>(null);
  const [panelFeedback, setPanelFeedback] = useState<string | null>(null);

  // --- État Base Copropriétés RNIC ---
  const [rnicPassToken, setRnicPassToken] = useState<string>("");
  const [rnicStatus, setRnicStatus] = useState<RnicAccessStatus | null>(null);
  const [rnicPostal, setRnicPostal] = useState("69003");
  const [rnicCommune, setRnicCommune] = useState("");
  const [rnicAddress, setRnicAddress] = useState("");
  const [rnicImmat, setRnicImmat] = useState("");
  const [rnicBusy, setRnicBusy] = useState(false);
  const [rnicResponseStatus, setRnicResponseStatus] = useState<string | null>(null);
  const [rnicAccessTier, setRnicAccessTier] = useState<"FULL" | "PAID_REQUIRED">("PAID_REQUIRED");
  const [rnicUnlockPrice, setRnicUnlockPrice] = useState<number>(19);
  const [rnicRecords, setRnicRecords] = useState<RnicCoproRecord[]>([]);
  const [checkoutEmail, setCheckoutEmail] = useState("");
  const [checkoutBusy, setCheckoutBusy] = useState(false);

  useEffect(() => {
    if (!prefillDossier) return;
    if (prefillDossier.dossierRef) setDossierRef(prefillDossier.dossierRef);
    if (prefillDossier.propertyTitle) setPropertyTitle(prefillDossier.propertyTitle);
    if (prefillDossier.propertyAddress) setPropertyAddress(prefillDossier.propertyAddress);
    if (prefillDossier.sellerName) setSellerName(prefillDossier.sellerName);
    if (prefillDossier.buyerName) setBuyerName(prefillDossier.buyerName);
    if (typeof prefillDossier.priceEuro === "number" && prefillDossier.priceEuro > 0) {
      setAgreedPriceEuro(prefillDossier.priceEuro);
    }
    if (prefillDossier.targetDate) setTargetDate(prefillDossier.targetDate);
  }, [prefillDossier]);

  const loadAgencyProData = useCallback(async () => {
    if (mode !== "pro" || !hasInMemoryAccessToken()) return;
    try {
      const [bookRes, txRes] = await Promise.all([
        montyApi.listAgencyNotaries(),
        montyApi.listNotaryTransmissions(),
      ]);
      setAgencyNotaries(bookRes.notaries);
      setTransmissions(txRes.transmissions);
    } catch {
      // Rôle particulier sans accès PRO ou API hors ligne
    }
  }, [mode]);

  const refreshRnicAccess = useCallback(async (tokenOverride?: string) => {
    try {
      const st = await montyApi.getRnicAccessStatus(tokenOverride);
      setRnicStatus(st);
      setRnicUnlockPrice(st.particulier_price_euro);
    } catch {
      // Mode hors ligne
    }
  }, []);

  useEffect(() => {
    loadAgencyProData();
  }, [loadAgencyProData]);

  useEffect(() => {
    let activeToken = "";
    if (typeof window !== "undefined") {
      try {
        activeToken = window.localStorage.getItem(RNIC_PASS_STORAGE_KEY) || "";
      } catch {
        activeToken = "";
      }
      const params = new URLSearchParams(window.location.search);
      const urlTok = params.get("rnic_pass_token") || "";
      const urlSess = params.get("rnic_session_id") || "";
      if (urlTok) {
        activeToken = urlTok;
        try {
          window.localStorage.setItem(RNIC_PASS_STORAGE_KEY, urlTok);
        } catch {
          // Ignore
        }
      }
      if (urlSess) {
        montyApi
          .verifyRnicPass({ pass_token: activeToken, session_id: urlSess })
          .then((res) => {
            if (res.active && res.pass_token) {
              setRnicPassToken(res.pass_token);
              try {
                window.localStorage.setItem(RNIC_PASS_STORAGE_KEY, res.pass_token);
              } catch {
                // Ignore
              }
              setPanelFeedback("Pass RNIC Copropriétés activé pour 30 jours !");
              refreshRnicAccess(res.pass_token);
            }
          })
          .catch(() => {
            // Ignore verification failure
          });
      }
    }
    setRnicPassToken(activeToken);
    refreshRnicAccess(activeToken);
  }, [refreshRnicAccess]);

  async function runNotarySearch(pageNumber: number) {
    setPanelError(null);
    setNotarySearchBusy(true);
    try {
      const res = await montyApi.searchFrenchNotaries({
        query: notaryQuery.trim(),
        postal_code: notaryPostal.trim(),
        department_code: notaryDept.trim(),
        per_page: 25,
        page: pageNumber,
      });
      setOfficialOffices(res.offices);
      setNotarySearchStatus(res.status);
      setNotaryNotice(res.email_notice);
      setNotaryPage(res.page ?? pageNumber);
      setNotaryTotalPages(res.total_pages ?? 0);
      setNotaryTotalResults(res.total_results ?? res.count ?? 0);
    } catch (err) {
      setPanelError(err instanceof Error ? err.message : "Erreur recherche annuaire des notaires.");
    } finally {
      setNotarySearchBusy(false);
    }
  }

  async function handleSearchNotaries(e: React.FormEvent) {
    e.preventDefault();
    await runNotarySearch(1);
  }

  function handlePrefillNotaryBook(office: OfficialNotaryOffice) {
    setBookSiret(office.siret);
    setBookOfficeName(office.office_name);
    setBookStreet(office.street_address);
    setBookPostal(office.postal_code);
    setBookCity(office.city);
    setRecipientOffice(office.office_name);
    setRecipientAddress(`${office.street_address}, ${office.postal_code} ${office.city}`.trim());
    setPanelFeedback(`Étude « ${office.office_name} » pré-remplie. Complétez l'e-mail @notaires.fr vérifié si connu.`);
  }

  function handleSelectAgencyNotary(notary: AgencyNotaryContact) {
    setSelectedNotaryId(notary.id);
    setRecipientOffice(notary.office_name);
    setRecipientEmail(notary.email);
    setRecipientAddress(`${notary.street_address}, ${notary.postal_code} ${notary.city}`.replace(/^,\s*/, ""));
    setPanelFeedback(`Destinataire sélectionné : ${notary.office_name}${notary.email ? ` (${notary.email})` : ""}`);
  }

  async function handleSaveAgencyNotary(e: React.FormEvent) {
    e.preventDefault();
    setPanelError(null);
    setPanelFeedback(null);
    if (!bookOfficeName.trim()) {
      setPanelError("Le nom de l'étude notariale est obligatoire.");
      return;
    }
    setBookBusy(true);
    try {
      const saved = await montyApi.saveAgencyNotary({
        siret: bookSiret.trim(),
        office_name: bookOfficeName.trim(),
        notary_name: bookNotaryName.trim(),
        email: bookEmail.trim(),
        phone: bookPhone.trim(),
        email_verified: bookEmailVerified,
        street_address: bookStreet.trim(),
        postal_code: bookPostal.trim(),
        city: bookCity.trim(),
        source: bookSiret ? "DINUM_API_GOUV" : "MANUEL_AGENCE",
        notes: bookNotes.trim(),
      });
      setAgencyNotaries((prev) => [saved, ...prev.filter((item) => item.id !== saved.id)]);
      handleSelectAgencyNotary(saved);
      setBookOfficeName("");
      setBookNotaryName("");
      setBookEmail("");
      setBookPhone("");
      setBookEmailVerified(false);
      setBookStreet("");
      setBookPostal("");
      setBookCity("");
      setBookSiret("");
      setBookNotes("");
      setPanelFeedback(`Étude « ${saved.office_name} » enregistrée dans le carnet de l'agence.`);
    } catch (err) {
      setPanelError(err instanceof Error ? err.message : "Connectez une session Agence pour enregistrer un notaire.");
    } finally {
      setBookBusy(false);
    }
  }

  async function handleCreateTransmission(e: React.FormEvent) {
    e.preventDefault();
    setPanelError(null);
    setPanelFeedback(null);
    if (!recipientOffice.trim() || !propertyTitle.trim()) {
      setPanelError("Précisez l'étude notariale destinataire et l'intitulé du bien.");
      return;
    }
    setTxBusy(true);
    try {
      const rec = await montyApi.createNotaryTransmission({
        notary_contact_id: selectedNotaryId,
        transaction_id: prefillDossier?.transactionId ?? null,
        property_id: prefillDossier?.propertyId ?? null,
        dossier_ref: dossierRef.trim() || "COMP-2026-001",
        transmission_type: transmissionType,
        recipient_email: recipientEmail.trim(),
        recipient_office: recipientOffice.trim(),
        recipient_address: recipientAddress.trim(),
        property_title: propertyTitle.trim(),
        property_address: propertyAddress.trim(),
        seller_name: sellerName.trim(),
        buyer_name: buyerName.trim(),
        agreed_price_euro: Math.max(0, Math.round(agreedPriceEuro || 0)),
        target_date: targetDate.trim(),
        checklist_items: checklistItems,
        notes: txNotes.trim(),
        pre_etat_date_html: prefillDossier?.preEtatHtml ?? "",
        send_now: sendNow,
      });
      setTransmissions((prev) => [rec, ...prev]);
      setPanelFeedback(
        rec.delivery_status === "SENT"
          ? `Dossier ${rec.dossier_ref} envoyé par e-mail à ${rec.recipient_email} !`
          : `Courrier .eml ${rec.dossier_ref} généré (${rec.delivery_status}). Téléchargez-le pour envoi immédiat.`
      );
    } catch (err) {
      setPanelError(err instanceof Error ? err.message : "Erreur lors de la création de la transmission notaire.");
    } finally {
      setTxBusy(false);
    }
  }

  async function handleDownloadEml(transmissionId: string) {
    setPanelError(null);
    try {
      const { filename, content } = await montyApi.downloadNotaryTransmissionEml(transmissionId);
      const blob = new Blob([content], { type: "message/rfc822;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setPanelError(err instanceof Error ? err.message : "Téléchargement .eml impossible.");
    }
  }

  async function handleSearchRnic(e: React.FormEvent) {
    e.preventDefault();
    setPanelError(null);
    setRnicBusy(true);
    try {
      const res = await montyApi.searchRnicCoproprietes({
        postal_code: rnicPostal.trim(),
        commune: rnicCommune.trim(),
        address_query: rnicAddress.trim(),
        immatriculation: rnicImmat.trim(),
        page_size: 12,
        rnic_pass_token: rnicPassToken || undefined,
      });
      setRnicRecords(res.records);
      setRnicResponseStatus(res.status);
      setRnicAccessTier(res.access_tier);
      setRnicUnlockPrice(res.unlock_price_euro);
      if (res.message) setPanelError(res.message);
    } catch (err) {
      setPanelError(err instanceof Error ? err.message : "Erreur interrogation base RNIC Copropriétés.");
    } finally {
      setRnicBusy(false);
    }
  }

  async function handleStartRnicCheckout(e: React.FormEvent) {
    e.preventDefault();
    setPanelError(null);
    const clean = checkoutEmail.trim();
    if (!clean.includes("@") || !clean.includes(".")) {
      setPanelError("Saisissez une adresse e-mail valide pour recevoir votre Pass Copropriétés.");
      return;
    }
    setCheckoutBusy(true);
    try {
      const origin = typeof window !== "undefined" ? window.location.origin : "https://monty-site.onrender.com";
      const path = typeof window !== "undefined" ? window.location.pathname : "/coproprietes";
      const res = await montyApi.createRnicCheckoutPass({
        buyer_email: clean,
        success_url: `${origin}${path}`,
        cancel_url: `${origin}${path}`,
      });
      if (res.pass_token && typeof window !== "undefined") {
        window.localStorage.setItem(RNIC_PASS_STORAGE_KEY, res.pass_token);
      }
      if (res.checkout_url && typeof window !== "undefined") {
        window.location.href = res.checkout_url;
      }
    } catch (err) {
      setPanelError(err instanceof Error ? err.message : "Paiement Stripe RNIC indisponible.");
    } finally {
      setCheckoutBusy(false);
    }
  }

  return (
    <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-5">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-borderSubtle pb-4">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-widest text-goldAntique">
            Données Officielles France • Open Data DINUM &amp; ANAH RNIC
          </p>
          <h3 className="font-display text-xl font-bold text-emeraldDeep">
            Notaires de France &amp; Base Nationale des Copropriétés
          </h3>
        </div>
        <div className="inline-flex rounded-xl bg-ivoryWarm p-1 border border-borderSubtle text-xs">
          <button
            type="button"
            onClick={() => setActiveTab("notaries")}
            className={`px-3.5 py-2 rounded-lg font-bold inline-flex items-center gap-1.5 transition ${
              activeTab === "notaries" ? "bg-emeraldDeep text-goldChampagne" : "text-slateMuted hover:text-obsidianInk"
            }`}
          >
            <Scale className="w-3.5 h-3.5" />
            Notaires de France &amp; Compromis
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("rnic")}
            className={`px-3.5 py-2 rounded-lg font-bold inline-flex items-center gap-1.5 transition ${
              activeTab === "rnic" ? "bg-emeraldDeep text-goldChampagne" : "text-slateMuted hover:text-obsidianInk"
            }`}
          >
            <Building2 className="w-3.5 h-3.5" />
            Copropriétés France (RNIC)
          </button>
        </div>
      </div>

      {panelError && (
        <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-2.5 text-xs text-amber-950">
          {panelError}
        </div>
      )}
      {panelFeedback && (
        <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-4 py-2.5 text-xs text-emerald-950 flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0" />
          <span>{panelFeedback}</span>
        </div>
      )}

      {activeTab === "notaries" ? (
        <div className="space-y-6">
          {/* 1. Recherche Live DINUM 69.10Z */}
          <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-3">
            <h4 className="font-display font-bold text-sm text-emeraldDeep flex items-center gap-1.5">
              <Search className="w-4 h-4 text-goldAntique" />
              Répertoire national des études notariales (DINUM / INSEE + fiches officielles CSN)
            </h4>
            <form onSubmit={handleSearchNotaries} className="grid grid-cols-1 sm:grid-cols-4 gap-2 text-xs">
              <input
                type="text"
                value={notaryQuery}
                onChange={(e) => setNotaryQuery(e.target.value)}
                placeholder="Nom d’étude ou ville (ex. Montpellier)"
                className="sm:col-span-2 rounded-xl border border-borderSubtle bg-white px-3 py-2"
              />
              <input
                type="text"
                value={notaryPostal}
                onChange={(e) => setNotaryPostal(e.target.value)}
                placeholder="Code postal (ex: 69006)"
                className="rounded-xl border border-borderSubtle bg-white px-3 py-2"
              />
              <div className="flex gap-2">
                <input
                  type="text"
                  value={notaryDept}
                  onChange={(e) => setNotaryDept(e.target.value)}
                  placeholder="Dép (69)"
                  className="w-20 rounded-xl border border-borderSubtle bg-white px-2.5 py-2"
                />
                <button
                  type="submit"
                  disabled={notarySearchBusy}
                  className="flex-1 px-3 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-50"
                >
                  {notarySearchBusy ? "Recherche..." : "Chercher"}
                </button>
              </div>
            </form>

            {notaryNotice && (
              <p className="text-[11px] text-slateMuted italic">
                Source : {notarySearchStatus} — {notaryNotice}
              </p>
            )}
            <p className="text-[11px] text-slateMuted">
              Le répertoire national permet de retrouver les études actives et leurs adresses. Pour les téléphones et e-mails, ouvrez la fiche CSN de chaque étude : ces coordonnées ne sont pas présentes dans l’API SIRENE. MON-AGENT ne complète jamais ces champs par déduction.
            </p>

            {officialOffices.length > 0 && (
              <>
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-[11px] text-slateMuted">
                <span>{notaryTotalResults.toLocaleString("fr-FR")} établissements trouvés · page {notaryPage}{notaryTotalPages ? ` / ${notaryTotalPages}` : ""}</span>
                <span>25 résultats maximum par page · recherche nationale</span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5 max-h-72 overflow-y-auto pr-1">
                {officialOffices.map((o) => (
                  <div
                    key={`${o.siret}-${o.office_name}`}
                    className="rounded-xl bg-white border border-borderSubtle p-3 text-xs space-y-1.5"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <p className="font-bold text-emeraldDeep">{o.office_name}</p>
                      <span className="px-2 py-0.5 rounded bg-emeraldDeep/10 text-emeraldDeep text-[10px] font-mono shrink-0">
                        SIRET {o.siret || "N/A"}
                      </span>
                    </div>
                    <p className="text-slateMuted flex items-center gap-1">
                      <MapPin className="w-3 h-3 shrink-0" />
                      {o.street_address} {o.postal_code} {o.city}
                    </p>
                    <div className="flex flex-wrap items-center gap-2 pt-1">
                      <a
                        href={o.csn_directory_url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border border-borderSubtle text-emeraldDeep font-semibold hover:border-emeraldDeep"
                      >
                        <ExternalLink className="w-3 h-3" />
                        Annuaire CSN notaires.fr
                      </a>
                      {mode === "pro" && (
                        <button
                          type="button"
                          onClick={() => handlePrefillNotaryBook(o)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emeraldDeep text-goldChampagne font-bold"
                        >
                          <Plus className="w-3 h-3" />
                          Utiliser / Carnet
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
              {notaryTotalPages > 1 && (
                <div className="mt-3 flex items-center justify-between gap-3">
                  <button
                    type="button"
                    disabled={notarySearchBusy || notaryPage <= 1}
                    onClick={() => runNotarySearch(notaryPage - 1)}
                    className="rounded-lg border border-borderSubtle px-3 py-1.5 text-xs font-semibold text-emeraldDeep disabled:opacity-40"
                  >
                    Page précédente
                  </button>
                  <span className="text-xs text-slateMuted">Page {notaryPage} / {notaryTotalPages}</span>
                  <button
                    type="button"
                    disabled={notarySearchBusy || notaryPage >= notaryTotalPages}
                    onClick={() => runNotarySearch(notaryPage + 1)}
                    className="rounded-lg border border-borderSubtle px-3 py-1.5 text-xs font-semibold text-emeraldDeep disabled:opacity-40"
                  >
                    Page suivante
                  </button>
                </div>
              )}
              </>
            )}
          </div>

          {mode === "pro" && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
              {/* 2. Carnet des Notaires de l'Agence */}
              <div className="lg:col-span-5 space-y-4">
                <form onSubmit={handleSaveAgencyNotary} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2.5 text-xs">
                  <p className="font-bold text-emeraldDeep flex items-center gap-1.5">
                    <Mail className="w-3.5 h-3.5 text-goldAntique" />
                    Ajouter au Carnet Notaires Agence
                  </p>
                  <input
                    type="text"
                    value={bookOfficeName}
                    onChange={(e) => setBookOfficeName(e.target.value)}
                    placeholder="Nom de l'étude notariale *"
                    className="w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                  />
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      type="text"
                      value={bookNotaryName}
                      onChange={(e) => setBookNotaryName(e.target.value)}
                      placeholder="Maître (Nom notaire)"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="email"
                      value={bookEmail}
                      onChange={(e) => setBookEmail(e.target.value)}
                      placeholder="Email (@notaires.fr)"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={bookPhone}
                      onChange={(e) => setBookPhone(e.target.value)}
                      placeholder="Téléphone étude"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={bookSiret}
                      onChange={(e) => setBookSiret(e.target.value)}
                      placeholder="SIRET"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                  </div>
                  <label className="flex items-start gap-2 text-[11px] text-slateMuted">
                    <input
                      type="checkbox"
                      checked={bookEmailVerified}
                      onChange={(e) => setBookEmailVerified(e.target.checked)}
                      className="mt-0.5"
                    />
                    <span>J’ai vérifié cette adresse e-mail sur la fiche officielle de l’office notarial (CSN).</span>
                  </label>
                  <div className="grid grid-cols-3 gap-2">
                    <input
                      type="text"
                      value={bookStreet}
                      onChange={(e) => setBookStreet(e.target.value)}
                      placeholder="Adresse étude"
                      className="col-span-3 rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={bookPostal}
                      onChange={(e) => setBookPostal(e.target.value)}
                      placeholder="CP"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={bookCity}
                      onChange={(e) => setBookCity(e.target.value)}
                      placeholder="Ville"
                      className="col-span-2 rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={bookBusy}
                    className="w-full py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold disabled:opacity-50"
                  >
                    {bookBusy ? "Enregistrement..." : "Enregistrer l'étude dans le carnet"}
                  </button>
                </form>

                <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2 text-xs">
                  <p className="font-bold text-emeraldDeep">Carnet Notaires de l&apos;Agence ({agencyNotaries.length})</p>
                  {agencyNotaries.length === 0 ? (
                    <p className="text-slateMuted italic">
                      Aucune étude enregistrée. Recherchez ci-dessus puis cliquez sur « Utiliser / Carnet ».
                    </p>
                  ) : (
                    <div className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
                      {agencyNotaries.map((n) => (
                        <button
                          key={n.id}
                          type="button"
                          onClick={() => handleSelectAgencyNotary(n)}
                          className={`w-full text-left p-2.5 rounded-lg border transition ${
                            selectedNotaryId === n.id
                              ? "bg-emeraldDeep text-ivoryWarm border-emeraldDeep"
                              : "bg-white border-borderSubtle hover:border-emeraldDeep"
                          }`}
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span className="font-bold truncate">{n.office_name}</span>
                            {n.is_official_notaire_domain && (
                              <span className="px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-900 text-[10px] font-bold">
                                @notaires.fr
                              </span>
                            )}
                          </div>
                          <p className={`text-[11px] ${selectedNotaryId === n.id ? "text-ivoryWarm/80" : "text-slateMuted"}`}>
                            {n.email || "Email à compléter"} • {n.postal_code} {n.city}
                          </p>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* 3. Formulaire Transmission Dossier Compromis / Ouverture Dossier */}
              <div className="lg:col-span-7 space-y-4">
                <form onSubmit={handleCreateTransmission} className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-3 text-xs">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <h4 className="font-display font-bold text-sm text-emeraldDeep flex items-center gap-1.5">
                      <FileText className="w-4 h-4 text-goldAntique" />
                      Transmission Dossier Notaire (Compromis / Ouverture Dossier)
                    </h4>
                    <select
                      value={transmissionType}
                      onChange={(e) => setTransmissionType(e.target.value as NotaryTransmissionType)}
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5 font-semibold"
                    >
                      <option value="OUVERTURE_DOSSIER">Ouverture de dossier</option>
                      <option value="SIGNATURE_COMPROMIS">Signature de compromis</option>
                      <option value="COMPLEMENT_ALUR">Complément ALUR / Pré-État Daté</option>
                    </select>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <input
                      type="text"
                      value={recipientOffice}
                      onChange={(e) => setRecipientOffice(e.target.value)}
                      placeholder="Étude notariale destinataire *"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="email"
                      value={recipientEmail}
                      onChange={(e) => setRecipientEmail(e.target.value)}
                      placeholder="E-mail notaire (@notaires.fr)"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={recipientAddress}
                      onChange={(e) => setRecipientAddress(e.target.value)}
                      placeholder="Adresse postale complète de l'étude"
                      className="sm:col-span-2 rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={dossierRef}
                      onChange={(e) => setDossierRef(e.target.value)}
                      placeholder="Référence dossier (ex: COMP-2026-042)"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={propertyTitle}
                      onChange={(e) => setPropertyTitle(e.target.value)}
                      placeholder="Intitulé du bien *"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={propertyAddress}
                      onChange={(e) => setPropertyAddress(e.target.value)}
                      placeholder="Adresse du bien"
                      className="sm:col-span-2 rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={sellerName}
                      onChange={(e) => setSellerName(e.target.value)}
                      placeholder="Vendeur(s)"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="text"
                      value={buyerName}
                      onChange={(e) => setBuyerName(e.target.value)}
                      placeholder="Acquéreur(s)"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="number"
                      value={agreedPriceEuro}
                      onChange={(e) => setAgreedPriceEuro(Number(e.target.value) || 0)}
                      placeholder="Prix convenu (€)"
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                    <input
                      type="date"
                      value={targetDate}
                      onChange={(e) => setTargetDate(e.target.value)}
                      className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <p className="font-semibold text-slateMuted">Pièces jointes / listées au bordereau :</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                      {DEFAULT_CHECKLIST.map((item) => {
                        const checked = checklistItems.includes(item);
                        return (
                          <label key={item} className="inline-flex items-center gap-1.5 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={(e) =>
                                setChecklistItems((prev) =>
                                  e.target.checked ? [...prev, item] : prev.filter((x) => x !== item)
                                )
                              }
                            />
                            <span>{item}</span>
                          </label>
                        );
                      })}
                    </div>
                  </div>

                  <textarea
                    rows={2}
                    value={txNotes}
                    onChange={(e) => setTxNotes(e.target.value)}
                    placeholder="Conditions suspensives, disponibilité parties, précision syndic..."
                    className="w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                  />

                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <label className="inline-flex items-center gap-1.5 cursor-pointer text-slateMuted">
                      <input
                        type="checkbox"
                        checked={sendNow}
                        onChange={(e) => setSendNow(e.target.checked)}
                      />
                      <span>Envoyer immédiatement par SMTP / Resend (si configuré sur le serveur)</span>
                    </label>
                    <button
                      type="submit"
                      disabled={txBusy}
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-50"
                    >
                      <Send className="w-3.5 h-3.5" />
                      {txBusy ? "Génération..." : "Générer & Transmettre le dossier"}
                    </button>
                  </div>
                </form>

                {/* Historique des transmissions */}
                <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2 text-xs">
                  <p className="font-bold text-emeraldDeep">Historique des Transmissions Notaires ({transmissions.length})</p>
                  {transmissions.length === 0 ? (
                    <p className="text-slateMuted italic">Aucune transmission créée pour cette agence.</p>
                  ) : (
                    <div className="space-y-1.5 max-h-52 overflow-y-auto pr-1">
                      {transmissions.map((tx) => (
                        <div
                          key={tx.id}
                          className="flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-white border border-borderSubtle"
                        >
                          <div>
                            <p className="font-bold text-emeraldDeep">
                              {tx.dossier_ref} • {tx.recipient_office}
                            </p>
                            <p className="text-[11px] text-slateMuted">
                              {tx.transmission_type} • {tx.recipient_email || "Sans e-mail"} • Statut :{" "}
                              <strong>{tx.delivery_status}</strong>
                            </p>
                          </div>
                          <button
                            type="button"
                            onClick={() => handleDownloadEml(tx.id)}
                            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-emeraldDeep text-emeraldDeep font-bold hover:bg-emeraldDeep hover:text-goldChampagne transition"
                          >
                            <Download className="w-3.5 h-3.5" />
                            Télécharger .eml
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      ) : (
        /* TAB 2 : BASE NATIONALE DES COPROPRIÉTÉS DE FRANCE (RNIC ANAH) */
        <div className="space-y-5">
          {/* Bandeau tarification différenciée Agence/Vendeur vs Particulier */}
          <div
            className={`rounded-xl p-4 border flex flex-col md:flex-row md:items-center justify-between gap-3 text-xs ${
              rnicStatus?.has_full_access || rnicAccessTier === "FULL"
                ? "bg-emerald-50 border-emerald-300 text-emerald-950"
                : "bg-amber-50 border-amber-300 text-amber-950"
            }`}
          >
            <div className="space-y-1">
              <p className="font-bold text-sm flex items-center gap-1.5">
                {rnicStatus?.has_full_access ? (
                  <Unlock className="w-4 h-4 text-emerald-700" />
                ) : (
                  <Lock className="w-4 h-4 text-amber-700" />
                )}
                {rnicStatus?.free_tier_eligible
                  ? `Accès Intégral Gratuit activé (${rnicStatus.role} — Agences & Vendeurs)`
                  : rnicStatus?.has_paid_pass
                  ? "Pass RNIC Particulier Actif (Accès complet 30 jours)"
                  : `Mode Aperçu Particulier — Déblocage complet ${rnicUnlockPrice} € (30 jours)`}
              </p>
              <p className="opacity-85">
                {rnicStatus?.pricing_policy ||
                  "Gratuit pour les agences immobilières et les vendeurs vérifiés ; payant pour les particuliers / acquéreurs."}
              </p>
            </div>

            {!rnicStatus?.has_full_access && (
              <form onSubmit={handleStartRnicCheckout} className="flex flex-wrap items-center gap-2 shrink-0">
                <input
                  type="email"
                  value={checkoutEmail}
                  onChange={(e) => setCheckoutEmail(e.target.value)}
                  placeholder="Votre e-mail acquéreur"
                  className="rounded-xl border border-amber-300 bg-white px-3 py-2 text-xs"
                />
                <button
                  type="submit"
                  disabled={checkoutBusy}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-50"
                >
                  <ShieldCheck className="w-3.5 h-3.5" />
                  {checkoutBusy ? "Redirection..." : `Débloquer RNIC (${rnicUnlockPrice} €)`}
                </button>
              </form>
            )}
          </div>

          <form onSubmit={handleSearchRnic} className="grid grid-cols-1 sm:grid-cols-5 gap-2 text-xs">
            <input
              type="text"
              value={rnicPostal}
              onChange={(e) => setRnicPostal(e.target.value)}
              placeholder="Code postal (ex: 69003)"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <input
              type="text"
              value={rnicCommune}
              onChange={(e) => setRnicCommune(e.target.value)}
              placeholder="Commune (ex: LYON)"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <input
              type="text"
              value={rnicAddress}
              onChange={(e) => setRnicAddress(e.target.value)}
              placeholder="Adresse / rue"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <input
              type="text"
              value={rnicImmat}
              onChange={(e) => setRnicImmat(e.target.value)}
              placeholder="N° Immatriculation (AA0-000-000)"
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
            <button
              type="submit"
              disabled={rnicBusy}
              className="px-4 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid disabled:opacity-50"
            >
              {rnicBusy ? "Interrogation ANAH..." : "Rechercher RNIC"}
            </button>
          </form>

          {rnicResponseStatus && (
            <p className="text-[11px] text-slateMuted">
              Source : RNIC ANAH / data.gouv.fr ({rnicResponseStatus}) — {rnicRecords.length} résultat(s).
            </p>
          )}

          {rnicRecords.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {rnicRecords.map((rec, idx) => (
                <div
                  key={`${rec.numero_immatriculation}-${idx}`}
                  className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 text-xs space-y-2"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="font-bold text-emeraldDeep text-sm">{rec.nom_usage}</p>
                      <p className="text-slateMuted">
                        {rec.adresse_reference} • {rec.code_postal} {rec.commune}
                      </p>
                    </div>
                    <span
                      className={`px-2 py-0.5 rounded-full font-bold text-[10px] shrink-0 ${
                        rec.locked ? "bg-amber-100 text-amber-900" : "bg-emeraldDeep text-goldChampagne"
                      }`}
                    >
                      {rec.locked ? "APERÇU" : rec.numero_immatriculation || "RNIC"}
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-1.5 text-[11px] text-obsidianInk">
                    <div>
                      Lots :{" "}
                      <strong>
                        {rec.locked
                          ? rec.lot_count_band || "Masqué"
                          : `${rec.nombre_total_lots ?? "?"} (Hab: ${rec.nombre_lots_habitation ?? "?"})`}
                      </strong>
                    </div>
                    <div>
                      Période : <strong>{rec.periode_construction}</strong>
                    </div>
                    <div>
                      Syndic : <strong>{rec.syndic_en_exercice}</strong>
                    </div>
                    <div>
                      Difficulté :{" "}
                      <strong>
                        {rec.locked
                          ? "Masqué"
                          : rec.copro_en_difficulte
                          ? "Procédure signalée"
                          : "Aucune signalée"}
                      </strong>
                    </div>
                  </div>

                  {!rec.locked && onPrefillPreEtat && (
                    <button
                      type="button"
                      onClick={() => onPrefillPreEtat(rec)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid"
                    >
                      <Sparkles className="w-3.5 h-3.5" />
                      Pré-remplir le Pré-État Daté
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
}
