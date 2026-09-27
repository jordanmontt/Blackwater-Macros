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

/**
 * Above this BMI the g/kg ranges apply to the weight at this BMI (web `REFERENCE_BMI`):
 * fat tissue barely raises protein needs (Kokura et al. 2024; McClave et al. 2016).
 */
const val REFERENCE_BMI = 25.0

/** Body fat (%) up to which a high BMI is taken to be muscle (Gallagher et al. 2000). */
val NORMAL_BODY_FAT_MAX: Map<Gender, Double> = mapOf(Gender.MALE to 25.0, Gender.FEMALE to 33.0)

private fun isUsableBodyFat(bodyFatPct: Double?): Boolean =
    bodyFatPct != null && bodyFatPct > 0 && bodyFatPct < 100

/** Height and sex, to tell when a high BMI is likely fat (both optional). */
data class ProteinPerson(val heightCm: Double?, val gender: Gender?)

/**
 * The weight at BMI 25 when the BMI is above it and the extra weight is likely
 * fat, else null. A logged body fat in the normal range means the high BMI is
 * muscle, so the actual weight is kept.
 */
fun proteinReferenceWeight(weightKg: Double, bodyFatPct: Double?, person: ProteinPerson): Double? {
    val heightCm = person.heightCm?.takeIf { it > 0 } ?: return null
    val heightM = heightCm / 100
    val reference = REFERENCE_BMI * heightM * heightM
    if (weightKg <= reference) return null
    val muscular = isUsableBodyFat(bodyFatPct) && person.gender != null &&
        bodyFatPct!! < NORMAL_BODY_FAT_MAX.getValue(person.gender)
    return if (muscular) null else reference
}

fun calculateProteinRecommendation(
    weightKg: Double,
    goal: Goal,
    bodyFatPct: Double? = null,
    person: ProteinPerson = ProteinPerson(null, null),
): ProteinRecommendation {
    val useLeanMass = goal == Goal.CUT && isUsableBodyFat(bodyFatPct)
    val reference = if (useLeanMass) null else proteinReferenceWeight(weightKg, bodyFatPct, person)
    val basisKg = if (useLeanMass) weightKg * (1 - bodyFatPct!! / 100) else reference ?: weightKg
    val (min, max) = if (useLeanMass) LEAN_MASS_CUT_RANGE else BODY_WEIGHT_RANGES.getValue(goal)

    val grams = range(basisKg * min, basisKg * max)

    return ProteinRecommendation(
        goal = goal,
        basis = when {
            useLeanMass -> ProteinBasis.LEAN_MASS
            reference != null -> ProteinBasis.REFERENCE_WEIGHT
            else -> ProteinBasis.BODY_WEIGHT
        },
        basisKg = mathRound(basisKg * 10) / 10,
        range = grams,
        perKg = perKgRange(min, max),
        target = mathRound((grams.min + grams.max) / 2),
    )
}
