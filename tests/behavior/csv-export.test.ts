import { describe, expect, it } from "vitest";
import { buildMealsCsv, buildWeightsCsv } from "@/server/services/export-service";
import type { MealIngredient } from "@/server/db/schema";

/**
 * Requisito de exportación: el usuario puede descargar todos sus datos como
 * CSV para usarlos fuera de la aplicación (hojas de cálculo, copias…).
 */

describe("exportar comidas", () => {
  it("incluye una fila por ingrediente y los totales de cada comida", () => {
    const csv = buildMealsCsv([
      mealRow({
        title: "Desayuno",
        ingredients: [
          { name: "4 huevos", quantity: "240 g", calories: 280, protein: 24, carbs: 2, fat: 20 },
          { name: "café con leche" },
        ],
        resolvedCalories: 400,
        resolvedProtein: 28,
      }),
    ]);

    expect(csv).toContain("fecha,comida,modo");
    expect(csv).toContain("4 huevos,240 g,280,24,2,20");
    expect(csv).toContain("café con leche");
    // Los totales aparecen en las filas de ingredientes de esa comida:
    const rows = csv.trim().split("\n");
    expect(rows).toHaveLength(3); // cabecera + 2 ingredientes
  });

  it("una comida de 'solo total' genera una única fila con sus totales", () => {
    const csv = buildMealsCsv([
      mealRow({
        entryMode: "total_only",
        title: "Menú del día",
        ingredients: [],
        totalCalories: 850,
        totalProtein: 45,
      }),
    ]);

    expect(csv).toContain("Menú del día");
    expect(csv).toContain("850,45");
    expect(csv.trim().split("\n")).toHaveLength(2);
  });

  it("escapa títulos que contienen comas o comillas", () => {
    const csv = buildMealsCsv([
      mealRow({ title: 'Arroz "a la cubana", con tomate', ingredients: [] }),
    ]);
    expect(csv).toContain('"Arroz ""a la cubana"", con tomate"');
  });

  it("lista vacía genera solo la cabecera", () => {
    const csv = buildMealsCsv([]);
    const rows = csv.trim().split("\n");
    expect(rows).toHaveLength(1);
    expect(rows[0]).toContain("fecha");
  });

  it("comida per_ingredient sin ingredientes genera una fila con ceros", () => {
    const csv = buildMealsCsv([
      mealRow({ entryMode: "per_ingredient", title: "Vacía", ingredients: [] }),
    ]);
    const rows = csv.trim().split("\n");
    expect(rows).toHaveLength(2);
    expect(rows[1]).toContain("Vacía");
  });

  it("total_only con totales nulos muestra campos vacíos", () => {
    const csv = buildMealsCsv([
      mealRow({
        entryMode: "total_only",
        title: "Sin datos",
        totalCalories: null,
        totalProtein: null,
        totalCarbs: null,
        totalFat: null,
      }),
    ]);
    expect(csv).toContain("Sin datos");
    const rows = csv.trim().split("\n");
    expect(rows).toHaveLength(2);
    const dataRow = rows[1];
    expect(dataRow).toContain("Sin datos,total_only");
  });
});

describe("exportar peso", () => {
  it("incluye fecha-hora en ISO, peso y nota", () => {
    const csv = buildWeightsCsv([
      {
        id: "w1",
        userId: "u1",
        measuredAt: new Date("2026-08-23T07:30:00Z"),
        weightKg: 81.2,
        bodyFatPct: 15.5,
        note: "en ayunas",
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    ]);

    expect(csv).toContain("fecha_hora,peso_kg,grasa_corporal_pct,nota");
    expect(csv).toContain("2026-08-23T07:30:00.000Z,81.2,15.5,en ayunas");
  });

  it("lista vacía genera solo la cabecera", () => {
    const csv = buildWeightsCsv([]);
    const rows = csv.trim().split("\n");
    expect(rows).toHaveLength(1);
    expect(rows[0]).toContain("fecha_hora");
  });

  it("peso sin grasa corporal ni nota muestra vacíos", () => {
    const csv = buildWeightsCsv([
      {
        id: "w2",
        userId: "u1",
        measuredAt: new Date("2026-08-30T08:00:00Z"),
        weightKg: 90,
        bodyFatPct: null,
        note: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    ]);
    expect(csv).toContain("90,,");
  });
});

// --- helper ---
function mealRow(overrides: {
  entryMode?: "per_ingredient" | "total_only";
  title?: string;
  ingredients?: MealIngredient[];
  resolvedCalories?: number;
  resolvedProtein?: number;
  resolvedCarbs?: number;
  resolvedFat?: number;
  totalCalories?: number | null;
  totalProtein?: number | null;
  totalCarbs?: number | null;
  totalFat?: number | null;
}) {
  return {
    id: "m-1",
    userId: "user-1",
    logDate: "2026-08-23",
    title: overrides.title ?? "Desayuno",
    notes: null,
    entryMode: overrides.entryMode ?? "per_ingredient",
    ingredients: overrides.ingredients ?? [],
    totalCalories: overrides.totalCalories ?? null,
    totalProtein: overrides.totalProtein ?? null,
    totalCarbs: overrides.totalCarbs ?? null,
    totalFat: overrides.totalFat ?? null,
    resolvedCalories: overrides.resolvedCalories ?? 0,
    resolvedProtein: overrides.resolvedProtein ?? 0,
    resolvedCarbs: overrides.resolvedCarbs ?? 0,
    resolvedFat: overrides.resolvedFat ?? 0,
    sortOrder: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}
