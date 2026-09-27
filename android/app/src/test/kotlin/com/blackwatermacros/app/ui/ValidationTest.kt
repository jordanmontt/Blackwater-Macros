package com.blackwatermacros.app.ui

import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProteinBasis
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.WireEntryMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The phone validates with the server's limits: anything saved offline must be
 * accepted when it syncs, otherwise it would stay pending forever.
 */
class ValidationTest {

    private fun form(
        title: String = "Desayuno",
        mode: WireEntryMode = WireEntryMode.PER_INGREDIENT,
        ingredients: List<IngredientDraft> = listOf(IngredientDraft(name = "Avena", calories = "300", protein = "10,5")),
        totals: List<String> = List(4) { "" },
    ) = buildFormValue(title, notes = "", mode = mode, ingredients = ingredients, totals = totals)

    @Test
    fun `a meal needs a title`() {
        assertThat(form(title = "  ")).isEqualTo(FormResult.Invalid(R.string.error_title_required))
    }

    @Test
    fun `a per-ingredient meal needs at least one named ingredient`() {
        assertThat(form(ingredients = listOf(IngredientDraft(calories = "100"))))
            .isEqualTo(FormResult.Invalid(R.string.error_ingredient_required))
    }

    @Test
    fun `negative or non-numeric macros are rejected, blanks are allowed`() {
        assertThat(form(ingredients = listOf(IngredientDraft(name = "Avena", calories = "-5"))))
            .isEqualTo(FormResult.Invalid(R.string.error_invalid_number))
        assertThat(form(mode = WireEntryMode.TOTAL_ONLY, totals = listOf("abc", "", "", "")))
            .isEqualTo(FormResult.Invalid(R.string.error_invalid_number))
        assertThat(form(ingredients = listOf(IngredientDraft(name = "Agua")))).isInstanceOf(FormResult.Valid::class.java)
    }

    @Test
    fun `both decimal separators are accepted and unnamed rows are dropped`() {
        val value = (form(ingredients = listOf(IngredientDraft(name = " Avena ", protein = "10,5"), IngredientDraft())) as FormResult.Valid).value
        assertThat(value.ingredients.single().name).isEqualTo("Avena")
        assertThat(value.ingredients.single().protein).isEqualTo(10.5)
        assertThat(value.totalCalories).isNull()
    }

    @Test
    fun `total-only meals store blank totals as zero and no ingredients`() {
        val value = (form(mode = WireEntryMode.TOTAL_ONLY, totals = listOf("700", "35.5", "", "")) as FormResult.Valid).value
        assertThat(value.ingredients).isEmpty()
        assertThat(listOf(value.totalCalories, value.totalProtein, value.totalCarbs, value.totalFat))
            .containsExactly(700.0, 35.5, 0.0, 0.0).inOrder()
    }

    @Test
    fun `weight and body fat use the server limits`() {
        assertThat(validateWeight("80,4", "")).isEqualTo(WeightValidation.Valid(80.4, null))
        assertThat(validateWeight("19", "")).isEqualTo(WeightValidation.Invalid(R.string.error_weight_range))
        assertThat(validateWeight("", "")).isEqualTo(WeightValidation.Invalid(R.string.error_weight_range))
        assertThat(validateWeight("80", "2")).isEqualTo(WeightValidation.Invalid(R.string.error_body_fat_range))
        assertThat(validateWeight("80", "18.5")).isEqualTo(WeightValidation.Valid(80.0, 18.5))
    }

    @Test
    fun `an out-of-range profile is not saved`() {
        val ok = CalorieProfile(Gender.MALE, 1990, 178.0, 3, 60, 30, Goal.CUT)
        assertThat(isValidProfile(ok)).isTrue()
        assertThat(isValidProfile(ok.copy(birthYear = 19))).isFalse()
        assertThat(isValidProfile(ok.copy(gymDaysPerWeek = 8))).isFalse()
        assertThat(isValidProfile(CalorieProfile(null, null, null, null, null, null, null))).isTrue()
    }

    @Test
    fun `recommendations explain what is missing`() {
        val weight = WeightDTO("w", "2026-06-15T06:30:00.000Z", 80.0, null, null, "")
        val empty = CalorieProfile(null, null, null, null, null, null, null)

        assertThat(recommend(emptyList(), empty)).isEqualTo(RecommendationsUiState.NoWeight)
        assertThat(recommend(listOf(weight), empty)).isEqualTo(RecommendationsUiState.NeedsProfile)
        val ready = recommend(listOf(weight), empty.copy(calorieGoal = Goal.CUT)) as RecommendationsUiState.Ready
        assertThat(ready.protein).isNotNull()
        assertThat(ready.calorie).isNull() // needs sex too
    }

    @Test
    fun `recommendations use the latest body fat and measure expenditure from 4 weeks of data`() {
        val today = "2026-03-01"
        val profile = CalorieProfile(Gender.MALE, 1990, 178.0, 3, 60, 30, Goal.CUT)
        // Stable 80 kg every other day; body fat only logged on the first weigh-in.
        val weights = (28 downTo 1 step 2).map { n ->
            WeightDTO("w$n", "${addDaysToKey(today, -n)}T12:00:00Z", 80.0, if (n == 28) 20.0 else null, null, "")
        }
        val meals = (1..28).map { n ->
            MealDTO(
                "m$n", addDaysToKey(today, -n), "Comida", null, WireEntryMode.TOTAL_ONLY, emptyList(),
                2500.0, 150.0, null, null, 2500.0, 150.0, 0.0, 0.0, "",
            )
        }

        val ready = recommend(weights, profile, meals, today) as RecommendationsUiState.Ready
        assertThat(ready.protein!!.basis).isEqualTo(ProteinBasis.LEAN_MASS)
        assertThat(ready.protein!!.basisKg).isEqualTo(64.0)
        assertThat(ready.expenditure!!.tdee).isEqualTo(2500.0)
        assertThat(ready.expenditure!!.margin).isEqualTo(250.0)

        // Weekly weigh-ins are too uncertain: no measured value.
        val weekly = weights.filterIndexed { i, _ -> i % 4 == 0 }
        assertThat((recommend(weekly, profile, meals, today) as RecommendationsUiState.Ready).expenditure).isNull()
    }

    @Test
    fun `the header cloud reflects the sync state and hides without an account`() {
        val account = com.blackwatermacros.app.data.Account("ana", "t", isAdmin = false)
        assertThat(syncIndicatorState(null, 3, false, null)).isEqualTo(SyncIndicatorState.Hidden)
        assertThat(syncIndicatorState(account, 0, false, null)).isEqualTo(SyncIndicatorState.Synced)
        assertThat(syncIndicatorState(account, 2, false, null)).isEqualTo(SyncIndicatorState.Pending(2))
        assertThat(syncIndicatorState(account, 2, true, null)).isEqualTo(SyncIndicatorState.Syncing)
        assertThat(syncIndicatorState(account.copy(sessionExpired = true), 0, false, null)).isEqualTo(SyncIndicatorState.Problem)
        assertThat(
            syncIndicatorState(account, 0, false, com.blackwatermacros.app.data.sync.SyncProblem.SERVER_ERROR),
        ).isEqualTo(SyncIndicatorState.Problem)
    }
}
