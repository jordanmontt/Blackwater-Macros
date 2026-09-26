"use client";

import { useState } from "react";
import { PlusIcon, Trash2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import type { EntryMode, IngredientInput } from "@/lib/core/types";
import { normalizeDecimal, toDecimalInput } from "@/lib/utils";
import { t } from "@/i18n";

export interface IngredientDraft {
  name: string;
  quantity: string;
  calories: string;
  protein: string;
  carbs: string;
  fat: string;
}

export const emptyIngredient: IngredientDraft = {
  name: "",
  quantity: "",
  calories: "",
  protein: "",
  carbs: "",
  fat: "",
};

export interface NutritionDraft {
  title: string;
  notes: string;
  entryMode: EntryMode;
  ingredients: IngredientDraft[];
  totalCalories: string;
  totalProtein: string;
  totalCarbs: string;
  totalFat: string;
}

export interface NutritionPayload {
  title: string;
  notes: string | null;
  entryMode: EntryMode;
  ingredients: IngredientInput[];
  totalCalories: number | null;
  totalProtein: number | null;
  totalCarbs: number | null;
  totalFat: number | null;
}

export function rawNutritionDraft(): NutritionDraft {
  return {
    title: "",
    notes: "",
    entryMode: "per_ingredient",
    ingredients: [{ ...emptyIngredient }],
    totalCalories: "",
    totalProtein: "",
    totalCarbs: "",
    totalFat: "",
  };
}

export function resultToNutritionDraft(nutrition: {
  title: string;
  notes: string | null;
  entryMode: EntryMode;
  ingredients: IngredientInput[];
  totalCalories: number | null;
  totalProtein: number | null;
  totalCarbs: number | null;
  totalFat: number | null;
}): NutritionDraft {
  const ingredients = nutrition.ingredients.map((ingredient) => ({
    name: ingredient.name,
    quantity: ingredient.quantity ?? "",
    calories: ingredient.calories !== undefined ? toDecimalInput(ingredient.calories) : "",
    protein: ingredient.protein !== undefined ? toDecimalInput(ingredient.protein) : "",
    carbs: ingredient.carbs !== undefined ? toDecimalInput(ingredient.carbs) : "",
    fat: ingredient.fat !== undefined ? toDecimalInput(ingredient.fat) : "",
  }));
  const isTotalOnly = nutrition.entryMode === "total_only";
  return {
    title: nutrition.title,
    notes: nutrition.notes ?? "",
    entryMode: nutrition.entryMode,
    ingredients: ingredients.length > 0 ? ingredients : [{ ...emptyIngredient }],
    totalCalories:
      isTotalOnly && nutrition.totalCalories !== null ? toDecimalInput(nutrition.totalCalories) : "",
    totalProtein:
      isTotalOnly && nutrition.totalProtein !== null ? toDecimalInput(nutrition.totalProtein) : "",
    totalCarbs:
      isTotalOnly && nutrition.totalCarbs !== null ? toDecimalInput(nutrition.totalCarbs) : "",
    totalFat: isTotalOnly && nutrition.totalFat !== null ? toDecimalInput(nutrition.totalFat) : "",
  };
}

function parseNumber(value: string): number | undefined {
  const trimmed = value.trim().replace(",", ".");
  if (trimmed === "") return undefined;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : NaN;
}

interface NutritionEntryFieldsProps {
  /** Initial values; the parent should key the component so state re-initializes. */
  draft: NutritionDraft;
  pending?: boolean;
  submitLabel: string;
  onCancel: () => void;
  onSubmit: (payload: NutritionPayload) => Promise<void>;
}

/**
 * Shared meal-nutrition entry form used by both MealForm and TemplateForm:
 * title, entry mode («Por ingrediente» / «Solo total»), ingredient editor or
 * manual totals, and notes. Owns all its state and reports the parsed payload
 * through onSubmit.
 */
export function NutritionEntryFields({
  draft,
  pending = false,
  submitLabel,
  onCancel,
  onSubmit,
}: NutritionEntryFieldsProps) {
  const [title, setTitle] = useState(draft.title);
  const [notes, setNotes] = useState(draft.notes);
  const [entryMode, setEntryMode] = useState<EntryMode>(draft.entryMode);
  const [ingredients, setIngredients] = useState<IngredientDraft[]>(draft.ingredients);
  const [totalCalories, setTotalCalories] = useState(draft.totalCalories);
  const [totalProtein, setTotalProtein] = useState(draft.totalProtein);
  const [totalCarbs, setTotalCarbs] = useState(draft.totalCarbs);
  const [totalFat, setTotalFat] = useState(draft.totalFat);
  const [error, setError] = useState<string | null>(null);

  function updateIngredient(index: number, patch: Partial<IngredientDraft>) {
    setIngredients((current) =>
      current.map((item, i) => (i === index ? { ...item, ...patch } : item)),
    );
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    if (title.trim() === "") {
      setError(t.meal.titleRequired);
      return;
    }

    const payload: NutritionPayload = {
      title: title.trim(),
      notes: notes.trim() || null,
      entryMode,
      ingredients: [],
      totalCalories: null,
      totalProtein: null,
      totalCarbs: null,
      totalFat: null,
    };

    if (entryMode === "per_ingredient") {
      const parsed: IngredientInput[] = [];
      for (const item of ingredients) {
        const name = item.name.trim();
        if (name === "") continue;
        const calories = parseNumber(item.calories);
        const protein = parseNumber(item.protein);
        const carbs = parseNumber(item.carbs);
        const fat = parseNumber(item.fat);
        if (Number.isNaN(calories) || Number.isNaN(protein) || Number.isNaN(carbs) || Number.isNaN(fat)) {
          setError(t.common.errorGeneric);
          return;
        }
        parsed.push({
          name,
          quantity: item.quantity.trim() || undefined,
          ...(calories !== undefined ? { calories } : {}),
          ...(protein !== undefined ? { protein } : {}),
          ...(carbs !== undefined ? { carbs } : {}),
          ...(fat !== undefined ? { fat } : {}),
        });
      }
      if (parsed.length === 0) {
        setError(t.meal.ingredientsLabel);
        return;
      }
      payload.ingredients = parsed;
    } else {
      const kcal = parseNumber(totalCalories) ?? 0;
      const prot = parseNumber(totalProtein) ?? 0;
      const carb = parseNumber(totalCarbs) ?? 0;
      const fatVal = parseNumber(totalFat) ?? 0;
      if (Number.isNaN(kcal) || Number.isNaN(prot) || Number.isNaN(carb) || Number.isNaN(fatVal)) {
        setError(t.common.errorGeneric);
        return;
      }
      payload.totalCalories = kcal;
      payload.totalProtein = prot;
      payload.totalCarbs = carb;
      payload.totalFat = fatVal;
    }

    await onSubmit(payload);
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="nutrition-title">{t.meal.titleLabel}</Label>
        <Input
          id="nutrition-title"
          placeholder={t.meal.titlePlaceholder}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
        />
      </div>

      <div className="space-y-2">
        <Label>{t.meal.modeLabel}</Label>
        <NativeSelect
          aria-label={t.meal.modeLabel}
          value={entryMode}
          onChange={(event) => setEntryMode(event.target.value as EntryMode)}
        >
          <option value="per_ingredient">{t.meal.modePerIngredient}</option>
          <option value="total_only">{t.meal.modeTotalOnly}</option>
        </NativeSelect>
        <p className="text-xs text-muted-foreground">
          {entryMode === "per_ingredient"
            ? t.meal.modePerIngredientHint
            : t.meal.modeTotalOnlyHint}
        </p>
      </div>

      {entryMode === "per_ingredient" ? (
        <div className="space-y-2">
          <Label>{t.meal.ingredientsLabel}</Label>
          <div className="space-y-3">
            {ingredients.map((item, index) => (
              <div key={index} className="rounded-lg border p-2.5 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex min-w-0 flex-1 gap-2">
                    <Input
                      placeholder={t.meal.ingredientNamePlaceholder}
                      aria-label={`${t.meal.ingredientsLabel} ${index + 1}`}
                      className="min-w-0 flex-1"
                      value={item.name}
                      onChange={(event) => updateIngredient(index, { name: event.target.value })}
                    />
                    <Input
                      placeholder={t.meal.quantityPlaceholder}
                      className="w-24 shrink-0"
                      value={item.quantity}
                      onChange={(event) =>
                        updateIngredient(index, { quantity: event.target.value })
                      }
                    />
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="size-7 shrink-0"
                    aria-label={t.meal.delete}
                    disabled={ingredients.length === 1}
                    onClick={() =>
                      setIngredients((current) => current.filter((_, i) => i !== index))
                    }
                  >
                    <Trash2Icon className="size-4 text-muted-foreground" />
                  </Button>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    inputMode="decimal"
                    placeholder={t.meal.caloriesPlaceholder}
                    value={item.calories}
                    onChange={(event) =>
                      updateIngredient(index, {
                        calories: normalizeDecimal(event.target.value),
                      })
                    }
                  />
                  <Input
                    inputMode="decimal"
                    placeholder={t.meal.proteinPlaceholder}
                    value={item.protein}
                    onChange={(event) =>
                      updateIngredient(index, {
                        protein: normalizeDecimal(event.target.value),
                      })
                    }
                  />
                  <Input
                    inputMode="decimal"
                    placeholder={t.meal.carbsPlaceholder}
                    value={item.carbs}
                    onChange={(event) =>
                      updateIngredient(index, {
                        carbs: normalizeDecimal(event.target.value),
                      })
                    }
                  />
                  <Input
                    inputMode="decimal"
                    placeholder={t.meal.fatPlaceholder}
                    value={item.fat}
                    onChange={(event) =>
                      updateIngredient(index, {
                        fat: normalizeDecimal(event.target.value),
                      })
                    }
                  />
                </div>
              </div>
            ))}
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setIngredients((current) => [...current, { ...emptyIngredient }])}
          >
            <PlusIcon /> {t.meal.addIngredient}
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-2">
          <div className="space-y-2">
            <Label htmlFor="nutrition-total-kcal">{t.hoy.calories}</Label>
            <Input
              id="nutrition-total-kcal"
              inputMode="decimal"
              placeholder="0"
              value={totalCalories}
              onChange={(event) => setTotalCalories(normalizeDecimal(event.target.value))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="nutrition-total-protein">{t.hoy.protein} (g)</Label>
            <Input
              id="nutrition-total-protein"
              inputMode="decimal"
              placeholder="0"
              value={totalProtein}
              onChange={(event) => setTotalProtein(normalizeDecimal(event.target.value))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="nutrition-total-carbs">{t.hoy.carbs} (g)</Label>
            <Input
              id="nutrition-total-carbs"
              inputMode="decimal"
              placeholder="0"
              value={totalCarbs}
              onChange={(event) => setTotalCarbs(normalizeDecimal(event.target.value))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="nutrition-total-fat">{t.hoy.fat} (g)</Label>
            <Input
              id="nutrition-total-fat"
              inputMode="decimal"
              placeholder="0"
              value={totalFat}
              onChange={(event) => setTotalFat(normalizeDecimal(event.target.value))}
            />
          </div>
        </div>
      )}

      <div className="space-y-2">
        <Label htmlFor="nutrition-notes">{t.meal.notesLabel}</Label>
        <Textarea
          id="nutrition-notes"
          rows={2}
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
        />
      </div>

      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}

      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="ghost" onClick={onCancel}>
          {t.meal.cancel}
        </Button>
        <Button type="submit" disabled={pending}>
          {pending ? t.common.loading : submitLabel}
        </Button>
      </div>
    </form>
  );
}