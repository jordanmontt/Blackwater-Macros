"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { DumbbellIcon, DownloadIcon, FlameIcon, InfoIcon, LogOutIcon, Trash2Icon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { api, ApiError } from "@/lib/api";
import { formatTemplate } from "@/i18n";
import type { CalorieProfile, Goal, MealTemplateDTO } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useMounted } from "@/lib/use-mounted";
import { calculateCalorieRecommendation } from "@/lib/calories";
import { calculateProteinRecommendation } from "@/lib/protein";
import { formatNumberEs } from "@/lib/dates";
import { t } from "@/i18n";

const GOAL_LABELS: Record<Goal, string> = {
  cut: t.ajustes.goalCut,
  maintain: t.ajustes.goalMaintain,
  surplus: t.ajustes.goalSurplus,
};

export default function AjustesPage() {
  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const [username, setUsername] = useState("");
  const [templates, setTemplates] = useState<MealTemplateDTO[]>([]);
  const [calorieProfile, setCalorieProfile] = useState<CalorieProfile>({
    gender: null,
    birthYear: null,
    heightCm: null,
    gymDaysPerWeek: null,
    gymSessionMinutes: null,
    walkingMinutesPerDay: null,
    calorieGoal: null,
  });
  const [latestWeight, setLatestWeight] = useState<number | null>(null);
  const mounted = useMounted();
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    void api.session().then((session) => {
      setUsername(session.username);
      setCalorieProfile(session.calorieProfile);
    });
    api
      .listTemplates()
      .then(setTemplates)
      .catch(() => undefined);
    api
      .listWeights()
      .then((weights) => {
        const last = weights.at(-1);
        if (last) setLatestWeight(last.weightKg);
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, []);

  const themeOptions = [
    { value: "light", label: t.ajustes.themeLight },
    { value: "dark", label: t.ajustes.themeDark },
    { value: "system", label: t.ajustes.themeSystem },
  ];

  const goalOptions: { value: Goal; label: string }[] = (
    Object.entries(GOAL_LABELS) as [Goal, string][]
  ).map(([value, label]) => ({ value, label }));

  function validateProfile(profile: CalorieProfile) {
    const errors: Record<string, string> = {};
    if (profile.birthYear !== null && (profile.birthYear < 1920 || profile.birthYear > 2010)) {
      errors.birthYear = "El año debe estar entre 1920 y 2010";
    }
    if (profile.heightCm !== null && (profile.heightCm < 100 || profile.heightCm > 250)) {
      errors.heightCm = "La altura debe estar entre 100 y 250 cm";
    }
    if (profile.gymDaysPerWeek !== null && (profile.gymDaysPerWeek < 0 || profile.gymDaysPerWeek > 7)) {
      errors.gymDaysPerWeek = "Los días deben ser entre 0 y 7";
    }
    if (profile.gymSessionMinutes !== null && (profile.gymSessionMinutes < 0 || profile.gymSessionMinutes > 300)) {
      errors.gymSessionMinutes = "La duración debe ser entre 0 y 300 min";
    }
    if (profile.walkingMinutesPerDay !== null && (profile.walkingMinutesPerDay < 0 || profile.walkingMinutesPerDay > 480)) {
      errors.walkingMinutesPerDay = "El tiempo debe ser entre 0 y 480 min";
    }
    return errors;
  }

  const profileErrors = useMemo(() => validateProfile(calorieProfile), [calorieProfile]);

  async function handleCalorieProfileChange(updates: Partial<CalorieProfile>) {
    const newProfile = { ...calorieProfile, ...updates };
    setCalorieProfile(newProfile);
    if (saveTimer.current) clearTimeout(saveTimer.current);
    const hasErrors = Object.values(validateProfile(newProfile)).some(Boolean);
    if (hasErrors) return;
    saveTimer.current = setTimeout(async () => {
      try {
        await api.updateSettings(newProfile);
      } catch (error) {
        toast.error(error instanceof ApiError ? error.message : t.common.errorGeneric);
      }
    }, 500);
  }

  async function handleLogout() {
    try {
      await api.logout();
      router.replace("/login");
      router.refresh();
    } catch {
      toast.error(t.common.errorGeneric);
    }
  }

  async function handleDeleteTemplate(id: string) {
    try {
      await api.deleteTemplate(id);
      setTemplates((current) => current.filter((template) => template.id !== id));
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : t.common.errorGeneric);
    }
  }

  const calorieRec = latestWeight
    ? calculateCalorieRecommendation(calorieProfile, latestWeight)
    : null;

  const proteinRec = latestWeight && calorieProfile.calorieGoal
    ? calculateProteinRecommendation(latestWeight, calorieProfile.calorieGoal)
    : null;

  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-lg font-semibold">{t.ajustes.title}</h1>
      </header>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.ajustes.appearance}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-3 gap-1 rounded-lg border p-1">
            {themeOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={mounted && theme === option.value}
                onClick={() => setTheme(option.value)}
                className={cn(
                  "rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent",
                  mounted && theme === option.value && "bg-primary text-primary-foreground",
                )}
              >
                {option.label}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.ajustes.goalSection}</CardTitle>
          <CardDescription>{t.ajustes.goalDescription}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-3 gap-1 rounded-lg border p-1">
            {goalOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={calorieProfile.calorieGoal === option.value}
                onClick={() => void handleCalorieProfileChange({ calorieGoal: option.value })}
                className={cn(
                  "rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent",
                  calorieProfile.calorieGoal === option.value && "bg-primary text-primary-foreground",
                )}
              >
                {option.label}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.calorias.recommendationTitle}</CardTitle>
          <CardDescription>{t.calorias.noProfile}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{t.calorias.genderLabel}</Label>
              <div className="grid grid-cols-2 gap-1 rounded-lg border p-1">
                {([
                  ["male", t.calorias.genderMale],
                  ["female", t.calorias.genderFemale],
                ] as const).map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    aria-pressed={calorieProfile.gender === value}
                    onClick={() => void handleCalorieProfileChange({ gender: value })}
                    className={cn(
                      "rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent",
                      calorieProfile.gender === value && "bg-primary text-primary-foreground",
                    )}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="birth-year">{t.calorias.birthYearLabel}</Label>
              <Input
                id="birth-year"
                type="number"
                inputMode="numeric"
                min={1920}
                max={2010}
                placeholder="1990"
                value={calorieProfile.birthYear ?? ""}
                onChange={(e) => {
                  const v = e.target.value === "" ? null : Number(e.target.value);
                  void handleCalorieProfileChange({ birthYear: v });
                }}
              />
              {profileErrors.birthYear && (
                <p className="text-xs text-destructive">{profileErrors.birthYear}</p>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="height-cm">{t.calorias.heightLabel}</Label>
            <Input
              id="height-cm"
              type="number"
              inputMode="decimal"
              step="any"
              min={100}
              max={250}
              placeholder="175"
              value={calorieProfile.heightCm ?? ""}
                onChange={(e) => {
                  const v = e.target.value === "" ? null : Number(e.target.value.replace(",", "."));
                  void handleCalorieProfileChange({ heightCm: v && Number.isFinite(v) ? v : null });
                }}
              />
              {profileErrors.heightCm && (
                <p className="text-xs text-destructive">{profileErrors.heightCm}</p>
              )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="gym-days">{t.calorias.gymDaysLabel}</Label>
              <Input
                id="gym-days"
                type="number"
                inputMode="numeric"
                min={0}
                max={7}
                placeholder="3"
                value={calorieProfile.gymDaysPerWeek ?? ""}
                onChange={(e) => {
                  const v = e.target.value === "" ? null : Number(e.target.value);
                  void handleCalorieProfileChange({ gymDaysPerWeek: v });
                }}
              />
              {profileErrors.gymDaysPerWeek && (
                <p className="text-xs text-destructive">{profileErrors.gymDaysPerWeek}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="gym-minutes">{t.calorias.gymSessionLabel}</Label>
              <Input
                id="gym-minutes"
                type="number"
                inputMode="numeric"
                min={0}
                max={300}
                placeholder="60"
                value={calorieProfile.gymSessionMinutes ?? ""}
                onChange={(e) => {
                  const v = e.target.value === "" ? null : Number(e.target.value);
                  void handleCalorieProfileChange({ gymSessionMinutes: v });
                }}
              />
              {profileErrors.gymSessionMinutes && (
                <p className="text-xs text-destructive">{profileErrors.gymSessionMinutes}</p>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="walking-minutes">{t.calorias.walkingLabel}</Label>
            <Input
              id="walking-minutes"
              type="number"
              inputMode="numeric"
              min={0}
              max={480}
              placeholder="30"
              value={calorieProfile.walkingMinutesPerDay ?? ""}
                onChange={(e) => {
                  const v = e.target.value === "" ? null : Number(e.target.value);
                  void handleCalorieProfileChange({ walkingMinutesPerDay: v });
                }}
              />
              {profileErrors.walkingMinutesPerDay && (
                <p className="text-xs text-destructive">{profileErrors.walkingMinutesPerDay}</p>
              )}
          </div>

          {(calorieRec || proteinRec) && (
            <div className="rounded-lg border bg-muted/40 p-3 space-y-3">
              {calorieRec && (
                <div className="space-y-1">
                  <p className="flex items-center gap-1.5 text-sm font-medium">
                    <FlameIcon className="size-3.5" /> {t.calorias.target}
                  </p>
                  <p className="text-2xl font-semibold tabular-nums">
                    {formatNumberEs(calorieRec.target)} <span className="text-sm font-normal text-muted-foreground">{t.calorias.perDay}</span>
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {formatNumberEs(calorieRec.targetMin)} – {formatNumberEs(calorieRec.targetMax)} {t.calorias.perDay}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {t.calorias.bmr}: {formatNumberEs(calorieRec.bmr)} · {t.calorias.tdee}: {formatNumberEs(calorieRec.tdee)}
                  </p>
                </div>
              )}
              {proteinRec && (
                <div className="space-y-1 border-t border-border pt-3">
                  <p className="flex items-center gap-1.5 text-sm font-medium">
                    <DumbbellIcon className="size-3.5" /> {t.ajustes.proteinRecLabel}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {proteinRec.bwRange.min} – {proteinRec.bwRange.max} g/día{" "}
                    ({proteinRec.bwPerKg.min} – {proteinRec.bwPerKg.max} g/kg)
                  </p>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.metodologia.title}</CardTitle>
          <CardDescription>{t.ajustes.methodologyLink}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="outline"
            nativeButton={false}
            render={<Link href="/metodologia" />}
          >
            <InfoIcon /> {t.metodologia.title}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.ajustes.exportSection}</CardTitle>
          <CardDescription>{t.ajustes.exportHint}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-2 sm:flex-row">
          <Button
            variant="outline"
            nativeButton={false}
            render={<a href="/api/export/meals.csv" download />}
          >
            <DownloadIcon /> {t.ajustes.exportMeals}
          </Button>
          <Button
            variant="outline"
            nativeButton={false}
            render={<a href="/api/export/weights.csv" download />}
          >
            <DownloadIcon /> {t.ajustes.exportWeights}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.hoy.templates}</CardTitle>
        </CardHeader>
        <CardContent>
          {templates.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t.hoy.noTemplates}</p>
          ) : (
            <ul className="divide-y">
              {templates.map((template) => (
                <li key={template.id} className="flex items-center gap-2 py-2">
                  <span className="min-w-0 flex-1 truncate text-sm">{template.name}</span>
                  <span className="text-xs text-muted-foreground">
                    {formatTemplate(t.meal.perIngredientSummary, { n: template.ingredients.length })}
                  </span>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={t.meal.delete}
                    onClick={() => void handleDeleteTemplate(template.id)}
                  >
                    <Trash2Icon className="text-muted-foreground" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{t.ajustes.session}</CardTitle>
          <CardDescription>
            {t.ajustes.loggedInAs} {username || "…"}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Separator className="mb-3" />
          <Button variant="outline" onClick={() => void handleLogout()}>
            <LogOutIcon /> {t.auth.logout}
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
