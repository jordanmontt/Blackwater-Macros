import type { MealIngredient } from "../db/schema";
import { resolveMealTotals } from "@/lib/core/nutrition";
import type { IngredientInput, MealTemplateDTO } from "@/lib/core/types";
import type { MealTemplatesRepository, NewTemplateData } from "../repositories/templates-repo";
import type { TemplateInput } from "../validation";

function toDomainData(input: TemplateInput): NewTemplateData {
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
    name: input.name,
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

function toDto(row: Awaited<ReturnType<MealTemplatesRepository["getById"]>>): MealTemplateDTO {
  if (!row) throw new Error("Plantilla no encontrada");
  return { ...row, ingredients: row.ingredients as IngredientInput[], updatedAt: row.updatedAt.toISOString() };
}

export async function listTemplates(repo: MealTemplatesRepository, userId: string) {
  const rows = await repo.listForUser(userId);
  return rows.map((row) => toDto(row));
}

export async function createTemplate(
  repo: MealTemplatesRepository,
  userId: string,
  input: TemplateInput,
) {
  return toDto(await repo.create(userId, toDomainData(input)));
}

export async function updateTemplate(
  repo: MealTemplatesRepository,
  userId: string,
  id: string,
  input: TemplateInput,
) {
  const row = await repo.update(userId, id, toDomainData(input));
  if (!row) return null;
  return toDto(row);
}

export async function deleteTemplate(
  repo: MealTemplatesRepository,
  userId: string,
  id: string,
): Promise<boolean> {
  return repo.delete(userId, id);
}