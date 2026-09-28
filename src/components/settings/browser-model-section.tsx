"use client";

import { useEffect } from "react";
import { DownloadIcon, Loader2Icon, Trash2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import {
  BROWSER_MODELS,
  checkBrowserModel,
  deleteBrowserModel,
  downloadBrowserModel,
  selectBrowserModel,
  useBrowserModel,
} from "@/lib/ai/browser-model";
import { saveAiSettings, useAiSettings } from "@/lib/ai/settings";
import { cn } from "@/lib/utils";
import { formatTemplate, t } from "@/i18n";
import { formatNumber } from "@/i18n/format";

/**
 * «Modelo en este navegador»: choose a small model (like Android's list),
 * download it once and let the coach run on it. Hidden behind a one-line
 * reason where WebGPU is missing or too limited.
 */
export function BrowserModelSection() {
  const model = useBrowserModel();
  const settings = useAiSettings();
  const size = "model" in model ? formatNumber(model.model.sizeGb, 1) : "";

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
        <p className="text-xs text-muted-foreground">
          {model.reason === "limited" ? t.ai.browserLimited : t.ai.browserUnsupported}
        </p>
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
          <p>{formatTemplate(t.ai.browserReady, { model: model.model.name })}</p>
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
          <div className="space-y-1.5">
            <Label htmlFor="browser-model">{t.ai.browserModel}</Label>
            <NativeSelect
              id="browser-model"
              value={model.model.id}
              onChange={(event) => selectBrowserModel(event.target.value)}
            >
              {BROWSER_MODELS.filter((option) => option.offered).map((option) => (
                <option key={option.id} value={option.id}>
                  {option.name} · {formatNumber(option.sizeGb, 1)} GB
                </option>
              ))}
            </NativeSelect>
            <p className="text-xs text-muted-foreground">{t.ai.browserModelNotes[model.model.id as keyof typeof t.ai.browserModelNotes]}</p>
          </div>
          <p className="text-xs text-muted-foreground">{formatTemplate(t.ai.browserWarning, { size })}</p>
          <Button variant="outline" size="sm" onClick={() => void downloadBrowserModel()}>
            <DownloadIcon /> {formatTemplate(t.ai.browserDownload, { model: model.model.name, size })}
          </Button>
        </div>
      )}
    </section>
  );
}
