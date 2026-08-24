import { and, eq, lt } from "drizzle-orm";
import type { AppDb } from "../db/client";
import { sessions } from "../db/schema";

export interface SessionRecord {
  token: string;
  userId: string;
  expiresAt: Date;
}

export interface SessionsRepository {
  create(record: SessionRecord): Promise<void>;
  findByToken(token: string): Promise<SessionRecord | null>;
  deleteByToken(token: string): Promise<void>;
  deleteExpiredBefore(cutoff: Date): Promise<void>;
}

export function createSessionsRepository(db: AppDb): SessionsRepository {
  return {
    async create({ token, userId, expiresAt }) {
      await db.insert(sessions).values({ token, userId, expiresAt });
    },
    async findByToken(token) {
      const rows = await db.select().from(sessions).where(eq(sessions.token, token)).limit(1);
      const row = rows[0];
      return row ? { token: row.token, userId: row.userId, expiresAt: row.expiresAt } : null;
    },
    async deleteByToken(token) {
      await db.delete(sessions).where(eq(sessions.token, token));
    },
    async deleteExpiredBefore(cutoff) {
      await db.delete(sessions).where(and(lt(sessions.expiresAt, cutoff)));
    },
  };
}
