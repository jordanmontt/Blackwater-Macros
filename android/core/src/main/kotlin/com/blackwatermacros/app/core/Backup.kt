package com.blackwatermacros.app.core

/**
 * Mirrors `src/lib/core/backup.ts`: the export file, indented JSON with plain English
 * names. [buildBackup] writes exactly the web's text (`JSON.stringify(file, null, 2)`);
 * the app reads it back with `data/BackupImport.kt`.
 */
const val BACKUP_APP = "Blackwater Macros"
const val BACKUP_FORMAT = 1

data class BackupWeight(val measuredAt: String, val weightKg: Double, val bodyFatPct: Double?, val note: String?)

/** A meal's or a template's content, with the totals the app counts (`resolveMealTotals`). */
data class BackupContent(
    val title: String,
    val notes: String?,
    val entryMode: EntryMode,
    val ingredients: List<IngredientInput>,
    val totalCalories: Double?,
    val totalProtein: Double?,
    val totalCarbs: Double?,
    val totalFat: Double?,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
)

data class BackupMeal(val logDate: String, val content: BackupContent)

data class BackupTemplate(val name: String, val content: BackupContent)

/** What goes in the file. Meals in day order (date, then their order in the day), weigh-ins oldest first. */
data class BackupData(
    val profile: CalorieProfile,
    val profileHistory: List<ProfilePeriod>,
    val weights: List<BackupWeight>,
    val meals: List<BackupMeal>,
    val templates: List<BackupTemplate>,
)

/** The file's names for the profile's values (web `BACKUP_SEX`, `BACKUP_GOAL`, `BACKUP_MODE`). */
fun backupName(gender: Gender): String = if (gender == Gender.MALE) "male" else "female"

fun backupName(goal: Goal): String = when (goal) {
    Goal.CUT -> "cut"
    Goal.MAINTAIN -> "maintain"
    Goal.SURPLUS -> "bulk"
}

fun backupName(mode: EntryMode): String = if (mode == EntryMode.PER_INGREDIENT) "foods" else "total"

private fun profileJson(profile: CalorieProfile): Map<String, Any?> = linkedMapOf(
    "sex" to profile.gender?.let(::backupName),
    "birthYear" to profile.birthYear,
    "heightCm" to profile.heightCm,
    "gymDaysPerWeek" to profile.gymDaysPerWeek,
    "gymMinutesPerSession" to profile.gymSessionMinutes,
    "walkingMinutesPerDay" to profile.walkingMinutesPerDay,
    "goal" to profile.calorieGoal?.let(::backupName),
)

/** A food with only the values it has. */
private fun foodJson(food: IngredientInput): Map<String, Any?> = buildMap {
    put("name", food.name)
    food.quantity?.let { put("amount", it) }
    food.calories?.let { put("kcal", it) }
    food.protein?.let { put("protein", it) }
    food.carbs?.let { put("carbs", it) }
    food.fat?.let { put("fat", it) }
}

private fun contentJson(content: BackupContent): Map<String, Any?> {
    val typed = content.entryMode == EntryMode.TOTAL_ONLY
    return linkedMapOf(
        "title" to content.title,
        "notes" to content.notes,
        "mode" to backupName(content.entryMode),
        "foods" to content.ingredients.map(::foodJson),
        "total" to linkedMapOf(
            "kcal" to if (typed) content.totalCalories else content.resolvedCalories,
            "protein" to if (typed) content.totalProtein else content.resolvedProtein,
            "carbs" to if (typed) content.totalCarbs else content.resolvedCarbs,
            "fat" to if (typed) content.totalFat else content.resolvedFat,
        ),
    )
}

fun buildBackup(data: BackupData, exportedAt: String): String {
    val file = linkedMapOf(
        "app" to BACKUP_APP,
        "format" to BACKUP_FORMAT,
        "exportedAt" to exportedAt,
        "profile" to profileJson(data.profile),
        "pastProfiles" to data.profileHistory.map { linkedMapOf<String, Any?>("until" to it.until) + profileJson(it.profile) },
        "weighIns" to data.weights.map {
            linkedMapOf("time" to it.measuredAt, "kg" to it.weightKg, "bodyFatPct" to it.bodyFatPct, "note" to it.note)
        },
        "meals" to data.meals.map { linkedMapOf<String, Any?>("date" to it.logDate) + contentJson(it.content) },
        "templates" to data.templates.map { linkedMapOf<String, Any?>("name" to it.name) + contentJson(it.content) },
    )
    return StringBuilder().also { writeJson(file, "", it) }.append('\n').toString()
}

/** «blackwater-macros-2026-10-26.json» */
fun backupFileName(day: String): String = "blackwater-macros-$day.json"

/** `JSON.stringify(value, null, 2)` for maps, lists, strings, numbers and null. */
private fun writeJson(value: Any?, indent: String, out: StringBuilder) {
    when (value) {
        null -> out.append("null")
        is String -> writeString(value, out)
        is Boolean -> out.append(value)
        is Int -> out.append(value)
        is Long -> out.append(value)
        is Double -> out.append(plainNumber(value))
        is Map<*, *> -> writeBlock(value.entries.toList(), '{', '}', indent, out) { entry, inner ->
            writeString(entry.key as String, out)
            out.append(": ")
            writeJson(entry.value, inner, out)
        }
        is List<*> -> writeBlock(value, '[', ']', indent, out) { item, inner -> writeJson(item, inner, out) }
        else -> error("Not a JSON value: $value")
    }
}

private fun <T> writeBlock(
    items: List<T>,
    open: Char,
    close: Char,
    indent: String,
    out: StringBuilder,
    writeItem: (T, String) -> Unit,
) {
    if (items.isEmpty()) {
        out.append(open).append(close)
        return
    }
    val inner = "$indent  "
    out.append(open).append('\n')
    items.forEachIndexed { i, item ->
        if (i > 0) out.append(",\n")
        out.append(inner)
        writeItem(item, inner)
    }
    out.append('\n').append(indent).append(close)
}

/** Escapes as JSON.stringify does: quote, backslash and control characters; the rest as is. */
private fun writeString(text: String, out: StringBuilder) {
    out.append('"')
    for (c in text) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
    }
    out.append('"')
}
