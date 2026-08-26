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
import { Separator } from "@/components/ui/separator";
import { api, ApiError } from "@/lib/api";
import { formatTemplate } from "@/i18n";
import type { MealTemplateDTO, ProteinGoal } from "@/lib/types";
import { PROTEIN_GOAL_LABELS } from "@/components/protein-recommendation";
import { cn } from "@/lib/utils";
import { useMounted } from "@/lib/use-mounted";
import { t } from "@/i18n";

export default function AjustesPage() {
  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const [username, setUsername] = useState("");
  const [templates, setTemplates] = useState<MealTemplateDTO[]>([]);
  const [proteinGoal, setProteinGoal] = useState<ProteinGoal>("build");
  const mounted = useMounted();

  useEffect(() => {
    void api.session().then((session) => {
      setUsername(session.username);
      setProteinGoal(session.proteinGoal);
    });
    api
      .listTemplates()
      .then(setTemplates)
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

  async function handleGoalChange(goal: ProteinGoal) {
    setProteinGoal(goal);
    try {
      await api.updateSettings({ proteinGoal: goal });
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
