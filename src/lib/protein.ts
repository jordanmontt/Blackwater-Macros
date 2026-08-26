import type { ProteinGoal, ProteinRange, ProteinRecommendation } from "./types";

/**
 * Evidence-based protein intake ranges (g/kg/day).
 *
 * Maintain: ISSN position stand — 1.2–1.6 g/kg BW
 * Build:    Morton et al. 2018 — plateau ~1.6, practical ceiling 2.0 g/kg BW
 * Cut:      Kokura et al. 2024 — 1.6–2.2 g/kg BW;
 *           Helms et al. — 2.3–3.1 g/kg FFM for lean athletes
 */
const RANGES: Record<ProteinGoal, { bwMin: number; bwMax: number }> = {
  maintain: { bwMin: 1.2, bwMax: 1.6 },
  build: { bwMin: 1.6, bwMax: 2.0 },
  cut: { bwMin: 1.6, bwMax: 2.2 },
};

const FFM_MIN_PER_KG = 2.3;
const FFM_MAX_PER_KG = 3.1;

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
  bodyFatPct: number | null,
  goal: ProteinGoal,
): ProteinRecommendation {
  const { bwMin, bwMax } = RANGES[goal];

  const bwRange = range(weightKg * bwMin, weightKg * bwMax);
  const bwPerKg = perKgRange(bwMin, bwMax);

  let ffmRange: ProteinRange | null = null;
  let ffmPerKg: ProteinRange | null = null;

  if (goal === "cut" && bodyFatPct !== null && bodyFatPct > 0 && bodyFatPct < 100) {
    const ffm = weightKg * (1 - bodyFatPct / 100);
    ffmRange = range(ffm * FFM_MIN_PER_KG, ffm * FFM_MAX_PER_KG);
    ffmPerKg = perKgRange(FFM_MIN_PER_KG, FFM_MAX_PER_KG);
  }

  return {
    goal,
    bodyWeightKg: weightKg,
    bodyFatPct,
    bwRange,
    ffmRange,
    bwPerKg,
    ffmPerKg,
  };
}
