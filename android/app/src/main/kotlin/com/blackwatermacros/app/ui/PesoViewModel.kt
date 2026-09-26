package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.core.DataPoint
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.formatDateKeyLong
import com.blackwatermacros.app.core.movingAverageByDays
import com.blackwatermacros.app.core.round1
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.ResponseErrorMapper
import com.blackwatermacros.app.data.SessionManager
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.WeightRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PesoUiState {
    data object Loading : PesoUiState
    data class Loaded(val summary: PesoSummary) : PesoUiState
    data class Error(val message: String) : PesoUiState
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

class PesoViewModel : ViewModel() {

    private val _state = MutableStateFlow<PesoUiState>(PesoUiState.Loading)
    val state: StateFlow<PesoUiState> = _state.asStateFlow()

    private val api = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = SessionManager::tokenProvider,
    )

    init {
        load()
    }

    fun load() {
        _state.value = PesoUiState.Loading
        viewModelScope.launch {
            try {
                val weights = api.listWeights().weights.sortedBy { it.measuredAt }
                _state.value = PesoUiState.Loaded(buildSummary(weights))
            } catch (t: Throwable) {
                _state.value = PesoUiState.Error(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    fun createWeight(request: WeightRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.createWeight(request)
                load()
                onDone(true)
            } catch (_: Throwable) {
                onDone(false)
            }
        }
    }

    fun updateWeight(id: String, request: WeightRequest, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.updateWeight(id, request)
                load()
                onDone(true)
            } catch (_: Throwable) {
                onDone(false)
            }
        }
    }

    fun deleteWeight(id: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.deleteWeight(id)
                load()
                onDone(true)
            } catch (_: Throwable) {
                onDone(false)
            }
        }
    }

    private fun buildSummary(weights: List<WeightDTO>): PesoSummary {
        val latest = weights.lastOrNull()
        val currentWeightKg = latest?.weightKg

        val changeWeight7d = latest?.let { latestEntry ->
            val latestDay = latestEntry.measuredAt.take(10)
            val cutoff = addDaysToKey(latestDay, -7)
            val prior = weights.asReversed()
                .firstOrNull { it.measuredAt.take(10) <= cutoff }
                ?: return@let null
            round1(latestEntry.weightKg - prior.weightKg)
        }

        val fatEntries = weights.filter { it.bodyFatPct != null }
        val lastFat = fatEntries.lastOrNull()
        val currentBodyFatPct = lastFat?.bodyFatPct

        val changeFat7d = lastFat?.let { latestEntry ->
            val latestDay = latestEntry.measuredAt.take(10)
            val cutoff = addDaysToKey(latestDay, -7)
            val prior = fatEntries.asReversed()
                .firstOrNull { it.measuredAt.take(10) <= cutoff }
                ?: return@let null
            round1(latestEntry.bodyFatPct!! - prior.bodyFatPct!!)
        }

        val points = weights.map { DataPoint(date = it.measuredAt.take(10), value = it.weightKg) }
        val trend = movingAverageByDays(points, 7)
        val fatByDay = weights.mapNotNull { entry ->
            entry.bodyFatPct?.let { entry.measuredAt.take(10) to it }
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
            .groupBy { it.measuredAt.take(10) }
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

    private fun ensureTrailingSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
}

private fun formatDayLong(key: String): String = formatDateKeyLong(key)