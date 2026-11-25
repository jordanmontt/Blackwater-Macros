package com.blackwatermacros.app.core.foodquestions

import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.extractJson
import com.blackwatermacros.app.core.isValidDateKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

enum class Split(val id: String) {
    WEEKDAY_WEEKEND("weekday_weekend"), PERIODS("periods");

    companion object {
        fun fromId(id: String): Split? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What the model asks the app to compute (step 1; web `FoodRequest`). [analysis]
 * null is «none»: the question is not about the log. Fields the analysis does
 * not use are null or empty.
 */
data class FoodRequest(
    val analysis: AnalysisId?,
    val foods: List<String> = emptyList(),
    val metric: Metric? = null,
    val split: Split? = null,
    val from: String? = null,
    val to: String? = null,
    val from2: String? = null,
    val to2: String? = null,
    val at: String? = null,
)

val NO_REQUEST = FoodRequest(analysis = null)

/** A request as compact JSON with only its fields (for the prompt and the conversation). */
fun requestJson(request: FoodRequest): String = buildJsonObject {
    put("analysis", request.analysis?.id ?: "none")
    if (request.foods.isNotEmpty()) putJsonArray("foods") { request.foods.forEach { add(JsonPrimitive(it)) } }
    request.metric?.let { put("metric", it.id) }
    request.split?.let { put("split", it.id) }
    request.from?.let { put("from", it) }
    request.to?.let { put("to", it) }
    request.from2?.let { put("from2", it) }
    request.to2?.let { put("to2", it) }
    request.at?.let { put("at", it) }
}.toString()

/**
 * The system prompt of step 1: the question becomes a request. [previous] is
 * the last request of the conversation, for follow-ups («¿y el mes pasado?»).
 */
fun buildFoodRequestPrompt(today: String, language: String, previous: FoodRequest?): String {
    val spec = FoodQuestionsSpec
    val firstOfMonth = "${today.take(8)}01"
    val lastMonthTo = addDaysToKey(firstOfMonth, -1)
    val dates = mapOf(
        "yesterday" to addDaysToKey(today, -1),
        "lastMonthFrom" to "${lastMonthTo.take(8)}01",
        "lastMonthTo" to lastMonthTo,
    )
    val analyses = AnalysisId.entries.map { id ->
        val analysis = spec.analysis(id)
        "- ${id.id}: ${analysis.description}. Fields: ${analysis.fields.joinToString(", ")}."
    } + "- none: ${spec.noneDescription}."
    val fields = spec.fields.map { (field, description) -> "- $field: ${fill(description, "language" to language)}" }
    val examples = spec.examples.map { "Question: ${it.question}\nRequest: ${fill(it.request.toString(), dates)}" }
    val prompt = fill(
        spec.requestPrompt.joinToString("\n"),
        "today" to today,
        "weekday" to weekdayName(today),
        "language" to language,
        "analyses" to analyses.joinToString("\n"),
        "fields" to fields.joinToString("\n"),
        "examples" to examples.joinToString("\n"),
        "previous" to (previous?.let { fill(spec.previousRequest, "request" to requestJson(it)) } ?: ""),
    )
    return prompt.trimEnd()
}

/** Read from the model's JSON: [Missing] (absent or null), a value, or [Unreadable]. */
private sealed interface Field<out T> {
    data object Missing : Field<Nothing>
    data object Unreadable : Field<Nothing>
    data class Value<T>(val value: T) : Field<T>
}

private fun <T> Field<T>.orNull(): T? = (this as? Field.Value<T>)?.value

private fun stringOf(value: JsonElement?): Field<String> = when {
    value == null || value is JsonNull -> Field.Missing
    value is JsonPrimitive && value.isString -> if (value.content.isEmpty()) Field.Missing else Field.Value(value.content)
    else -> Field.Unreadable
}

private fun dateField(value: JsonElement?): Field<String> = when (val text = stringOf(value)) {
    is Field.Value -> text.value.trim().let { if (isValidDateKey(it)) Field.Value(it) else Field.Unreadable }
    else -> text
}

private fun <T : Any> enumField(value: JsonElement?, of: (String) -> T?): Field<T> = when (val text = stringOf(value)) {
    is Field.Value -> of(text.value)?.let { Field.Value(it) } ?: Field.Unreadable
    Field.Missing -> Field.Missing
    Field.Unreadable -> Field.Unreadable
}

private fun foodsField(value: JsonElement?): Field<List<String>> {
    val list = when {
        value == null || value is JsonNull -> return Field.Value(emptyList())
        value is JsonPrimitive && value.isString -> listOf(value)
        value is JsonArray -> value
        else -> return Field.Unreadable
    }
    if (list.any { it !is JsonPrimitive || !it.isString }) return Field.Unreadable
    return Field.Value(
        list.map { (it as JsonPrimitive).content.trim() }.filter { it.isNotEmpty() }.take(FoodQuestionsSpec.rules.maxSearchWords),
    )
}

/**
 * The model's answer to step 1 as a request, or null when it cannot be read: no
 * JSON, an unknown analysis, a value of the wrong kind, a required field missing
 * or a period that ends before it starts (web `parseFoodRequest`).
 */
fun parseFoodRequest(text: String): FoodRequest? {
    val json = extractJson(text) as? JsonObject ?: return null
    val analysisValue = json["analysis"]
    if (analysisValue is JsonPrimitive && analysisValue.isString && analysisValue.content == "none") return NO_REQUEST
    val id = (analysisValue as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(AnalysisId::fromId) ?: return null
    val spec = FoodQuestionsSpec.analysis(id)
    fun uses(field: String) = field in spec.fields

    val foods = foodsField(json["foods"])
    val metric = enumField(json["metric"], Metric::fromId)
    val split = enumField(json["split"], Split::fromId)
    val dates = listOf("from", "to", "from2", "to2", "at").map { dateField(json[it]) }
    if (listOf(foods, metric, split).any { it is Field.Unreadable } || dates.any { it is Field.Unreadable }) return null
    val (from, to, from2, to2) = dates.map { it.orNull() }
    val at = dates[4].orNull()

    val request = FoodRequest(
        analysis = id,
        foods = if (uses("foods")) foods.orNull().orEmpty() else emptyList(),
        metric = if (uses("metric")) metric.orNull() else null,
        split = if (uses("split")) split.orNull() else null,
        from = if (uses("from")) from else null,
        to = if (uses("to")) to else null,
        from2 = if (uses("from2")) from2 else null,
        to2 = if (uses("to2")) to2 else null,
        at = if (uses("at")) at else null,
    )
    val missing = spec.required.any { field ->
        when (field) {
            "foods" -> request.foods.isEmpty()
            "metric" -> request.metric == null
            "split" -> request.split == null
            "from" -> request.from == null
            "to" -> request.to == null
            "from2" -> request.from2 == null
            "to2" -> request.to2 == null
            "at" -> request.at == null
            else -> true
        }
    }
    if (missing) return null
    if (request.from != null && request.to != null && request.from > request.to) return null
    if (request.from2 != null && request.to2 != null && request.from2 > request.to2) return null
    return request
}
