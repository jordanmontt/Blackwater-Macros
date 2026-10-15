import type { MealDTO } from "@/lib/core/types";
import { storedMealContent } from "@/lib/meal-payload";
import type { MealsRepository, NewMealData } from "../repositories/meals-repo";
import type { MealInput } from "../validation";

function toDomainData(input: MealInput): NewMealData {
  return { logDate: input.logDate, ...storedMealContent(input) };
}

export function toMealDto(row: Awaited<ReturnType<MealsRepository["getById"]>>): MealDTO {
  if (!row) throw new Error("Comida no encontrada");
  return { ...row, updatedAt: row.updatedAt.toISOString() };
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

/** Idempotent create-or-replace used by offline clients (client-generated id). */
export async function upsertMeal(
  repo: MealsRepository,
  userId: string,
  id: string,
  input: MealInput,
  sortOrder?: number,
) {
  const meal = await repo.upsert(userId, id, toDomainData(input), sortOrder);
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
