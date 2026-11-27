import React from "react";
/* Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code. */
import type { Metadata } from "next";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { NewsHubExperience } from "@/components/news/NewsHubExperience";
import { SponsoredBrandSlot } from "@/components/ads/SponsoredBrandSlot";

export const metadata: Metadata = {
  title: "Actualités immobilières (France & Portugal) | MON-AGENT",
  description:
    "Suivez les taux immobiliers, les tendances prix et délais par région, les nouvelles lois (DPE, Loi Le Meur, PTZ, IMT Portugal) avec des sources vérifiées.",
};

export default function PublicNewsPage() {
  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="actualites" />
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
        <SponsoredBrandSlot placement="CONTENT" countryCode="FR" />
        <NewsHubExperience audience="public" initialCountry="FR" />
      </main>
      <PublicWebFooter />
    </div>
  );
}
