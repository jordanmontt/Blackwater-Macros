package com.blackwatermacros.app.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Every request to the Blackwater server goes through here. With an account it
 * adds `Authorization: Bearer <token>`. **Without one, nothing leaves the phone**
 * except the login the user starts: any other request fails here, before the
 * network, so no code path can send data to our server without an account.
 */
class AccountInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider()
        if (token == null) {
            if (request.method != "POST" || request.url.encodedPath != LOGIN_PATH) throw NoAccountException()
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }

    companion object {
        const val LOGIN_PATH = "/api/auth/login"
    }
}

/** A request to the Blackwater server without an account: refused on the phone. */
class NoAccountException : IOException("No account: nothing is sent to the server")
