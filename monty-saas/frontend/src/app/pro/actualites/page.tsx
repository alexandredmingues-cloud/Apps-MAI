"use client";

import React, { useState } from "react";
import { MontyShell } from "@/components/layout/MontyShell";
import { NewsHubExperience } from "@/components/news/NewsHubExperience";
import type { MontyRole, NavSectionKey } from "@/lib/types";

export default function ProNewsPage() {
  const [activeSection, setActiveSection] = useState<NavSectionKey>("actualites");
  const [role] = useState<MontyRole>("DIRECTOR");

  return (
    <MontyShell
      activeSection={activeSection}
      onSelectSection={setActiveSection}
      agencyId="agency-france-reseau"
      role={role}
    >
      <NewsHubExperience audience="pro" role={role} initialCountry="FR" />
    </MontyShell>
  );
}
