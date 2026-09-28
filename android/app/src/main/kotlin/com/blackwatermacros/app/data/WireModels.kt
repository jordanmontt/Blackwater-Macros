package com.blackwatermacros.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs mirroring `docs/api.md`. JSON uses camelCase for field names
 * (matches Kotlin properties by default) and `.` as the decimal separator.
 * A dedicated `Json` instance is configured for these models. Some `:core` enums
 * are re-declared here with `@SerialName` because their wire strings differ from
 * the internal camelCase enum entries.
 */

// --- Enums (wire strings) ---

@Serializable
enum class WireEntryMode {
    @SerialName("per_ingredient")
    PER_INGREDIENT,

    @SerialName("total_only")
    TOTAL_ONLY,
}

@Serializable
enum class WireGender {
    @SerialName("male")
    MALE,

    @SerialName("female")
    FEMALE,
}

@Serializable
enum class WireGoal {
    @SerialName("cut")
    CUT,

    @SerialName("maintain")
    MAINTAIN,

    @SerialName("surplus")
    SURPLUS,
}

// --- Auth ---

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(
    val ok: Boolean,
    val token: String,
    val expiresAt: String,
)

@Serializable
data class SessionResponse(
    val username: String,
    val isAdmin: Boolean,
    val calorieProfile: WireCalorieProfile,
)

@Serializable
data class OkResponse(val ok: Boolean)

// --- Meals ---

@Serializable
data class WireIngredient(
    val name: String,
    val quantity: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

@Serializable
data class MealRequest(
    val logDate: String,
    val title: String,
    val notes: String? = null,
    val entryMode: WireEntryMode,
    val ingredients: List<WireIngredient> = emptyList(),
    val totalCalories: Double? = null,
    val totalProtein: Double? = null,
    val totalCarbs: Double? = null,
    val totalFat: Double? = null,
    /** Position within the day; only sent by the offline-sync `PUT`. */
    val sortOrder: Int? = null,
)

@Serializable
data class MealDTO(
    val id: String,
    val logDate: String,
    val title: String,
    val notes: String?,
    val entryMode: WireEntryMode,
    val ingredients: List<WireIngredient>,
    val totalCalories: Double?,
    val totalProtein: Double?,
    val totalCarbs: Double?,
    val totalFat: Double?,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
    val updatedAt: String,
    val sortOrder: Int = 0,
)

@Serializable
data class MealsResponse(val meals: List<MealDTO>)

@Serializable
data class MealResponse(val meal: MealDTO)

// --- Templates ---

@Serializable
data class TemplateRequest(
    val name: String,
    val title: String,
    val notes: String? = null,
    val entryMode: WireEntryMode,
    val ingredients: List<WireIngredient> = emptyList(),
    val totalCalories: Double? = null,
    val totalProtein: Double? = null,
    val totalCarbs: Double? = null,
    val totalFat: Double? = null,
)

@Serializable
data class TemplateDTO(
    val id: String,
    val name: String,
    val title: String,
    val notes: String?,
    val entryMode: WireEntryMode,
    val ingredients: List<WireIngredient>,
    val totalCalories: Double?,
    val totalProtein: Double?,
    val totalCarbs: Double?,
    val totalFat: Double?,
    val resolvedCalories: Double,
    val resolvedProtein: Double,
    val resolvedCarbs: Double,
    val resolvedFat: Double,
    val updatedAt: String,
)

@Serializable
data class TemplatesResponse(val templates: List<TemplateDTO>)

@Serializable
data class TemplateResponse(val template: TemplateDTO)

// --- Weights ---

@Serializable
data class WeightRequest(
    val measuredAt: String,
    val weightKg: Double,
    val bodyFatPct: Double? = null,
    val note: String? = null,
)

@Serializable
data class WeightDTO(
    val id: String,
    val measuredAt: String,
    val weightKg: Double,
    val bodyFatPct: Double?,
    val note: String?,
    val updatedAt: String,
)

@Serializable
data class WeightsResponse(val weights: List<WeightDTO>)

@Serializable
data class WeightResponse(val weight: WeightDTO)

// --- Settings ---

@Serializable
data class WireCalorieProfile(
    val gender: WireGender? = null,
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    val gymDaysPerWeek: Int? = null,
    val gymSessionMinutes: Int? = null,
    val walkingMinutesPerDay: Int? = null,
    val calorieGoal: WireGoal? = null,
)

@Serializable
data class SettingsResponse(val calorieProfile: WireCalorieProfile)

// --- Admin ---

@Serializable
data class AdminUserDTO(
    val id: String,
    val username: String,
    val isAdmin: Boolean,
    val createdAt: String,
)

@Serializable
data class UsersResponse(val users: List<AdminUserDTO>)

@Serializable
data class UserResponse(val user: AdminUserDTO)

@Serializable
data class AdminCreateUserRequest(val username: String, val password: String)

@Serializable
data class AdminUpdateUserRequest(
    val username: String? = null,
    val password: String? = null,
    val isAdmin: Boolean? = null,
)

// --- Error envelope ---

@Serializable
data class ApiError(val error: String? = null, val code: String? = null)