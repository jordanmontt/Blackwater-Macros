package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.MealRequest
import com.blackwatermacros.app.data.ResponseErrorMapper
import com.blackwatermacros.app.data.WireEntryMode
import com.blackwatermacros.app.data.WireIngredient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Result of submitting the meal form. */
sealed interface AddMealResult {
    data object Saved : AddMealResult
    data class Failed(val message: String) : AddMealResult
}

/**
 * Parses and submits a meal (`POST /api/meals` when creating, `PATCH
 * /api/meals/{id}` when editing). Supports both entry modes:
 * per-ingredient nutrition and total-only. Mirrors the web `MealForm`.
 */
class AddMealViewModel(
    private val logDate: String,
    private val mealId: String? = null,
) : ViewModel() {

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val api = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = com.blackwatermacros.app.data.SessionManager::tokenProvider,
    )

    /** @return null on success, or a Spanish error message on failure/validation error. */
    fun submit(
        title: String,
        notes: String,
        entryMode: WireEntryMode,
        ingredients: List<WireIngredient>,
        totalCalories: Double,
        totalProtein: Double,
        totalCarbs: Double,
        totalFat: Double,
        onResult: (AddMealResult) -> Unit,
    ) {
        if (_saving.value) return
        if (title.isBlank()) {
            onResult(AddMealResult.Failed("El título es obligatorio."))
            return
        }
        if (entryMode == WireEntryMode.PER_INGREDIENT && ingredients.isEmpty()) {
            onResult(AddMealResult.Failed("Ingredientes"))
            return
        }
        val request = MealRequest(
            logDate = logDate,
            title = title.trim(),
            notes = notes.trim().ifBlank { null },
            entryMode = entryMode,
            ingredients = ingredients,
            totalCalories = if (entryMode == WireEntryMode.TOTAL_ONLY) totalCalories else null,
            totalProtein = if (entryMode == WireEntryMode.TOTAL_ONLY) totalProtein else null,
            totalCarbs = if (entryMode == WireEntryMode.TOTAL_ONLY) totalCarbs else null,
            totalFat = if (entryMode == WireEntryMode.TOTAL_ONLY) totalFat else null,
        )
        _saving.value = true
        viewModelScope.launch {
            val result = try {
                if (mealId != null) api.updateMeal(mealId, request) else api.createMeal(request)
                _saving.value = false
                AddMealResult.Saved
            } catch (t: Throwable) {
                _saving.value = false
                AddMealResult.Failed(ResponseErrorMapper.messageFrom(t))
            }
            onResult(result)
        }
    }

    private fun ensureTrailingSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
}