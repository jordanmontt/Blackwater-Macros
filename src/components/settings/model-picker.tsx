"use client";

import { useEffect, useState } from "react";
import { Loader2Icon, RefreshCwIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { DEFAULT_MODELS, isAiConfigured } from "@/lib/core/ai-providers";
import { AiError } from "@/lib/ai/client";
import { configFingerprint, refreshModelList, useModelList } from "@/lib/ai/model-list";
import { aiConfigOf, type AiSettings } from "@/lib/ai/settings";
import { formatTemplate, t } from "@/i18n";

const OTHER = "__other__";

type Status = { kind: "idle" } | { kind: "loading" } | { kind: "error"; message: string };

/**
 * «Modelo»: a dropdown with the models the provider says this key can use
 * (`lib/ai/model-list.ts`), «Predeterminado» first and «Otro…» to type a name.
 * Without a list (no key yet, offline, a server that does not list) it is the
 * plain text field it always was.
 */
export function ModelPicker({ settings, onChange }: { settings: AiSettings; onChange: (model: string) => void }) {
  const provider = settings.provider;
  const config = aiConfigOf(settings);
  const fingerprint = configFingerprint(config);
  const list = useModelList(config);
  const [status, setStatus] = useState<Status>({ kind: "idle" });
  const [typing, setTyping] = useState(false);
  const saved = settings.models[provider]?.trim() ?? "";
  const configured = isAiConfigured(config);

  async function load(force: boolean) {
    setStatus({ kind: "loading" });
    try {
      await refreshModelList(config, force);
      setStatus({ kind: "idle" });
    } catch (error) {
      const kind = error instanceof AiError ? error.kind : "provider";
      setStatus({ kind: "error", message: t.ai.errors[kind] });
    }
  }

  // A new key (or server) is looked up once the user stops typing; the same one only once a day.
  useEffect(() => {
    if (!configured) return;
    const timer = setTimeout(() => void load(false), 700);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- keyed on the key/server, not on every render
  }, [fingerprint, configured]);

  const options = list?.models ?? [];
  const known = saved === "" || options.some((option) => option.id === saved);
  const showSelect = options.length > 0 && !typing;

  return (
    <div className="space-y-1.5">
      <div className="flex items-end justify-between gap-2">
        <Label htmlFor="ai-model">{t.ai.model}</Label>
        {configured ? (
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t.ai.modelRefresh}
            disabled={status.kind === "loading"}
            onClick={() => void load(true)}
          >
            {status.kind === "loading" ? <Loader2Icon className="animate-spin" /> : <RefreshCwIcon />}
          </Button>
        ) : null}
      </div>
      {showSelect ? (
        <NativeSelect
          id="ai-model"
          value={saved}
          onChange={(event) => {
            if (event.target.value === OTHER) {
              setTyping(true);
              return;
            }
            onChange(event.target.value);
          }}
        >
          <option value="">{formatTemplate(t.ai.modelDefault, { model: DEFAULT_MODELS[provider] || "—" })}</option>
          {known ? null : <option value={saved}>{saved}</option>}
          {options.map((option) => (
            <option key={option.id} value={option.id}>
              {option.label}
            </option>
          ))}
          <option value={OTHER}>{t.ai.modelOther}</option>
        </NativeSelect>
      ) : (
        <Input
          id="ai-model"
          autoComplete="off"
          spellCheck={false}
          placeholder={DEFAULT_MODELS[provider]}
          value={settings.models[provider] ?? ""}
          onChange={(event) => onChange(event.target.value)}
          onBlur={() => setTyping(false)}
        />
      )}
      <p className="text-xs text-muted-foreground" role="status">
        {status.kind === "loading"
          ? t.ai.modelListLoading
          : status.kind === "error"
            ? formatTemplate(t.ai.modelListError, { message: status.message })
            : options.length > 0
              ? `${formatTemplate(t.ai.modelListCount, { n: options.length })} ${t.ai.modelLimits}`
              : configured
                ? DEFAULT_MODELS[provider]
                  ? formatTemplate(t.ai.modelHint, { model: DEFAULT_MODELS[provider] })
                  : ""
                : t.ai.modelListNeedsKey}
        {showSelect && saved !== "" ? ` · ${saved}` : ""}
      </p>
    </div>
  );
}
