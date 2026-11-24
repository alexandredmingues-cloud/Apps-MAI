import React from "react";
import Link from "next/link";
import { Briefcase, Calculator, Globe2, Home, Newspaper, PlusCircle, ShieldCheck } from "lucide-react";

interface PublicWebHeaderProps {
  activePath: "site" | "annonces" | "publier" | "actualites" | "simulateurs";
}

export function PublicWebHeader({ activePath }: PublicWebHeaderProps) {
  return (
    <header className="sticky top-0 z-40 bg-emeraldDeep/95 backdrop-blur border-b border-goldAntique/30 text-ivoryWarm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        <Link href="/site" className="flex items-center gap-3 shrink-0">
          <div className="h-9 w-9 rounded-xl bg-goldAntique/20 border border-goldAntique/60 flex items-center justify-center text-goldChampagne font-display font-bold text-lg">
            M
          </div>
          <div>
            <p className="font-display font-bold text-base tracking-tight leading-none">
              MONTY IMMOBILIER
            </p>
            <p className="text-[11px] text-goldChampagne/90 mt-0.5">
              France entière • Portugal (Prêt)
            </p>
          </div>
        </Link>

        <nav className="hidden lg:flex items-center gap-1.5 text-sm font-medium">
          <Link
            href="/site"
            className={`px-3 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "site"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Globe2 className="w-4 h-4" />
            Accueil
          </Link>
          <Link
            href="/annonces"
            className={`px-3 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "annonces"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Home className="w-4 h-4" />
            Annonces &amp; Trajet
          </Link>
          <Link
            href="/publier"
            className={`px-3 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "publier"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <PlusCircle className="w-4 h-4" />
            Publier
          </Link>
          <Link
            href="/actualites"
            className={`px-3 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "actualites"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Newspaper className="w-4 h-4" />
            Actualités
          </Link>
          <Link
            href="/simulateurs"
            className={`px-3 py-2 rounded-xl transition inline-flex items-center gap-1.5 ${
              activePath === "simulateurs"
                ? "bg-emeraldSurface text-goldChampagne border border-goldAntique/40"
                : "text-ivoryWarm/85 hover:bg-emeraldMid"
            }`}
          >
            <Calculator className="w-4 h-4" />
            Simulateurs
          </Link>
        </nav>

        <div className="flex items-center gap-2">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-goldAntique text-emeraldDeep text-xs sm:text-sm font-bold hover:bg-goldChampagne transition"
          >
            <Briefcase className="w-4 h-4" />
            Espace Pro SaaS
          </Link>
        </div>
      </div>

      {/* Navigation mobile compacte */}
      <div className="lg:hidden px-4 pb-2.5 flex items-center gap-2 overflow-x-auto text-xs font-semibold whitespace-nowrap">
        <Link href="/site" className={`px-3 py-1.5 rounded-lg ${activePath === "site" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          Accueil
        </Link>
        <Link href="/annonces" className={`px-3 py-1.5 rounded-lg ${activePath === "annonces" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          Annonces &amp; Trajet
        </Link>
        <Link href="/publier" className={`px-3 py-1.5 rounded-lg ${activePath === "publier" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          Publier
        </Link>
        <Link href="/actualites" className={`px-3 py-1.5 rounded-lg ${activePath === "actualites" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          Actualités
        </Link>
        <Link href="/simulateurs" className={`px-3 py-1.5 rounded-lg ${activePath === "simulateurs" ? "bg-emeraldSurface text-goldChampagne" : "bg-emeraldMid/50"}`}>
          Simulateurs
        </Link>
      </div>
    </header>
  );
}

export function PublicWebFooter() {
  return (
    <footer className="bg-emeraldDeep text-ivoryWarm/85 border-t border-goldAntique/30 mt-14">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 grid grid-cols-1 md:grid-cols-3 gap-8 text-xs">
        <div className="space-y-2">
          <p className="font-display text-base font-bold text-ivoryWarm">MONTY IMMOBILIER</p>
          <p className="text-goldChampagne">« L&apos;humain avant tout. La technologie au service du conseiller immobilier. »</p>
          <p className="text-ivoryWarm/70">
            Architecture multi-pays et multilingue (France active • Portugal isolé • Europe extensible).
          </p>
        </div>
        <div className="space-y-2">
          <p className="font-bold text-ivoryWarm uppercase tracking-wider">Accès Rapide</p>
          <ul className="space-y-1.5">
            <li><Link href="/site" className="hover:text-goldChampagne">Accueil Site Web Public</Link></li>
            <li><Link href="/annonces" className="hover:text-goldChampagne">Annonces &amp; Recherche par Temps de Trajet</Link></li>
            <li><Link href="/publier" className="hover:text-goldChampagne">Publier un Bien (Particuliers &amp; Agences)</Link></li>
            <li><Link href="/actualites" className="hover:text-goldChampagne">Centre d&apos;Actualités Particuliers</Link></li>
            <li><Link href="/simulateurs" className="hover:text-goldChampagne">6 Simulateurs Immobiliers Gratuits</Link></li>
            <li><Link href="/" className="hover:text-goldChampagne">Cockpit Professionnel Agences (SaaS)</Link></li>
          </ul>
        </div>
        <div className="space-y-2">
          <p className="font-bold text-ivoryWarm uppercase tracking-wider flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-goldChampagne" />
            Conformité RGPD &amp; Sécurité
          </p>
          <p className="text-ivoryWarm/70 leading-relaxed">
            Aucune donnée privée d&apos;agence, de client, de mandat ou note interne n&apos;est accessible depuis l&apos;espace public.
            Les alertes nécessitent un consentement explicite révocable (Art. 6.1.a RGPD) et les estimations restent indicatives.
          </p>
        </div>
      </div>
    </footer>
  );
}
