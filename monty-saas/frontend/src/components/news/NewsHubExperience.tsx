"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  Archive,
  Bell,
  Bookmark,
  Briefcase,
  CheckCircle2,
  ExternalLink,
  Filter,
  Globe2,
  PlusCircle,
  Search,
  ShieldAlert,
  Sparkles,
  UserCheck,
} from "lucide-react";
import { hasInMemoryAccessToken, montyApi } from "@/lib/api-client";
import { COUNTRY_PACKS, UI_I18N } from "@/lib/country-packs";
import {
  CATEGORY_LABELS,
  SEEDED_ARTICLES,
  SEEDED_SOURCES,
  getPublicArticlesCatalog,
} from "@/lib/news-catalog";
import type {
  AudienceScope,
  CountryCode,
  DualBriefingResponse,
  LocaleCode,
  MontyRole,
  NewsCategory,
  NewsSource,
  ProNewsArticle,
  PublicNewsArticle,
} from "@/lib/types";

interface NewsHubExperienceProps {
  audience: "public" | "pro";
  role?: MontyRole;
  initialCountry?: CountryCode;
}

const ALL_CATEGORIES: NewsCategory[] = [
  "TAUX_CREDIT",
  "MARCHE_LOCAL",
  "LOIS_FISCALITE",
  "DPE_TRAVAUX",
  "LOCATION_COPRO",
  "INVESTISSEMENT",
  "METIER_CONSEILLER",
];

export function NewsHubExperience({
  audience,
  role = "BUYER",
  initialCountry = "FR",
}: NewsHubExperienceProps) {
  const [country, setCountry] = useState<CountryCode>(initialCountry);
  const [locale, setLocale] = useState<LocaleCode>(initialCountry === "PT" ? "pt-PT" : "fr-FR");
  const [selectedRegion, setSelectedRegion] = useState<string>("National");
  const [cityFilter, setCityFilter] = useState<string>("");
  const [selectedCategory, setSelectedCategory] = useState<NewsCategory | "ALL">("ALL");
  const [query, setQuery] = useState<string>("");
  const [includeArchived, setIncludeArchived] = useState<boolean>(false);

  // Personnalisation & Consentement RGPD
  const [interests, setInterests] = useState<NewsCategory[]>([
    "TAUX_CREDIT",
    "MARCHE_LOCAL",
    "LOIS_FISCALITE",
    "DPE_TRAVAUX",
  ]);
  const [rgpdConsent, setRgpdConsent] = useState<boolean>(false);
  const [alertsEnabled, setAlertsEnabled] = useState<boolean>(false);
  const [prefStatusMsg, setPrefStatusMsg] = useState<string>("");
  const [bookmarks, setBookmarks] = useState<string[]>([]);

  // Vue double format pour les Pros
  const [proViewMode, setProViewMode] = useState<"pro" | "public">("pro");
  const [articles, setArticles] = useState<Array<PublicNewsArticle | ProNewsArticle>>(() =>
    audience === "pro"
      ? SEEDED_ARTICLES.filter((a) => a.country_code === initialCountry && a.status === "PUBLISHED")
      : getPublicArticlesCatalog(initialCountry, false)
  );
  const [sources, setSources] = useState<NewsSource[]>(() =>
    SEEDED_SOURCES.filter((s) => s.country_code === initialCountry)
  );
  const [dataOrigin, setDataOrigin] = useState<"API FastAPI" | "Catalogue Local">("Catalogue Local");

  // Générateur IA Double Format (Pro)
  const [aiTopic, setAiTopic] = useState("Évolution des taux immobiliers et DPE 2026");
  const [aiExcerpt, setAiExcerpt] = useState(
    "Les taux moyens sur 20 ans se stabilisent autour de 3,25 %, tandis que le calendrier DPE interdit la remise en location des logements classés G."
  );
  const [aiBrief, setAiBrief] = useState<DualBriefingResponse | null>(null);
  const [aiLoading, setAiLoading] = useState(false);

  // Admin Sources & Publication (uniquement DIRECTOR / ADMIN / SUPER_ADMIN)
  const canAdminNews =
    audience === "pro" && (role === "SUPER_ADMIN" || role === "DIRECTOR" || role === "ADMIN");
  const [newTitle, setNewTitle] = useState("");
  const [newSummary, setNewSummary] = useState("");
  const [newProAnalysis, setNewProAnalysis] = useState("");
  const [newCategory, setNewCategory] = useState<NewsCategory>("LOIS_FISCALITE");
  const [newScope, setNewScope] = useState<AudienceScope>("BOTH");
  const [adminFeedback, setAdminFeedback] = useState("");

  const pack = COUNTRY_PACKS[country];
  const t = UI_I18N[locale];

  function handleCountrySwitch(nextCountry: CountryCode) {
    setCountry(nextCountry);
    setLocale(nextCountry === "PT" ? "pt-PT" : "fr-FR");
    setSelectedRegion(nextCountry === "PT" ? "Nacional" : "National");
    setCityFilter("");
  }

  useEffect(() => {
    let active = true;
    async function load() {
      const fallbackSources = SEEDED_SOURCES.filter((s) => s.country_code === country);
      const fallbackArticles =
        audience === "pro"
          ? SEEDED_ARTICLES.filter(
              (a) =>
                a.country_code === country &&
                (a.status === "PUBLISHED" || (includeArchived && a.status === "ARCHIVED"))
            )
          : getPublicArticlesCatalog(country, includeArchived);

      try {
        const [srcRes, artRes] = await Promise.all([
          montyApi.listPublicSources(country),
          audience === "pro" && hasInMemoryAccessToken()
            ? montyApi.listProNews({
                country_code: country,
                category: selectedCategory === "ALL" ? undefined : selectedCategory,
                region_name: selectedRegion,
                city: cityFilter,
                query,
                include_archived: includeArchived,
              })
            : montyApi.listPublicNews({
                country_code: country,
                category: selectedCategory === "ALL" ? undefined : selectedCategory,
                region_name: selectedRegion,
                city: cityFilter,
                query,
                include_archived: includeArchived,
              }),
        ]);
        if (!active) return;
        setSources(srcRes.sources);
        setArticles(artRes.articles);
        setDataOrigin("API FastAPI");
      } catch {
        if (!active) return;
        setSources(fallbackSources);
        setArticles(fallbackArticles);
        setDataOrigin("Catalogue Local");
      }
    }
    load();
    return () => {
      active = false;
    };
  }, [audience, country, selectedCategory, selectedRegion, cityFilter, query, includeArchived]);

  const filteredArticles = useMemo(() => {
    return articles.filter((art) => {
      if (art.country_code !== country) return false;
      if (audience === "public" && art.category === "METIER_CONSEILLER") return false;
      if (selectedCategory !== "ALL" && art.category !== selectedCategory) return false;
      if (
        selectedRegion &&
        selectedRegion !== "National" &&
        selectedRegion !== "Nacional" &&
        art.region_name !== "National" &&
        art.region_name !== "Nacional" &&
        !art.region_name.toLowerCase().includes(selectedRegion.toLowerCase())
      ) {
        return false;
      }
      if (cityFilter.trim() && art.city && !art.city.toLowerCase().includes(cityFilter.trim().toLowerCase())) {
        return false;
      }
      if (query.trim()) {
        const q = query.trim().toLowerCase();
        const hay = `${art.title} ${art.public_summary} ${art.city} ${art.region_name}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [articles, audience, country, selectedCategory, selectedRegion, cityFilter, query]);

  function toggleInterest(cat: NewsCategory) {
    setInterests((prev) => (prev.includes(cat) ? prev.filter((c) => c !== cat) : [...prev, cat]));
  }

  function toggleBookmark(id: string) {
    setBookmarks((prev) => (prev.includes(id) ? prev.filter((b) => b !== id) : [...prev, id]));
  }

  async function savePreferences() {
    if (alertsEnabled && !rgpdConsent) {
      setPrefStatusMsg("RGPD Art. 6.1.a : cochez d'abord votre consentement explicite pour activer les alertes.");
      return;
    }
    try {
      if (hasInMemoryAccessToken()) {
        await montyApi.updatePreferences({
          country_code: country,
          locale,
          city: cityFilter,
          department_code: "",
          region_name: selectedRegion,
          interests,
          notifications_enabled: alertsEnabled,
          rgpd_consent_granted: rgpdConsent,
        });
      }
      setPrefStatusMsg(
        `Préférences enregistrées (${pack.name_fr} • ${selectedRegion}${cityFilter ? ` • ${cityFilter}` : ""}).`
      );
    } catch (err) {
      setPrefStatusMsg(err instanceof Error ? err.message : "Erreur d'enregistrement");
    }
  }

  async function handleGenerateDualBrief() {
    setAiLoading(true);
    try {
      const res = await montyApi.generateDualBrief({
        topic: aiTopic,
        source_excerpt: aiExcerpt,
        location_label: `${pack.name_fr} — ${selectedRegion}`,
      });
      setAiBrief(res);
    } catch {
      setAiBrief({
        mode: "deterministic_fallback",
        model_used: "Synthèse déterministe (connectez une session Pro API pour OpenAI)",
        sanitized_topic: aiTopic,
        pro_analysis: `[Analyse Pro ${pack.name_fr}] ${aiTopic} — ${aiExcerpt}. Vérifier l'impact financement (${pack.credit_regulator}) et prix locaux avant entretien mandat.`,
        pro_commercial_arguments: [
          "Chiffrer l'impact budgétaire avec le simulateur Monty correspondant.",
          "Citer exclusivement des sources officielles datées.",
        ],
        public_summary: `En clair (${pack.name_fr}) : ${aiExcerpt}`,
        public_takeaways: [
          "Vérifiez la date et la source officielle avant toute décision.",
          "Testez votre scénario dans nos simulateurs gratuits.",
        ],
        legal_disclaimer: t.legalNote,
      });
    } finally {
      setAiLoading(false);
    }
  }

  async function handleAdminPublish(e: React.FormEvent) {
    e.preventDefault();
    setAdminFeedback("");
    if (!newTitle.trim() || !newSummary.trim()) {
      setAdminFeedback("Titre et résumé public obligatoires.");
      return;
    }
    const defaultSource = sources[0];
    if (!defaultSource) return;

    try {
      if (hasInMemoryAccessToken()) {
        const created = await montyApi.adminCreateArticle({
          source_id: defaultSource.id,
          source_url: defaultSource.base_url,
          country_code: country,
          category: newCategory,
          audience_scope: newScope,
          title: newTitle,
          region_name: selectedRegion,
          city: cityFilter,
          public_summary: newSummary,
          public_takeaways: ["Source vérifiée rattachée à l'article."],
          pro_analysis: newProAnalysis || newSummary,
          pro_commercial_arguments: ["Utiliser cet élément sourcé en rendez-vous client."],
        });
        setArticles((prev) => [created, ...prev]);
        setAdminFeedback("Article publié via FastAPI RBAC (NEWS_ADMIN).");
      } else {
        setAdminFeedback("Connectez une session API DIRECTOR/ADMIN dans le Cockpit pour persister côté serveur.");
      }
      setNewTitle("");
      setNewSummary("");
      setNewProAnalysis("");
    } catch (err) {
      setAdminFeedback(err instanceof Error ? err.message : "Erreur de publication");
    }
  }

  const visibleCategories = audience === "public" ? ALL_CATEGORIES.filter((c) => c !== "METIER_CONSEILLER") : ALL_CATEGORIES;

  return (
    <div className="space-y-6">
      {/* Bannière Multi-Pays & Double Public */}
      <section className="rounded-2xl bg-gradient-to-r from-emeraldDeep to-emeraldMid text-ivoryWarm p-6 border border-goldAntique/40">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-widest text-goldChampagne flex items-center gap-2">
              {audience === "pro" ? <Briefcase className="w-4 h-4" /> : <UserCheck className="w-4 h-4" />}
              {audience === "pro" ? t.proSpace : t.publicSpace} • {pack.rollout_priority}
            </p>
            <h2 className="mt-1 font-display text-2xl sm:text-3xl font-bold">
              {audience === "pro"
                ? "Veille Stratégique Pro & Synthèse Double Public"
                : "Centre d'Information Immobilier & Guides Pratiques"}
            </h2>
            <p className="mt-1 text-xs sm:text-sm text-ivoryWarm/85">{t.jurisdictionNotice}</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {(["FR", "PT"] as CountryCode[]).map((code) => (
              <button
                key={code}
                type="button"
                onClick={() => handleCountrySwitch(code)}
                className={`px-3 py-2 rounded-xl text-xs font-semibold border transition ${
                  country === code
                    ? "bg-goldAntique text-emeraldDeep border-goldChampagne"
                    : "bg-emeraldSurface/80 text-ivoryWarm border-goldAntique/30 hover:bg-emeraldMid"
                }`}
              >
                {code === "FR" ? "🇫🇷 France entière" : "🇵🇹 Portugal (Isolé)"}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setLocale((l) => (l === "fr-FR" ? "pt-PT" : "fr-FR"))}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-emeraldSurface text-xs font-semibold border border-goldAntique/30"
            >
              <Globe2 className="w-3.5 h-3.5 text-goldChampagne" />
              Langue : {locale === "fr-FR" ? "FR" : "PT"}
            </button>
            <span className="px-2.5 py-1.5 rounded-lg bg-emeraldDeep/70 text-[11px] text-goldChampagne border border-goldAntique/30">
              {dataOrigin}
            </span>
          </div>
        </div>
      </section>

      {/* Filtres Géographiques, Thématiques & Personnalisation RGPD */}
      <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
          <h3 className="font-display text-base font-bold text-emeraldDeep flex items-center gap-2">
            <Filter className="w-4 h-4 text-goldAntique" />
            Personnalisation territoriale &amp; thématique ({pack.name_fr})
          </h3>
          <div className="flex flex-wrap items-center gap-3 text-xs">
            <label className="inline-flex items-center gap-1.5 font-semibold text-slateMuted">
              <input
                type="checkbox"
                checked={includeArchived}
                onChange={(e) => setIncludeArchived(e.target.checked)}
              />
              <Archive className="w-3.5 h-3.5" />
              Inclure l&apos;historique (archives)
            </label>
            {audience === "pro" && (
              <div className="inline-flex rounded-xl border border-borderSubtle overflow-hidden">
                <button
                  type="button"
                  onClick={() => setProViewMode("pro")}
                  className={`px-3 py-1.5 font-semibold ${proViewMode === "pro" ? "bg-emeraldDeep text-goldChampagne" : "bg-ivoryWarm text-slateMuted"}`}
                >
                  Analyse Pro
                </button>
                <button
                  type="button"
                  onClick={() => setProViewMode("public")}
                  className={`px-3 py-1.5 font-semibold ${proViewMode === "public" ? "bg-emeraldDeep text-goldChampagne" : "bg-ivoryWarm text-slateMuted"}`}
                >
                  Vue Particulier
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-sm">
          <label className="block">
            <span className="text-xs font-semibold text-slateMuted">Région ({pack.country_code})</span>
            <select
              value={selectedRegion}
              onChange={(e) => setSelectedRegion(e.target.value)}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            >
              {pack.regions.map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-semibold text-slateMuted">Ville</span>
            <input
              type="text"
              placeholder={pack.major_cities.slice(0, 3).join(", ") + "..."}
              value={cityFilter}
              onChange={(e) => setCityFilter(e.target.value)}
              className="mt-1 w-full rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
            />
          </label>
          <label className="block sm:col-span-2">
            <span className="text-xs font-semibold text-slateMuted">Recherche mot-clé</span>
            <div className="relative mt-1">
              <Search className="w-4 h-4 text-slateMuted absolute left-3 top-2.5" />
              <input
                type="text"
                placeholder="Ex: DPE, taux, HCSF, IMT, notaire..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="w-full rounded-xl border border-borderSubtle bg-ivoryWarm pl-9 pr-3 py-2"
              />
            </div>
          </label>
        </div>

        {/* Catégories */}
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setSelectedCategory("ALL")}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${
              selectedCategory === "ALL"
                ? "bg-emeraldDeep text-goldChampagne border-emeraldDeep"
                : "bg-ivoryWarm text-obsidianInk border-borderSubtle"
            }`}
          >
            Toutes ({filteredArticles.length})
          </button>
          {visibleCategories.map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setSelectedCategory(cat)}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${
                selectedCategory === cat
                  ? "bg-emeraldDeep text-goldChampagne border-emeraldDeep"
                  : "bg-ivoryWarm text-obsidianInk border-borderSubtle"
              }`}
            >
              {locale === "pt-PT" ? CATEGORY_LABELS[cat].pt : CATEGORY_LABELS[cat].fr}
            </button>
          ))}
        </div>

        {/* Préférences & Consentement RGPD */}
        <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-3 text-xs">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-semibold text-emeraldDeep flex items-center gap-1.5">
              <Bell className="w-4 h-4 text-goldAntique" />
              Mes centres d&apos;intérêt &amp; alertes ciblées (RGPD Art. 6.1.a)
            </span>
            <span className="text-slateMuted">{bookmarks.length} favori(s)</span>
          </div>
          <div className="flex flex-wrap gap-2">
            {visibleCategories.map((cat) => {
              const active = interests.includes(cat);
              return (
                <button
                  key={cat}
                  type="button"
                  onClick={() => toggleInterest(cat)}
                  className={`px-2.5 py-1 rounded-lg border text-[11px] font-semibold ${
                    active ? "bg-emeraldDeep/10 border-emeraldDeep text-emeraldDeep" : "bg-ivoryCard border-borderSubtle text-slateMuted"
                  }`}
                >
                  {active ? "✓ " : "+ "}
                  {locale === "pt-PT" ? CATEGORY_LABELS[cat].pt : CATEGORY_LABELS[cat].fr}
                </button>
              );
            })}
          </div>
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-1">
            <div className="flex flex-wrap items-center gap-4">
              <label className="inline-flex items-center gap-2 font-medium text-obsidianInk">
                <input type="checkbox" checked={rgpdConsent} onChange={(e) => setRgpdConsent(e.target.checked)} />
                J&apos;accepte de recevoir les alertes immobilières Monty (consentement révocable à tout moment)
              </label>
              <label className="inline-flex items-center gap-2 font-semibold text-emeraldDeep">
                <input type="checkbox" checked={alertsEnabled} onChange={(e) => setAlertsEnabled(e.target.checked)} />
                Activer les notifications
              </label>
            </div>
            <button
              type="button"
              onClick={savePreferences}
              className="px-3.5 py-1.5 rounded-lg bg-emeraldDeep text-ivoryWarm font-semibold hover:bg-emeraldMid shrink-0"
            >
              Enregistrer mes préférences
            </button>
          </div>
          {prefStatusMsg && <p className="text-emeraldDeep font-semibold">{prefStatusMsg}</p>}
        </div>
      </section>

      {/* Liste des Articles Sourcés */}
      <section className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {filteredArticles.map((art) => {
          const isProArt = "pro_analysis" in art;
          const isSaved = bookmarks.includes(art.id);
          return (
            <article
              key={art.id}
              className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 flex flex-col justify-between space-y-4 hover:border-emeraldMid transition"
            >
              <div className="space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                  <span className="px-2.5 py-1 rounded-full bg-emeraldDeep/10 text-emeraldDeep font-semibold">
                    {art.country_code} • {locale === "pt-PT" ? CATEGORY_LABELS[art.category].pt : CATEGORY_LABELS[art.category].fr}
                  </span>
                  <div className="flex items-center gap-2 text-slateMuted">
                    <span>{art.city ? `${art.city} (${art.region_name})` : art.region_name}</span>
                    <span>•</span>
                    <time dateTime={art.published_at}>{art.published_at}</time>
                    {art.status === "ARCHIVED" && (
                      <span className="px-2 py-0.5 rounded bg-amber-100 text-amber-800 font-semibold">Archive</span>
                    )}
                  </div>
                </div>

                <h3 className="font-display text-lg font-bold text-obsidianInk leading-snug">
                  {art.title}
                </h3>

                {audience === "pro" && proViewMode === "pro" && isProArt ? (
                  <div className="space-y-3">
                    <p className="text-sm text-obsidianInk/90 leading-relaxed">{art.pro_analysis}</p>
                    {Object.keys(art.pro_key_metrics).length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {Object.entries(art.pro_key_metrics).map(([k, v]) => (
                          <span key={k} className="px-2.5 py-1 rounded-lg bg-emeraldDeep text-ivoryWarm text-xs font-semibold">
                            {k} : <span className="text-goldChampagne">{v}</span>
                          </span>
                        ))}
                      </div>
                    )}
                    {art.pro_commercial_arguments.length > 0 && (
                      <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-3 text-xs space-y-1">
                        <p className="font-bold text-emeraldDeep">Arguments commerciaux conseillers :</p>
                        <ul className="list-disc pl-4 space-y-1 text-slateMuted">
                          {art.pro_commercial_arguments.map((arg, i) => (
                            <li key={i}>{arg}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="space-y-3">
                    <p className="text-sm text-obsidianInk/90 leading-relaxed">{art.public_summary}</p>
                    {art.public_takeaways.length > 0 && (
                      <ul className="space-y-1 text-xs text-slateMuted">
                        {art.public_takeaways.map((tk, i) => (
                          <li key={i} className="flex items-start gap-1.5">
                            <CheckCircle2 className="w-3.5 h-3.5 text-emeraldDeep shrink-0 mt-0.5" />
                            <span>{tk}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
              </div>

              <div className="pt-3 border-t border-borderSubtle space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                  <a
                    href={art.source_url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 font-semibold text-emeraldDeep hover:underline"
                  >
                    Source : {art.source_name}
                    <ExternalLink className="w-3 h-3" />
                  </a>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => toggleBookmark(art.id)}
                      className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg border font-semibold ${
                        isSaved ? "bg-goldAntique/20 border-goldAntique text-emeraldDeep" : "border-borderSubtle text-slateMuted"
                      }`}
                    >
                      <Bookmark className="w-3.5 h-3.5" />
                      {isSaved ? "Enregistré" : "Favori"}
                    </button>
                    {art.status === "PUBLISHED" && (
                      <Link
                        href={`/actualites/${art.slug}`}
                        className="px-2.5 py-1 rounded-lg bg-emeraldDeep text-ivoryWarm font-semibold hover:bg-emeraldMid"
                      >
                        Fiche SEO
                      </Link>
                    )}
                  </div>
                </div>
                <p className="text-[11px] text-slateMuted flex items-center gap-1">
                  <ShieldAlert className="w-3 h-3 shrink-0" />
                  {art.legal_disclaimer}
                </p>
              </div>
            </article>
          );
        })}
      </section>

      {/* Outil IA Double Format (Espace Pro uniquement) */}
      {audience === "pro" && (
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <h3 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-goldAntique" />
            Générateur IA Double Format (Analyse Pro + Vulgarisation Particulier)
          </h3>
          <p className="text-xs text-slateMuted">
            Minimisation RGPD appliquée avant appel OpenAI serveur. Aucune donnée inventée ; avertissement juridique systématique.
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
            <input
              type="text"
              value={aiTopic}
              onChange={(e) => setAiTopic(e.target.value)}
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              placeholder="Sujet immobilier sourcé"
            />
            <input
              type="text"
              value={aiExcerpt}
              onChange={(e) => setAiExcerpt(e.target.value)}
              className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              placeholder="Faits & chiffres vérifiés issus de la source"
            />
          </div>
          <button
            type="button"
            disabled={aiLoading}
            onClick={handleGenerateDualBrief}
            className="px-4 py-2.5 rounded-xl bg-emeraldDeep text-ivoryWarm text-sm font-semibold hover:bg-emeraldMid disabled:opacity-50"
          >
            {aiLoading ? "Synthèse en cours..." : "Générer les deux versions (Pro & Particulier)"}
          </button>

          {aiBrief && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
              <div className="rounded-xl bg-emeraldDeep text-ivoryWarm p-4 space-y-2 text-xs">
                <p className="font-bold text-goldChampagne">Format Conseiller Pro ({aiBrief.mode})</p>
                <p>{aiBrief.pro_analysis}</p>
                <ul className="list-disc pl-4 text-ivoryWarm/85">
                  {aiBrief.pro_commercial_arguments.map((a, i) => (
                    <li key={i}>{a}</li>
                  ))}
                </ul>
              </div>
              <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-4 space-y-2 text-xs">
                <p className="font-bold text-emeraldDeep">Format Vulgarisé Particulier</p>
                <p className="text-obsidianInk">{aiBrief.public_summary}</p>
                <ul className="list-disc pl-4 text-slateMuted">
                  {aiBrief.public_takeaways.map((a, i) => (
                    <li key={i}>{a}</li>
                  ))}
                </ul>
                <p className="text-[11px] text-slateMuted pt-1">{aiBrief.legal_disclaimer}</p>
              </div>
            </div>
          )}
        </section>
      )}

      {/* Administration des Sources & Publications (NEWS_ADMIN uniquement) */}
      {canAdminNews && (
        <section className="rounded-2xl bg-ivoryCard border border-borderSubtle p-5 space-y-4">
          <h3 className="font-display text-lg font-bold text-emeraldDeep flex items-center gap-2">
            <PlusCircle className="w-5 h-5 text-goldAntique" />
            Administration Éditoriale &amp; Sources Vérifiées ({pack.country_code})
          </h3>
          <form onSubmit={handleAdminPublish} className="space-y-3 text-sm">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <input
                type="text"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="Titre de l'actualité..."
                className="sm:col-span-2 rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
              <select
                value={newCategory}
                onChange={(e) => setNewCategory(e.target.value as NewsCategory)}
                className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              >
                {ALL_CATEGORIES.map((c) => (
                  <option key={c} value={c}>{CATEGORY_LABELS[c].fr}</option>
                ))}
              </select>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <textarea
                rows={2}
                value={newSummary}
                onChange={(e) => setNewSummary(e.target.value)}
                placeholder="Explication vulgarisée Particulier..."
                className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
              <textarea
                rows={2}
                value={newProAnalysis}
                onChange={(e) => setNewProAnalysis(e.target.value)}
                placeholder="Analyse approfondie Conseiller Pro..."
                className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2"
              />
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <select
                value={newScope}
                onChange={(e) => setNewScope(e.target.value as AudienceScope)}
                className="rounded-xl border border-borderSubtle bg-ivoryWarm px-3 py-2 text-xs font-semibold"
              >
                <option value="BOTH">Double public (Pro + Particulier)</option>
                <option value="PRO_ONLY">Espace Pro uniquement</option>
                <option value="PUBLIC_ONLY">Particulier uniquement</option>
              </select>
              <button
                type="submit"
                className="px-4 py-2 rounded-xl bg-emeraldDeep text-ivoryWarm font-semibold hover:bg-emeraldMid"
              >
                Publier (RBAC NEWS_ADMIN)
              </button>
            </div>
            {adminFeedback && <p className="text-xs font-semibold text-emeraldDeep">{adminFeedback}</p>}
          </form>
        </section>
      )}
    </div>
  );
}
