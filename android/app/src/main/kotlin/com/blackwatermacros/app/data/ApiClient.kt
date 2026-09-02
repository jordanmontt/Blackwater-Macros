package com.blackwatermacros.app.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds the Retrofit `ApiService`. `baseUrl` is the deployed backend root
 * including `/api/` (e.g. `https://example.com/api/`).
 */
object ApiClient {

    fun create(
        baseUrl: String,
        tokenProvider: () -> String?,
        json: Json = ApiJson,
        httpClient: OkHttpClient? = null,
    ): ApiService {
        val client = httpClient ?: OkHttpClient.Builder()
            .addInterceptor(BearerAuthInterceptor(tokenProvider))
            .build()

        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)
    }
}