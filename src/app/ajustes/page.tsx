"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { DownloadIcon, InfoIcon, LogOutIcon, Trash2Icon } from "lucide-react";
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
import type { CalorieGoal, CalorieProfile, MealTemplateDTO, ProteinGoal } from "@/lib/types";
import { PROTEIN_GOAL_LABELS } from "@/components/protein-recommendation";
import { cn } from "@/lib/utils";
import { useMounted } from "@/lib/use-mounted";
import { calculateCalorieRecommendation } from "@/lib/calories";
import { formatNumberEs } from "@/lib/dates";
import { t } from "@/i18n";

const CALORIE_GOAL_LABELS: Record<CalorieGoal, string> = {
  deficit: t.calorias.goalDeficit,
  maintain: t.calorias.goalMaintain,
  surplus: t.calorias.goalSurplus,
};

export default function AjustesPage() {
  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const [username, setUsername] = useState("");
  const [templates, setTemplates] = useState<MealTemplateDTO[]>([]);
  const [proteinGoal, setProteinGoal] = useState<ProteinGoal>("build");
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

  useEffect(() => {
    void api.session().then((session) => {
      setUsername(session.username);
      setProteinGoal(session.proteinGoal);
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

  const themeOptions = [
    { value: "light", label: t.ajustes.themeLight },
    { value: "dark", label: t.ajustes.themeDark },
    { value: "system", label: t.ajustes.themeSystem },
  ];

  const goalOptions: { value: ProteinGoal; label: string }[] = (
    Object.entries(PROTEIN_GOAL_LABELS) as [ProteinGoal, string][]
  ).map(([value, label]) => ({ value, label }));

  const calorieGoalOptions: { value: CalorieGoal; label: string }[] = (
    Object.entries(CALORIE_GOAL_LABELS) as [CalorieGoal, string][]
  ).map(([value, label]) => ({ value, label }));

  async function handleGoalChange(goal: ProteinGoal) {
    setProteinGoal(goal);
    try {
      await api.updateSettings({ proteinGoal: goal });
    } catch {
      toast.error(t.common.errorGeneric);
    }
  }

  async function handleCalorieProfileChange(updates: Partial<CalorieProfile>) {
    const newProfile = { ...calorieProfile, ...updates };
    setCalorieProfile(newProfile);
    try {
      await api.updateSettings(newProfile);
    } catch {
      toast.error(t.common.errorGeneric);
    }
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
          <CardTitle className="text-base">{t.ajustes.proteinGoalSection}</CardTitle>
          <CardDescription>{t.ajustes.proteinGoalDescription}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-3 gap-1 rounded-lg border p-1">
            {goalOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={proteinGoal === option.value}
                onClick={() => void handleGoalChange(option.value)}
                className={cn(
                  "rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent",
                  proteinGoal === option.value && "bg-primary text-primary-foreground",
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
          </div>

          <div className="space-y-2">
            <Label>{t.calorias.calorieGoalLabel}</Label>
            <div className="grid grid-cols-3 gap-1 rounded-lg border p-1">
              {calorieGoalOptions.map((option) => (
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
          </div>

          {calorieRec && (
            <div className="rounded-lg border bg-muted/40 p-3 space-y-2">
              <p className="text-sm font-medium">{t.calorias.tdee}</p>
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
