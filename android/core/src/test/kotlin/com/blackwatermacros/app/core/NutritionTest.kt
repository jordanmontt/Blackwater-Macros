package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Kotlin JUnit mirror of `tests/unit/nutrition.test.ts`. Option A contract:
 * this file must stay in lockstep with the TS spec.
 */
class NutritionTest {

    // --- sumIngredientNutrition ---

    @Test
    fun sumIngredientNutrition_sumsMacrosPerIngredient() {
        val ingredients = listOf(
            IngredientInput(name = "Avena", calories = 150.0, protein = 5.0, carbs = 27.0, fat = 3.0),
            IngredientInput(name = "Leche", calories = 100.0, protein = 3.5, carbs = 5.0, fat = 7.0),
        )
        val totals = sumIngredientNutrition(ingredients)
        assertThat(totals.calories).isEqualTo(250.0)
        assertThat(totals.protein).isEqualTo(8.5)
        assertThat(totals.carbs).isEqualTo(32.0)
        assertThat(totals.fat).isEqualTo(10.0)
    }

    @Test
    fun sumIngredientNutrition_emptyListReturnsZeros() {
        val totals = sumIngredientNutrition(emptyList())
        assertThat(totals.calories).isEqualTo(0.0)
        assertThat(totals.protein).isEqualTo(0.0)
        assertThat(totals.carbs).isEqualTo(0.0)
        assertThat(totals.fat).isEqualTo(0.0)
    }

    @Test
    fun sumIngredientNutrition_ingredientsWithoutValuesContributeZero() {
        val ingredients = listOf(
            IngredientInput(name = "Solo nombre"),
            IngredientInput(name = "Solo kcal", calories = 300.0),
        )
        val totals = sumIngredientNutrition(ingredients)
        assertThat(totals.calories).isEqualTo(300.0)
        assertThat(totals.protein).isEqualTo(0.0)
        assertThat(totals.carbs).isEqualTo(0.0)
        assertThat(totals.fat).isEqualTo(0.0)
    }

    @Test
    fun sumIngredientNutrition_singleIngredientWithNullablePart() {
        val ingredients = listOf(
            IngredientInput(name = "Huevo", calories = 70.0, protein = 6.0, carbs = 0.6, fat = null),
        )
        assertThat(sumIngredientNutrition(ingredients).fat).isEqualTo(0.0)
    }

    // --- resolveMealTotals ---

    @Test
    fun resolveMealTotals_totalOnlyUsesManualTotals() {
        val totals = resolveMealTotals(EntryMode.TOTAL_ONLY, emptyList(), 700.0, 35.0, 90.0, 20.0)
        assertThat(totals.calories).isEqualTo(700.0)
        assertThat(totals.protein).isEqualTo(35.0)
        assertThat(totals.carbs).isEqualTo(90.0)
        assertThat(totals.fat).isEqualTo(20.0)
    }

    @Test
    fun resolveMealTotals_totalOnlyRoundsToTwoDecimals() {
        val totals = resolveMealTotals(EntryMode.TOTAL_ONLY, emptyList(), 700.006, 35.006, 0.0, 0.0)
        assertThat(totals.calories).isEqualTo(700.01)
        assertThat(totals.protein).isEqualTo(35.01)
        assertThat(totals.carbs).isEqualTo(0.0)
        assertThat(totals.fat).isEqualTo(0.0)
    }

    @Test
    fun resolveMealTotals_perIngredientSumsIngredients() {
        val ingredients = listOf(
            IngredientInput(name = "A", calories = 100.0, protein = 4.0, carbs = 20.0, fat = 2.0),
            IngredientInput(name = "B", calories = 200.0, protein = 8.0, carbs = 10.0, fat = 5.0),
        )
        val totals = resolveMealTotals(EntryMode.PER_INGREDIENT, ingredients)
        assertThat(totals.calories).isEqualTo(300.0)
        assertThat(totals.protein).isEqualTo(12.0)
        assertThat(totals.carbs).isEqualTo(30.0)
        assertThat(totals.fat).isEqualTo(7.0)
    }

    @Test
    fun resolveMealTotals_perIngredientIgnoresManualTotals() {
        val ingredients = listOf(
            IngredientInput(name = "A", calories = 100.0, protein = 4.0, carbs = 20.0, fat = 2.0),
        )
        val totals = resolveMealTotals(EntryMode.PER_INGREDIENT, ingredients, 999.0, 999.0, 999.0, 999.0)
        assertThat(totals.calories).isEqualTo(100.0)
        assertThat(totals.protein).isEqualTo(4.0)
        assertThat(totals.carbs).isEqualTo(20.0)
        assertThat(totals.fat).isEqualTo(2.0)
    }

    @Test
    fun resolveMealTotals_noIngredientsNoTotalsReturnsZeros() {
        val totals = resolveMealTotals(EntryMode.PER_INGREDIENT, emptyList())
        assertThat(totals.calories).isEqualTo(0.0)
        assertThat(totals.protein).isEqualTo(0.0)
        assertThat(totals.carbs).isEqualTo(0.0)
        assertThat(totals.fat).isEqualTo(0.0)
    }

    // --- round1 / round2 ---

    @Test
    fun round1_roundsToOneDecimal() {
        assertThat(round1(3.14159)).isEqualTo(3.1)
        assertThat(round1(1.25)).isEqualTo(1.3)
        assertThat(round1(0.05)).isEqualTo(0.1)
    }

    @Test
    fun round2_roundsToTwoDecimals() {
        assertThat(round2(3.14159)).isEqualTo(3.14)
        assertThat(round2(1.006)).isEqualTo(1.01)
        assertThat(round2(0.0)).isEqualTo(0.0)
    }
}