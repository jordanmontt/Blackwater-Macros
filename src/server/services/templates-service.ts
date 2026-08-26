import type { MealIngredient } from "../db/schema";
import type { MealTemplateDTO } from "@/lib/types";
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
  return {
    name: input.name,
    title: input.title,
    notes: input.notes ?? null,
    ingredients,
  };
}

function toDto(row: Awaited<ReturnType<MealTemplatesRepository["getById"]>>): MealTemplateDTO {
  if (!row) throw new Error("Plantilla no encontrada");
  return { ...row };
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

export async function deleteTemplate(
  repo: MealTemplatesRepository,
  userId: string,
  id: string,
): Promise<boolean> {
  return repo.delete(userId, id);
}
