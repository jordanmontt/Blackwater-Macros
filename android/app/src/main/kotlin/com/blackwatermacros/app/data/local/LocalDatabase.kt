package com.blackwatermacros.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * On-device database — the only data source the UI ever reads. Works the same
 * with or without an account; when logged in, `SyncEngine` mirrors it with the
 * server.
 *
 * Sync bookkeeping on every syncable row:
 * - `id` is generated on the phone (UUID), so offline-created rows never need
 *   an id mapping and re-uploading one is idempotent (`PUT /api/<x>/:id`).
 * - `pending` = changed locally and not uploaded yet.
 * - `deleted` = tombstone kept until the delete reaches the server.
 * - `updatedAt` doubles as the local edit stamp: a push only clears `pending`
 *   if the row was not edited again while the request was in flight.
 */

@Entity(tableName = "meals", indices = [Index("logDate")])
data class MealEntity(
    @PrimaryKey val id: String,
    val logDate: String,
    val title: String,
    val notes: String?,
    val entryMode: String,
    val ingredientsJson: String,
    val totalCalories: Double?,
    val totalProtein: Double?,
    val totalCarbs: Double?,
    val totalFat: Double?,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
    val sortOrder: Int,
    val updatedAt: String,
    val pending: Boolean,
    val deleted: Boolean = false,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val title: String,
    val notes: String?,
    val entryMode: String,
    val ingredientsJson: String,
    val totalCalories: Double?,
    val totalProtein: Double?,
    val totalCarbs: Double?,
    val totalFat: Double?,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
    val updatedAt: String,
    val pending: Boolean,
    val deleted: Boolean = false,
)

@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val id: String,
    /** ISO-8601 UTC instant, same format the server returns. */
    val measuredAt: String,
    val weightKg: Double,
    val bodyFatPct: Double?,
    val note: String?,
    val updatedAt: String,
    val pending: Boolean,
    val deleted: Boolean = false,
)

/** Single-row table (id = 0) holding the calorie profile. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val gender: String?,
    val birthYear: Int?,
    val heightCm: Double?,
    val gymDaysPerWeek: Int?,
    val gymSessionMinutes: Int?,
    val walkingMinutesPerDay: Int?,
    val calorieGoal: String?,
    val pending: Boolean,
)

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE logDate = :day AND deleted = 0 ORDER BY sortOrder, rowid")
    fun observeDay(day: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE deleted = 0 ORDER BY logDate, sortOrder, rowid")
    fun observeAll(): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun get(id: String): MealEntity?

    @Query("SELECT MAX(sortOrder) FROM meals WHERE logDate = :day AND deleted = 0")
    suspend fun maxSortOrder(day: String): Int?

    @Upsert
    suspend fun upsert(entity: MealEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MealEntity>)

    @Query("SELECT * FROM meals WHERE pending = 1")
    suspend fun pending(): List<MealEntity>

    @Query("SELECT id FROM meals WHERE pending = 1")
    suspend fun pendingIds(): List<String>

    @Query("SELECT id FROM meals WHERE pending = 0")
    suspend fun syncedIds(): List<String>

    @Query("UPDATE meals SET pending = 0, updatedAt = :serverUpdatedAt WHERE id = :id AND updatedAt = :sentUpdatedAt")
    suspend fun markSynced(id: String, sentUpdatedAt: String, serverUpdatedAt: String)

    @Query("DELETE FROM meals WHERE id = :id AND updatedAt = :sentUpdatedAt")
    suspend fun purgeIfUnchanged(id: String, sentUpdatedAt: String)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun purge(id: String)

    @Query("DELETE FROM meals WHERE id IN (:ids)")
    suspend fun purgeAll(ids: List<String>)

    @Query("SELECT COUNT(*) FROM meals WHERE deleted = 0")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM meals WHERE pending = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: String): TemplateEntity?

    @Upsert
    suspend fun upsert(entity: TemplateEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TemplateEntity>)

    @Query("SELECT * FROM templates WHERE pending = 1")
    suspend fun pending(): List<TemplateEntity>

    @Query("SELECT id FROM templates WHERE pending = 1")
    suspend fun pendingIds(): List<String>

    @Query("SELECT id FROM templates WHERE pending = 0")
    suspend fun syncedIds(): List<String>

    @Query("UPDATE templates SET pending = 0, updatedAt = :serverUpdatedAt WHERE id = :id AND updatedAt = :sentUpdatedAt")
    suspend fun markSynced(id: String, sentUpdatedAt: String, serverUpdatedAt: String)

    @Query("DELETE FROM templates WHERE id = :id AND updatedAt = :sentUpdatedAt")
    suspend fun purgeIfUnchanged(id: String, sentUpdatedAt: String)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun purge(id: String)

    @Query("DELETE FROM templates WHERE id IN (:ids)")
    suspend fun purgeAll(ids: List<String>)

    @Query("SELECT COUNT(*) FROM templates WHERE deleted = 0")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM templates WHERE pending = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights WHERE deleted = 0 ORDER BY measuredAt")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights WHERE id = :id")
    suspend fun get(id: String): WeightEntity?

    @Upsert
    suspend fun upsert(entity: WeightEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WeightEntity>)

    @Query("SELECT * FROM weights WHERE pending = 1")
    suspend fun pending(): List<WeightEntity>

    @Query("SELECT id FROM weights WHERE pending = 1")
    suspend fun pendingIds(): List<String>

    @Query("SELECT id FROM weights WHERE pending = 0")
    suspend fun syncedIds(): List<String>

    @Query("UPDATE weights SET pending = 0, updatedAt = :serverUpdatedAt WHERE id = :id AND updatedAt = :sentUpdatedAt")
    suspend fun markSynced(id: String, sentUpdatedAt: String, serverUpdatedAt: String)

    @Query("DELETE FROM weights WHERE id = :id AND updatedAt = :sentUpdatedAt")
    suspend fun purgeIfUnchanged(id: String, sentUpdatedAt: String)

    @Query("DELETE FROM weights WHERE id = :id")
    suspend fun purge(id: String)

    @Query("DELETE FROM weights WHERE id IN (:ids)")
    suspend fun purgeAll(ids: List<String>)

    @Query("SELECT COUNT(*) FROM weights WHERE deleted = 0")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM weights WHERE pending = 1")
    fun observePendingCount(): Flow<Int>
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(entity: ProfileEntity)

    @Query("UPDATE profile SET pending = 0 WHERE id = 0")
    suspend fun markSynced()

    @Query("SELECT COUNT(*) FROM profile WHERE pending = 1")
    fun observePendingCount(): Flow<Int>
}

@Database(
    entities = [MealEntity::class, TemplateEntity::class, WeightEntity::class, ProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun meals(): MealDao
    abstract fun templates(): TemplateDao
    abstract fun weights(): WeightDao
    abstract fun profile(): ProfileDao

    companion object {
        fun open(context: Context): LocalDatabase =
            Room.databaseBuilder(context, LocalDatabase::class.java, "blackwater.db").build()
    }
}
