import type { MealIngredient } from "../db/schema";
import type { MealTemplateDTO } from "@/lib/types";
import type { MealTemplatesRepository, NewTemplateData } from "../repositories/templates-repo";
import type { TemplateInput } from "../validation";

export interface TemplatesServiceDeps {
  templates: MealTemplatesRepository;
}

function toDomainData(input: TemplateInput): NewTemplateData {
  const ingredients: MealIngredient[] = input.ingredients.map((ingredient) => ({
    name: ingredient.name,
    quantity: ingredient.quantity,
    calories: ingredient.calories,
    protein: ingredient.protein,
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
  return {
    id: row.id,
    name: row.name,
    title: row.title,
    notes: row.notes,
    ingredients: row.ingredients,
  };
}

export async function listTemplates(deps: TemplatesServiceDeps, userId: string) {
  const rows = await deps.templates.listForUser(userId);
  return rows.map((row) => toDto(row));
}

export async function createTemplate(
  deps: TemplatesServiceDeps,
  userId: string,
  input: TemplateInput,
) {
  return toDto(await deps.templates.create(userId, toDomainData(input)));
}

export async function deleteTemplate(
  deps: TemplatesServiceDeps,
  userId: string,
  id: string,
): Promise<boolean> {
  return deps.templates.delete(userId, id);
}
