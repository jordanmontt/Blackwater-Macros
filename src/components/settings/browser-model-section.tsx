"use client";

import { useEffect } from "react";
import { DownloadIcon, Loader2Icon, Trash2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  BROWSER_MODEL,
  checkBrowserModel,
  deleteBrowserModel,
  downloadBrowserModel,
  useBrowserModel,
} from "@/lib/ai/browser-model";
import { saveAiSettings, useAiSettings } from "@/lib/ai/settings";
import { cn } from "@/lib/utils";
import { formatTemplate, t } from "@/i18n";

/**
 * «Modelo en este navegador» (D9): download a small model once and let the
 * coach run on it. Hidden behind a one-line reason where WebGPU is missing.
 */
export function BrowserModelSection() {
  const model = useBrowserModel();
  const settings = useAiSettings();
  const size = String(BROWSER_MODEL.sizeGb).replace(".", ",");

  useEffect(() => {
    void checkBrowserModel();
  }, []);

  function chooseEngine(engine: "cloud" | "browser") {
    saveAiSettings((current) => ({ ...current, coachEngine: engine }));
  }

  return (
    <section aria-label={t.ai.browserTitle} className="space-y-3 rounded-lg border p-3 text-sm">
      <div>
        <h3 className="font-medium">{t.ai.browserTitle}</h3>
        <p className="text-xs text-muted-foreground">{t.ai.browserDescription}</p>
      </div>

      {model.status === "checking" ? (
        <p className="text-xs text-muted-foreground">{t.ai.browserChecking}</p>
      ) : model.status === "unsupported" ? (
        <p className="text-xs text-muted-foreground">{t.ai.browserUnsupported}</p>
      ) : model.status === "downloading" ? (
        <div className="space-y-1.5">
          <div className="h-2 overflow-hidden rounded-full bg-muted">
            <div className="h-full bg-primary transition-all" style={{ width: `${Math.round(model.progress * 100)}%` }} />
          </div>
          <p role="status" className="flex items-center gap-1.5 text-xs">
            <Loader2Icon className="size-3.5 animate-spin" />
            {formatTemplate(t.ai.browserDownloading, { percent: Math.round(model.progress * 100) })}
          </p>
        </div>
      ) : model.status === "ready" ? (
        <div className="space-y-3">
          <p>{formatTemplate(t.ai.browserReady, { model: BROWSER_MODEL.name })}</p>
          <div className="space-y-1.5" role="group" aria-label={t.ai.useForCoach}>
            <Label>{t.ai.useForCoach}</Label>
            <div className="grid grid-cols-2 gap-1 rounded-lg border p-1">
              {(
                [
                  ["cloud", t.ai.engineCloud],
                  ["browser", t.ai.engineBrowser],
                ] as const
              ).map(([engine, label]) => (
                <button
                  key={engine}
                  type="button"
                  aria-pressed={settings.coachEngine === engine}
                  onClick={() => chooseEngine(engine)}
                  className={cn(
                    "rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent",
                    settings.coachEngine === engine && "bg-primary text-primary-foreground",
                  )}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              chooseEngine("cloud");
              void deleteBrowserModel();
            }}
          >
            <Trash2Icon /> {t.ai.browserDelete}
          </Button>
        </div>
      ) : (
        <div className="space-y-2">
          {model.status === "error" ? (
            <p className="text-xs text-destructive">{formatTemplate(t.ai.browserError, { message: model.message })}</p>
          ) : null}
          <p className="text-xs text-muted-foreground">{formatTemplate(t.ai.browserWarning, { size })}</p>
          <Button variant="outline" size="sm" onClick={() => void downloadBrowserModel()}>
            <DownloadIcon /> {formatTemplate(t.ai.browserDownload, { model: BROWSER_MODEL.name, size })}
          </Button>
        </div>
      )}
    </section>
  );
}
