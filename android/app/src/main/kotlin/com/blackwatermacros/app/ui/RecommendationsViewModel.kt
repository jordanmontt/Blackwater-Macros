package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.CalorieRecommendation
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.core.calculateCalorieRecommendation
import com.blackwatermacros.app.core.calculateProteinRecommendation
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.WeightDTO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/** Immutable recommendation results for a single day's intake. */
sealed interface RecommendationsUiState {
    data object Loading : RecommendationsUiState
    data object NoWeight : RecommendationsUiState

    /** There is a weight, but no goal/profile yet to compute anything from. */
    data object NeedsProfile : RecommendationsUiState
    data class Ready(
        val calorie: CalorieRecommendation?,
        val protein: ProteinRecommendation?,
    ) : RecommendationsUiState
}

/**
 * Computes calorie + protein recommendations via `:core` from the latest
 * local weight and the profile, mirroring the web `NutritionRecommendationsCard`.
 */
class RecommendationsViewModel(
    repository: AppRepository = AppGraph.repository,
) : ViewModel() {

    val state: StateFlow<RecommendationsUiState> =
        combine(repository.weights(), repository.profile(), ::recommend)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecommendationsUiState.Loading)
}

internal fun recommend(weights: List<WeightDTO>, profile: CalorieProfile): RecommendationsUiState {
    val latestWeight = weights.lastOrNull() ?: return RecommendationsUiState.NoWeight
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val calorie = if (profile.gender != null && profile.calorieGoal != null) {
        calculateCalorieRecommendation(profile, latestWeight.weightKg, year)
    } else {
        null
    }
    val protein = profile.calorieGoal?.let { calculateProteinRecommendation(latestWeight.weightKg, it) }
    return if (calorie == null && protein == null) {
        RecommendationsUiState.NeedsProfile
    } else {
        RecommendationsUiState.Ready(calorie, protein)
    }
}
