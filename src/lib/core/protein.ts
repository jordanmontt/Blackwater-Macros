import type { Goal, ProteinRange, ProteinRecommendation } from "./types";

/**
 * Evidence-based protein intake ranges (g per kg of body weight per day).
 *
 * Maintain: ISSN position stand (Jäger et al. 2017) — 1.4–2.0 g/kg BW.
 * Surplus:  Morton et al. 2018 (benefit plateaus at ~1.6, upper CI 2.2) and
 *           Iraki et al. 2019 (1.6–2.2 g/kg in an energy surplus).
 * Cut:      Jäger et al. 2017 (2.3–3.1 g/kg of lean mass in a deficit) and
 *           Helms et al. 2014 (IJSNEM), expressed per kg of body weight.
 */
const BODY_WEIGHT_RANGES: Record<Goal, { min: number; max: number }> = {
  maintain: { min: 1.4, max: 2.0 },
  surplus: { min: 1.6, max: 2.2 },
  cut: { min: 1.8, max: 2.7 },
};

/**
 * In a deficit, when body fat is known, protein is set per kg of lean mass
 * (Helms et al. 2014, IJSNEM: 2.3–3.1 g/kg FFM). Lean mass is the better basis
 * there: fat tissue barely raises protein needs, so a body-weight factor
 * overshoots for people with more fat and undershoots for very lean ones.
 */
const LEAN_MASS_CUT_RANGE = { min: 2.3, max: 3.1 };

function range(min: number, max: number): ProteinRange {
  return { min: Math.round(min), max: Math.round(max) };
}

function perKgRange(min: number, max: number): ProteinRange {
  return { min: Math.round(min * 10) / 10, max: Math.round(max * 10) / 10 };
}

function isUsableBodyFat(bodyFatPct: number | null | undefined): bodyFatPct is number {
  return bodyFatPct != null && bodyFatPct > 0 && bodyFatPct < 100;
}

export function calculateProteinRecommendation(
  weightKg: number,
  goal: Goal,
  bodyFatPct: number | null = null,
): ProteinRecommendation {
  const useLeanMass = goal === "cut" && isUsableBodyFat(bodyFatPct);
  const basisKg = useLeanMass ? weightKg * (1 - bodyFatPct / 100) : weightKg;
  const factors = useLeanMass ? LEAN_MASS_CUT_RANGE : BODY_WEIGHT_RANGES[goal];

  const grams = range(basisKg * factors.min, basisKg * factors.max);

  return {
    goal,
    basis: useLeanMass ? "leanMass" : "bodyWeight",
    basisKg: Math.round(basisKg * 10) / 10,
    range: grams,
    perKg: perKgRange(factors.min, factors.max),
    target: Math.round((grams.min + grams.max) / 2),
  };
}
