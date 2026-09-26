package com.blackwatermacros.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The optional sync account. `null` = the app runs purely local. */
data class Account(
    val username: String,
    val token: String,
    val isAdmin: Boolean,
    /** The server rejected the token (401). Data stays; the user must log in again. */
    val sessionExpired: Boolean = false,
    /** ISO instant of the last successful sync, or null if never. */
    val lastSyncAt: String? = null,
)

/**
 * Persists the account in the app's private SharedPreferences so the user
 * stays logged in across restarts. Exposed as a [StateFlow] for the UI.
 */
class AccountStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences("account", Context.MODE_PRIVATE))

    private val _account = MutableStateFlow(read())
    val account: StateFlow<Account?> = _account.asStateFlow()

    val current: Account? get() = _account.value

    /** Token for API calls; null when logged out or the session expired. */
    fun token(): String? = current?.takeUnless { it.sessionExpired }?.token

    fun save(account: Account) {
        prefs.edit {
            putString(KEY_USERNAME, account.username)
            putString(KEY_TOKEN, account.token)
            putBoolean(KEY_ADMIN, account.isAdmin)
            putBoolean(KEY_EXPIRED, account.sessionExpired)
            putString(KEY_LAST_SYNC, account.lastSyncAt)
        }
        _account.value = account
    }

    fun update(transform: (Account) -> Account) {
        current?.let { save(transform(it)) }
    }

    fun clear() {
        prefs.edit { clear() }
        _account.value = null
    }

    private fun read(): Account? {
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Account(
            username = username,
            token = token,
            isAdmin = prefs.getBoolean(KEY_ADMIN, false),
            sessionExpired = prefs.getBoolean(KEY_EXPIRED, false),
            lastSyncAt = prefs.getString(KEY_LAST_SYNC, null),
        )
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_TOKEN = "token"
        const val KEY_ADMIN = "isAdmin"
        const val KEY_EXPIRED = "sessionExpired"
        const val KEY_LAST_SYNC = "lastSyncAt"
    }
}
