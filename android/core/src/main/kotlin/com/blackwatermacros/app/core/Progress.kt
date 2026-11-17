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

// --- Fat as a share of calories ---

/**
 * The adult range for fat, as % of energy (US Institute of Medicine 2005, AMDR).
 * Mirrors `FAT_SHARE_MIN` / `FAT_SHARE_MAX` in `progress.ts`.
 */
const val FAT_SHARE_MIN = 20.0
const val FAT_SHARE_MAX = 35.0

/** Logged days before the average says anything: one heavy or light day must not. */
const val FAT_CHECK_MIN_DAYS = 7

enum class FatVerdict { LOW, HIGH }

data class FatShareCheck(
    val verdict: FatVerdict,
    /** Fat's share of the energy from macros, in % (the same number as `split.fat`). */
    val share: Double,
    /** Mean grams of fat per logged day, and their energy (x 9). */
    val fat: Double,
    val fatKcal: Double,
    /** Energy from protein, carbs and fat (4 / 4 / 9 kcal per g), the base of the share. */
    val macroKcal: Double,
)

/**
 * Fat outside 20–35 % of calories over the period's logged days, or null when it
 * is inside, there are no macros, or fewer than 7 days were logged. Uses the
 * rounded share the card shows. Information only: no target changes with it.
 */
fun checkFatShare(averages: MacroAverages): FatShareCheck? {
    val split = averages.split ?: return null
    if (averages.loggedDays < FAT_CHECK_MIN_DAYS) return null
    val share = split.fat
    if (share in FAT_SHARE_MIN..FAT_SHARE_MAX) return null
    return FatShareCheck(
        verdict = if (share < FAT_SHARE_MIN) FatVerdict.LOW else FatVerdict.HIGH,
        share = share,
        fat = averages.fat,
        fatKcal = mathRound(averages.fat * 9),
        macroKcal = mathRound(averages.protein * 4 + averages.carbs * 4 + averages.fat * 9),
    )
}
