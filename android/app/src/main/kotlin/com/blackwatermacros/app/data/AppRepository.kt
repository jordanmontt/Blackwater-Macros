package com.blackwatermacros.app.data

import androidx.room.withTransaction
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.local.MealEntity
import com.blackwatermacros.app.data.local.nowIso
import com.blackwatermacros.app.data.local.toCore
import com.blackwatermacros.app.data.local.toDto
import com.blackwatermacros.app.data.local.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/** How much data the phone holds — asked about when logging in. */
data class LocalDataSummary(val meals: Int, val weights: Int, val templates: Int) {
    val isEmpty: Boolean get() = meals == 0 && weights == 0 && templates == 0
}

/** Outcome of a CSV import; [skipped] rows already existed on the phone. */
data class ImportResult(val added: Int, val skipped: Int)

/**
 * Everything the screens read and write. Always local and instant; it never
 * touches the network. When an account is connected, each write is marked
 * pending and a background sync is scheduled.
 */
class AppRepository(
    private val db: LocalDatabase,
    private val account: AccountStore,
    private val onLocalChange: () -> Unit,
) {
    private val loggedIn: Boolean get() = account.current != null

    // --- Meals ---

    fun mealsForDay(day: String): Flow<List<MealDTO>> =
        db.meals().observeDay(day).map { rows -> rows.map { it.toDto() } }

    fun allMeals(): Flow<List<MealDTO>> =
        db.meals().observeAll().map { rows -> rows.map { it.toDto() } }

    /** Creates ([id] null) or replaces a meal. */
    suspend fun saveMeal(id: String?, request: MealRequest) {
        val dao = db.meals()
        db.withTransaction {
            val existing = id?.let { dao.get(it) }
            val sortOrder = if (existing != null && existing.logDate == request.logDate) {
                existing.sortOrder
            } else {
                (dao.maxSortOrder(request.logDate) ?: -1) + 1
            }
            dao.upsert(request.toEntity(id ?: newId(), sortOrder, nowIso()))
        }
        changed()
    }

    suspend fun deleteMeal(id: String) {
        val dao = db.meals()
        if (!loggedIn) {
            dao.purge(id)
        } else {
            dao.get(id)?.let { dao.upsert(it.copy(deleted = true, pending = true, updatedAt = nowIso())) }
        }
        changed()
    }

    /** Undo of [deleteMeal]: puts the meal back exactly as it was (same id and position). */
    suspend fun restoreMeal(meal: MealDTO) {
        db.meals().upsert(meal.toEntity().copy(pending = true, updatedAt = nowIso()))
        changed()
    }

    suspend fun reorderMeals(orderedIds: List<String>) {
        val dao = db.meals()
        val now = nowIso()
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                val row = dao.get(id) ?: return@forEachIndexed
                if (row.sortOrder != index) {
                    dao.upsert(row.copy(sortOrder = index, pending = true, updatedAt = now))
                }
            }
        }
        changed()
    }

    // --- Templates ---

    fun templates(): Flow<List<TemplateDTO>> =
        db.templates().observeAll().map { rows -> rows.map { it.toDto() } }

    suspend fun saveTemplate(id: String?, request: TemplateRequest) {
        db.templates().upsert(request.toEntity(id ?: newId(), nowIso()))
        changed()
    }

    suspend fun deleteTemplate(id: String) {
        val dao = db.templates()
        if (!loggedIn) {
            dao.purge(id)
        } else {
            dao.get(id)?.let { dao.upsert(it.copy(deleted = true, pending = true, updatedAt = nowIso())) }
        }
        changed()
    }

    // --- Weights ---

    /** Sorted by `measuredAt`, oldest first. */
    fun weights(): Flow<List<WeightDTO>> =
        db.weights().observeAll().map { rows -> rows.map { it.toDto() } }

    suspend fun saveWeight(id: String?, request: WeightRequest) {
        db.weights().upsert(request.toEntity(id ?: newId(), nowIso()))
        changed()
    }

    suspend fun deleteWeight(id: String) {
        val dao = db.weights()
        if (!loggedIn) {
            dao.purge(id)
        } else {
            dao.get(id)?.let { dao.upsert(it.copy(deleted = true, pending = true, updatedAt = nowIso())) }
        }
        changed()
    }

    // --- Profile ---

    fun profile(): Flow<CalorieProfile> = db.profile().observe().map { it.toCore() }

    suspend fun saveProfile(profile: CalorieProfile) {
        db.profile().upsert(profile.toEntity(pending = true))
        changed()
    }

    // --- CSV import ---

    /** Adds the meals, skipping any identical one already stored (re-importing a file is harmless). */
    suspend fun importMeals(meals: List<MealRequest>): ImportResult {
        val dao = db.meals()
        var added = 0
        db.withTransaction {
            val now = nowIso()
            val known = dao.observeAll().first().map { it.importKey() }.toMutableSet()
            for (meal in meals) {
                val entity = meal.toEntity(newId(), sortOrder = 0, updatedAt = now)
                if (!known.add(entity.importKey())) continue
                dao.upsert(entity.copy(sortOrder = (dao.maxSortOrder(meal.logDate) ?: -1) + 1))
                added++
            }
        }
        if (added > 0) changed()
        return ImportResult(added, meals.size - added)
    }

    suspend fun importWeights(weights: List<WeightRequest>): ImportResult {
        val dao = db.weights()
        var added = 0
        db.withTransaction {
            val now = nowIso()
            val known = dao.observeAll().first().map { it.measuredAt to it.weightKg }.toMutableSet()
            for (weight in weights) {
                val entity = weight.toEntity(newId(), now)
                if (!known.add(entity.measuredAt to entity.weightKg)) continue
                dao.upsert(entity)
                added++
            }
        }
        if (added > 0) changed()
        return ImportResult(added, weights.size - added)
    }

    private fun MealEntity.importKey() = listOf(logDate, title, entryMode, ingredientsJson, resolvedCalories, resolvedProtein)

    // --- Account support ---

    /** Number of local changes not yet on the server. */
    fun pendingChanges(): Flow<Int> = combine(
        listOf(
            db.meals().observePendingCount(),
            db.templates().observePendingCount(),
            db.weights().observePendingCount(),
            db.profile().observePendingCount(),
        ),
    ) { counts -> counts.sum() }

    suspend fun localDataSummary(): LocalDataSummary = LocalDataSummary(
        meals = db.meals().count(),
        weights = db.weights().count(),
        templates = db.templates().count(),
    )

    /** Deletes everything stored on the phone. */
    suspend fun wipe() = withContext(Dispatchers.IO) { db.clearAllTables() }

    private fun changed() {
        if (loggedIn) onLocalChange()
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
