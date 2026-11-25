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
      <body className="font-sans antialiased bg-ivoryWarm text-obsidianInk">
        {children}
      </body>
    </html>
  );
}
