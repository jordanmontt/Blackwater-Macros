package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.CalorieRecommendation
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.data.Account
import com.blackwatermacros.app.data.AccountController
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppPreferences
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.CsvBackup
import com.blackwatermacros.app.data.ImportResult
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.TemplateRequest
import com.blackwatermacros.app.data.ThemeMode
import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncOutcome
import com.blackwatermacros.app.data.sync.SyncProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Loaded(
        val profile: CalorieProfile,
        val calorieRec: CalorieRecommendation?,
        val proteinRec: ProteinRecommendation?,
    ) : SettingsUiState
}

/** What the «Cuenta» card shows. `account == null` = local-only mode. */
data class AccountUiState(
    val account: Account? = null,
    val pendingChanges: Int = 0,
    val syncing: Boolean = false,
    val problem: SyncProblem? = null,
    /** The last manual sync found no connection. */
    val offline: Boolean = false,
)

sealed interface LogoutPrompt {
    data object None : LogoutPrompt

    /** Trying a last sync before deciding whether anything would be lost. */
    data object Checking : LogoutPrompt
    data class UnsyncedChanges(val count: Int) : LogoutPrompt
}

/** Confirmation for «Delete all data» (local only, never the server). */
data class DeleteDataPrompt(val loggedIn: Boolean, val unsyncedChanges: Int)

/** Feedback after a CSV export/import or a data deletion. */
sealed interface DataMessage {
    data object Exported : DataMessage
    data object Deleted : DataMessage
    data class ImportedMeals(val result: ImportResult, val invalid: Int) : DataMessage
    data class ImportedWeights(val result: ImportResult, val invalid: Int) : DataMessage
    data object UnknownFile : DataMessage
    data object Failed : DataMessage
}

class SettingsViewModel(
    private val repository: AppRepository = AppGraph.repository,
    private val accountStore: AccountStore = AppGraph.account,
    private val preferences: AppPreferences = AppGraph.preferences,
    private val sync: SyncEngine = AppGraph.sync,
    private val accounts: AccountController = AppGraph.accounts,
) : ViewModel() {

    /** Unsaved edit shown while typing (possibly invalid); null = show what is stored. */
    private val draft = MutableStateFlow<CalorieProfile?>(null)

    val state: StateFlow<SettingsUiState> =
        combine(repository.profile(), draft, repository.weights()) { stored, draft, weights ->
            val profile = draft ?: stored
            val ready = recommend(weights, profile) as? RecommendationsUiState.Ready
            SettingsUiState.Loaded(profile, ready?.calorie, ready?.protein) as SettingsUiState
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    val templates: StateFlow<List<TemplateDTO>> = repository.templates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val theme: StateFlow<ThemeMode> = preferences.theme

    private val offline = MutableStateFlow(false)

    val account: StateFlow<AccountUiState> = combine(
        accountStore.account,
        repository.pendingChanges(),
        sync.running,
        sync.lastProblem,
        offline,
    ) { account, pending, running, problem, offline ->
        AccountUiState(account, pending, running, problem, offline)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(accountStore.current))

    private val _logoutPrompt = MutableStateFlow<LogoutPrompt>(LogoutPrompt.None)
    val logoutPrompt: StateFlow<LogoutPrompt> = _logoutPrompt.asStateFlow()

    private val _dataMessage = MutableStateFlow<DataMessage?>(null)
    val dataMessage: StateFlow<DataMessage?> = _dataMessage.asStateFlow()

    private val _deletePrompt = MutableStateFlow<DeleteDataPrompt?>(null)
    val deletePrompt: StateFlow<DeleteDataPrompt?> = _deletePrompt.asStateFlow()

    private var saveJob: Job? = null

    fun setTheme(mode: ThemeMode) = preferences.setTheme(mode)

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
        val current = state.value as? SettingsUiState.Loaded ?: return
        updateProfile(current.profile.copy(calorieGoal = goal))
    }

    // --- Templates ---

    fun saveTemplate(id: String?, request: TemplateRequest) {
        viewModelScope.launch { repository.saveTemplate(id, request) }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch { repository.deleteTemplate(id) }
    }

    // --- CSV (web-compatible format) ---

    suspend fun mealsCsv(): String = CsvBackup.mealsCsv(repository.allMeals().first())

    suspend fun weightsCsv(): String = CsvBackup.weightsCsv(repository.weights().first())

    fun onExported(success: Boolean) {
        _dataMessage.value = if (success) DataMessage.Exported else DataMessage.Failed
    }

    /** Imports a meals or weights CSV (detected from its header). */
    fun importCsv(readText: suspend () -> String) {
        viewModelScope.launch {
            _dataMessage.value = try {
                when (val parsed = withContext(Dispatchers.Default) { CsvBackup.parse(readText()) }) {
                    is CsvBackup.Parsed.Meals -> DataMessage.ImportedMeals(repository.importMeals(parsed.meals), parsed.invalidRows)
                    is CsvBackup.Parsed.Weights -> DataMessage.ImportedWeights(repository.importWeights(parsed.weights), parsed.invalidRows)
                    CsvBackup.Parsed.Unknown -> DataMessage.UnknownFile
                }
            } catch (e: Exception) {
                DataMessage.Failed
            }
        }
    }

    fun dismissDataMessage() {
        _dataMessage.value = null
    }

    // --- Delete all data (this phone only) ---

    fun requestDeleteData() {
        viewModelScope.launch {
            val loggedIn = accountStore.current != null
            val unsynced = if (loggedIn) repository.pendingChanges().first() else 0
            _deletePrompt.value = DeleteDataPrompt(loggedIn, unsynced)
        }
    }

    fun confirmDeleteData() {
        _deletePrompt.value = null
        viewModelScope.launch {
            saveJob?.cancel()
            draft.value = null
            accounts.deleteLocalData()
            _dataMessage.value = DataMessage.Deleted
        }
    }

    fun dismissDeleteData() {
        _deletePrompt.value = null
    }

    // --- Account ---

    fun syncNow() {
        viewModelScope.launch { offline.value = sync.sync() == SyncOutcome.Offline }
    }

    /** Syncs one last time; logs out directly if nothing would be lost, otherwise asks. */
    fun requestLogout() {
        _logoutPrompt.value = LogoutPrompt.Checking
        viewModelScope.launch {
            sync.sync()
            val pending = repository.pendingChanges().first()
            if (pending == 0) confirmLogout() else _logoutPrompt.value = LogoutPrompt.UnsyncedChanges(pending)
        }
    }

    fun confirmLogout() {
        viewModelScope.launch {
            accounts.logout()
            draft.value = null
            offline.value = false
            _logoutPrompt.value = LogoutPrompt.None
        }
    }

    fun dismissLogout() {
        _logoutPrompt.value = LogoutPrompt.None
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
