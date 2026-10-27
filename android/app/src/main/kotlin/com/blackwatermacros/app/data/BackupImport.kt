package com.blackwatermacros.app.data

import com.blackwatermacros.app.core.BACKUP_APP
import com.blackwatermacros.app.core.BACKUP_FORMAT
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProfilePeriod
import com.blackwatermacros.app.core.isValidDateKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.OffsetDateTime

/** What an export file holds, ready to replace the phone's data (`AppRepository.replaceAll`). */
data class BackupContents(
    val profile: CalorieProfile,
    val profileHistory: List<ProfilePeriod>,
    val weights: List<WeightRequest>,
    val meals: List<MealRequest>,
    val templates: List<TemplateRequest>,
) {
    /** First and last day with a meal or a weigh-in, for the confirmation; null when there is none. */
    val firstDay: String? get() = (meals.map { it.logDate } + weights.map { it.measuredAt.take(10) }).minOrNull()
    val lastDay: String? get() = (meals.map { it.logDate } + weights.map { it.measuredAt.take(10) }).maxOrNull()
}

/**
 * Reads the export file (`core/Backup.kt`, web `core/backup.ts`) back. All or nothing:
 * any value the server would refuse (same limits as `src/server/validation.ts`) rejects
 * the whole file, so an import never leaves half a file or data a later login
 * couldn't upload. Only used without an account (docs/TECHNICAL.md §5.11).
 */
object BackupImport {

    sealed interface Result {
        data class Ok(val contents: BackupContents) : Result

        /** Not JSON, or not a Blackwater Macros export. */
        data object NotBackup : Result

        /** Made by a newer version of the app (a higher `format`). */
        data object NewerFormat : Result

        /** A missing or wrong value; [where] names it, e.g. «meals › 13 › foods › 1 › kcal». */
        data class Invalid(val where: String) : Result
    }

    private class Bad(val where: String) : Exception(where)

    fun parse(text: String): Result {
        val root = runCatching { Json.parseToJsonElement(text.removePrefix("\uFEFF")) }.getOrNull() as? JsonObject
            ?: return Result.NotBackup
        if ((root["app"] as? JsonPrimitive)?.takeIf { it.isString }?.content != BACKUP_APP) return Result.NotBackup
        val format = (root["format"] as? JsonPrimitive)?.doubleOrNull ?: return Result.NotBackup
        if (format > BACKUP_FORMAT) return Result.NewerFormat
        if (format != BACKUP_FORMAT.toDouble()) return Result.NotBackup
        return try {
            Result.Ok(contents(Node(root, emptyList())))
        } catch (e: Bad) {
            Result.Invalid(e.where)
        }
    }

    private fun contents(root: Node): BackupContents = BackupContents(
        profile = profile(root.obj("profile")),
        profileHistory = root.list("pastProfiles", max = 1000).map { period ->
            ProfilePeriod(period.date("until"), profile(period))
        },
        weights = root.list("weighIns", max = 100_000).map(::weight),
        meals = root.list("meals", max = 100_000).map { meal ->
            val content = content(meal)
            MealRequest(
                logDate = meal.date("date"),
                title = content.title,
                notes = content.notes,
                entryMode = content.mode,
                ingredients = content.foods,
                totalCalories = content.total[0],
                totalProtein = content.total[1],
                totalCarbs = content.total[2],
                totalFat = content.total[3],
            )
        },
        templates = root.list("templates", max = 10_000).map { template ->
            val content = content(template)
            TemplateRequest(
                name = template.text("name", max = 120),
                title = content.title,
                notes = content.notes,
                entryMode = content.mode,
                ingredients = content.foods,
                totalCalories = content.total[0],
                totalProtein = content.total[1],
                totalCarbs = content.total[2],
                totalFat = content.total[3],
            )
        },
    )

    private fun profile(node: Node) = CalorieProfile(
        gender = node.choice("sex", mapOf("male" to Gender.MALE, "female" to Gender.FEMALE)),
        birthYear = node.int("birthYear", 1920, 2010),
        heightCm = node.number("heightCm", 100.0, 250.0),
        gymDaysPerWeek = node.int("gymDaysPerWeek", 0, 7),
        gymSessionMinutes = node.int("gymMinutesPerSession", 0, 300),
        walkingMinutesPerDay = node.int("walkingMinutesPerDay", 0, 480),
        calorieGoal = node.choice("goal", mapOf("cut" to Goal.CUT, "maintain" to Goal.MAINTAIN, "bulk" to Goal.SURPLUS)),
    )

    private fun weight(node: Node): WeightRequest {
        val time = node.text("time", max = 64)
        val valid = runCatching { Instant.parse(time) }.isSuccess || runCatching { OffsetDateTime.parse(time) }.isSuccess
        if (!valid) throw Bad(node.path("time"))
        return WeightRequest(
            measuredAt = time,
            weightKg = node.number("kg", 20.0, 400.0) ?: throw Bad(node.path("kg")),
            bodyFatPct = node.number("bodyFatPct", 3.0, 60.0),
            note = node.optionalText("note", max = 500),
        )
    }

    private class Content(
        val title: String,
        val notes: String?,
        val mode: WireEntryMode,
        val foods: List<WireIngredient>,
        /** kcal, protein, carbs, fat: the typed total, only for «total». */
        val total: List<Double?>,
    )

    private fun content(node: Node): Content {
        val mode = node.choice("mode", mapOf("foods" to WireEntryMode.PER_INGREDIENT, "total" to WireEntryMode.TOTAL_ONLY))
            ?: throw Bad(node.path("mode"))
        val foods = node.list("foods", max = 100).map { food ->
            WireIngredient(
                name = food.text("name", max = 200),
                quantity = food.optionalText("amount", max = 200),
                calories = food.number("kcal", 0.0, 100_000.0),
                protein = food.number("protein", 0.0, 10_000.0),
                carbs = food.number("carbs", 0.0, 10_000.0),
                fat = food.number("fat", 0.0, 10_000.0),
            )
        }
        // «foods»: the total is the sum of the foods, recomputed when saved; the file's is only to read.
        val total = if (mode == WireEntryMode.TOTAL_ONLY) {
            val typed = node.obj("total")
            listOf(
                typed.number("kcal", 0.0, 100_000.0),
                typed.number("protein", 0.0, 10_000.0),
                typed.number("carbs", 0.0, 10_000.0),
                typed.number("fat", 0.0, 10_000.0),
            )
        } else {
            listOf(null, null, null, null)
        }
        return Content(node.text("title", max = 120), node.optionalText("notes", max = 2_000), mode, foods, total)
    }

    /** A JSON object and where it is in the file, for the error message (1-based positions). */
    private class Node(val json: JsonObject, val at: List<String>) {
        fun path(key: String) = (at + key).joinToString(" › ")

        private fun value(key: String): JsonElement? = json[key]?.takeUnless { it is JsonNull }

        fun obj(key: String): Node = Node(value(key) as? JsonObject ?: throw Bad(path(key)), at + key)

        fun list(key: String, max: Int): List<Node> {
            val array = value(key) as? JsonArray ?: throw Bad(path(key))
            if (array.size > max) throw Bad(path(key))
            return array.mapIndexed { i, item -> Node(item as? JsonObject ?: throw Bad(path(key) + " › ${i + 1}"), at + key + "${i + 1}") }
        }

        private fun string(key: String): String? {
            val primitive = value(key) ?: return null
            return (primitive as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw Bad(path(key))
        }

        /** Required, trimmed, 1..[max] characters (as the server stores it). */
        fun text(key: String, max: Int): String =
            string(key)?.trim()?.takeIf { it.isNotEmpty() && it.length <= max } ?: throw Bad(path(key))

        /** Optional: absent, null or blank is null; else trimmed, at most [max] characters. */
        fun optionalText(key: String, max: Int): String? {
            val text = string(key)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (text.length > max) throw Bad(path(key))
            return text
        }

        fun date(key: String): String = text(key, max = 10).takeIf(::isValidDateKey) ?: throw Bad(path(key))

        fun number(key: String, min: Double, max: Double): Double? {
            val primitive = value(key) ?: return null
            val number = (primitive as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: throw Bad(path(key))
            if (number.isNaN() || number < min || number > max) throw Bad(path(key))
            return number
        }

        fun int(key: String, min: Int, max: Int): Int? {
            val number = number(key, min.toDouble(), max.toDouble()) ?: return null
            if (number % 1.0 != 0.0) throw Bad(path(key))
            return number.toInt()
        }

        fun <T> choice(key: String, options: Map<String, T>): T? {
            val name = string(key) ?: return null
            return options[name] ?: throw Bad(path(key))
        }
    }
}
