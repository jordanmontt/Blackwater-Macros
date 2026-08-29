import { eq } from "drizzle-orm";
import type { AppDb } from "../db/client";
import { users, type UserRow } from "../db/schema";

export interface NewUserData {
  username: string;
  passwordHash: string;
  isAdmin?: boolean;
}

export interface UpdateUserData {
  username?: string;
  passwordHash?: string;
  isAdmin?: boolean;
}

export interface UsersRepository {
  findByUsername(username: string): Promise<UserRow | null>;
  findById(id: string): Promise<UserRow | null>;
  list(): Promise<UserRow[]>;
  create(data: NewUserData): Promise<UserRow>;
  update(id: string, data: UpdateUserData): Promise<UserRow | null>;
  delete(id: string): Promise<boolean>;
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
    async findById(id) {
      const rows = await db.select().from(users).where(eq(users.id, id)).limit(1);
      return rows[0] ?? null;
    },
    async list() {
      return db.select().from(users).orderBy(users.createdAt, users.username);
    },
    async create({ username, passwordHash, isAdmin }) {
      const rows = await db
        .insert(users)
        .values({ username: username.toLowerCase(), passwordHash, isAdmin: isAdmin ?? false })
        .returning();
      return rows[0];
    },
    async update(id, data) {
      const rows = await db
        .update(users)
        .set({
          ...(data.username !== undefined ? { username: data.username.toLowerCase() } : {}),
          ...(data.passwordHash !== undefined ? { passwordHash: data.passwordHash } : {}),
          ...(data.isAdmin !== undefined ? { isAdmin: data.isAdmin } : {}),
        })
        .where(eq(users.id, id))
        .returning();
      return rows[0] ?? null;
    },
    async delete(id) {
      const deleted = await db.delete(users).where(eq(users.id, id)).returning({ id: users.id });
      return deleted.length === 1;
    },
  };
}