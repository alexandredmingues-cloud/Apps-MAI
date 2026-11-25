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
import { montyApi } from "@/lib/api-client";
import type {
  AdvisorPrivateNote,
  ContactExchange,
  ContactRole,
  CountryCode,
  CrmContact,
  CsvImportSummary,
  ExchangeChannel,
  FollowUpTask,
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
      const [cResp, tResp, lResp] = await Promise.all([
        montyApi.listCrmContacts(),
        montyApi.getAutoFollowups(),
        montyApi.listPublicLeads(),
      ]);
      setContacts(cResp.contacts);
      setTasks(tResp.tasks);
      setWebLeads(lResp.leads);
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

  async function handleUpdateLeadStatus(leadId: string, nextStatus: LeadStatus) {
    setWebLeads((prev) => prev.map((l) => (l.id === leadId ? { ...l, status: nextStatus } : l)));
    try {
      const updated = await montyApi.updatePublicLeadStatus(leadId, nextStatus);
      setWebLeads((prev) => prev.map((l) => (l.id === leadId ? updated : l)));
      setStatusMessage(`Statut de la demande mis à jour : ${nextStatus}.`);
    } catch {
      // Conserver état local si session expirée
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
      setStatusMessage(`Contact « ${created.full_name} » enregistré via API FastAPI.`);
    } catch {
      const todayIso = new Date().toISOString().slice(0, 10);
      const created: CrmContact = {
        id: `cnt-${Date.now()}`,
        agency_id: agencyId,
        assigned_user_id: `usr-${role.toLowerCase()}`,
        country_code: newCountry,
        full_name: newName.trim(),
        email: newEmail.trim().toLowerCase(),
        phone: newPhone.trim(),
        role: newRole,
        city: newCity.trim(),
        budget_euro: newBudget,
        legal_basis: "CONSENTEMENT_EXPLICITE",
        consent_granted: true,
        retention_until: "2029-04-14",
        created_at: todayIso,
        last_contact_at: todayIso,
        has_missing_docs: false,
        pending_offer_days: 0,
        last_visit_days_ago: null,
        is_anonymized: false,
      };
      setContacts((prev) => [created, ...prev]);
      setSelectedContactId(created.id);
      setStatusMessage(`Contact « ${created.full_name} » créé en local (RGPD 36 mois appliqué).`);
    }
    setNewName("");
    setNewEmail("");
    setNewPhone("");
    setShowCreateForm(false);
  }

  async function handleAddPrivateNote(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedContact || !newNoteText.trim()) return;
    if (role === "BUYER" || role === "SELLER") {
      setStatusMessage("Accès refusé (RBAC) : les notes privées sont réservées aux conseillers.");
      return;
    }
    try {
      const note = await montyApi.addPrivateNote(selectedContact.id, newNoteText);
      setPrivateNotesByContact((prev) => ({
        ...prev,
        [selectedContact.id]: [...(prev[selectedContact.id] ?? []), note],
      }));
    } catch {
      const note: AdvisorPrivateNote = {
        id: `note-${Date.now()}`,
        agency_id: agencyId,
        contact_id: selectedContact.id,
        author_user_id: `usr-${role.toLowerCase()}`,
        content: newNoteText.trim(),
        created_at: new Date().toISOString(),
      };
      setPrivateNotesByContact((prev) => ({
        ...prev,
        [selectedContact.id]: [...(prev[selectedContact.id] ?? []), note],
      }));
    }
    setNewNoteText("");
    setStatusMessage("Note privée ajoutée (strictement isolée de l'export client RGPD).");
  }

  async function handleAddExchange(e: React.FormEvent) {
    e.preventDefault();
    if (!selectedContact || !newExchangeSummary.trim()) return;
    try {
      const ex = await montyApi.addExchange(selectedContact.id, {
        channel: newExchangeChannel,
        summary: newExchangeSummary,
      });
      setExchangesByContact((prev) => ({
        ...prev,
        [selectedContact.id]: [...(prev[selectedContact.id] ?? []), ex],
      }));
    } catch {
      const ex: ContactExchange = {
        id: `exc-${Date.now()}`,
        agency_id: agencyId,
        contact_id: selectedContact.id,
        author_user_id: `usr-${role.toLowerCase()}`,
        channel: newExchangeChannel,
        summary: newExchangeSummary.trim(),
        created_at: new Date().toISOString(),
      };
      setExchangesByContact((prev) => ({
        ...prev,
        [selectedContact.id]: [...(prev[selectedContact.id] ?? []), ex],
      }));
    }
    setNewExchangeSummary("");
  }

  async function handleCompleteTask(taskId: string) {
    try {
      await montyApi.completeFollowup(taskId);
    } catch {
      // fallback local
    }
    setTasks((prev) => prev.map((t) => (t.id === taskId ? { ...t, completed: true } : t)));
  }

  async function handleImportCsv() {
    try {
      const summary = await montyApi.importCsv({ csv_text: csvInput, default_country: "FR" });
      setImportSummary(summary);
      await refreshCrmData();
    } catch {
      const lines = csvInput.trim().split(/\r?\n/);
      if (lines.length < 2) return;
      const sep = lines[0].includes(";") ? ";" : ",";
      const existing = new Set(contacts.map((c) => c.email.toLowerCase()).filter(Boolean));
      let imported = 0;
      let skipped = 0;
      let rejected = 0;
      const newOnes: CrmContact[] = [];
      const createdIds: string[] = [];
      const todayIso = new Date().toISOString().slice(0, 10);

      for (const line of lines.slice(1)) {
        const cols = line.split(sep).map((s) => s.trim());
        const [name, email = "", phone = "", city = "", rawBudget = "0", rawRole = "LEAD", rawCountry = "FR"] = cols;
        if (!name) {
          rejected += 1;
          continue;
        }
        if (email && existing.has(email.toLowerCase())) {
          skipped += 1;
          continue;
        }
        const id = `cnt-csv-${Date.now()}-${imported}`;
        const c: CrmContact = {
          id,
          agency_id: agencyId,
          assigned_user_id: `usr-${role.toLowerCase()}`,
          country_code: rawCountry.toUpperCase() === "PT" ? "PT" : "FR",
          full_name: name,
          email: email.toLowerCase(),
          phone,
          role: (["BUYER", "SELLER", "LEAD", "PROSPECT", "PARTNER"].includes(rawRole.toUpperCase())
            ? rawRole.toUpperCase()
            : "LEAD") as ContactRole,
          city,
          budget_euro: Number(rawBudget.replace(/\D/g, "")) || 0,
          legal_basis: "CONSENTEMENT_EXPLICITE",
          consent_granted: true,
          retention_until: "2029-04-14",
          created_at: todayIso,
          last_contact_at: todayIso,
          has_missing_docs: false,
          pending_offer_days: 0,
          last_visit_days_ago: null,
          is_anonymized: false,
        };
        if (c.email) existing.add(c.email);
        newOnes.push(c);
        createdIds.push(id);
        imported += 1;
      }
      setContacts((prev) => [...newOnes, ...prev]);
      setImportSummary({
        imported_count: imported,
        skipped_duplicates: skipped,
        rejected_rows: rejected,
        warnings: [],
        created_contact_ids: createdIds,
      });
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
    try {
      const res = await montyApi.rgpdPurgeExpired();
      setStatusMessage(`Purge RGPD exécutée : ${res.anonymized_count} contact(s) expiré(s) anonymisé(s).`);
      await refreshCrmData();
    } catch {
      const todayIso = new Date().toISOString().slice(0, 10);
      let count = 0;
      setContacts((prev) =>
        prev.map((c) => {
          if (!c.is_anonymized && c.retention_until < todayIso) {
            count += 1;
            return { ...c, full_name: "[Contact anonymisé RGPD]", email: "", phone: "", is_anonymized: true };
          }
          return c;
        })
      );
      setStatusMessage(`Vérification RGPD effectuée : ${count} contact(s) expiré(s) anonymisé(s).`);
    }
  }

  return (
    <div className="space-y-6">
      {/* En-tête CRM Phase 2 */}
      <section className="rounded-2xl bg-gradient-to-r from-emeraldDeep to-emeraldMid text-ivoryWarm p-6 border border-goldAntique/40">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-widest text-goldChampagne">
              Phase 2 • CRM Multi-Agences, Relances Auto &amp; RGPD ({modeLabel === "api" ? "FastAPI Live" : "Mode Local"})
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
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
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
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="px-2 py-0.5 rounded-full bg-emeraldDeep text-goldChampagne font-bold text-[10px]">
                        {lead.lead_kind}
                      </span>
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
