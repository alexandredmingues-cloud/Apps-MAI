import React from "react";
import type { Metadata } from "next";
import Link from "next/link";
import {
  ArrowRight,
  Bike,
  Briefcase,
  Building2,
  Bus,
  Calculator,
  Car,
  CheckCircle2,
  Clock,
  Footprints,
  Globe2,
  Home,
  Lock,
  MapPin,
  Newspaper,
  ShieldCheck,
  Sparkles,
  TrainFront,
  TramFront,
} from "lucide-react";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { getPublicArticlesCatalog } from "@/lib/news-catalog";

export const metadata: Metadata = {
  title: "MON-AGENT — Plateforme immobilière intelligente (France & Portugal)",
  description:
    "Recherchez un bien par temps de trajet, estimez le prix et le délai de vente, publiez votre annonce, suivez l'actualité immobilière sourcée et utilisez nos 6 simulateurs gratuits.",
};

export default function MontyPublicWebsitePage() {
  const frHighlights = getPublicArticlesCatalog("FR").slice(0, 3);
  const ptHighlights = getPublicArticlesCatalog("PT").slice(0, 2);

  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="site" />

      {/* 1. HERO PRINCIPAL DOUBLE PUBLIC */}
      <section className="bg-gradient-to-br from-emeraldDeep via-emeraldMid to-emeraldDeep text-ivoryWarm border-b border-goldAntique/40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-14 lg:py-20 grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
          <div className="lg:col-span-7 space-y-6">
            <div className="inline-flex items-center gap-2 rounded-full bg-emeraldSurface border border-goldAntique/40 px-3.5 py-1.5 text-xs font-semibold text-goldChampagne">
              <Globe2 className="w-3.5 h-3.5" />
              France entière (Active) • Portugal (Module isolé prêt) • Europe
            </div>
            <h1 className="font-display text-3xl sm:text-5xl font-bold leading-tight">
              L&apos;immobilier clair pour les <span className="text-goldChampagne">particuliers</span>,
              le système d&apos;exploitation des <span className="text-goldChampagne">agences</span>.
            </h1>
            <p className="text-base sm:text-lg text-ivoryWarm/85 leading-relaxed max-w-2xl">
              « L&apos;humain avant tout. La technologie au service de l&apos;immobilier. »
              Recherchez votre bien selon votre trajet réel, estimez la valeur et le délai de vente,
              simulez votre financement et suivez l&apos;actualité immobilière vérifiée.
            </p>
            <div className="space-y-3 pt-1">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                <Link
                  href="/annonces"
                  className="inline-flex items-center justify-center gap-2 px-4 py-3.5 rounded-xl bg-goldAntique text-emeraldDeep font-bold text-sm hover:bg-goldChampagne transition text-center"
                >
                  <Home className="w-4 h-4 shrink-0" />
                  Je cherche un bien
                </Link>
                <Link
                  href="/publier"
                  className="inline-flex items-center justify-center gap-2 px-4 py-3.5 rounded-xl bg-emeraldSurface text-ivoryWarm font-semibold text-sm border border-goldAntique/50 hover:bg-emeraldMid transition text-center"
                >
                  <Sparkles className="w-4 h-4 text-goldChampagne shrink-0" />
                  Je vends mon bien
                </Link>
                <Link
                  href="/"
                  className="inline-flex items-center justify-center gap-2 px-4 py-3.5 rounded-xl bg-transparent text-ivoryWarm font-semibold text-sm border border-ivoryWarm/35 hover:bg-emeraldSurface/60 transition text-center"
                >
                  <Briefcase className="w-4 h-4 text-goldChampagne shrink-0" />
                  Je suis professionnel
                </Link>
              </div>
              <div className="flex flex-wrap items-center gap-4 text-xs text-ivoryWarm/85 pt-0.5">
                <Link href="/simulateurs" className="inline-flex items-center gap-1.5 hover:text-goldChampagne underline underline-offset-4">
                  <Calculator className="w-3.5 h-3.5 text-goldChampagne" />
                  Accéder aux 6 simulateurs gratuits
                </Link>
                <Link href="/actualites" className="inline-flex items-center gap-1.5 hover:text-goldChampagne underline underline-offset-4">
                  <Newspaper className="w-3.5 h-3.5 text-goldChampagne" />
                  Actualités immobilières FR &amp; PT
                </Link>
              </div>
            </div>
          </div>

          {/* Carte interactive d'illustration Recherche par Temps de Trajet */}
          <div className="lg:col-span-5 rounded-2xl bg-ivoryWarm text-obsidianInk p-6 border border-goldAntique/40 shadow-xl space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold uppercase tracking-wider text-emeraldDeep">
                Recherche intelligente par mobilité
              </span>
              <span className="text-[11px] px-2 py-0.5 rounded bg-emeraldDeep/10 text-emeraldDeep font-semibold">
                6 modes de trajet
              </span>
            </div>
            <div className="space-y-2.5 text-sm">
              <div className="rounded-xl border border-borderSubtle bg-ivoryCard px-3.5 py-2.5 flex items-center gap-2.5">
                <MapPin className="w-4 h-4 text-emeraldDeep shrink-0" />
                <span>Adresse de référence (travail, école, gare, centre...)</span>
              </div>
              <div className="grid grid-cols-3 gap-2 text-xs font-semibold">
                {[
                  { label: "Train / TER", Icon: TrainFront },
                  { label: "Tramway", Icon: TramFront },
                  { label: "Bus", Icon: Bus },
                  { label: "Vélo", Icon: Bike },
                  { label: "À pied", Icon: Footprints },
                  { label: "Voiture", Icon: Car },
                ].map(({ label, Icon }) => (
                  <div
                    key={label}
                    className="rounded-xl border border-borderSubtle bg-ivoryCard px-2.5 py-2 flex items-center gap-1.5 text-emeraldDeep"
                  >
                    <Icon className="w-3.5 h-3.5 shrink-0" />
                    <span className="truncate">{label}</span>
                  </div>
                ))}
              </div>
              <Link
                href="/annonces"
                className="rounded-xl bg-emeraldDeep text-ivoryWarm p-3.5 text-xs flex items-center justify-between hover:bg-emeraldMid transition"
              >
                <div>
                  <p className="text-ivoryWarm/75">Critère combiné</p>
                  <p className="font-bold text-goldChampagne">Rayon km OU Temps max → Rechercher</p>
                </div>
                <ArrowRight className="w-5 h-5 text-goldChampagne" />
              </Link>
            </div>
          </div>
        </div>
      </section>

      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-14 w-full">
        {/* 2. PARCOURS PARTICULIERS : ACHETER, VENDRE & PUBLIER */}
        <section className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-2">
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-goldAntique">
                Mon-Agent Particulier • France entière &amp; Portugal
              </p>
              <h2 className="font-display text-2xl sm:text-3xl font-bold text-emeraldDeep">
                Tout pour acheter, vendre et publier en confiance
              </h2>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Link href="/annonces" className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-3 hover:border-emeraldDeep transition">
              <div className="h-10 w-10 rounded-xl bg-emeraldDeep text-goldChampagne flex items-center justify-center">
                <Home className="w-5 h-5" />
              </div>
              <h3 className="font-display text-lg font-bold text-emeraldDeep">
                Annonces Particuliers &amp; Agences →
              </h3>
              <p className="text-sm text-slateMuted leading-relaxed">
                Accédez librement aux biens publiés par des particuliers vendeurs et des agences partenaires,
                filtrés par budget, DPE/certificat énergétique, distance ou temps de trajet réel + comparateur.
              </p>
            </Link>

            <Link href="/publier" className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-3 hover:border-emeraldDeep transition">
              <div className="h-10 w-10 rounded-xl bg-emeraldDeep text-goldChampagne flex items-center justify-center">
                <Sparkles className="w-5 h-5" />
              </div>
              <h3 className="font-display text-lg font-bold text-emeraldDeep">
                Publication Vendeur &amp; Aide à la rédaction →
              </h3>
              <p className="text-sm text-slateMuted leading-relaxed">
                Les propriétaires publient leur bien facilement avec une aide intelligente à la rédaction d&apos;annonce
                appuyée sur les critères techniques, les photos et la protection anti-démarchage RGPD.
              </p>
            </Link>

            <Link href="/simulateurs" className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-3 hover:border-emeraldDeep transition">
              <div className="h-10 w-10 rounded-xl bg-emeraldDeep text-goldChampagne flex items-center justify-center">
                <Clock className="w-5 h-5" />
              </div>
              <h3 className="font-display text-lg font-bold text-emeraldDeep">
                Prix DVF, HCSF &amp; Délai de vente →
              </h3>
              <p className="text-sm text-slateMuted leading-relaxed">
                Obtenez une fourchette de prix indicative, vos frais de notaire/IMT et une estimation transparente du délai
                de vente probable selon le DPE, les photos et la complétude du dossier.
              </p>
            </Link>
          </div>

          {/* Pourquoi MON-AGENT surpasse les portails généralistes */}
          <div className="rounded-2xl bg-ivoryCard border border-goldAntique/40 p-6 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <h3 className="font-display text-xl font-bold text-emeraldDeep">
                Pourquoi MON-AGENT est conçu comme la référence face aux portails classiques
              </h3>
              <span className="text-xs font-bold px-3 py-1 rounded-full bg-emeraldDeep text-goldChampagne">
                Transparence • Mobilité • Zéro Spam
              </span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-1">
                <p className="font-bold text-emeraldDeep">1. Recherche par trajet réel (6 modes)</p>
                <p className="text-slateMuted">
                  Là où les portails classiques imposent un code postal ou un simple cercle km, MON-AGENT calcule le temps réel en
                  Train/TER, Tram/Métro, Bus, Vélo, À pied ou Voiture.
                </p>
              </div>
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-1">
                <p className="font-bold text-emeraldDeep">2. Comparateur &amp; Délai de vente transparent</p>
                <p className="text-slateMuted">
                  Comparez jusqu&apos;à 3 biens côte à côte avec prix/m², DPE/CE, délai probable de commercialisation et accès aux
                  6 simulateurs financiers sans quitter la plateforme.
                </p>
              </div>
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-1">
                <p className="font-bold text-emeraldDeep">3. Protection RGPD &amp; Veille Double Public</p>
                <p className="text-slateMuted">
                  Masquage automatique des PII dans les textes publics, actualités sourcées expliquées pour les particuliers et
                  arguments terrain pour les conseillers.
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* 3. LES 6 SIMULATEURS IMMOBILIERS */}
        <section className="rounded-2xl bg-emeraldDeep text-ivoryWarm p-6 sm:p-8 border border-goldAntique/40 space-y-6">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-goldChampagne">
                Outils Gratuits • Particuliers &amp; Conseillers
              </p>
              <h2 className="mt-1 font-display text-2xl font-bold">
                6 Simulateurs Immobiliers Multi-Juridictions
              </h2>
            </div>
            <Link
              href="/simulateurs"
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-goldAntique text-emeraldDeep text-sm font-bold hover:bg-goldChampagne transition shrink-0"
            >
              Ouvrir tous les simulateurs
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 text-sm">
            {[
              { title: "1. Mensualités & Assurance", desc: "Échéance mensuelle, coût total du crédit et scénarios 20/25 ans." },
              { title: "2. Capacité d'emprunt HCSF 35 %", desc: "Plafond réglementaire France (et module DSTI Banco de Portugal isolé)." },
              { title: "3. Frais de notaire / IMT (PT)", desc: "DMTO par département (Ancien vs Neuf FR) ou IMT + Imposto do Selo (PT)." },
              { title: "4. Rendement locatif & Cash-flow", desc: "Rentabilité brute/nette après charges et alertes réglementaires DPE F/G." },
              { title: "5. Estimation indicative", desc: "Repères multi-villes France ajustés selon surface, état et performance." },
              { title: "6. Plus-value immobilière CGI", desc: "Abattements 22 ans IR / 30 ans PS, surtaxe > 50 k€ et résidence principale." },
            ].map((sim) => (
              <div key={sim.title} className="rounded-xl bg-emeraldSurface/80 border border-goldAntique/30 p-4 space-y-1">
                <p className="font-bold text-goldChampagne">{sim.title}</p>
                <p className="text-xs text-ivoryWarm/80">{sim.desc}</p>
              </div>
            ))}
          </div>
        </section>

        {/* 4. ACTUALITÉS DOUBLE PUBLIC FRANCE & PORTUGAL */}
        <section className="space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-goldAntique">
                Sources Officielles Vérifiées &amp; Historique
              </p>
              <h2 className="font-display text-2xl sm:text-3xl font-bold text-emeraldDeep">
                Actualités Immobilières (France &amp; Portugal)
              </h2>
            </div>
            <Link
              href="/actualites"
              className="inline-flex items-center gap-1.5 text-sm font-bold text-emeraldDeep hover:underline"
            >
              Voir tout le centre d&apos;actualités <ArrowRight className="w-4 h-4" />
            </Link>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            {frHighlights.map((art) => (
              <Link
                key={art.id}
                href={`/actualites/${art.slug}`}
                className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3 hover:border-emeraldMid transition flex flex-col justify-between"
              >
                <div className="space-y-2">
                  <span className="text-[11px] font-bold px-2.5 py-0.5 rounded-full bg-emeraldDeep/10 text-emeraldDeep">
                    🇫🇷 {art.region_name} • {art.published_at}
                  </span>
                  <h3 className="font-display text-base font-bold text-obsidianInk">{art.title}</h3>
                  <p className="text-xs text-slateMuted line-clamp-3">{art.public_summary}</p>
                </div>
                <p className="text-[11px] font-semibold text-emeraldDeep">Source : {art.source_name}</p>
              </Link>
            ))}
          </div>

          <div className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-xs font-bold uppercase tracking-wider text-emeraldDeep">
                🇵🇹 Module National Portugal Isolé (Priorité 4 — Banco de Portugal, Portal das Finanças, ADENE)
              </p>
              <Link href="/actualites" className="text-xs font-semibold text-emeraldDeep hover:underline">
                Explorer l&apos;espace Portugal →
              </Link>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {ptHighlights.map((art) => (
                <Link
                  key={art.id}
                  href={`/actualites/${art.slug}`}
                  className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 hover:border-emeraldMid transition space-y-1"
                >
                  <p className="text-[11px] font-semibold text-slateMuted">🇵🇹 {art.region_name} • {art.published_at}</p>
                  <p className="font-display font-bold text-sm text-obsidianInk">{art.title}</p>
                </Link>
              ))}
            </div>
          </div>
        </section>

        {/* 4 bis. TRANSPARENCE DES SOURCES & MÉTHODOLOGIE */}
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <p className="text-xs font-bold uppercase tracking-widest text-goldAntique">
                Crédibilité &amp; Données Vérifiées • Mise à jour Novembre 2026
              </p>
              <h2 className="font-display text-xl sm:text-2xl font-bold text-emeraldDeep">
                Sources officielles et limites transparentes de nos outils
              </h2>
            </div>
            <span className="inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1 rounded-full bg-emeraldDeep/10 text-emeraldDeep">
              <ShieldCheck className="w-4 h-4" />
              Données publiques vs privées cloisonnées
            </span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
            <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-1">
              <p className="font-bold text-emeraldDeep">Référentiels France &amp; Géocodage</p>
              <p className="text-slateMuted leading-relaxed">
                Transactions notariées DVF (Etalab / DGFiP), Base Adresse Nationale (BAN), barèmes HCSF 35 %,
                DMTO départementaux, CGI plus-values et Observatoire ADEME DPE.
              </p>
            </div>
            <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-1">
              <p className="font-bold text-emeraldDeep">Module Portugal Isolé</p>
              <p className="text-slateMuted leading-relaxed">
                Règles macroprudentielles Banco de Portugal (DSTI / LTV), barèmes IMT &amp; Imposto do Selo
                (Portal das Finanças / IMT Jovem) et certificats ADENE (A+ à F).
              </p>
            </div>
            <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-1">
              <p className="font-bold text-emeraldDeep">Clarté de démonstration &amp; limites</p>
              <p className="text-slateMuted leading-relaxed">
                Les estimations et délais de vente sont indicatifs. Les annonces d&apos;exemple sont clairement
                identifiées tant que le catalogue local d&apos;une zone est en cours d&apos;ouverture.
              </p>
            </div>
          </div>
        </section>

        {/* 5. MONTY PRO — POUR LES AGENCES IMMOBILIÈRES */}
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 sm:p-8 grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
          <div className="lg:col-span-7 space-y-4">
            <span className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-goldAntique">
              <Building2 className="w-4 h-4" />
              Mon-Agent Pro • SaaS Multi-Agences Sécurisé
            </span>
            <h2 className="font-display text-2xl sm:text-3xl font-bold text-emeraldDeep">
              Un cockpit métier complet pour vos conseillers et réseaux d&apos;agences
            </h2>
            <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-sm text-obsidianInk">
              {[
                "CRM Contacts, Acquéreurs & Vendeurs",
                "Matching acquéreurs / biens & Temps de trajet",
                "Estimations DVF pondérées & Délai de vente",
                "Veille Pro + Arguments commerciaux IA",
                "Isolation PostgreSQL RLS multi-agences",
                "Conformité RGPD native (Art. 5, 6, 15, 17, 20)",
              ].map((feat) => (
                <li key={feat} className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emeraldDeep shrink-0" />
                  <span>{feat}</span>
                </li>
              ))}
            </ul>
          </div>

          <div className="lg:col-span-5 rounded-2xl bg-emeraldDeep text-ivoryWarm p-6 space-y-4 border border-goldAntique/40">
            <div className="flex items-center justify-between">
              <p className="font-display text-lg font-bold">Accès Professionnel</p>
              <Lock className="w-5 h-5 text-goldChampagne" />
            </div>
            <p className="text-xs text-ivoryWarm/80 leading-relaxed">
              Déjà équipé ou en démonstration technique ? Ouvrez le Cockpit SaaS multi-agences avec RBAC 7 rôles
              ou consultez la veille stratégique réservée aux conseillers.
            </p>
            <div className="flex flex-col sm:flex-row gap-2.5">
              <Link
                href="/"
                className="flex-1 text-center px-4 py-2.5 rounded-xl bg-goldAntique text-emeraldDeep text-xs font-bold hover:bg-goldChampagne transition"
              >
                Ouvrir le Cockpit SaaS
              </Link>
              <Link
                href="/pro/actualites"
                className="flex-1 text-center px-4 py-2.5 rounded-xl bg-emeraldSurface text-ivoryWarm text-xs font-semibold border border-goldAntique/40 hover:bg-emeraldMid transition"
              >
                Veille Pro Conseillers
              </Link>
            </div>
            <p className="text-[11px] text-ivoryWarm/70 flex items-center gap-1.5">
              <ShieldCheck className="w-3.5 h-3.5 text-goldChampagne shrink-0" />
              Données d&apos;agences et notes privées strictement isolées du site public.
            </p>
          </div>
        </section>
      </main>

      <PublicWebFooter />
    </div>
  );
}
