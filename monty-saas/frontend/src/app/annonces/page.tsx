import React from "react";
/* Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code. */
import type { Metadata } from "next";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { MarketplaceAndPublishView } from "@/components/marketplace/MarketplaceAndPublishView";
import { SponsoredBrandSlot } from "@/components/ads/SponsoredBrandSlot";

export const metadata: Metadata = {
  title: "Annonces immobilières & recherche intelligente | MON-AGENT",
  description:
    "Recherchez des biens immobiliers de particuliers et d'agences en France et au Portugal. Comparez les biens, explorez la carte et filtrez selon votre budget, vos critères et vos temps de trajet.",
  alternates: { canonical: "/annonces" },
};

export default function PublicListingsPage() {
  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="annonces" />
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
        <SponsoredBrandSlot placement="SEARCH" countryCode="FR" />
        <MarketplaceAndPublishView
          initialTab="search"
          initialCountry="FR"
          showTabs={true}
          audienceLabel="MON-AGENT • Acheteurs & Locataires"
        />
      </main>
      <PublicWebFooter />
    </div>
  );
}
