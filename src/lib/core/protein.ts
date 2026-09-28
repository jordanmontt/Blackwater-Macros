import type { Gender, Goal, ProteinRange, ProteinRecommendation } from "./types";

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

/**
 * Above this BMI the g/kg ranges apply to the weight at this BMI instead of the
 * actual weight. The studies behind the ranges are mostly in normal-weight
 * people, and fat tissue barely raises protein needs: for 110 kg at 1.80 m the
 * cut range would otherwise be 198–297 g/day, far above the >1.3 g/kg of actual
 * weight that already preserves muscle in adults with overweight or obesity
 * (Kokura et al. 2024). Weight at a reference BMI is how clinical nutrition
 * doses protein in obesity (McClave et al. 2016).
 */
export const REFERENCE_BMI = 25;

/**
 * Body fat (%) that goes with BMI 25 in adults, by sex and age band (20–39,
 * 40–59 and 60+ years; Gallagher et al. 2000, Table 4). Below it, a BMI above
 * 25 is lean mass (muscle), not fat. Under 20, or with no birth year, the
 * 20–39 band applies.
 */
export const BODY_FAT_AT_REFERENCE_BMI: Record<Gender, readonly [number, number, number]> = {
  male: [20, 22, 25],
  female: [33, 34, 36],
};

export function normalBodyFatMax(gender: Gender, age: number | null): number {
  const band = age === null || age < 40 ? 0 : age < 60 ? 1 : 2;
  return BODY_FAT_AT_REFERENCE_BMI[gender][band];
}

function isUsableBodyFat(bodyFatPct: number | null | undefined): bodyFatPct is number {
  return bodyFatPct != null && bodyFatPct > 0 && bodyFatPct < 100;
}

/** Height, sex and age (years), to tell when a high BMI is likely fat (all optional). */
export interface ProteinPerson {
  heightCm: number | null;
  gender: Gender | null;
  age: number | null;
}

/**
 * The weight at BMI 25 when the BMI is above it and the extra weight is likely
 * fat, else null. A logged body fat in the normal range means the high BMI is
 * muscle (common in people who lift), so the actual weight is kept.
 */
export function proteinReferenceWeight(
  weightKg: number,
  bodyFatPct: number | null,
  person: ProteinPerson,
): number | null {
  if (person.heightCm === null || person.heightCm <= 0) return null;
  const heightM = person.heightCm / 100;
  const reference = REFERENCE_BMI * heightM * heightM;
  if (weightKg <= reference) return null;
  const muscular =
    isUsableBodyFat(bodyFatPct) &&
    person.gender !== null &&
    bodyFatPct < normalBodyFatMax(person.gender, person.age);
  return muscular ? null : reference;
}

export function calculateProteinRecommendation(
  weightKg: number,
  goal: Goal,
  bodyFatPct: number | null = null,
  person: ProteinPerson = { heightCm: null, gender: null, age: null },
): ProteinRecommendation {
  const useLeanMass = goal === "cut" && isUsableBodyFat(bodyFatPct);
  const reference = useLeanMass ? null : proteinReferenceWeight(weightKg, bodyFatPct, person);
  const basisKg = useLeanMass ? weightKg * (1 - bodyFatPct / 100) : (reference ?? weightKg);
  const factors = useLeanMass ? LEAN_MASS_CUT_RANGE : BODY_WEIGHT_RANGES[goal];

  const grams = range(basisKg * factors.min, basisKg * factors.max);

  return {
    goal,
    basis: useLeanMass ? "leanMass" : reference !== null ? "referenceWeight" : "bodyWeight",
    basisKg: Math.round(basisKg * 10) / 10,
    range: grams,
    perKg: perKgRange(factors.min, factors.max),
    target: Math.round((grams.min + grams.max) / 2),
  };
}
