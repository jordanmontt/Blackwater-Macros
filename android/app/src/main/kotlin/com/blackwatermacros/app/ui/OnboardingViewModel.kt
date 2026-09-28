package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.isCalorieProfileComplete
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppPreferences
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.WeightRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

enum class OnboardingStep { WELCOME, DATA, AI, DONE }

/**
 * On Android: the first steps show on a fresh install only — not done yet,
 * no account, and nothing logged on this phone (so an update never shows them
 * to someone who already uses the app).
 */
fun shouldShowOnboarding(done: Boolean, loggedIn: Boolean, hasLocalData: Boolean): Boolean =
    !done && !loggedIn && !hasLocalData

/** Web `DEFAULT_ACTIVITY`: used when the user does not open «Actividad». */
val DEFAULT_ACTIVITY = Triple(0, 60, 30)

/** The «Tus datos» form as typed (text fields keep what the user wrote). */
data class OnboardingForm(
    val gender: Gender? = null,
    val goal: Goal? = null,
    val birthYear: String = "",
    val height: String = "",
    val weight: String = "",
    val gymDays: String = DEFAULT_ACTIVITY.first.toString(),
    val sessionMinutes: String = DEFAULT_ACTIVITY.second.toString(),
    val walkingMinutes: String = DEFAULT_ACTIVITY.third.toString(),
) {
    /** The profile and weight to save, or null while something is missing or out of range. */
    fun parsed(): Pair<CalorieProfile, Double>? {
        val year = birthYear.trim().toIntOrNull()?.takeIf { it in 1920..2010 } ?: return null
        val heightCm = parseDecimal(height)?.takeIf { it in 100.0..250.0 } ?: return null
        val kg = parseDecimal(weight)?.takeIf { it in 20.0..400.0 } ?: return null
        val gym = gymDays.trim().toIntOrNull()?.takeIf { it in 0..7 } ?: return null
        val session = sessionMinutes.trim().toIntOrNull()?.takeIf { it in 0..300 } ?: return null
        val walking = walkingMinutes.trim().toIntOrNull()?.takeIf { it in 0..480 } ?: return null
        val chosenGender = gender ?: return null
        val chosenGoal = goal ?: return null
        return CalorieProfile(chosenGender, year, heightCm, gym, session, walking, chosenGoal) to kg
    }

    /** Every field filled, whether or not in range (to show the range message only then). */
    val filled: Boolean
        get() = gender != null && goal != null && listOf(birthYear, height, weight).all { it.isNotBlank() }
}

class OnboardingViewModel(
    private val repository: AppRepository = AppGraph.repository,
    private val preferences: AppPreferences = AppGraph.preferences,
    private val account: AccountStore = AppGraph.account,
) : ViewModel() {

    // «Ver tutorial» while logged in: nothing to log in to, start at «Tus datos».
    private val _step = MutableStateFlow(if (account.current != null) OnboardingStep.DATA else OnboardingStep.WELCOME)
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    private val _form = MutableStateFlow(OnboardingForm())
    val form: StateFlow<OnboardingForm> = _form.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private var latestWeightKg: Double? = null

    init {
        // «Ver tutorial» from Ajustes: start from what is already saved.
        viewModelScope.launch { prefill() }
    }

    private suspend fun prefill() {
        val profile = repository.profile().first()
        val latest = repository.weights().first().lastOrNull()
        latestWeightKg = latest?.weightKg
        _form.value = OnboardingForm(
            gender = profile.gender,
            goal = profile.calorieGoal,
            birthYear = profile.birthYear?.toString().orEmpty(),
            height = profile.heightCm?.let { formatNumber(it, maxDecimals = 1) }.orEmpty(),
            weight = latest?.weightKg?.let { formatNumber(it, maxDecimals = 1) }.orEmpty(),
            gymDays = (profile.gymDaysPerWeek ?: DEFAULT_ACTIVITY.first).toString(),
            sessionMinutes = (profile.gymSessionMinutes ?: DEFAULT_ACTIVITY.second).toString(),
            walkingMinutes = (profile.walkingMinutesPerDay ?: DEFAULT_ACTIVITY.third).toString(),
        )
    }

    fun update(transform: (OnboardingForm) -> OnboardingForm) {
        _form.value = transform(_form.value)
    }

    fun goTo(step: OnboardingStep) {
        _step.value = step
    }

    /** Back from the login screen: with a complete synced profile, «Tus datos» is skipped. */
    fun afterLogin() {
        if (account.current == null || _step.value != OnboardingStep.WELCOME) return
        viewModelScope.launch {
            prefill()
            val complete = isCalorieProfileComplete(repository.profile().first()) && latestWeightKg != null
            _step.value = if (complete) OnboardingStep.AI else OnboardingStep.DATA
        }
    }

    fun saveData() {
        val (profile, kg) = _form.value.parsed() ?: return
        _saving.value = true
        viewModelScope.launch {
            try {
                repository.saveProfile(profile)
                if (latestWeightKg != kg) {
                    repository.saveWeight(null, WeightRequest(Instant.now().toString(), kg))
                    latestWeightKg = kg
                }
                _step.value = OnboardingStep.AI
            } finally {
                _saving.value = false
            }
        }
    }

    /** «Saltar» or «Empezar». */
    fun finish() {
        preferences.onboardingDone = true
    }
}

/**
 * Whether the app opens on the first steps. Someone who already uses the app
 * (an account or anything logged) never sees them, and is marked as done.
 */
suspend fun resolveOnboarding(preferences: AppPreferences, account: AccountStore, repository: AppRepository): Boolean {
    if (preferences.onboardingDone) return false
    val hasLocalData = repository.allMeals().first().isNotEmpty() ||
        repository.weights().first().isNotEmpty() ||
        repository.profile().first().calorieGoal != null
    val show = shouldShowOnboarding(done = false, loggedIn = account.current != null, hasLocalData = hasLocalData)
    if (!show) preferences.onboardingDone = true
    return show
}
