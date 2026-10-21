package com.blackwatermacros.app.data

import com.blackwatermacros.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit `ApiService`. `baseUrl` is the deployment **origin root**
 * (e.g. `https://app.blackwater-macros.jordanmontt.fr/`); every `ApiService` endpoint
 * carries its own absolute `/api/...` path.
 */
object ApiClient {

    fun create(
        baseUrl: String,
        tokenProvider: () -> String?,
        json: Json = ApiJson,
        httpClient: OkHttpClient? = null,
    ): ApiService {
        val client = httpClient ?: OkHttpClient.Builder()
            // Poor signal: OkHttp's 10 s default gives up on answers that are only slow
            // (the server may even have saved the change), leaving it «pending» for nothing.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AccountInterceptor(tokenProvider))
            .apply {
                // Log full request/response bodies in debug builds to aid testing.
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY),
                    )
                }
            }
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