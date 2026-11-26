"use client";

import React from "react";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { ClientPortalView } from "@/components/portal/ClientPortalView";

export default function MonComptePage() {
  return (
    <div className="min-h-screen bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="mon-compte" />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <ClientPortalView />
      </main>
      <PublicWebFooter />
    </div>
  );
}
