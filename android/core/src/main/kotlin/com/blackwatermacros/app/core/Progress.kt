package com.blackwatermacros.app.core

/** Mirrors `src/lib/core/progress.ts`. */

data class MacroSplit(val protein: Double, val carbs: Double, val fat: Double)

data class MacroAverages(
    /** Days with food logged (calories > 0). Averages use only these. */
    val loggedDays: Int,
    val totalDays: Int,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    /** Share of the energy from each macro (4 / 4 / 9 kcal per g), in %; null without macros. */
    val split: MacroSplit?,
)

/**
 * Daily averages over the days that were logged; unlogged days are left out.
 * Null when nothing was logged.
 */
fun macroAverages(days: List<DailyNutritionPoint>): MacroAverages? {
    val logged = days.filter { it.calories > 0 }
    if (logged.isEmpty()) return null
    fun mean(pick: (DailyNutritionPoint) -> Double) = logged.sumOf(pick) / logged.size

    val protein = mean { it.protein }
    val carbs = mean { it.carbs }
    val fat = mean { it.fat }
    val energyProtein = protein * 4
    val energyCarbs = carbs * 4
    val energyFat = fat * 9
    val total = energyProtein + energyCarbs + energyFat

    return MacroAverages(
        loggedDays = logged.size,
        totalDays = days.size,
        calories = mathRound(mean { it.calories }),
        protein = round1(protein),
        carbs = round1(carbs),
        fat = round1(fat),
        split = if (total > 0) {
            MacroSplit(
                protein = mathRound(energyProtein / total * 100),
                carbs = mathRound(energyCarbs / total * 100),
                fat = mathRound(energyFat / total * 100),
            )
        } else {
            null
        },
    )
}
