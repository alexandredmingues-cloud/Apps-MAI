import React from "react";
import type { Metadata } from "next";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { RealEstateSimulatorsSuite } from "@/components/calculators/RealEstateSimulatorsSuite";

export const metadata: Metadata = {
  title: "Simulateurs immobiliers gratuits (France & Portugal) | MON-AGENT",
  description:
    "Calculez vos mensualités, capacité d'emprunt HCSF 35 %, frais de notaire ou IMT Portugal, rendement locatif, estimation indicative et plus-value immobilière.",
};

export default function PublicSimulatorsPage() {
  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="simulateurs" />
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
        <RealEstateSimulatorsSuite initialCountry="FR" />
      </main>
      <PublicWebFooter />
    </div>
  );
}
