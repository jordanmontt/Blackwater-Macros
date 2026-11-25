import { describe, expect, it } from "vitest";
import { chartSummary } from "@/components/weight-fat-chart";
import { formatDateKeyShort, formatNumber } from "@/i18n/format";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos del gráfico de peso para un lector de pantalla: su nombre dice en una
 * frase lo que muestra, con los números del usuario (de dónde a dónde va la
 * tendencia, o la grasa, y entre qué fechas).
 */

const rows = [
  { date: "2026-10-26", weight: 83.4, weightTrend: 83.4, bodyFatPct: 17.2 },
  { date: "2026-11-10", weight: 82.4, weightTrend: 82.6 },
  { date: "2026-11-24", weight: 81.5, weightTrend: 81.8, bodyFatPct: 15.7 },
];

describe("gráfico de peso: resumen en una frase", () => {
  it("el peso dice de dónde a dónde va la tendencia", () => {
    expect(chartSummary(rows, "weight")).toBe(
      formatTemplate(t.stats.weightChartSummary, {
        from: formatNumber(83.4, 1),
        to: formatNumber(81.8, 1),
        start: formatDateKeyShort("2026-10-26"),
        end: formatDateKeyShort("2026-11-24"),
      }),
    );
  });

  it("la grasa usa solo los días con medida", () => {
    expect(chartSummary(rows, "bodyFat")).toBe(
      formatTemplate(t.stats.bodyFatChartSummary, {
        from: formatNumber(17.2, 1),
        to: formatNumber(15.7, 1),
        start: formatDateKeyShort("2026-10-26"),
        end: formatDateKeyShort("2026-11-24"),
      }),
    );
  });
});
