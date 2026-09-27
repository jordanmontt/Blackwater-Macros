package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/foods.test.ts`. */
class FoodsTest {

    private val offProduct = """{"code":"3033490004743","status":1,"product":{"code":"3033490004743","product_name":"Yaourt à la grecque","brands":"Danone, Oikos","serving_size":"125 g","serving_quantity":"125","nutriments":{"energy-kcal_100g":97,"proteins_100g":"3.2","carbohydrates_100g":4.5,"fat_100g":7.4}}}"""
    private val offKjOnly = """{"status":1,"product":{"code":"8480000123456","product_name":"Galletas","serving_size":"2 galletas (25g)","nutriments":{"energy_100g":1000,"carbohydrates_100g":70,"fat_100g":8}}}"""
    private val offSearch = """{"hits":[{"code":"8480000592170","product_name":"Yogur griego natural","brands":["Hacendado"],"nutriments":{"energy-kcal_100g":122,"proteins_100g":3.5,"carbohydrates_100g":4.2,"fat_100g":10}},{"code":"1","product_name":"Sin energía","nutriments":{}}]}"""

    private val foods = listOf(
        GenericFood("usda:1", "usda", linkedMapOf(FoodLang.EN to "Yogurt, Greek, plain, nonfat", FoodLang.ES to "Yogur griego natural desnatado"), Per100g(59.0, 10.2, 3.6, 0.4)),
        GenericFood("ch:2", "ch", linkedMapOf(FoodLang.DE to "Griechischer Joghurt", FoodLang.FR to "Yogourt grec", FoodLang.IT to "Yogurt greco", FoodLang.EN to "Greek yogurt"), Per100g(115.0, 4.0, 4.0, 9.0)),
        GenericFood("usda:3", "usda", linkedMapOf(FoodLang.EN to "Bananas, raw", FoodLang.ES to "Plátano crudo"), Per100g(89.0, 1.1, 22.8, 0.3)),
        GenericFood("ciqual:4", "ciqual", linkedMapOf(FoodLang.FR to "Banane, pulpe, crue", FoodLang.EN to "Banana, pulp, raw"), Per100g(90.0, 1.1, 20.5, 0.3)),
    )

    private fun json(text: String) = Json.parseToJsonElement(text)

    // --- scalePer100g / foodToIngredient ---

    @Test
    fun scalesPer100gValuesToAPortion() {
        assertThat(scalePer100g(Per100g(59.0, 10.0, 3.6, 0.4), 150.0)).isEqualTo(NutritionTotals(89.0, 15.0, 5.4, 0.6))
    }

    @Test
    fun buildsAnIngredientRowWithTheQuantityInGrams() {
        assertThat(foodToIngredient("Plátano", Per100g(89.0, 1.1, 22.8, 0.3), 120.0))
            .isEqualTo(IngredientInput("Plátano", "120 g", 107.0, 1.3, 27.4, 0.4))
        assertThat(foodToIngredient("Aceite", Per100g(884.0, 0.0, 0.0, 100.0), 12.5).quantity).isEqualTo("12.5 g")
    }

    // --- parseServingGrams ---

    @Test
    fun readsGramsOrMillilitresFromAServingDescription() {
        assertThat(parseServingGrams("30 g")).isEqualTo(30.0)
        assertThat(parseServingGrams("1 cup (240 ml)")).isEqualTo(240.0)
        assertThat(parseServingGrams("2 galletas (25g)")).isEqualTo(25.0)
        assertThat(parseServingGrams("1,5 gramos")).isEqualTo(1.5)
    }

    @Test
    fun servingIsNullWhenThereIsNoAmount() {
        assertThat(parseServingGrams("1 unidad")).isNull()
        assertThat(parseServingGrams("0 g")).isNull()
        assertThat(parseServingGrams(null)).isNull()
    }

    // --- normalizeText ---

    @Test
    fun normalizeIgnoresCaseAccentsAndExtraSpaces() {
        assertThat(normalizeText("  Yogúr  Griego ")).isEqualTo("yogur griego")
        assertThat(normalizeText("Crème Brûlée")).isEqualTo("creme brulee")
        assertThat(normalizeText("Piña")).isEqualTo("pina")
    }

    // --- Open Food Facts ---

    @Test
    fun readsAProduct() {
        assertThat(parseOffProduct(json(offProduct))).isEqualTo(
            FoodProduct("3033490004743", "Yaourt à la grecque", "Danone", Per100g(97.0, 3.2, 4.5, 7.4), 125.0, false),
        )
    }

    @Test
    fun fallsBackToKilojoulesAndFlagsMissingMacros() {
        val product = parseOffProduct(json(offKjOnly))!!
        assertThat(product.per100g).isEqualTo(Per100g(239.0, 0.0, 70.0, 8.0))
        assertThat(product.incomplete).isTrue()
        assertThat(product.servingGrams).isEqualTo(25.0)
        assertThat(product.brand).isNull()
    }

    @Test
    fun unknownProductsOrWithoutEnergyAreNull() {
        assertThat(parseOffProduct(json("""{"status":0,"status_verbose":"product not found"}"""))).isNull()
        assertThat(parseOffProduct(json("""{"status":1,"product":{"product_name":"X","nutriments":{}}}"""))).isNull()
        assertThat(parseOffProduct(json("\"nonsense\""))).isNull()
    }

    @Test
    fun readsSearchResultsAndSkipsProductsWithoutEnergy() {
        val results = parseOffSearch(json(offSearch))
        assertThat(results).hasSize(1)
        assertThat(results[0].name).isEqualTo("Yogur griego natural")
        assertThat(results[0].brand).isEqualTo("Hacendado")
        assertThat(results[0].servingGrams).isNull()
        val product = (json(offProduct) as kotlinx.serialization.json.JsonObject)["product"].toString()
        assertThat(parseOffSearch(json("""{"products":[$product]}"""))).hasSize(1)
        assertThat(parseOffSearch(json("""{"error":"x"}"""))).isEmpty()
    }

    // --- searchGenericFoods ---

    private fun ids(query: String, lang: FoodLang = FoodLang.ES) = searchGenericFoods(foods, query, lang).map { it.food.id }

    @Test
    fun matchesEveryWordAtTheStartOfAWordInAnyLanguage() {
        assertThat(ids("yogur griego")).containsExactly("usda:1")
        assertThat(ids("platano")).containsExactly("usda:3")
        assertThat(ids("joghurt")).containsExactly("ch:2")
        assertThat(ids("gurt")).isEmpty()
        assertThat(ids("   ")).isEmpty()
    }

    @Test
    fun ranksExactNamesFirstAndShowsTheNameInTheAppLanguage() {
        val matches = searchGenericFoods(foods, "greek yogurt", FoodLang.ES)
        assertThat(matches.map { it.food.id }).containsExactly("ch:2", "usda:1").inOrder()
        assertThat(matches.map { it.name }).containsExactly("Greek yogurt", "Yogur griego natural desnatado").inOrder()
    }

    @Test
    fun breaksTiesByShorterDisplayNameAndHonoursTheLimit() {
        assertThat(ids("ban")).containsExactly("usda:3", "ciqual:4").inOrder()
        assertThat(searchGenericFoods(foods, "ban", FoodLang.ES, 1)).hasSize(1)
    }
}
