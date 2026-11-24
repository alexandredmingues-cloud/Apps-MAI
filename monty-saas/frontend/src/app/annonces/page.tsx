import React from "react";
import type { Metadata } from "next";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { MarketplaceAndPublishView } from "@/components/marketplace/MarketplaceAndPublishView";

export const metadata: Metadata = {
  title: "Annonces & Recherche par Temps de Trajet | Monty Immobilier",
  description:
    "Recherchez des biens immobiliers (particuliers & agences) en France et au Portugal selon votre temps de trajet réel en train, tramway, bus, vélo, à pied ou en voiture, avec comparateur DVF et délai estimé.",
};

export default function PublicListingsPage() {
  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="annonces" />
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
        <MarketplaceAndPublishView
          initialTab="search"
          initialCountry="FR"
          showTabs={true}
          audienceLabel="Site Public • Acheteurs & Locataires"
        />
      </main>
      <PublicWebFooter />
    </div>
  );
}
