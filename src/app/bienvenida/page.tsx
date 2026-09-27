"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2Icon, ExternalLinkIcon, Loader2Icon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Logo } from "@/components/logo";
import { AI_STUDIO_URL } from "@/components/settings/ai-settings-card";
import { api, ApiError } from "@/lib/api";
import { invalidate } from "@/lib/client-cache";
import { useCachedResource } from "@/lib/use-cached-resource";
import { AiError, testAi } from "@/lib/ai/client";
import { aiConfigOf, getAiSettings, isAiReady, saveAiSettings, useAiSettings } from "@/lib/ai/settings";
import { DEFAULT_ACTIVITY, markOnboardingDone } from "@/lib/onboarding";
import type { CalorieProfile, Gender, Goal, WeightDTO } from "@/lib/core/types";
import { cn } from "@/lib/utils";
import { formatTemplate, t } from "@/i18n";

type Step = "datos" | "ia" | "listo";
const STEPS: Step[] = ["datos", "ia", "listo"];

/**
 * First steps after the first login (§4.5 of docs/AI-PLAN.md, D12): your data
 * (profile + current weight), the optional AI key, done. «Saltar» always works.
 */
export default function BienvenidaPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("datos");

  function finish() {
    markOnboardingDone();
    router.replace("/");
  }

  return (
    <main className="mx-auto w-full max-w-md space-y-4 px-4 pt-6">
      <header className="flex items-center justify-between gap-2">
        <Logo size="header" />
        <div className="flex items-center gap-3">
          <span className="text-xs text-muted-foreground">
            {formatTemplate(t.onboarding.step, { n: STEPS.indexOf(step) + 1, total: STEPS.length })}
          </span>
          {step !== "listo" ? (
            <Button variant="ghost" size="sm" onClick={finish}>
              {t.onboarding.skip}
            </Button>
          ) : null}
        </div>
      </header>
      {step === "datos" ? (
        <DataStep onDone={() => setStep("ia")} />
      ) : step === "ia" ? (
        <AiStep onBack={() => setStep("datos")} onDone={() => setStep("listo")} />
      ) : (
        <DoneStep onStart={finish} />
      )}
    </main>
  );
}

function Segmented<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: [T, string][];
  value: T | null;
  onChange: (value: T) => void;
}) {
  return (
    <div className="space-y-1.5" role="group" aria-label={label}>
      <Label>{label}</Label>
      <div className={cn("grid gap-1 rounded-lg border p-1", options.length === 2 ? "grid-cols-2" : "grid-cols-3")}>
        {options.map(([option, text]) => (
          <button
            key={option}
            type="button"
            aria-pressed={value === option}
            onClick={() => onChange(option)}
            className={cn(
              "rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent",
              value === option && "bg-primary text-primary-foreground",
            )}
          >
            {text}
          </button>
        ))}
      </div>
    </div>
  );
}

function numberOrNull(text: string): number | null {
  const value = Number(text.replace(",", "."));
  return text.trim() === "" || !Number.isFinite(value) ? null : value;
}

function DataStep({ onDone }: { onDone: () => void }) {
  const sessionRes = useCachedResource<Awaited<ReturnType<typeof api.session>>>("session", () => api.session());
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());
  const saved = sessionRes.data?.calorieProfile;
  const latest = weightsRes.data?.at(-1) ?? null;

  // Edits on top of what is saved (a returning user may have filled part of it).
  const [edits, setEdits] = useState<Partial<Record<"gender" | "goal" | "birthYear" | "height" | "weight" | "gym" | "session" | "walk", string>>>({});
  const [busy, setBusy] = useState(false);
  const field = (key: keyof typeof edits, fallback: number | string | null | undefined) =>
    edits[key] ?? (fallback === null || fallback === undefined ? "" : String(fallback));
  const set = (key: keyof typeof edits) => (value: string) => setEdits((current) => ({ ...current, [key]: value }));

  const gender = (field("gender", saved?.gender) || null) as Gender | null;
  const goal = (field("goal", saved?.calorieGoal) || null) as Goal | null;
  const birthYear = numberOrNull(field("birthYear", saved?.birthYear));
  const height = numberOrNull(field("height", saved?.heightCm));
  const weight = numberOrNull(field("weight", latest?.weightKg));
  const gym = numberOrNull(field("gym", saved?.gymDaysPerWeek ?? DEFAULT_ACTIVITY.gymDaysPerWeek));
  const session = numberOrNull(field("session", saved?.gymSessionMinutes ?? DEFAULT_ACTIVITY.gymSessionMinutes));
  const walk = numberOrNull(field("walk", saved?.walkingMinutesPerDay ?? DEFAULT_ACTIVITY.walkingMinutesPerDay));

  const complete = gender !== null && goal !== null && birthYear !== null && height !== null && weight !== null;
  const valid =
    complete &&
    Number.isInteger(birthYear) &&
    birthYear >= 1920 &&
    birthYear <= 2010 &&
    height >= 100 &&
    height <= 250 &&
    weight >= 20 &&
    weight <= 400 &&
    gym !== null && gym >= 0 && gym <= 7 &&
    session !== null && session >= 0 && session <= 300 &&
    walk !== null && walk >= 0 && walk <= 480;

  async function save() {
    if (!valid) return;
    const profile: CalorieProfile = {
      gender,
      birthYear,
      heightCm: height,
      gymDaysPerWeek: Math.round(gym),
      gymSessionMinutes: Math.round(session),
      walkingMinutesPerDay: Math.round(walk),
      calorieGoal: goal,
    };
    setBusy(true);
    try {
      await api.updateSettings(profile);
      if (latest === null || latest.weightKg !== weight) {
        await api.createWeight({ measuredAt: new Date().toISOString(), weightKg: weight });
      }
      invalidate("session");
      invalidate("weights");
      onDone();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : t.common.errorGeneric);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{t.onboarding.dataTitle}</CardTitle>
        <CardDescription>{t.onboarding.dataIntro}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <Segmented
          label={t.calorias.genderLabel}
          options={[
            ["male", t.calorias.genderMale],
            ["female", t.calorias.genderFemale],
          ]}
          value={gender}
          onChange={set("gender")}
        />
        <div className="grid grid-cols-3 gap-2">
          <NumberField id="ob-birth" label={t.calorias.birthYearLabel} value={field("birthYear", saved?.birthYear)} onChange={set("birthYear")} placeholder="1990" />
          <NumberField id="ob-height" label={t.calorias.heightLabel} value={field("height", saved?.heightCm)} onChange={set("height")} placeholder="170" />
          <NumberField id="ob-weight" label={t.onboarding.weight} value={field("weight", latest?.weightKg)} onChange={set("weight")} placeholder="70" decimal />
        </div>
        <Segmented
          label={t.onboarding.goal}
          options={[
            ["cut", t.ajustes.goalCut],
            ["maintain", t.ajustes.goalMaintain],
            ["surplus", t.ajustes.goalSurplus],
          ]}
          value={goal}
          onChange={set("goal")}
        />
        <details className="rounded-lg border p-3 text-sm">
          <summary className="cursor-pointer">
            <span className="font-medium">{t.onboarding.activity}</span>{" "}
            <span className="text-muted-foreground">
              · {formatTemplate(t.onboarding.activitySummary, { days: gym ?? "–", walk: walk ?? "–" })}
            </span>
          </summary>
          <div className="mt-3 grid gap-3">
            <NumberField id="ob-gym" label={t.calorias.gymDaysLabel} value={field("gym", saved?.gymDaysPerWeek ?? DEFAULT_ACTIVITY.gymDaysPerWeek)} onChange={set("gym")} />
            <NumberField id="ob-session" label={t.calorias.gymSessionLabel} value={field("session", saved?.gymSessionMinutes ?? DEFAULT_ACTIVITY.gymSessionMinutes)} onChange={set("session")} />
            <NumberField id="ob-walk" label={t.calorias.walkingLabel} value={field("walk", saved?.walkingMinutesPerDay ?? DEFAULT_ACTIVITY.walkingMinutesPerDay)} onChange={set("walk")} />
          </div>
        </details>
        {complete && !valid ? <p className="text-sm text-destructive">{t.onboarding.invalid}</p> : null}
        <Button className="w-full" disabled={!valid || busy} onClick={() => void save()}>
          {busy ? <Loader2Icon className="animate-spin" /> : null}
          {t.onboarding.next}
        </Button>
      </CardContent>
    </Card>
  );
}

function NumberField({
  id,
  label,
  value,
  onChange,
  placeholder,
  decimal = false,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  decimal?: boolean;
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id} className="flex min-h-8 items-end text-xs leading-tight">
        {label}
      </Label>
      <Input
        id={id}
        inputMode={decimal ? "decimal" : "numeric"}
        autoComplete="off"
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}

type TestState = "idle" | "testing" | "ok" | { error: string };

function AiStep({ onBack, onDone }: { onBack: () => void; onDone: () => void }) {
  const settings = useAiSettings();
  const [test, setTest] = useState<TestState>("idle");
  const key = settings.apiKeys.gemini ?? "";

  async function runTest() {
    setTest("testing");
    try {
      await testAi(aiConfigOf(getAiSettings()));
      setTest("ok");
    } catch (error) {
      setTest({ error: t.ai.errors[error instanceof AiError ? error.kind : "provider"] });
    }
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{t.onboarding.aiTitle}</CardTitle>
        <CardDescription>{t.onboarding.aiIntro}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        <ol className="list-decimal space-y-1 pl-5 text-muted-foreground">
          <li>{t.ai.freeKeyStep1}</li>
          <li>{t.ai.freeKeyStep2}</li>
          <li>{t.ai.freeKeyStep3}</li>
        </ol>
        <Button variant="outline" size="sm" nativeButton={false} render={<a href={AI_STUDIO_URL} target="_blank" rel="noreferrer" />}>
          <ExternalLinkIcon /> {t.ai.freeKeyOpen}
        </Button>
        <div className="space-y-1.5">
          <Label htmlFor="ob-key">{t.onboarding.aiKey}</Label>
          <div className="flex gap-2">
            <Input
              id="ob-key"
              type="password"
              autoComplete="off"
              spellCheck={false}
              value={key}
              onChange={(event) => {
                const value = event.target.value.trim();
                setTest("idle");
                saveAiSettings((current) => ({ ...current, provider: "gemini", apiKeys: { ...current.apiKeys, gemini: value } }));
              }}
            />
            <Button variant="outline" disabled={key === "" || test === "testing"} onClick={() => void runTest()}>
              {test === "testing" ? <Loader2Icon className="animate-spin" /> : null}
              {t.ai.test}
            </Button>
          </div>
          <p role="status" className="min-h-5">
            {test === "ok" ? (
              <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
                <CheckCircle2Icon className="size-4" /> {t.ai.testOk}
              </span>
            ) : typeof test === "object" ? (
              <span className="text-destructive">{test.error}</span>
            ) : null}
          </p>
        </div>
        <p className="text-xs text-muted-foreground">{t.ai.freeKeyPrivacy}</p>
        <p className="text-xs text-muted-foreground">{t.onboarding.aiOther}</p>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onBack}>
            {t.onboarding.back}
          </Button>
          <div className="flex-1" />
          <Button variant="outline" onClick={onDone}>
            {t.onboarding.notNow}
          </Button>
          <Button disabled={!isAiReady(settings)} onClick={onDone}>
            {t.onboarding.next}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function DoneStep({ onStart }: { onStart: () => void }) {
  const settings = useAiSettings();
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{t.onboarding.doneTitle}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <p>{t.onboarding.doneBody}</p>
        {isAiReady(settings) ? <p>{t.onboarding.doneAi}</p> : null}
        <Button className="w-full" onClick={onStart}>
          {t.onboarding.start}
        </Button>
      </CardContent>
    </Card>
  );
}
