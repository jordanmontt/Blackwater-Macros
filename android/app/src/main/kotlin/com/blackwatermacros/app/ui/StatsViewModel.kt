package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.ResponseErrorMapper
import com.blackwatermacros.app.data.SessionManager
import com.blackwatermacros.app.data.StatsSummary
import com.blackwatermacros.app.data.WireStatsRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Loaded(val summary: StatsSummary) : StatsUiState
    data class Error(val message: String) : StatsUiState
}

enum class StatsRangeOption(val wireValue: WireStatsRange, val label: String) {
    R7D(WireStatsRange.RANGE_7D, "7 días"),
    R30D(WireStatsRange.RANGE_30D, "30 días"),
    R90D(WireStatsRange.RANGE_90D, "90 días"),
    ALL(WireStatsRange.ALL, "Todo"),
}

class StatsViewModel : ViewModel() {

    private val _range = MutableStateFlow(StatsRangeOption.R30D)
    val range: StateFlow<StatsRangeOption> = _range.asStateFlow()

    private val _state = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    private val api = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = SessionManager::tokenProvider,
    )

    init {
        load()
    }

    fun selectRange(option: StatsRangeOption) {
        if (_range.value == option) return
        _range.value = option
        load()
    }

    fun load() {
        _state.value = StatsUiState.Loading
        val range = _range.value.wireValue.toWireString()
        viewModelScope.launch {
            try {
                val summary = api.stats(range = range, today = todayKey())
                _state.value = StatsUiState.Loaded(summary)
            } catch (t: Throwable) {
                _state.value = StatsUiState.Error(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    private fun WireStatsRange.toWireString(): String = when (this) {
        WireStatsRange.RANGE_7D -> "7d"
        WireStatsRange.RANGE_30D -> "30d"
        WireStatsRange.RANGE_90D -> "90d"
        WireStatsRange.ALL -> "all"
    }

    private fun ensureTrailingSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
}