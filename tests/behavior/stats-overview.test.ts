import { describe, expect, it } from "vitest";
import { buildStatsSummary } from "@/server/services/stats-service";
import { addDaysToKey, toDateKey } from "@/lib/dates";

/**
 * Requisitos de la sección de estadísticas:
 *  - totales diarios de calorías y proteína sumando todas las comidas del día,
 *  - los días sin comidas cuentan como cero (para ver patrones reales),
 *  - evolución del peso con línea de tendencia y métricas derivadas
 *    (peso actual, cambio total, ritmo semanal, mínimos y máximos).
 */

const TODAY = "2026-08-23";

function makeDeps(meals: { logDate: string; kcal: number; protein: number; carbs?: number; fat?: number }[], weights: { iso: string; kg: number; bodyFatPct?: number | null }[]) {
  return {
    meals: {
      async listInRange() {
        return meals.map((meal, index) => ({
          id: `m-${index}`,
          userId: "user-1",
          logDate: meal.logDate,
          title: `Comida ${index}`,
          notes: null,
          entryMode: "per_ingredient" as const,
          ingredients: [],
          totalCalories: null,
          totalProtein: null,
          totalCarbs: null,
          totalFat: null,
          resolvedCalories: meal.kcal,
          resolvedProtein: meal.protein,
          resolvedCarbs: meal.carbs ?? 0,
          resolvedFat: meal.fat ?? 0,
          createdAt: new Date(),
          updatedAt: new Date(),
        }));
      },
    },
    weights: {
      async listForUser() {
        return weights.map((entry, index) => ({
          id: `w-${index}`,
          userId: "user-1",
          measuredAt: new Date(entry.iso),
          weightKg: entry.kg,
          bodyFatPct: entry.bodyFatPct ?? null,
          note: null,
          createdAt: new Date(),
        }));
      },
    },
  };
}

describe("totales diarios de nutrición", () => {
  it("suman todas las comidas registradas el mismo día", async () => {
    const deps = makeDeps(
      [
        { logDate: TODAY, kcal: 475, protein: 32 },
        { logDate: TODAY, kcal: 850, protein: 45 },
        { logDate: TODAY, kcal: 600, protein: 40 },
      ],
      [],
    );

    const summary = await buildStatsSummary(deps as never, "user-1", "7d", TODAY);

    const todayPoint = summary.calories.find((point) => point.date === TODAY);
    expect(todayPoint?.calories).toBe(1925);
    expect(summary.protein.find((point) => point.date === TODAY)?.protein).toBe(117);
  });

  it("un día sin comidas aparece como cero en lugar de desaparecer", async () => {
    const yesterday = addDaysToKey(TODAY, -1);
    const deps = makeDeps([{ logDate: TODAY, kcal: 2000, protein: 100 }], []);

    const summary = await buildStatsSummary(deps as never, "user-1", "7d", TODAY);

    expect(summary.calories).toHaveLength(7);
    const yesterdayPoint = summary.calories.find((point) => point.date === yesterday);
    expect(yesterdayPoint?.calories).toBe(0);
  });

  it("calcula la media y el día pico solo entre los días con registros", async () => {
    const twoDaysAgo = addDaysToKey(TODAY, -2);
    const deps = makeDeps(
      [
        { logDate: TODAY, kcal: 1500, protein: 90 },
        { logDate: twoDaysAgo, kcal: 2500, protein: 150 }, // pico
      ],
      [],
    );

    const summary = await buildStatsSummary(deps as never, "user-1", "7d", TODAY);

    expect(summary.caloriesAvg).toBe(2000);
    expect(summary.caloriesMaxDay?.date).toBe(twoDaysAgo);
    expect(summary.proteinAvg).toBe(120);
  });
});

describe("evolución y métricas del peso", () => {
  it("el peso actual es el último registro y muestra la tendencia suavizada", async () => {
    const entries = [
      { iso: `${addDaysToKey(TODAY, -6)}T12:00:00Z`, kg: 82 },
      { iso: `${addDaysToKey(TODAY, -3)}T12:00:00Z`, kg: 81.5 },
      { iso: `${addDaysToKey(TODAY, -0)}T12:00:00Z`, kg: 81.2 },
    ];
    const deps = makeDeps([], entries);

    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.weight.currentWeightKg).toBe(81.2);
    expect(summary.weights.at(-1)?.trend).not.toBeNull();
  });

  it("un mes perdiendo ~0,5 kg por semana se traduce en un ritmo semanal negativo", async () => {
    const entries = Array.from({ length: 5 }, (_, week) => ({
      iso: `${addDaysToKey(addDaysToKey(TODAY, -28), week * 7)}T12:00:00Z`,
      kg: 85 - week * 0.5,
    }));
    const deps = makeDeps([], entries);

    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.weight.changeSinceStartKg).toBeCloseTo(-2, 6);
    expect(summary.weight.ratePerWeekKg).toBeCloseTo(-0.5, 1);
  });

  it("registra mínimos y máximos dentro del rango elegido", async () => {
    const deps = makeDeps(
      [],
      [
        { iso: `${addDaysToKey(TODAY, -10)}T12:00:00Z`, kg: 84 },
        { iso: `${addDaysToKey(TODAY, -5)}T12:00:00Z`, kg: 82.9 },
        { iso: `${addDaysToKey(TODAY, -2)}T12:00:00Z`, kg: 83.4 },
      ],
    );

    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.weight.minKg).toBe(82.9);
    expect(summary.weight.maxKg).toBe(84);
  });

  it("los registros fuera del rango no afectan a las estadísticas", async () => {
    const farPast = `${addDaysToKey(TODAY, -300)}T12:00:00Z`;
    const recent = `${addDaysToKey(TODAY, -2)}T12:00:00Z`;
    const deps = makeDeps([], [
      { iso: farPast, kg: 95 }, // peso antiguo, fuera del rango de 30 días
      { iso: recent, kg: 83 },
    ]);

    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.weights.map((point) => point.weight)).toEqual([83]);
    expect(summary.weight.currentWeightKg).toBe(83);
  });

  it("las medias semanales agrupan por semanas completas", async () => {
    // 2026-08-19 es miércoles; 2026-08-24 lunes siguiente.
    const deps = makeDeps([], [
      { iso: "2026-08-19T12:00:00Z", kg: 82 },
      { iso: "2026-08-21T12:00:00Z", kg: 81.6 },
      { iso: "2026-08-24T12:00:00Z", kg: 81.4 },
    ]);

    const summary = await buildStatsSummary(deps as never, "user-1", "all", TODAY);

    expect(summary.weeklyWeightAvg).toHaveLength(2);
    expect(summary.weeklyWeightAvg[0].avg).toBeCloseTo(81.8, 6);
    expect(toDateKey(new Date("2026-08-19T12:00:00Z"))).toBeTypeOf("string");
  });
});

describe("grasa corporal y masa libre", () => {
  it("la serie de grasa corporal se llena cuando las entradas tienen bodyFatPct", async () => {
    const deps = makeDeps([], [
      { iso: `${addDaysToKey(TODAY, -6)}T12:00:00Z`, kg: 82, bodyFatPct: 18 },
      { iso: `${addDaysToKey(TODAY, -3)}T12:00:00Z`, kg: 81.5, bodyFatPct: 17 },
      { iso: `${TODAY}T12:00:00Z`, kg: 81.2, bodyFatPct: 16 },
    ]);

    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.bodyFat).toHaveLength(3);
    expect(summary.bodyFat[0].bodyFatPct).toBe(18);
    expect(summary.bodyFat[2].bodyFatPct).toBe(16);
    expect(summary.weight.currentBodyFatPct).toBe(16);
    expect(summary.weight.changeBodyFatPct).toBe(-2);
  });

  it("la serie de grasa corporal queda vacía cuando ninguna entrada tiene bodyFatPct", async () => {
    const deps = makeDeps([], [
      { iso: `${addDaysToKey(TODAY, -3)}T12:00:00Z`, kg: 82 },
      { iso: `${TODAY}T12:00:00Z`, kg: 81.5 },
    ]);

    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.bodyFat).toHaveLength(0);
    expect(summary.weight.currentBodyFatPct).toBeNull();
  });

  it("la masa libre se calcula correctamente a partir del peso y la grasa", async () => {
    // 80 kg con 15% grasa → masa libre = 80 × 0.85 = 68 kg
    const deps = makeDeps([], [
      { iso: `${addDaysToKey(TODAY, -3)}T12:00:00Z`, kg: 80, bodyFatPct: 15 },
    ]);

    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.leanMass).toHaveLength(1);
    expect(summary.leanMass[0].leanMassKg).toBe(68);
    expect(summary.weight.currentLeanMassKg).toBe(68);
  });

  it("las métricas de grasa corporal (mínimo, máximo, cambio) se calculan correctamente", async () => {
    const deps = makeDeps([], [
      { iso: `${addDaysToKey(TODAY, -6)}T12:00:00Z`, kg: 85, bodyFatPct: 20 },
      { iso: `${addDaysToKey(TODAY, -3)}T12:00:00Z`, kg: 83, bodyFatPct: 18 },
      { iso: `${TODAY}T12:00:00Z`, kg: 82, bodyFatPct: 15 },
    ]);

    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.weight.minBodyFatPct).toBe(15);
    expect(summary.weight.maxBodyFatPct).toBe(20);
    expect(summary.weight.changeBodyFatPct).toBe(-5);
    expect(summary.weight.currentLeanMassKg).toBeCloseTo(69.7, 1);
  });
});

describe("datos vacíos y rango all", () => {
  it("sin comidas ni pesos, todo devuelve ceros o null", async () => {
    const deps = makeDeps([], []);
    const summary = await buildStatsSummary(deps as never, "user-1", "30d", TODAY);

    expect(summary.calories).toHaveLength(30);
    expect(summary.caloriesAvg).toBeNull();
    expect(summary.caloriesMaxDay).toBeNull();
    expect(summary.proteinAvg).toBeNull();
    expect(summary.carbsAvg).toBeNull();
    expect(summary.fatAvg).toBeNull();
    expect(summary.weight.currentWeightKg).toBeNull();
    expect(summary.weight.currentTrendKg).toBeNull();
    expect(summary.weight.changeSinceStartKg).toBeNull();
    expect(summary.weight.ratePerWeekKg).toBeNull();
    expect(summary.weight.minKg).toBeNull();
    expect(summary.weight.maxKg).toBeNull();
    expect(summary.bodyFat).toHaveLength(0);
    expect(summary.leanMass).toHaveLength(0);
    expect(summary.weeklyWeightAvg).toHaveLength(0);
  });

  it("rango 'all' incluye todas las comidas sin importar la fecha", async () => {
    const deps = makeDeps(
      [
        { logDate: "2026-01-01", kcal: 2000, protein: 100 },
        { logDate: TODAY, kcal: 1500, protein: 80 },
      ],
      [],
    );

    const summary = await buildStatsSummary(deps as never, "user-1", "all", TODAY);

    expect(summary.caloriesAvg).toBe(1750);
    expect(summary.proteinAvg).toBe(90);
  });

  it("las series de carbs y fat se calculan correctamente", async () => {
    const deps = makeDeps(
      [
        { logDate: TODAY, kcal: 2000, protein: 100, carbs: 200, fat: 70 },
      ],
      [],
    );

    const summary = await buildStatsSummary(deps as never, "user-1", "7d", TODAY);

    const todayCarbs = summary.carbs.find((p) => p.date === TODAY);
    expect(todayCarbs?.carbs).toBe(200);
    expect(summary.carbsAvg).toBe(200);
    expect(summary.carbsMaxDay?.carbs).toBe(200);

    const todayFat = summary.fat.find((p) => p.date === TODAY);
    expect(todayFat?.fat).toBe(70);
    expect(summary.fatAvg).toBe(70);
    expect(summary.fatMaxDay?.fat).toBe(70);
  });
});
