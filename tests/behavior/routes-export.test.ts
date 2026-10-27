import { describe, expect, it, vi, beforeEach } from "vitest";
import { createMemoryWorld, authenticateWith, mealRow, templateRow, weightRow } from "../helpers/repos";
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

import { GET as getExport } from "@/app/api/export/route";

holder.world = createMemoryWorld();

const world = holder.world;
const USER = "u-1";

function authenticate(): void {
  authenticateWith(holder.authCookie, world!, USER);
}

/**
 * Requisito: el usuario descarga todos sus datos en un solo archivo legible
 * (JSON con nombres en inglés, core/backup.ts): perfil, perfiles anteriores,
 * pesajes, comidas y plantillas.
 */
describe("ruta de exportación", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  it("devuelve 401 sin sesión", async () => {
    const res = await getExport(new Request("http://test/api/export"));
    expect(res.status).toBe(401);
  });

  it("descarga un solo archivo con todo, nombrado con el día del navegador", async () => {
    authenticate();
    world!.data.settings.set(USER, {
      gender: "female",
      birthYear: 1995,
      heightCm: 165,
      gymDaysPerWeek: 2,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 20,
      calorieGoal: "surplus",
    });
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
    world!.data.weights.set(
      "w-1",
      weightRow({ id: "w-1", userId: USER, measuredAt: new Date("2026-06-15T08:00:00Z"), weightKg: 77.4, bodyFatPct: 18.5 }),
    );
    world!.data.templates.set("t-1", templateRow({ id: "t-1", userId: USER, name: "Batido" }));

    const res = await getExport(new Request("http://test/api/export?day=2026-06-16"));
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("application/json; charset=utf-8");
    expect(res.headers.get("content-disposition")).toBe(`attachment; filename="blackwater-macros-2026-06-16.json"`);

    const text = await res.text();
    expect(text).toContain('\n  "app": "Blackwater Macros",\n'); // indented, readable
    const file = JSON.parse(text);
    expect(file.format).toBe(1);
    expect(file.profile).toMatchObject({ sex: "female", goal: "bulk", gymMinutesPerSession: 60 });
    expect(file.weighIns).toEqual([{ time: "2026-06-15T08:00:00.000Z", kg: 77.4, bodyFatPct: 18.5, note: null }]);
    expect(file.meals).toHaveLength(1);
    expect(file.meals[0]).toMatchObject({
      date: "2026-06-15",
      title: "Desayuno",
      mode: "foods",
      foods: [{ name: "Avena", amount: "50g", kcal: 150, protein: 5, carbs: 27, fat: 3 }],
      total: { kcal: 150, protein: 5, carbs: 27, fat: 3 },
    });
    expect(file.templates.map((template: { name: string }) => template.name)).toEqual(["Batido"]);
  });

  it("solo exporta los datos de quien lo pide", async () => {
    authenticate();
    world!.data.meals.set("m-2", mealRow({ id: "m-2", userId: "otra", title: "Ajena" }));
    const file = JSON.parse(await (await getExport(new Request("http://test/api/export"))).text());
    expect(file.meals).toEqual([]);
  });

  it("sin día válido, el nombre lleva la fecha del servidor", async () => {
    authenticate();
    const res = await getExport(new Request("http://test/api/export?day=mañana"));
    expect(res.headers.get("content-disposition")).toMatch(/^attachment; filename="blackwater-macros-\d{4}-\d{2}-\d{2}\.json"$/);
  });
});
