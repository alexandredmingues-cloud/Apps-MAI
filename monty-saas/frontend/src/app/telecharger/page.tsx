"use client";

import React from "react";
import { PublicWebFooter, PublicWebHeader } from "@/components/layout/PublicWebHeader";
import { ApkDownloadExperience } from "@/components/mobile/ApkDownloadExperience";

export default function TelechargerApplicationPage() {
  return (
    <div className="min-h-screen flex flex-col bg-ivoryWarm text-obsidianInk">
      <PublicWebHeader activePath="telecharger" />
      <main className="flex-1">
        <ApkDownloadExperience />
      </main>
      <PublicWebFooter />
    </div>
  );
}
