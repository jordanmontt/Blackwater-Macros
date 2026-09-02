import type { MealIngredient } from "../db/schema";
import { resolveMealTotals } from "@/lib/core/nutrition";
import type { IngredientInput, MealDTO } from "@/lib/core/types";
import type { MealsRepository, NewMealData } from "../repositories/meals-repo";
import type { MealInput } from "../validation";

function toDomainData(input: MealInput): NewMealData {
  const ingredients: MealIngredient[] = input.ingredients.map((ingredient) => ({
    name: ingredient.name,
    quantity: ingredient.quantity,
    calories: ingredient.calories,
    protein: ingredient.protein,
    carbs: ingredient.carbs,
    fat: ingredient.fat,
  }));
  const totals = resolveMealTotals(
    input.entryMode,
    ingredients,
    input.totalCalories ?? null,
    input.totalProtein ?? null,
    input.totalCarbs ?? null,
    input.totalFat ?? null,
  );
  const isTotalOnly = input.entryMode === "total_only";
  return {
    logDate: input.logDate,
    title: input.title,
    notes: input.notes ?? null,
    entryMode: input.entryMode,
    ingredients,
    totalCalories: isTotalOnly ? (input.totalCalories ?? null) : null,
    totalProtein: isTotalOnly ? (input.totalProtein ?? null) : null,
    totalCarbs: isTotalOnly ? (input.totalCarbs ?? null) : null,
    totalFat: isTotalOnly ? (input.totalFat ?? null) : null,
    resolvedCalories: totals.calories,
    resolvedProtein: totals.protein,
    resolvedCarbs: totals.carbs,
    resolvedFat: totals.fat,
  };
}

export function toMealDto(row: Awaited<ReturnType<MealsRepository["getById"]>>): MealDTO {
  if (!row) throw new Error("Comida no encontrada");
  return {
    ...row,
    ingredients: row.ingredients as IngredientInput[],
    updatedAt: row.updatedAt.toISOString(),
  };
}

export async function createMeal(repo: MealsRepository, userId: string, input: MealInput) {
  const meal = await repo.create(userId, toDomainData(input));
  return toMealDto(meal);
}

export async function updateMeal(
  repo: MealsRepository,
  userId: string,
  id: string,
  input: MealInput,
) {
  const meal = await repo.update(userId, id, toDomainData(input));
  if (!meal) return null;
  return toMealDto(meal);
}

export async function deleteMeal(repo: MealsRepository, userId: string, id: string) {
  return repo.delete(userId, id);
}

export async function listMealsInRange(
  repo: MealsRepository,
  userId: string,
  fromKey: string | null,
  toKey: string | null,
) {
  const rows = await repo.listInRange(userId, fromKey, toKey);
  return rows.map((row) => toMealDto(row));
}

export async function reorderMeals(
  repo: MealsRepository,
  userId: string,
  orderedIds: string[],
) {
  await repo.reorder(userId, orderedIds);
}
