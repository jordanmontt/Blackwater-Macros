import { hashPassword } from "@/server/auth/password";
import type { UsersRepository } from "@/server/repositories/users-repo";
import type { SessionsRepository } from "@/server/repositories/sessions-repo";
import type { MealsRepository } from "@/server/repositories/meals-repo";
import type { MealTemplatesRepository } from "@/server/repositories/templates-repo";
import type { WeightsRepository } from "@/server/repositories/weights-repo";
import type { SettingsRepository } from "@/server/repositories/settings-repo";
import type { MealTemplateRow } from "@/server/db/schema";
import type { AppDeps } from "../src/deps";

type UserRow = NonNullable<Awaited<ReturnType<UsersRepository["findByUsername"]>>>;
type MealRow = Awaited<ReturnType<MealsRepository["listInRange"]>>[number];
type WeightRow = Awaited<ReturnType<WeightsRepository["listForUser"]>>[number];
type ProfileRow = NonNullable<Awaited<ReturnType<SettingsRepository["getCalorieProfile"]>>>;

/** Repositorios en memoria que imitan la base de datos (sin red ni SQL). */
export function makeMemoryDeps(): AppDeps {
  const users: UserRow[] = [];
  const sessions = new Map<string, { token: string; userId: string; expiresAt: Date }>();
  const meals: MealRow[] = [];
  const templates: MealTemplateRow[] = [];
  const weights: WeightRow[] = [];
  const profile = new Map<string, Pick<ProfileRow, "calorieGoal">>();

  let counter = 0;
  const nextUuid = () =>
    `00000000-0000-4000-8000-${String(++counter).padStart(12, "0")}`;

  const usersRepo: UsersRepository = {
    async findByUsername(username: string) {
      return users.find((user) => user.username === username.toLowerCase()) ?? null;
    },
    async create({ username, passwordHash }) {
      const user = {
        id: nextUuid(),
        username: username.toLowerCase(),
        passwordHash,
        gender: null,
        birthYear: null,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: null,
        createdAt: new Date(),
      } as UserRow;
      users.push(user);
      return user;
    },
  };

  const sessionsRepo: SessionsRepository = {
    async create({ token, userId, expiresAt }) {
      sessions.set(token, { token, userId, expiresAt });
    },
    async findByToken(token: string) {
      const record = sessions.get(token);
      return record ? { ...record } : null;
    },
    async deleteByToken(token: string) {
      sessions.delete(token);
    },
    async deleteExpiredBefore() {
      for (const [token, record] of sessions) {
        if (record.expiresAt.getTime() <= Date.now()) sessions.delete(token);
      }
    },
  };

  const mealsRepo: MealsRepository = {
    async listInRange(userId) {
      return meals.filter((meal) => meal.userId === userId);
    },
    async getById(userId, id) {
      return meals.find((meal) => meal.userId === userId && meal.id === id) ?? null;
    },
    async create(userId, data) {
      const meal = {
        id: nextUuid(),
        userId,
        ...data,
        sortOrder: 0,
        createdAt: new Date(),
        updatedAt: new Date(),
      } as MealRow;
      meals.push(meal);
      return meal;
    },
    async update(userId, id, data) {
      const meal = meals.find((item) => item.userId === userId && item.id === id);
      if (!meal) return null;
      Object.assign(meal, data, { updatedAt: new Date() });
      return meal;
    },
    async delete(userId, id) {
      const index = meals.findIndex((item) => item.userId === userId && item.id === id);
      if (index === -1) return false;
      meals.splice(index, 1);
      return true;
    },
    async reorder(userId, orderedIds) {
      const owned = meals.filter((meal) => meal.userId === userId);
      orderedIds.forEach((id, sortOrder) => {
        const meal = owned.find((item) => item.id === id);
        if (meal) meal.sortOrder = sortOrder;
      });
    },
  };

  const templatesRepo: MealTemplatesRepository = {
    async listForUser(userId) {
      return templates.filter((template) => template.userId === userId);
    },
    async getById(userId, id) {
      return templates.find((template) => template.userId === userId && template.id === id) ?? null;
    },
    async create(userId, data) {
      const template = { id: nextUuid(), userId, ...data, createdAt: new Date() } as MealTemplateRow;
      templates.push(template);
      return template;
    },
    async update(userId, id, data) {
      const template = templates.find((item) => item.userId === userId && item.id === id);
      if (!template) return null;
      Object.assign(template, data);
      return template;
    },
    async delete(userId, id) {
      const index = templates.findIndex((item) => item.userId === userId && item.id === id);
      if (index === -1) return false;
      templates.splice(index, 1);
      return true;
    },
  };

  const weightsRepo: WeightsRepository = {
    async listForUser(userId) {
      return weights.filter((weight) => weight.userId === userId);
    },
    async getById(userId, id) {
      return weights.find((weight) => weight.userId === userId && weight.id === id) ?? null;
    },
    async create(userId, data) {
      const weight = { id: nextUuid(), userId, ...data } as WeightRow;
      weights.push(weight);
      return weight;
    },
    async update(userId, id, data) {
      const weight = weights.find((item) => item.userId === userId && item.id === id);
      if (!weight) return null;
      Object.assign(weight, data);
      return weight;
    },
    async delete(userId, id) {
      const index = weights.findIndex((item) => item.userId === userId && item.id === id);
      if (index === -1) return false;
      weights.splice(index, 1);
      return true;
    },
  };

  const settingsRepo: SettingsRepository = {
    async getCalorieProfile(userId) {
      const row = profile.get(userId);
      return {
        gender: null,
        birthYear: null,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: row?.calorieGoal ?? null,
      };
    },
    async updateCalorieProfile(userId, p) {
      profile.set(userId, { calorieGoal: p.calorieGoal });
    },
  };

  return {
    repositories: {
      meals: mealsRepo,
      templates: templatesRepo,
      weights: weightsRepo,
      settings: settingsRepo,
    },
    auth: { users: usersRepo, sessions: sessionsRepo },
    stats: { meals: mealsRepo, weights: weightsRepo },
    async getSession(userId) {
      const user = users.find((item) => item.id === userId);
      if (!user) return null;
      return {
        username: user.username,
        calorieProfile: await settingsRepo.getCalorieProfile(userId),
      };
    },
  };
}

export async function seedUser(
  deps: AppDeps,
  username = "sebastian",
  password = "clave-fuerte",
): Promise<{ userId: string; username: string; password: string }> {
  const user = await deps.auth.users.create({
    username,
    passwordHash: await hashPassword(password),
  });
  return { userId: user.id, username: user.username, password };
}