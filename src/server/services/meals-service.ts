import type { MealIngredient } from "../db/schema";
import { resolveMealTotals } from "@/lib/nutrition";
import type { IngredientInput, MealDTO } from "@/lib/types";
import type { MealsRepository, NewMealData } from "../repositories/meals-repo";
import type { MealInput } from "../validation";

export interface MealsServiceDeps {
  meals: MealsRepository;
}

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
  return {
    logDate: input.logDate,
    title: input.title,
    notes: input.notes ?? null,
    entryMode: input.entryMode,
    ingredients,
    totalCalories: input.entryMode === "total_only" ? (input.totalCalories ?? null) : null,
    totalProtein: input.entryMode === "total_only" ? (input.totalProtein ?? null) : null,
    resolvedCalories: totals.calories,
    resolvedProtein: totals.protein,
  };
}

export function toMealDto(row: Awaited<ReturnType<MealsRepository["getById"]>>): MealDTO {
  if (!row) throw new Error("Comida no encontrada");
  const ingredients: IngredientInput[] = row.ingredients;
  return {
    id: row.id,
    logDate: row.logDate,
    title: row.title,
    notes: row.notes,
    entryMode: row.entryMode,
    ingredients,
    totalCalories: row.totalCalories,
    totalProtein: row.totalProtein,
    totalCarbs: null,
    totalFat: null,
    resolvedCalories: row.resolvedCalories,
    resolvedProtein: row.resolvedProtein,
    resolvedCarbs: ingredients.reduce((sum, i) => sum + (i.carbs ?? 0), 0),
    resolvedFat: ingredients.reduce((sum, i) => sum + (i.fat ?? 0), 0),
  };
}

export async function createMeal(deps: MealsServiceDeps, userId: string, input: MealInput) {
  const meal = await deps.meals.create(userId, toDomainData(input));
  return toMealDto(meal);
}

export async function updateMeal(
  deps: MealsServiceDeps,
  userId: string,
  id: string,
  input: MealInput,
) {
  const meal = await deps.meals.update(userId, id, toDomainData(input));
  if (!meal) return null;
  return toMealDto(meal);
}

export async function deleteMeal(deps: MealsServiceDeps, userId: string, id: string) {
  return deps.meals.delete(userId, id);
}

export async function listMealsInRange(
  deps: MealsServiceDeps,
  userId: string,
  fromKey: string | null,
  toKey: string | null,
) {
  const rows = await deps.meals.listInRange(userId, fromKey, toKey);
  return rows.map((row) => toMealDto(row));
}
