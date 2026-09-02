package com.blackwatermacros.app.data

/**
 * Holds the current Bearer token for this app process. In-memory only for now
 * (a fresh process starts logged out); persistent storage arrives with the Room /
 * offline milestone. Both the login and home screens share this so they use the
 * same authenticated `ApiService`.
 */
object SessionManager {

    @Volatile
    var token: String? = null

    /** Provider suitable for [ApiClient.create]'s `tokenProvider`. */
    fun tokenProvider(): String? = token

    fun updateToken(newToken: String) {
        token = newToken
    }

    fun clear() {
        token = null
    }

    val isLoggedIn: Boolean get() = token != null
}