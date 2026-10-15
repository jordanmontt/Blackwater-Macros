import type { MealTemplateDTO } from "@/lib/core/types";
import { storedMealContent } from "@/lib/meal-payload";
import type { MealTemplatesRepository, NewTemplateData } from "../repositories/templates-repo";
import type { TemplateInput } from "../validation";

function toDomainData(input: TemplateInput): NewTemplateData {
  return { name: input.name, ...storedMealContent(input) };
}

function toDto(row: Awaited<ReturnType<MealTemplatesRepository["getById"]>>): MealTemplateDTO {
  if (!row) throw new Error("Plantilla no encontrada");
  return { ...row, updatedAt: row.updatedAt.toISOString() };
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

/** Idempotent create-or-replace used by offline clients (client-generated id). */
export async function upsertTemplate(
  repo: MealTemplatesRepository,
  userId: string,
  id: string,
  input: TemplateInput,
) {
  const row = await repo.upsert(userId, id, toDomainData(input));
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