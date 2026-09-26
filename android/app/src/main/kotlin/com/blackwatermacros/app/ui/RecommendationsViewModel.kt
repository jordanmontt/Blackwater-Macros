package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.CalorieRecommendation
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.core.calculateCalorieRecommendation
import com.blackwatermacros.app.core.calculateProteinRecommendation
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.WireCalorieProfile
import com.blackwatermacros.app.data.WireGender
import com.blackwatermacros.app.data.WireGoal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/** Immutable recommendation results for a single day's intake. */
sealed interface RecommendationsUiState {
    data object Loading : RecommendationsUiState
    data object NoWeight : RecommendationsUiState
    data class Ready(
        val calorie: CalorieRecommendation?,
        val protein: ProteinRecommendation?,
    ) : RecommendationsUiState
}

/**
 * Loads the latest weight and the user's calorie profile, computes calorie +
 * protein recommendations via `:core`, mirroring the web
 * `NutritionRecommendationsCard`.
 */
class RecommendationsViewModel : ViewModel() {

    private val _state = MutableStateFlow<RecommendationsUiState>(RecommendationsUiState.Loading)
    val state: StateFlow<RecommendationsUiState> = _state.asStateFlow()

    private val api = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = com.blackwatermacros.app.data.SessionManager::tokenProvider,
    )

    init {
        load()
    }

    fun load() {
        _state.value = RecommendationsUiState.Loading
        viewModelScope.launch {
            try {
                val weights = api.listWeights().weights
                val session = api.session()
                val latestWeight = weights.lastOrNull()
                if (latestWeight == null) {
                    _state.value = RecommendationsUiState.NoWeight
                    return@launch
                }
                val profile = session.calorieProfile.toCoreProfile()
                val year = Calendar.getInstance().get(Calendar.YEAR)
                val calorie = if (profile.gender != null && profile.calorieGoal != null) {
                    calculateCalorieRecommendation(profile, latestWeight.weightKg, year)
                } else {
                    null
                }
                val protein = latestWeight.weightKg.takeIf { profile.calorieGoal != null }
                    ?.let { w -> calculateProteinRecommendation(w, profile.calorieGoal!!) }
                _state.value = RecommendationsUiState.Ready(calorie, protein)
            } catch (_: Throwable) {
                _state.value = RecommendationsUiState.NoWeight
            }
        }
    }

    private fun WireCalorieProfile.toCoreProfile() = CalorieProfile(
        gender = gender?.toCoreGender(),
        birthYear = birthYear,
        heightCm = heightCm,
        gymDaysPerWeek = gymDaysPerWeek,
        gymSessionMinutes = gymSessionMinutes,
        walkingMinutesPerDay = walkingMinutesPerDay,
        calorieGoal = calorieGoal?.toCoreGoal(),
    )

    private fun WireGender.toCoreGender(): Gender = when (this) {
        WireGender.MALE -> Gender.MALE
        WireGender.FEMALE -> Gender.FEMALE
    }

    private fun WireGoal.toCoreGoal(): Goal = when (this) {
        WireGoal.CUT -> Goal.CUT
        WireGoal.MAINTAIN -> Goal.MAINTAIN
        WireGoal.SURPLUS -> Goal.SURPLUS
    }

    private fun ensureTrailingSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
}