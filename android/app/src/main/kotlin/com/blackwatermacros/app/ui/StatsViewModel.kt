package com.blackwatermacros.app.ui

import com.blackwatermacros.app.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.StatsMeal
import com.blackwatermacros.app.core.StatsRange
import com.blackwatermacros.app.core.StatsSummary
import com.blackwatermacros.app.core.StatsWeight
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.buildStatsFromData
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.WeightDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Loaded(val summary: StatsSummary) : StatsUiState
}

enum class StatsRangeOption(val range: StatsRange, val days: Int?, @StringRes val labelRes: Int) {
    R7D(StatsRange.RANGE_7D, 7, R.string.range_7d),
    R30D(StatsRange.RANGE_30D, 30, R.string.range_30d),
    R90D(StatsRange.RANGE_90D, 90, R.string.range_90d),
    ALL(StatsRange.ALL, null, R.string.range_all),
}

/**
 * Statistics computed on the phone with the `:core` port of the web's stats
 * builder — the same numbers `/api/stats` would return, without a request.
 */
class StatsViewModel(
    repository: AppRepository = AppGraph.repository,
) : ViewModel() {

    private val _range = MutableStateFlow(StatsRangeOption.R30D)
    val range: StateFlow<StatsRangeOption> = _range.asStateFlow()

    val state: StateFlow<StatsUiState> =
        combine(_range, repository.allMeals(), repository.weights()) { range, meals, weights ->
            StatsUiState.Loaded(buildSummary(range, meals, weights)) as StatsUiState
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    fun selectRange(option: StatsRangeOption) {
        _range.value = option
    }

    private fun buildSummary(range: StatsRangeOption, meals: List<MealDTO>, weights: List<WeightDTO>): StatsSummary {
        val today = todayKey()
        // Same window the server applies before calling the builder.
        val fromKey = range.days?.let { addDaysToKey(today, -(it - 1)) }
        val inRange = meals.filter { (fromKey == null || it.logDate >= fromKey) && it.logDate <= today }
        return buildStatsFromData(
            meals = inRange.map {
                StatsMeal(it.logDate, it.resolvedCalories, it.resolvedProtein, it.resolvedCarbs, it.resolvedFat)
            },
            weights = weights.map { StatsWeight(Instant.parse(it.measuredAt), it.weightKg, it.bodyFatPct) },
            range = range.range,
            todayKeyParam = today,
        )
    }
}
