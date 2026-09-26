package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.CalorieRecommendation
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.data.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Loaded(
        val profile: CalorieProfile,
        val calorieRec: CalorieRecommendation?,
        val proteinRec: ProteinRecommendation?,
    ) : ProfileUiState
}

class ProfileViewModel(
    private val repository: AppRepository = AppGraph.repository,
) : ViewModel() {

    /** Unsaved edit shown while typing (possibly invalid); null = show what is stored. */
    private val draft = MutableStateFlow<CalorieProfile?>(null)

    val state: StateFlow<ProfileUiState> =
        combine(repository.profile(), draft, repository.weights()) { stored, draft, weights ->
            val profile = draft ?: stored
            val ready = recommend(weights, profile) as? RecommendationsUiState.Ready
            ProfileUiState.Loaded(profile, ready?.calorie, ready?.protein) as ProfileUiState
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)

    private var saveJob: Job? = null

    /**
     * Mirrors web `handleCalorieProfileChange`: the edit shows immediately and
     * is stored 500 ms later, unless the profile has validation errors.
     */
    fun updateProfile(profile: CalorieProfile) {
        draft.value = profile
        saveJob?.cancel()
        if (!isValidProfile(profile)) return
        saveJob = viewModelScope.launch {
            delay(500)
            repository.saveProfile(profile)
            draft.value = null
        }
    }

    fun updateCalorieGoal(goal: Goal) {
        val current = state.value as? ProfileUiState.Loaded ?: return
        updateProfile(current.profile.copy(calorieGoal = goal))
    }
}

/** Server limits (`calorieProfileInputSchema`); the field hints show them to the user. */
internal fun isValidProfile(profile: CalorieProfile): Boolean =
    profile.birthYear.inRange(1920, 2010) &&
        profile.heightCm.inRange(100.0, 250.0) &&
        profile.gymDaysPerWeek.inRange(0, 7) &&
        profile.gymSessionMinutes.inRange(0, 300) &&
        profile.walkingMinutesPerDay.inRange(0, 480)

private fun <T : Comparable<T>> T?.inRange(min: T, max: T): Boolean = this == null || this in min..max
