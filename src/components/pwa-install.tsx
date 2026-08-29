"use client";

import { useEffect, useState } from "react";
import { DownloadIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { t } from "@/i18n";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

/**
 * Registers the service worker in production builds and shows a one-time
 * banner when the browser offers an install prompt (Chromium on Android).
 * Other browsers install through their own menu ("Añadir a pantalla de
 * inicio"), so no banner is shown for them.
 */
export function PwaInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (process.env.NODE_ENV !== "production") return;
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => {
        // Service workers are an enhancement; ignore registration failures.
      });
    }

    const isStandalone = window.matchMedia("(display-mode: standalone)").matches;
    if (isStandalone) return;

    function onPrompt(event: Event) {
      event.preventDefault();
      setDeferredPrompt(event as BeforeInstallPromptEvent);
    }

    function onInstalled() {
      setDeferredPrompt(null);
    }

    window.addEventListener("beforeinstallprompt", onPrompt);
    window.addEventListener("appinstalled", onInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onPrompt);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);

  if (!deferredPrompt || dismissed) return null;

  async function handleInstall() {
    const promptEvent = deferredPrompt;
    if (!promptEvent) return;
    await promptEvent.prompt();
    const choice = await promptEvent.userChoice;
    if (choice.outcome === "accepted") {
      setDeferredPrompt(null);
    } else {
      setDismissed(true);
    }
  }

  return (
    <div className="border-b bg-primary/5 px-4 py-2">
      <div className="mx-auto flex w-full max-w-2xl items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-xs text-muted-foreground">
          <DownloadIcon className="size-4 shrink-0 text-primary" />
          <span>{t.install.banner}</span>
        </p>
        <div className="flex shrink-0 items-center gap-1">
          <Button variant="outline" size="sm" onClick={handleInstall}>
            {t.install.action}
          </Button>
          <button
            type="button"
            aria-label={t.common.close}
            onClick={() => setDismissed(true)}
            className="rounded p-1 text-muted-foreground transition-colors hover:text-foreground"
          >
            <XIcon className="size-4" />
          </button>
        </div>
      </div>
    </div>
  );
}