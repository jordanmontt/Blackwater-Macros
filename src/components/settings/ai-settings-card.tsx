"use client";

import { useRef, useState } from "react";
import { CheckCircle2Icon, ExternalLinkIcon, EyeIcon, EyeOffIcon, Loader2Icon, SparklesIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import type { AiProvider } from "@/lib/core/ai-providers";
import { AiError, testAi } from "@/lib/ai/client";
import { AI_PROVIDERS, aiConfigOf, saveAiSettings, useAiSettings } from "@/lib/ai/settings";
import { BrowserModelSection } from "@/components/settings/browser-model-section";
import { ModelPicker } from "@/components/settings/model-picker";
import { t } from "@/i18n";

type TestState =
  | { status: "idle" }
  | { status: "testing" }
  | { status: "ok" }
  | { status: "error"; message: string; detail: string };

export const AI_STUDIO_URL = "https://aistudio.google.com/api-keys";

/** Ajustes → IA. Everything is saved as you type, in this browser only. */
export function AiSettingsCard() {
  const settings = useAiSettings();
  const provider = settings.provider;
  const [showKey, setShowKey] = useState(false);
  const [test, setTest] = useState<TestState>({ status: "idle" });
  const testAbort = useRef<AbortController | null>(null);

  function update(change: Parameters<typeof saveAiSettings>[0]) {
    testAbort.current?.abort();
    setTest({ status: "idle" });
    saveAiSettings(change);
  }

  async function runTest() {
    testAbort.current?.abort();
    const controller = new AbortController();
    testAbort.current = controller;
    setTest({ status: "testing" });
    try {
      await testAi(aiConfigOf(settings), controller.signal);
      if (!controller.signal.aborted) setTest({ status: "ok" });
    } catch (error) {
      if (controller.signal.aborted) return;
      const kind = error instanceof AiError ? error.kind : "provider";
      setTest({ status: "error", message: t.ai.errors[kind], detail: error instanceof AiError ? error.detail : "" });
    }
  }

  const custom = provider === "custom";

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          <SparklesIcon className="size-4" /> {t.ai.title}
        </CardTitle>
        <CardDescription>{t.ai.description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="ai-provider">{t.ai.provider}</Label>
          <NativeSelect
            id="ai-provider"
            value={provider}
            onChange={(event) => {
              const next = event.target.value as AiProvider;
              update((current) => ({ ...current, provider: next }));
            }}
          >
            {AI_PROVIDERS.map((id) => (
              <option key={id} value={id}>
                {t.ai.providers[id]}
              </option>
            ))}
          </NativeSelect>
        </div>

        {provider === "gemini" ? (
          <details className="rounded-lg border p-3 text-sm">
            <summary className="cursor-pointer font-medium">{t.ai.freeKeyTitle}</summary>
            <ol className="mt-2 list-decimal space-y-1 pl-5 text-muted-foreground">
              <li>{t.ai.freeKeyStep1}</li>
              <li>{t.ai.freeKeyStep2}</li>
              <li>{t.ai.freeKeyStep3}</li>
            </ol>
            <Button
              variant="outline"
              size="sm"
              className="mt-3"
              nativeButton={false}
              render={<a href={AI_STUDIO_URL} target="_blank" rel="noreferrer" />}
            >
              <ExternalLinkIcon /> {t.ai.freeKeyOpen}
            </Button>
            <p className="mt-3 text-xs text-muted-foreground">{t.ai.freeKeyPrivacy}</p>
          </details>
        ) : null}

        {custom ? (
          <div className="space-y-1.5">
            <Label htmlFor="ai-base-url">{t.ai.baseUrl}</Label>
            <Input
              id="ai-base-url"
              type="url"
              inputMode="url"
              autoComplete="off"
              spellCheck={false}
              placeholder="http://localhost:11434/v1"
              value={settings.baseUrl}
              onChange={(event) => {
                const value = event.target.value;
                update((current) => ({ ...current, baseUrl: value }));
              }}
            />
            <p className="text-xs text-muted-foreground">{t.ai.baseUrlHint}</p>
            <p className="text-xs text-muted-foreground">{t.ai.localHint}</p>
          </div>
        ) : null}

        <div className="space-y-1.5">
          <Label htmlFor="ai-key">{custom ? t.ai.apiKeyOptional : t.ai.apiKey}</Label>
          <div className="flex gap-2">
            <Input
              id="ai-key"
              type={showKey ? "text" : "password"}
              autoComplete="off"
              spellCheck={false}
              value={settings.apiKeys[provider] ?? ""}
              onChange={(event) => {
                const value = event.target.value.trim();
                update((current) => ({ ...current, apiKeys: { ...current.apiKeys, [provider]: value } }));
              }}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={showKey ? t.ai.hideKey : t.ai.showKey}
              onClick={() => setShowKey((value) => !value)}
            >
              {showKey ? <EyeOffIcon /> : <EyeIcon />}
            </Button>
          </div>
        </div>

        <ModelPicker
          settings={settings}
          onChange={(model) => update((current) => ({ ...current, models: { ...current.models, [provider]: model } }))}
        />

        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" disabled={test.status === "testing"} onClick={() => void runTest()}>
            {test.status === "testing" ? <Loader2Icon className="animate-spin" /> : null}
            {test.status === "testing" ? t.ai.testing : t.ai.test}
          </Button>
          <p role="status" className="min-w-0 flex-1 text-sm">
            {test.status === "ok" ? (
              <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
                <CheckCircle2Icon className="size-4" /> {t.ai.testOk}
              </span>
            ) : test.status === "error" ? (
              <>
                <span className="text-destructive">{test.message}</span>
                {test.detail ? <span className="block text-xs text-muted-foreground">{test.detail}</span> : null}
              </>
            ) : null}
          </p>
        </div>

        <BrowserModelSection />

        <label className="flex items-start gap-3 rounded-lg border p-3">
          <input
            type="checkbox"
            className="mt-0.5 size-4 accent-primary"
            checked={settings.coachSeesData}
            onChange={(event) => {
              const checked = event.target.checked;
              update((current) => ({ ...current, coachSeesData: checked }));
            }}
          />
          <span className="space-y-0.5">
            <span className="block text-sm font-medium">{t.ai.coachSeesData}</span>
            <span className="block text-xs text-muted-foreground">{t.ai.coachSeesDataHint}</span>
          </span>
        </label>
      </CardContent>
    </Card>
  );
}
