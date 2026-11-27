"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";

declare global {
  interface Window {
    turnstile?: {
      render: (container: HTMLElement, options: {
        sitekey: string;
        callback: (token: string) => void;
        "expired-callback"?: () => void;
        "error-callback"?: () => void;
        theme?: "light" | "dark" | "auto";
      }) => string;
      remove: (widgetId: string) => void;
      reset: (widgetId: string) => void;
    };
  }
}

const SITE_KEY = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY ?? "";

export function TurnstileWidget({ onVerify, resetKey = 0 }: { onVerify: (token: string) => void; resetKey?: number }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const widgetIdRef = useRef<string | null>(null);
  const [scriptReady, setScriptReady] = useState(false);

  useEffect(() => {
    if (!scriptReady || !SITE_KEY || !containerRef.current || !window.turnstile) return;
    if (widgetIdRef.current) window.turnstile.remove(widgetIdRef.current);
    widgetIdRef.current = window.turnstile.render(containerRef.current, {
      sitekey: SITE_KEY,
      theme: "light",
      callback: onVerify,
      "expired-callback": () => onVerify(""),
      "error-callback": () => onVerify(""),
    });
    return () => {
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current);
        widgetIdRef.current = null;
      }
    };
  }, [scriptReady, onVerify, resetKey]);

  if (!SITE_KEY) {
    return <p role="status" className="text-xs text-amber-700">Protection anti-robot en attente de configuration.</p>;
  }

  return (
    <div className="space-y-1">
      <Script
        src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
        strategy="afterInteractive"
        onLoad={() => setScriptReady(true)}
      />
      <div ref={containerRef} aria-label="Vérification anti-robot" />
      <p className="text-[10px] text-slateMuted">Protection anti-robot Cloudflare Turnstile.</p>
    </div>
  );
}
