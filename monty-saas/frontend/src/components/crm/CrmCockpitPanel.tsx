"use client";

import React, { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  BellRing,
  CheckCircle2,
  Download,
  FileSpreadsheet,
  Globe,
  Lock,
  MessageSquarePlus,
  Plus,
  Search,
  ShieldAlert,
  Trash2,
  UserPlus,
  Users,
} from "lucide-react";
import { hasInMemoryAccessToken, montyApi } from "@/lib/api-client";
import type {
  AdvisorPrivateNote,
  ContactExchange,
  ContactRole,
  CountryCode,
  CrmContact,
  CsvImportSummary,
  ExchangeChannel,
  FollowUpTask,
  LeadKeywordRule,
  LeadStatus,
  MontyRole,
  PublicInboundLead,
} from "@/lib/types";

interface CrmCockpitPanelProps {
  agencyId: string;
  role: MontyRole;
  initialRoleFilter?: ContactRole | "ALL";
  initialTab?: "contacts" | "followups" | "web_leads" | "csv_rgpd";
}

const SEED_LOCAL_CONTACTS: CrmContact[] = [
  {
    id: "cnt-fr-1",
    agency_id: "agency-france-reseau",
    assigned_user_id: "usr-director",
    country_code: "FR",
    full_name: "Camille Laurent",
    email: "camille.laurent@exemple.fr",
    phone: "06 42 18 90 11",
    role: "BUYER",
    city: "Lyon",
    budget_euro: 420000,
    legal_basis: "MESURES_PRECONTRACTUELLES",
    consent_granted: true,
    retention_until: "2029-11-25",
    created_at: "2026-10-25",
    last_contact_at: "2026-11-20",
    has_missing_docs: false,
    pending_offer_days: 0,
    last_visit_days_ago: 4,
    is_anonymized: false,
  },
  {
    id: "cnt-fr-2",
    agency_id: "agency-france-reseau",
    assigned_user_id: "usr-agent",
    country_code: "FR",
    full_name: "Thomas Mercier",
    email: "thomas.mercier@exemple.fr",
    phone: "06 77 31 04 55",
    role: "SELLER",
    city: "Bordeaux",
    budget_euro: 540000,
    legal_basis: "EXECUTION_MANDAT",
    consent_granted: true,
    retention_until: "2031-11-25",
    created_at: "2026-10-10",
    last_contact_at: "2026-11-15",
    has_missing_docs: true,
    pending_offer_days: 8,
    last_visit_days_ago: null,
    is_anonymized: false,
  },
  {
    id: "cnt-pt-1",
    agency_id: "agency-france-reseau",
    assigned_user_id: "usr-director",
    country_code: "PT",
    full_name: "Inês Ferreira",
    email: "ines.ferreira@exemplo.pt",
    phone: "+351 912 345 678",
    role: "BUYER",
    city: "Lisboa",
    budget_euro: 360000,
    legal_basis: "CONSENTEMENT_EXPLICITE",
    consent_granted: true,
    retention_until: "2029-11-25",
    created_at: "2026-11-04",
    last_contact_at: "2026-11-22",
    has_missing_docs: false,
    pending_offer_days: 0,
    last_visit_days_ago: null,
    is_anonymized: false,
  },
];

function sanitizeSpreadsheetCell(value: string | number): string {
  const s = String(value);
  if (/^[=+\-@]/.test(s)) return `'${s}`;
  return s;
}

export function CrmCockpitPanel({
  agencyId,
  role,
  initialRoleFilter = "ALL",
  initialTab = "contacts",
}: CrmCockpitPanelProps) {
  const [tab, setTab] = useState<"contacts" | "followups" | "web_leads" | "csv_rgpd">(initialTab);
  const [contacts, setContacts] = useState<CrmContact[]>(SEED_LOCAL_CONTACTS);
  const [webLeads, setWebLeads] = useState<PublicInboundLead[]>([]);
  const [leadKeywords, setLeadKeywords] = useState<LeadKeywordRule[]>([]);
  const [kwCategory, setKwCategory] = useState("VENDEURS");
  const [kwText, setKwText] = useState("");
  const [kwGeo, setKwGeo] = useState("France entière");
  const [selectedContactId, setSelectedContactId] = useState<string>(SEED_LOCAL_CONTACTS[0].id);
  const [roleFilter, setRoleFilter] = useState<ContactRole | "ALL">(initialRoleFilter);
  const [countryFilter, setCountryFilter] = useState<"ALL" | CountryCode>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [modeLabel, setModeLabel] = useState<"api" | "local">("local");

  // Notes privées & échanges
  const [privateNotesByContact, setPrivateNotesByContact] = useState<Record<string, AdvisorPrivateNote[]>>({});
  const [exchangesByContact, setExchangesByContact] = useState<Record<string, ContactExchange[]>>({});
  const [newNoteText, setNewNoteText] = useState("");
  const [newExchangeSummary, setNewExchangeSummary] = useState("");
  const [newExchangeChannel, setNewExchangeChannel] = useState<ExchangeChannel>("CALL");

  // Nouveau contact
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newName, setNewName] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [newPhone, setNewPhone] = useState("");
  const [newCity, setNewCity] = useState("Lyon");
  const [newBudget, setNewBudget] = useState(350000);
  const [newRole, setNewRole] = useState<ContactRole>("BUYER");
  const [newCountry, setNewCountry] = useState<CountryCode>("FR");

  // Relances automatiques & états UX
  const [tasks, setTasks] = useState<FollowUpTask[]>([]);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [apiWarning, setApiWarning] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // Import CSV & Portabilité RGPD
  const [csvInput, setCsvInput] = useState(
    "nom;email;telephone;ville;budget;role;pays\nClaire Moreau;claire.moreau@exemple.fr;06 11 22 33 44;Nantes;390000;BUYER;FR\n=CMD|'calc'!A0;test.secu@exemple.fr;06 99 88 77 66;Paris;610000;SELLER;FR"
  );
  const [importSummary, setImportSummary] = useState<CsvImportSummary | null>(null);
  const [rgpdJsonPreview, setRgpdJsonPreview] = useState<string | null>(null);
  const [csvExportPreview, setCsvExportPreview] = useState<string | null>(null);

  useEffect(() => {
    setRoleFilter(initialRoleFilter);
  }, [initialRoleFilter]);

  useEffect(() => {
    setTab(initialTab);
  }, [initialTab]);

  function computeLocalFollowups(list: CrmContact[]): FollowUpTask[] {
    const out: FollowUpTask[] = [];
    const todayIso = new Date().toISOString().slice(0, 10);
    for (const c of list) {
      if (c.is_anonymized) continue;
      if (c.last_visit_days_ago !== null && c.last_visit_days_ago >= 3) {
        out.push({
          id: `tsk-visit-${c.id}`,
          agency_id: c.agency_id,
          contact_id: c.id,
          contact_name: c.full_name,
          assigned_user_id: c.assigned_user_id,
          title: `Débriefing visite J+${c.last_visit_days_ago} — ${c.full_name}`,
          reason: "Visite réalisée sans retour enregistré depuis ≥ 3 jours.",
          priority: "HIGH",
          due_date: todayIso,
          is_auto_generated: true,
          completed: false,
        });
      }
      if (c.pending_offer_days >= 7) {
        out.push({
          id: `tsk-offer-${c.id}`,
          agency_id: c.agency_id,
          contact_id: c.id,
          contact_name: c.full_name,
          assigned_user_id: c.assigned_user_id,
          title: `Relance offre / décision J+${c.pending_offer_days} — ${c.full_name}`,
          reason: "Proposition ou négociation ouverte depuis ≥ 7 jours.",
          priority: "HIGH",
          due_date: todayIso,
          is_auto_generated: true,
          completed: false,
        });
      }
      if (c.has_missing_docs) {
        out.push({
          id: `tsk-docs-${c.id}`,
          agency_id: c.agency_id,
          contact_id: c.id,
          contact_name: c.full_name,
          assigned_user_id: c.assigned_user_id,
          title: `Pièces réglementaires à compléter — ${c.full_name}`,
          reason: "Diagnostics, DPE/SCE ou pièces pré-contractuelles manquantes.",
          priority: "MEDIUM",
          due_date: todayIso,
          is_auto_generated: true,
          completed: false,
        });
      }
    }
    return out;
  }

  async function refreshCrmData() {
    setIsLoading(true);
    try {
      const [cResp, tResp, lResp, kwResp] = await Promise.all([
        montyApi.listCrmContacts(),
        montyApi.getAutoFollowups(),
        montyApi.listPublicLeads(),
        montyApi.listLeadKeywords().catch(() => ({ count: 0, keywords: [] as LeadKeywordRule[] })),
      ]);
      setContacts(cResp.contacts);
      setTasks(tResp.tasks);
      setWebLeads(lResp.leads);
      setLeadKeywords(kwResp.keywords);
      setModeLabel("api");
      setApiWarning(null);
    } catch {
      setTasks((prev) => (prev.length > 0 ? prev : computeLocalFollowups(contacts)));
      setModeLabel("local");
      setApiWarning("Session JWT non connectée ou API indisponible : affichage en mode local temporaire.");
    } finally {
      setIsLoading(false);
    }
  }

  async function handleCreateLeadKeyword(e: React.FormEvent) {
    e.preventDefault();
    if (kwText.trim().length < 2) return;
    try {
      const created = await montyApi.createLeadKeyword({
        category: kwCategory,
        keyword: kwText.trim(),
        geo_scope: kwGeo.trim() || "France entière",
      });
      setLeadKeywords((prev) => [created, ...prev]);
      setKwText("");
    } catch (err) {
      setApiWarning(err instanceof Error ? err.message : "Impossible d'ajouter ce mot-clé Monty Leads.");
    }
  }

  async function handleDeleteLeadKeyword(id: string) {
    try {
      await montyApi.deleteLeadKeyword(id);
      setLeadKeywords((prev) => prev.filter((k) => k.id !== id));
    } catch (err) {
      setApiWarning(err instanceof Error ? err.message : "Suppression mot-clé refusée.");
    }
  }

  async function handleUpdateLeadStatus(leadId: string, nextStatus: LeadStatus) {
    if (!hasInMemoryAccessToken()) {
      setApiWarning("Session Pro requise pour qualifier une demande entrante en base.");
      return;
    }
    const previous = webLeads.find((l) => l.id === leadId)?.status;
    setWebLeads((prev) => prev.map((l) => (l.id === leadId ? { ...l, status: nextStatus } : l)));
    try {
      const updated = await montyApi.updatePublicLeadStatus(leadId, nextStatus);
      setWebLeads((prev) => prev.map((l) => (l.id === leadId ? updated : l)));
      setStatusMessage(`Statut de la demande mis à jour : ${nextStatus}.`);
    } catch (err) {
      if (previous) {
        setWebLeads((prev) => prev.map((l) => (l.id === leadId ? { ...l, status: previous } : l)));
      }
      setApiWarning(err instanceof Error ? err.message : "Échec de mise à jour du statut de la demande.");
    }
  }

  useEffect(() => {
    refreshCrmData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agencyId, role]);

  const filteredContacts = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return contacts.filter((c) => {
      if (roleFilter !== "ALL" && c.role !== roleFilter) return false;
      if (countryFilter !== "ALL" && c.country_code !== countryFilter) return false;
      if (q && !`${c.full_name} ${c.email} ${c.city}`.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [contacts, roleFilter, countryFilter, searchQuery]);

  const selectedContact = useMemo(
    () => contacts.find((c) => c.id === selectedContactId) ?? filteredContacts[0] ?? null,
    [contacts, selectedContactId, filteredContacts]
  );

  async function handleSelectContact(contactId: string) {
    setSelectedContactId(contactId);
    setRgpdJsonPreview(null);
    if (modeLabel === "api") {
      try {
        const res = await montyApi.getPrivateNotes(contactId);
        setPrivateNotesByContact((prev) => ({ ...prev, [contactId]: res.notes }));
      } catch {
        // RBAC peut bloquer BUYER/SELLER
      }
    }
  }

  async function handleCreateContact(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    if (newName.trim().length < 2) {
      setFormError("Le nom complet doit contenir au moins 2 caractères.");
      return;
    }
    if (newEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(newEmail.trim())) {
      setFormError("L'adresse e-mail saisie est invalide.");
      return;
    }
    if (newBudget < 0) {
      setFormError("Le budget doit être positif ou nul.");
      return;
    }
    if (!hasInMemoryAccessToken()) {
      setFormError("Session Agence requise pour créer et persister un contact CRM dans PostgreSQL RLS.");
      return;
    }
    try {
      const created = await montyApi.createCrmContact({
        full_name: newName,
        email: newEmail,
        phone: newPhone,
        role: newRole,
        country_code: newCountry,
        city: newCity,
        budget_euro: newBudget,
      });
      setContacts((prev) => [created, ...prev]);
      setSelectedContactId(created.id);
      setStatusMessage(`Contact « ${created.full_name} » enregistré en base via API FastAPI.`);
      setNewName("");
      setNewEmail("");
      setNewPhone("");
      setShowCreateForm(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Échec d'enregistrement du contact CRM.");
    }
  }

  async function handleAddPrivateNote(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedContact || !newNoteText.trim()) return;
    if (role === "BUYER" || role === "SELLER") {
      setStatusMessage("Accès refusé (RBAC) : les notes privées sont réservées aux conseillers.");
      return;
    }
    if (!hasInMemoryAccessToken()) {
      setApiWarning("Session Agence requise pour enregistrer une note privée cloisonnée.");
      return;
    }
    try {
      const note = await montyApi.addPrivateNote(selectedContact.id, newNoteText);
      setPrivateNotesByContact((prev) => ({
        ...prev,
        [selectedContact.id]: [...(prev[selectedContact.id] ?? []), note],
      }));
      setNewNoteText("");
      setStatusMessage("Note privée ajoutée (strictement isolée de l'export client RGPD).");
    } catch (err) {
      setApiWarning(err instanceof Error ? err.message : "Impossible d'ajouter la note privée.");
    }
  }

  async function handleAddExchange(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedContact || !newExchangeSummary.trim()) return;
    if (!hasInMemoryAccessToken()) {
      setApiWarning("Session Agence requise pour tracer un échange client en base.");
      return;
    }
    try {
      const ex = await montyApi.addExchange(selectedContact.id, {
        channel: newExchangeChannel,
        summary: newExchangeSummary,
      });
      setExchangesByContact((prev) => ({
        ...prev,
        [selectedContact.id]: [...(prev[selectedContact.id] ?? []), ex],
      }));
      setNewExchangeSummary("");
      setStatusMessage("Échange historisé sur la fiche contact.");
    } catch (err) {
      setApiWarning(err instanceof Error ? err.message : "Impossible d'enregistrer cet échange.");
    }
  }

  async function handleCompleteTask(taskId: string) {
    if (!hasInMemoryAccessToken()) {
      setApiWarning("Session Agence requise pour clôturer une relance CRM.");
      return;
    }
    try {
      await montyApi.completeFollowup(taskId);
      setTasks((prev) => prev.map((t) => (t.id === taskId ? { ...t, completed: true } : t)));
    } catch (err) {
      setApiWarning(err instanceof Error ? err.message : "Impossible de clôturer la relance.");
    }
  }

  async function handleImportCsv() {
    if (!hasInMemoryAccessToken()) {
      setApiWarning("Session Agence requise pour exécuter l'import CSV dédupliqué en base.");
      return;
    }
    try {
      const summary = await montyApi.importCsv({ csv_text: csvInput, default_country: "FR" });
      setImportSummary(summary);
      await refreshCrmData();
    } catch (err) {
      setApiWarning(err instanceof Error ? err.message : "Échec de l'import CSV.");
    }
  }

  async function handleExportSafeCsv() {
    try {
      const txt = await montyApi.exportCsvText();
      setCsvExportPreview(txt);
    } catch {
      const rows = [
        "id;country_code;full_name;email;phone;role;city;budget_euro;legal_basis;retention_until",
        ...contacts.map((c) =>
          [
            sanitizeSpreadsheetCell(c.id),
            sanitizeSpreadsheetCell(c.country_code),
            sanitizeSpreadsheetCell(c.full_name),
            sanitizeSpreadsheetCell(c.email),
            sanitizeSpreadsheetCell(c.phone),
            sanitizeSpreadsheetCell(c.role),
            sanitizeSpreadsheetCell(c.city),
            c.budget_euro,
            sanitizeSpreadsheetCell(c.legal_basis),
            sanitizeSpreadsheetCell(c.retention_until),
          ].join(";")
        ),
      ];
      setCsvExportPreview(`\ufeff${rows.join("\n")}`);
    }
  }

  async function handleRgpdExport(contact: CrmContact) {
    try {
      const dump = await montyApi.rgpdPortabilityExport(contact.id);
      setRgpdJsonPreview(JSON.stringify(dump, null, 2));
    } catch {
      const dump = {
        rgpd_article: "Article 15 & Article 20 RGPD — Droit d'accès et portabilité",
        agency_id: contact.agency_id,
        exported_at: new Date().toISOString(),
        subject: {
          id: contact.id,
          country_code: contact.country_code,
          full_name: contact.full_name,
          email: contact.email,
          phone: contact.phone,
          role: contact.role,
          city: contact.city,
          budget_euro: contact.budget_euro,
          legal_basis: contact.legal_basis,
          retention_until: contact.retention_until,
        },
        exchanges: exchangesByContact[contact.id] ?? [],
        security_guarantee: "Notes privées internes exclues de cet export conformément au cloisonnement Monty.",
      };
      setRgpdJsonPreview(JSON.stringify(dump, null, 2));
    }
  }

  async function handlePurgeExpired() {
    if (!hasInMemoryAccessToken()) {
      setApiWarning("Session Directeur/Admin requise pour exécuter la purge RGPD sur PostgreSQL.");
      return;
    }
    try {
      const res = await montyApi.rgpdPurgeExpired();
      setStatusMessage(`Purge RGPD exécutée en base : ${res.anonymized_count} contact(s) expiré(s) anonymisé(s).`);
      await refreshCrmData();
    } catch (err) {
      setApiWarning(err instanceof Error ? err.message : "Échec de la purge RGPD.");
    }
  }

  return (
    <div className="space-y-6">
      {/* En-tête CRM Phase 2 */}
      <section className="rounded-2xl bg-gradient-to-r from-emeraldDeep to-emeraldMid text-ivoryWarm p-6 border border-goldAntique/40">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-goldChampagne">
              Phase 2 • CRM Multi-Agences, Relances Auto &amp; RGPD ({modeLabel === "api" ? "Données synchronisées" : "Démo locale · non persistée"})
            </p>
            <h2 className="mt-1 font-display text-2xl font-bold">
              Contacts, Notes Privées Isolées &amp; Automatisations Commerciales
            </h2>
          </div>
          <div className="flex flex-wrap gap-2">
            {[
              { id: "contacts", label: "Contacts & Notes Privées", Icon: Users },
              { id: "followups", label: `Relances Auto (${tasks.filter((t) => !t.completed).length})`, Icon: BellRing },
              { id: "web_leads", label: `Demandes Web (${webLeads.filter((l) => l.status === "NEW").length})`, Icon: Globe },
              { id: "csv_rgpd", label: "Import CSV & RGPD", Icon: FileSpreadsheet },
            ].map(({ id, label, Icon }) => (
              <button
                key={id}
                type="button"
                onClick={() => setTab(id as typeof tab)}
                className={`px-3.5 py-2 rounded-xl text-xs font-bold inline-flex items-center gap-1.5 transition ${
                  tab === id
                    ? "bg-goldAntique text-emeraldDeep"
                    : "bg-emeraldSurface text-ivoryWarm border border-goldAntique/30 hover:bg-emeraldMid"
                }`}
              >
                <Icon className="w-4 h-4" />
                {label}
              </button>
            ))}
          </div>
        </div>
      </section>

      {isLoading && (
        <div className="rounded-xl bg-ivoryCard border border-borderSubtle px-4 py-2.5 text-xs text-slateMuted">
          Synchronisation CRM avec PostgreSQL en cours...
        </div>
      )}

      {apiWarning && (
        <div className="rounded-xl bg-amber-50 border border-amber-300 px-4 py-2.5 text-xs text-amber-900 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-amber-700 shrink-0" />
          <span>{apiWarning}</span>
        </div>
      )}

      {statusMessage && (
        <div className="rounded-xl bg-emerald-50 border border-emerald-300 px-4 py-3 text-xs text-emerald-950 flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0" />
          <span>{statusMessage}</span>
        </div>
      )}

      {tab === "contacts" && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Liste des contacts */}
          <div className="lg:col-span-5 rounded-2xl bg-ivoryCard border border-borderSubtle p-4 space-y-3">
            <div className="flex items-center justify-between gap-2">
              <div className="relative flex-1">
                <Search className="w-4 h-4 text-slateMuted absolute left-3 top-2.5" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Rechercher nom, ville, e-mail..."
                  className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm pl-9 pr-3 py-2 text-xs"
                />
              </div>
              <button
                type="button"
                onClick={() => setShowCreateForm((v) => !v)}
                className="px-3 py-2 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold inline-flex items-center gap-1 shrink-0"
              >
                <UserPlus className="w-3.5 h-3.5" />
                Nouveau
              </button>
            </div>

            <div className="flex flex-wrap gap-1.5 text-[11px]">
              {(["ALL", "BUYER", "SELLER", "LEAD"] as const).map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setRoleFilter(r)}
                  className={`px-2.5 py-1 rounded-lg font-semibold border ${
                    roleFilter === r
                      ? "bg-emeraldDeep text-ivoryWarm border-emeraldDeep"
                      : "bg-ivoryWarm text-obsidianInk border-borderSubtle"
                  }`}
                >
                  {r === "ALL" ? "Tous" : r === "BUYER" ? "Acquéreurs" : r === "SELLER" ? "Vendeurs" : "Leads"}
                </button>
              ))}
              {(["ALL", "FR", "PT"] as const).map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setCountryFilter(c)}
                  className={`px-2.5 py-1 rounded-lg font-semibold border ${
                    countryFilter === c
                      ? "bg-goldAntique text-emeraldDeep border-goldAntique"
                      : "bg-ivoryWarm text-slateMuted border-borderSubtle"
                  }`}
                >
                  {c === "ALL" ? "FR + PT" : c}
                </button>
              ))}
            </div>

            {showCreateForm && (
              <form onSubmit={handleCreateContact} noValidate className="rounded-xl bg-ivoryWarm border border-emeraldMid/40 p-3 space-y-2 text-xs">
                <p className="font-bold text-emeraldDeep">Nouveau contact multi-pays (RGPD natif)</p>
                {formError && (
                  <p className="rounded-lg bg-red-50 border border-red-200 px-2.5 py-1.5 text-red-900">
                    {formError}
                  </p>
                )}
                <input
                  type="text"
                  required
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="Nom complet"
                  className="w-full rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                />
                <div className="grid grid-cols-2 gap-2">
                  <input
                    type="email"
                    value={newEmail}
                    onChange={(e) => setNewEmail(e.target.value)}
                    placeholder="Email"
                    className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                  />
                  <input
                    type="text"
                    value={newPhone}
                    onChange={(e) => setNewPhone(e.target.value)}
                    placeholder="Téléphone"
                    className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                  />
                  <select
                    value={newRole}
                    onChange={(e) => setNewRole(e.target.value as ContactRole)}
                    className="rounded-lg border border-borderSubtle bg-white px-2 py-1.5"
                  >
                    <option value="BUYER">Acquéreur</option>
                    <option value="SELLER">Vendeur</option>
                    <option value="LEAD">Lead</option>
                  </select>
                  <select
                    value={newCountry}
                    onChange={(e) => setNewCountry(e.target.value as CountryCode)}
                    className="rounded-lg border border-borderSubtle bg-white px-2 py-1.5"
                  >
                    <option value="FR">🇫🇷 France</option>
                    <option value="PT">🇵🇹 Portugal</option>
                  </select>
                  <input
                    type="text"
                    value={newCity}
                    onChange={(e) => setNewCity(e.target.value)}
                    placeholder="Ville"
                    className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                  />
                  <input
                    type="number"
                    value={newBudget}
                    onChange={(e) => setNewBudget(Number(e.target.value) || 0)}
                    placeholder="Budget €"
                    className="rounded-lg border border-borderSubtle bg-white px-2.5 py-1.5"
                  />
                </div>
                <button type="submit" className="w-full py-2 rounded-lg bg-emeraldDeep text-goldChampagne font-bold">
                  Enregistrer le contact
                </button>
              </form>
            )}

            <div className="space-y-2 max-h-[460px] overflow-y-auto pr-1">
              {filteredContacts.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => handleSelectContact(c.id)}
                  className={`w-full text-left rounded-xl p-3 border transition ${
                    selectedContact?.id === c.id
                      ? "bg-emeraldDeep text-ivoryWarm border-emeraldDeep"
                      : "bg-ivoryWarm text-obsidianInk border-borderSubtle hover:border-emeraldMid"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="font-bold text-sm truncate">
                      {c.country_code === "PT" ? "🇵🇹" : "🇫🇷"} {c.full_name}
                    </p>
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                        selectedContact?.id === c.id
                          ? "bg-goldAntique text-emeraldDeep"
                          : "bg-emeraldDeep/10 text-emeraldDeep"
                      }`}
                    >
                      {c.role}
                    </span>
                  </div>
                  <p className={`text-xs mt-1 ${selectedContact?.id === c.id ? "text-ivoryWarm/80" : "text-slateMuted"}`}>
                    {c.city} • {c.budget_euro.toLocaleString("fr-FR")} €
                  </p>
                </button>
              ))}
            </div>
          </div>

          {/* Fiche contact détaillée : Échanges + Notes Privées Isolées + RGPD */}
          <div className="lg:col-span-7 space-y-4">
            {selectedContact ? (
              <>
                <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h3 className="font-display text-xl font-bold text-emeraldDeep">
                        {selectedContact.full_name}
                      </h3>
                      <p className="text-xs text-slateMuted">
                        {selectedContact.email || "Email anonymisé"} • {selectedContact.phone || "Tél. anonymisé"} •{" "}
                        {selectedContact.city} ({selectedContact.country_code})
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRgpdExport(selectedContact)}
                      className="px-3 py-1.5 rounded-lg border border-emeraldDeep text-emeraldDeep text-xs font-bold inline-flex items-center gap-1.5 hover:bg-emeraldDeep hover:text-ivoryWarm transition"
                    >
                      <Download className="w-3.5 h-3.5" />
                      Export Portabilité RGPD
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2 text-[11px]">
                    <span className="px-2.5 py-1 rounded-lg bg-ivoryWarm border border-borderSubtle">
                      Base légale : <strong>{selectedContact.legal_basis}</strong>
                    </span>
                    <span className="px-2.5 py-1 rounded-lg bg-ivoryWarm border border-borderSubtle">
                      Conservation max : <strong>{selectedContact.retention_until}</strong>
                    </span>
                  </div>
                </div>

                {/* Notes privées conseiller (strictement cloisonnées) */}
                <div className="rounded-2xl bg-amber-50/70 border border-amber-300 p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="font-display font-bold text-sm text-amber-950 flex items-center gap-1.5">
                      <Lock className="w-4 h-4 text-amber-700" />
                      Notes Privées Conseiller (Table isolée • Exclue des exports RGPD)
                    </h4>
                  </div>
                  <form onSubmit={handleAddPrivateNote} className="flex gap-2">
                    <input
                      type="text"
                      value={newNoteText}
                      onChange={(e) => setNewNoteText(e.target.value)}
                      placeholder="Note confidentielle interne (invisible des acquéreurs/vendeurs et autres conseillers)..."
                      className="flex-1 rounded-xl border border-amber-300 bg-white px-3 py-2 text-xs"
                    />
                    <button
                      type="submit"
                      className="px-3.5 py-2 rounded-xl bg-amber-900 text-white text-xs font-bold inline-flex items-center gap-1"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Ajouter
                    </button>
                  </form>
                  <div className="space-y-1.5 text-xs">
                    {(privateNotesByContact[selectedContact.id] ?? []).length === 0 ? (
                      <p className="text-amber-900/75 italic">Aucune note privée enregistrée sur ce contact.</p>
                    ) : (
                      (privateNotesByContact[selectedContact.id] ?? []).map((n) => (
                        <div key={n.id} className="rounded-lg bg-white border border-amber-200 px-3 py-2">
                          <p className="text-obsidianInk">{n.content}</p>
                        </div>
                      ))
                    )}
                  </div>
                </div>

                {/* Historique des échanges */}
                <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3">
                  <h4 className="font-display font-bold text-sm text-emeraldDeep flex items-center gap-1.5">
                    <MessageSquarePlus className="w-4 h-4 text-goldAntique" />
                    Journal des échanges commerciaux
                  </h4>
                  <form onSubmit={handleAddExchange} className="flex flex-wrap gap-2">
                    <select
                      value={newExchangeChannel}
                      onChange={(e) => setNewExchangeChannel(e.target.value as ExchangeChannel)}
                      className="rounded-xl border border-borderSubtle bg-ivoryWarm px-2.5 py-2 text-xs"
                    >
                      <option value="CALL">Appel</option>
                      <option value="EMAIL">Email</option>
                      <option value="VISIT">Visite</option>
                      <option value="OFFER">Offre</option>
                      <option value="MEETING">Rendez-vous</option>
                    </select>
                    <input
                      type="text"
                      value={newExchangeSummary}
                      onChange={(e) => setNewExchangeSummary(e.target.value)}
                      placeholder="Résumé de l'échange avec le client..."
                      className="flex-1 min-w-[200px] rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-xs"
                    />
                    <button
                      type="submit"
                      className="px-4 py-2 rounded-xl bg-emeraldDeep text-ivoryWarm text-xs font-bold"
                    >
                      Consigner
                    </button>
                  </form>
                  <div className="space-y-1.5 text-xs">
                    {(exchangesByContact[selectedContact.id] ?? []).map((ex) => (
                      <div key={ex.id} className="rounded-xl bg-ivoryWarm border border-borderSubtle px-3 py-2 flex items-center justify-between">
                        <span>
                          <strong className="text-emeraldDeep">[{ex.channel}]</strong> {ex.summary}
                        </span>
                        <span className="text-[10px] text-slateMuted">{ex.created_at.slice(0, 10)}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {rgpdJsonPreview && (
                  <div className="rounded-2xl bg-obsidianInk text-ivoryWarm p-4 space-y-2">
                    <p className="text-xs font-bold text-goldChampagne">
                      Aperçu Export Portabilité RGPD (Art. 15/20 — Notes privées exclues)
                    </p>
                    <pre className="text-[11px] overflow-x-auto max-h-56 text-emerald-200">{rgpdJsonPreview}</pre>
                  </div>
                )}
              </>
            ) : (
              <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 text-sm text-slateMuted">
                Sélectionnez un contact pour afficher sa fiche détaillée.
              </div>
            )}
          </div>
        </div>
      )}

      {tab === "followups" && (
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-display text-lg font-bold text-emeraldDeep">
              Moteur de relances automatiques (J+3 visites, J+7 offres, pièces manquantes, RGPD)
            </h3>
          </div>
          <div className="space-y-2.5">
            {tasks.map((t) => (
              <div
                key={t.id}
                className={`rounded-xl border p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
                  t.completed ? "bg-emerald-50/50 border-emerald-200 opacity-70" : "bg-ivoryWarm border-borderSubtle"
                }`}
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                        t.priority === "HIGH" ? "bg-red-100 text-red-900" : "bg-amber-100 text-amber-900"
                      }`}
                    >
                      {t.priority}
                    </span>
                    <p className="font-bold text-sm text-obsidianInk">{t.title}</p>
                  </div>
                  <p className="text-xs text-slateMuted">{t.reason}</p>
                </div>
                <button
                  type="button"
                  disabled={t.completed}
                  onClick={() => handleCompleteTask(t.id)}
                  className="px-4 py-2 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold disabled:opacity-50 shrink-0"
                >
                  {t.completed ? "Traitée ✓" : "Marquer traitée"}
                </button>
              </div>
            ))}
          </div>
        </section>
      )}

      {tab === "web_leads" && (
        <section className="space-y-5">
          {/* Règles Mots-Clés Monty Leads */}
          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 text-xs">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h3 className="font-display text-base font-bold text-emeraldDeep">
                  Monty Leads • Règles de détection &amp; scoring 0–100
                </h3>
                <p className="text-slateMuted">
                  Mots-clés métier actifs sur les leads entrants (Vendeurs, Acquéreurs, Investisseurs, Bailleurs + zone géo).
                </p>
              </div>
            </div>
            <form onSubmit={handleCreateLeadKeyword} className="grid grid-cols-1 sm:grid-cols-4 gap-2">
              <select
                value={kwCategory}
                onChange={(e) => setKwCategory(e.target.value)}
                className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              >
                <option value="VENDEURS">VENDEURS</option>
                <option value="ACQUEREURS">ACQUEREURS</option>
                <option value="INVESTISSEURS">INVESTISSEURS</option>
                <option value="BAILLEURS">BAILLEURS</option>
              </select>
              <input
                type="text"
                value={kwText}
                onChange={(e) => setKwText(e.target.value)}
                placeholder="Mot-clé (ex: succession, DPE, mandat)"
                className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
              <input
                type="text"
                value={kwGeo}
                onChange={(e) => setKwGeo(e.target.value)}
                placeholder="Portée géographique (ex: Lyon, Hérault)"
                className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
              <button
                type="submit"
                className="px-3 py-2 rounded-xl bg-emeraldDeep text-goldChampagne font-bold hover:bg-emeraldMid"
              >
                Ajouter règle
              </button>
            </form>
            {leadKeywords.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                {leadKeywords.map((kw) => (
                  <span
                    key={kw.id}
                    className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-ivoryWarm border border-borderSubtle"
                  >
                    <strong className="text-emeraldDeep">{kw.category}</strong>
                    <span>{kw.keyword}</span>
                    <span className="text-slateMuted">({kw.geo_scope})</span>
                    <button
                      type="button"
                      onClick={() => handleDeleteLeadKeyword(kw.id)}
                      aria-label={`Supprimer ${kw.keyword}`}
                      className="text-rose-700 hover:text-rose-900 font-bold"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>

          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h3 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
                  <Globe className="w-5 h-5 text-goldAntique" />
                  Demandes Entrantes Site Web Public (Consentement RGPD vérifié)
                </h3>
                <p className="text-xs text-slateMuted">
                  Alertes acquéreurs, recherches guidées « Je ne trouve pas mon bien » et avis de valeur vendeurs.
                </p>
              </div>
              <button
                type="button"
                onClick={refreshCrmData}
                className="px-3 py-1.5 rounded-xl border border-borderSubtle text-xs font-semibold hover:border-emeraldDeep"
              >
                Actualiser
              </button>
            </div>
            {webLeads.length === 0 ? (
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 text-xs text-slateMuted">
                Aucune demande web entrante enregistrée pour le moment sur cette agence.
              </div>
            ) : (
              <div className="space-y-2.5 text-xs">
                {webLeads.map((lead) => (
                  <div
                    key={lead.id}
                    className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                  >
                    <div className="space-y-1.5">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="px-2 py-0.5 rounded-full bg-emeraldDeep text-goldChampagne font-bold text-[10px]">
                          {lead.lead_kind === "VISIT_REQUEST"
                            ? "Demande de Visite"
                            : lead.lead_kind === "SEARCH_ALERT"
                            ? "Alerte Recherche"
                            : lead.lead_kind === "SELLER_VALUATION"
                            ? "Avis de Valeur Vendeur"
                            : "Conciergerie Acquéreur"}
                        </span>
                        {lead.qualification_score !== undefined && (
                          <span
                            className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${
                              lead.qualification_score >= 70
                                ? "bg-emerald-100 text-emerald-900"
                                : lead.qualification_score >= 45
                                ? "bg-amber-100 text-amber-900"
                                : "bg-slate-200 text-slate-700"
                            }`}
                          >
                            Score {lead.qualification_score}/100
                          </span>
                        )}
                        {lead.intent_stage && (
                          <span className="px-2 py-0.5 rounded-full bg-goldChampagne/30 text-emeraldDeep font-bold text-[10px]">
                            {lead.intent_stage}
                          </span>
                        )}
                        <span className="font-bold text-sm text-obsidianInk">{lead.email}</span>
                        <span className="text-slateMuted">({lead.country_code})</span>
                      </div>
                      <p className="text-slateMuted">
                        Secteur : <strong>{lead.city || "Non précisé"}</strong> • Budget :{" "}
                        <strong>{lead.budget_euro.toLocaleString("fr-FR")} €</strong>
                        {lead.property_title ? ` • Bien : ${lead.property_title}` : ""}
                        {lead.horizon ? ` • Horizon : ${lead.horizon}` : ""}
                      </p>
                      {lead.notes && <p className="text-obsidianInk/80">{lead.notes}</p>}
                      {(lead.qualification_reasons?.length || lead.matched_keywords?.length) ? (
                        <div className="flex flex-wrap gap-1 pt-0.5 text-[10px]">
                          {(lead.matched_keywords ?? []).map((mk) => (
                            <span key={mk} className="px-2 py-0.5 rounded bg-emeraldDeep/10 text-emeraldDeep font-semibold">
                              #{mk}
                            </span>
                          ))}
                          {(lead.qualification_reasons ?? []).map((rs) => (
                            <span key={rs} className="px-2 py-0.5 rounded bg-white border border-borderSubtle text-slateMuted">
                              {rs}
                            </span>
                          ))}
                        </div>
                      ) : null}
                    </div>
                    <div className="flex flex-wrap items-center gap-1.5 shrink-0">
                      {(["NEW", "CONTACTED", "CONVERTED", "ARCHIVED"] as LeadStatus[]).map((st) => (
                        <button
                          key={st}
                          type="button"
                          onClick={() => handleUpdateLeadStatus(lead.id, st)}
                          className={`px-2.5 py-1 rounded-lg font-bold ${
                            lead.status === st
                              ? "bg-emeraldDeep text-goldChampagne"
                              : "bg-white border border-borderSubtle text-slateMuted hover:text-obsidianInk"
                          }`}
                        >
                          {st}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>
      )}

      {tab === "csv_rgpd" && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
              <FileSpreadsheet className="w-5 h-5 text-goldAntique" />
              Import CSV / Excel avec dédoublonnage &amp; validation
            </h3>
            <p className="text-xs text-slateMuted">
              Accepte les en-têtes français, anglais et portugais (`nom;email;telephone;ville;budget;role;pays`).
            </p>
            <textarea
              rows={6}
              value={csvInput}
              onChange={(e) => setCsvInput(e.target.value)}
              className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm p-3 font-mono text-xs"
            />
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={handleImportCsv}
                className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold"
              >
                Lancer l&apos;import CSV
              </button>
              <button
                type="button"
                onClick={handleExportSafeCsv}
                className="px-4 py-2.5 rounded-xl border border-emeraldDeep text-emeraldDeep text-xs font-bold inline-flex items-center gap-1.5"
              >
                <Download className="w-4 h-4" />
                Générer Export CSV sécurisé
              </button>
            </div>
            {importSummary && (
              <div className="rounded-xl bg-emerald-50 border border-emerald-200 p-3 text-xs space-y-1">
                <p className="font-bold text-emerald-950">
                  Import terminé : {importSummary.imported_count} importé(s), {importSummary.skipped_duplicates} doublon(s) ignoré(s),{" "}
                  {importSummary.rejected_rows} ligne(s) rejetée(s).
                </p>
              </div>
            )}
            {csvExportPreview && (
              <pre className="rounded-xl bg-obsidianInk text-emerald-200 p-3 text-[11px] overflow-x-auto max-h-48">
                {csvExportPreview}
              </pre>
            )}
          </section>

          <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
            <h3 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
              <ShieldAlert className="w-5 h-5 text-goldAntique" />
              Gouvernance RGPD &amp; Sécurité Anti-Injection Excel
            </h3>
            <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 text-xs space-y-2 text-slateMuted">
              <p className="font-bold text-obsidianInk flex items-center gap-1.5">
                <AlertTriangle className="w-4 h-4 text-amber-600" />
                Protection contre l&apos;injection de formules CSV (=, +, -, @)
              </p>
              <p>
                Toute cellule exportée commençant par `=`, `+`, `-` ou `@` est automatiquement préfixée par une apostrophe
                protectrice avant ouverture dans Excel / LibreOffice.
              </p>
            </div>
            <button
              type="button"
              onClick={handlePurgeExpired}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-red-900 text-white text-xs font-bold hover:bg-red-800 transition"
            >
              <Trash2 className="w-4 h-4" />
              Exécuter la purge RGPD des contacts expirés
            </button>
          </section>
        </div>
      )}
    </div>
  );
}
