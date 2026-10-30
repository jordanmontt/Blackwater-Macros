package com.blackwatermacros.app.core

/**
 * Wire-format DTOs and enums shared by the client. Mirrors `src/lib/core/types.ts`.
 * Kotlin `data class` replaces TS interfaces; `enum class` replaces string unions.
 */
enum class EntryMode { PER_INGREDIENT, TOTAL_ONLY }

enum class Gender { MALE, FEMALE }

enum class Goal { CUT, MAINTAIN, SURPLUS }

enum class StatsRange { RANGE_7D, RANGE_30D, RANGE_90D, ALL }

data class IngredientInput(
    val name: String,
    val quantity: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

// --- Meals ---

data class MealDTO(
    val id: String,
    val logDate: String,
    val title: String,
    val notes: String?,
    val entryMode: EntryMode,
    val ingredients: List<IngredientInput>,
    val totalCalories: Double?,
    val totalProtein: Double?,
    val totalCarbs: Double?,
    val totalFat: Double?,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
    val updatedAt: String,
)

data class WeightDTO(
    val id: String,
    val measuredAt: String,
    val weightKg: Double,
    val bodyFatPct: Double?,
    val note: String?,
    val updatedAt: String,
)

// --- Statistics ---

data class DailyNutritionPoint(
    val date: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

data class CompositionStats(
    val currentWeightKg: Double?,
    val currentTrendKg: Double?,
    val changeSinceStartKg: Double?,
    val ratePerWeekKg: Double?,
    val minKg: Double?,
    val maxKg: Double?,
    val currentBodyFatPct: Double?,
    val changeBodyFatPct: Double?,
    val minBodyFatPct: Double?,
    val maxBodyFatPct: Double?,
)

data class WeightPoint(
    val date: String,
    val weight: Double,
    val trend: Double?,
)

data class BodyFatPoint(
    val date: String,
    val bodyFatPct: Double,
    val trend: Double?,
)

data class WeeklyAvg(
    val weekStart: String,
    val avg: Double,
)

data class StatsSummary(
    val calories: List<DailyNutritionPoint>,
    val protein: List<DailyNutritionPoint>,
    val carbs: List<DailyNutritionPoint>,
    val fat: List<DailyNutritionPoint>,
    val weights: List<WeightPoint>,
    val bodyFat: List<BodyFatPoint>,
    val caloriesAvg: Double?,
    val caloriesMaxDay: DailyNutritionPoint?,
    val proteinAvg: Double?,
    val proteinMaxDay: DailyNutritionPoint?,
    val carbsAvg: Double?,
    val carbsMaxDay: DailyNutritionPoint?,
    val fatAvg: Double?,
    val fatMaxDay: DailyNutritionPoint?,
    val weight: CompositionStats,
    val weeklyWeightAvg: List<WeeklyAvg>,
)

// --- Settings / users ---

data class AdminUserDTO(
    val id: String,
    val username: String,
    val isAdmin: Boolean,
    val createdAt: String,
)

data class CalorieProfile(
    val gender: Gender?,
    val birthYear: Int?,
    val heightCm: Double?,
    val gymDaysPerWeek: Int?,
    val gymSessionMinutes: Int?,
    val walkingMinutesPerDay: Int?,
    val calorieGoal: Goal?,
)

// --- Recommendation DTOs ---

data class CalorieRecommendation(
    val bmr: Double,
    /** PAL used for the TDEE (TDEE / BMR), rounded to 2 decimals for display. */
    val activityFactor: Double,
    /** The profile's expenditure: BMR x activity factor. */
    val tdee: Double,
    /** What the target is built on: [tdee] combined with the measured expenditure ([combineExpenditure]), or [tdee] without one. */
    val expenditure: Double,
    /** The measured expenditure's weight in [expenditure], 0–1 (0 without one). */
    val dataShare: Double,
    val target: Double,
    val targetMin: Double,
    val targetMax: Double,
    val goal: Goal,
)

data class ProteinRange(val min: Double, val max: Double)

/** What the g/kg factors multiply: total body weight or lean (fat-free) mass. */
/** What the g/kg factors multiply (web `ProteinBasis`). */
enum class ProteinBasis { BODY_WEIGHT, LEAN_MASS, REFERENCE_WEIGHT }

data class ProteinRecommendation(
    val goal: Goal,
    val basis: ProteinBasis,
    /** The kilograms the factors multiply (body weight, or lean mass when basis is LEAN_MASS). */
    val basisKg: Double,
    /** Grams per day. */
    val range: ProteinRange,
    /** g per kg of [basisKg]. */
    val perKg: ProteinRange,
    val target: Double,
)

data class ExpenditureEstimate(
    /** Measured total daily energy expenditure, kcal/day. */
    val tdee: Double,
    /** +- kcal/day, 95 % margin from the uncertainty of the weight trend. */
    val margin: Double,
    /** Mean intake over the logged days of the window, kcal/day. */
    val avgIntake: Double,
    /** Weight trend over the window (least-squares slope), kg/week. */
    val weightChangePerWeek: Double,
    val loggedDays: Int,
    val weighIns: Int,
    val windowDays: Int,
)

/**
 * What the measurement found in its 28-day window, also when it is not enough
 * (web `ExpenditureData`): the profile tells the user exactly what is missing.
 */
data class ExpenditureData(
    /** Days with meals logged (kcal > 0). */
    val loggedDays: Int,
    /** Days with at least one weigh-in. */
    val weighInDays: Int,
    /** Days between the first and the last weigh-in (0 with fewer than 2). */
    val weightSpanDays: Int,
    /** 95 % margin of the weight trend, kcal/day; null with fewer than 3 weigh-in days. */
    val margin: Double?,
    /** The measured expenditure; null until every rule is met. */
    val estimate: ExpenditureEstimate?,
)

/** Why the measured expenditure is not there yet, in the order the user should fix it. */
enum class MissingExpenditureData { MEALS, WEIGH_INS, WEIGHT_SPAN, MARGIN }

/** The measured expenditure is above, inside or below the formula's normal range. */
enum class ExpenditureVerdict { HIGHER, MATCHES, LOWER }

/** The formula's expenditure (profile) checked against the measured one (web `ExpenditureCheck`). */
sealed interface ExpenditureCheck {
    data class NotEnoughData(val data: ExpenditureData, val missing: List<MissingExpenditureData>) : ExpenditureCheck

    data class Measured(
        val verdict: ExpenditureVerdict,
        /** The same verdict one week earlier too (always true for MATCHES). */
        val confirmed: Boolean,
        val estimate: ExpenditureEstimate,
        /** From the profile: BMR × activity factor. */
        val formulaTdee: Double,
        val bmr: Double,
        val activityFactor: Double,
        /** formulaTdee ± 10 %: what the formula can be off by for anyone. */
        val normalMin: Double,
        val normalMax: Double,
        /** Measured − formula, kcal/day. */
        val gap: Double,
        /** The activity factor the measured expenditure corresponds to (measured / BMR). */
        val impliedActivityFactor: Double,
    ) : ExpenditureCheck
}

/** «Not now» on the Today card's gap notice: it comes back if the verdict changes or after a new 4-week window. */
data class ExpenditureNoticeDismissal(val verdict: ExpenditureVerdict, val date: String)
