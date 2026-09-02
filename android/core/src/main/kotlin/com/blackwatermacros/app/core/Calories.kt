package com.blackwatermacros.app.core

/**
 * Mirrors `src/lib/core/calories.ts`. Mifflin-St Jeor BMR, activity multiplier,
 * and calorie target offsets keyed by goal. Rounding via `mathRound` reproduces
 * JS `Math.round` (round-half-up toward +∞).
 */

/** Mifflin-St Jeor (1990). */
fun calculateBMR(gender: Gender, weightKg: Double, heightCm: Double, age: Int): Double {
    val base = 10 * weightKg + 6.25 * heightCm - 5 * age
    return if (gender == Gender.MALE) base + 5 else base - 161
}

/**
 * Activity multiplier from gym frequency (days/week x session minutes) and
 * daily walking minutes. The cascade order matters; first match wins.
 */
fun getActivityMultiplier(gymDays: Int, gymMinutes: Int, walkingMinutes: Int): Double {
    val weeklyGymMinutes = gymDays * gymMinutes

    if (weeklyGymMinutes == 0 && walkingMinutes < 30) return 1.2
    if (weeklyGymMinutes == 0 && walkingMinutes >= 30) return 1.375
    if (weeklyGymMinutes > 0 && weeklyGymMinutes <= 150 && walkingMinutes < 30) return 1.375
    if (weeklyGymMinutes > 0 && weeklyGymMinutes <= 150 && walkingMinutes >= 30) return 1.55
    if (weeklyGymMinutes > 150 && weeklyGymMinutes <= 360 && walkingMinutes >= 30) return 1.55
    if (weeklyGymMinutes > 150 && weeklyGymMinutes <= 360 && walkingMinutes < 30) return 1.375
    if (weeklyGymMinutes > 360 && weeklyGymMinutes <= 540) return 1.725
    return 1.9
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

    return CalorieRecommendation(
        bmr = mathRound(bmr),
        tdee = tdee,
        target = tdee + offsets.target,
        targetMin = tdee + offsets.min,
        targetMax = tdee + offsets.max,
        goal = profile.calorieGoal!!,
    )
}