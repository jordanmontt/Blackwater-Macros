"use client";

import { useState } from "react";
import { PlusIcon, Trash2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { api, ApiError, type MealPayload } from "@/lib/api";
import type { IngredientInput, MealDTO, EntryMode } from "@/lib/types";
import { normalizeDecimal, toDecimalInput } from "@/lib/utils";
import { t } from "@/i18n";

interface IngredientDraft {
  name: string;
  quantity: string;
  calories: string;
  protein: string;
  carbs: string;
  fat: string;
}

interface MealFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  logDate: string;
  /** Meal being edited, or null to create a new one. */
  meal: MealDTO | null;
  onSaved: (meal: MealDTO) => void;
}

const emptyIngredient: IngredientDraft = { name: "", quantity: "", calories: "", protein: "", carbs: "", fat: "" };

/** Labels for the entry-mode select; lets <SelectValue> render text instead of the raw enum. */
const modeItems = [
  { value: "per_ingredient", label: t.meal.modePerIngredient },
  { value: "total_only", label: t.meal.modeTotalOnly },
];

function draftFromMeal(meal: MealDTO | null): IngredientDraft[] {
  if (!meal) return [{ ...emptyIngredient }];
  if (meal.ingredients.length === 0) return [{ ...emptyIngredient }];
  return meal.ingredients.map((ingredient) => ({
    name: ingredient.name,
    quantity: ingredient.quantity ?? "",
    calories:
      ingredient.calories !== undefined ? toDecimalInput(ingredient.calories) : "",
    protein: ingredient.protein !== undefined ? toDecimalInput(ingredient.protein) : "",
    carbs: ingredient.carbs !== undefined ? toDecimalInput(ingredient.carbs) : "",
    fat: ingredient.fat !== undefined ? toDecimalInput(ingredient.fat) : "",
  }));
}

function parseNumber(value: string): number | undefined {
  const trimmed = value.trim().replace(",", ".");
  if (trimmed === "") return undefined;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : NaN;
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
  const [title, setTitle] = useState(() => meal?.title ?? "");
  const [notes, setNotes] = useState(() => meal?.notes ?? "");
  const [entryMode, setEntryMode] = useState<EntryMode>(() => meal?.entryMode ?? "total_only");
  const [ingredients, setIngredients] = useState<IngredientDraft[]>(() => draftFromMeal(meal));
  const [totalCalories, setTotalCalories] = useState(() =>
    meal && meal.entryMode === "total_only" && meal.totalCalories !== null
      ? toDecimalInput(meal.totalCalories)
      : "",
  );
  const [totalProtein, setTotalProtein] = useState(() =>
    meal && meal.entryMode === "total_only" && meal.totalProtein !== null
      ? toDecimalInput(meal.totalProtein)
      : "",
  );
  const [totalCarbs, setTotalCarbs] = useState(() =>
    meal && meal.entryMode === "total_only" && meal.totalCarbs !== null
      ? toDecimalInput(meal.totalCarbs)
      : "",
  );
  const [totalFat, setTotalFat] = useState(() =>
    meal && meal.entryMode === "total_only" && meal.totalFat !== null
      ? toDecimalInput(meal.totalFat)
      : "",
  );
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function updateIngredient(index: number, patch: Partial<IngredientDraft>) {
    setIngredients((current) =>
      current.map((draft, i) => (i === index ? { ...draft, ...patch } : draft)),
    );
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    const payload: MealPayload = {
      logDate,
      title: title.trim(),
      notes: notes.trim() || null,
      entryMode,
      ingredients: [],
    };

    if (entryMode === "per_ingredient") {
      const parsed: IngredientInput[] = [];
      for (const draft of ingredients) {
        const name = draft.name.trim();
        if (name === "") continue;
        const calories = parseNumber(draft.calories);
        const protein = parseNumber(draft.protein);
        const carbs = parseNumber(draft.carbs);
        const fat = parseNumber(draft.fat);
        if (Number.isNaN(calories) || Number.isNaN(protein) || Number.isNaN(carbs) || Number.isNaN(fat)) {
          setError(t.common.errorGeneric);
          return;
        }
        parsed.push({
          name,
          quantity: draft.quantity.trim() || undefined,
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

    setPending(true);
    try {
      const saved = meal
        ? await api.updateMeal(meal.id, payload)
        : await api.createMeal(payload);
      onClose();
      onSaved(saved);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t.common.errorGeneric);
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="meal-title">{t.meal.titleLabel}</Label>
            <Input
              id="meal-title"
              required
              placeholder={t.meal.titlePlaceholder}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label>{t.meal.modeLabel}</Label>
            <Select
              items={modeItems}
              value={entryMode}
              onValueChange={(value) => setEntryMode(value as EntryMode)}
            >
              <SelectTrigger className="w-full" aria-label={t.meal.modeLabel}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="per_ingredient">{t.meal.modePerIngredient}</SelectItem>
                <SelectItem value="total_only">{t.meal.modeTotalOnly}</SelectItem>
              </SelectContent>
            </Select>
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
                {ingredients.map((draft, index) => (
                  <div key={index} className="rounded-lg border p-2.5 space-y-2">
                    <div className="flex gap-2">
                      <Input
                        placeholder={t.meal.ingredientNamePlaceholder}
                        aria-label={`${t.meal.ingredientsLabel} ${index + 1}`}
                        value={draft.name}
                        onChange={(event) => updateIngredient(index, { name: event.target.value })}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label={t.meal.delete}
                        disabled={ingredients.length === 1}
                        onClick={() =>
                          setIngredients((current) => current.filter((_, i) => i !== index))
                        }
                      >
                        <Trash2Icon className="text-muted-foreground" />
                      </Button>
                    </div>
                    <div className="flex gap-2">
                      <Input
                        placeholder={t.meal.quantityPlaceholder}
                        value={draft.quantity}
                        onChange={(event) =>
                          updateIngredient(index, { quantity: event.target.value })
                        }
                      />
                      <Input
                        inputMode="decimal"
                        placeholder={t.meal.caloriesPlaceholder}
                        className="w-24"
                        value={draft.calories}
                        onChange={(event) =>
                          updateIngredient(index, {
                            calories: normalizeDecimal(event.target.value),
                          })
                        }
                      />
                      <Input
                        inputMode="decimal"
                        placeholder={t.meal.proteinPlaceholder}
                        className="w-24"
                        value={draft.protein}
                        onChange={(event) =>
                          updateIngredient(index, {
                            protein: normalizeDecimal(event.target.value),
                          })
                        }
                      />
                      <Input
                        inputMode="decimal"
                        placeholder={t.meal.carbsPlaceholder}
                        className="w-24"
                        value={draft.carbs}
                        onChange={(event) =>
                          updateIngredient(index, {
                            carbs: normalizeDecimal(event.target.value),
                          })
                        }
                      />
                      <Input
                        inputMode="decimal"
                        placeholder={t.meal.fatPlaceholder}
                        className="w-24"
                        value={draft.fat}
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
                <Label htmlFor="meal-total-kcal">{t.hoy.calories}</Label>
                <Input
                  id="meal-total-kcal"
                  inputMode="decimal"
                  placeholder="0"
                  value={totalCalories}
                  onChange={(event) => setTotalCalories(normalizeDecimal(event.target.value))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="meal-total-protein">{t.hoy.protein} (g)</Label>
                <Input
                  id="meal-total-protein"
                  inputMode="decimal"
                  placeholder="0"
                  value={totalProtein}
                  onChange={(event) => setTotalProtein(normalizeDecimal(event.target.value))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="meal-total-carbs">{t.hoy.carbs} (g)</Label>
                <Input
                  id="meal-total-carbs"
                  inputMode="decimal"
                  placeholder="0"
                  value={totalCarbs}
                  onChange={(event) => setTotalCarbs(normalizeDecimal(event.target.value))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="meal-total-fat">{t.hoy.fat} (g)</Label>
                <Input
                  id="meal-total-fat"
                  inputMode="decimal"
                  placeholder="0"
                  value={totalFat}
                  onChange={(event) => setTotalFat(normalizeDecimal(event.target.value))}
                />
              </div>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="meal-notes">{t.meal.notesLabel}</Label>
            <Textarea
              id="meal-notes"
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
            <Button type="button" variant="ghost" onClick={onClose}>
              {t.meal.cancel}
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? t.common.loading : t.meal.save}
            </Button>
          </div>
        </form>
  );
}
