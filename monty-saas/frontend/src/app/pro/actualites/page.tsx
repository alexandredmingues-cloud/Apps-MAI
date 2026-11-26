"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { ShieldAlert } from "lucide-react";
import { MontyShell } from "@/components/layout/MontyShell";
import { NewsHubExperience } from "@/components/news/NewsHubExperience";
import { loadPersistedSession, montyApi, persistSession } from "@/lib/api-client";
import type { MontyRole, NavSectionKey, SessionInfo } from "@/lib/types";

const PRO_ROLES: MontyRole[] = ["SUPER_ADMIN", "DIRECTOR", "ADMIN", "AGENT", "ASSISTANT"];

export default function ProNewsPage() {
  const [activeSection, setActiveSection] = useState<NavSectionKey>("actualites");
  const [session, setSession] = useState<SessionInfo | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    const stored = loadPersistedSession();
    if (!stored) {
      setChecking(false);
      return;
    }
    montyApi
      .getMe()
      .then((me) => {
        const verified: SessionInfo = {
          ...stored,
          user_id: me.user_id,
          agency_id: me.agency_id,
          role: me.role,
          permissions: me.permissions,
        };
        persistSession(verified);
        setSession(verified);
      })
      .catch(() => {
        persistSession(null);
        setSession(null);
      })
      .finally(() => {
        setChecking(false);
      });
  }, []);

  const isProAuthorized = Boolean(session && PRO_ROLES.includes(session.role));

  return (
    <MontyShell
      activeSection={activeSection}
      onSelectSection={setActiveSection}
      agencyId={session?.agency_id ?? "Session non connectée"}
      role={session?.role ?? "VISITEUR"}
    >
      {checking ? (
        <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 text-sm text-slateMuted">
          Vérification de la session professionnelle Monty Pro...
        </div>
      ) : !isProAuthorized ? (
        <div className="rounded-2xl bg-ivoryCard border border-amber-300 p-6 space-y-3">
          <p className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-amber-700" />
            Accès Veille &amp; Actualités Pro réservé aux conseillers connectés
          </p>
          <p className="text-sm text-slateMuted">
            {session
              ? `Votre session actuelle (${session.role}) dispose uniquement de l'accès Particulier.`
              : "Veuillez vous connecter avec un compte agence Monty Pro pour accéder aux analyses professionnelles et au générateur IA."}
          </p>
          <div className="flex flex-wrap gap-2">
            <Link
              href="/"
              className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-goldChampagne text-xs font-bold hover:bg-emeraldMid"
            >
              Connexion Cockpit Pro
            </Link>
            <Link
              href="/actualites"
              className="px-4 py-2.5 rounded-xl border border-emeraldDeep text-emeraldDeep text-xs font-bold hover:bg-ivoryWarm"
            >
              Voir les Actualités Particulier
            </Link>
          </div>
        </div>
      ) : (
        <NewsHubExperience audience="pro" role={session!.role} initialCountry="FR" />
      )}
    </MontyShell>
  );
}
