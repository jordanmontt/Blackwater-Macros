import { describe, expect, it, vi, beforeEach } from "vitest";
import { createMemoryWorld, authenticateWith, mealRow, weightRow } from "../helpers/repos";
import type { MemoryWorld } from "../helpers/repos";

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

import { GET as getExport } from "@/app/api/export/[kind]/route";

holder.world = createMemoryWorld();

const world = holder.world;
const USER = "u-1";

function authenticate(): void {
  authenticateWith(holder.authCookie, world!, USER);
}

function routeParams(kind: string): { params: Promise<{ kind: string }> } {
  return { params: Promise.resolve({ kind }) };
}

describe("ruta de exportación CSV", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  it("devuelve 401 sin sesión", async () => {
    const res = await getExport(new Request("http://test/api/export/meals"), routeParams("meals"));
    expect(res.status).toBe(401);
  });

  it("exporta las comidas como CSV adjunto", async () => {
    authenticate();
    world!.data.meals.set(
      "m-1",
      mealRow({
        id: "m-1",
        userId: USER,
        logDate: "2026-06-15",
        title: "Desayuno",
        ingredients: [{ name: "Avena", quantity: "50g", calories: 150, protein: 5, carbs: 27, fat: 3 }],
        resolvedCalories: 150,
        resolvedProtein: 5,
        resolvedCarbs: 27,
        resolvedFat: 3,
      }),
    );

    const res = await getExport(new Request("http://test/api/export/meals"), routeParams("meals"));
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("text/csv; charset=utf-8");
    expect(res.headers.get("content-disposition")).toBe(`attachment; filename="comidas.csv"`);

    const text = await res.text();
    expect(text).toContain("fecha,comida");
    expect(text).toContain("2026-06-15,Desayuno");
    expect(text).toContain("Avena");
  });

  it("exporta los pesajes como CSV adjunto", async () => {
    authenticate();
    world!.data.weights.set(
      "w-1",
      weightRow({
        id: "w-1",
        userId: USER,
        measuredAt: new Date("2026-06-15T08:00:00Z"),
        weightKg: 77.4,
        bodyFatPct: 18.5,
      }),
    );

    const res = await getExport(new Request("http://test/api/export/weights"), routeParams("weights"));
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("text/csv; charset=utf-8");
    expect(res.headers.get("content-disposition")).toBe(`attachment; filename="peso.csv"`);

    const text = await res.text();
    expect(text).toContain("fecha_hora,peso_kg,grasa_corporal_pct,nota");
    expect(text).toContain("77.4");
    expect(text).toContain("18.5");
  });

  it("exporta celdas vacías (no ceros) para los valores que faltan", async () => {
    authenticate();
    world!.data.meals.set(
      "m-1",
      mealRow({
        id: "m-1",
        userId: USER,
        logDate: "2026-06-15",
        title: "Avena sola",
        ingredients: [{ name: "Avena" }],
      }),
    );
    world!.data.weights.set(
      "w-1",
      weightRow({
        id: "w-1",
        userId: USER,
        measuredAt: new Date("2026-06-15T08:00:00Z"),
        weightKg: 77.4,
        bodyFatPct: null,
        note: null,
      }),
    );

    const mealsRes = await getExport(
      new Request("http://test/api/export/meals"),
      routeParams("meals"),
    );
    const mealsText = await mealsRes.text();
    // sin macros (ingrediente con solo nombre) → celdas vacías, no ceros
    const expectedMealRow = [
      "2026-06-15",
      "Avena sola",
      "per_ingredient",
      null,
      "Avena",
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
    ]
      .map((value) => (value === null ? "" : String(value)))
      .join(",");
    expect(mealsText).toContain(expectedMealRow);

    const weightsRes = await getExport(
      new Request("http://test/api/export/weights"),
      routeParams("weights"),
    );
    const weightsText = await weightsRes.text();
    // peso sin grasa corporal ni nota → últimas columnas vacías
    const expectedWeightRow = [
      "2026-06-15T08:00:00.000Z",
      77.4,
      null,
      null,
    ]
      .map((value) => (value === null ? "" : String(value)))
      .join(",");
    expect(weightsText).toContain(expectedWeightRow);
  });

  it("devuelve 404 si el tipo de exportación no existe", async () => {
    authenticate();
    const res = await getExport(new Request("http://test/api/export/raro"), routeParams("raro"));
    expect(res.status).toBe(404);
    expect(await res.json()).toEqual({ error: "Recurso no encontrado" });
  });
});