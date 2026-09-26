package com.blackwatermacros.app.data.local

import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.EntryMode
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.IngredientInput
import com.blackwatermacros.app.core.resolveMealTotals
import com.blackwatermacros.app.data.ApiJson
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.MealRequest
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.TemplateRequest
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.WeightRequest
import com.blackwatermacros.app.data.WireCalorieProfile
import com.blackwatermacros.app.data.WireEntryMode
import com.blackwatermacros.app.data.WireGender
import com.blackwatermacros.app.data.WireGoal
import com.blackwatermacros.app.data.WireIngredient
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Conversions between wire DTOs (what the UI and server speak) and Room rows. */

private val ingredientsSerializer = ListSerializer(WireIngredient.serializer())

private val IsoUtcMillis: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** ISO-8601 UTC with millis — the exact shape the server's `toISOString()` returns. */
fun isoUtc(instant: Instant): String = IsoUtcMillis.format(instant)

private var lastStamp: Instant = Instant.EPOCH

/**
 * Local edit stamp. Strictly increasing within the process, so two edits in the
 * same millisecond still differ — the sync engine relies on that to notice an
 * edit made while the previous version was being uploaded.
 */
@Synchronized
fun nowIso(): String {
    val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
    lastStamp = if (now > lastStamp) now else lastStamp.plusMillis(1)
    return isoUtc(lastStamp)
}

/**
 * The weight form sends a local wall-clock time ("2026-06-15T08:00"); anything
 * with an offset/Z is already absolute. Stored as a UTC instant.
 */
fun normalizeMeasuredAt(value: String, zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: LocalDateTime.parse(value).atZone(zone).toInstant()
    return isoUtc(instant)
}

private fun encodeIngredients(list: List<WireIngredient>): String =
    ApiJson.encodeToString(ingredientsSerializer, list)

private fun decodeIngredients(json: String): List<WireIngredient> =
    ApiJson.decodeFromString(ingredientsSerializer, json)

private fun WireEntryMode.toCore(): EntryMode = when (this) {
    WireEntryMode.PER_INGREDIENT -> EntryMode.PER_INGREDIENT
    WireEntryMode.TOTAL_ONLY -> EntryMode.TOTAL_ONLY
}

private fun WireIngredient.toCore() = IngredientInput(name, quantity, calories, protein, carbs, fat)

private fun totalIfOnly(mode: WireEntryMode, value: Double?): Double? =
    if (mode == WireEntryMode.TOTAL_ONLY) value else null

// --- Meals ---

/** A meal created/edited on the phone: totals resolved with `:core`, same as the server. */
fun MealRequest.toEntity(id: String, sortOrder: Int, updatedAt: String): MealEntity {
    val totals = resolveMealTotals(
        entryMode.toCore(),
        ingredients.map { it.toCore() },
        totalCalories,
        totalProtein,
        totalCarbs,
        totalFat,
    )
    return MealEntity(
        id = id,
        logDate = logDate,
        title = title,
        notes = notes,
        entryMode = entryMode.name,
        ingredientsJson = encodeIngredients(ingredients),
        totalCalories = totalIfOnly(entryMode, totalCalories),
        totalProtein = totalIfOnly(entryMode, totalProtein),
        totalCarbs = totalIfOnly(entryMode, totalCarbs),
        totalFat = totalIfOnly(entryMode, totalFat),
        resolvedCalories = totals.calories,
        resolvedProtein = totals.protein,
        resolvedCarbs = totals.carbs,
        resolvedFat = totals.fat,
        sortOrder = sortOrder,
        updatedAt = updatedAt,
        pending = true,
    )
}

fun MealDTO.toEntity(): MealEntity = MealEntity(
    id = id,
    logDate = logDate,
    title = title,
    notes = notes,
    entryMode = entryMode.name,
    ingredientsJson = encodeIngredients(ingredients),
    totalCalories = totalCalories,
    totalProtein = totalProtein,
    totalCarbs = totalCarbs,
    totalFat = totalFat,
    resolvedCalories = resolvedCalories,
    resolvedProtein = resolvedProtein,
    resolvedCarbs = resolvedCarbs,
    resolvedFat = resolvedFat,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
    pending = false,
)

fun MealEntity.toDto(): MealDTO = MealDTO(
    id = id,
    logDate = logDate,
    title = title,
    notes = notes,
    entryMode = WireEntryMode.valueOf(entryMode),
    ingredients = decodeIngredients(ingredientsJson),
    totalCalories = totalCalories,
    totalProtein = totalProtein,
    totalCarbs = totalCarbs,
    totalFat = totalFat,
    resolvedCalories = resolvedCalories,
    resolvedProtein = resolvedProtein,
    resolvedCarbs = resolvedCarbs,
    resolvedFat = resolvedFat,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
)

fun MealEntity.toRequest(): MealRequest = MealRequest(
    logDate = logDate,
    title = title,
    notes = notes,
    entryMode = WireEntryMode.valueOf(entryMode),
    ingredients = decodeIngredients(ingredientsJson),
    totalCalories = totalCalories,
    totalProtein = totalProtein,
    totalCarbs = totalCarbs,
    totalFat = totalFat,
    sortOrder = sortOrder,
)

// --- Templates ---

fun TemplateRequest.toEntity(id: String, updatedAt: String): TemplateEntity {
    val totals = resolveMealTotals(
        entryMode.toCore(),
        ingredients.map { it.toCore() },
        totalCalories,
        totalProtein,
        totalCarbs,
        totalFat,
    )
    return TemplateEntity(
        id = id,
        name = name,
        title = title,
        notes = notes,
        entryMode = entryMode.name,
        ingredientsJson = encodeIngredients(ingredients),
        totalCalories = totalIfOnly(entryMode, totalCalories),
        totalProtein = totalIfOnly(entryMode, totalProtein),
        totalCarbs = totalIfOnly(entryMode, totalCarbs),
        totalFat = totalIfOnly(entryMode, totalFat),
        resolvedCalories = totals.calories,
        resolvedProtein = totals.protein,
        resolvedCarbs = totals.carbs,
        resolvedFat = totals.fat,
        updatedAt = updatedAt,
        pending = true,
    )
}

fun TemplateDTO.toEntity(): TemplateEntity = TemplateEntity(
    id = id,
    name = name,
    title = title,
    notes = notes,
    entryMode = entryMode.name,
    ingredientsJson = encodeIngredients(ingredients),
    totalCalories = totalCalories,
    totalProtein = totalProtein,
    totalCarbs = totalCarbs,
    totalFat = totalFat,
    resolvedCalories = resolvedCalories,
    resolvedProtein = resolvedProtein,
    resolvedCarbs = resolvedCarbs,
    resolvedFat = resolvedFat,
    updatedAt = updatedAt,
    pending = false,
)

fun TemplateEntity.toDto(): TemplateDTO = TemplateDTO(
    id = id,
    name = name,
    title = title,
    notes = notes,
    entryMode = WireEntryMode.valueOf(entryMode),
    ingredients = decodeIngredients(ingredientsJson),
    totalCalories = totalCalories,
    totalProtein = totalProtein,
    totalCarbs = totalCarbs,
    totalFat = totalFat,
    resolvedCalories = resolvedCalories,
    resolvedProtein = resolvedProtein,
    resolvedCarbs = resolvedCarbs,
    resolvedFat = resolvedFat,
    updatedAt = updatedAt,
)

fun TemplateEntity.toRequest(): TemplateRequest = TemplateRequest(
    name = name,
    title = title,
    notes = notes,
    entryMode = WireEntryMode.valueOf(entryMode),
    ingredients = decodeIngredients(ingredientsJson),
    totalCalories = totalCalories,
    totalProtein = totalProtein,
    totalCarbs = totalCarbs,
    totalFat = totalFat,
)

// --- Weights ---

fun WeightRequest.toEntity(id: String, updatedAt: String): WeightEntity = WeightEntity(
    id = id,
    measuredAt = normalizeMeasuredAt(measuredAt),
    weightKg = weightKg,
    bodyFatPct = bodyFatPct,
    note = note,
    updatedAt = updatedAt,
    pending = true,
)

fun WeightDTO.toEntity(): WeightEntity = WeightEntity(
    id = id,
    measuredAt = measuredAt,
    weightKg = weightKg,
    bodyFatPct = bodyFatPct,
    note = note,
    updatedAt = updatedAt,
    pending = false,
)

fun WeightEntity.toDto(): WeightDTO = WeightDTO(
    id = id,
    measuredAt = measuredAt,
    weightKg = weightKg,
    bodyFatPct = bodyFatPct,
    note = note,
    updatedAt = updatedAt,
)

fun WeightEntity.toRequest(): WeightRequest = WeightRequest(
    measuredAt = measuredAt,
    weightKg = weightKg,
    bodyFatPct = bodyFatPct,
    note = note,
)

// --- Profile ---

val EmptyProfile = CalorieProfile(null, null, null, null, null, null, null)

fun ProfileEntity?.toCore(): CalorieProfile = if (this == null) EmptyProfile else CalorieProfile(
    gender = gender?.let { Gender.valueOf(it) },
    birthYear = birthYear,
    heightCm = heightCm,
    gymDaysPerWeek = gymDaysPerWeek,
    gymSessionMinutes = gymSessionMinutes,
    walkingMinutesPerDay = walkingMinutesPerDay,
    calorieGoal = calorieGoal?.let { Goal.valueOf(it) },
)

fun CalorieProfile.toEntity(pending: Boolean): ProfileEntity = ProfileEntity(
    gender = gender?.name,
    birthYear = birthYear,
    heightCm = heightCm,
    gymDaysPerWeek = gymDaysPerWeek,
    gymSessionMinutes = gymSessionMinutes,
    walkingMinutesPerDay = walkingMinutesPerDay,
    calorieGoal = calorieGoal?.name,
    pending = pending,
)

fun WireCalorieProfile.toCore(): CalorieProfile = CalorieProfile(
    gender = when (gender) {
        WireGender.MALE -> Gender.MALE
        WireGender.FEMALE -> Gender.FEMALE
        null -> null
    },
    birthYear = birthYear,
    heightCm = heightCm,
    gymDaysPerWeek = gymDaysPerWeek,
    gymSessionMinutes = gymSessionMinutes,
    walkingMinutesPerDay = walkingMinutesPerDay,
    calorieGoal = when (calorieGoal) {
        WireGoal.CUT -> Goal.CUT
        WireGoal.MAINTAIN -> Goal.MAINTAIN
        WireGoal.SURPLUS -> Goal.SURPLUS
        null -> null
    },
)

fun CalorieProfile.toWire(): WireCalorieProfile = WireCalorieProfile(
    gender = when (gender) {
        Gender.MALE -> WireGender.MALE
        Gender.FEMALE -> WireGender.FEMALE
        null -> null
    },
    birthYear = birthYear,
    heightCm = heightCm,
    gymDaysPerWeek = gymDaysPerWeek,
    gymSessionMinutes = gymSessionMinutes,
    walkingMinutesPerDay = walkingMinutesPerDay,
    calorieGoal = when (calorieGoal) {
        Goal.CUT -> WireGoal.CUT
        Goal.MAINTAIN -> WireGoal.MAINTAIN
        Goal.SURPLUS -> WireGoal.SURPLUS
        null -> null
    },
)
