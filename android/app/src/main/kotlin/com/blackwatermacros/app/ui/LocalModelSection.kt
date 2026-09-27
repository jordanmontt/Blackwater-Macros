package com.blackwatermacros.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackwatermacros.app.R
import com.blackwatermacros.app.data.ai.AiEngineChoice
import com.blackwatermacros.app.data.ai.local.DeviceSupport
import com.blackwatermacros.app.data.ai.local.LocalModels
import com.blackwatermacros.app.data.ai.local.LocalModelState
import kotlin.math.roundToInt

/**
 * «Modelo en el dispositivo» (§4.4, D5/D6): download or delete Gemma, then
 * choose where photos and the coach run. Warnings before the 2.6 GB download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalModelSection(viewModel: AiSettingsViewModel) {
    val state by viewModel.modelState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vision by viewModel.deviceVision.collectAsStateWithLifecycle()
    val model by viewModel.selectedModel.collectAsStateWithLifecycle()
    var confirmDownload by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val sizeGb = gigabytes(model.sizeBytes)
    val ramGb = formatNumber(model.minRamBytes / 1e9, maxDecimals = 0)

    SettingsCard(stringResource(R.string.local_model_title)) {
        CardDescription(stringResource(R.string.local_model_description))
        Spacer(Modifier.height(12.dp))
        when (val current = state) {
            LocalModelState.Unsupported -> Text(
                stringResource(R.string.local_model_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LocalModelState.NotDownloaded, is LocalModelState.Failed -> {
                // Choose the model first; only one is kept on the phone.
                FieldLabel(stringResource(R.string.local_model_choose))
                Spacer(Modifier.height(6.dp))
                var menuExpanded by rememberSaveable { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = !menuExpanded }) {
                    val label = "${model.name} · ${gigabytes(model.sizeBytes)} GB"
                    CompactField(
                        value = label,
                        onValueChange = {},
                        placeholder = label,
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        LocalModels.ALL.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${option.name} · ${gigabytes(option.sizeBytes)} GB")
                                        Text(
                                            stringResource(option.noteRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.selectModel(option)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                CardDescription(stringResource(model.noteRes))
                Spacer(Modifier.height(12.dp))
                if (current is LocalModelState.Failed) {
                    Text(
                        stringResource(if (current.checksum) R.string.local_model_checksum_failed else R.string.local_model_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val support = viewModel.support(model)
                if (support == DeviceSupport.NOT_ENOUGH_RAM) {
                    Text(
                        stringResource(R.string.local_model_needs_ram, ramGb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = { confirmDownload = true }, enabled = support == DeviceSupport.SUPPORTED) {
                    Text(stringResource(R.string.local_model_download, sizeGb))
                }
            }
            is LocalModelState.Downloading -> {
                Text("${model.name} · $sizeGb GB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { current.fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    if (current.waitingForWifi) {
                        stringResource(R.string.local_model_waiting)
                    } else {
                        stringResource(R.string.local_model_progress, (current.fraction * 100).roundToInt())
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::cancelDownload) { Text(stringResource(R.string.action_cancel)) }
            }
            LocalModelState.Ready -> {
                Text("${model.name} · $sizeGb GB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.local_model_ready), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                EngineChoice(
                    label = stringResource(R.string.local_model_use_photos),
                    selected = settings.photoEngine,
                    deviceEnabled = vision == true,
                    onSelect = viewModel::setPhotoEngine,
                )
                if (vision == false) {
                    Spacer(Modifier.height(4.dp))
                    CardDescription(stringResource(R.string.local_model_no_vision))
                }
                Spacer(Modifier.height(12.dp))
                EngineChoice(
                    label = stringResource(R.string.local_model_use_coach),
                    selected = settings.coachEngine,
                    deviceEnabled = true,
                    onSelect = viewModel::setCoachEngine,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.local_model_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDownload) {
        var anyNetwork by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text(stringResource(R.string.local_model_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.local_model_warning, sizeGb, ramGb))
                    Row(
                        Modifier.fillMaxWidth().clickable { anyNetwork = !anyNetwork },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = anyNetwork, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.local_model_mobile_data), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = false
                    if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    viewModel.downloadModel(anyNetwork)
                }) { Text(stringResource(R.string.local_model_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownload = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.local_model_delete)) },
            text = { Text(stringResource(R.string.local_model_delete_body, sizeGb)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteModel()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EngineChoice(
    label: String,
    selected: AiEngineChoice,
    deviceEnabled: Boolean,
    onSelect: (AiEngineChoice) -> Unit,
) {
    FieldLabel(label)
    Spacer(Modifier.height(6.dp))
    val options = buildList {
        add(AiEngineChoice.CLOUD to stringResource(R.string.local_model_cloud))
        if (deviceEnabled) add(AiEngineChoice.DEVICE to stringResource(R.string.local_model_device))
    }
    SegmentedChoice(options, selected) { onSelect(it) }
    Spacer(Modifier.padding(0.dp))
}

private fun gigabytes(bytes: Long): String = formatNumber(bytes / 1e9, maxDecimals = 1)
