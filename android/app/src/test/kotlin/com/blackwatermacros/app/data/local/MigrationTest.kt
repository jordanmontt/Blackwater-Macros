package com.blackwatermacros.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Updating the app keeps everything on the phone: a database written by an older
 * version (built from its exported schema in `app/schemas/`, as Room created it)
 * opens with today's code through the migrations, never by wiping it. Without an
 * account the phone holds the only copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Creates the database exactly as version [version] did, from its exported schema. */
    private fun createVersion(version: Int, name: String, seed: (SQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(
            File("schemas/com.blackwatermacros.app.data.local.LocalDatabase/$version.json").readText(),
        ).jsonObject["database"]!!.jsonObject
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (entity in schema["entities"]!!.jsonArray) {
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                db.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            for (query in schema["setupQueries"]!!.jsonArray) db.execSQL(query.jsonPrimitive.content)
            db.version = version
            seed(db)
        }
    }

    @Test
    fun `version 1 data survives the update and the profile gets an empty history`() = runBlocking<Unit> {
        val name = "migration-v1.db"
        createVersion(1, name) { db ->
            db.execSQL(
                "INSERT INTO profile (id, gender, birthYear, heightCm, gymDaysPerWeek, gymSessionMinutes, " +
                    "walkingMinutesPerDay, calorieGoal, pending) VALUES (0, 'FEMALE', 1995, 165.0, 2, 60, 20, 'CUT', 1)",
            )
            db.execSQL(
                "INSERT INTO weights (id, measuredAt, weightKg, bodyFatPct, note, updatedAt, pending, deleted) " +
                    "VALUES ('w1', '2026-10-10T12:00:00Z', 61.0, NULL, NULL, '2026-10-10T12:00:00Z', 0, 0)",
            )
        }

        val db = Room.databaseBuilder(context, LocalDatabase::class.java, name)
            .addMigrations(*LocalDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val profile = db.profile().get()
            assertThat(profile.toCore().gender).isEqualTo(Gender.FEMALE)
            assertThat(profile.toCore().calorieGoal).isEqualTo(Goal.CUT)
            assertThat(profile!!.pending).isTrue()
            assertThat(profile.history()).isEmpty()
            assertThat(db.weights().count()).isEqualTo(1)
        } finally {
            db.close()
        }
    }
}
