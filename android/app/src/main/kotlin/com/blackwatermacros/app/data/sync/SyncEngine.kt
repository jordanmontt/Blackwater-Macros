package com.blackwatermacros.app.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.ApiService
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.local.nowIso
import com.blackwatermacros.app.data.local.toCore
import com.blackwatermacros.app.data.local.toEntity
import com.blackwatermacros.app.data.local.toRequest
import com.blackwatermacros.app.data.local.toWire
import com.blackwatermacros.app.data.profileBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

/** Why the last sync did not fully succeed (shown in Ajustes → Cuenta). */
enum class SyncProblem {
    /** The server answered with a 5xx error. */
    SERVER_ERROR,

    /** The server refused one change (it stays pending and is retried). */
    CHANGE_REJECTED,

    /** Something unexpected, e.g. a Wi-Fi login page instead of the API. */
    UNEXPECTED,
}

sealed interface SyncOutcome {
    data object Success : SyncOutcome
    data object NotLoggedIn : SyncOutcome
    data object SessionExpired : SyncOutcome

    /** No connection or the server is unreachable — retry later. */
    data object Offline : SyncOutcome
    data class Failed(val problem: SyncProblem) : SyncOutcome
}

/**
 * Mirrors the local database with the server account. One run =
 * 1. **push** every pending row (`PUT /:id` or `DELETE /:id`, both idempotent),
 * 2. **pull** the full lists and replace every row that has no pending change.
 *
 * Conflicts resolve as "last to sync wins" per record. Pulling everything (not
 * deltas) keeps the server simple and also removes rows deleted on the web.
 */
class SyncEngine(
    private val db: LocalDatabase,
    private val account: AccountStore,
    private val api: ApiService,
) {
    private val mutex = Mutex()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Last run's problem worth showing (null = fine or offline). */
    private val _lastProblem = MutableStateFlow<SyncProblem?>(null)
    val lastProblem: StateFlow<SyncProblem?> = _lastProblem.asStateFlow()

    suspend fun sync(): SyncOutcome = mutex.withLock {
        val current = account.current ?: return SyncOutcome.NotLoggedIn
        if (current.sessionExpired) return SyncOutcome.SessionExpired
        _running.value = true
        _lastProblem.value = null
        try {
            push()
            pull()
            account.update { it.copy(lastSyncAt = nowIso()) }
            SyncOutcome.Success
        } catch (e: HttpException) {
            if (e.code() == 401) {
                account.update { it.copy(sessionExpired = true) }
                SyncOutcome.SessionExpired
            } else {
                Log.w(TAG, "sync failed", e)
                fail(SyncProblem.SERVER_ERROR)
            }
        } catch (e: IOException) {
            SyncOutcome.Offline
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.g. a Wi-Fi captive portal answering with HTML: never crash, retry later.
            Log.w(TAG, "sync failed", e)
            fail(SyncProblem.UNEXPECTED)
        } finally {
            _running.value = false
        }
    }

    private fun fail(problem: SyncProblem): SyncOutcome {
        _lastProblem.value = problem
        return SyncOutcome.Failed(problem)
    }

    /** Runs [block] while no sync can start (used to wipe data on logout/login). */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    private suspend fun push() {
        val meals = db.meals()
        for (row in meals.pending()) {
            if (row.deleted) {
                deleteRemote { api.deleteMeal(row.id) }
                meals.purgeIfUnchanged(row.id, row.updatedAt)
            } else {
                upload("meal ${row.id}") {
                    val saved = api.putMeal(row.id, row.toRequest()).meal
                    meals.markSynced(row.id, row.updatedAt, saved.updatedAt)
                }
            }
        }

        val templates = db.templates()
        for (row in templates.pending()) {
            if (row.deleted) {
                deleteRemote { api.deleteTemplate(row.id) }
                templates.purgeIfUnchanged(row.id, row.updatedAt)
            } else {
                upload("template ${row.id}") {
                    val saved = api.putTemplate(row.id, row.toRequest()).template
                    templates.markSynced(row.id, row.updatedAt, saved.updatedAt)
                }
            }
        }

        val weights = db.weights()
        for (row in weights.pending()) {
            if (row.deleted) {
                deleteRemote { api.deleteWeight(row.id) }
                weights.purgeIfUnchanged(row.id, row.updatedAt)
            } else {
                upload("weight ${row.id}") {
                    val saved = api.putWeight(row.id, row.toRequest()).weight
                    weights.markSynced(row.id, row.updatedAt, saved.updatedAt)
                }
            }
        }

        val profile = db.profile()
        val sent = profile.get()
        if (sent != null && sent.pending) {
            upload("profile") {
                api.updateSettings(profileBody(sent.toCore().toWire()))
                // Only clear the flag if it was not edited while the request was in flight.
                if (profile.get() == sent) profile.markSynced()
            }
        }
    }

    private suspend fun pull() = coroutineScope {
        val meals = async { api.listMeals().meals }
        val templates = async { api.listTemplates().templates }
        val weights = async { api.listWeights().weights }
        val session = async { api.session() }

        val serverMeals = meals.await()
        val serverTemplates = templates.await()
        val serverWeights = weights.await()
        val serverSession = session.await()

        db.withTransaction {
            db.meals().let { dao ->
                replaceSynced(
                    serverIds = serverMeals.map { it.id }.toSet(),
                    pendingIds = dao.pendingIds().toSet(),
                    syncedIds = dao.syncedIds(),
                    purgeAll = { dao.purgeAll(it) },
                    upsertKept = { keep -> dao.upsertAll(serverMeals.filter { keep(it.id) }.map { it.toEntity() }) },
                )
            }
            db.templates().let { dao ->
                replaceSynced(
                    serverIds = serverTemplates.map { it.id }.toSet(),
                    pendingIds = dao.pendingIds().toSet(),
                    syncedIds = dao.syncedIds(),
                    purgeAll = { dao.purgeAll(it) },
                    upsertKept = { keep -> dao.upsertAll(serverTemplates.filter { keep(it.id) }.map { it.toEntity() }) },
                )
            }
            db.weights().let { dao ->
                replaceSynced(
                    serverIds = serverWeights.map { it.id }.toSet(),
                    pendingIds = dao.pendingIds().toSet(),
                    syncedIds = dao.syncedIds(),
                    purgeAll = { dao.purgeAll(it) },
                    upsertKept = { keep -> dao.upsertAll(serverWeights.filter { keep(it.id) }.map { it.toEntity() }) },
                )
            }
            val localProfile = db.profile().get()
            if (localProfile?.pending != true) {
                db.profile().upsert(serverSession.calorieProfile.toCore().toEntity(pending = false))
            }
        }
        account.update { it.copy(username = serverSession.username, isAdmin = serverSession.isAdmin) }
    }

    /**
     * Server wins for every row without a local pending change: rows gone from
     * the server are removed, the rest are overwritten.
     */
    private suspend fun replaceSynced(
        serverIds: Set<String>,
        pendingIds: Set<String>,
        syncedIds: List<String>,
        purgeAll: suspend (List<String>) -> Unit,
        upsertKept: suspend ((String) -> Boolean) -> Unit,
    ) {
        syncedIds.filter { it !in serverIds }
            .chunked(500) // SQLite bound-parameter limit
            .forEach { purgeAll(it) }
        upsertKept { id -> id !in pendingIds }
    }

    /** A record the server rejects (e.g. 400) stays pending but must not block the rest. */
    private suspend fun upload(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() >= 500) throw e
            Log.w(TAG, "server rejected $label (${e.code()})")
            _lastProblem.value = SyncProblem.CHANGE_REJECTED
        }
    }

    /** Deleting something the server no longer has is a success. */
    private suspend fun deleteRemote(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
    }

    private companion object {
        const val TAG = "SyncEngine"
    }
}
