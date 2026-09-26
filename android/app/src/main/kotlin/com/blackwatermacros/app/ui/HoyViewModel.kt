package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.MealRequest
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.WireEntryMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HoyUiState {
    data object Loading : HoyUiState
    data class Loaded(val meals: List<MealDTO>) : HoyUiState
}

/**
 * Drives the "Comidas/Hoy" screen from the local database: the selected day's
 * meals and the saved templates update live, with no network in the way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HoyViewModel(
    private val repository: AppRepository = AppGraph.repository,
) : ViewModel() {

    private val _day = MutableStateFlow(todayKey())
    val day: StateFlow<String> = _day.asStateFlow()

    val state: StateFlow<HoyUiState> = _day
        .flatMapLatest { repository.mealsForDay(it) }
        .map<List<MealDTO>, HoyUiState> { HoyUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoyUiState.Loading)

    val templates: StateFlow<List<TemplateDTO>> = repository.templates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var reorderJob: Job? = null

    /** Caption for the day navigator, e.g. "Tuesday, September 2". */
    fun dayCaption(): String = formatDateLong(_day.value)

    fun prevDay() = shiftDay(-1)

    fun nextDay() = shiftDay(1)

    fun goToday() {
        _day.value = todayKey()
    }

    private fun shiftDay(delta: Int) {
        _day.value = addDaysToKey(_day.value, delta)
    }

    /** Persists a drag reorder; debounced because the list reports every step of the drag. */
    fun reorder(meals: List<MealDTO>) {
        val ids = meals.map { it.id }
        reorderJob?.cancel()
        reorderJob = viewModelScope.launch {
            delay(300)
            repository.reorderMeals(ids)
        }
    }

    /** Creates ([mealId] null) or replaces a meal. */
    fun saveMeal(mealId: String?, request: MealRequest) {
        viewModelScope.launch { repository.saveMeal(mealId, request) }
    }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch { repository.deleteMeal(mealId) }
    }

    fun restoreMeal(meal: MealDTO) {
        viewModelScope.launch { repository.restoreMeal(meal) }
    }

    /** Applies a saved template as a new meal on the current day (web `applyTemplate`). */
    fun applyTemplate(template: TemplateDTO) {
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
        viewModelScope.launch { repository.saveMeal(null, request) }
    }

    private fun totalIfOnly(entryMode: WireEntryMode, value: Double?): Double? =
        if (entryMode == WireEntryMode.TOTAL_ONLY) value else null
}
