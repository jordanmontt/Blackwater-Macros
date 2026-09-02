import type { Goal, ProteinRange, ProteinRecommendation } from "./types";

/**
 * Evidence-based protein intake ranges (g/kg/day).
 *
 * Maintain: ISSN position stand — 1.2–1.6 g/kg BW
 * Surplus:  Morton et al. 2018 — plateau ~1.6, practical ceiling 2.0 g/kg BW
 * Cut:     Kokura et al. 2024 — 1.6–2.2 g/kg BW
 */
const RANGES: Record<Goal, { bwMin: number; bwMax: number }> = {
  maintain: { bwMin: 1.2, bwMax: 1.6 },
  surplus: { bwMin: 1.6, bwMax: 2.0 },
  cut: { bwMin: 1.6, bwMax: 2.2 },
};

function roundToInteger(g: number): number {
  return Math.round(g);
}

function range(min: number, max: number): ProteinRange {
  return { min: roundToInteger(min), max: roundToInteger(max) };
}

function perKgRange(min: number, max: number): ProteinRange {
  return { min: Math.round(min * 10) / 10, max: Math.round(max * 10) / 10 };
}

export function calculateProteinRecommendation(
  weightKg: number,
  goal: Goal,
): ProteinRecommendation {
  const { bwMin, bwMax } = RANGES[goal];

  const bwRange = range(weightKg * bwMin, weightKg * bwMax);
  const bwPerKg = perKgRange(bwMin, bwMax);

  return {
    goal,
    bodyWeightKg: weightKg,
    bwRange,
    bwPerKg,
    target: Math.round((bwRange.min + bwRange.max) / 2),
  };
}
