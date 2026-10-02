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
    val tdee: Double,
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