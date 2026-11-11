package com.blackwatermacros.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.R
import com.blackwatermacros.app.data.update.CheckState

/** Comidas, under the date: «Version X is available» · Download (the website) · Not now. */
@Composable
internal fun UpdateNotice() {
    val updates = AppGraph.updates
    val version by updates.available.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val latest = version ?: return
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp),
    ) {
        Icon(
            Icons.Filled.SystemUpdate,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_available, latest), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.offset(x = (-8).dp)) {
                TextButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.WEBSITE_URL.toUri())) } },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.update_download), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = updates::dismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        stringResource(R.string.check_not_now),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Ajustes: one row for updates, only in the website's APK (F-Droid updates its own).
 * The switch is the daily check; «Check now» asks right away, with the switch on
 * or off, and the row says what it found: up to date, a newer version (always the
 * newest known, with Download) or that it couldn't check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdateCheckRow() {
    val updates = AppGraph.updates
    if (!updates.ownBuild) return
    val enabled by updates.enabled.collectAsStateWithLifecycle()
    val newer by updates.newer.collectAsStateWithLifecycle()
    val check by updates.check.collectAsStateWithLifecycle()
    val lastChecked by updates.lastChecked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val latest = newer
    val (status, statusColor) = when {
        check == CheckState.FAILED -> stringResource(R.string.update_check_failed) to colors.error
        latest != null -> stringResource(R.string.update_available, latest) to colors.primary
        check == CheckState.UP_TO_DATE -> stringResource(R.string.update_up_to_date, updates.installed) to colors.onSurface
        else -> lastChecked?.let { stringResource(R.string.update_last_checked, formatDateTimeShort(it)) } to colors.onSurfaceVariant
    }
    SettingsRow(
        icon = Icons.Filled.SystemUpdate,
        title = stringResource(R.string.update_check_title),
        subtitle = stringResource(R.string.update_check_hint),
        onClick = { updates.setEnabled(!enabled) },
        trailing = { Switch(checked = enabled, onCheckedChange = updates::setEnabled) },
        below = {
            Column {
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                // Download and Check now wrap onto two lines when the words are long (German).
                FlowRow(
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = if (latest != null) Modifier.padding(top = 8.dp) else Modifier.offset(x = (-12).dp),
                ) {
                    if (latest != null) {
                        Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.WEBSITE_URL.toUri())) } }) {
                            Icon(Icons.Filled.Download, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.update_download))
                        }
                    }
                    if (check == CheckState.CHECKING) {
                        TextButton(onClick = {}, enabled = false) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.update_checking))
                        }
                    } else {
                        TextButton(onClick = AppGraph::checkForUpdatesNow) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (check == CheckState.FAILED) R.string.common_retry else R.string.update_check_now))
                        }
                    }
                }
            }
        },
    )
}
