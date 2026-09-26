package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.DataPoint
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.movingAverageByDays
import com.blackwatermacros.app.core.round1
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.WeightRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PesoUiState {
    data object Loading : PesoUiState
    data class Loaded(val summary: PesoSummary) : PesoUiState
}

/** Rows for the weight/fat chart (mirror web `chartRows`). */
data class WeightFatRow(
    val date: String,
    val weight: Double,
    val weightTrend: Double?,
    val bodyFatPct: Double?,
)

/** Derived values shown on the Peso page (mirror web `peso/page.tsx`). */
data class PesoSummary(
    val weights: List<WeightDTO>,
    val currentWeightKg: Double?,
    val changeWeight7d: Double?,
    val currentBodyFatPct: Double?,
    val changeFat7d: Double?,
    val chartRows: List<WeightFatRow>,
    val groupedWeights: List<Pair<String, List<WeightDTO>>>,
)

class PesoViewModel(
    private val repository: AppRepository = AppGraph.repository,
) : ViewModel() {

    val state: StateFlow<PesoUiState> = repository.weights()
        .map<List<WeightDTO>, PesoUiState> { PesoUiState.Loaded(buildSummary(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PesoUiState.Loading)

    fun saveWeight(id: String?, request: WeightRequest) {
        viewModelScope.launch { repository.saveWeight(id, request) }
    }

    fun deleteWeight(id: String) {
        viewModelScope.launch { repository.deleteWeight(id) }
    }

    private fun buildSummary(weights: List<WeightDTO>): PesoSummary {
        val latest = weights.lastOrNull()
        val currentWeightKg = latest?.weightKg

        val changeWeight7d = latest?.let { latestEntry ->
            val latestDay = latestEntry.measuredAt.localDay()
            val cutoff = addDaysToKey(latestDay, -7)
            val prior = weights.asReversed()
                .firstOrNull { it.measuredAt.localDay() <= cutoff }
                ?: return@let null
            round1(latestEntry.weightKg - prior.weightKg)
        }

        val fatEntries = weights.filter { it.bodyFatPct != null }
        val lastFat = fatEntries.lastOrNull()
        val currentBodyFatPct = lastFat?.bodyFatPct

        val changeFat7d = lastFat?.let { latestEntry ->
            val latestDay = latestEntry.measuredAt.localDay()
            val cutoff = addDaysToKey(latestDay, -7)
            val prior = fatEntries.asReversed()
                .firstOrNull { it.measuredAt.localDay() <= cutoff }
                ?: return@let null
            round1(latestEntry.bodyFatPct!! - prior.bodyFatPct!!)
        }

        val points = weights.map { DataPoint(date = it.measuredAt.localDay(), value = it.weightKg) }
        val trend = movingAverageByDays(points, 7)
        val fatByDay = weights.mapNotNull { entry ->
            entry.bodyFatPct?.let { entry.measuredAt.localDay() to it }
        }

        val chartRows = points.mapIndexed { i, p ->
            WeightFatRow(
                date = p.date,
                weight = p.value,
                weightTrend = trend[i]?.let { round1(it) },
                bodyFatPct = fatByDay.firstOrNull { it.first == p.date }?.second,
            )
        }

        val groupedWeights = weights
            .groupBy { it.measuredAt.localDay() }
            .toSortedMap(compareByDescending { it })
            .map { (day, entries) -> day to entries.asReversed() }

        return PesoSummary(
            weights = weights,
            currentWeightKg = currentWeightKg,
            changeWeight7d = changeWeight7d,
            currentBodyFatPct = currentBodyFatPct,
            changeFat7d = changeFat7d,
            chartRows = chartRows,
            groupedWeights = groupedWeights,
        )
    }
}

/** Calendar day of a UTC instant in the phone's timezone (a 00:30 weigh-in belongs to that local day). */
private fun String.localDay(): String =
    java.time.Instant.parse(this).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
