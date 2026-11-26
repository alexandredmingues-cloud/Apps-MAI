"use client";

import React from "react";
import Link from "next/link";
import { Building2, ShieldCheck } from "lucide-react";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { NotariesAndRnicPanel } from "@/components/legal/NotariesAndRnicPanel";

export default function CoproprietesPublicPage() {
  return (
    <div className="min-h-screen bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="coproprietes" />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <section className="rounded-2xl bg-gradient-to-r from-emeraldDeep to-emeraldMid text-ivoryWarm p-6 border border-goldAntique/40 space-y-2">
          <p className="text-xs font-bold uppercase tracking-widest text-goldChampagne flex items-center gap-1.5">
            <Building2 className="w-4 h-4" />
            Base Nationale des Copropriétés de France (RNIC ANAH) &amp; Annuaire Notaires
          </p>
          <h1 className="font-display text-2xl sm:text-3xl font-bold">
            Vérifier une Copropriété ou Trouver un Notaire en France
          </h1>
          <p className="text-xs sm:text-sm text-ivoryWarm/85 max-w-3xl">
            Consultez le Registre National d&apos;Immatriculation des Copropriétés (ANAH / data.gouv.fr) et l&apos;annuaire officiel des
            études notariales de France. Accès intégral gratuit pour les agences partenaires et vendeurs vérifiés ; Pass
            Particulier 30 jours pour les acquéreurs.
          </p>
          <div className="pt-2 flex flex-wrap gap-2 text-xs">
            <Link
              href="/mon-compte"
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-goldAntique text-emeraldDeep font-bold hover:bg-goldChampagne"
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              Je suis Vendeur / Agence (Accès Gratuit)
            </Link>
            <Link
              href="/annonces"
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-emeraldSurface border border-goldAntique/40 text-ivoryWarm font-semibold hover:bg-emeraldMid"
            >
              Voir les annonces par temps de trajet
            </Link>
          </div>
        </section>

        <NotariesAndRnicPanel mode="public" initialTab="rnic" />
      </main>
      <PublicWebFooter />
    </div>
  );
}
