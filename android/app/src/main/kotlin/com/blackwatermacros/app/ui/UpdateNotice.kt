package com.blackwatermacros.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.R

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

/** Ajustes: the «Check for updates» switch, only in the website's APK (F-Droid updates its own). */
@Composable
internal fun UpdateCheckCard() {
    val updates = AppGraph.updates
    if (!updates.ownBuild) return
    val enabled by updates.enabled.collectAsStateWithLifecycle()
    SettingsCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_check_title), style = MaterialTheme.typography.bodyMedium)
                CardDescription(stringResource(R.string.update_check_hint))
            }
            Switch(checked = enabled, onCheckedChange = updates::setEnabled)
        }
    }
}
