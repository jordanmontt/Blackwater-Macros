import { describe, expect, it } from "vitest";
import { buildStatsFromData, type StatsMeal, type StatsWeight } from "@/lib/stats-builder";
import { addDaysToKey } from "@/lib/dates";

/**
 * Pruebas técnicas del constructor de estadísticas compartido
 * (lib/stats-builder.ts) — la misma pieza pura que usan tanto el servidor
 * (stats-service) como el modo demo, para garantizar que no hay divergencia.
 */

const TODAY = "2026-08-23";

function meals(points: { date: string; kcal: number; protein: number }[]): StatsMeal[] {
  return points.map((p) => ({
    logDate: p.date,
    resolvedCalories: p.kcal,
    resolvedProtein: p.protein,
    resolvedCarbs: 0,
    resolvedFat: 0,
  }));
}

function weights(entries: { iso: string; kg: number; bodyFatPct?: number | null }[]): StatsWeight[] {
  return entries.map((e) => ({
    measuredAt: new Date(e.iso),
    weightKg: e.kg,
    bodyFatPct: e.bodyFatPct ?? null,
  }));
}

describe("buildStatsFromData", () => {
  it("suma todas las comidas de un día y rellena con ceros los días vacíos", () => {
    const summary = buildStatsFromData(
      meals([
        { date: TODAY, kcal: 475, protein: 32 },
        { date: TODAY, kcal: 850, protein: 45 },
        { date: TODAY, kcal: 600, protein: 40 },
      ]),
      [],
      "7d",
      TODAY,
    );

    expect(summary.calories.find((p) => p.date === TODAY)?.calories).toBe(1925);
    expect(summary.calories).toHaveLength(7);
    const yesterday = addDaysToKey(TODAY, -1);
    expect(summary.calories.find((p) => p.date === yesterday)?.calories).toBe(0);
  });

  it("calcula media y día pico solo entre días con registros", () => {
    const twoDaysAgo = addDaysToKey(TODAY, -2);
    const summary = buildStatsFromData(
      meals([
        { date: TODAY, kcal: 1500, protein: 90 },
        { date: twoDaysAgo, kcal: 2500, protein: 150 },
      ]),
      [],
      "7d",
      TODAY,
    );

    expect(summary.caloriesAvg).toBe(2000);
    expect(summary.caloriesMaxDay?.date).toBe(twoDaysAgo);
    expect(summary.proteinAvg).toBe(120);
  });

  it("peso actual igual al último registro y tendencia suavizada presente", () => {
    const summary = buildStatsFromData(
      [],
      weights([
        { iso: `${addDaysToKey(TODAY, -6)}T12:00:00Z`, kg: 82 },
        { iso: `${addDaysToKey(TODAY, -3)}T12:00:00Z`, kg: 81.5 },
        { iso: `${TODAY}T12:00:00Z`, kg: 81.2 },
      ]),
      "30d",
      TODAY,
    );

    expect(summary.weight.currentWeightKg).toBe(81.2);
    expect(summary.weights.at(-1)?.trend).not.toBeNull();
  });

  it("un mes perdiendo ~0,5 kg/semana produce ritmo semanal negativo y cambio total", () => {
    const entries = Array.from({ length: 5 }, (_, week) => ({
      iso: `${addDaysToKey(addDaysToKey(TODAY, -28), week * 7)}T12:00:00Z`,
      kg: 85 - week * 0.5,
      bodyFatPct: null,
    }));
    const summary = buildStatsFromData([], weights(entries), "30d", TODAY);

    expect(summary.weight.changeSinceStartKg).toBeCloseTo(-2, 6);
    expect(summary.weight.ratePerWeekKg).toBeCloseTo(-0.5, 1);
  });

  it("construye series de grasa corporal y masa magra solo con datos válidos de grasa", () => {
    const summary = buildStatsFromData(
      [],
      weights([
        { iso: `${addDaysToKey(TODAY, -5)}T08:00:00Z`, kg: 86, bodyFatPct: 20 },
        { iso: `${TODAY}T08:00:00Z`, kg: 84, bodyFatPct: 18 },
        { iso: `${addDaysToKey(TODAY, -2)}T08:00:00Z`, kg: 85, bodyFatPct: null },
      ]),
      "30d",
      TODAY,
    );

    expect(summary.bodyFat.length).toBe(2);
    expect(summary.weight.currentBodyFatPct).toBe(18);
    // masa magra = peso × (1 - grasa/100)
    expect(summary.weight.currentLeanMassKg).toBeCloseTo(84 * 0.82, 2);
    expect(summary.leanMass.length).toBe(2);
  });

  it("devuelve nulos cuando no hay comidas ni pesos en el rango", () => {
    const summary = buildStatsFromData([], [], "7d", TODAY);
    expect(summary.caloriesAvg).toBeNull();
    expect(summary.weight.currentWeightKg).toBeNull();
    expect(summary.weights).toEqual([]);
  });
});
