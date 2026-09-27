package com.blackwatermacros.app.core

/**
 * Mirrors `src/lib/core/protein.ts`. Evidence-based protein intake ranges (g/kg/day).
 * Rounding via `mathRound` reproduces JS `Math.round`.
 *
 * Maintain: ISSN position stand (Jäger et al. 2017), 1.4–2.0 g/kg BW.
 * Surplus:  Morton et al. 2018 and Iraki et al. 2019, 1.6–2.2 g/kg BW.
 * Cut:      Jäger et al. 2017 and Helms et al. 2014 (IJSNEM), 1.8–2.7 g/kg BW,
 *           or 2.3–3.1 g/kg of lean mass when body fat is known.
 */

private val BODY_WEIGHT_RANGES: Map<Goal, Pair<Double, Double>> = mapOf(
    Goal.MAINTAIN to (1.4 to 2.0),
    Goal.SURPLUS to (1.6 to 2.2),
    Goal.CUT to (1.8 to 2.7),
)

private val LEAN_MASS_CUT_RANGE = 2.3 to 3.1

private fun range(min: Double, max: Double): ProteinRange = ProteinRange(mathRound(min), mathRound(max))

private fun perKgRange(min: Double, max: Double): ProteinRange =
    ProteinRange(mathRound(min * 10) / 10, mathRound(max * 10) / 10)

private fun isUsableBodyFat(bodyFatPct: Double?): Boolean =
    bodyFatPct != null && bodyFatPct > 0 && bodyFatPct < 100

fun calculateProteinRecommendation(
    weightKg: Double,
    goal: Goal,
    bodyFatPct: Double? = null,
): ProteinRecommendation {
    val useLeanMass = goal == Goal.CUT && isUsableBodyFat(bodyFatPct)
    val basisKg = if (useLeanMass) weightKg * (1 - bodyFatPct!! / 100) else weightKg
    val (min, max) = if (useLeanMass) LEAN_MASS_CUT_RANGE else BODY_WEIGHT_RANGES.getValue(goal)

    val grams = range(basisKg * min, basisKg * max)

    return ProteinRecommendation(
        goal = goal,
        basis = if (useLeanMass) ProteinBasis.LEAN_MASS else ProteinBasis.BODY_WEIGHT,
        basisKg = mathRound(basisKg * 10) / 10,
        range = grams,
        perKg = perKgRange(min, max),
        target = mathRound((grams.min + grams.max) / 2),
    )
}
