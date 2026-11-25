package com.blackwatermacros.app.core.foodquestions

import com.blackwatermacros.app.core.CoachWeight
import com.blackwatermacros.app.core.EntryMode
import com.blackwatermacros.app.core.IngredientInput
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.daysBetweenKeys
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A meal as food questions read it (web `FoodLogMeal`). */
data class FoodLogMeal(
    val logDate: String,
    val title: String,
    val entryMode: EntryMode,
    val ingredients: List<IngredientInput>,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
)

/** Everything an answer may read: the meals up to today and the weigh-ins, oldest first. */
data class FoodLog(val today: String, val meals: List<FoodLogMeal>, val weights: List<CoachWeight>)

enum class AmountUnit(val id: String) { G("g"), ML("ml") }

data class Amount(val value: Double, val unit: AmountUnit)

/** One food as logged: an ingredient, or a «Solo total» meal by its title (web `FoodItem`). */
data class FoodItem(
    val date: String,
    val name: String,
    val key: String,
    val amount: Amount?,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

data class DayTotals(val calories: Double, val protein: Double, val carbs: Double, val fat: Double)

data class DayRange(val from: String, val to: String)

private val MARKS = Regex("\\p{M}+")
private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

/** Lower case, without accents, words split on anything that is not a letter or digit. */
fun normalizeName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(NOT_WORD, " ").trim()

/** Unit as written → the unit it is counted in and the factor to it. */
private val UNIT_FACTORS: Map<String, Pair<AmountUnit, Double>> = mapOf(
    "g" to (AmountUnit.G to 1.0),
    "gr" to (AmountUnit.G to 1.0),
    "grs" to (AmountUnit.G to 1.0),
    "gramo" to (AmountUnit.G to 1.0),
    "gramos" to (AmountUnit.G to 1.0),
    "gram" to (AmountUnit.G to 1.0),
    "grams" to (AmountUnit.G to 1.0),
    "kg" to (AmountUnit.G to 1000.0),
    "ml" to (AmountUnit.ML to 1.0),
    "cl" to (AmountUnit.ML to 10.0),
    "dl" to (AmountUnit.ML to 100.0),
    "l" to (AmountUnit.ML to 1000.0),
    "lt" to (AmountUnit.ML to 1000.0),
    "litro" to (AmountUnit.ML to 1000.0),
    "litros" to (AmountUnit.ML to 1000.0),
    "liter" to (AmountUnit.ML to 1000.0),
    "liters" to (AmountUnit.ML to 1000.0),
    "litre" to (AmountUnit.ML to 1000.0),
    "litres" to (AmountUnit.ML to 1000.0),
)

private val AMOUNT = Regex(
    "([0-9]+(?:[.,][0-9]+)?)[ \\t\\u00a0]*(kg|grams|gram|gramos|gramo|grs|gr|g|ml|cl|dl|litres|litre|liters|liter|litros|litro|lt|l)(?!\\p{L})",
    RegexOption.IGNORE_CASE,
)
private val NUMBER = Regex("[0-9]+(?:[.,][0-9]+)?")

/**
 * The amount in g or ml of a free-text quantity («200 ml», «1,5 kg», «150g»).
 * Only when it is the one number there: «1 taza (250 ml)» or «2 x 30 g» are
 * left out rather than guessed.
 */
fun parseAmount(quantity: String?): Amount? {
    if (quantity.isNullOrEmpty()) return null
    if (NUMBER.findAll(quantity).count() != 1) return null
    val match = AMOUNT.find(quantity) ?: return null
    val (unit, factor) = UNIT_FACTORS.getValue(match.groupValues[2].lowercase())
    val value = match.groupValues[1].replace(",", ".").toDouble() * factor
    return if (value > 0) Amount(value, unit) else null
}

/** The foods of the meals, in their order. */
fun foodItems(meals: List<FoodLogMeal>): List<FoodItem> {
    val items = mutableListOf<FoodItem>()
    for (meal in meals) {
        if (meal.entryMode == EntryMode.TOTAL_ONLY) {
            val name = meal.title.trim()
            if (name.isEmpty()) continue
            items += FoodItem(meal.logDate, name, normalizeName(name), null, meal.resolvedCalories, meal.resolvedProtein, meal.resolvedCarbs, meal.resolvedFat)
            continue
        }
        for (ingredient in meal.ingredients) {
            val name = ingredient.name.trim()
            if (name.isEmpty()) continue
            items += FoodItem(
                date = meal.logDate,
                name = name,
                key = normalizeName(name),
                amount = parseAmount(ingredient.quantity),
                calories = ingredient.calories ?: 0.0,
                protein = ingredient.protein ?: 0.0,
                carbs = ingredient.carbs ?: 0.0,
                fat = ingredient.fat ?: 0.0,
            )
        }
    }
    return items
}

/** Each day's totals, from the meals' resolved totals. */
fun dayTotals(meals: List<FoodLogMeal>): Map<String, DayTotals> {
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

/** The search words, as lists of normalized words (empty ones dropped, at most `maxSearchWords`). */
fun searchTerms(foods: List<String>): List<List<String>> =
    foods.map { food -> normalizeName(food).split(" ").filter { it.isNotEmpty() } }
        .filter { it.isNotEmpty() }
        .take(FoodQuestionsSpec.rules.maxSearchWords)

/** A name matches a search term when every word of the term starts a word of the name. */
fun matchesTerms(key: String, terms: List<List<String>>): Boolean {
    val words = key.split(" ")
    return terms.any { term -> term.all { part -> words.any { it.startsWith(part) } } }
}

/** The days from `from` to `to`, both included. */
fun daysOf(range: DayRange): List<String> {
    val days = mutableListOf<String>()
    var day = range.from
    while (day <= range.to) {
        days += day
        day = addDaysToKey(day, 1)
    }
    return days
}

fun inRange(date: String, range: DayRange): Boolean = date >= range.from && date <= range.to

fun rangeDays(range: DayRange): Int = daysBetweenKeys(range.from, range.to) + 1

/** «Monday»: the model reads English. */
fun weekdayName(date: String): String = LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

fun isWeekend(date: String): Boolean = LocalDate.parse(date).dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }

/**
 * The period a request asks for: `to` never after today; without `from`, the
 * `defaultDays` up to `to`. Null when it starts after it ends.
 */
fun resolveRange(from: String?, to: String?, today: String, defaultDays: Int): DayRange? {
    val end = if (to == null || to > today) today else to
    val start = from ?: addDaysToKey(end, -(defaultDays - 1))
    return if (start <= end) DayRange(start, end) else null
}

/** Each name as it was written most often, by normalized name. */
fun displayNames(items: List<FoodItem>): Map<String, String> {
    val counts = linkedMapOf<String, LinkedHashMap<String, Int>>()
    for (item in items) {
        val spellings = counts.getOrPut(item.key) { linkedMapOf() }
        spellings[item.name] = (spellings[item.name] ?: 0) + 1
    }
    return counts.mapValues { (_, spellings) ->
        spellings.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first().key
    }
}

/** The names of [items], the most frequent (by days) first. */
fun namesByDays(items: List<FoodItem>): List<String> {
    val days = linkedMapOf<String, MutableSet<String>>()
    for (item in items) days.getOrPut(item.key) { mutableSetOf() } += item.date
    val names = displayNames(items)
    return days.map { (key, dates) -> names.getValue(key) to dates.size }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .map { it.first }
}

/** The food logged on most days in the 28 days up to today, for example questions; null with none. */
fun mostLoggedFood(meals: List<FoodLogMeal>, today: String): String? {
    val range = DayRange(addDaysToKey(today, -(FoodQuestionsSpec.rules.defaultDays - 1)), today)
    return namesByDays(foodItems(meals.filter { inRange(it.logDate, range) })).firstOrNull()
}
