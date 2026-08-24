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
          { name: "4 huevos", quantity: "240 g", calories: 280, protein: 24 },
          { name: "café con leche" },
        ],
        resolvedCalories: 400,
        resolvedProtein: 28,
      }),
    ]);

    expect(csv).toContain("fecha,comida,modo");
    expect(csv).toContain("4 huevos,240 g,280,24");
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
});

describe("exportar peso", () => {
  it("incluye fecha-hora en ISO, peso y nota", () => {
    const csv = buildWeightsCsv([
      {
        id: "w1",
        userId: "u1",
        measuredAt: new Date("2026-08-23T07:30:00Z"),
        weightKg: 81.2,
        note: "en ayunas",
        createdAt: new Date(),
      },
    ]);

    expect(csv).toContain("fecha_hora,peso_kg,nota");
    expect(csv).toContain("2026-08-23T07:30:00.000Z,81.2,en ayunas");
  });
});

// --- helper ---
function mealRow(overrides: {
  entryMode?: "per_ingredient" | "total_only";
  title?: string;
  ingredients?: MealIngredient[];
  resolvedCalories?: number;
  resolvedProtein?: number;
  totalCalories?: number | null;
  totalProtein?: number | null;
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
    resolvedCalories: overrides.resolvedCalories ?? 0,
    resolvedProtein: overrides.resolvedProtein ?? 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}
