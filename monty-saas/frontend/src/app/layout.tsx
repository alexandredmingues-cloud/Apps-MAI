import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Monty SaaS — Système d'exploitation immobilier multi-agences",
  description:
    "L'humain avant tout. La technologie au service du conseiller immobilier.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fr">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap"
        />
      </head>
      <body className="font-sans antialiased bg-ivoryWarm text-obsidianInk">
        {children}
      </body>
    </html>
  );
}
