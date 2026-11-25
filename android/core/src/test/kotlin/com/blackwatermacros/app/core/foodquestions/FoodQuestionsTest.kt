package com.blackwatermacros.app.core.foodquestions

import com.blackwatermacros.app.core.CoachWeight
import com.blackwatermacros.app.core.EntryMode
import com.blackwatermacros.app.core.IngredientInput
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Kotlin mirror of `tests/unit/food-questions.test.ts`. Both read the same
 * expectations from `tests/fixtures/food-questions/`, so web and Android must
 * give the same requests, answers and texts.
 */
class FoodQuestionsTest {

    private val fixtures = File(System.getProperty("fixtures"), "food-questions")
    private fun read(name: String) = File(fixtures, name).readText()
    private val log = readLog(Json.parseToJsonElement(read("log.json")).jsonObject)
    private val expected = Json.parseToJsonElement(read("cases.json")).jsonObject
    private val basics = expected.getValue("basics").jsonObject
    private val cases = expected.getValue("cases").jsonArray.map { it.jsonObject }

    @Test
    fun amounts() {
        for (case in basics.getValue("amounts").jsonArray.map { it.jsonObject }) {
            val quantity = case.text("quantity")!!
            val amount = (case["amount"] as? JsonObject)?.let { Amount(it.getValue("value").jsonPrimitive.double, unitOf(it.text("unit")!!)) }
            assertWithMessage(quantity).that(parseAmount(quantity)).isEqualTo(amount)
        }
    }

    @Test
    fun names() {
        for (case in basics.getValue("names").jsonArray.map { it.jsonObject }) {
            assertThat(normalizeName(case.text("name")!!)).isEqualTo(case.text("normalized"))
        }
    }

    @Test
    fun matches() {
        for (case in basics.getValue("matches").jsonArray.map { it.jsonObject }) {
            val name = case.text("name")!!
            val foods = case.strings("foods")
            assertWithMessage("$name $foods").that(matchesTerms(normalizeName(name), searchTerms(foods)))
                .isEqualTo(case.getValue("matches").jsonPrimitive.boolean)
        }
    }

    @Test
    fun theFoodLoggedOnMostDaysForTheExampleQuestion() {
        assertThat(mostLoggedFood(log.meals, log.today)).isEqualTo(basics.text("mostLoggedFood"))
        assertThat(mostLoggedFood(emptyList(), log.today)).isNull()
    }

    @Test
    fun theResultIsCutAtWholeLines() {
        val cut = basics.getValue("cut").jsonObject
        val text = resultText(cut.strings("lines"))
        assertThat(text).isEqualTo(cut.text("text"))
        assertThat(text.length).isAtMost(FoodQuestionsSpec.rules.resultMaxChars)
    }

    @Test
    fun theRequestPromptIsTheWebOne() {
        val previous = readRequest(cases.first().getValue("request").jsonObject)
        assertThat(buildFoodRequestPrompt(log.today, "Spanish", previous)).isEqualTo(read("request-prompt.txt").trimEnd())
        assertThat(buildFoodRequestPrompt(log.today, "Spanish", null)).endsWith("Request: {\"analysis\":\"none\"}")
    }

    @Test
    fun sharedCases() {
        for (case in cases) {
            val name = case.text("name")!!
            val request = (case["request"] as? JsonObject)?.let(::readRequest)
            val parsed = parseFoodRequest(case.text("model")!!)
            assertWithMessage(name).that(parsed).isEqualTo(request)
            if (request == null) continue
            assertWithMessage(name).that(requestJson(request)).isEqualTo(case.text("requestJson"))
            val answer = (case["answer"] as? JsonObject)?.let(::readAnswer)
            assertWithMessage(name).that(answerFoodRequest(log, request)).isEqualTo(answer)
        }
    }

    @Test
    fun theRegistryHasEveryAnalysisOfTheSharedFile() {
        assertThat(ANALYSES.keys.map { it.id }).containsExactlyElementsIn(FoodQuestionsSpec.analyses.keys)
        assertThat(AnalysisId.entries.map { it.id }).containsExactlyElementsIn(FoodQuestionsSpec.analyses.keys).inOrder()
        ANALYSES.forEach { (id, analysis) -> assertThat(analysis.id).isEqualTo(id) }
    }

    @Test
    fun step3ReadsTheQuestionThenTheNumbers() {
        assertThat(foodQuestionMessage("¿Cuánta leche tomo?", "FOOD LOG RESULT…")).isEqualTo("¿Cuánta leche tomo?\n\nFOOD LOG RESULT…")
    }

    // --- Reading the shared JSON ---

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
    private fun JsonObject.strings(key: String): List<String> = getValue(key).jsonArray.map { it.jsonPrimitive.content }
    private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double

    private fun unitOf(id: String) = AmountUnit.entries.first { it.id == id }

    private fun readLog(json: JsonObject) = FoodLog(
        today = json.text("today")!!,
        meals = json.getValue("meals").jsonArray.map { it.jsonObject }.map { meal ->
            FoodLogMeal(
                logDate = meal.text("logDate")!!,
                title = meal.text("title")!!,
                entryMode = if (meal.text("entryMode") == "total_only") EntryMode.TOTAL_ONLY else EntryMode.PER_INGREDIENT,
                ingredients = meal.getValue("ingredients").jsonArray.map { it.jsonObject }.map { item ->
                    IngredientInput(
                        item.text("name")!!,
                        item.text("quantity"),
                        item["calories"]?.jsonPrimitive?.doubleOrNull,
                        item["protein"]?.jsonPrimitive?.doubleOrNull,
                        item["carbs"]?.jsonPrimitive?.doubleOrNull,
                        item["fat"]?.jsonPrimitive?.doubleOrNull,
                    )
                },
                resolvedCalories = meal.double("resolvedCalories"),
                resolvedProtein = meal.double("resolvedProtein"),
                resolvedCarbs = meal.double("resolvedCarbs"),
                resolvedFat = meal.double("resolvedFat"),
            )
        },
        weights = json.getValue("weights").jsonArray.map { it.jsonObject }.map { w ->
            CoachWeight(
                date = w.text("date")!!,
                weightKg = w.double("weightKg"),
                bodyFatPct = w["bodyFatPct"]?.jsonPrimitive?.doubleOrNull,
                fasted = w.getValue("fasted").jsonPrimitive.boolean,
                time = w.text("time")!!,
                timeZone = w.text("timeZone"),
            )
        },
    )

    private fun readRequest(json: JsonObject) = FoodRequest(
        analysis = json.text("analysis")!!.let { if (it == "none") null else AnalysisId.fromId(it)!! },
        foods = json.strings("foods"),
        metric = json.text("metric")?.let { Metric.fromId(it)!! },
        split = json.text("split")?.let { Split.fromId(it)!! },
        from = json.text("from"),
        to = json.text("to"),
        from2 = json.text("from2"),
        to2 = json.text("to2"),
        at = json.text("at"),
    )

    private fun readRange(json: JsonObject) = DayRange(json.text("from")!!, json.text("to")!!)

    private fun readDetails(json: JsonObject) = FoodDetails(
        analysis = AnalysisId.fromId(json.text("analysis")!!)!!,
        groups = json.getValue("groups").jsonArray.map { it.jsonObject }.map { g ->
            FoodGroup(
                label = GroupLabel.entries.first { it.id == g.text("label") },
                from = g.text("from")!!,
                to = g.text("to")!!,
                loggedDays = g.getValue("loggedDays").jsonPrimitive.int,
                totalDays = g.getValue("totalDays").jsonPrimitive.int,
            )
        },
        counted = json.strings("counted"),
        moreCounted = json.getValue("moreCounted").jsonPrimitive.int,
        withoutAmount = json.getValue("withoutAmount").jsonPrimitive.int,
        few = json.getValue("few").jsonPrimitive.boolean,
        split = (json["split"] as? JsonObject)?.let { s ->
            WeightSplit(s.text("at")!!, SplitReason.entries.first { it.id == s.text("reason") })
        },
    )

    private fun readAnswer(json: JsonObject): FoodAnswer = when (json.text("kind")) {
        "result" -> FoodAnswer.Result(json.text("text")!!, readDetails(json.getValue("details").jsonObject))
        "no_match" -> FoodAnswer.NoMatch(
            json.strings("foods"),
            readRange(json.getValue("range").jsonObject),
            json.text("lastSeen"),
            json.strings("similar"),
        )
        "not_enough" -> FoodAnswer.NotEnough(
            NotEnoughReason.entries.first { it.id == json.text("reason") },
            readDetails(json.getValue("details").jsonObject),
        )
        else -> error("Unknown answer kind in cases.json: $json")
    }
}
