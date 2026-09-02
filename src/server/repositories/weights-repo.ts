import { and, asc, eq, sql } from "drizzle-orm";
import type { AppDb } from "../db/client";
import { weights, type WeightRow } from "../db/schema";

export interface NewWeightData {
  measuredAt: Date;
  weightKg: number;
  bodyFatPct: number | null;
  note: string | null;
}

export interface WeightsRepository {
  listForUser(userId: string): Promise<WeightRow[]>;
  getById(userId: string, id: string): Promise<WeightRow | null>;
  create(userId: string, data: NewWeightData): Promise<WeightRow>;
  update(userId: string, id: string, data: NewWeightData): Promise<WeightRow | null>;
  delete(userId: string, id: string): Promise<boolean>;
}

export function createWeightsRepository(db: AppDb): WeightsRepository {
  return {
    async listForUser(userId) {
      return db
        .select()
        .from(weights)
        .where(eq(weights.userId, userId))
        .orderBy(asc(weights.measuredAt));
    },
    async getById(userId, id) {
      const rows = await db
        .select()
        .from(weights)
        .where(and(eq(weights.userId, userId), eq(weights.id, id)))
        .limit(1);
      return rows[0] ?? null;
    },
    async create(userId, data) {
      const rows = await db
        .insert(weights)
        .values({ ...data, userId })
        .returning();
      return rows[0];
    },
    async update(userId, id, data) {
      const rows = await db
        .update(weights)
        .set({ ...data, updatedAt: sql`now()` })
        .where(and(eq(weights.userId, userId), eq(weights.id, id)))
        .returning();
      return rows[0] ?? null;
    },
    async delete(userId, id) {
      const rows = await db
        .delete(weights)
        .where(and(eq(weights.userId, userId), eq(weights.id, id)))
        .returning({ id: weights.id });
      return rows.length > 0;
    },
  };
}
