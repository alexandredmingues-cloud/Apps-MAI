import React from "react";
import type { Metadata } from "next";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { MarketplaceAndPublishView } from "@/components/marketplace/MarketplaceAndPublishView";

export const metadata: Metadata = {
  title: "Vendre un bien & publier une annonce | MON-AGENT",
  description:
    "Préparez et publiez votre annonce immobilière avec une aide à la rédaction, des repères de prix et un parcours vendeur conçu pour protéger vos données personnelles.",
  alternates: { canonical: "/publier" },
};

export default function PublicPublishListingPage() {
  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="publier" />
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
        <MarketplaceAndPublishView
          initialTab="publish"
          initialCountry="FR"
          showTabs={true}
          audienceLabel="MON-AGENT • Espace Vendeurs"
        />
      </main>
      <PublicWebFooter />
    </div>
  );
}
