package com.blackwatermacros.app.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.DEFAULT_MODELS

const val AI_STUDIO_URL = "https://aistudio.google.com/api-keys"

/** Ajustes → Inteligencia artificial (§4.4 of docs/AI-PLAN.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit, viewModel: AiSettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val provider = settings.provider
    val custom = provider == AiProvider.CUSTOM
    var showKey by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenteredTopAppBar(
                title = stringResource(R.string.ai_title),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsCard(null) { CardDescription(stringResource(R.string.ai_description)) }

            SettingsCard(stringResource(R.string.ai_provider)) {
                var menuExpanded by rememberSaveable { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = !menuExpanded }) {
                    val label = stringResource(provider.labelRes())
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
                        AiProvider.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes())) },
                                onClick = {
                                    viewModel.setProvider(option)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
                if (provider == AiProvider.GEMINI) {
                    Spacer(Modifier.height(8.dp))
                    FreeKeyGuide()
                }
            }

            SettingsCard(null) {
                if (custom) {
                    FieldLabel(stringResource(R.string.ai_base_url))
                    CompactField(
                        value = settings.baseUrl,
                        onValueChange = viewModel::setBaseUrl,
                        placeholder = "http://192.168.1.10:11434/v1",
                        keyboardType = KeyboardType.Uri,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    CardDescription(stringResource(R.string.ai_base_url_hint))
                    Spacer(Modifier.height(4.dp))
                    CardDescription(stringResource(R.string.ai_local_hint))
                    Spacer(Modifier.height(12.dp))
                }
                FieldLabel(stringResource(if (custom) R.string.ai_api_key_optional else R.string.ai_api_key))
                CompactField(
                    value = settings.apiKeys[provider].orEmpty(),
                    onValueChange = viewModel::setApiKey,
                    placeholder = "",
                    secret = !showKey,
                    keyboardType = if (showKey) KeyboardType.Ascii else null,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(if (showKey) R.string.ai_hide_key else R.string.ai_show_key),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                ModelPicker(viewModel, provider, settings.models[provider].orEmpty(), settings.ready)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = viewModel::runTest, enabled = test != AiTestState.Testing) {
                        if (test == AiTestState.Testing) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (test == AiTestState.Testing) R.string.ai_testing else R.string.ai_test))
                    }
                    when (val state = test) {
                        AiTestState.Ok -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_test_ok), style = MaterialTheme.typography.bodySmall)
                        }
                        is AiTestState.Failed -> Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(state.failure.messageRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (state.detail.isNotBlank()) {
                                Text(state.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> Unit
                    }
                }
            }

            LocalModelSection(viewModel)

            SettingsCard(null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setCoachSeesData(!settings.coachSeesData) },
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(checked = settings.coachSeesData, onCheckedChange = null, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.ai_coach_sees_data),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        CardDescription(stringResource(R.string.ai_coach_sees_data_hint))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** «Cómo conseguir una clave gratis de Google»: collapsed until tapped. Also used by onboarding. */
@Composable
internal fun FreeKeyGuide(initiallyOpen: Boolean = false) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.ai_free_key_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (!open) return
    Text(stringResource(R.string.ai_free_key_steps), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, AI_STUDIO_URL.toUri())) }
    }) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.ai_free_key_open))
    }
    Spacer(Modifier.height(8.dp))
    CardDescription(stringResource(R.string.ai_free_key_privacy))
}

/**
 * «Modelo» (web `ModelPicker`): the models the provider lists for this key,
 * «Predeterminado» first and «Otro…» to type a name. Without a list (no key
 * yet, offline, a server that does not list) it is the plain text field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(viewModel: AiSettingsViewModel, provider: AiProvider, saved: String, keyReady: Boolean) {
    val options by viewModel.modelOptions.collectAsStateWithLifecycle()
    val status by viewModel.modelListStatus.collectAsStateWithLifecycle()
    var typing by rememberSaveable(provider) { mutableStateOf(false) }
    val defaultModel = DEFAULT_MODELS.getValue(provider)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { FieldLabel(stringResource(R.string.ai_model)) }
        if (keyReady) {
            IconButton(onClick = { viewModel.refreshModels(force = true) }, enabled = status != ModelListStatus.Loading) {
                if (status == ModelListStatus.Loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.ai_model_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (options.isNotEmpty() && !typing) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        val defaultLabel = stringResource(R.string.ai_model_default, defaultModel.ifEmpty { "—" })
        val label = when {
            saved.isEmpty() -> defaultLabel
            else -> options.firstOrNull { it.id == saved }?.label ?: saved
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
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
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                DropdownMenuItem(text = { Text(defaultLabel) }, onClick = { viewModel.setModel(""); expanded = false })
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.label)
                                if (option.label != option.id) {
                                    Text(option.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        onClick = { viewModel.setModel(option.id); expanded = false },
                    )
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.ai_model_other)) }, onClick = { typing = true; expanded = false })
            }
        }
    } else {
        CompactField(
            value = saved,
            onValueChange = viewModel::setModel,
            placeholder = defaultModel,
            keyboardType = KeyboardType.Ascii,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(4.dp))
    val failed = status as? ModelListStatus.Failed
    CardDescription(
        when {
            status == ModelListStatus.Loading -> stringResource(R.string.ai_model_list_loading)
            failed != null -> stringResource(R.string.ai_model_list_error, stringResource(failed.failure.messageRes()))
            options.isNotEmpty() ->
                pluralStringResource(R.plurals.ai_model_list_count, options.size, options.size) + " " + stringResource(R.string.ai_model_limits)
            !keyReady -> stringResource(R.string.ai_model_list_needs_key)
            defaultModel.isNotEmpty() -> stringResource(R.string.ai_model_hint, defaultModel)
            else -> ""
        },
    )
}
