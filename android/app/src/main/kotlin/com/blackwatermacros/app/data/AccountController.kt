package com.blackwatermacros.app.data

import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncScheduler

/** Credentials verified by the server but not yet applied to the phone. */
data class VerifiedLogin(
    val username: String,
    val token: String,
    val isAdmin: Boolean,
    /** What is on the phone right now; non-empty means the user must choose. */
    val localData: LocalDataSummary,
    /** Logging back into the same account after the session expired: keep everything. */
    val isReLogin: Boolean,
)

/** The phone is linked to [expected] (session expired); logging into another account needs a logout first. */
class WrongAccountException(val expected: String) : IllegalStateException("Phone is linked to $expected")

/**
 * Connects and disconnects the optional sync account.
 *
 * - Login with local data → the user chooses to upload it or discard it.
 * - Logout → local data is deleted (it is safe on the server); the caller
 *   warns first if some changes never reached the server.
 */
class AccountController(
    private val account: AccountStore,
    private val repository: AppRepository,
    private val sync: SyncEngine,
    private val scheduler: SyncScheduler,
    private val api: ApiService,
    private val apiWithToken: (String) -> ApiService,
) {
    /** Step 1: checks the credentials against the server. Changes nothing locally. */
    suspend fun verify(username: String, password: String): VerifiedLogin {
        val login = api.login(LoginRequest(username.trim(), password))
        val session = apiWithToken(login.token).session()
        val current = account.current
        if (current != null && !current.username.equals(session.username, ignoreCase = true)) {
            throw WrongAccountException(current.username)
        }
        return VerifiedLogin(
            username = session.username,
            token = login.token,
            isAdmin = session.isAdmin,
            localData = repository.localDataSummary(),
            isReLogin = current != null,
        )
    }

    /**
     * Step 2: links the phone to the account. With [uploadLocalData] the rows
     * created offline (all still pending) are pushed by the first sync;
     * otherwise the phone is wiped and becomes a copy of the account.
     */
    suspend fun connect(login: VerifiedLogin, uploadLocalData: Boolean) {
        sync.exclusive {
            if (!login.isReLogin && !uploadLocalData) repository.wipe()
            account.save(
                Account(
                    username = login.username,
                    token = login.token,
                    isAdmin = login.isAdmin,
                    lastSyncAt = account.current?.lastSyncAt,
                ),
            )
        }
        scheduler.requestSync()
    }

    /**
     * Deletes everything stored on the phone, never the account on the server.
     * When logged in, the next sync downloads the account's data again (changes
     * not yet uploaded are lost — the caller warns first).
     */
    suspend fun deleteLocalData() {
        scheduler.cancel()
        sync.exclusive { repository.wipe() }
        if (account.current != null) scheduler.requestSync()
    }

    /** Disconnects and deletes all local data. */
    suspend fun logout() {
        scheduler.cancel()
        sync.exclusive {
            if (account.token() != null) runCatching { api.logout() }
            repository.wipe()
            account.clear()
        }
    }
}
