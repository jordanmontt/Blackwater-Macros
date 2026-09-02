import { and, asc, eq, gte, lte, sql } from "drizzle-orm";
import type { AppDb } from "../db/client";
import { meals, type MealIngredient, type MealRow } from "../db/schema";
import type { EntryMode } from "@/lib/core/types";

export interface NewMealData {
  logDate: string;
  title: string;
  notes: string | null;
  entryMode: EntryMode;
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

export interface MealsRepository {
  listInRange(userId: string, fromKey: string | null, toKey: string | null): Promise<MealRow[]>;
  getById(userId: string, id: string): Promise<MealRow | null>;
  create(userId: string, data: NewMealData): Promise<MealRow>;
  update(userId: string, id: string, data: NewMealData): Promise<MealRow | null>;
  delete(userId: string, id: string): Promise<boolean>;
  reorder(userId: string, orderedIds: string[]): Promise<void>;
}

export function createMealsRepository(db: AppDb): MealsRepository {
  return {
    async listInRange(userId, fromKey, toKey) {
      const conditions = [eq(meals.userId, userId)];
      if (fromKey) conditions.push(gte(meals.logDate, fromKey));
      if (toKey) conditions.push(lte(meals.logDate, toKey));
      return db
        .select()
        .from(meals)
        .where(and(...conditions))
        .orderBy(asc(meals.logDate), asc(meals.sortOrder), asc(meals.createdAt));
    },
    async getById(userId, id) {
      const rows = await db
        .select()
        .from(meals)
        .where(and(eq(meals.userId, userId), eq(meals.id, id)))
        .limit(1);
      return rows[0] ?? null;
    },
    async create(userId, data) {
      const nextOrder = await db
        .select({ value: sql<number>`coalesce(max(${meals.sortOrder}) + 1, 0)` })
        .from(meals)
        .where(and(eq(meals.userId, userId), eq(meals.logDate, data.logDate)));
      const sortOrder = nextOrder[0]?.value ?? 0;
      const rows = await db
        .insert(meals)
        .values({ ...data, userId, sortOrder })
        .returning();
      return rows[0];
    },
    async update(userId, id, data) {
      const rows = await db
        .update(meals)
        .set({ ...data, updatedAt: new Date() })
        .where(and(eq(meals.userId, userId), eq(meals.id, id)))
        .returning();
      return rows[0] ?? null;
    },
    async delete(userId, id) {
      const rows = await db
        .delete(meals)
        .where(and(eq(meals.userId, userId), eq(meals.id, id)))
        .returning({ id: meals.id });
      return rows.length > 0;
    },
    async reorder(userId, orderedIds) {
      await db.transaction(async (tx) => {
        for (let i = 0; i < orderedIds.length; i++) {
          await tx
            .update(meals)
            .set({ sortOrder: i, updatedAt: new Date() })
            .where(and(eq(meals.userId, userId), eq(meals.id, orderedIds[i])));
        }
      });
    },
  };
}
