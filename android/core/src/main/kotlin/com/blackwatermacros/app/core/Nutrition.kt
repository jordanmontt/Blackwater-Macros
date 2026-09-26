package com.blackwatermacros.app.core

/**
 * Mirrors `src/lib/core/nutrition.ts`. Rounding uses `java.lang.Math.round`
 * (round-half-up toward +∞, ties toward +∞) to match JS `Math.round`.
 */

data class NutritionTotals(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

/** JS-compatible round-half-up (toward +∞). Returns a Double. */
fun mathRound(value: Double): Double = java.lang.Math.round(value).toDouble()

/** Sums the nutrition values entered for each ingredient; missing values count as zero. */
fun sumIngredientNutrition(ingredients: List<IngredientInput>): NutritionTotals {
    var calories = 0.0
    var protein = 0.0
    var carbs = 0.0
    var fat = 0.0
    for (ingredient in ingredients) {
        calories += ingredient.calories ?: 0.0
        protein += ingredient.protein ?: 0.0
        carbs += ingredient.carbs ?: 0.0
        fat += ingredient.fat ?: 0.0
    }
    return NutritionTotals(calories, protein, carbs, fat)
}

/**
 * Resolves the nutrition totals of a meal according to its entry mode:
 * - TOTAL_ONLY: uses the manually entered meal-level values.
 * - PER_INGREDIENT: sums whatever was entered per ingredient.
 */
fun resolveMealTotals(
    entryMode: EntryMode,
    ingredients: List<IngredientInput>,
    manualTotalCalories: Double? = null,
    manualTotalProtein: Double? = null,
    manualTotalCarbs: Double? = null,
    manualTotalFat: Double? = null,
): NutritionTotals {
    if (entryMode == EntryMode.TOTAL_ONLY) {
        return NutritionTotals(
            calories = round2(manualTotalCalories ?: 0.0),
            protein = round2(manualTotalProtein ?: 0.0),
            carbs = round2(manualTotalCarbs ?: 0.0),
            fat = round2(manualTotalFat ?: 0.0),
        )
    }
    val summed = sumIngredientNutrition(ingredients)
    return NutritionTotals(
        calories = round2(summed.calories),
        protein = round2(summed.protein),
        carbs = round2(summed.carbs),
        fat = round2(summed.fat),
    )
}

fun round1(value: Double): Double = mathRound(value * 10) / 10

fun round2(value: Double): Double = mathRound(value * 100) / 100