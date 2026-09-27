import { describe, expect, it, vi, beforeEach } from "vitest";
import { createMemoryWorld, authenticateWith, jsonRequest, mealRow, weightRow } from "../helpers/repos";
import type { MemoryWorld } from "../helpers/repos";
import { buildMealsCsv, buildWeightsCsv } from "@/server/services/export-service";
import { parseBackupCsv } from "@/lib/csv-import";

/**
 * Requisitos de «Importar CSV» en la web (como la app Android):
 *  - lee exactamente lo que escribe la exportación (ida y vuelta sin pérdidas),
 *  - también el formato de una hoja de cálculo en español (coma decimal),
 *  - detecta el tipo por la cabecera y descarta filas que el servidor rechazaría,
 *  - la ruta añade lo nuevo y salta lo que ya estaba: importar dos veces no duplica.
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
      name === "bw_session" && holder.authCookie.value ? { name, value: holder.authCookie.value } : undefined,
  }),
}));

import { POST as postImport } from "@/app/api/import/[kind]/route";

holder.world = createMemoryWorld();
const world = holder.world;
const USER = "u-1";

const kind = (value: string) => ({ params: Promise.resolve({ kind: value }) });

const MEALS = [
  mealRow({
    id: "m-1",
    userId: USER,
    logDate: "2026-03-01",
    title: "Desayuno, con avena",
    notes: 'Notas con "comillas"',
    entryMode: "per_ingredient",
    ingredients: [
      { name: "Avena", quantity: "80 g", calories: 300, protein: 10.5, carbs: 50, fat: 5 },
      { name: "Leche", quantity: "200 ml", calories: 120, protein: 7, carbs: 10, fat: 6 },
    ],
    resolvedCalories: 420,
    resolvedProtein: 17.5,
    resolvedCarbs: 60,
    resolvedFat: 11,
  }),
  mealRow({
    id: "m-2",
    userId: USER,
    logDate: "2026-03-02",
    title: "Cena",
    notes: null,
    entryMode: "total_only",
    ingredients: [],
    totalCalories: 700,
    totalProtein: 40,
    totalCarbs: 60,
    totalFat: 25,
    resolvedCalories: 700,
    resolvedProtein: 40,
    resolvedCarbs: 60,
    resolvedFat: 25,
  }),
];

describe("importar CSV", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  it("lee lo que escribe la exportación de comidas", () => {
    const parsed = parseBackupCsv(buildMealsCsv(MEALS));
    expect(parsed.kind).toBe("meals");
    if (parsed.kind !== "meals") return;
    expect(parsed.invalidRows).toBe(0);
    expect(parsed.meals).toEqual([
      {
        logDate: "2026-03-01",
        title: "Desayuno, con avena",
        notes: 'Notas con "comillas"',
        entryMode: "per_ingredient",
        ingredients: [
          { name: "Avena", quantity: "80 g", calories: 300, protein: 10.5, carbs: 50, fat: 5 },
          { name: "Leche", quantity: "200 ml", calories: 120, protein: 7, carbs: 10, fat: 6 },
        ],
        totalCalories: null,
        totalProtein: null,
        totalCarbs: null,
        totalFat: null,
      },
      {
        logDate: "2026-03-02",
        title: "Cena",
        notes: null,
        entryMode: "total_only",
        ingredients: [],
        totalCalories: 700,
        totalProtein: 40,
        totalCarbs: 60,
        totalFat: 25,
      },
    ]);
  });

  it("lee el peso exportado y una hoja de cálculo con coma decimal; descarta filas imposibles", () => {
    const exported = parseBackupCsv(
      buildWeightsCsv([
        weightRow({ userId: USER, measuredAt: new Date("2026-03-01T07:30:00Z"), weightKg: 80.25, bodyFatPct: 18, note: "ayunas" }),
      ]),
    );
    expect(exported).toEqual({
      kind: "weights",
      weights: [{ measuredAt: "2026-03-01T07:30:00.000Z", weightKg: 80.25, bodyFatPct: 18, note: "ayunas" }],
      invalidRows: 0,
    });
    const sheet = parseBackupCsv(
      'fecha_hora,peso_kg,grasa_corporal_pct,nota\n2026-03-02T07:30:00Z,"79,8",,\n2026-03-03T07:30:00Z,900,,\n',
    );
    expect(sheet).toMatchObject({ kind: "weights", invalidRows: 1, weights: [{ weightKg: 79.8, bodyFatPct: null }] });
    expect(parseBackupCsv("nombre,apellido\nAna,Pérez")).toEqual({ kind: "unknown" });
  });

  it("la ruta necesita sesión", async () => {
    const res = await postImport(jsonRequest("/api/import/meals", { meals: [] }), kind("meals"));
    expect(res.status).toBe(401);
  });

  it("importar dos veces no duplica comidas ni pesajes", async () => {
    authenticateWith(holder.authCookie, world!, USER);
    const parsed = parseBackupCsv(buildMealsCsv(MEALS));
    if (parsed.kind !== "meals") throw new Error("expected meals");

    const first = await postImport(jsonRequest("/api/import/meals", { meals: parsed.meals }), kind("meals"));
    expect(await first.json()).toEqual({ added: 2, skipped: 0 });
    const again = await postImport(jsonRequest("/api/import/meals", { meals: parsed.meals }), kind("meals"));
    expect(await again.json()).toEqual({ added: 0, skipped: 2 });
    expect([...world!.data.meals.values()].filter((meal) => meal.userId === USER)).toHaveLength(2);

    const weights = [{ measuredAt: "2026-03-01T07:30:00.000Z", weightKg: 80.25, bodyFatPct: null, note: null }];
    const w1 = await postImport(jsonRequest("/api/import/weights", { weights }), kind("weights"));
    expect(await w1.json()).toEqual({ added: 1, skipped: 0 });
    const w2 = await postImport(jsonRequest("/api/import/weights", { weights }), kind("weights"));
    expect(await w2.json()).toEqual({ added: 0, skipped: 1 });
  });

  it("rechaza un tipo desconocido", async () => {
    authenticateWith(holder.authCookie, world!, USER);
    const res = await postImport(jsonRequest("/api/import/templates", {}), kind("templates"));
    expect(res.status).toBe(404);
  });
});
