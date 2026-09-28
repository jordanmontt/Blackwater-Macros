package com.blackwatermacros.app.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.blackwatermacros.app.R
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** A message for the user: an app string (in the app language) or, as a last resort, the server's own words. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> text
}

/**
 * Decodes the backend error envelope `{ "error": "<Spanish text>", "code": "<code>" }`
 * (TECHNICAL.md §5.3). The code is shown in the app language (`server_error_*`
 * strings, the same codes as the web's `src/lib/server-errors.ts`); the Spanish
 * text only when the code is unknown to this version of the app.
 */
object ResponseErrorMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /** Every code the server sends, with its string. `ServerErrorCodesTest` checks it against the web. */
    val CODES: Map<String, Int> = mapOf(
        "network" to R.string.server_error_network,
        "http" to R.string.server_error_http,
        "unauthenticated" to R.string.server_error_unauthenticated,
        "forbidden" to R.string.server_error_forbidden,
        "invalid_json" to R.string.server_error_invalid_json,
        "invalid_data" to R.string.server_error_invalid_data,
        "internal" to R.string.server_error_internal,
        "not_found" to R.string.server_error_not_found,
        "meal_not_found" to R.string.server_error_meal_not_found,
        "template_not_found" to R.string.server_error_template_not_found,
        "weight_not_found" to R.string.server_error_weight_not_found,
        "user_not_found" to R.string.server_error_user_not_found,
        "invalid_search" to R.string.server_error_invalid_search,
        "foods_unavailable" to R.string.server_error_foods_unavailable,
        "invalid_credentials" to R.string.server_error_invalid_credentials,
        "username_exists" to R.string.server_error_username_exists,
        "username_too_short" to R.string.server_error_username_too_short,
        "password_too_short" to R.string.server_error_password_too_short,
        "no_changes" to R.string.server_error_no_changes,
        "cannot_demote_self" to R.string.server_error_cannot_demote_self,
        "cannot_delete_self" to R.string.server_error_cannot_delete_self,
        "last_admin" to R.string.server_error_last_admin,
        "invalid_id" to R.string.server_error_invalid_id,
        "invalid_date" to R.string.server_error_invalid_date,
        "invalid_datetime" to R.string.server_error_invalid_datetime,
        "title_required" to R.string.server_error_title_required,
        "ingredient_name_required" to R.string.server_error_ingredient_name_required,
        "template_name_required" to R.string.server_error_template_name_required,
        "weight_out_of_range" to R.string.server_error_weight_out_of_range,
        "body_fat_out_of_range" to R.string.server_error_body_fat_out_of_range,
        "birth_year_out_of_range" to R.string.server_error_birth_year_out_of_range,
        "height_out_of_range" to R.string.server_error_height_out_of_range,
        "gym_days_out_of_range" to R.string.server_error_gym_days_out_of_range,
        "gym_minutes_out_of_range" to R.string.server_error_gym_minutes_out_of_range,
        "walking_out_of_range" to R.string.server_error_walking_out_of_range,
    )

    fun messageFrom(throwable: Throwable): UiText {
        val http = throwable as? HttpException
            ?: return if (throwable is IOException) UiText.Res(R.string.server_error_network) else UiText.Res(R.string.error_generic)

        val envelope = try {
            http.response()?.errorBody()?.string()?.let { json.decodeFromString<ApiError>(it) }
        } catch (_: Exception) {
            null
        }

        envelope?.code?.let { code -> CODES[code]?.let { return UiText.Res(it) } }
        if (!envelope?.error.isNullOrBlank()) return UiText.Raw(envelope.error)
        return when (http.code()) {
            401 -> UiText.Res(R.string.server_error_unauthenticated)
            403 -> UiText.Res(R.string.server_error_forbidden)
            404 -> UiText.Res(R.string.server_error_not_found)
            else -> UiText.Res(R.string.server_error_http, listOf(http.code()))
        }
    }
}
