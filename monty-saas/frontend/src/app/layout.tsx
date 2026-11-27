import type { Metadata } from "next";
/* Copyright © 2024–2026 MON-AGENT. All rights reserved for original project code. */
import "./globals.css";

export const metadata: Metadata = {
  title: "MON-AGENT — Plateforme immobilière intelligente",
  description:
    "L'humain avant tout. La technologie au service de l'immobilier.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fr">
      <head>
        <link rel="manifest" href="/manifest.webmanifest" />
        <link rel="icon" href="/icon-mon-agent.svg" type="image/svg+xml" />
        <meta name="theme-color" content="#0B3B2E" />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap"
        />
      </head>
      <body className="font-sans antialiased bg-ivoryWarm text-obsidianInk">
        {children}
        <footer className="border-t border-borderSubtle bg-ivoryCard px-4 py-4 text-center text-[11px] text-slateMuted">
          MON-AGENT · Copyright © 2024–2026. Tous droits réservés sur les éléments originaux. Les composants et données tiers restent soumis à leurs licences respectives.
          <a href="/publicite" className="ml-2 font-semibold text-emeraldDeep underline underline-offset-2">Devenir annonceur</a>
        </footer>
      </body>
    </html>
  );
}
