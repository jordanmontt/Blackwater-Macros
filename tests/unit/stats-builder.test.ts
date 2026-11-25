import { describe, expect, it } from "vitest";
import { buildStatsFromData, type StatsMeal, type StatsWeight } from "@/lib/core/stats-builder";
import { addDaysToKey } from "@/lib/core/dates";

/**
 * Pruebas técnicas del constructor de estadísticas compartido
 * (lib/core/stats-builder.ts) — la misma pieza pura que usan tanto el servidor
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

function weights(
  entries: { iso: string; kg: number; bodyFatPct?: number | null; fasted?: boolean; timeZone?: string | null }[],
): StatsWeight[] {
  return entries.map((e) => ({
    measuredAt: new Date(e.iso),
    weightKg: e.kg,
    bodyFatPct: e.bodyFatPct ?? null,
    fasted: e.fasted ?? true,
    timeZone: e.timeZone ?? null,
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

  it("el ritmo compara solo pesajes tomados igual: pasar a pesarse en ayunas no es bajar", () => {
    const entries = Array.from({ length: 28 }, (_, i) => ({
      iso: `${addDaysToKey(TODAY, -(27 - i))}T12:00:00Z`,
      kg: i < 14 ? 80.8 : 80,
      fasted: i >= 14,
    }));
    expect(buildStatsFromData([], weights(entries), "30d", TODAY).weight.ratePerWeekKg).toBe(0);
    const unmarked = entries.map(({ iso, kg }) => ({ iso, kg }));
    expect(buildStatsFromData([], weights(unmarked), "30d", TODAY).weight.ratePerWeekKg).toBeLessThan(-0.2);
  });

  it("la tendencia y el cambio usan solo los pesajes tomados como la mayoría", () => {
    // Two weeks fasted at 80 kg, and the last three evenings also after dinner at 81,2 kg.
    const entries = Array.from({ length: 14 }, (_, i) => addDaysToKey(TODAY, -(13 - i))).flatMap((day, i) => [
      { iso: `${day}T10:00:00Z`, kg: 80 },
      ...(i >= 11 ? [{ iso: `${day}T14:00:00Z`, kg: 81.2, fasted: false }] : []),
    ]);
    const summary = buildStatsFromData([], weights(entries), "30d", TODAY);
    expect(summary.weights.filter((point) => !point.inTrend)).toHaveLength(3);
    expect(summary.weights.at(-1)).toMatchObject({ weight: 81.2, trend: 80, inTrend: false });
    expect(summary.weight.currentTrendKg).toBe(80);
    expect(summary.weight.changeSinceStartKg).toBe(0);
    // The last weigh-in is still the big number: it is what the scale said.
    expect(summary.weight.currentWeightKg).toBe(81.2);

    // Unmarked, the same weigh-ins pull the trend up as if 1,2 kg had been gained.
    const unmarked = buildStatsFromData([], weights(entries.map(({ iso, kg }) => ({ iso, kg }))), "30d", TODAY);
    expect(unmarked.weight.currentTrendKg).toBeGreaterThan(80.2);
    expect(unmarked.weight.changeSinceStartKg).toBeCloseTo(1.2, 6);
  });

  it("si casi siempre se pesa sin ayunar, esos pesajes son la tendencia", () => {
    const entries = Array.from({ length: 10 }, (_, i) => {
      const fasted = i < 2;
      return { iso: `${addDaysToKey(TODAY, -(9 - i))}T${fasted ? "10" : "14"}:00:00Z`, kg: fasted ? 80 : 81, fasted };
    });
    const summary = buildStatsFromData([], weights(entries), "30d", TODAY);
    expect(summary.weights.map((point) => point.inTrend)).toEqual([false, false, ...Array(8).fill(true)]);
    expect(summary.weight.currentTrendKg).toBe(81);
    expect(summary.weight.changeSinceStartKg).toBe(0);
  });

  it("construye la serie de grasa corporal solo con datos válidos de grasa", () => {
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
  });

  it("la grasa corporal sigue la misma regla: su tendencia y su cambio solo usan las lecturas tomadas como la mayoría", () => {
    // Two weeks fasted at 20 %, and the last three evenings also after dinner at 22 %.
    const entries = Array.from({ length: 14 }, (_, i) => addDaysToKey(TODAY, -(13 - i))).flatMap((day, i) => [
      { iso: `${day}T10:00:00Z`, kg: 80, bodyFatPct: 20 },
      ...(i >= 11 ? [{ iso: `${day}T14:00:00Z`, kg: 81.2, bodyFatPct: 22, fasted: false }] : []),
    ]);
    const summary = buildStatsFromData([], weights(entries), "30d", TODAY);
    expect(summary.bodyFat.filter((point) => !point.inTrend)).toHaveLength(3);
    expect(summary.bodyFat.at(-1)).toMatchObject({ bodyFatPct: 22, trend: 20, inTrend: false });
    expect(summary.weight.changeBodyFatPct).toBe(0);
    // The last reading is still the one shown: it is what the scale said.
    expect(summary.weight.currentBodyFatPct).toBe(22);

    const unmarked = buildStatsFromData([], weights(entries.map(({ iso, kg, bodyFatPct }) => ({ iso, kg, bodyFatPct }))), "30d", TODAY);
    expect(unmarked.weight.changeBodyFatPct).toBe(2);
  });

  it("la mayoría de la grasa se cuenta entre las lecturas con grasa, no entre todos los pesajes", () => {
    // Ten fasted weigh-ins without body fat, and three after eating with it.
    const entries = [
      ...Array.from({ length: 10 }, (_, i) => ({ iso: `${addDaysToKey(TODAY, -(12 - i))}T10:00:00Z`, kg: 80 })),
      ...[21.8, 21.6, 21.4].map((bodyFatPct, i) => ({
        iso: `${addDaysToKey(TODAY, -(2 - i))}T14:00:00Z`,
        kg: 81,
        bodyFatPct,
        fasted: false,
      })),
    ];
    const summary = buildStatsFromData([], weights(entries), "30d", TODAY);
    expect(summary.weights.filter((point) => !point.inTrend)).toHaveLength(3);
    expect(summary.bodyFat.every((point) => point.inTrend)).toBe(true);
    expect(summary.weight.changeBodyFatPct).toBe(-0.4);
  });

  it("devuelve nulos cuando no hay comidas ni pesos en el rango", () => {
    const summary = buildStatsFromData([], [], "7d", TODAY);
    expect(summary.caloriesAvg).toBeNull();
    expect(summary.weight.currentWeightKg).toBeNull();
    expect(summary.weights).toEqual([]);
  });
});
