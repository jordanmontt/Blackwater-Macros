package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.R
import com.blackwatermacros.app.data.ProfileSummary

/**
 * Several accounts on one phone, switched with a tap (Android only, see
 * [com.blackwatermacros.app.data.AccountProfiles]). With two or more accounts every tab
 * header shows the active one's initial; tapping it opens the switcher.
 */

/** Opens the login screen in «Añadir cuenta» mode (provided by the NavHost). */
val LocalOpenAddAccount = staticCompositionLocalOf<() -> Unit> { {} }

/** Header avatar of the active account; nothing with fewer than two accounts. */
@Composable
fun AccountSwitcherButton() {
    val profiles = AppGraph.profiles
    val accounts by remember { profiles.summariesFlow() }.collectAsStateWithLifecycle(profiles.summaries())
    val activeId by profiles.activeId.collectAsStateWithLifecycle()
    if (accounts.size < 2) return
    val active = accounts.firstOrNull { it.id == activeId } ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.account_switch_cd, active.username)
    Box(
        Modifier
            .padding(start = 12.dp)
            .size(40.dp)
            .clip(CircleShape)
            .clickable { open = true }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AccountAvatar(active.username, size = 32.dp)
    }
    if (open) AccountSwitcherSheet(onDismiss = { open = false })
}

/**
 * The accounts on the phone (✓ on the active one) and «Añadir cuenta». Picking
 * another account makes it active; MainActivity then restarts on its data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(onDismiss: () -> Unit) {
    val profiles = AppGraph.profiles
    val accounts by remember { profiles.summariesFlow() }.collectAsStateWithLifecycle(profiles.summaries())
    val activeId by profiles.activeId.collectAsStateWithLifecycle()
    val openAddAccount = LocalOpenAddAccount.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.accounts_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            accounts.forEach { account ->
                AccountRow(account, active = account.id == activeId) {
                    onDismiss()
                    profiles.switchTo(account.id)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        openAddAccount()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.account_add), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun AccountRow(account: ProfileSummary, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !active, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(account.username, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                account.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (account.sessionExpired) {
                Text(
                    stringResource(R.string.account_session_expired_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** The account's initial in a filled circle (no photos: accounts have none). */
@Composable
fun AccountAvatar(username: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            username.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onPrimary,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
