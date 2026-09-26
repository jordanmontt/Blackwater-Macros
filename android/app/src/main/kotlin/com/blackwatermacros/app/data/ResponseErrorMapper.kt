package com.blackwatermacros.app.data

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Decodes the backend error envelope `{ "error": "<Spanish message>" }` (see
 * `docs/api.md`) into a user-facing message. Retrofit's default `HttpException`
 * only exposes `code()`/`message()`, not the Spanish body, so we parse it here.
 */
object ResponseErrorMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns the Spanish error message from an HTTP error body, or a stable
     * fallback when the body can't be decoded.
     */
    fun messageFrom(throwable: Throwable): String {
        val http = throwable as? HttpException
            ?: return if (throwable is IOException) {
                "Problema de conexión. Inténtalo de nuevo."
            } else {
                throwable.message ?: "Error inesperado."
            }

        val envelope = try {
            http.response()?.errorBody()?.string()?.let {
                json.decodeFromString<ApiError>(it).error
            }
        } catch (_: Exception) {
            null
        }

        return when {
            !envelope.isNullOrBlank() -> envelope
            http.code() == 401 -> "No autenticado"
            http.code() == 403 -> "No autorizado"
            http.code() == 404 -> "Recurso no encontrado"
            http.code() == 409 -> "Conflicto de datos"
            else -> "Error del servidor (${http.code()})"
        }
    }
}