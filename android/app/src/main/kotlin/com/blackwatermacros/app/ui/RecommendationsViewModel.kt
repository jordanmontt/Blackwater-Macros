package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.CalorieRecommendation
import com.blackwatermacros.app.core.DataPoint
import com.blackwatermacros.app.core.ExpenditureCheck
import com.blackwatermacros.app.core.ExpenditureEstimate
import com.blackwatermacros.app.core.ExpenditureNoticeDismissal
import com.blackwatermacros.app.core.ProteinPerson
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.core.calculateCalorieRecommendation
import com.blackwatermacros.app.core.calculateProteinRecommendation
import com.blackwatermacros.app.core.checkExpenditure
import com.blackwatermacros.app.core.estimateExpenditure
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.AppPreferences
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.WeightDTO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
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
        /** Measured from the last 4 weeks; null until the data supports it. */
        val expenditure: ExpenditureEstimate? = null,
        /** The profile's expenditure checked against the measured one; null without a calorie target. */
        val check: ExpenditureCheck? = null,
        /** What the formula was fed, to write its equations with the user's numbers. */
        val inputs: FormulaInputs? = null,
    ) : RecommendationsUiState
}

/**
 * Computes calorie + protein recommendations via `:core` from the latest
 * local weight and the profile, mirroring the web `NutritionRecommendationsCard`.
 */
class RecommendationsViewModel(
    repository: AppRepository = AppGraph.repository,
    private val preferences: AppPreferences = AppGraph.preferences,
) : ViewModel() {

    val state: StateFlow<RecommendationsUiState> =
        combine(repository.weights(), repository.profile(), repository.allMeals()) { weights, profile, meals ->
            recommend(weights, profile, meals)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecommendationsUiState.Loading)

    /** «Not now» on the Today card's expenditure notices (see [ExpenditureNotices]). */
    val noticeDismissal: StateFlow<ExpenditureNoticeDismissal?> = preferences.expenditureNoticeDismissal
    val weighInHintDismissed: StateFlow<Boolean> = preferences.weighInHintDismissed

    fun dismissNotice(dismissal: ExpenditureNoticeDismissal) = preferences.dismissExpenditureNotice(dismissal)

    fun dismissWeighInHint() = preferences.dismissWeighInHint()
}

internal fun recommend(
    weights: List<WeightDTO>,
    profile: CalorieProfile,
    meals: List<MealDTO> = emptyList(),
    today: String = todayKey(),
): RecommendationsUiState {
    val latestWeight = weights.lastOrNull() ?: return RecommendationsUiState.NoWeight
    // Body fat is not logged at every weigh-in: use the most recent one there is.
    val bodyFatPct = weights.lastOrNull { it.bodyFatPct != null }?.bodyFatPct
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val calorie = if (profile.gender != null && profile.calorieGoal != null) {
        calculateCalorieRecommendation(profile, latestWeight.weightKg, year)
    } else {
        null
    }
    val protein = profile.calorieGoal?.let {
        calculateProteinRecommendation(latestWeight.weightKg, it, bodyFatPct, ProteinPerson(profile.heightCm, profile.gender, profile.birthYear?.let { year - it }))
    }
    if (calorie == null && protein == null) return RecommendationsUiState.NeedsProfile
    val intake = meals.map { DataPoint(it.logDate, it.resolvedCalories) }
    val weighIns = weights.mapNotNull { w -> localDateKey(w.measuredAt)?.let { DataPoint(it, w.weightKg) } }
    val expenditure = estimateExpenditure(intake, weighIns, today)
    val check = calorie?.let { checkExpenditure(intake, weighIns, it, today) }
    return RecommendationsUiState.Ready(calorie, protein, expenditure, check, formulaInputs(profile, latestWeight.weightKg, year))
}

/** The local calendar day of an ISO instant ("2026-03-01T07:30:00Z" → "2026-03-01"). */
internal fun localDateKey(instant: String): String? =
    runCatching { Instant.parse(instant).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.getOrNull()
