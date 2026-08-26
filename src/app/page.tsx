"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { PlusIcon } from "lucide-react";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { DayNavigator } from "@/components/meals/day-navigator";
import { MealCard } from "@/components/meals/meal-card";
import { MealForm } from "@/components/meals/meal-form";
import { SaveTemplateDialog } from "@/components/meals/save-template-dialog";
import { ThemeToggle } from "@/components/theme-toggle";
import { ProteinRecommendationCard } from "@/components/protein-recommendation";
import { CalorieRecommendationCard } from "@/components/calorie-recommendation";
import { api, ApiError } from "@/lib/api";
import { todayKey } from "@/lib/dates";
import { formatNumberEs } from "@/lib/dates";
import type { MealDTO, MealTemplateDTO } from "@/lib/types";
import { formatTemplate, t } from "@/i18n";

export default function HoyPage() {
  const [selectedDay, setSelectedDay] = useState<string>(() => todayKey());
  const [loaded, setLoaded] = useState<{ day: string; meals: MealDTO[] } | null>(null);
  const [templates, setTemplates] = useState<MealTemplateDTO[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [editingMeal, setEditingMeal] = useState<MealDTO | null>(null);
  const [deletingMeal, setDeletingMeal] = useState<MealDTO | null>(null);
  const [templateSourceMeal, setTemplateSourceMeal] = useState<MealDTO | null>(null);
  const [applyingTemplateId, setApplyingTemplateId] = useState<string | null>(null);

  // Mientras el día pedido aún no tiene datos cargados, la lista muestra "cargando".
  const meals = loaded && loaded.day === selectedDay ? loaded.meals : null;

  const refreshMeals = useCallback(async (day: string) => {
    try {
      const data = await api.listMeals(day, day);
      setLoaded({ day, meals: data });
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) toast.error(t.common.errorGeneric);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    api
      .listMeals(selectedDay, selectedDay)
      .then((data) => {
        if (!cancelled) setLoaded({ day: selectedDay, meals: data });
      })
      .catch((error) => {
        if (!cancelled && !(error instanceof ApiError && error.status === 401)) {
          toast.error(t.common.errorGeneric);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [selectedDay]);

  useEffect(() => {
    api
      .listTemplates()
      .then(setTemplates)
      .catch(() => undefined);
  }, []);

  const totals = useMemo(() => {
    const empty = { calories: 0, protein: 0, carbs: 0, fat: 0 };
    return (meals ?? []).reduce(
      (acc, meal) => ({
        calories: acc.calories + meal.resolvedCalories,
        protein: acc.protein + meal.resolvedProtein,
        carbs: acc.carbs + meal.resolvedCarbs,
        fat: acc.fat + meal.resolvedFat,
      }),
      empty,
    );
  }, [meals]);

  function openCreate() {
    setEditingMeal(null);
    setFormOpen(true);
  }

  async function handleSaved() {
    await refreshMeals(selectedDay);
  }

  async function handleDeleteConfirmed() {
    if (!deletingMeal) return;
    try {
      await api.deleteMeal(deletingMeal.id);
      setDeletingMeal(null);
      await refreshMeals(selectedDay);
    } catch {
      toast.error(t.common.errorGeneric);
    }
  }

  async function handleApplyTemplate(template: MealTemplateDTO) {
    setApplyingTemplateId(template.id);
    try {
      await api.createMeal({
        logDate: selectedDay,
        title: template.title,
        notes: template.notes,
        entryMode: "per_ingredient",
        ingredients: template.ingredients,
      });
      toast.success(formatTemplate(t.hoy.templateApplied, { name: template.name }));
      await refreshMeals(selectedDay);
    } catch {
      toast.error(t.common.errorGeneric);
    } finally {
      setApplyingTemplateId(null);
    }
  }

  return (
    <main className="mx-auto w-full max-w-2xl px-4 pt-4 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-lg font-semibold">{t.hoy.title}</h1>
        <ThemeToggle />
      </header>

      <div className="mt-3 flex items-center justify-between gap-2">
        <DayNavigator value={selectedDay} onChange={setSelectedDay} />
        <Button onClick={openCreate} size="sm">
          <PlusIcon /> <span className="hidden sm:inline">{t.hoy.addMeal}</span>
        </Button>
      </div>

      <section aria-label={t.hoy.dailyTotals} className="mt-4 grid grid-cols-2 gap-2">
        <Card>
          <CardContent className="px-4 py-3 text-center">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {t.hoy.calories}
            </p>
            <p className="text-2xl font-semibold tabular-nums">
              {formatNumberEs(totals.calories)}
            </p>
            <p className="text-xs text-muted-foreground">{t.hoy.kcalUnit}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="px-4 py-3 text-center">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {t.hoy.protein}
            </p>
            <p className="text-2xl font-semibold tabular-nums">
              {formatNumberEs(totals.protein, 1)}
            </p>
            <p className="text-xs text-muted-foreground">{t.hoy.proteinUnit}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="px-4 py-3 text-center">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {t.hoy.carbs}
            </p>
            <p className="text-2xl font-semibold tabular-nums">
              {formatNumberEs(totals.carbs, 1)}
            </p>
            <p className="text-xs text-muted-foreground">{t.hoy.gramUnit}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="px-4 py-3 text-center">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {t.hoy.fat}
            </p>
            <p className="text-2xl font-semibold tabular-nums">
              {formatNumberEs(totals.fat, 1)}
            </p>
            <p className="text-xs text-muted-foreground">{t.hoy.gramUnit}</p>
          </CardContent>
        </Card>
      </section>

      <section className="mt-4">
        <ProteinRecommendationCard dailyProtein={totals.protein} />
      </section>

      <section className="mt-4">
        <CalorieRecommendationCard dailyCalories={totals.calories} />
      </section>

      {templates.length > 0 ? (
        <section aria-label={t.hoy.templates} className="mt-4">
          <h2 className="text-sm font-medium text-muted-foreground">{t.hoy.applyTemplate}</h2>
          <div className="mt-2 flex gap-2 overflow-x-auto pb-1">
            {templates.map((template) => (
              <Button
                key={template.id}
                variant="outline"
                size="sm"
                className="shrink-0 rounded-full"
                disabled={applyingTemplateId !== null}
                onClick={() => void handleApplyTemplate(template)}
              >
                {applyingTemplateId === template.id ? t.hoy.applyingTemplate : template.name}
              </Button>
            ))}
          </div>
        </section>
      ) : null}

      <section className="mt-5 space-y-3" aria-label={t.hoy.title}>
        {meals === null ? (
          <p className="py-8 text-center text-sm text-muted-foreground">{t.common.loading}</p>
        ) : meals.length === 0 ? (
          <button
            type="button"
            onClick={openCreate}
            className="w-full rounded-xl border border-dashed py-10 text-center text-sm text-muted-foreground transition-colors hover:bg-accent"
          >
            {t.hoy.emptyDay}
          </button>
        ) : (
          meals.map((meal) => (
            <MealCard
              key={meal.id}
              meal={meal}
              onEdit={(target) => {
                setEditingMeal(target);
                setFormOpen(true);
              }}
              onDelete={(target) => setDeletingMeal(target)}
              onSaveAsTemplate={(target) => setTemplateSourceMeal(target)}
            />
          ))
        )}
      </section>

      <MealForm
        open={formOpen}
        onOpenChange={setFormOpen}
        logDate={selectedDay}
        meal={editingMeal}
        onSaved={handleSaved}
      />

      <SaveTemplateDialog
        open={templateSourceMeal !== null}
        onOpenChange={(open) => {
          if (!open) setTemplateSourceMeal(null);
        }}
        meal={templateSourceMeal}
        onSaved={(template) => {
          setTemplates((current) =>
            [...current, template].sort((a, b) => a.name.localeCompare(b.name)),
          );
          toast.success(t.meal.saveAsTemplate);
        }}
      />

      <AlertDialog
        open={deletingMeal !== null}
        onOpenChange={(open) => {
          if (!open) setDeletingMeal(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t.meal.deleteConfirmTitle}</AlertDialogTitle>
            <AlertDialogDescription>{t.meal.deleteConfirmBody}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t.meal.cancel}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => void handleDeleteConfirmed()}>
              {t.meal.delete}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </main>
  );
}
