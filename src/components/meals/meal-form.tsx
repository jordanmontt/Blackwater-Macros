"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  NutritionEntryFields,
  rawNutritionDraft,
  resultToNutritionDraft,
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
  onSaved: (meal: MealDTO) => void;
}

/**
 * Create/edit form for meals. Supports the two entry modes:
 * per-ingredient nutrition or a single manual total for the whole meal.
 */
export function MealForm({ open, onOpenChange, logDate, meal, onSaved }: MealFormProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{meal ? t.meal.editTitle : t.meal.newTitle}</DialogTitle>
        </DialogHeader>
        {open ? (
          <MealFormFields
            key={`${meal?.id ?? "new"}-${logDate}`}
            meal={meal}
            logDate={logDate}
            onClose={() => onOpenChange(false)}
            onSaved={onSaved}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

interface MealFormFieldsProps {
  meal: MealDTO | null;
  logDate: string;
  onClose: () => void;
  onSaved: (meal: MealDTO) => void;
}

/**
 * Rendered only while the dialog is open, so its state initializes
 * directly from the meal being edited — no reset effects needed.
 */
function MealFormFields({ meal, logDate, onClose, onSaved }: MealFormFieldsProps) {
  const [pending, setPending] = useState(false);

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
      const saved = meal
        ? await api.updateMeal(meal.id, mealPayload)
        : await api.createMeal(mealPayload);
      onClose();
      onSaved(saved);
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  const draft = meal ? resultToNutritionDraft(meal) : rawNutritionDraft();

  return (
    <NutritionEntryFields
      draft={draft}
      pending={pending}
      submitLabel={t.meal.save}
      onCancel={onClose}
      onSubmit={handleSubmit}
    />
  );
}