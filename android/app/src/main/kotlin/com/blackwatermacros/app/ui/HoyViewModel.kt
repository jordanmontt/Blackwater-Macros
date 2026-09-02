package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.formatDateKeyLong
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.MealRequest
import com.blackwatermacros.app.data.ReorderRequest
import com.blackwatermacros.app.data.ResponseErrorMapper
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.WireEntryMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HoyUiState {
    data object Loading : HoyUiState
    data class Loaded(val meals: List<MealDTO>) : HoyUiState
    data class Error(val message: String) : HoyUiState
}

/**
 * Drives the "Comidas/Hoy" screen: holds the selected calendar day and loads
 * that day's meals from `GET /api/meals?from&to`. Mirrors the web `today`
 * page: create/edit/delete meals (`POST`/`PATCH`/`DELETE /api/meals`),
 * drag reorder (`PATCH /api/meals/reorder`), apply templates
 * (`POST /api/meals` from a template), and surface the saved meal templates.
 */
class HoyViewModel : ViewModel() {

    private val _day = MutableStateFlow(todayKey())
    val day: StateFlow<String> = _day.asStateFlow()

    private val _state = MutableStateFlow<HoyUiState>(HoyUiState.Loading)
    val state: StateFlow<HoyUiState> = _state.asStateFlow()

    private val _templates = MutableStateFlow<List<TemplateDTO>>(emptyList())
    val templates: StateFlow<List<TemplateDTO>> = _templates.asStateFlow()

    private val api = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = com.blackwatermacros.app.data.SessionManager::tokenProvider,
    )

    init {
        load()
        loadTemplates()
    }

    /** ISO date caption for the day navigator, e.g. "martes, 2 de septiembre". */
    fun dayCaption(): String = formatDateKeyLong(_day.value)

    fun prevDay() = shiftDay(-1)

    fun nextDay() = shiftDay(1)

    fun goToday() {
        if (_day.value == todayKey()) return
        _day.value = todayKey()
        load()
    }

    fun refresh() {
        load()
        loadTemplates()
    }

    private fun shiftDay(delta: Int) {
        _day.value = addDaysToKey(_day.value, delta)
        load()
    }

    fun load() {
        _state.value = HoyUiState.Loading
        val day = _day.value
        viewModelScope.launch {
            try {
                val res = api.listMeals(from = day, to = day)
                _state.value = HoyUiState.Loaded(res.meals)
            } catch (t: Throwable) {
                _state.value = HoyUiState.Error(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            try {
                _templates.value = api.listTemplates().templates
            } catch (_: Throwable) {
                // Templates row hides gracefully if the fetch fails (web keeps []).
                _templates.value = emptyList()
            }
        }
    }

    /** Optimistically reorders the visible list and persists via the reorder endpoint. */
    fun reorder(meals: List<MealDTO>) {
        val ids = meals.map { it.id }
        _state.value = HoyUiState.Loaded(meals)
        viewModelScope.launch {
            try {
                api.reorderMeals(ReorderRequest(orderedIds = ids))
            } catch (_: Throwable) {
                load()
            }
        }
    }

    fun createMeal(request: MealRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val created = api.createMeal(request).meal
                _state.value = _state.value.let { current ->
                    if (current is HoyUiState.Loaded) {
                        HoyUiState.Loaded(current.meals + created)
                    } else {
                        current
                    }
                }
                onDone(true)
            } catch (t: Throwable) {
                onDone(false)
            }
        }
    }

    fun updateMeal(mealId: String, request: MealRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val updated = api.updateMeal(mealId, request).meal
                _state.value = _state.value.let { current ->
                    if (current is HoyUiState.Loaded) {
                        HoyUiState.Loaded(
                            current.meals.map { if (it.id == mealId) updated else it },
                        )
                    } else {
                        current
                    }
                }
                onDone(true)
            } catch (t: Throwable) {
                onDone(false)
            }
        }
    }

    fun deleteMeal(mealId: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.deleteMeal(mealId)
                _state.value = _state.value.let { current ->
                    if (current is HoyUiState.Loaded) {
                        HoyUiState.Loaded(current.meals.filter { it.id != mealId })
                    } else {
                        current
                    }
                }
                onDone(true)
            } catch (_: Throwable) {
                onDone(false)
            }
        }
    }

    /** Applies a saved template as a new meal on the current day (web `applyTemplate`). */
    fun applyTemplate(template: TemplateDTO, onResult: (ApplyTemplateResult) -> Unit) {
        val request = MealRequest(
            logDate = _day.value,
            title = template.title,
            notes = template.notes,
            entryMode = template.entryMode,
            ingredients = template.ingredients,
            totalCalories = totalIfOnly(template.entryMode, template.totalCalories),
            totalProtein = totalIfOnly(template.entryMode, template.totalProtein),
            totalCarbs = totalIfOnly(template.entryMode, template.totalCarbs),
            totalFat = totalIfOnly(template.entryMode, template.totalFat),
        )
        viewModelScope.launch {
            try {
                val created = api.createMeal(request).meal
                _state.value = _state.value.let { current ->
                    if (current is HoyUiState.Loaded) {
                        HoyUiState.Loaded(current.meals + created)
                    } else {
                        current
                    }
                }
                onResult(ApplyTemplateResult.Applied(template))
            } catch (t: Throwable) {
                onResult(ApplyTemplateResult.Failed(ResponseErrorMapper.messageFrom(t)))
            }
        }
    }

    private fun totalIfOnly(entryMode: WireEntryMode, value: Double?): Double? =
        if (entryMode == WireEntryMode.TOTAL_ONLY) value else null

    private fun ensureTrailingSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
}

sealed interface ApplyTemplateResult {
    data class Applied(val template: TemplateDTO) : ApplyTemplateResult
    data class Failed(val message: String) : ApplyTemplateResult
}