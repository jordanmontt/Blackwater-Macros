package com.blackwatermacros.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.R
import com.blackwatermacros.app.data.Account
import com.blackwatermacros.app.data.AccountStore
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.sync.SyncEngine
import com.blackwatermacros.app.data.sync.SyncProblem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the header cloud icon shows. Hidden entirely without an account. */
sealed interface SyncIndicatorState {
    data object Hidden : SyncIndicatorState
    data object Syncing : SyncIndicatorState
    data object Synced : SyncIndicatorState
    data class Pending(val count: Int) : SyncIndicatorState

    /** Session expired or the last sync failed: needs the user's attention. */
    data object Problem : SyncIndicatorState
}

class SyncIndicatorViewModel(
    account: AccountStore = AppGraph.account,
    repository: AppRepository = AppGraph.repository,
    sync: SyncEngine = AppGraph.sync,
) : ViewModel() {
    val state: StateFlow<SyncIndicatorState> = combine(
        account.account,
        repository.pendingChanges(),
        sync.running,
        sync.lastProblem,
        ::syncIndicatorState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncIndicatorState.Hidden)
}

internal fun syncIndicatorState(
    account: Account?,
    pendingChanges: Int,
    syncing: Boolean,
    problem: SyncProblem?,
): SyncIndicatorState = when {
    account == null -> SyncIndicatorState.Hidden
    account.sessionExpired || problem != null -> SyncIndicatorState.Problem
    syncing -> SyncIndicatorState.Syncing
    pendingChanges > 0 -> SyncIndicatorState.Pending(pendingChanges)
    else -> SyncIndicatorState.Synced
}

/**
 * Small cloud in the Comidas header: tells at a glance whether today's entries
 * reached the server. Tapping it opens Ajustes (account and sync details).
 */
@Composable
fun SyncIndicator(onClick: () -> Unit, viewModel: SyncIndicatorViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state == SyncIndicatorState.Hidden) return
    val description = when (val s = state) {
        SyncIndicatorState.Syncing -> stringResource(R.string.sync_in_progress)
        SyncIndicatorState.Synced -> stringResource(R.string.sync_indicator_synced)
        is SyncIndicatorState.Pending -> pluralStringResource(R.plurals.sync_pending, s.count, s.count)
        SyncIndicatorState.Problem -> stringResource(R.string.sync_indicator_problem)
        SyncIndicatorState.Hidden -> ""
    }
    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            when (val s = state) {
                SyncIndicatorState.Syncing ->
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                SyncIndicatorState.Synced ->
                    Icon(Icons.Filled.CloudDone, description, tint = MaterialTheme.colorScheme.primary)
                is SyncIndicatorState.Pending -> BadgedBox(badge = { Badge { Text(s.count.coerceAtMost(99).toString()) } }) {
                    Icon(Icons.Filled.CloudUpload, description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SyncIndicatorState.Problem ->
                    Icon(Icons.Filled.CloudOff, description, tint = MaterialTheme.colorScheme.error)
                SyncIndicatorState.Hidden -> Unit
            }
        }
    }
}
