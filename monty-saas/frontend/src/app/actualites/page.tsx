import React from "react";
import type { Metadata } from "next";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { NewsHubExperience } from "@/components/news/NewsHubExperience";

export const metadata: Metadata = {
  title: "Actualités Immobilières (France & Portugal) | Monty Particulier",
  description:
    "Suivez les taux immobiliers, les tendances prix et délais par région, les nouvelles lois (DPE, Loi Le Meur, PTZ, IMT Portugal) avec des sources vérifiées.",
};

export default function PublicNewsPage() {
  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="actualites" />
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
        <NewsHubExperience audience="public" initialCountry="FR" />
      </main>
      <PublicWebFooter />
    </div>
  );
}
