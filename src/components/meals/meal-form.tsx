"use client";

import { useCallback, useState } from "react";
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
import { Sheet } from "@/components/ui/sheet";
import {
  NutritionEntryFields,
  rawNutritionDraft,
  resultToNutritionDraft,
  type IngredientDraft,
  type NutritionDraft,
  type NutritionPayload,
} from "@/components/meals/nutrition-fields";
import { api, ApiError, type MealPayload } from "@/lib/api";
import type { MealDTO } from "@/lib/core/types";
import { t } from "@/i18n";

interface MealFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  logDate: string;
  /** Meal being edited, or null to create a new one. */
  meal: MealDTO | null;
  /** Pre-filled values for a new meal (search, barcode, AI); empty form when absent. */
  initial?: NutritionDraft | null;
  onSaved: (meal: MealDTO) => void;
  /** «Buscar alimento» in the form (opens search / barcode to add one more food). */
  onSearchFood?: () => void;
  /** A food picked while the form is open, appended once per id. */
  appendRequest?: { id: number; ingredient: IngredientDraft } | null;
}

/**
 * The review form every way of adding food ends in: title, per-ingredient
 * nutrition or a single manual total, notes. In a sheet that closes by
 * dragging down or tapping outside, asking first when there are unsaved edits.
 */
export function MealForm({
  open,
  onOpenChange,
  logDate,
  meal,
  initial = null,
  onSaved,
  onSearchFood,
  appendRequest = null,
}: MealFormProps) {
  const [dirty, setDirty] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState(false);

  function requestOpenChange(next: boolean) {
    if (!next && dirty) {
      setConfirmDiscard(true);
      return;
    }
    if (!next) setDirty(false);
    onOpenChange(next);
  }

  function close() {
    setDirty(false);
    setConfirmDiscard(false);
    onOpenChange(false);
  }

  return (
    <>
      <Sheet open={open} onOpenChange={requestOpenChange} title={meal ? t.meal.editTitle : t.meal.newTitle}>
        {open ? (
          <MealFormFields
            key={`${meal?.id ?? "new"}-${logDate}`}
            meal={meal}
            initial={initial}
            logDate={logDate}
            onCancel={() => requestOpenChange(false)}
            onSaved={(saved) => {
              close();
              onSaved(saved);
            }}
            onDirtyChange={setDirty}
            onSearchFood={onSearchFood}
            appendRequest={appendRequest}
          />
        ) : null}
      </Sheet>
      <AlertDialog open={confirmDiscard} onOpenChange={setConfirmDiscard}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t.addFood.discardTitle}</AlertDialogTitle>
            <AlertDialogDescription>{t.addFood.discardBody}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t.addFood.keepEditing}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={close}>
              {t.addFood.discard}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

interface MealFormFieldsProps {
  meal: MealDTO | null;
  initial: NutritionDraft | null;
  logDate: string;
  onCancel: () => void;
  onSaved: (meal: MealDTO) => void;
  onDirtyChange: (dirty: boolean) => void;
  onSearchFood?: () => void;
  appendRequest: { id: number; ingredient: IngredientDraft } | null;
}

/**
 * Rendered only while the sheet is open, so its state initializes
 * directly from the meal being edited — no reset effects needed.
 */
function MealFormFields({
  meal,
  initial,
  logDate,
  onCancel,
  onSaved,
  onDirtyChange,
  onSearchFood,
  appendRequest,
}: MealFormFieldsProps) {
  const [pending, setPending] = useState(false);
  const [draft] = useState(() => (meal ? resultToNutritionDraft(meal) : initial ?? rawNutritionDraft()));
  // A pre-filled form is unsaved work from the start: dragging it away must ask.
  const prefilled = !meal && initial !== null;
  const reportDirty = useCallback((dirty: boolean) => onDirtyChange(dirty || prefilled), [onDirtyChange, prefilled]);

  async function handleSubmit(payload: NutritionPayload) {
    const mealPayload: MealPayload = {
      logDate,
      title: payload.title,
      notes: payload.notes,
      entryMode: payload.entryMode,
      ingredients: payload.ingredients,
      totalCalories: payload.totalCalories,
      totalProtein: payload.totalProtein,
      totalCarbs: payload.totalCarbs,
      totalFat: payload.totalFat,
    };
    setPending(true);
    try {
      const saved = meal ? await api.updateMeal(meal.id, mealPayload) : await api.createMeal(mealPayload);
      onSaved(saved);
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  return (
    <NutritionEntryFields
      draft={draft}
      pending={pending}
      submitLabel={t.meal.save}
      onCancel={onCancel}
      onSubmit={handleSubmit}
      onDirtyChange={reportDirty}
      onSearchFood={onSearchFood}
      appendRequest={appendRequest}
    />
  );
}
