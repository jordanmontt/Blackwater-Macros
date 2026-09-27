package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/coach.test.ts` (fixture: `tests/unit/coach.fixture.ts`). */
class CoachTest {

    private val today = "2026-03-01"

    private val profile = CalorieProfile(Gender.MALE, 1990, 178.0, 3, 60, 30, Goal.CUT)

    private val input = CoachInput(
        today = today,
        profile = profile,
        calorie = calculateCalorieRecommendation(profile, 80.0, 2026),
        protein = calculateProteinRecommendation(80.0, Goal.CUT, 20.0),
        expenditure = ExpenditureEstimate(2750.0, 177.0, 2300.0, -0.5, 28, 28, 28),
        meals = listOf(
            CoachMeal(
                today, "Desayuno",
                listOf(IngredientInput("Avena", "80 g", 300.0), IngredientInput("Leche", "200 ml"), IngredientInput(" ")),
                450.0, 30.0, 50.0, 12.0,
            ),
            CoachMeal(addDaysToKey(today, -1), "Comida", emptyList(), 2100.4, 150.0, 200.0, 70.25),
            CoachMeal(addDaysToKey(today, -3), "Cena", emptyList(), 1900.0, 120.0, 180.0, 60.0),
            CoachMeal(addDaysToKey(today, -40), "Antigua", emptyList(), 3000.0, 100.0, 300.0, 100.0),
        ),
        weights = (0 until 28).map { i ->
            CoachWeight(addDaysToKey(today, -(27 - i)), 80 - (0.5 / 7) * i, if (i == 0) 20.0 else null)
        },
    )

    private fun daily(kgPerWeek: Double) = (0 until 28).map { i -> DataPoint(addDaysToKey(today, -(27 - i)), 80 + (kgPerWeek / 7) * i) }

    // --- weightProjection ---

    @Test
    fun stableWeightStaysPut() {
        assertThat(weightProjection(daily(0.0), today)).isEqualTo(WeightProjection(30, 80.0, 80.0, 1.0, 0.0))
    }

    @Test
    fun continuesTheTrend() {
        assertThat(weightProjection(daily(-0.5), today)).isEqualTo(WeightProjection(30, 78.1, 75.9, 1.0, -0.5))
        assertThat(weightProjection(daily(-0.5), today, 7)!!.projectedKg).isEqualTo(77.6)
    }

    @Test
    fun needsFourWeighInDaysSpanningFourteenDaysWithinFourWeeks() {
        assertThat(weightProjection(daily(0.0).take(3), today)).isNull()
        assertThat(weightProjection(daily(0.0).takeLast(10), today)).isNull()
        val old = daily(0.0).map { it.copy(date = addDaysToKey(it.date, -30)) }
        assertThat(weightProjection(old, today)).isNull()
    }

    // --- buildCoachContext ---

    @Test
    fun summarisesProfileTargetsTodayHistoryAndWeight() {
        assertThat(buildCoachContext(input)).isEqualTo(
            listOf(
                "Today: 2026-03-01",
                "Profile: male, 36 years, 178 cm, goal: cut (lose fat).",
                "Activity: gym 3 days/week × 60 min, walking 30 min/day.",
                "Calorie target: 2089–2289 kcal/day (BMR 1738, estimated TDEE 2589, activity factor 1.49).",
                "Protein target: 147–198 g/day (2.3–3.1 g/kg of 64 kg lean mass).",
                "Measured expenditure (energy balance, last 28 days): 2750 ± 177 kcal/day.",
                "Today's meals:",
                "- Desayuno: 450 kcal, 30 g protein, 50 g carbs, 12 g fat. Items: Avena (80 g), Leche (200 ml).",
                "Today so far: 450 kcal, 30 g protein, 50 g carbs, 12 g fat.",
                "Calories: 1639–1839 kcal left to reach the target range.",
                "Protein: 117–168 g left to reach the target range.",
                "Last 14 days (logged days only, oldest first):",
                "- 2026-02-26: 1900 kcal, 120 g protein, 180 g carbs, 60 g fat",
                "- 2026-02-28: 2100 kcal, 150 g protein, 200 g carbs, 70.3 g fat",
                "Average over 2 of 14 days logged: 2000 kcal, 135 g protein, 190 g carbs, 65.1 g fat (protein 29%, carbs 40%, fat 31% of calories).",
                "Weight: latest 78.1 kg on 2026-03-01.",
                "Body fat: 20% on 2026-02-02.",
                "Weight in the last 60 days: 28 weigh-ins, the first 80 kg on 2026-02-02.",
                "Weight trend (last 28 days): -0.5 kg/week, now 78.1 kg. Projection computed by the app if this trend continues: 75.9 ± 1 kg in 30 days.",
            ).joinToString("\n"),
        )
    }

    @Test
    fun saysWhatIsMissingWithAnEmptyProfileAndNoData() {
        val empty = CalorieProfile(null, null, null, null, null, null, null)
        assertThat(buildCoachContext(CoachInput(today, empty, null, null, null, emptyList(), emptyList()))).isEqualTo(
            listOf(
                "Today: 2026-03-01",
                "Profile: sex unknown, age unknown, height unknown, goal unknown.",
                "Calorie target: not available (profile incomplete).",
                "Measured expenditure: not enough data yet (needs 4 weeks of logged meals and frequent weigh-ins).",
                "Today's meals: none logged yet.",
                "Today so far: 0 kcal, 0 g protein, 0 g carbs, 0 g fat.",
                "Last 14 days: no meals logged.",
                "Weight: no weigh-ins logged.",
            ).joinToString("\n"),
        )
    }

    @Test
    fun saysWhenTodayIsWithinOrOverTheTargetRange() {
        val within = input.copy(meals = listOf(input.meals[0].copy(resolvedCalories = 2150.0, resolvedProtein = 220.0)))
        val text = buildCoachContext(within)
        assertThat(text).contains("Calories: within the target range (up to 139 kcal more).")
        assertThat(text).contains("Protein: over the target range by 22 g.")
    }

    // --- buildCoachSystemPrompt ---

    @Test
    fun setsTheLanguageAndIncludesTheDataOrSaysItIsNotShared() {
        val withData = buildCoachSystemPrompt("Spanish", "Today: 2026-03-01")
        assertThat(withData).contains("Always answer in Spanish.")
        assertThat(withData).endsWith("USER DATA\nToday: 2026-03-01")
        assertThat(buildCoachSystemPrompt("English", null)).endsWith("The user chose not to share their data with the coach.")
    }
}
