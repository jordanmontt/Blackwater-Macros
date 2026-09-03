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
import com.blackwatermacros.app.data.ApiService
import com.blackwatermacros.app.data.ResponseErrorMapper
import com.blackwatermacros.app.data.SessionManager
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.TemplateRequest
import com.blackwatermacros.app.data.WireCalorieProfile
import com.blackwatermacros.app.data.WireGender
import com.blackwatermacros.app.data.WireGoal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Loaded(
        val username: String,
        val isAdmin: Boolean,
        val profile: CalorieProfile,
        val calorieRec: CalorieRecommendation?,
        val proteinRec: ProteinRecommendation?,
    ) : SettingsUiState

    data class Error(val message: String) : SettingsUiState
}

class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _templates = MutableStateFlow<List<TemplateDTO>>(emptyList())
    val templates: StateFlow<List<TemplateDTO>> = _templates.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val api: ApiService = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = SessionManager::tokenProvider,
    )

    private var saveJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = SettingsUiState.Loading
            try {
                val session = api.session()
                val weights = api.listWeights().weights
                val templates = api.listTemplates().templates
                _templates.value = templates
                val latestWeight = weights.lastOrNull()?.weightKg
                val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val profile = session.calorieProfile.toCoreProfile()
                val calorieRec = if (latestWeight != null && profile.gender != null && profile.calorieGoal != null) {
                    calculateCalorieRecommendation(profile, latestWeight, year)
                } else null
                val proteinRec = latestWeight?.takeIf { profile.calorieGoal != null }
                    ?.let { calculateProteinRecommendation(it, profile.calorieGoal!!) }
                _state.value = SettingsUiState.Loaded(
                    username = session.username,
                    isAdmin = session.isAdmin,
                    profile = profile,
                    calorieRec = calorieRec,
                    proteinRec = proteinRec,
                )
            } catch (t: Throwable) {
                _state.value = SettingsUiState.Error(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    /**
     * Update the profile with a debounced autosave (mirror web
     * `handleCalorieProfileChange`): applies immediately, persists 500ms
     * later, skipping if the resulting profile has validation errors.
     */
    fun updateProfile(profile: CalorieProfile) {
        val current = _state.value
        if (current !is SettingsUiState.Loaded) return
        _state.value = current.copy(profile = profile)
        saveJob?.cancel()
        if (validateProfile(profile).isNotEmpty()) return
        saveJob = viewModelScope.launch {
            delay(500)
            persistProfile(profile)
        }
    }

    fun updateCalorieGoal(goal: Goal) {
        val current = _state.value
        if (current !is SettingsUiState.Loaded) return
        updateProfile(current.profile.copy(calorieGoal = goal))
    }

    private suspend fun persistProfile(profile: CalorieProfile) {
        _saving.value = true
        try {
            api.updateSettings(profile.toWireProfile())
        } catch (_: Throwable) {
            // Profile remains in local state; the user will see it on reload.
        } finally {
            _saving.value = false
        }
    }

    fun deleteTemplate(id: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.deleteTemplate(id)
                _templates.value = _templates.value.filter { it.id != id }
                onDone(true)
            } catch (_: Throwable) {
                onDone(false)
            }
        }
    }

    fun createTemplate(request: TemplateRequest, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val created = api.createTemplate(request).template
                _templates.value = (_templates.value + created).sortedBy { it.name.lowercase() }
                onDone(null)
            } catch (t: Throwable) {
                onDone(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    fun updateTemplate(id: String, request: TemplateRequest, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val updated = api.updateTemplate(id, request).template
                _templates.value = _templates.value.map { if (it.id == id) updated else it }
                    .sortedBy { it.name.lowercase() }
                onDone(null)
            } catch (t: Throwable) {
                onDone(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                runCatching { api.logout() }
            } finally {
                SessionManager.clear()
                onDone()
            }
        }
    }

    private fun validateProfile(profile: CalorieProfile): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val birthYear = profile.birthYear
        if (birthYear != null && (birthYear < 1920 || birthYear > 2010)) {
            errors["birthYear"] = "El año debe estar entre 1920 y 2010"
        }
        val heightCm = profile.heightCm
        if (heightCm != null && (heightCm < 100 || heightCm > 250)) {
            errors["heightCm"] = "La altura debe estar entre 100 y 250 cm"
        }
        val gymDaysPerWeek = profile.gymDaysPerWeek
        if (gymDaysPerWeek != null && (gymDaysPerWeek < 0 || gymDaysPerWeek > 7)) {
            errors["gymDaysPerWeek"] = "Los días deben ser entre 0 y 7"
        }
        val gymSessionMinutes = profile.gymSessionMinutes
        if (gymSessionMinutes != null && (gymSessionMinutes < 0 || gymSessionMinutes > 300)) {
            errors["gymSessionMinutes"] = "La duración debe ser entre 0 y 300 min"
        }
        val walkingMinutesPerDay = profile.walkingMinutesPerDay
        if (walkingMinutesPerDay != null && (walkingMinutesPerDay < 0 || walkingMinutesPerDay > 480)) {
            errors["walkingMinutesPerDay"] = "El tiempo debe ser entre 0 y 480 min"
        }
        return errors
    }

    private fun ensureTrailingSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
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

private fun CalorieProfile.toWireProfile() = WireCalorieProfile(
    gender = gender?.let {
        when (it) {
            Gender.MALE -> WireGender.MALE
            Gender.FEMALE -> WireGender.FEMALE
        }
    },
    birthYear = birthYear,
    heightCm = heightCm,
    gymDaysPerWeek = gymDaysPerWeek,
    gymSessionMinutes = gymSessionMinutes,
    walkingMinutesPerDay = walkingMinutesPerDay,
    calorieGoal = calorieGoal?.let {
        when (it) {
            Goal.CUT -> WireGoal.CUT
            Goal.MAINTAIN -> WireGoal.MAINTAIN
            Goal.SURPLUS -> WireGoal.SURPLUS
        }
    },
)