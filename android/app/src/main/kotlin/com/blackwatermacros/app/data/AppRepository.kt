package com.blackwatermacros.app.data

import androidx.room.withTransaction
import com.blackwatermacros.app.core.BackupContent
import com.blackwatermacros.app.core.BackupData
import com.blackwatermacros.app.core.BackupMeal
import com.blackwatermacros.app.core.BackupTemplate
import com.blackwatermacros.app.core.BackupWeight
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.EntryMode
import com.blackwatermacros.app.core.IngredientInput
import com.blackwatermacros.app.core.ProfilePeriod
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.core.withProfileChange
import com.blackwatermacros.app.data.local.LocalDatabase
import com.blackwatermacros.app.data.local.history
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

    /** Creates ([id] null) or replaces a meal; returns its id. */
    suspend fun saveMeal(id: String?, request: MealRequest): String {
        val dao = db.meals()
        val mealId = id ?: newId()
        db.withTransaction {
            val existing = id?.let { dao.get(it) }
            val sortOrder = if (existing != null && existing.logDate == request.logDate) {
                existing.sortOrder
            } else {
                (dao.maxSortOrder(request.logDate) ?: -1) + 1
            }
            dao.upsert(request.toEntity(mealId, sortOrder, nowIso()))
        }
        changed()
        return mealId
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

    /** Creates ([id] null) or replaces a template; returns its id. */
    suspend fun saveTemplate(id: String?, request: TemplateRequest): String {
        val templateId = id ?: newId()
        db.templates().upsert(request.toEntity(templateId, nowIso()))
        changed()
        return templateId
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

    /** Undo of [deleteTemplate]: the same template (same id) back. */
    suspend fun restoreTemplate(template: TemplateDTO) {
        db.templates().upsert(template.toEntity().copy(pending = true, updatedAt = nowIso()))
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

    /** Undo of [deleteWeight]: the same weigh-in (same id) back. */
    suspend fun restoreWeight(weight: WeightDTO) {
        db.weights().upsert(weight.toEntity().copy(pending = true, updatedAt = nowIso()))
        changed()
    }

    // --- Profile ---

    fun profile(): Flow<CalorieProfile> = db.profile().observe().map { it.toCore() }

    /** Former profiles: past days keep the targets they had (`core/ProfileHistory.kt`). */
    fun profileHistory(): Flow<List<ProfilePeriod>> = db.profile().observe().map { it.history() }

    /** Saves the profile; the change applies from today on (days up to yesterday keep the old one). */
    suspend fun saveProfile(profile: CalorieProfile, today: String = todayKey()) {
        db.withTransaction {
            val saved = db.profile().get()
            val history = withProfileChange(saved.toCore(), saved.history(), profile, today)
            db.profile().upsert(profile.toEntity(pending = true, history = history))
        }
        changed()
    }

    // --- Export file ---

    /** Everything the export file holds (`core/Backup.kt`). */
    suspend fun backupData(): BackupData = BackupData(
        profile = db.profile().get().toCore(),
        profileHistory = db.profile().get().history(),
        weights = db.weights().observeAll().first().map { BackupWeight(it.measuredAt, it.weightKg, it.bodyFatPct, it.note) },
        meals = db.meals().observeAll().first().map { BackupMeal(it.logDate, it.toDto().backupContent()) },
        templates = db.templates().observeAll().first().map { it.toDto().let { t -> BackupTemplate(t.name, t.backupContent()) } },
    )

    /**
     * The import: the phone's meals, weigh-ins, templates and profile become the file's.
     * Only without an account (the screen does not offer it otherwise): with one, the
     * next sync would upload it all, and the import must not touch the server.
     */
    suspend fun replaceAll(contents: BackupContents) {
        check(!loggedIn) { "The import is for local mode only" }
        val now = nowIso()
        db.withTransaction {
            db.meals().deleteEverything()
            db.templates().deleteEverything()
            db.weights().deleteEverything()
            val position = mutableMapOf<String, Int>()
            db.meals().upsertAll(
                contents.meals.map { meal ->
                    val sortOrder = position.getOrDefault(meal.logDate, 0)
                    position[meal.logDate] = sortOrder + 1
                    meal.toEntity(newId(), sortOrder, now)
                },
            )
            contents.templates.forEach { db.templates().upsert(it.toEntity(newId(), now)) }
            contents.weights.forEach { db.weights().upsert(it.toEntity(newId(), now)) }
            db.profile().upsert(contents.profile.toEntity(pending = true, history = contents.profileHistory))
        }
    }

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

/** A meal's or template's content as the export file writes it. */
private fun backupContent(
    title: String,
    notes: String?,
    mode: WireEntryMode,
    ingredients: List<WireIngredient>,
    typed: List<Double?>,
    resolved: List<Double>,
) = BackupContent(
    title = title,
    notes = notes,
    entryMode = if (mode == WireEntryMode.TOTAL_ONLY) EntryMode.TOTAL_ONLY else EntryMode.PER_INGREDIENT,
    ingredients = ingredients.map { IngredientInput(it.name, it.quantity, it.calories, it.protein, it.carbs, it.fat) },
    totalCalories = typed[0],
    totalProtein = typed[1],
    totalCarbs = typed[2],
    totalFat = typed[3],
    resolvedCalories = resolved[0],
    resolvedProtein = resolved[1],
    resolvedCarbs = resolved[2],
    resolvedFat = resolved[3],
)

private fun MealDTO.backupContent() = backupContent(
    title, notes, entryMode, ingredients,
    listOf(totalCalories, totalProtein, totalCarbs, totalFat),
    listOf(resolvedCalories, resolvedProtein, resolvedCarbs, resolvedFat),
)

private fun TemplateDTO.backupContent() = backupContent(
    title, notes, entryMode, ingredients,
    listOf(totalCalories, totalProtein, totalCarbs, totalFat),
    listOf(resolvedCalories, resolvedProtein, resolvedCarbs, resolvedFat),
)
