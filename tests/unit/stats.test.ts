import { describe, expect, it } from "vitest";
import {
  buildDailyNutritionSeries,
  linearRatePerWeek,
  linearSlopePerDay,
  movingAverageByDays,
  rangeToDays,
  weeklyAverages,
  type DataPoint,
} from "@/lib/stats";

describe("tendencia de peso (media móvil de 7 días)", () => {
  it("suaviza el ruido diario mostrando la media de la última semana", () => {
    const points = days("2026-03-01", [80, 81, 80.5, 80, 79.5, 79, 78]);
    const result = movingAverageByDays(points, 7);
    expect(result.at(-1)).toBeCloseTo(79.714, 3);
  });

  it("no se rompe cuando faltan días: usa solo los registros disponibles", () => {
    // Lunes y viernes de la misma semana, sin registros entre medias.
    const points: DataPoint[] = [
      { date: "2026-03-02", value: 80 },
      { date: "2026-03-06", value: 79 },
    ];
    const result = movingAverageByDays(points, 7);
    expect(result).toEqual([80, (80 + 79) / 2]);
  });

  it("excluye los puntos que quedan fuera de la ventana", () => {
    const points: DataPoint[] = [
      { date: "2026-03-01", value: 90 }, // fuera de la ventana del día 8
      { date: "2026-03-08", value: 80 },
    ];
    const result = movingAverageByDays(points, 7);
    expect(result[1]).toBe(80);
  });

  it("devuelve una lista vacía si no hay datos", () => {
    expect(movingAverageByDays([], 7)).toEqual([]);
  });
});

describe("ritmo de cambio (kg/semana)", () => {
  it("detecta una pérdida constante de 0,5 kg por semana", () => {
    // -0.5 kg cada 7 días durante 5 semanas
    const points = Array.from({ length: 5 }, (_, week) => ({
      date: addDaysKey("2026-01-05", week * 7),
      value: 85 - week * 0.5,
    }));
    expect(linearRatePerWeek(points)).toBeCloseTo(-0.5, 6);
  });

  it("es insensible al orden temporal de los registros (regresión, no diferencia)", () => {
    const points: DataPoint[] = [
      { date: "2026-01-12", value: 84.5 },
      { date: "2026-01-05", value: 85 },
      { date: "2026-01-19", value: 84 },
    ];
    expect(linearRatePerWeek(points)).toBeCloseTo(-0.5, 6);
  });

  it("devuelve null con menos de dos fechas distintas", () => {
    expect(linearRatePerDaySafe([{ date: "2026-01-05", value: 80 }])).toBeNull();
    expect(linearRatePerDaySafe([])).toBeNull();
  });

  it("devuelve 0 cuando el peso se mantiene estable", () => {
    const points: DataPoint[] = [
      { date: "2026-01-05", value: 80 },
      { date: "2026-01-19", value: 80 },
    ];
    expect(linearSlopePerDay(points)).toBe(0);
  });

  function linearRatePerDaySafe(points: DataPoint[]) {
    return linearSlopePerDay(points);
  }
});

describe("medias semanales del peso", () => {
  it("agrupa por semanas que empiezan en lunes aunque el mes cambie", () => {
    const points: DataPoint[] = [
      { date: "2026-02-28", value: 82 }, // sábado
      { date: "2026-03-01", value: 81.4 }, // domingo
      { date: "2026-03-02", value: 81 }, // lunes
      { date: "2026-03-03", value: 80.8 }, // martes
    ];
    const weeks = weeklyAverages(points);
    expect(weeks).toHaveLength(2);
    expect(weeks[0].weekStart).toBe("2026-02-23");
    expect(weeks[0].avg).toBeCloseTo((82 + 81.4) / 2, 6);
    expect(weeks[1].weekStart).toBe("2026-03-02");
    expect(weeks[1].avg).toBeCloseTo((81 + 80.8) / 2, 6);
  });

  it("ignora las semanas sin datos en lugar de rellenarlas", () => {
    const points: DataPoint[] = [
      { date: "2026-03-02", value: 80 },
      { date: "2026-03-16", value: 79 }, // dos semanas después
    ];
    expect(weeklyAverages(points).map((week) => week.weekStart)).toEqual([
      "2026-03-02",
      "2026-03-16",
    ]);
  });
});

describe("serie diaria de calorías y proteína", () => {
  it("rellena con ceros los días sin comidas para mostrar huecos honestos", () => {
    const totals = new Map([
      ["2026-03-01", { date: "2026-03-01", calories: 2000, protein: 120, carbs: 250, fat: 70 }],
      ["2026-03-03", { date: "2026-03-03", calories: 1800, protein: 100, carbs: 200, fat: 60 }],
    ]);
    const series = buildDailyNutritionSeries(totals, "2026-03-01", "2026-03-04");
    expect(series.map((point) => point.calories)).toEqual([2000, 0, 1800, 0]);
    expect(series.map((point) => point.protein)).toEqual([120, 0, 100, 0]);
    expect(series.map((point) => point.carbs)).toEqual([250, 0, 200, 0]);
    expect(series.map((point) => point.fat)).toEqual([70, 0, 60, 0]);
  });

  it("cubre rangos de un único día", () => {
    const series = buildDailyNutritionSeries(new Map(), "2026-03-01", "2026-03-01");
    expect(series).toEqual([{ date: "2026-03-01", calories: 0, protein: 0, carbs: 0, fat: 0 }]);
  });
});

describe("rangos de estadísticas", () => {
  it("convierte los nombres de rango en número de días; 'todo' no limita", () => {
    expect(rangeToDays("7d")).toBe(7);
    expect(rangeToDays("30d")).toBe(30);
    expect(rangeToDays("90d")).toBe(90);
    expect(rangeToDays("all")).toBeNull();
  });
});

// --- helpers ---
function days(startKey: string, values: number[]): DataPoint[] {
  return values.map((value, index) => ({ date: addDaysKey(startKey, index), value }));
}

function addDaysKey(key: string, amount: number): string {
  const date = new Date(`${key}T00:00:00`);
  date.setDate(date.getDate() + amount);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
