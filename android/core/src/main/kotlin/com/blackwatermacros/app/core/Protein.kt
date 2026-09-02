package com.blackwatermacros.app.core

/**
 * Mirrors `src/lib/core/protein.ts`. Evidence-based protein intake ranges (g/kg/day).
 * Rounding via `mathRound` reproduces JS `Math.round`.
 */

private val RANGES: Map<Goal, Pair<Double, Double>> = mapOf(
    Goal.MAINTAIN to (1.2 to 1.6),
    Goal.SURPLUS to (1.6 to 2.0),
    Goal.CUT to (1.6 to 2.2),
)

private fun roundToInteger(g: Double): Double = mathRound(g)

private fun range(min: Double, max: Double): ProteinRange = ProteinRange(roundToInteger(min), roundToInteger(max))

private fun perKgRange(min: Double, max: Double): ProteinRange =
    ProteinRange(mathRound(min * 10) / 10, mathRound(max * 10) / 10)

fun calculateProteinRecommendation(weightKg: Double, goal: Goal): ProteinRecommendation {
    val (bwMin, bwMax) = RANGES.getValue(goal)

    val bwRange = range(weightKg * bwMin, weightKg * bwMax)
    val bwPerKg = perKgRange(bwMin, bwMax)

    return ProteinRecommendation(
        goal = goal,
        bodyWeightKg = weightKg,
        bwRange = bwRange,
        bwPerKg = bwPerKg,
        target = mathRound((bwRange.min + bwRange.max) / 2),
    )
}