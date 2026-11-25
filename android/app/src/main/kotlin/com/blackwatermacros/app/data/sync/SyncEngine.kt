package com.blackwatermacros.app.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.ApiService
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.SessionResponse
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.local.history
import com.blackwatermacros.app.data.local.nowIso
import com.blackwatermacros.app.data.local.toCore
import com.blackwatermacros.app.data.local.toEntity
import com.blackwatermacros.app.data.local.toRequest
import com.blackwatermacros.app.data.local.toWire
import com.blackwatermacros.app.data.profileBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
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
 * 1. **push** every pending row (`PUT /:id` or `DELETE /:id`, both idempotent), list by
 *    list: one the server can't take now waits without holding back the others,
 * 2. **pull** the full lists and replace every row that has no pending change.
 *    Each list on its own: one the server can't send (on 24 Nov 2026 the weigh-ins
 *    answered 500, a database column was missing) doesn't hold back the others.
 *
 * Conflicts resolve as "last to sync wins" per record. Pulling everything (not
 * deltas) keeps the server simple and also removes rows deleted on the web.
 * `lastSyncAt` moves only when the whole run succeeds.
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
            val uploadFailure = push()
            pull()
            // A list whose upload must wait: its rows stay pending, the run fails and is retried.
            uploadFailure?.let { throw it }
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

    /**
     * Uploads every pending row, list by list. A «try again later» answer (408, 5xx) stops that
     * list only: the other lists and the download still run (on 24 Nov 2026 the weigh-ins
     * answered 500 for hours, and one pending weigh-in would have held back everything).
     * Returns that answer, so the run fails afterwards and is retried.
     */
    private suspend fun push(): HttpException? {
        var retryLater: HttpException? = null
        suspend fun list(name: String, uploads: suspend () -> Unit) {
            try {
                uploads()
            } catch (e: HttpException) {
                if (e.code() != 408 && e.code() < 500) throw e
                Log.w(TAG, "$name: the server answered ${e.code()}, retried later")
                if (retryLater == null) retryLater = e
            }
        }

        list("meals") {
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
        }

        list("templates") {
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
        }

        list("weights") {
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
        }

        list("profile") {
            val profile = db.profile()
            val sent = profile.get()
            if (sent != null && sent.pending) {
                upload("profile") {
                    api.updateSettings(profileBody(sent.toCore().toWire(), sent.history().map { it.toWire() }))
                    // Only clear the flag if it was not edited while the request was in flight.
                    if (profile.get() == sent) profile.markSynced()
                }
            }
        }
        return retryLater
    }

    private suspend fun pull() {
        val (meals, templates, weights, session) = supervisorScope {
            val meals = async { api.listMeals().meals }
            val templates = async { api.listTemplates().templates }
            val weights = async { api.listWeights().weights }
            val session = async { api.session() }
            Downloads(meals.outcome(), templates.outcome(), weights.outcome(), session.outcome())
        }

        db.withTransaction {
            meals.getOrNull()?.let { serverMeals ->
                val dao = db.meals()
                replaceSynced(
                    serverIds = serverMeals.map { it.id }.toSet(),
                    pendingIds = dao.pendingIds().toSet(),
                    syncedIds = dao.syncedIds(),
                    purgeAll = { dao.purgeAll(it) },
                    upsertKept = { keep -> dao.upsertAll(serverMeals.filter { keep(it.id) }.map { it.toEntity() }) },
                )
            }
            templates.getOrNull()?.let { serverTemplates ->
                val dao = db.templates()
                replaceSynced(
                    serverIds = serverTemplates.map { it.id }.toSet(),
                    pendingIds = dao.pendingIds().toSet(),
                    syncedIds = dao.syncedIds(),
                    purgeAll = { dao.purgeAll(it) },
                    upsertKept = { keep -> dao.upsertAll(serverTemplates.filter { keep(it.id) }.map { it.toEntity() }) },
                )
            }
            weights.getOrNull()?.let { serverWeights ->
                val dao = db.weights()
                replaceSynced(
                    serverIds = serverWeights.map { it.id }.toSet(),
                    pendingIds = dao.pendingIds().toSet(),
                    syncedIds = dao.syncedIds(),
                    purgeAll = { dao.purgeAll(it) },
                    upsertKept = { keep -> dao.upsertAll(serverWeights.filter { keep(it.id) }.map { it.toEntity() }) },
                )
            }
            session.getOrNull()?.let { serverSession ->
                val localProfile = db.profile().get()
                if (localProfile?.pending != true) {
                    // An older server sends no history: keep the phone's.
                    val history = serverSession.profileHistory?.map { it.toCore() } ?: localProfile.history()
                    db.profile().upsert(serverSession.calorieProfile.toCore().toEntity(pending = false, history = history))
                }
            }
        }
        session.getOrNull()?.let { serverSession ->
            account.update { it.copy(username = serverSession.username, isAdmin = serverSession.isAdmin) }
        }

        // What was downloaded is saved; the run still fails, so it is retried and shown.
        val failures = listOf(meals, templates, weights, session).mapNotNull { it.exceptionOrNull() }
        (failures.firstOrNull { it is HttpException && it.code() == 401 } ?: failures.firstOrNull())?.let { throw it }
    }

    /** The four downloads of one run, each a success or its own failure. */
    private data class Downloads(
        val meals: Result<List<MealDTO>>,
        val templates: Result<List<TemplateDTO>>,
        val weights: Result<List<WeightDTO>>,
        val session: Result<SessionResponse>,
    )

    /** The download's result, or its failure (a cancelled sync still stops here). */
    private suspend fun <T> Deferred<T>.outcome(): Result<T> = try {
        Result.success(await())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
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

    /**
     * A record the server rejects (e.g. 400) stays pending but must not block the rest.
     * «Try again later» answers are not a rejection: 408 (timeout) and 5xx stop that list
     * ([push]), 429 (too many requests) ends the run; either way it is retried, like being offline.
     */
    private suspend fun upload(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 408 || e.code() == 429 || e.code() >= 500) throw e
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
