import { eq } from "drizzle-orm";
import type { AppDb } from "../db/client";
import { users } from "../db/schema";
import type { ProteinGoal } from "@/lib/types";

export interface SettingsRepository {
  getProteinGoal(userId: string): Promise<ProteinGoal>;
  updateProteinGoal(userId: string, goal: ProteinGoal): Promise<void>;
}

export function createSettingsRepository(db: AppDb): SettingsRepository {
  return {
    async getProteinGoal(userId) {
      const rows = await db
        .select({ proteinGoal: users.proteinGoal })
        .from(users)
        .where(eq(users.id, userId))
        .limit(1);
      return rows[0]?.proteinGoal ?? "build";
    },
    async updateProteinGoal(userId, goal) {
      await db.update(users).set({ proteinGoal: goal }).where(eq(users.id, userId));
    },
  };
}
