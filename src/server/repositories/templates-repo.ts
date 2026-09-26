import { and, asc, eq, sql } from "drizzle-orm";
import type { AppDb } from "../db/client";
import { mealTemplates, type MealIngredient, type MealTemplateRow } from "../db/schema";

export interface NewTemplateData {
  name: string;
  title: string;
  notes: string | null;
  entryMode: "per_ingredient" | "total_only";
  ingredients: MealIngredient[];
  totalCalories: number | null;
  totalProtein: number | null;
  totalCarbs: number | null;
  totalFat: number | null;
  resolvedCalories: number;
  resolvedProtein: number;
  resolvedCarbs: number;
  resolvedFat: number;
}

export interface MealTemplatesRepository {
  listForUser(userId: string): Promise<MealTemplateRow[]>;
  getById(userId: string, id: string): Promise<MealTemplateRow | null>;
  create(userId: string, data: NewTemplateData): Promise<MealTemplateRow>;
  update(userId: string, id: string, data: NewTemplateData): Promise<MealTemplateRow | null>;
  delete(userId: string, id: string): Promise<boolean>;
  /** Create-or-replace with a client-generated id (offline sync); null if the id belongs to another user. */
  upsert(userId: string, id: string, data: NewTemplateData): Promise<MealTemplateRow | null>;
}

export function createMealTemplatesRepository(db: AppDb): MealTemplatesRepository {
  return {
    async listForUser(userId) {
      return db
        .select()
        .from(mealTemplates)
        .where(eq(mealTemplates.userId, userId))
        .orderBy(asc(mealTemplates.name));
    },
    async getById(userId, id) {
      const rows = await db
        .select()
        .from(mealTemplates)
        .where(and(eq(mealTemplates.userId, userId), eq(mealTemplates.id, id)))
        .limit(1);
      return rows[0] ?? null;
    },
    async create(userId, data) {
      const rows = await db
        .insert(mealTemplates)
        .values({ ...data, userId })
        .returning();
      return rows[0];
    },
    async update(userId, id, data) {
      const rows = await db
        .update(mealTemplates)
        .set({ ...data, updatedAt: sql`now()` })
        .where(and(eq(mealTemplates.userId, userId), eq(mealTemplates.id, id)))
        .returning();
      return rows[0] ?? null;
    },
    async delete(userId, id) {
      const rows = await db
        .delete(mealTemplates)
        .where(and(eq(mealTemplates.userId, userId), eq(mealTemplates.id, id)))
        .returning({ id: mealTemplates.id });
      return rows.length > 0;
    },
    async upsert(userId, id, data) {
      const updated = await db
        .update(mealTemplates)
        .set({ ...data, updatedAt: sql`now()` })
        .where(and(eq(mealTemplates.userId, userId), eq(mealTemplates.id, id)))
        .returning();
      if (updated[0]) return updated[0];
      const inserted = await db
        .insert(mealTemplates)
        .values({ ...data, id, userId })
        .onConflictDoNothing({ target: mealTemplates.id })
        .returning();
      return inserted[0] ?? null;
    },
  };
}
