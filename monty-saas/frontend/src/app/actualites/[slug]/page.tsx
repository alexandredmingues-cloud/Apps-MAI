import React from "react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, Calculator, CheckCircle2, ExternalLink, ShieldAlert } from "lucide-react";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { CATEGORY_LABELS, getPublicArticleBySlugCatalog } from "@/lib/news-catalog";

interface PageProps {
  params: Promise<{ slug: string }>;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { slug } = await params;
  const article = getPublicArticleBySlugCatalog(slug);
  if (!article) {
    return { title: "Actualité introuvable | MON-AGENT" };
  }
  return {
    title: article.seo_meta_title || article.title,
    description: article.seo_meta_description || article.public_summary.slice(0, 160),
    openGraph: {
      title: article.seo_meta_title || article.title,
      description: article.seo_meta_description || article.public_summary.slice(0, 160),
      type: "article",
      publishedTime: article.published_at,
    },
  };
}

export default async function PublicArticleDetailPage({ params }: PageProps) {
  const { slug } = await params;
  const article = getPublicArticleBySlugCatalog(slug);
  if (!article) {
    notFound();
  }

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "NewsArticle",
    headline: article.title,
    datePublished: article.published_at,
    inLanguage: article.locale,
    publisher: {
      "@type": "Organization",
      name: "MON-AGENT",
    },
    citation: article.source_url,
  };

  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="actualites" />
      <main className="flex-1 max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full space-y-6">
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />

        <Link
          href="/actualites"
          className="inline-flex items-center gap-1.5 text-sm font-semibold text-emeraldDeep hover:underline"
        >
          <ArrowLeft className="w-4 h-4" />
          Retour au centre d&apos;actualités
        </Link>

        <article className="rounded-2xl bg-ivoryCard border border-borderSubtle p-6 sm:p-8 space-y-6">
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="px-3 py-1 rounded-full bg-emeraldDeep text-goldChampagne font-semibold">
              {article.country_code} • {CATEGORY_LABELS[article.category].fr}
            </span>
            <span className="text-slateMuted">
              {article.city ? `${article.city} (${article.region_name})` : article.region_name}
            </span>
            <span className="text-slateMuted">• Publié le {article.published_at}</span>
          </div>

          <h1 className="font-display text-2xl sm:text-4xl font-bold text-obsidianInk leading-tight">
            {article.title}
          </h1>

          <div className="rounded-xl bg-ivoryWarm border border-borderSubtle p-5 space-y-3">
            <p className="text-xs font-bold uppercase tracking-wider text-emeraldDeep">
              Ce qu&apos;il faut comprendre (Espace Particulier)
            </p>
            <p className="text-base text-obsidianInk leading-relaxed">{article.public_summary}</p>
          </div>

          {article.public_takeaways.length > 0 && (
            <div className="space-y-3">
              <h2 className="font-display text-lg font-bold text-emeraldDeep">
                Points clés pratiques pour votre projet
              </h2>
              <ul className="space-y-2 text-sm text-obsidianInk">
                {article.public_takeaways.map((tk, idx) => (
                  <li key={idx} className="flex items-start gap-2">
                    <CheckCircle2 className="w-4 h-4 text-emeraldDeep shrink-0 mt-0.5" />
                    <span>{tk}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="rounded-xl bg-emeraldDeep text-ivoryWarm p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <p className="font-display font-bold text-base text-goldChampagne">
                Chiffrez l&apos;impact sur votre projet immobilier
              </p>
              <p className="text-xs text-ivoryWarm/80">
                Mensualités, capacité HCSF / DSTI, frais de notaire / IMT, rendement locatif ou plus-value.
              </p>
            </div>
            <Link
              href="/simulateurs"
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-goldAntique text-emeraldDeep text-xs font-bold hover:bg-goldChampagne shrink-0"
            >
              <Calculator className="w-4 h-4" />
              Ouvrir les simulateurs
            </Link>
          </div>

          <div className="pt-4 border-t border-borderSubtle space-y-2 text-xs">
            <a
              href={article.source_url}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 font-semibold text-emeraldDeep hover:underline"
            >
              Source officielle vérifiée : {article.source_name} ({article.regulation_jurisdiction})
              <ExternalLink className="w-3.5 h-3.5" />
            </a>
            <p className="text-slateMuted flex items-center gap-1.5">
              <ShieldAlert className="w-4 h-4 shrink-0" />
              {article.legal_disclaimer}
            </p>
          </div>
        </article>
      </main>
      <PublicWebFooter />
    </div>
  );
}
