import { Hono } from "hono";
import { cors } from "hono/cors";
import { z } from "zod";
import { InvalidCredentialsError, login, logout } from "@/server/services/auth-service";
import {
  createMeal,
  deleteMeal,
  listMealsInRange,
  reorderMeals,
  updateMeal,
} from "@/server/services/meals-service";
import {
  createTemplate,
  deleteTemplate,
  listTemplates,
} from "@/server/services/templates-service";
import {
  createWeight,
  deleteWeight,
  listWeights,
  updateWeight,
} from "@/server/services/weights-service";
import { updateCalorieProfile } from "@/server/services/settings-service";
import { buildStatsSummary } from "@/server/services/stats-service";
import { buildMealsCsv, buildWeightsCsv } from "@/server/services/export-service";
import {
  calorieProfileInputSchema,
  loginInputSchema,
  mealInputSchema,
  templateInputSchema,
  weightInputSchema,
} from "@/server/validation";
import { isValidDateKey, todayKey } from "@/lib/dates";
import type { StatsRange } from "@/lib/types";
import type { AppDeps } from "./deps";
import { clearSessionCookie, jsonError, sessionCookie, sessionTokenFromRequest, withUserId } from "./helpers";

const RANGE_VALUES: StatsRange[] = ["7d", "30d", "90d", "all"];

const reorderSchema = z.object({
  orderedIds: z.array(z.string().uuid()).min(1),
});

function corsAllowedOrigins(): string[] {
  const raw = process.env.CORS_ORIGIN ?? "http://localhost:3000,http://localhost:8080";
  return raw
    .split(",")
    .map((origin) => origin.trim())
    .filter(Boolean);
}

// Dev: CORS_ORIGIN="*" refleja cualquier origen (útil para pruebas locales
// desde puertos arbitrarios o `flutter test --platform chrome`).
function corsOrigin(): string | string[] | ((origin: string) => string) {
  const origins = corsAllowedOrigins();
  if (origins.length === 1 && origins[0] === "*") return (origin: string) => origin;
  return origins;
}

function csvResponse(filename: string, body: string): Response {
  return new Response(body, {
    headers: {
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": `attachment; filename="${filename}"`,
    },
  });
}

export function createApp(deps: AppDeps): Hono {
  const app = new Hono();

  app.use(
    "*",
    cors({
      origin: corsOrigin(),
      allowMethods: ["GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"],
      allowHeaders: ["Authorization", "Content-Type"],
    }),
  );

  // ── Auth ──────────────────────────────────────────────────────────────────

  app.post("/api/auth/login", async (c) => {
    let body: unknown;
    try {
      body = await c.req.json();
    } catch {
      return jsonError("Cuerpo JSON no válido", 400);
    }
    const parsed = loginInputSchema.safeParse(body);
    if (!parsed.success) return jsonError("Datos no válidos", 400);
    try {
      const issued = await login(deps.auth, parsed.data.username, parsed.data.password);
      // El token viaja también en el cuerpo para que las apps (web y Android)
      // lo guarden en almacenamiento seguro; la cookie se conserva para la web
      // legacy durante la migración.
      return c.json(
        { ok: true, token: issued.token, expiresAt: issued.expiresAt.toISOString() },
        200,
        { "Set-Cookie": sessionCookie(issued.token, issued.expiresAt) },
      );
    } catch (error) {
      if (error instanceof InvalidCredentialsError) {
        // DevOps de personal: diferenciar usuario inexistente de contraseña
        // errónea para mostrar un mensaje útil en el cliente. (El servicio
        // compartido sigue quemando tiempo para no filtrar nombres por latencia.)
        const user = await deps.auth.users.findByUsername(parsed.data.username);
        return jsonError(user ? "INVALID_PASSWORD" : "USER_NOT_FOUND", 401);
      }
      console.error("login failed", error);
      return jsonError("Error interno", 500);
    }
  });

  app.post("/api/auth/logout", async (c) => {
    await logout(deps.auth, sessionTokenFromRequest(c));
    return c.json({ ok: true }, 200, { "Set-Cookie": clearSessionCookie() });
  });

  app.get(
    "/api/auth/session",
    withUserId(deps.auth, async (c, userId) => {
      const session = await deps.getSession(userId);
      if (!session) return jsonError("No autenticado", 401);
      return c.json({ username: session.username, calorieProfile: session.calorieProfile });
    }),
  );

  // ── Meals ────────────────────────────────────────────────────────────────

  app.get(
    "/api/meals",
    withUserId(deps.auth, async (c, userId) => {
      const url = new URL(c.req.url);
      const from = url.searchParams.get("from");
      const to = url.searchParams.get("to");
      const meals = await listMealsInRange(deps.repositories.meals, userId, from, to);
      return c.json({ meals });
    }),
  );

  app.post(
    "/api/meals",
    withUserId(deps.auth, async (c, userId) => {
      const input = mealInputSchema.parse(await c.req.json());
      const meal = await createMeal(deps.repositories.meals, userId, input);
      return c.json({ meal }, 201);
    }),
  );

  app.patch(
    "/api/meals/reorder",
    withUserId(deps.auth, async (c, userId) => {
      const body = reorderSchema.parse(await c.req.json());
      await reorderMeals(deps.repositories.meals, userId, body.orderedIds);
      return c.json({ ok: true });
    }),
  );

  app.patch(
    "/api/meals/:id",
    withUserId(deps.auth, async (c, userId) => {
      const id = c.req.param("id") ?? "";
      const input = mealInputSchema.parse(await c.req.json());
      const meal = await updateMeal(deps.repositories.meals, userId, id, input);
      if (!meal) return jsonError("Comida no encontrada", 404);
      return c.json({ meal });
    }),
  );

  app.delete(
    "/api/meals/:id",
    withUserId(deps.auth, async (c, userId) => {
      const id = c.req.param("id") ?? "";
      const deleted = await deleteMeal(deps.repositories.meals, userId, id);
      if (!deleted) return jsonError("Comida no encontrada", 404);
      return c.json({ ok: true });
    }),
  );

  // ── Templates ────────────────────────────────────────────────────────────

  app.get(
    "/api/templates",
    withUserId(deps.auth, async (c, userId) => {
      const templates = await listTemplates(deps.repositories.templates, userId);
      return c.json({ templates });
    }),
  );

  app.post(
    "/api/templates",
    withUserId(deps.auth, async (c, userId) => {
      const input = templateInputSchema.parse(await c.req.json());
      const template = await createTemplate(deps.repositories.templates, userId, input);
      return c.json({ template }, 201);
    }),
  );

  app.delete(
    "/api/templates/:id",
    withUserId(deps.auth, async (c, userId) => {
      const id = c.req.param("id") ?? "";
      const deleted = await deleteTemplate(deps.repositories.templates, userId, id);
      if (!deleted) return jsonError("Plantilla no encontrada", 404);
      return c.json({ ok: true });
    }),
  );

  // ── Weights ──────────────────────────────────────────────────────────────

  app.get(
    "/api/weights",
    withUserId(deps.auth, async (c, userId) => {
      const weights = await listWeights(deps.repositories.weights, userId);
      return c.json({ weights });
    }),
  );

  app.post(
    "/api/weights",
    withUserId(deps.auth, async (c, userId) => {
      const input = weightInputSchema.parse(await c.req.json());
      const weight = await createWeight(deps.repositories.weights, userId, input);
      return c.json({ weight }, 201);
    }),
  );

  app.patch(
    "/api/weights/:id",
    withUserId(deps.auth, async (c, userId) => {
      const id = c.req.param("id") ?? "";
      const input = weightInputSchema.parse(await c.req.json());
      const weight = await updateWeight(deps.repositories.weights, userId, id, input);
      if (!weight) return jsonError("Registro no encontrado", 404);
      return c.json({ weight });
    }),
  );

  app.delete(
    "/api/weights/:id",
    withUserId(deps.auth, async (c, userId) => {
      const id = c.req.param("id") ?? "";
      const deleted = await deleteWeight(deps.repositories.weights, userId, id);
      if (!deleted) return jsonError("Registro no encontrado", 404);
      return c.json({ ok: true });
    }),
  );

  // ── Settings ─────────────────────────────────────────────────────────────

  app.put(
    "/api/settings",
    withUserId(deps.auth, async (c, userId) => {
      const body = (await c.req.json()) as Record<string, unknown>;
      if (
        "gender" in body ||
        "birthYear" in body ||
        "heightCm" in body ||
        "calorieGoal" in body
      ) {
        const input = calorieProfileInputSchema.parse(body);
        const settings = await updateCalorieProfile(deps.repositories.settings, userId, input);
        return c.json(settings);
      }
      return jsonError("Payload no válido", 400);
    }),
  );

  // ── Stats ────────────────────────────────────────────────────────────────

  app.get(
    "/api/stats",
    withUserId(deps.auth, async (c, userId) => {
      const url = new URL(c.req.url);
      const rangeParam = url.searchParams.get("range") ?? "30d";
      const range = (RANGE_VALUES as string[]).includes(rangeParam)
        ? (rangeParam as StatsRange)
        : "30d";
      const todayParam = url.searchParams.get("today");
      const today = todayParam && isValidDateKey(todayParam) ? todayParam : todayKey();
      const summary = await buildStatsSummary(deps.stats, userId, range, today);
      return c.json(summary);
    }),
  );

  // ── Export ───────────────────────────────────────────────────────────────

  app.get(
    "/api/export/:kind",
    withUserId(deps.auth, async (c, userId) => {
      // Acepta "meals"/"meals.csv" y "weights"/"weights.csv" — la web legacy
      // enlaza a los nombres con extensión.
      const kind = (c.req.param("kind") ?? "").replace(/\.csv$/, "");
      if (kind === "meals") {
        const meals = await deps.repositories.meals.listInRange(userId, null, null);
        return csvResponse("comidas.csv", buildMealsCsv(meals));
      }
      if (kind === "weights") {
        const weights = await deps.repositories.weights.listForUser(userId);
        return csvResponse("peso.csv", buildWeightsCsv(weights));
      }
      return jsonError("Recurso no encontrado", 404);
    }),
  );

  return app;
}