package com.blackwatermacros.app.core.foodquestions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The analyses the app can run (web `AnalysisId`); the same ids as `food-questions.json`. */
enum class AnalysisId(val id: String) {
    FOOD_STATS("food_stats"),
    RANKING("ranking"),
    DAY_LOG("day_log"),
    COMPARE("compare"),
    FOOD_VS_WEIGHT("food_vs_weight");

    companion object {
        fun fromId(id: String): AnalysisId? = entries.firstOrNull { it.id == id }
    }
}

enum class Metric(val id: String) {
    CALORIES("calories"), PROTEIN("protein"), CARBS("carbs"), FAT("fat"), TIMES("times");

    companion object {
        fun fromId(id: String): Metric? = entries.firstOrNull { it.id == id }
    }
}

/** A part of the log an answer counted (web `GroupLabel`). */
enum class GroupLabel(val id: String) {
    PERIOD("period"), WEEKDAYS("weekdays"), WEEKEND("weekend"), FIRST("first"), SECOND("second"), BEFORE("before"), AFTER("after")
}

data class FoodQuestionRules(
    val minLoggedDays: Int,
    val fewLoggedDays: Int,
    val defaultDays: Int,
    val weightDefaultDays: Int,
    val weekendDefaultDays: Int,
    val minSideDays: Int,
    val changeGapDays: Int,
    val dayLogMaxDays: Int,
    val rankingSize: Int,
    val countedNamesShown: Int,
    val similarNamesShown: Int,
    val maxSearchWords: Int,
    val resultMaxChars: Int,
    /** Thinking cloud models spend part of it before answering. */
    val requestMaxTokens: Int,
    /** The phone's and the browser's models: the request is a few lines of JSON. */
    val localRequestMaxTokens: Int,
)

data class AnalysisSpec(val description: String, val fields: List<String>, val required: List<String>)

data class ExampleSpec(val question: String, val request: JsonObject)

data class MetricSpec(val name: String, val unit: String)

/**
 * The shared part of food questions, as data (web `spec.ts`): the rules, the
 * prompt of step 1, the analyses' descriptions and the result's text templates.
 * Read from `food-questions.json`, the web's own file, bundled as a resource.
 */
object FoodQuestionsSpec {
    private val json: JsonObject = Json.parseToJsonElement(
        FoodQuestionsSpec::class.java.getResourceAsStream("/foodquestions/food-questions.json")!!
            .bufferedReader().use { it.readText() },
    ).jsonObject

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.texts(key: String): List<String> = getValue(key).jsonArray.map { it.jsonPrimitive.content }

    val rules: FoodQuestionRules = json.getValue("rules").jsonObject.let { r ->
        fun n(key: String) = r.getValue(key).jsonPrimitive.int
        FoodQuestionRules(
            minLoggedDays = n("minLoggedDays"),
            fewLoggedDays = n("fewLoggedDays"),
            defaultDays = n("defaultDays"),
            weightDefaultDays = n("weightDefaultDays"),
            weekendDefaultDays = n("weekendDefaultDays"),
            minSideDays = n("minSideDays"),
            changeGapDays = n("changeGapDays"),
            dayLogMaxDays = n("dayLogMaxDays"),
            rankingSize = n("rankingSize"),
            countedNamesShown = n("countedNamesShown"),
            similarNamesShown = n("similarNamesShown"),
            maxSearchWords = n("maxSearchWords"),
            resultMaxChars = n("resultMaxChars"),
            requestMaxTokens = n("requestMaxTokens"),
            localRequestMaxTokens = n("localRequestMaxTokens"),
        )
    }

    val requestPrompt: List<String> = json.texts("requestPrompt")
    val noneDescription: String = json.text("noneDescription")
    val previousRequest: String = json.text("previousRequest")

    /** In the file's order (the prompt lists them so). */
    val analyses: Map<String, AnalysisSpec> = json.getValue("analyses").jsonObject.mapValues { (_, value) ->
        val spec = value.jsonObject
        AnalysisSpec(spec.text("description"), spec.texts("fields"), spec.texts("required"))
    }

    val fields: Map<String, String> = json.getValue("fields").jsonObject.mapValues { it.value.jsonPrimitive.content }

    val examples: List<ExampleSpec> = json.getValue("examples").jsonArray.map {
        val example = it.jsonObject
        ExampleSpec(example.text("question"), example.getValue("request").jsonObject)
    }

    private val result = json.getValue("result").jsonObject

    /** A result template by its name in the file («period», «foodDays»…). */
    fun template(name: String): String = result.text(name)

    fun label(label: GroupLabel): String = result.getValue("labels").jsonObject.text(label.id)

    fun metric(metric: Metric): MetricSpec = result.getValue("metrics").jsonObject.getValue(metric.id).jsonObject.let {
        MetricSpec(it.text("name"), it.text("unit"))
    }

    fun analysis(id: AnalysisId): AnalysisSpec = analyses.getValue(id.id)
}

private val PLACEHOLDER = Regex("\\{(\\w+)\\}")

/** «{name}» in [template] replaced by its value; unknown names are left as they are (web `fill`). */
fun fill(template: String, values: Map<String, Any>): String =
    PLACEHOLDER.replace(template) { match -> values[match.groupValues[1]]?.toString() ?: match.value }

fun fill(template: String, vararg values: Pair<String, Any>): String = fill(template, values.toMap())
