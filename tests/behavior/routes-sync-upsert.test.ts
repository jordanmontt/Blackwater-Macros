import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  createMemoryWorld,
  authenticateWith,
  mealRow,
  jsonRequest,
  routeParams,
} from "../helpers/repos";
import type { MemoryWorld } from "../helpers/repos";

/**
 * Requisito: la app Android funciona sin conexión y genera los ids en el
 * teléfono. Cuando vuelve la conexión sube cada registro con `PUT /:id`, que
 * crea o reemplaza. Reintentar el mismo envío (p. ej. se cortó la red justo
 * después de guardar) nunca duplica datos.
 */

const holder = vi.hoisted(() => ({
  world: null as MemoryWorld | null,
  authCookie: { value: "" },
}));

vi.mock("@/server/composition", () => ({
  get repositories() {
    return holder.world?.repositories;
  },
  get serviceDeps() {
    return holder.world?.serviceDeps;
  },
}));

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "bw_session" && holder.authCookie.value
        ? { name, value: holder.authCookie.value }
        : undefined,
  }),
}));

import { GET as getMeals } from "@/app/api/meals/route";
import { PUT as putMeal } from "@/app/api/meals/[id]/route";
import { PUT as putTemplate } from "@/app/api/templates/[id]/route";
import { PUT as putWeight } from "@/app/api/weights/[id]/route";

holder.world = createMemoryWorld();
const world = holder.world;
const USER = "u-1";

const MEAL_ID = "5b0f7a52-9d0a-4f5e-8a57-3f1c2b6f0a11";
const TEMPLATE_ID = "0e8e3f1a-7c55-4a9b-9d2e-1f4b6c8d2a33";
const WEIGHT_ID = "a3c1d2e4-5f67-4890-8abc-def012345678";

const breakfast = {
  logDate: "2026-06-15",
  title: "Desayuno",
  entryMode: "total_only",
  ingredients: [],
  totalCalories: 400,
  totalProtein: 20,
};

function put(path: string, body: unknown): Request {
  return jsonRequest(path, body, "PUT");
}

describe("subida de registros creados sin conexión (PUT /:id)", () => {
  beforeEach(() => {
    world.reset();
    holder.authCookie.value = "";
  });

  it("sin sesión devuelve 401", async () => {
    const res = await putMeal(put(`/api/meals/${MEAL_ID}`, breakfast), routeParams(MEAL_ID));
    expect(res.status).toBe(401);
  });

  it("crea la comida con el id generado en el teléfono", async () => {
    authenticateWith(holder.authCookie, world, USER);
    const res = await putMeal(put(`/api/meals/${MEAL_ID}`, breakfast), routeParams(MEAL_ID));
    expect(res.status).toBe(200);
    const { meal } = await res.json();
    expect(meal.id).toBe(MEAL_ID);
    expect(meal.resolvedCalories).toBe(400);
  });

  it("reenviar la misma comida no la duplica y aplica la última versión", async () => {
    authenticateWith(holder.authCookie, world, USER);
    await putMeal(put(`/api/meals/${MEAL_ID}`, breakfast), routeParams(MEAL_ID));
    await putMeal(put(`/api/meals/${MEAL_ID}`, breakfast), routeParams(MEAL_ID));
    await putMeal(
      put(`/api/meals/${MEAL_ID}`, { ...breakfast, title: "Desayuno grande", totalCalories: 600 }),
      routeParams(MEAL_ID),
    );

    const { meals } = await (await getMeals(new Request("http://test/api/meals"))).json();
    expect(meals).toHaveLength(1);
    expect(meals[0].title).toBe("Desayuno grande");
    expect(meals[0].resolvedCalories).toBe(600);
  });

  it("respeta el orden enviado y, si no se envía, conserva el que ya tenía", async () => {
    authenticateWith(holder.authCookie, world, USER);
    await putMeal(put(`/api/meals/${MEAL_ID}`, { ...breakfast, sortOrder: 3 }), routeParams(MEAL_ID));
    expect(world.data.meals.get(MEAL_ID)?.sortOrder).toBe(3);

    await putMeal(put(`/api/meals/${MEAL_ID}`, breakfast), routeParams(MEAL_ID));
    expect(world.data.meals.get(MEAL_ID)?.sortOrder).toBe(3);
  });

  it("no permite pisar la comida de otro usuario", async () => {
    world.data.meals.set(MEAL_ID, mealRow({ id: MEAL_ID, userId: "u-otro", title: "Ajena" }));
    authenticateWith(holder.authCookie, world, USER);
    const res = await putMeal(put(`/api/meals/${MEAL_ID}`, breakfast), routeParams(MEAL_ID));
    expect(res.status).toBe(404);
    expect(world.data.meals.get(MEAL_ID)?.title).toBe("Ajena");
  });

  it("rechaza ids que no son UUID", async () => {
    authenticateWith(holder.authCookie, world, USER);
    const res = await putMeal(put("/api/meals/abc", breakfast), routeParams("abc"));
    expect(res.status).toBe(400);
  });

  it("crea y reemplaza plantillas con el id del teléfono", async () => {
    authenticateWith(holder.authCookie, world, USER);
    const template = { name: "Base", title: "Avena", entryMode: "total_only", ingredients: [], totalCalories: 300 };
    await putTemplate(put(`/api/templates/${TEMPLATE_ID}`, template), routeParams(TEMPLATE_ID));
    const res = await putTemplate(
      put(`/api/templates/${TEMPLATE_ID}`, { ...template, name: "Base 2" }),
      routeParams(TEMPLATE_ID),
    );
    expect(res.status).toBe(200);
    expect((await res.json()).template.id).toBe(TEMPLATE_ID);
    expect(world.data.templates.size).toBe(1);
    expect(world.data.templates.get(TEMPLATE_ID)?.name).toBe("Base 2");
  });

  it("crea y reemplaza pesajes con el id del teléfono", async () => {
    authenticateWith(holder.authCookie, world, USER);
    const weight = { measuredAt: "2026-06-15T07:30:00.000Z", weightKg: 80.4 };
    await putWeight(put(`/api/weights/${WEIGHT_ID}`, weight), routeParams(WEIGHT_ID));
    const res = await putWeight(
      put(`/api/weights/${WEIGHT_ID}`, { ...weight, weightKg: 80.1, bodyFatPct: 18 }),
      routeParams(WEIGHT_ID),
    );
    expect(res.status).toBe(200);
    const { weight: saved } = await res.json();
    expect(saved.id).toBe(WEIGHT_ID);
    expect(saved.weightKg).toBe(80.1);
    expect(world.data.weights.size).toBe(1);
  });
});
