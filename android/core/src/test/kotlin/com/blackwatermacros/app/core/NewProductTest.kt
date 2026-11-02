package com.blackwatermacros.app.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** Kotlin JUnit mirror of `tests/unit/new-product.test.ts`. */
class NewProductTest {

    private val milk = NewProduct(
        code = "7771234000123",
        name = " Leche entera ",
        brand = "PIL",
        basis = LabelBasis.SERVING,
        servingSize = "200 ml",
        calories = 124.0,
        protein = 6.4,
        carbs = 9.6,
        fat = 6.6,
    )

    private val oats = NewProduct(
        code = "8410128005432",
        name = "Oat drink",
        brand = null,
        basis = LabelBasis.PER_100ML,
        servingSize = null,
        calories = 46.0,
        protein = 1.0,
        carbs = null,
        fat = 1.5,
    )

    private val context = OffWriteContext(
        lang = FoodLang.ES,
        userId = "blackwater-macros-app",
        password = "secret",
        appVersion = "1.4.0",
        appUuid = "0b4e1c52-7d1a-4f0e-9a7b-3c2d1e0f9a8b",
        existing = false,
    )

    /** Open Food Facts has the milk per 100 ml, with the old recipe's values. */
    private val milkOnOff = OffCorrection(
        per = OffDataPer.PER_100G,
        base = milk.copy(name = "Leche entera", basis = LabelBasis.PER_100G, calories = 64.0, protein = 3.2, carbs = 4.8, fat = null),
    )

    private fun json(text: String) = Json.parseToJsonElement(text)

    // --- newProductPer100g / servingAmount ---

    @Test
    fun turnsALabelPerServingIntoPer100ml() {
        assertThat(servingAmount(milk)).isEqualTo(200.0)
        assertThat(newProductPer100g(milk)).isEqualTo(Per100g(62.0, 3.2, 4.8, 3.3))
    }

    @Test
    fun keepsALabelPer100AsItIsAndMissingCarbsOrFatCountAsZero() {
        assertThat(newProductPer100g(oats)).isEqualTo(Per100g(46.0, 1.0, 0.0, 1.5))
    }

    @Test
    fun roundsToOneDecimal() {
        assertThat(newProductPer100g(milk.copy(servingSize = "30 g", calories = 113.0, protein = 2.5)))
            .isEqualTo(Per100g(376.7, 8.3, 32.0, 22.0))
    }

    // --- newProductProblems ---

    @Test
    fun acceptsACompleteLabel() {
        assertThat(newProductProblems(milk)).isEmpty()
        assertThat(newProductProblems(oats)).isEmpty()
    }

    @Test
    fun needsANameTheKcalAndTheProteinInScreenOrder() {
        assertThat(newProductProblems(oats.copy(name = "  ", calories = null, protein = null)))
            .containsExactly(NewProductProblem.NAME, NewProductProblem.CALORIES, NewProductProblem.PROTEIN).inOrder()
    }

    @Test
    fun needsAServingSizeItCanReadWhenTheValuesArePerServing() {
        assertThat(newProductProblems(milk.copy(servingSize = null))).containsExactly(NewProductProblem.SERVING)
        assertThat(newProductProblems(milk.copy(servingSize = "1 vaso"))).containsExactly(NewProductProblem.SERVING)
    }

    @Test
    fun refusesValuesImpossibleFor100g() {
        assertThat(newProductProblems(oats.copy(calories = 4600.0))).containsExactly(NewProductProblem.TOO_HIGH)
        assertThat(newProductProblems(oats.copy(basis = LabelBasis.PER_100G, protein = 60.0, carbs = 50.0)))
            .containsExactly(NewProductProblem.TOO_HIGH)
        assertThat(newProductProblems(milk.copy(servingSize = "20 ml"))).containsExactly(NewProductProblem.TOO_HIGH)
    }

    // --- newProductToFoodProduct ---

    @Test
    fun isTheProductThePortionScreenAndSearchUse() {
        assertThat(newProductToFoodProduct(milk)).isEqualTo(
            FoodProduct("7771234000123", "Leche entera", "PIL", Per100g(62.0, 3.2, 4.8, 3.3), 200.0, false),
        )
        val oat = newProductToFoodProduct(oats.copy(brand = "  "))
        assertThat(oat.brand).isNull()
        assertThat(oat.servingGrams).isNull()
        assertThat(oat.incomplete).isTrue()
    }

    // --- offProductFields / offImageFields ---

    @Test
    fun sendsTheLabelAsPrintedWithTheAccountAndTheAnonymousInstallId() {
        assertThat(offProductFields(milk, context)).containsExactly(
            "code" to "7771234000123",
            "user_id" to "blackwater-macros-app",
            "password" to "secret",
            "app_name" to "Blackwater Macros",
            "app_version" to "1.4.0",
            "app_uuid" to "0b4e1c52-7d1a-4f0e-9a7b-3c2d1e0f9a8b",
            "lc" to "es",
            "product_name_es" to "Leche entera",
            "add_brands" to "PIL",
            "nutrition_data_per" to "serving",
            "serving_size" to "200 ml",
            "nutriment_energy-kcal" to "124",
            "nutriment_energy-kcal_unit" to "kcal",
            "nutriment_proteins" to "6.4",
            "nutriment_proteins_unit" to "g",
            "nutriment_carbohydrates" to "9.6",
            "nutriment_carbohydrates_unit" to "g",
            "nutriment_fat" to "6.6",
            "nutriment_fat_unit" to "g",
            "comment" to "Blackwater Macros 1.4.0",
        ).inOrder()
    }

    @Test
    fun leavesOutWhatTheUserLeftEmpty() {
        val fields = offProductFields(oats, context.copy(lang = FoodLang.EN))
        assertThat(fields.map { it.first }).doesNotContain("add_brands")
        assertThat(fields.map { it.first }).doesNotContain("nutriment_carbohydrates")
        assertThat(fields).contains("product_name_en" to "Oat drink")
        assertThat(fields).contains("nutrition_data_per" to "100ml")
    }

    @Test
    fun onlyAddsTheNutritionToAProductOpenFoodFactsAlreadyHad() {
        val keys = offProductFields(milk, context.copy(existing = true)).map { it.first }
        assertThat(keys).doesNotContain("product_name_es")
        assertThat(keys).doesNotContain("add_brands")
        assertThat(keys).contains("nutriment_energy-kcal")
    }

    @Test
    fun sendsThePhotoAsTheNutritionLabelInTheAppLanguage() {
        assertThat(offImageFields("7771234000123", context.copy(lang = FoodLang.FR))).containsExactly(
            "code" to "7771234000123",
            "user_id" to "blackwater-macros-app",
            "password" to "secret",
            "app_name" to "Blackwater Macros",
            "app_version" to "1.4.0",
            "app_uuid" to "0b4e1c52-7d1a-4f0e-9a7b-3c2d1e0f9a8b",
            "imagefield" to "nutrition_fr",
        ).inOrder()
    }

    // --- offFieldsOutcome / offImageOutcome ---

    @Test
    fun readsOpenFoodFactsAnswers() {
        assertThat(offFieldsOutcome(200, json("""{"status":1,"status_verbose":"fields saved"}"""))).isEqualTo(OffWriteOutcome.OK)
        assertThat(offFieldsOutcome(200, json("""{"status":0,"status_verbose":"no user credentials"}"""))).isEqualTo(OffWriteOutcome.ACCOUNT)
        assertThat(offFieldsOutcome(200, json("""{"status":0,"status_verbose":"no code or invalid code"}"""))).isEqualTo(OffWriteOutcome.REJECTED)
        assertThat(offFieldsOutcome(200, null)).isEqualTo(OffWriteOutcome.RETRY)
        assertThat(offImageOutcome(200, json("""{"status":"status ok","imgid":1}"""))).isEqualTo(OffWriteOutcome.OK)
        assertThat(offImageOutcome(200, json("""{"status":"status not ok","error":"This picture has already been sent."}"""))).isEqualTo(OffWriteOutcome.OK)
        assertThat(offImageOutcome(200, json("""{"status":"status not ok","error":"image too small"}"""))).isEqualTo(OffWriteOutcome.REJECTED)
    }

    @Test
    fun retriesWhatMayWorkLaterAndStopsOnTheRest() {
        for (status in listOf(408, 429, 500, 502, 503)) assertThat(offFieldsOutcome(status, null)).isEqualTo(OffWriteOutcome.RETRY)
        assertThat(offFieldsOutcome(403, null)).isEqualTo(OffWriteOutcome.ACCOUNT)
        assertThat(offImageOutcome(401, null)).isEqualTo(OffWriteOutcome.ACCOUNT)
        assertThat(offFieldsOutcome(400, null)).isEqualTo(OffWriteOutcome.REJECTED)
    }

    // --- offKnownProduct ---

    @Test
    fun startsTheFormFromWhatOpenFoodFactsKnows() {
        val known = json("""{"status":1,"product":{"product_name":"Galletas María","product_name_es":"Galletas María Oro","brands":"Fontaneda, Mondelez"}}""")
        assertThat(offKnownProduct(known, FoodLang.ES)).isEqualTo(OffKnownProduct("Galletas María Oro", "Fontaneda"))
        assertThat(offKnownProduct(known, FoodLang.FR)).isEqualTo(OffKnownProduct("Galletas María", "Fontaneda"))
        assertThat(offKnownProduct(json("""{"status":0}"""), FoodLang.ES)).isEqualTo(OffKnownProduct(null, null))
    }

    // --- searchOwnProducts ---

    @Test
    fun findsByTheStartOfAnyWordOfTheNameOrBrandWithoutAccents() {
        val products = listOf(
            newProductToFoodProduct(milk),
            newProductToFoodProduct(oats),
            newProductToFoodProduct(oats).copy(code = "1", name = "Yogur de avena", brand = "Hacendado"),
        )
        assertThat(searchOwnProducts(products, "lech").map { it.name }).containsExactly("Leche entera")
        assertThat(searchOwnProducts(products, "pil").map { it.name }).containsExactly("Leche entera")
        assertThat(searchOwnProducts(products, "Avéna yog").map { it.name }).containsExactly("Yogur de avena")
        assertThat(searchOwnProducts(products, "tera")).isEmpty()
        assertThat(searchOwnProducts(products, "  ")).isEmpty()
    }

    // --- parseLabelReading ---

    @Test
    fun readsTheModelsJsonAlsoInsideAFence() {
        val answer = "```json\n{\"name\":\"Leche entera\",\"brand\":\"PIL\",\"basis\":\"serving\",\"servingSize\":\"200 ml\",\"calories\":124,\"protein\":\"6,4 g\",\"carbs\":9.6,\"fat\":null}\n```"
        assertThat(parseLabelReading(answer)).isEqualTo(
            LabelReading("Leche entera", "PIL", LabelBasis.SERVING, "200 ml", 124.0, 6.4, 9.6, null),
        )
    }

    @Test
    fun understandsTheBasisWrittenInOtherWays() {
        fun read(basis: String?, servingSize: String? = null): LabelBasis? {
            val basisJson = basis?.let { "\"$it\"" } ?: "null"
            val servingJson = servingSize?.let { "\"$it\"" } ?: "null"
            return parseLabelReading("""{"basis":$basisJson,"servingSize":$servingJson,"calories":50,"protein":1}""")?.basis
        }
        assertThat(read("per 100 ml")).isEqualTo(LabelBasis.PER_100ML)
        assertThat(read("100 g")).isEqualTo(LabelBasis.PER_100G)
        assertThat(read("por porción")).isEqualTo(LabelBasis.SERVING)
        assertThat(read(null, "30 g")).isEqualTo(LabelBasis.SERVING)
        assertThat(read(null)).isEqualTo(LabelBasis.PER_100G)
    }

    @Test
    fun givesNothingWhenItReadNeitherKcalNorProtein() {
        assertThat(parseLabelReading("""{"name":"Leche","calories":null,"protein":null}""")).isNull()
        assertThat(parseLabelReading("I can't read this label.")).isNull()
    }

    // --- buildLabelReadingSystemPrompt ---

    @Test
    fun asksForTheLabelAsPrintedNeverEstimated() {
        val prompt = buildLabelReadingSystemPrompt("Spanish")
        assertThat(prompt).contains("Never estimate or invent a value")
        assertThat(prompt).contains("\"basis\": \"100g\" | \"100ml\" | \"serving\"")
        assertThat(prompt).contains("write it in Spanish")
    }

    // --- newProductInBasis ---

    @Test
    fun keepsALabelAlreadyInTheBasisOpenFoodFactsStores() {
        assertThat(newProductInBasis(milk, OffDataPer.SERVING)).isSameInstanceAs(milk)
        assertThat(newProductInBasis(oats, OffDataPer.PER_100G)).isSameInstanceAs(oats)
    }

    @Test
    fun convertsALabelPerServingToPer100AndPer100ToPerServing() {
        assertThat(newProductInBasis(milk, OffDataPer.PER_100G))
            .isEqualTo(milk.copy(basis = LabelBasis.PER_100G, calories = 62.0, protein = 3.2, carbs = 4.8, fat = 3.3))
        assertThat(newProductInBasis(oats.copy(servingSize = "250 ml"), OffDataPer.SERVING)).isEqualTo(
            oats.copy(servingSize = "250 ml", basis = LabelBasis.SERVING, calories = 115.0, protein = 2.5, carbs = null, fat = 3.75),
        )
    }

    @Test
    fun cantConvertWithoutAServingSize() {
        assertThat(newProductInBasis(oats, OffDataPer.SERVING)).isNull()
        assertThat(newProductProblems(oats, OffDataPer.SERVING)).containsExactly(NewProductProblem.SERVING)
        assertThat(newProductProblems(oats.copy(servingSize = "250 ml"), OffDataPer.SERVING)).isEmpty()
    }

    // --- correctionBaseline / correctionChanges ---

    @Test
    fun showsWhatOpenFoodFactsHasInTheBasisAndServingOfTheForm() {
        assertThat(correctionBaseline(milkOnOff, milkOnOff.base)).isEqualTo(LabelValues(64.0, 3.2, 4.8, null))
        assertThat(correctionBaseline(milkOnOff, milk)).isEqualTo(LabelValues(128.0, 6.4, 9.6, null))
        assertThat(correctionBaseline(milkOnOff, milk.copy(servingSize = null))).isNull()
    }

    @Test
    fun listsOnlyTheValuesThatReallyChangeAndAnEmptyFieldChangesNothing() {
        assertThat(correctionChanges(milkOnOff, milkOnOff.base)).isEmpty()
        assertThat(correctionChanges(milkOnOff, milk)).containsExactly(LabelNutrient.CALORIES, LabelNutrient.FAT).inOrder()
        assertThat(correctionChanges(milkOnOff, milkOnOff.base.copy(calories = 64.3, protein = null))).isEmpty()
    }

    // --- parseOffCorrection ---

    private val offMilk = """
        {"status":1,"product":{"product_name_es":"Leche entera","brands":"PIL","serving_size":"200 ml",
        "nutrition_data_per":"100g","nutriments":{"energy-kcal_100g":64,"proteins_100g":3.2,"carbohydrates_100g":4.8,"energy-kcal_serving":128}}}
    """

    @Test
    fun startsFromOpenFoodFactsValuesInTheBasisItStoresMissingOnesEmpty() {
        assertThat(parseOffCorrection(json(offMilk), "7771234000123", FoodLang.ES)).isEqualTo(milkOnOff)
    }

    @Test
    fun readsAProductStoredPerServingAndKjWhenKcalIsMissing() {
        val perServing = """
            {"product":{"product_name_es":"Leche entera","brands":"PIL","serving_size":"200 ml",
            "nutrition_data_per":"serving","nutriments":{"energy-kj_serving":535.6,"proteins_serving":6.4}}}
        """
        assertThat(parseOffCorrection(json(perServing), "7771234000123", FoodLang.ES)).isEqualTo(
            OffCorrection(OffDataPer.SERVING, milkOnOff.base.copy(basis = LabelBasis.SERVING, calories = 128.0, protein = 6.4, carbs = null, fat = null)),
        )
        assertThat(parseOffCorrection(json("""{"status":0}"""), "1", FoodLang.ES)).isNull()
    }

    // --- offProductFields for a correction ---

    @Test
    fun sendsTheValuesInTheBasisOpenFoodFactsStoresNeverTheNameOrBrand() {
        val fields = offProductFields(milk, context.copy(existing = true, keepPer = OffDataPer.PER_100G))
        assertThat(fields).contains("nutrition_data_per" to "100g")
        assertThat(fields).contains("nutriment_energy-kcal" to "62")
        assertThat(fields).contains("nutriment_fat" to "3.3")
        assertThat(fields).contains("serving_size" to "200 ml")
        assertThat(fields.map { it.first }).doesNotContain("product_name_es")
    }
}
