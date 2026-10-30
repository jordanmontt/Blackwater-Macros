package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.data.Account
import com.blackwatermacros.app.data.AccountController
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppPreferences
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.core.buildBackup
import com.blackwatermacros.app.data.BackupContents
import com.blackwatermacros.app.data.BackupImport
import com.blackwatermacros.app.data.local.isoUtc
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.TemplateRequest
import com.blackwatermacros.app.data.ThemeMode
import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncOutcome
import com.blackwatermacros.app.data.sync.SyncProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the «Cuenta» card shows. `account == null` = local-only mode. */
data class AccountUiState(
    val account: Account? = null,
    val pendingChanges: Int = 0,
    val syncing: Boolean = false,
    val problem: SyncProblem? = null,
    /** The last manual sync found no connection. */
    val offline: Boolean = false,
    /** Other accounts on this phone (Android only), to offer «Cambiar de cuenta». */
    val otherAccounts: Int = 0,
)

sealed interface LogoutPrompt {
    data object None : LogoutPrompt

    /** Trying a last sync before deciding whether anything would be lost. */
    data object Checking : LogoutPrompt
    data class UnsyncedChanges(val count: Int) : LogoutPrompt
}

/** Confirmation for «Delete all data» (local only, never the server). */
data class DeleteDataPrompt(val loggedIn: Boolean, val unsyncedChanges: Int)

/** Feedback after an export, an import or a data deletion. */
sealed interface DataMessage {
    /** [fileName]: what the file was saved as (the user can rename it in the picker). */
    data class Exported(val fileName: String) : DataMessage
    data object Imported : DataMessage
    data object Deleted : DataMessage
    data object NotBackup : DataMessage
    data object NewerFormat : DataMessage
    data class Invalid(val where: String) : DataMessage
    data object Failed : DataMessage
}

class SettingsViewModel(
    private val repository: AppRepository = AppGraph.repository,
    private val accountStore: AccountStore = AppGraph.account,
    private val preferences: AppPreferences = AppGraph.preferences,
    private val sync: SyncEngine = AppGraph.sync,
    private val accounts: AccountController = AppGraph.accounts,
    private val accountCount: Flow<Int> = AppGraph.profiles.summariesFlow().map { it.size },
    /** Logs out of the active account; with others on the phone, one of them becomes active. */
    private val logout: suspend () -> Unit = { AppGraph.profiles.logoutActive() },
) : ViewModel() {

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
        accountCount,
    ) { values ->
        AccountUiState(
            account = values[0] as Account?,
            pendingChanges = values[1] as Int,
            syncing = values[2] as Boolean,
            problem = values[3] as SyncProblem?,
            offline = values[4] as Boolean,
            otherAccounts = ((values[5] as Int) - 1).coerceAtLeast(0),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(accountStore.current))

    private val _logoutPrompt = MutableStateFlow<LogoutPrompt>(LogoutPrompt.None)
    val logoutPrompt: StateFlow<LogoutPrompt> = _logoutPrompt.asStateFlow()

    private val _dataMessage = MutableStateFlow<DataMessage?>(null)
    val dataMessage: StateFlow<DataMessage?> = _dataMessage.asStateFlow()

    private val _deletePrompt = MutableStateFlow<DeleteDataPrompt?>(null)
    val deletePrompt: StateFlow<DeleteDataPrompt?> = _deletePrompt.asStateFlow()

    /** A file read and checked, waiting for «Replace» (it replaces the phone's data). */
    private val _importPrompt = MutableStateFlow<BackupContents?>(null)
    val importPrompt: StateFlow<BackupContents?> = _importPrompt.asStateFlow()


    fun setTheme(mode: ThemeMode) = preferences.setTheme(mode)

    // --- Templates ---

    fun saveTemplate(id: String?, request: TemplateRequest) {
        viewModelScope.launch { repository.saveTemplate(id, request) }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch { repository.deleteTemplate(id) }
    }

    fun restoreTemplate(template: TemplateDTO) {
        viewModelScope.launch { repository.restoreTemplate(template) }
    }

    // --- Export file (core `Backup.kt`; the same file as the web's) ---

    suspend fun backupText(): String = buildBackup(repository.backupData(), isoUtc(java.time.Instant.now()))

    fun onExported(success: Boolean, fileName: String) {
        _dataMessage.value = if (success) DataMessage.Exported(fileName) else DataMessage.Failed
    }

    /** Reads and checks a file; a valid one waits for the confirmation. Without an account only. */
    fun readImport(readText: suspend () -> String) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { BackupImport.parse(readText()) }
            } catch (e: Exception) {
                null
            }
            when (result) {
                is BackupImport.Result.Ok -> _importPrompt.value = result.contents
                BackupImport.Result.NotBackup -> _dataMessage.value = DataMessage.NotBackup
                BackupImport.Result.NewerFormat -> _dataMessage.value = DataMessage.NewerFormat
                is BackupImport.Result.Invalid -> _dataMessage.value = DataMessage.Invalid(result.where)
                null -> _dataMessage.value = DataMessage.Failed
            }
        }
    }

    fun confirmImport() {
        val contents = _importPrompt.value ?: return
        _importPrompt.value = null
        viewModelScope.launch {
            _dataMessage.value = try {
                repository.replaceAll(contents)
                DataMessage.Imported
            } catch (e: Exception) {
                DataMessage.Failed
            }
        }
    }

    fun dismissImport() {
        _importPrompt.value = null
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
            logout()
            offline.value = false
            _logoutPrompt.value = LogoutPrompt.None
        }
    }

    fun dismissLogout() {
        _logoutPrompt.value = LogoutPrompt.None
    }
}
