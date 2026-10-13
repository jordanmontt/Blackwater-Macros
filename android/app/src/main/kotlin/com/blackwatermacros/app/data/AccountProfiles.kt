package com.blackwatermacros.app.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Everything that belongs to one account on the phone: its own Room database, its
 * own session ([AccountStore]) and the clients built on them. Two accounts never
 * share a table, so a meal logged for one person can't end up in another's account.
 */
class ProfileScope(
    val id: String,
    val db: LocalDatabase,
    val account: AccountStore,
    val api: ApiService,
    val sync: SyncEngine,
    val repository: AppRepository,
    val accounts: AccountController,
)

/** One row of the account switcher. */
data class ProfileSummary(val id: String, val username: String, val sessionExpired: Boolean)

/**
 * Several accounts on one phone, switched with a tap (Android only, on purpose: the web
 * has one account per browser). Each account is a [ProfileScope]; one is active and
 * the screens only ever see that one. Switching changes the active id and the
 * Activity restarts, so every ViewModel is built again on the new scope.
 *
 * Invariant: either a single profile in local mode (no account, as before), or one
 * or more profiles that all have an account. «Añadir cuenta» is only offered with an
 * account, and logging out of one of several accounts removes its profile.
 *
 * The first profile is [DEFAULT_ID]: its database and session are the files a
 * single-account install already had, so updating the app keeps everything.
 */
class AccountProfiles(
    private val prefs: SharedPreferences,
    private val createScope: (id: String) -> ProfileScope,
    /** Deletes a removed profile's files (database and session). Called at start-up,
     * before anything opens them: never while a screen may still be reading them. */
    private val deleteFiles: (id: String) -> Unit,
    /** Server client without a token: only `login` goes through (see [AccountInterceptor]). */
    private val loginApi: ApiService,
    private val apiWithToken: (String) -> ApiService,
    private val requestSync: () -> Unit,
) {
    private val scopes = mutableMapOf<String, ProfileScope>()

    init {
        // Profiles logged out of last time: their data was already wiped; now the files go.
        prefs.getStringSet(KEY_REMOVED, null)?.forEach(deleteFiles)
        prefs.edit { remove(KEY_REMOVED) }
    }

    private val _ids = MutableStateFlow(readIds())
    private val _activeId = MutableStateFlow(readActive(_ids.value))

    /** Profiles in the order they were added. */
    val ids: StateFlow<List<String>> = _ids.asStateFlow()
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    val active: ProfileScope get() = scope(_activeId.value)

    @Synchronized
    fun scope(id: String): ProfileScope = scopes.getOrPut(id) { createScope(id) }

    /** The accounts on the phone, for the switcher (local mode = empty). */
    fun summaries(): List<ProfileSummary> = _ids.value.mapNotNull { id ->
        scope(id).account.current?.let { ProfileSummary(id, it.username, it.sessionExpired) }
    }

    /** [summaries], updated when an account is added, removed or its session expires. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun summariesFlow(): Flow<List<ProfileSummary>> = _ids.flatMapLatest { ids ->
        combine(ids.map { id -> scope(id).account.account.map { id to it } }) { pairs ->
            pairs.mapNotNull { (id, account) -> account?.let { ProfileSummary(id, it.username, it.sessionExpired) } }
        }
    }

    /** «Añadir cuenta» is offered once the active profile has an account. */
    val canAddAccount: Boolean get() = active.account.current != null

    fun switchTo(id: String) {
        require(id in _ids.value) { "Unknown profile $id" }
        if (id == _activeId.value) return
        prefs.edit { putString(KEY_ACTIVE, id) }
        _activeId.value = id
        requestSync()
    }

    /**
     * «Añadir cuenta»: checks the credentials, then gives the account its own, empty
     * profile and makes it active (its data arrives with the first sync). An account
     * already on the phone is not added twice: its session is renewed and it becomes
     * the active one. Wrong credentials or no network: nothing changes.
     */
    suspend fun addAccount(username: String, password: String): String {
        check(canAddAccount) { "Log in first: the local profile becomes the account's (upload or discard)" }
        val login = loginApi.login(LoginRequest(username.trim(), password))
        val session = apiWithToken(login.token).session()
        val account = Account(username = session.username, token = login.token, isAdmin = session.isAdmin)
        val existing = _ids.value.firstOrNull { id ->
            scope(id).account.current?.username?.equals(session.username, ignoreCase = true) == true
        }
        val id = existing ?: UUID.randomUUID().toString()
        val store = scope(id).account
        store.save(account.copy(lastSyncAt = store.current?.lastSyncAt))
        if (existing == null) writeIds(_ids.value + id)
        if (id == _activeId.value) requestSync() else switchTo(id)
        return id
    }

    /**
     * Logs out of the active account (local data deleted, as with a single account).
     * With other accounts on the phone its profile goes away and the first remaining
     * one becomes active; with none, the phone is back in local mode.
     */
    suspend fun logoutActive() {
        val scope = active
        scope.accounts.logout()
        val others = _ids.value - scope.id
        if (others.isEmpty()) return
        prefs.edit {
            putString(KEY_ACTIVE, others.first())
            // The open screens may still read this database until MainActivity restarts:
            // its files are deleted at the next start instead of now.
            putStringSet(KEY_REMOVED, (prefs.getStringSet(KEY_REMOVED, null).orEmpty() + scope.id))
        }
        writeIds(others)
        _activeId.value = others.first()
        synchronized(this) { scopes.remove(scope.id) }
        requestSync()
    }

    /** Background sync of every account, not just the active one: a meal logged for
     * someone else before switching still uploads. Retry if any account needs it. */
    suspend fun syncAll(): SyncOutcome {
        var outcome: SyncOutcome = SyncOutcome.Success
        for (id in _ids.value) {
            val result = scope(id).sync.sync()
            if (result == SyncOutcome.Offline || result is SyncOutcome.Failed) outcome = result
        }
        return outcome
    }

    private fun writeIds(ids: List<String>) {
        prefs.edit { putString(KEY_IDS, ids.joinToString(",")) }
        _ids.value = ids
    }

    private fun readIds(): List<String> =
        prefs.getString(KEY_IDS, null)?.split(',')?.filter { it.isNotBlank() }?.ifEmpty { null } ?: listOf(DEFAULT_ID)

    private fun readActive(ids: List<String>): String =
        prefs.getString(KEY_ACTIVE, null)?.takeIf { it in ids } ?: ids.first()

    companion object {
        /** The profile of a single-account install: `blackwater.db` and the `account` preferences. */
        const val DEFAULT_ID = "default"
        const val PREFS_NAME = "profiles"
        private const val KEY_IDS = "ids"
        private const val KEY_ACTIVE = "active"
        private const val KEY_REMOVED = "removed"

        fun databaseName(id: String) = if (id == DEFAULT_ID) "blackwater.db" else "blackwater-$id.db"
        fun accountPrefsName(id: String) = if (id == DEFAULT_ID) "account" else "account-$id"
    }
}
