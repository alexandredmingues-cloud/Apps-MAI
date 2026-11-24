import React from "react";
import type { Metadata } from "next";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { MarketplaceAndPublishView } from "@/components/marketplace/MarketplaceAndPublishView";

export const metadata: Metadata = {
  title: "Vendre & Publier une Annonce Gratuite | Monty Immobilier",
  description:
    "Publiez votre bien immobilier (particulier ou agence) avec aide intelligente à la rédaction sans fuite de données personnelles, prix au m² et estimation du délai probable de vente.",
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
          audienceLabel="Espace Vendeurs • Particuliers & Agences"
        />
      </main>
      <PublicWebFooter />
    </div>
  );
}
