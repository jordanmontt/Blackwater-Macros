"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { formatNumber } from "@/i18n/format";
import Link from "next/link";
import { ArrowLeftIcon, DumbbellIcon, FlameIcon } from "lucide-react";
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
import { api, ApiError } from "@/lib/api";
import { useCachedResource } from "@/lib/use-cached-resource";
import { useMeasuredExpenditure } from "@/lib/use-measured-expenditure";
import type { CalorieProfile, Goal, WeightDTO } from "@/lib/core/types";
import { cn } from "@/lib/utils";
import { calculateCalorieRecommendation } from "@/lib/core/calories";
import { calculateProteinRecommendation } from "@/lib/core/protein";
import { formatTemplate, t } from "@/i18n";

const GOAL_LABELS: Record<Goal, string> = {
  cut: t.ajustes.goalCut,
  maintain: t.ajustes.goalMaintain,
  surplus: t.ajustes.goalSurplus,
};

/**
 * "Perfil": objetivo deportivo, datos corporales y las recomendaciones de
 * calorías y proteína que se derivan de ellos. Separado de Ajustes para que
 * Ajustes quede en cuenta, datos y apariencia (igual que en Android).
 */
export default function PerfilPage() {
  const [calorieProfile, setCalorieProfile] = useState<CalorieProfile>({
    gender: null,
    birthYear: null,
    heightCm: null,
    gymDaysPerWeek: null,
    gymSessionMinutes: null,
    walkingMinutesPerDay: null,
    calorieGoal: null,
  });
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const sessionRes = useCachedResource<
    Awaited<ReturnType<typeof api.session>>
  >("session", () => api.session());
  const weightsRes = useCachedResource<WeightDTO[]>("weights", () => api.listWeights());

  const session = sessionRes.data;
  const latestWeight = weightsRes.data?.at(-1)?.weightKg ?? null;
  // Body fat is not logged at every weigh-in: use the most recent one there is.
  const bodyFatPct = weightsRes.data?.findLast((w) => w.bodyFatPct !== null)?.bodyFatPct ?? null;
  const measured = useMeasuredExpenditure();

  // Siembra el perfil de calorías desde la sesión solo mientras el usuario no
  // haya tocado el formulario (evita pisar ediciones a mitad de escritura).
  useEffect(() => {
    const profile = session?.calorieProfile;
    if (!profile) return;
    void Promise.resolve(profile).then((seeded) => {
      setCalorieProfile((current) =>
        current.calorieGoal === null &&
        current.gender === null &&
        current.heightCm === null &&
        current.birthYear === null &&
        current.gymDaysPerWeek === null &&
        current.gymSessionMinutes === null &&
        current.walkingMinutesPerDay === null
          ? seeded
          : current,
      );
    });
  }, [session]);

  useEffect(() => {
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, []);

  useEffect(() => {
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, []);

  const goalOptions: { value: Goal; label: string }[] = (
    Object.entries(GOAL_LABELS) as [Goal, string][]
  ).map(([value, label]) => ({ value, label }));

  function validateProfile(profile: CalorieProfile) {
    const errors: Record<string, string> = {};
    if (profile.birthYear !== null && (profile.birthYear < 1920 || profile.birthYear > 2010)) {
      errors.birthYear = t.perfil.errorBirthYear;
    }
    if (profile.heightCm !== null && (profile.heightCm < 100 || profile.heightCm > 250)) {
      errors.heightCm = t.perfil.errorHeight;
    }
    if (profile.gymDaysPerWeek !== null && (profile.gymDaysPerWeek < 0 || profile.gymDaysPerWeek > 7)) {
      errors.gymDaysPerWeek = t.perfil.errorGymDays;
    }
    if (profile.gymSessionMinutes !== null && (profile.gymSessionMinutes < 0 || profile.gymSessionMinutes > 300)) {
      errors.gymSessionMinutes = t.perfil.errorGymMinutes;
    }
    if (profile.walkingMinutesPerDay !== null && (profile.walkingMinutesPerDay < 0 || profile.walkingMinutesPerDay > 480)) {
      errors.walkingMinutesPerDay = t.perfil.errorWalking;
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

  const calorieRec = latestWeight
    ? calculateCalorieRecommendation(calorieProfile, latestWeight, new Date().getFullYear())
    : null;

  const proteinRec = latestWeight && calorieProfile.calorieGoal
    ? calculateProteinRecommendation(latestWeight, calorieProfile.calorieGoal, bodyFatPct, {
        heightCm: calorieProfile.heightCm,
        gender: calorieProfile.gender,
      })
    : null;

  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h1 className="text-lg font-semibold">{t.perfil.title}</h1>
        </div>
        <Button
          variant="ghost"
          size="icon-sm"
          nativeButton={false}
          render={<Link href="/ajustes" aria-label={t.perfil.backToSettings} />}
        >
          <ArrowLeftIcon />
        </Button>
      </header>

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

          <div className="grid grid-cols-2 gap-3">
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
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="gym-days" className="block min-h-10 leading-tight">
                {t.calorias.gymDaysLabel}
              </Label>
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
              <Label htmlFor="gym-minutes" className="block min-h-10 leading-tight">
                {t.calorias.gymSessionLabel}
              </Label>
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
                    {formatNumber(calorieRec.target)} <span className="text-sm font-normal text-muted-foreground">{t.calorias.perDay}</span>
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {formatNumber(calorieRec.targetMin)} – {formatNumber(calorieRec.targetMax)} {t.calorias.perDay}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {t.calorias.bmr}: {formatNumber(calorieRec.bmr)}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {formatTemplate(t.calorias.activityFactor, { n: formatNumber(calorieRec.activityFactor, 2) })}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {t.calorias.tdee}: {formatNumber(calorieRec.tdee)}
                  </p>
                  {measured ? (
                    <>
                      <p className="text-xs text-muted-foreground">
                        {t.calorias.measuredTdee}:{" "}
                        {formatTemplate(t.calorias.measuredTdeeValue, {
                          n: formatNumber(measured.tdee),
                          margin: formatNumber(measured.margin),
                        })}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {formatTemplate(t.calorias.measuredTdeeDetail, {
                          intake: formatNumber(measured.avgIntake),
                          days: measured.loggedDays,
                          rate: `${measured.weightChangePerWeek > 0 ? "+" : ""}${formatNumber(measured.weightChangePerWeek, 2)}`,
                        })}
                      </p>
                    </>
                  ) : (
                    <p className="text-xs text-muted-foreground">{t.calorias.measuredTdeePending}</p>
                  )}
                </div>
              )}
              {proteinRec && (
                <div className="space-y-1 border-t border-border pt-3">
                  <p className="flex items-center gap-1.5 text-sm font-medium">
                    <DumbbellIcon className="size-3.5" /> {t.ajustes.proteinRecLabel}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {proteinRec.range.min} – {proteinRec.range.max} g/día{" "}
                    {proteinRec.basis !== "bodyWeight"
                ? formatTemplate(proteinRec.basis === "leanMass" ? t.protein.perKgLeanMass : t.protein.perKgReference, {
                          min: formatNumber(proteinRec.perKg.min, 1),
                          max: formatNumber(proteinRec.perKg.max, 1),
                          kg: formatNumber(proteinRec.basisKg, 1),
                        })
                      : formatTemplate(t.protein.perKg, {
                          min: formatNumber(proteinRec.perKg.min, 1),
                          max: formatNumber(proteinRec.perKg.max, 1),
                        })}
                  </p>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

    </main>
  );
}
