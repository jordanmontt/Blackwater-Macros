"use client";

import { useCallback, useMemo, useState } from "react";
import { formatNumber } from "@/i18n/format";
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from "@dnd-kit/sortable";
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
import { AddFoodSheet } from "@/components/meals/add-food-sheet";
import { estimateNotice } from "@/components/foods/photo-estimate";
import { DayNavigator } from "@/components/meals/day-navigator";
import { MealCard } from "@/components/meals/meal-card";
import { MealForm } from "@/components/meals/meal-form";
import {
  draftWithIngredients,
  ingredientToDraft,
  type IngredientDraft,
  type NutritionDraft,
} from "@/components/meals/nutrition-fields";
import { NutritionRecommendationsCard } from "@/components/nutrition-recommendations";
import { api, ApiError, errorText, UNDO_TOAST_MS } from "@/lib/api";
import { writeCache } from "@/lib/client-cache";
import { useCachedResource } from "@/lib/use-cached-resource";
import { todayKey } from "@/lib/core/dates";
import { estimateToIngredients, type MealEstimate } from "@/lib/core/ai-schema";
import type { IngredientInput, MealDTO, MealTemplateDTO } from "@/lib/core/types";
import { t } from "@/i18n";

export default function HoyPage() {
  const [selectedDay, setSelectedDay] = useState<string>(() => todayKey());
  const [formOpen, setFormOpen] = useState(false);
  const [editingMeal, setEditingMeal] = useState<MealDTO | null>(null);
  const [deletingMeal, setDeletingMeal] = useState<MealDTO | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  // Search / barcode: a new pre-filled meal, or one more food for the open form.
  const [reviewDraft, setReviewDraft] = useState<NutritionDraft | null>(null);
  const [pickOnly, setPickOnly] = useState(false);
  const [appendRequest, setAppendRequest] = useState<{ id: number; ingredients: IngredientDraft[] } | null>(null);
  const [reviewNotice, setReviewNotice] = useState<string | null>(null);

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
    setReviewDraft(null);
    setReviewNotice(null);
    setFormOpen(true);
  }

  function openAdd() {
    setPickOnly(false);
    setAddOpen(true);
  }

  function handleFoodPicked(ingredient: IngredientInput) {
    if (pickOnly) {
      setAppendRequest({ id: Date.now(), ingredients: [ingredientToDraft(ingredient)] });
      return;
    }
    setEditingMeal(null);
    setAppendRequest(null);
    setReviewNotice(null);
    setReviewDraft(draftWithIngredients(ingredient.name, [ingredient]));
    setFormOpen(true);
  }

  function handleEstimate(estimate: MealEstimate) {
    const ingredients = estimateToIngredients(estimate);
    if (pickOnly) {
      setAppendRequest({ id: Date.now(), ingredients: ingredients.map(ingredientToDraft) });
      return;
    }
    setEditingMeal(null);
    setAppendRequest(null);
    setReviewNotice(estimateNotice(estimate));
    setReviewDraft(draftWithIngredients(estimate.title, ingredients));
    setFormOpen(true);
  }

  async function handleSaved() {
    await refreshMeals(selectedDay);
  }

  /** Asked first (the dialog), then deleted with an Undo that puts it back in its place. */
  async function handleDeleteConfirmed() {
    if (!deletingMeal) return;
    const meal = deletingMeal;
    const day = selectedDay;
    const orderedIds = (meals ?? []).map((m) => m.id);
    try {
      await api.deleteMeal(meal.id);
      setDeletingMeal(null);
      await refreshMeals(day);
      toast.success(t.meal.deleted, {
        duration: UNDO_TOAST_MS,
        action: {
          label: t.common.undo,
          onClick: () => {
            api
              .restoreMeal(meal, orderedIds)
              .then(() => refreshMeals(day))
              .catch((error) => toast.error(errorText(error)));
          },
        },
      });
    } catch (error) {
      toast.error(errorText(error));
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
        <div />
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
                {formatNumber(totals.calories)}
                <span className="ml-1 text-xs font-normal text-muted-foreground">{t.hoy.kcalUnit}</span>
              </p>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t.hoy.protein}
              </p>
              <p className="text-base font-semibold tabular-nums sm:text-lg">
                {formatNumber(totals.protein, 1)}
                <span className="ml-1 text-xs font-normal text-muted-foreground">{t.hoy.proteinUnit}</span>
              </p>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t.hoy.carbs}
              </p>
              <p className="text-base font-semibold tabular-nums sm:text-lg">
                {formatNumber(totals.carbs, 1)}
                <span className="ml-1 text-xs font-normal text-muted-foreground">{t.hoy.gramUnit}</span>
              </p>
            </div>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {t.hoy.fat}
              </p>
              <p className="text-base font-semibold tabular-nums sm:text-lg">
                {formatNumber(totals.fat, 1)}
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

      <section className="mt-5 space-y-3" aria-label={t.hoy.title}>
        {meals === null ? (
          <p className="py-8 text-center text-sm text-muted-foreground">{t.common.loading}</p>
        ) : meals.length === 0 ? (
          <button
            type="button"
            onClick={openAdd}
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

      <AddFoodSheet
        open={addOpen}
        onOpenChange={setAddOpen}
        day={selectedDay}
        templates={templates}
        onManual={openCreate}
        onAdded={() => refreshMeals(selectedDay)}
        onFoodPicked={handleFoodPicked}
        onEstimate={handleEstimate}
        pickOnly={pickOnly}
      />

      <MealForm
        open={formOpen}
        onOpenChange={(open) => {
          setFormOpen(open);
          if (!open) {
            // A pending item or pre-filled draft belongs to the form that just closed.
            setAppendRequest(null);
            setReviewDraft(null);
            setReviewNotice(null);
          }
        }}
        logDate={selectedDay}
        meal={editingMeal}
        initial={editingMeal ? null : reviewDraft}
        onSaved={handleSaved}
        onSearchFood={() => {
          setPickOnly(true);
          setAddOpen(true);
        }}
        appendRequest={appendRequest}
        notice={editingMeal ? null : reviewNotice}
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
        onClick={openAdd}
        aria-label={t.hoy.addMeal}
        size="icon"
        className="fixed right-4 bottom-[calc(5rem+env(safe-area-inset-bottom))] z-50 size-14 rounded-full bg-tertiary text-tertiary-foreground shadow-lg hover:bg-tertiary/90 md:bottom-6"
      >
        <PlusIcon className="size-6" />
      </Button>
    </main>
  );
}
