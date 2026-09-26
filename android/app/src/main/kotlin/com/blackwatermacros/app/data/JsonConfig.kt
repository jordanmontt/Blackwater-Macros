package com.blackwatermacros.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Shared JSON configuration for the wire contract. JSON uses `.` as the decimal
 * separator (IEEE 754) and camelCase field names; unknown keys are tolerated so
 * adding server fields won't break older clients.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
/** Encodes every key, nulls included — required by `PUT /api/settings`. */
private val ExplicitNullsJson: Json = Json(ApiJson) { explicitNulls = true }

fun profileBody(profile: WireCalorieProfile): JsonObject =
    ExplicitNullsJson.encodeToJsonElement(WireCalorieProfile.serializer(), profile).jsonObject
