package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/ai-schema.test.ts`. */
class AiSchemaTest {

    private val clean = """{"title":"Pasta boloñesa","items":[{"name":"Espaguetis cocidos","grams":180,"calories":284,"protein":10.4,"carbs":55.8,"fat":1.7},{"name":"Salsa boloñesa","grams":120,"calories":156,"protein":9.6,"carbs":6,"fat":10.2}],"confidence":"Medium","notes":"Aceite estimado"}"""

    private fun ok(text: String) = (parseMealEstimate(text) as MealEstimateResult.Ok).estimate

    @Test
    fun readsACleanAnswer() {
        assertThat(ok(clean)).isEqualTo(
            MealEstimate(
                title = "Pasta boloñesa",
                items = listOf(
                    EstimatedItem("Espaguetis cocidos", 180.0, 284.0, 10.4, 55.8, 1.7, false),
                    EstimatedItem("Salsa boloñesa", 120.0, 156.0, 9.6, 6.0, 10.2, false),
                ),
                confidence = EstimateConfidence.MEDIUM,
                notes = "Aceite estimado",
            ),
        )
    }

    @Test
    fun toleratesCodeFencesAndTextAround() {
        assertThat(ok("Aquí tienes:\n```json\n$clean\n```\nEspero que ayude.").items).hasSize(2)
    }

    @Test
    fun acceptsABareListOtherKeysAndNumbersAsText() {
        assertThat(parseMealEstimate("""[{"food":"Plátano","weight_g":"120 g","kcal":"107 kcal","proteins":1.3,"carbohydrates":27.4,"fats":0.4}]"""))
            .isEqualTo(
                MealEstimateResult.Ok(
                    MealEstimate("Plátano", listOf(EstimatedItem("Plátano", 120.0, 107.0, 1.3, 27.4, 0.4, false)), null, null),
                ),
            )
    }

    @Test
    fun clampsNegativesDropsNamelessItemsAndFlagsInconsistentKcal() {
        val estimate = ok("""{"items":[{"name":"Aceite","calories":-5,"fat":10},{"name":"Pollo","grams":0,"calories":500,"protein":20,"carbs":0,"fat":2},{"calories":100}]}""")
        assertThat(estimate.items).containsExactly(
            EstimatedItem("Aceite", null, 0.0, 0.0, 0.0, 10.0, true),
            EstimatedItem("Pollo", null, 500.0, 20.0, 0.0, 2.0, true),
        ).inOrder()
        assertThat(estimate.title).isEqualTo("Aceite, Pollo")
    }

    @Test
    fun reportsAnswersWithoutJsonOrWithoutItems() {
        assertThat(parseMealEstimate("No puedo ver bien la imagen.")).isEqualTo(MealEstimateResult.Error(MealEstimateError.NO_JSON))
        assertThat(parseMealEstimate("broken {json")).isEqualTo(MealEstimateResult.Error(MealEstimateError.NO_JSON))
        assertThat(parseMealEstimate("""{"items":[]}""")).isEqualTo(MealEstimateResult.Error(MealEstimateError.NO_ITEMS))
        assertThat(parseMealEstimate("""{"title":"x"}""")).isEqualTo(MealEstimateResult.Error(MealEstimateError.NO_ITEMS))
    }

    @Test
    fun extractJsonTakesWhicheverValueOpensFirst() {
        assertThat(extractJson("""Result: [{"name":"a"}] {""")).isEqualTo(JsonArray(listOf(JsonObject(mapOf("name" to JsonPrimitive("a"))))))
        assertThat(extractJson("nothing here")).isNull()
    }

    @Test
    fun turnsItemsIntoIngredientRows() {
        val estimate = ok("""{"items":[{"name":"Arroz","grams":150,"calories":195,"protein":4,"carbs":42,"fat":0.4},{"name":"Salsa","calories":40}]}""")
        assertThat(estimateToIngredients(estimate)).containsExactly(
            IngredientInput("Arroz", "150 g", 195.0, 4.0, 42.0, 0.4),
            IngredientInput("Salsa", null, 40.0, 0.0, 0.0, 0.0),
        ).inOrder()
    }

    @Test
    fun mealEstimatePromptAsksForTheShapeInTheAppLanguage() {
        val system = buildMealEstimateSystemPrompt("Spanish")
        assertThat(system).contains(MEAL_ESTIMATE_SHAPE)
        assertThat(system).contains("Write the title, the item names and the notes in Spanish.")
        assertThat(system.split("\n")).hasSize(9)
    }

    @Test
    fun mealEstimateUserTextDescribesPhotosAndAddsTheUsersWords() {
        assertThat(buildMealEstimateUserText("", 1)).isEqualTo("A photo of my meal.")
        assertThat(buildMealEstimateUserText("  con una cucharada de aceite ", 3)).isEqualTo(
            "3 photos of the same meal from different angles (one may be a nutrition label).\nWhat I can add: con una cucharada de aceite",
        )
        assertThat(buildMealEstimateUserText("3 plátanos", 0)).isEqualTo("Estimate this meal: 3 plátanos")
    }
}
