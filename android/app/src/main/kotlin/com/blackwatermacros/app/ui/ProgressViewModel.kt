package com.blackwatermacros.app.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.CalorieRecommendation
import com.blackwatermacros.app.core.ExpenditureEstimate
import com.blackwatermacros.app.core.MacroAverages
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.core.StatsMeal
import com.blackwatermacros.app.core.StatsRange
import com.blackwatermacros.app.core.StatsSummary
import com.blackwatermacros.app.core.StatsWeight
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.buildStatsFromData
import com.blackwatermacros.app.core.macroAverages
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.WeightRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

enum class StatsRangeOption(val range: StatsRange, val days: Int?, @StringRes val labelRes: Int) {
    R7D(StatsRange.RANGE_7D, 7, R.string.range_7d),
    R30D(StatsRange.RANGE_30D, 30, R.string.range_30d),
    R90D(StatsRange.RANGE_90D, 90, R.string.range_90d),
    ALL(StatsRange.ALL, null, R.string.range_all),
}

/** Rows for the weight/fat chart. */
data class WeightFatRow(
    val date: String,
    val weight: Double,
    val weightTrend: Double?,
    val bodyFatPct: Double?,
)

/** Everything the Progreso screen shows for one period (mirror web `progreso/page.tsx`). */
data class Progress(
    val summary: StatsSummary,
    val weightRows: List<WeightFatRow>,
    /** Averages over logged days of the period; null when nothing was logged. */
    val averages: MacroAverages?,
    val calorie: CalorieRecommendation?,
    val protein: ProteinRecommendation?,
    val expenditure: ExpenditureEstimate?,
    /** Weigh-ins of the period by local day, newest first. */
    val groupedWeights: List<Pair<String, List<WeightDTO>>>,
    val hasAnyWeight: Boolean,
)

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Loaded(val progress: Progress) : ProgressUiState
}

/**
 * Progreso: weight and eating over one selected period, computed on the phone
 * with the `:core` ports (same numbers as the web's `/api/stats`).
 */
class ProgressViewModel(
    private val repository: AppRepository = AppGraph.repository,
) : ViewModel() {

    private val _range = MutableStateFlow(StatsRangeOption.R30D)
    val range: StateFlow<StatsRangeOption> = _range.asStateFlow()

    val state: StateFlow<ProgressUiState> =
        combine(_range, repository.allMeals(), repository.weights(), repository.profile()) { range, meals, weights, profile ->
            ProgressUiState.Loaded(buildProgress(range, meals, weights, profile, todayKey())) as ProgressUiState
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState.Loading)

    fun selectRange(option: StatsRangeOption) {
        _range.value = option
    }

    fun saveWeight(id: String?, request: WeightRequest) {
        viewModelScope.launch { repository.saveWeight(id, request) }
    }

    fun deleteWeight(id: String) {
        viewModelScope.launch { repository.deleteWeight(id) }
    }

    fun restoreWeight(weight: WeightDTO) {
        viewModelScope.launch { repository.restoreWeight(weight) }
    }
}

internal fun buildProgress(
    range: StatsRangeOption,
    meals: List<MealDTO>,
    weights: List<WeightDTO>,
    profile: CalorieProfile,
    today: String,
): Progress {
    // Same window the server applies before calling the builder.
    val fromKey = range.days?.let { addDaysToKey(today, -(it - 1)) }
    val inRange = meals.filter { (fromKey == null || it.logDate >= fromKey) && it.logDate <= today }
    val summary = buildStatsFromData(
        meals = inRange.map { StatsMeal(it.logDate, it.resolvedCalories, it.resolvedProtein, it.resolvedCarbs, it.resolvedFat) },
        weights = weights.map { StatsWeight(Instant.parse(it.measuredAt), it.weightKg, it.bodyFatPct) },
        range = range.range,
        todayKeyParam = today,
    )

    // The stats series has every day of the period (0 when nothing was logged).
    val averages = macroAverages(summary.calories)

    val ready = recommend(weights, profile, meals, today) as? RecommendationsUiState.Ready

    val grouped = weights
        .filter { fromKey == null || it.measuredAt.localDay() >= fromKey }
        .groupBy { it.measuredAt.localDay() }
        .toSortedMap(compareByDescending { it })
        .map { (day, entries) -> day to entries.asReversed() }

    return Progress(
        summary = summary,
        weightRows = weightRows(summary),
        averages = averages,
        calorie = ready?.calorie,
        protein = ready?.protein,
        expenditure = ready?.expenditure,
        groupedWeights = grouped,
        hasAnyWeight = weights.isNotEmpty(),
    )
}

private fun weightRows(summary: StatsSummary): List<WeightFatRow> {
    val trendByDate = summary.weights.mapNotNull { point -> point.trend?.let { point.date to it } }.toMap()
    val fatByDate = summary.bodyFat.associate { it.date to it.bodyFatPct }
    return summary.weights.associate { it.date to it.weight }.toSortedMap().map { (date, weight) ->
        WeightFatRow(date, weight, trendByDate[date], fatByDate[date])
    }
}

/** Calendar day of a UTC instant in the phone's timezone (a 00:30 weigh-in belongs to that local day). */
private fun String.localDay(): String = Instant.parse(this).atZone(ZoneId.systemDefault()).toLocalDate().toString()
