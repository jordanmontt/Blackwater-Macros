package com.blackwatermacros.app.ui

import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.DayTargets
import com.blackwatermacros.app.core.ExpenditureCheck
import com.blackwatermacros.app.core.ExpenditureVerdict
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProfilePeriod
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
    fun `adding food asks before closing only when something would be lost`() {
        fun unsaved(
            view: AddFoodView,
            photoText: String = "",
            photoCount: Int = 0,
            estimating: Boolean = false,
            query: String = "",
            barcodeCode: String = "",
            copySelected: Int = 0,
        ) = addFoodHasUnsavedInput(view, photoText, photoCount, estimating, query, barcodeCode, copySelected)

        assertThat(AddFoodView.entries.map { unsaved(it) })
            .containsExactly(false, false, false, false, true, false).inOrder() // only a chosen portion
        // The AI description and photos count everywhere: they survive the sheet's Back arrow.
        assertThat(unsaved(AddFoodView.PHOTO, photoText = "Arroz con pollo, un plato grande")).isTrue()
        assertThat(unsaved(AddFoodView.MENU, photoText = "Arroz con pollo")).isTrue()
        assertThat(unsaved(AddFoodView.PHOTO, photoText = "   ")).isFalse()
        assertThat(unsaved(AddFoodView.PHOTO, photoCount = 1)).isTrue()
        assertThat(unsaved(AddFoodView.PHOTO, estimating = true)).isTrue()
        // The rest only on their own view.
        assertThat(unsaved(AddFoodView.SEARCH, query = "plátano")).isTrue()
        assertThat(unsaved(AddFoodView.MENU, query = "plátano")).isFalse()
        assertThat(unsaved(AddFoodView.BARCODE, barcodeCode = "8410")).isTrue()
        assertThat(unsaved(AddFoodView.COPY, copySelected = 2)).isTrue()
        assertThat(unsaved(AddFoodView.COPY)).isFalse()
    }

    @Test
    fun `numbers are edited with a decimal comma and parse back`() {
        assertThat(toDecimalInput(8.5)).isEqualTo("8,5")
        assertThat(toDecimalInput(12345.5)).isEqualTo("12345,5")
        assertThat(toDecimalInput(2.0)).isEqualTo("2")
        assertThat(parseDecimal(toDecimalInput(1.6))).isEqualTo(1.6)
        assertThat(formatNumber(1.6, 1)).isEqualTo("1,6")
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
    fun `recommendations check the profile against the data and keep the formula's inputs for the why`() {
        val today = "2026-03-01"
        val profile = CalorieProfile(Gender.MALE, 1990, 178.0, 3, 60, 30, Goal.MAINTAIN)
        // Stable 80 kg weighed daily for 5 weeks on 1800 kcal/day: 1800 ± 177, far below this profile.
        val weights = (35 downTo 1).map { n -> WeightDTO("w$n", "${addDaysToKey(today, -n)}T12:00:00Z", 80.0, null, null, "") }
        val meals = (1..35).map { n ->
            MealDTO(
                "m$n", addDaysToKey(today, -n), "Comida", null, WireEntryMode.TOTAL_ONLY, emptyList(),
                1800.0, 150.0, null, null, 1800.0, 150.0, 0.0, 0.0, "",
            )
        }

        val ready = recommend(weights, profile, meals, today) as RecommendationsUiState.Ready
        val check = ready.check as ExpenditureCheck.Measured
        assertThat(check.verdict).isEqualTo(ExpenditureVerdict.LOWER)
        assertThat(check.confirmed).isTrue()
        assertThat(check.formulaTdee).isEqualTo(ready.calorie!!.tdee)
        assertThat(ready.inputs).isEqualTo(FormulaInputs(Gender.MALE, 80.0, 178.0, 2026 - 1990, 3, 60, 30))

        // Without a calorie target (no sex yet) there is nothing to check.
        val noSex = recommend(weights, profile.copy(gender = null), meals, today) as RecommendationsUiState.Ready
        assertThat(noSex.check).isNull()
        assertThat(noSex.inputs).isNull()
    }

    @Test
    fun `a past day keeps the targets it had, today uses the current profile`() {
        val today = "2026-10-15"
        val before = CalorieProfile(Gender.FEMALE, 1995, 165.0, 2, 60, 20, Goal.CUT)
        val after = before.copy(gymDaysPerWeek = 4, walkingMinutesPerDay = 60)
        val weights = listOf(
            WeightDTO("w1", "2026-10-10T12:00:00Z", 61.0, null, null, ""),
            WeightDTO("w2", "2026-10-15T12:00:00Z", 60.5, null, null, ""),
        )
        val history = listOf(ProfilePeriod("2026-10-14", before))

        val ready = recommend(weights, after, today = today, history = history) as RecommendationsUiState.Ready
        // Same numbers as core ProfileHistoryTest: +111 kcal from the new activity.
        assertThat(ready.calorie!!.targetMin).isEqualTo(1546.0)
        assertThat(ready.targets!!.on("2026-10-12").calorie!!.targetMin).isEqualTo(1435.0)
        assertThat(ready.targets!!.on(today)).isEqualTo(DayTargets(ready.calorie, ready.protein))
    }

    @Test
    fun `copying a meal or template keeps its content and only sends totals for total-only`() {
        val perIngredient = MealFormValue(
            "Tortilla", "con cebolla", WireEntryMode.PER_INGREDIENT,
            listOf(com.blackwatermacros.app.data.WireIngredient("Huevos", "3", 210.0, 18.0, 1.0, 15.0)),
            999.0, 999.0, 999.0, 999.0,
        )
        val copy = perIngredient.toCopyRequest("2026-06-16")
        assertThat(copy.logDate).isEqualTo("2026-06-16")
        assertThat(copy.title).isEqualTo("Tortilla")
        assertThat(copy.ingredients).isEqualTo(perIngredient.ingredients)
        assertThat(copy.totalCalories).isNull()

        val totalOnly = perIngredient.copy(entryMode = WireEntryMode.TOTAL_ONLY, ingredients = emptyList())
        assertThat(totalOnly.toCopyRequest("2026-06-16").totalCalories).isEqualTo(999.0)
    }

    @Test
    fun `the header cloud reflects the sync state and hides without an account`() {
        val account = com.blackwatermacros.app.data.Account("ana", "t", isAdmin = false)
        assertThat(syncIndicatorState(null, 3, false, null)).isEqualTo(SyncIndicatorState.Hidden)
        assertThat(syncIndicatorState(account, 0, false, null)).isEqualTo(SyncIndicatorState.Synced)
        assertThat(syncIndicatorState(account, 2, false, null)).isEqualTo(SyncIndicatorState.Pending(2))
        assertThat(syncIndicatorState(account, 2, true, null)).isEqualTo(SyncIndicatorState.Syncing)
        assertThat(syncIndicatorState(account.copy(sessionExpired = true), 0, false, null)).isEqualTo(SyncIndicatorState.Problem)
        assertThat(syncIndicatorState(account.copy(sessionExpired = true), 2, true, null)).isEqualTo(SyncIndicatorState.Problem)
    }

    @Test
    fun `the header cloud is red only when a change won't reach the server on its own`() {
        val account = com.blackwatermacros.app.data.Account("ana", "t", isAdmin = false)
        for (problem in com.blackwatermacros.app.data.sync.SyncProblem.entries) {
            // Everything uploaded; only the run as a whole failed (e.g. the download): data is safe.
            assertThat(syncIndicatorState(account, 0, false, problem)).isEqualTo(SyncIndicatorState.Synced)
        }
        // The server refuses a change: retrying the same data won't help, it needs a look.
        assertThat(syncIndicatorState(account, 2, false, com.blackwatermacros.app.data.sync.SyncProblem.CHANGE_REJECTED))
            .isEqualTo(SyncIndicatorState.Problem)
        // A brief server error or a Wi-Fi login page: retried on its own, the cloud just counts.
        assertThat(syncIndicatorState(account, 2, false, com.blackwatermacros.app.data.sync.SyncProblem.SERVER_ERROR))
            .isEqualTo(SyncIndicatorState.Pending(2))
        assertThat(syncIndicatorState(account, 2, false, com.blackwatermacros.app.data.sync.SyncProblem.UNEXPECTED))
            .isEqualTo(SyncIndicatorState.Pending(2))
    }
}
