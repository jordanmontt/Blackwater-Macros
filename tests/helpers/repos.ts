import type { MealsRepository } from "@/server/repositories/meals-repo";
import type { MealTemplatesRepository } from "@/server/repositories/templates-repo";
import type { WeightsRepository } from "@/server/repositories/weights-repo";
import type { SettingsRepository } from "@/server/repositories/settings-repo";
import type { UsersRepository } from "@/server/repositories/users-repo";
import type { SessionsRepository } from "@/server/repositories/sessions-repo";
import type { AuthServiceDeps } from "@/server/services/auth-service";
import type { StatsServiceDeps } from "@/server/services/stats-service";
import type { MealRow, MealTemplateRow, WeightRow, UserRow } from "@/server/db/schema";
import type { CalorieProfile, MealTemplateDTO, WeightDTO } from "@/lib/core/types";

/**
 * Infraestructura en memoria para probar rutas HTTP (handlers de la API) sin
 * base de datos: parametriza los repositories y serviceDeps que cada handler
 * importa de "@/server/composition".
 */

interface SessionRow {
  token: string;
  userId: string;
  expiresAt: Date;
}

export interface MemoryWorld {
  repositories: {
    users: UsersRepository;
    sessions: SessionsRepository;
    meals: MealsRepository;
    templates: MealTemplatesRepository;
    weights: WeightsRepository;
    settings: SettingsRepository;
  };
  serviceDeps: {
    auth: AuthServiceDeps;
    stats: StatsServiceDeps;
  };
  /** Colecciones expuestas para sembrar y comprobar datos. */
  data: {
    meals: Map<string, MealRow>;
    templates: Map<string, MealTemplateRow>;
    weights: Map<string, WeightRow>;
    settings: Map<string, CalorieProfile>;
    users: Map<string, UserRow>;
    sessions: Map<string, SessionRow>;
  };
  reset(): void;
}

export function createMemoryWorld(): MemoryWorld {
  const mealsData = new Map<string, MealRow>();
  const templatesData = new Map<string, MealTemplateRow>();
  const weightsData = new Map<string, WeightRow>();
  const settingsData = new Map<string, CalorieProfile>();
  const usersData = new Map<string, UserRow>();
  const sessionsData = new Map<string, SessionRow>();

  const emptyProfile: CalorieProfile = {
    gender: null,
    birthYear: null,
    heightCm: null,
    gymDaysPerWeek: null,
    gymSessionMinutes: null,
    walkingMinutesPerDay: null,
    calorieGoal: null,
  };

  const meals: MealsRepository = {
    async listInRange(userId, from, to) {
      return [...mealsData.values()]
        .filter(
          (row) =>
            row.userId === userId &&
            (from === null || row.logDate >= from) &&
            (to === null || row.logDate <= to),
        )
        .sort((a, b) => a.logDate.localeCompare(b.logDate) || a.sortOrder - b.sortOrder);
    },
    async getById(userId, id) {
      const found = mealsData.get(id);
      return found && found.userId === userId ? found : null;
    },
    async create(userId, input) {
      const id = `meal-${mealsData.size + 1}`;
      const sameDay = [...mealsData.values()].filter(
        (row) => row.userId === userId && row.logDate === input.logDate,
      );
      const sortOrder = sameDay.length
        ? Math.max(...sameDay.map((row) => row.sortOrder)) + 1
        : 0;
      const now = new Date();
      const row: MealRow = { id, userId, createdAt: now, updatedAt: now, sortOrder, ...input };
      mealsData.set(id, row);
      return row;
    },
    async update(userId, id, input) {
      const existing = mealsData.get(id);
      if (!existing || existing.userId !== userId) return null;
      const updated: MealRow = { ...existing, ...input, updatedAt: new Date() };
      mealsData.set(id, updated);
      return updated;
    },
    async delete(userId, id) {
      const existing = mealsData.get(id);
      if (!existing || existing.userId !== userId) return false;
      mealsData.delete(id);
      return true;
    },
    async reorder(userId, orderedIds) {
      orderedIds.forEach((id, index) => {
        const row = mealsData.get(id);
        if (row && row.userId === userId) mealsData.set(id, { ...row, sortOrder: index });
      });
    },
  };

  const templates: MealTemplatesRepository = {
    async listForUser(userId) {
      return [...templatesData.values()]
        .filter((row) => row.userId === userId)
        .sort((a, b) => a.name.localeCompare(b.name));
    },
    async getById(userId, id) {
      const found = templatesData.get(id);
      return found && found.userId === userId ? found : null;
    },
    async create(userId, input) {
      const id = `tpl-${templatesData.size + 1}`;
      const now = new Date();
      const row: MealTemplateRow = { id, userId, createdAt: now, updatedAt: now, ...input };
      templatesData.set(id, row);
      return row;
    },
    async update(userId, id, input) {
      const existing = templatesData.get(id);
      if (!existing || existing.userId !== userId) return null;
      const updated: MealTemplateRow = { ...existing, ...input, updatedAt: new Date() };
      templatesData.set(id, updated);
      return updated;
    },
    async delete(userId, id) {
      const existing = templatesData.get(id);
      if (!existing || existing.userId !== userId) return false;
      templatesData.delete(id);
      return true;
    },
  };

  const weights: WeightsRepository = {
    async listForUser(userId) {
      return [...weightsData.values()]
        .filter((row) => row.userId === userId)
        .sort((a, b) => a.measuredAt.getTime() - b.measuredAt.getTime());
    },
    async getById(userId, id) {
      const found = weightsData.get(id);
      return found && found.userId === userId ? found : null;
    },
    async create(userId, input) {
      const id = `wt-${weightsData.size + 1}`;
      const now = new Date();
      const row: WeightRow = { id, userId, createdAt: now, updatedAt: now, ...input };
      weightsData.set(id, row);
      return row;
    },
    async update(userId, id, input) {
      const existing = weightsData.get(id);
      if (!existing || existing.userId !== userId) return null;
      const updated: WeightRow = { ...existing, ...input, updatedAt: new Date() };
      weightsData.set(id, updated);
      return updated;
    },
    async delete(userId, id) {
      const existing = weightsData.get(id);
      if (!existing || existing.userId !== userId) return false;
      weightsData.delete(id);
      return true;
    },
  };

  const settings: SettingsRepository = {
    async getCalorieProfile(userId) {
      return settingsData.get(userId) ?? emptyProfile;
    },
    async updateCalorieProfile(userId, profile) {
      settingsData.set(userId, profile);
    },
  };

  const users: UsersRepository = {
    async findByUsername(username) {
      return (
        [...usersData.values()].find((user) => user.username === username.toLowerCase()) ?? null
      );
    },
    async findById(id) {
      return usersData.get(id) ?? null;
    },
    async list() {
      return [...usersData.values()].sort(
        (a, b) => a.createdAt.getTime() - b.createdAt.getTime(),
      );
    },
    async create({ username, passwordHash, isAdmin }) {
      const id = `u-${usersData.size + 1}`;
      const row: UserRow = {
        id,
        username: username.toLowerCase(),
        passwordHash,
        isAdmin: isAdmin ?? false,
        gender: null,
        birthYear: null,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: null,
        createdAt: new Date(),
      };
      usersData.set(id, row);
      return row;
    },
    async update(id, data) {
      const existing = usersData.get(id);
      if (!existing) return null;
      const updated: UserRow = {
        ...existing,
        ...(data.username !== undefined ? { username: data.username.toLowerCase() } : {}),
        ...(data.passwordHash !== undefined ? { passwordHash: data.passwordHash } : {}),
        ...(data.isAdmin !== undefined ? { isAdmin: data.isAdmin } : {}),
      };
      usersData.set(id, updated);
      return updated;
    },
    async delete(id) {
      return usersData.delete(id);
    },
  };

  const sessions: SessionsRepository = {
    async create({ token, userId, expiresAt }) {
      sessionsData.set(token, { token, userId, expiresAt });
    },
    async findByToken(token) {
      return sessionsData.get(token) ?? null;
    },
    async deleteByToken(token) {
      sessionsData.delete(token);
    },
    async deleteExpiredBefore() {},
  };

  return {
    repositories: {
      users,
      sessions,
      meals,
      templates,
      weights,
      settings,
    },
    serviceDeps: { auth: { users, sessions }, stats: { meals, weights } },
    data: {
      meals: mealsData,
      templates: templatesData,
      weights: weightsData,
      settings: settingsData,
      users: usersData,
      sessions: sessionsData,
    },
    reset() {
      mealsData.clear();
      templatesData.clear();
      weightsData.clear();
      settingsData.clear();
      usersData.clear();
      sessionsData.clear();
    },
  };
}

/** Establece una sesión válida para `userId` y devuelve el token que la acredita. */
export function seedSession(world: MemoryWorld, userId: string, token = "test-token"): string {
  world.data.sessions.set(token, { token, userId, expiresAt: new Date(Date.now() + 3_600_000) });
  return token;
}

/** Añade una sesión válida y la expone como cookie activa del test. */
export function authenticateWith(
  authCookie: { value: string },
  world: MemoryWorld,
  userId = "u-1",
): void {
  authCookie.value = seedSession(world, userId);
}

/** Crea un usuario con contraseña en claro a través del repositorio en memoria. */
export async function seedUser(
  world: MemoryWorld,
  username: string,
  password: string,
  isAdmin = false,
): Promise<UserRow> {
  const { hashPassword } = await import("@/server/auth/password");
  const passwordHash = await hashPassword(password);
  return world.repositories.users.create({ username, passwordHash, isAdmin });
}

/** Extrae el token de la cookie `bw_session` emitida por `POST /api/auth/login`. */
export function extractSessionToken(response: Response): string {
  const cookie = response.headers.get("set-cookie") ?? "";
  const match = /bw_session=([^;]+)/.exec(cookie);
  if (!match) throw new Error("No se emitió la cookie de sesión");
  return match[1];
}

const SAMPLE_DATE = new Date("2026-06-15T08:00:00Z");

/** Fila de comida en BD con valores por defecto sanos. */
export function mealRow(overrides: Partial<MealRow> = {}): MealRow {
  return {
    id: "m-1",
    userId: "u-1",
    logDate: "2026-06-15",
    title: "Comida",
    notes: null,
    entryMode: "per_ingredient",
    ingredients: [],
    totalCalories: null,
    totalProtein: null,
    totalCarbs: null,
    totalFat: null,
    resolvedCalories: 0,
    resolvedProtein: 0,
    resolvedCarbs: 0,
    resolvedFat: 0,
    sortOrder: 0,
    createdAt: SAMPLE_DATE,
    updatedAt: SAMPLE_DATE,
    ...overrides,
  };
}

/** Fila de plantilla en BD con valores por defecto sanos. */
export function templateRow(overrides: Partial<MealTemplateRow> = {}): MealTemplateRow {
  return {
    id: "t-1",
    userId: "u-1",
    name: "Desayuno base",
    title: "Avena con leche",
    notes: null,
    entryMode: "per_ingredient",
    ingredients: [],
    totalCalories: null,
    totalProtein: null,
    totalCarbs: null,
    totalFat: null,
    resolvedCalories: 0,
    resolvedProtein: 0,
    resolvedCarbs: 0,
    resolvedFat: 0,
    createdAt: SAMPLE_DATE,
    updatedAt: SAMPLE_DATE,
    ...overrides,
  };
}

/** Fila de pesaje en BD con valores por defecto sanos. */
export function weightRow(overrides: Partial<WeightRow> = {}): WeightRow {
  return {
    id: "w-1",
    userId: "u-1",
    measuredAt: SAMPLE_DATE,
    weightKg: 80,
    bodyFatPct: null,
    note: null,
    createdAt: SAMPLE_DATE,
    updatedAt: SAMPLE_DATE,
    ...overrides,
  };
}

/** DTO de pesaje (lo que devuelve la API) con valores por defecto sanos. */
export function weightDto(overrides: Partial<WeightDTO> = {}): WeightDTO {
  return {
    id: "w-1",
    measuredAt: "2026-06-15T08:00:00.000Z",
    weightKg: 80,
    bodyFatPct: null,
    note: null,
    updatedAt: "2026-06-15T08:00:00.000Z",
    ...overrides,
  };
}

/** DTO de plantilla (lo que devuelve la API) con valores por defecto sanos. */
export function templateDto(overrides: Partial<MealTemplateDTO> = {}): MealTemplateDTO {
  return {
    id: "t-1",
    name: "Desayuno base",
    title: "Avena con leche",
    notes: null,
    entryMode: "per_ingredient",
    ingredients: [],
    totalCalories: null,
    totalProtein: null,
    totalCarbs: null,
    totalFat: null,
    resolvedCalories: 0,
    resolvedProtein: 0,
    resolvedCarbs: 0,
    resolvedFat: 0,
    updatedAt: "2026-06-15T08:00:00.000Z",
    ...overrides,
  };
}

/** Perfil calórico completamente vacío. */
export function emptyCalorieProfile(): CalorieProfile {
  return {
    gender: null,
    birthYear: null,
    heightCm: null,
    gymDaysPerWeek: null,
    gymSessionMinutes: null,
    walkingMinutesPerDay: null,
    calorieGoal: null,
  };
}

/** Request JSON tipica contra una ruta de la API. */
export function jsonRequest(path: string, body: unknown, method = "POST"): Request {
  return new Request(`http://test${path}`, {
    method,
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

/** Params de contexto de una ruta dinámica (`/ruta/[id]`). */
export function routeParams(id: string): { params: Promise<{ id: string }> } {
  return { params: Promise.resolve({ id }) };
}

/** Respuesta JSON simulada para `vi.stubGlobal("fetch", ...)`. */
export function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get(name: string) {
        return name.toLowerCase() === "content-type" ? "application/json" : null;
      },
    },
    json: async () => body,
  } as unknown as Response;
}