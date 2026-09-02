package com.blackwatermacros.app.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every authenticated request. The token
 * provider returns null when the user is not logged in, in which case the header
 * is omitted (endpoints that require auth respond with 401 → re-login flow).
 */
class BearerAuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}