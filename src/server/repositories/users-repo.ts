import { eq } from "drizzle-orm";
import type { AppDb } from "../db/client";
import { users, type UserRow } from "../db/schema";

export interface NewUserData {
  username: string;
  passwordHash: string;
}

export interface UsersRepository {
  findByUsername(username: string): Promise<UserRow | null>;
  create(data: NewUserData): Promise<UserRow>;
}

export function createUsersRepository(db: AppDb): UsersRepository {
  return {
    async findByUsername(username) {
      const rows = await db
        .select()
        .from(users)
        .where(eq(users.username, username.toLowerCase()))
        .limit(1);
      return rows[0] ?? null;
    },
    async create({ username, passwordHash }) {
      const rows = await db
        .insert(users)
        .values({ username: username.toLowerCase(), passwordHash })
        .returning();
      return rows[0];
    },
  };
}
