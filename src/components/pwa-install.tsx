"use client";

import { useEffect, useState } from "react";
import { DownloadIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { t } from "@/i18n";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

/** iPhone/iPad (iPadOS reports itself as a Mac with touch). */
export function isIos(): boolean {
  const ua = navigator.userAgent;
  return /iPhone|iPad|iPod/.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
}

const IOS_DISMISSED_KEY = "bw:install-hint-dismissed";

/**
 * Registers the service worker in production builds and shows a banner to
 * install the app: with a button where the browser offers an install prompt
 * (Chromium on Android), with the steps on iPhone/iPad (no prompt there: Share
 * → «Añadir a pantalla de inicio»; closing it hides it for good).
 */
export function PwaInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [iosHint, setIosHint] = useState(false);

  useEffect(() => {
    if (process.env.NODE_ENV !== "production") return;
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => {
        // Service workers are an enhancement; ignore registration failures.
      });
    }

    const isStandalone =
      window.matchMedia("(display-mode: standalone)").matches ||
      (navigator as Navigator & { standalone?: boolean }).standalone === true;
    if (isStandalone) return;
    if (isIos()) {
      let hidden = false;
      try {
        hidden = localStorage.getItem(IOS_DISMISSED_KEY) === "1";
      } catch {
        // Storage blocked: show it; closing still hides it for this visit.
      }
      // A one-off decision after mount (the server cannot know the device).
      // eslint-disable-next-line react-hooks/set-state-in-effect
      if (!hidden) setIosHint(true);
      return;
    }

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

  if (iosHint && !dismissed) {
    return (
      <div className="border-b bg-primary/5 px-4 py-2">
        <div className="mx-auto flex w-full max-w-2xl items-center justify-between gap-3">
          <p className="flex items-center gap-2 text-xs text-muted-foreground">
            <DownloadIcon className="size-4 shrink-0 text-primary" />
            <span>
              {t.install.banner} {t.install.iosSteps}
            </span>
          </p>
          <button
            type="button"
            aria-label={t.common.close}
            onClick={() => {
              setDismissed(true);
              try {
                localStorage.setItem(IOS_DISMISSED_KEY, "1");
              } catch {
                // Storage blocked: hidden for this visit only.
              }
            }}
            className="shrink-0 rounded p-1 text-muted-foreground transition-colors hover:text-foreground"
          >
            <XIcon className="size-4" />
          </button>
        </div>
      </div>
    );
  }

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