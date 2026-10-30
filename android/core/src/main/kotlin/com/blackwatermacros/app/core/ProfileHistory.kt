package com.blackwatermacros.app.core

/** Mirrors `src/lib/core/profile-history.ts`. */

/**
 * A former profile and the last day it applied to (inclusive). The current
 * profile applies to every day after the last period; an empty history means
 * the current profile applies to every day.
 */
data class ProfilePeriod(val until: String, val profile: CalorieProfile)

/** A weigh-in on its local calendar day. */
data class DatedWeight(val date: String, val weightKg: Double, val bodyFatPct: Double?)

/** A min–max target (kcal or g per day). */
data class TargetRange(val min: Double, val max: Double)

data class DayTargets(val calorie: CalorieRecommendation?, val protein: ProteinRecommendation?)

data class AverageTargets(val calorie: TargetRange?, val protein: TargetRange?)

/** The measured expenditure a day's target uses ([weeklyExpenditure]); none by default. */
typealias MeasuredOn = (String) -> ExpenditureEstimate?

private val NOT_MEASURED: MeasuredOn = { null }

/**
 * The history after replacing [previous] with [next] today: changes apply from
 * today on, so days up to yesterday keep [previous]. Unchanged when nothing
 * changed, when [previous] gave no calorie target (an unfinished profile: the
 * new one covers the earlier days too), or when the days up to yesterday are
 * already kept (a second edit the same day).
 */
fun withProfileChange(
    previous: CalorieProfile,
    history: List<ProfilePeriod>,
    next: CalorieProfile,
    today: String,
): List<ProfilePeriod> {
    if (previous == next || !isCalorieProfileComplete(previous)) return history
    val yesterday = addDaysToKey(today, -1)
    if (history.any { it.until >= yesterday }) return history
    return history + ProfilePeriod(yesterday, previous)
}

/** The profile that applied on [day]: the earliest period still running that day, else the current one. */
fun profileOn(profile: CalorieProfile, history: List<ProfilePeriod>, day: String): CalorieProfile =
    history.filter { it.until >= day }.minByOrNull { it.until }?.profile ?: profile

/**
 * The last weigh-in on or before [day]; before the first weigh-in, the first
 * one (the closest the app knows). [weights] in measuring order.
 */
fun weightOn(weights: List<DatedWeight>, day: String): DatedWeight? =
    weights.lastOrNull { it.date <= day } ?: weights.firstOrNull()

/** Body fat is not logged at every weigh-in: the last one on or before [day], else the first one. */
fun bodyFatOn(weights: List<DatedWeight>, day: String): Double? {
    val logged = weights.filter { it.bodyFatPct != null }
    return (logged.lastOrNull { it.date <= day } ?: logged.firstOrNull())?.bodyFatPct
}

/**
 * The calorie and protein targets of [day], with the profile, weight and body
 * fat known that day, the age in that day's year and the expenditure measured
 * at the start of that week ([measuredOn]). Today's targets are the usual ones
 * (current profile, latest weigh-in).
 */
fun targetsOn(
    profile: CalorieProfile,
    history: List<ProfilePeriod>,
    weights: List<DatedWeight>,
    day: String,
    measuredOn: MeasuredOn = NOT_MEASURED,
): DayTargets {
    val weight = weightOn(weights, day) ?: return DayTargets(null, null)
    val dayProfile = profileOn(profile, history, day)
    val year = day.substring(0, 4).toInt()
    val calorie = calculateCalorieRecommendation(dayProfile, weight.weightKg, year, measuredOn(day))
    val protein = dayProfile.calorieGoal?.let { goal ->
        calculateProteinRecommendation(
            weight.weightKg,
            goal,
            bodyFatOn(weights, day),
            ProteinPerson(
                dayProfile.heightCm,
                dayProfile.gender,
                dayProfile.birthYear?.let { year - it },
                strengthTraining = doesStrengthTraining(dayProfile),
            ),
        )
    }
    return DayTargets(calorie, protein)
}

/**
 * The mean of the targets of [days] (e.g. the logged days of a period), each
 * with its own profile, weight and measured expenditure. Null for a target no day has.
 */
fun averageTargets(
    profile: CalorieProfile,
    history: List<ProfilePeriod>,
    weights: List<DatedWeight>,
    days: List<String>,
    measuredOn: MeasuredOn = NOT_MEASURED,
): AverageTargets {
    val calories = mutableListOf<TargetRange>()
    val proteins = mutableListOf<TargetRange>()
    for (day in days) {
        val targets = targetsOn(profile, history, weights, day, measuredOn)
        targets.calorie?.let { calories += TargetRange(it.targetMin, it.targetMax) }
        targets.protein?.let { proteins += TargetRange(it.range.min, it.range.max) }
    }
    return AverageTargets(meanRange(calories), meanRange(proteins))
}

private fun meanRange(ranges: List<TargetRange>): TargetRange? {
    if (ranges.isEmpty()) return null
    return TargetRange(
        mathRound(ranges.sumOf { it.min } / ranges.size),
        mathRound(ranges.sumOf { it.max } / ranges.size),
    )
}
