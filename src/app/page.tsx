"use client";

import { useCallback, useMemo, useState } from "react";
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { Logo } from "@/components/logo";
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
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { DayNavigator } from "@/components/meals/day-navigator";
import { MealCard } from "@/components/meals/meal-card";
import { MealForm } from "@/components/meals/meal-form";
import { NutritionRecommendationsCard } from "@/components/nutrition-recommendations";
import { api, ApiError } from "@/lib/api";
import { writeCache } from "@/lib/client-cache";
import { useCachedResource } from "@/lib/use-cached-resource";
import { todayKey } from "@/lib/core/dates";
import { formatNumberEs } from "@/lib/core/dates";
import type { MealDTO, MealTemplateDTO } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

export default function HoyPage() {
  const [selectedDay, setSelectedDay] = useState<string>(() => todayKey());
  const [formOpen, setFormOpen] = useState(false);
  const [editingMeal, setEditingMeal] = useState<MealDTO | null>(null);
  const [deletingMeal, setDeletingMeal] = useState<MealDTO | null>(null);
  const [applyingTemplateId, setApplyingTemplateId] = useState<string | null>(null);

  const mealsKey = `meals:${selectedDay}:${selectedDay}`;
  const mealsRes = useCachedResource<MealDTO[]>(
    mealsKey,
    () => api.listMeals(selectedDay, selectedDay),
    {
      onError: (error) => {
        if (!(error instanceof ApiError && error.status === 401)) {
          toast.error(t.common.errorGeneric);
        }
      },
    },
  );
  const templatesRes = useCachedResource<MealTemplateDTO[]>("templates", () =>
    api.listTemplates(),
  );

  const meals = mealsRes.data ?? null;
  const templates = templatesRes.data ?? [];

  const refreshMeals = useCallback(
    (day: string) => {
      return api.listMeals(day, day).then((data) => {
        writeCache(`meals:${day}:${day}`, data);
        return data;
      });
    },
    [],
  );

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

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
        entryMode: template.entryMode,
        ingredients: template.ingredients,
        totalCalories: template.entryMode === "total_only" ? template.totalCalories : null,
        totalProtein: template.entryMode === "total_only" ? template.totalProtein : null,
        totalCarbs: template.entryMode === "total_only" ? template.totalCarbs : null,
        totalFat: template.entryMode === "total_only" ? template.totalFat : null,
      });
      toast.success(formatTemplate(t.hoy.templateApplied, { name: template.name }));
      await refreshMeals(selectedDay);
    } catch {
      toast.error(t.common.errorGeneric);
    } finally {
      setApplyingTemplateId(null);
    }
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id || !meals) return;

    const oldIndex = meals.findIndex((m) => m.id === active.id);
    const newIndex = meals.findIndex((m) => m.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;

    const reordered = arrayMove(meals, oldIndex, newIndex);
    writeCache(mealsKey, reordered);

    api.reorderMeals(reordered.map((m) => m.id)).catch(() => {
      toast.error(t.common.errorGeneric);
      refreshMeals(selectedDay);
    });
  }

  return (
    <main className="mx-auto w-full max-w-2xl px-4 pt-4 md:pt-6">
      <header className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
        <div className="flex items-center justify-self-start gap-2">
          <Logo size="header" priority />
        </div>
        <h1 className="text-lg font-semibold text-center">{t.hoy.title}</h1>
      </header>

      <div className="mt-3 flex items-center justify-center">
        <DayNavigator value={selectedDay} onChange={setSelectedDay} />
      </div>

      <section aria-label={t.hoy.dailyTotals} className="mt-4">
        <Card className="py-3">
          <CardHeader className="pb-0 pt-2.5">
            <CardTitle className="text-base">{t.hoy.dailyTotals}</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 gap-x-3 gap-y-2 px-4 pb-2.5 sm:grid-cols-4">
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t.hoy.calories}
              </p>
              <p className="text-base font-semibold tabular-nums sm:text-lg">
                {formatNumberEs(totals.calories)}
                <span className="ml-1 text-xs font-normal text-muted-foreground">{t.hoy.kcalUnit}</span>
              </p>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t.hoy.protein}
              </p>
              <p className="text-base font-semibold tabular-nums sm:text-lg">
                {formatNumberEs(totals.protein, 1)}
                <span className="ml-1 text-xs font-normal text-muted-foreground">{t.hoy.proteinUnit}</span>
              </p>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t.hoy.carbs}
              </p>
              <p className="text-base font-semibold tabular-nums sm:text-lg">
                {formatNumberEs(totals.carbs, 1)}
                <span className="ml-1 text-xs font-normal text-muted-foreground">{t.hoy.gramUnit}</span>
              </p>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t.hoy.fat}
              </p>
              <p className="text-base font-semibold tabular-nums sm:text-lg">
                {formatNumberEs(totals.fat, 1)}
                <span className="ml-1 text-xs font-normal text-muted-foreground">{t.hoy.gramUnit}</span>
              </p>
            </div>
          </CardContent>
        </Card>
      </section>

      <section className="mt-4">
        <NutritionRecommendationsCard
          dailyCalories={totals.calories}
          dailyProtein={totals.protein}
        />
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
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={meals.map((m) => m.id)} strategy={verticalListSortingStrategy}>
              {meals.map((meal) => (
                <MealCard
                  key={meal.id}
                  meal={meal}
                  onEdit={(target) => {
                    setEditingMeal(target);
                    setFormOpen(true);
                  }}
                  onDelete={(target) => setDeletingMeal(target)}
                />
              ))}
            </SortableContext>
          </DndContext>
        )}
      </section>

      <MealForm
        open={formOpen}
        onOpenChange={setFormOpen}
        logDate={selectedDay}
        meal={editingMeal}
        onSaved={handleSaved}
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

      <Button
        onClick={openCreate}
        aria-label={t.hoy.addMeal}
        size="icon"
        className="fixed right-4 bottom-[calc(5rem+env(safe-area-inset-bottom))] z-50 size-14 rounded-full bg-tertiary text-tertiary-foreground shadow-lg hover:bg-tertiary/90 md:bottom-6"
      >
        <PlusIcon className="size-6" />
      </Button>
    </main>
  );
}
