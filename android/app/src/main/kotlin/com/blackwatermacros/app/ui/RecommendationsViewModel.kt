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
import com.blackwatermacros.app.core.AverageTargets
import com.blackwatermacros.app.core.DatedWeight
import com.blackwatermacros.app.core.DayTargets
import com.blackwatermacros.app.core.ProfilePeriod
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.core.averageTargets
import com.blackwatermacros.app.core.targetsOn
import com.blackwatermacros.app.core.checkExpenditure
import com.blackwatermacros.app.core.MeasuredOn
import com.blackwatermacros.app.core.weekStartOf
import com.blackwatermacros.app.core.weeklyExpenditure
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

/** Immutable recommendation results for a single day's intake. */
sealed interface RecommendationsUiState {
    data object Loading : RecommendationsUiState
    data object NoWeight : RecommendationsUiState

    /** There is a weight, but no goal/profile yet to compute anything from. */
    data object NeedsProfile : RecommendationsUiState
    data class Ready(
        val calorie: CalorieRecommendation?,
        val protein: ProteinRecommendation?,
        /** This week's: measured on Monday from the 4 weeks before; null until the data supports it. */
        val expenditure: ExpenditureEstimate? = null,
        /** The profile's expenditure checked against the measured one; null without a calorie target. */
        val check: ExpenditureCheck? = null,
        /** What the formula was fed, to write its equations with the user's numbers. */
        val inputs: FormulaInputs? = null,
        /** Any day's targets, with the profile and weight it had (a past day keeps its own). */
        val targets: TargetHistory? = null,
    ) : RecommendationsUiState
}

/**
 * What a day's targets come from: each day keeps the profile, the weight and the
 * measured expenditure (that week's Monday) it had (`core/ProfileHistory.kt`).
 */
class TargetHistory(
    val profile: CalorieProfile,
    val history: List<ProfilePeriod>,
    val weights: List<DatedWeight>,
    val measuredOn: MeasuredOn = { null },
) {
    fun on(day: String): DayTargets = targetsOn(profile, history, weights, day, measuredOn)

    /** The mean of [days]' own targets (Progreso compares the period's average with it). */
    fun average(days: List<String>): AverageTargets = averageTargets(profile, history, weights, days, measuredOn)
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
        combine(
            repository.weights(),
            repository.profile(),
            repository.allMeals(),
            repository.profileHistory(),
        ) { weights, profile, meals, history ->
            recommend(weights, profile, meals, history = history)
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
    history: List<ProfilePeriod> = emptyList(),
): RecommendationsUiState {
    val latestWeight = weights.lastOrNull() ?: return RecommendationsUiState.NoWeight
    val intake = meals.map { DataPoint(it.logDate, it.resolvedCalories) }
    val weighIns = weights.mapNotNull { w -> localDateKey(w.measuredAt)?.let { DataPoint(it, w.weightKg) } }
    val measuredOn = weeklyExpenditure(intake, weighIns)
    val targets = TargetHistory(profile, history, datedWeights(weights), measuredOn)
    // Today: the current profile, the latest weigh-in and this week's measurement.
    val (calorie, protein) = targets.on(today)
    if (calorie == null && protein == null) return RecommendationsUiState.NeedsProfile
    val year = today.substring(0, 4).toInt()
    val expenditure = measuredOn(today)
    val check = calorie?.let { checkExpenditure(intake, weighIns, it, weekStartOf(today)) }
    return RecommendationsUiState.Ready(
        calorie,
        protein,
        expenditure,
        check,
        formulaInputs(profile, latestWeight.weightKg, year),
        targets,
    )
}

/** Weigh-ins on their local calendar day, in measuring order. */
internal fun datedWeights(weights: List<WeightDTO>): List<DatedWeight> =
    weights.mapNotNull { w -> localDateKey(w.measuredAt)?.let { DatedWeight(it, w.weightKg, w.bodyFatPct) } }

/** The local calendar day of an ISO instant ("2026-03-01T07:30:00Z" → "2026-03-01"). */
internal fun localDateKey(instant: String): String? =
    runCatching { Instant.parse(instant).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.getOrNull()
