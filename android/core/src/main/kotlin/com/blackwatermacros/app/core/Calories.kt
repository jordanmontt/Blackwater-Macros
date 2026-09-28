package com.blackwatermacros.app.core

/**
 * Mirrors `src/lib/core/calories.ts`. Mifflin-St Jeor BMR, factorial activity
 * level (PAL), and calorie target offsets keyed by goal. Rounding via `mathRound` reproduces
 * JS `Math.round` (round-half-up toward +∞).
 */

/** Mifflin-St Jeor (1990). */
fun calculateBMR(gender: Gender, weightKg: Double, heightCm: Double, age: Int): Double {
    val base = 10 * weightKg + 6.25 * heightCm - 5 * age
    return if (gender == Gender.MALE) base + 5 else base - 161
}

/** Minutes in a day, the unit of the factorial model below. */
private const val MINUTES_PER_DAY = 1440.0

/** Ordinary living with no exercise, × BMR (FAO/WHO/UNU 2004, low end of 1.40–1.69). */
private const val BASELINE_INTENSITY = 1.4

/** Resistance training including rest between sets (Compendium 2024: 3.5–6 METs). */
private const val GYM_INTENSITY = 4.0

/** Walking at an everyday pace, ~4–5 km/h (Compendium 2024: 3.0 at 4 km/h, 3.5 for pleasure, 3.8 at 4.5–5.5 km/h). */
private const val WALKING_INTENSITY = 3.5

/**
 * Physical activity level (PAL = TDEE / BMR) with the factorial method of
 * FAO/WHO/UNU 2004: minutes of the day weighted by their intensity as a
 * multiple of BMR.
 *
 *   gym = gymDays x gymMinutes / 7   (average minutes per day)
 *   PAL = ((1440 - gym - walking) x 1.4 + gym x 4.0 + walking x 3.5) / 1440
 */
fun getActivityMultiplier(gymDays: Int, gymMinutes: Int, walkingMinutes: Int): Double {
    val gymPerDay = (gymDays * gymMinutes) / 7.0
    val restMinutes = maxOf(MINUTES_PER_DAY - gymPerDay - walkingMinutes, 0.0)
    return (restMinutes * BASELINE_INTENSITY + gymPerDay * GYM_INTENSITY + walkingMinutes * WALKING_INTENSITY) /
        MINUTES_PER_DAY
}

private data class CalorieOffsets(val target: Double, val min: Double, val max: Double)

private val CALORIE_OFFSETS: Map<Goal, CalorieOffsets> = mapOf(
    Goal.CUT to CalorieOffsets(-400.0, -500.0, -300.0),
    Goal.MAINTAIN to CalorieOffsets(0.0, -100.0, 100.0),
    Goal.SURPLUS to CalorieOffsets(300.0, 200.0, 400.0),
)

fun isCalorieProfileComplete(profile: CalorieProfile): Boolean =
    profile.gender != null &&
        profile.birthYear != null &&
        profile.heightCm != null &&
        profile.gymDaysPerWeek != null &&
        profile.gymSessionMinutes != null &&
        profile.walkingMinutesPerDay != null &&
        profile.calorieGoal != null

fun calculateCalorieRecommendation(
    profile: CalorieProfile,
    weightKg: Double,
    currentYear: Int,
): CalorieRecommendation? {
    if (!isCalorieProfileComplete(profile)) return null

    val age = currentYear - profile.birthYear!!
    val bmr = calculateBMR(profile.gender!!, weightKg, profile.heightCm!!, age)
    val multiplier = getActivityMultiplier(
        profile.gymDaysPerWeek!!,
        profile.gymSessionMinutes!!,
        profile.walkingMinutesPerDay!!,
    )
    val tdee = mathRound(bmr * multiplier)
    val offsets = CALORIE_OFFSETS.getValue(profile.calorieGoal!!)
    // Safety floor: the target never goes below the basal metabolic rate (web `calories.ts`).
    val floor = mathRound(bmr)
    fun atLeastBmr(kcal: Double) = maxOf(kcal, floor)

    return CalorieRecommendation(
        bmr = floor,
        activityFactor = mathRound(multiplier * 100) / 100,
        tdee = tdee,
        target = atLeastBmr(tdee + offsets.target),
        targetMin = atLeastBmr(tdee + offsets.min),
        targetMax = atLeastBmr(tdee + offsets.max),
        goal = profile.calorieGoal!!,
    )
}