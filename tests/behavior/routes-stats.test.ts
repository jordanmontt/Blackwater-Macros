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

import { GET as getStats } from "@/app/api/stats/route";

holder.world = createMemoryWorld();

const world = holder.world;
const USER = "u-1";

function authenticate(): void {
  authenticateWith(holder.authCookie, world!, USER);
}

function statsUrl(range?: string, today?: string): string {
  const params = new URLSearchParams();
  if (range) params.set("range", range);
  if (today) params.set("today", today);
  const query = params.toString();
  return `http://test/api/stats${query ? `?${query}` : ""}`;
}

describe("ruta de estadísticas", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  it("devuelve 401 sin sesión", async () => {
    const res = await getStats(new Request(statsUrl()));
    expect(res.status).toBe(401);
  });

  it("usa el rango por defecto si el parámetro es inválido", async () => {
    authenticate();
    const res = await getStats(new Request(statsUrl("bogus", "2026-06-15")));
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.calories.length).toBe(30);
  });

  it("devuelve una serie vacía al no haber datos", async () => {
    authenticate();
    const res = await getStats(new Request(statsUrl("30d", "2026-06-15")));
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.calories).toEqual(
      [
        "2026-05-17",
        "2026-05-18",
        "2026-05-19",
        "2026-05-20",
        "2026-05-21",
        "2026-05-22",
        "2026-05-23",
        "2026-05-24",
        "2026-05-25",
        "2026-05-26",
        "2026-05-27",
        "2026-05-28",
        "2026-05-29",
        "2026-05-30",
        "2026-05-31",
        "2026-06-01",
        "2026-06-02",
        "2026-06-03",
        "2026-06-04",
        "2026-06-05",
        "2026-06-06",
        "2026-06-07",
        "2026-06-08",
        "2026-06-09",
        "2026-06-10",
        "2026-06-11",
        "2026-06-12",
        "2026-06-13",
        "2026-06-14",
        "2026-06-15",
      ].map((date) => ({ date, calories: 0, protein: 0, carbs: 0, fat: 0 })),
    );
    expect(body.caloriesAvg).toBeNull();
    expect(body.weight.currentWeightKg).toBeNull();
  });

  it("resume las comidas del usuario en el rango", async () => {
    authenticate();
    world!.data.meals.set(
      "m-1",
      mealRow({
        id: "m-1",
        userId: USER,
        logDate: "2026-06-15",
        resolvedCalories: 500,
        resolvedProtein: 25,
        resolvedCarbs: 50,
        resolvedFat: 20,
      }),
    );
    const res = await getStats(new Request(statsUrl("30d", "2026-06-15")));
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.calories.length).toBe(30);
    expect(body.caloriesAvg).toBe(500);
    expect(body.caloriesMaxDay).toEqual({ date: "2026-06-15", calories: 500, protein: 25, carbs: 50, fat: 20 });
    expect(body.proteinAvg).toBe(25);
  });

  it("ignora las comidas fuera del rango", async () => {
    authenticate();
    world!.data.meals.set(
      "m-vieja",
      mealRow({
        id: "m-vieja",
        userId: USER,
        logDate: "2026-04-01",
        title: "Comida vieja",
        resolvedCalories: 900,
      }),
    );
    const res = await getStats(new Request(statsUrl("30d", "2026-06-15")));
    const body = await res.json();
    expect(body.caloriesAvg).toBeNull();
  });

  it("incluye los pesajes del usuario", async () => {
    authenticate();
    world!.data.weights.set(
      "w-1",
      weightRow({
        id: "w-1",
        userId: USER,
        measuredAt: new Date("2026-06-10T08:00:00Z"),
        bodyFatPct: 20,
      }),
    );
    const res = await getStats(new Request(statsUrl("30d", "2026-06-15")));
    const body = await res.json();
    expect(body.weight.currentWeightKg).toBe(80);
    expect(body.weight.currentBodyFatPct).toBe(20);
    expect(body.weights[0]).toMatchObject({ date: "2026-06-10", weight: 80 });
  });
});