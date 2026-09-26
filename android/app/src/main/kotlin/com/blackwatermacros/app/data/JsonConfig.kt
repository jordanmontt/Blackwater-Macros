package com.blackwatermacros.app.data

import kotlinx.serialization.json.Json

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