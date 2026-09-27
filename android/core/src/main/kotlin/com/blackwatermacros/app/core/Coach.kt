package com.blackwatermacros.app.core

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Mirrors `src/lib/core/coach.ts`: weight projection and the data summary the
 * coach sees. The context text must be identical to the web's for the same data.
 */

data class WeightProjection(
    val days: Int,
    /** Trend value today, kg. */
    val currentKg: Double,
    val projectedKg: Double,
    /** +- kg, 95 % interval of the trend line at the projected date. */
    val marginKg: Double,
    val ratePerWeek: Double,
)

/**
 * Where the weight trend of the last 4 weeks (today included) leads in `days`
 * days if it continues. Same data rules as the measured expenditure. The margin
 * is the 95 % interval of the fitted line at that date.
 */
fun weightProjection(weights: List<DataPoint>, today: String, days: Int = 30): WeightProjection? {
    val from = addDaysToKey(today, -(EXPENDITURE_WINDOW_DAYS - 1))
    val recent = weights.filter { it.date >= from && it.date <= today }.sortedBy { it.date }
    if (recent.map { it.date }.toSet().size < MIN_WEIGH_INS) return null
    if (daysBetweenKeys(recent.first().date, recent.last().date) < MIN_WEIGHT_SPAN_DAYS) return null

    val trend = fitWeightTrend(recent) ?: return null
    val xToday = daysBetweenKeys(trend.origin, today).toDouble()
    val xTarget = xToday + days
    fun fitted(x: Double) = trend.meanY + trend.slopePerDay * (x - trend.meanX)
    val margin = Z_95 * trend.sigma * sqrt(1.0 / trend.n + (xTarget - trend.meanX).pow(2) / trend.sxx)

    return WeightProjection(
        days = days,
        currentKg = round1(fitted(xToday)),
        projectedKg = round1(fitted(xTarget)),
        marginKg = round1(margin),
        ratePerWeek = round2(trend.slopePerDay * 7),
    )
}

/** Minimal meal shape the coach needs. */
data class CoachMeal(
    val logDate: String,
    val title: String,
    val ingredients: List<IngredientInput>,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
)

data class CoachWeight(val date: String, val weightKg: Double, val bodyFatPct: Double?)

data class CoachInput(
    val today: String,
    val profile: CalorieProfile,
    val calorie: CalorieRecommendation?,
    val protein: ProteinRecommendation?,
    val expenditure: ExpenditureEstimate?,
    val meals: List<CoachMeal>,
    /** Sorted by date, oldest first. */
    val weights: List<CoachWeight>,
)

/** Days of daily totals included, before today. */
const val COACH_HISTORY_DAYS = 14

/** How far back the weight summary looks. */
const val COACH_WEIGHT_DAYS = 60

private fun num(value: Double): String = plainNumber(value)

private fun num(value: Int): String = value.toString()

private fun goalText(goal: Goal): String = when (goal) {
    Goal.CUT -> "cut (lose fat)"
    Goal.MAINTAIN -> "maintain weight"
    Goal.SURPLUS -> "bulk (gain muscle)"
}

private fun macros(calories: Double, protein: Double, carbs: Double, fat: Double): String =
    "${num(mathRound(calories))} kcal, ${num(round1(protein))} g protein, ${num(round1(carbs))} g carbs, ${num(round1(fat))} g fat"

private fun remaining(label: String, unit: String, eaten: Double, min: Double, max: Double): String {
    val toMin = mathRound(min - eaten)
    val toMax = mathRound(max - eaten)
    return when {
        toMax < 0 -> "$label: over the target range by ${num(-toMax)} $unit"
        toMin <= 0 -> "$label: within the target range (up to ${num(toMax)} $unit more)"
        else -> "$label: ${num(toMin)}–${num(toMax)} $unit left to reach the target range"
    }
}

private data class DayTotals(val calories: Double, val protein: Double, val carbs: Double, val fat: Double)

private fun dailyTotals(meals: List<CoachMeal>): Map<String, DayTotals> {
    val totals = linkedMapOf<String, DayTotals>()
    for (meal in meals) {
        val day = totals[meal.logDate] ?: DayTotals(0.0, 0.0, 0.0, 0.0)
        totals[meal.logDate] = DayTotals(
            day.calories + meal.resolvedCalories,
            day.protein + meal.resolvedProtein,
            day.carbs + meal.resolvedCarbs,
            day.fat + meal.resolvedFat,
        )
    }
    return totals
}

/**
 * The user's data as compact English text for the coach's system prompt.
 * Deterministic: same input, same text, on web and Android.
 */
fun buildCoachContext(input: CoachInput): String {
    val today = input.today
    val profile = input.profile
    val calorie = input.calorie
    val protein = input.protein
    val expenditure = input.expenditure
    val lines = mutableListOf("Today: $today")

    // Profile
    val year = today.substring(0, 4).toInt()
    val parts = listOf(
        profile.gender?.let { if (it == Gender.MALE) "male" else "female" } ?: "sex unknown",
        profile.birthYear?.let { "${num(year - it)} years" } ?: "age unknown",
        profile.heightCm?.let { "${num(it)} cm" } ?: "height unknown",
        profile.calorieGoal?.let { "goal: ${goalText(it)}" } ?: "goal unknown",
    )
    lines += "Profile: ${parts.joinToString(", ")}."
    if (profile.gymDaysPerWeek != null && profile.gymSessionMinutes != null && profile.walkingMinutesPerDay != null) {
        lines += "Activity: gym ${num(profile.gymDaysPerWeek)} days/week × ${num(profile.gymSessionMinutes)} min, " +
            "walking ${num(profile.walkingMinutesPerDay)} min/day."
    }

    // Targets
    lines += if (calorie != null) {
        "Calorie target: ${num(calorie.targetMin)}–${num(calorie.targetMax)} kcal/day (BMR ${num(calorie.bmr)}, " +
            "estimated TDEE ${num(calorie.tdee)}, activity factor ${num(calorie.activityFactor)})."
    } else {
        "Calorie target: not available (profile incomplete)."
    }
    if (protein != null) {
        val basis = if (protein.basis == ProteinBasis.LEAN_MASS) {
            "${num(protein.perKg.min)}–${num(protein.perKg.max)} g/kg of ${num(protein.basisKg)} kg lean mass"
        } else {
            "${num(protein.perKg.min)}–${num(protein.perKg.max)} g/kg body weight"
        }
        lines += "Protein target: ${num(protein.range.min)}–${num(protein.range.max)} g/day ($basis)."
    }
    lines += if (expenditure != null) {
        "Measured expenditure (energy balance, last ${num(expenditure.windowDays)} days): " +
            "${num(expenditure.tdee)} ± ${num(expenditure.margin)} kcal/day."
    } else {
        "Measured expenditure: not enough data yet (needs 4 weeks of logged meals and frequent weigh-ins)."
    }

    // Today
    val totals = dailyTotals(input.meals)
    val todaysMeals = input.meals.filter { it.logDate == today }
    if (todaysMeals.isEmpty()) {
        lines += "Today's meals: none logged yet."
    } else {
        lines += "Today's meals:"
        for (meal in todaysMeals) {
            val items = meal.ingredients
                .filter { it.name.trim().isNotEmpty() }
                .map { if (!it.quantity.isNullOrEmpty()) "${it.name} (${it.quantity})" else it.name }
            val itemText = if (items.isNotEmpty()) ". Items: ${items.joinToString(", ")}" else ""
            lines += "- ${meal.title}: ${macros(meal.resolvedCalories, meal.resolvedProtein, meal.resolvedCarbs, meal.resolvedFat)}$itemText."
        }
    }
    val eaten = totals[today] ?: DayTotals(0.0, 0.0, 0.0, 0.0)
    lines += "Today so far: ${macros(eaten.calories, eaten.protein, eaten.carbs, eaten.fat)}."
    if (calorie != null) lines += "${remaining("Calories", "kcal", eaten.calories, calorie.targetMin, calorie.targetMax)}."
    if (protein != null) lines += "${remaining("Protein", "g", eaten.protein, protein.range.min, protein.range.max)}."

    // History (logged days only)
    val history = (COACH_HISTORY_DAYS downTo 1).map { n ->
        val date = addDaysToKey(today, -n)
        val day = totals[date] ?: DayTotals(0.0, 0.0, 0.0, 0.0)
        DailyNutritionPoint(date, day.calories, day.protein, day.carbs, day.fat)
    }
    val logged = history.filter { it.calories > 0 }
    if (logged.isEmpty()) {
        lines += "Last $COACH_HISTORY_DAYS days: no meals logged."
    } else {
        lines += "Last $COACH_HISTORY_DAYS days (logged days only, oldest first):"
        for (day in logged) lines += "- ${day.date}: ${macros(day.calories, day.protein, day.carbs, day.fat)}"
        val averages = macroAverages(history)!!
        val split = averages.split?.let {
            " (protein ${num(it.protein)}%, carbs ${num(it.carbs)}%, fat ${num(it.fat)}% of calories)"
        } ?: ""
        lines += "Average over ${num(averages.loggedDays)} of ${num(averages.totalDays)} days logged: " +
            "${macros(averages.calories, averages.protein, averages.carbs, averages.fat)}$split."
    }

    // Weight
    val from = addDaysToKey(today, -COACH_WEIGHT_DAYS)
    val recentWeights = input.weights.filter { it.date >= from && it.date <= today }
    val latest = input.weights.lastOrNull()
    if (latest == null) {
        lines += "Weight: no weigh-ins logged."
    } else {
        lines += "Weight: latest ${num(round1(latest.weightKg))} kg on ${latest.date}."
        val bodyFat = input.weights.lastOrNull { it.bodyFatPct != null }
        if (bodyFat != null) lines += "Body fat: ${num(round1(bodyFat.bodyFatPct!!))}% on ${bodyFat.date}."
        if (recentWeights.size > 1) {
            val first = recentWeights.first()
            lines += "Weight in the last $COACH_WEIGHT_DAYS days: ${num(recentWeights.size)} weigh-ins, " +
                "the first ${num(round1(first.weightKg))} kg on ${first.date}."
        }
        val projection = weightProjection(input.weights.map { DataPoint(it.date, it.weightKg) }, today)
        lines += if (projection != null) {
            "Weight trend (last $EXPENDITURE_WINDOW_DAYS days): ${num(projection.ratePerWeek)} kg/week, now ${num(projection.currentKg)} kg. " +
                "Projection computed by the app if this trend continues: ${num(projection.projectedKg)} ± ${num(projection.marginKg)} kg " +
                "in ${num(projection.days)} days."
        } else {
            "Weight trend: not enough weigh-ins in the last $EXPENDITURE_WINDOW_DAYS days to project " +
                "(needs at least $MIN_WEIGH_INS days spanning $MIN_WEIGHT_SPAN_DAYS)."
        }
    }

    return lines.joinToString("\n")
}

/**
 * System prompt for the coach. `language` is the name of the app language in
 * English («Spanish»); `context` is [buildCoachContext] or null when the user
 * does not share their data.
 */
fun buildCoachSystemPrompt(language: String, context: String?): String {
    val rules = listOf(
        "You are the coach inside Blackwater Macros, a simple app to log meals (calories, protein, carbs, fat) and body weight.",
        "Always answer in $language. Be brief and practical: short paragraphs or short lists.",
        "For questions about the macros of foods, give an estimate per item (grams, kcal, protein, carbs, fat) and a total.",
        "Use the numbers the app computed (targets, measured expenditure, weight projection) instead of calculating your own, and say so when data is missing.",
        "Do not invent data the user did not log.",
        "You are not a doctor: for medical conditions, eating disorders, pregnancy or medication, recommend seeing a professional.",
    )
    val data = if (context == null) "The user chose not to share their data with the coach." else "USER DATA\n$context"
    return "${rules.joinToString("\n")}\n\n$data"
}
