package com.blackwatermacros.app.ui

import com.blackwatermacros.app.BuildConfig
import androidx.core.net.toUri
import androidx.compose.material.icons.filled.Code
import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.backupFileName
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.BackupContents
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.ThemeMode
import com.blackwatermacros.app.data.WireEntryMode
import com.blackwatermacros.app.data.sync.SyncProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)

/** The public repository (AGPL: the source of what runs, one tap away). */
const val SOURCE_CODE_URL = "https://github.com/jordanmontt/Blackwater-Macros"

/** Donations (Android only: the app people install; F-Droid lists it too). */
const val DONATE_URL = "https://ko-fi.com/jordanmontt"

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenMetodologia: () -> Unit,
    onOpenAdmin: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val logoutPrompt by viewModel.logoutPrompt.collectAsStateWithLifecycle()
    val dataMessage by viewModel.dataMessage.collectAsStateWithLifecycle()
    val deletePrompt by viewModel.deletePrompt.collectAsStateWithLifecycle()
    val importPrompt by viewModel.importPrompt.collectAsStateWithLifecycle()
    var deletingTemplate by remember { mutableStateOf<TemplateDTO?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val templateDeletedMessage = stringResource(R.string.template_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    var editingTemplate by remember { mutableStateOf<TemplateDTO?>(null) }
    var templateFormOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { CenteredTopAppBar(title = stringResource(R.string.tab_settings), leading = { AccountSwitcherButton() }) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            val context = LocalContext.current
            fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            AccountGroup(
                state = account,
                loggingOut = logoutPrompt == LogoutPrompt.Checking,
                onLogin = onOpenLogin,
                onAddAccount = LocalOpenAddAccount.current,
                onSyncNow = viewModel::syncNow,
                onLogout = viewModel::requestLogout,
            )
            SettingsGroup(stringResource(R.string.settings_group_personal)) {
                SettingsRow(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.profile_title),
                    subtitle = stringResource(R.string.profile_link_subtitle),
                    onClick = onOpenProfile,
                    trailing = { RowArrow() },
                )
                SettingsRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.ai_title),
                    subtitle = stringResource(R.string.ai_link_subtitle),
                    onClick = onOpenAi,
                    trailing = { RowArrow() },
                )
                TemplatesRows(
                    templates = templates,
                    onCreate = {
                        editingTemplate = null
                        templateFormOpen = true
                    },
                    onEdit = { template ->
                        editingTemplate = template
                        templateFormOpen = true
                    },
                    onDelete = { deletingTemplate = it },
                )
            }
            DataGroup(viewModel = viewModel, message = dataMessage, loggedIn = account.account != null)
            SettingsGroup(stringResource(R.string.settings_group_app)) {
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.appearance_title),
                    below = {
                        SegmentedChoice(
                            options = listOf(
                                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                                ThemeMode.DARK to stringResource(R.string.theme_dark),
                            ),
                            selected = theme,
                            onSelect = viewModel::setTheme,
                        )
                    },
                )
                LanguageRow()
                UpdateCheckRow()
                SettingsRow(
                    icon = Icons.Filled.PlayCircle,
                    title = stringResource(R.string.onboarding_replay),
                    subtitle = stringResource(R.string.onboarding_replay_hint),
                    onClick = onOpenTutorial,
                    trailing = { RowArrow() },
                )
            }
            SettingsGroup(stringResource(R.string.settings_group_about)) {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.methodology),
                    subtitle = stringResource(R.string.methodology_subtitle),
                    onClick = onOpenMetodologia,
                    trailing = { RowArrow() },
                )
                SettingsRow(
                    icon = Icons.Filled.Public,
                    title = stringResource(R.string.website),
                    subtitle = stringResource(R.string.website_hint),
                    onClick = { open(BuildConfig.WEBSITE_URL) },
                    trailing = { RowArrow() },
                )
                SettingsRow(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.source_code),
                    subtitle = stringResource(R.string.source_code_hint) + " v" + BuildConfig.VERSION_NAME,
                    onClick = { open(SOURCE_CODE_URL) },
                    trailing = { RowArrow() },
                )
                SettingsRow(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.donate),
                    subtitle = stringResource(R.string.donate_hint),
                    onClick = { open(DONATE_URL) },
                    trailing = { RowArrow() },
                )
                if (account.account?.isAdmin == true) {
                    SettingsRow(
                        icon = Icons.Filled.Shield,
                        title = stringResource(R.string.admin_title),
                        subtitle = stringResource(R.string.admin_subtitle),
                        onClick = onOpenAdmin,
                        trailing = { RowArrow() },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    (logoutPrompt as? LogoutPrompt.UnsyncedChanges)?.let { prompt ->
        ConfirmDialog(
            title = stringResource(R.string.logout_title),
            text = pluralStringResource(R.plurals.logout_unsynced, prompt.count, prompt.count),
            confirmLabel = stringResource(R.string.logout),
            onConfirm = viewModel::confirmLogout,
            onCancel = viewModel::dismissLogout,
        )
    }

    deletePrompt?.let { prompt ->
        val body = stringResource(if (prompt.loggedIn) R.string.data_delete_body_account else R.string.data_delete_body_local)
        val unsynced = if (prompt.unsyncedChanges > 0) {
            "\n\n" + pluralStringResource(R.plurals.data_delete_unsynced, prompt.unsyncedChanges, prompt.unsyncedChanges)
        } else {
            ""
        }
        ConfirmDialog(
            title = stringResource(R.string.data_delete_title),
            text = body + unsynced,
            confirmLabel = stringResource(R.string.data_delete_confirm),
            onConfirm = viewModel::confirmDeleteData,
            onCancel = viewModel::dismissDeleteData,
        )
    }

    importPrompt?.let { contents ->
        ConfirmDialog(
            title = stringResource(R.string.data_import_title),
            text = stringResource(R.string.data_import_body, importSummary(contents)),
            confirmLabel = stringResource(R.string.data_import_confirm),
            onConfirm = viewModel::confirmImport,
            onCancel = viewModel::dismissImport,
        )
    }

    deletingTemplate?.let { template ->
        ConfirmDialog(
            title = stringResource(R.string.template_delete_title),
            text = stringResource(R.string.template_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.deleteTemplate(template.id)
                deletingTemplate = null
                snackbarScope.offerUndo(snackbarHostState, templateDeletedMessage, undoLabel) {
                    viewModel.restoreTemplate(template)
                }
            },
            onCancel = { deletingTemplate = null },
        )
    }

    if (templateFormOpen) {
        val editing = editingTemplate
        MealFormSheet(
            heading = stringResource(if (editing != null) R.string.template_edit else R.string.template_new),
            initial = editing?.toFormValue(),
            onDismiss = { templateFormOpen = false },
            onSubmit = { value ->
                viewModel.saveTemplate(editing?.id, value.toTemplateRequest())
                templateFormOpen = false
            },
        )
    }
}

// --- Account ---

@Composable
private fun AccountGroup(
    state: AccountUiState,
    loggingOut: Boolean,
    onLogin: () -> Unit,
    onAddAccount: () -> Unit,
    onSyncNow: () -> Unit,
    onLogout: () -> Unit,
) {
    val account = state.account
    var switching by rememberSaveable { mutableStateOf(false) }
    if (switching) AccountSwitcherSheet(onDismiss = { switching = false })
    SettingsGroup(stringResource(R.string.settings_group_account)) {
        if (account == null) {
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Login,
                title = stringResource(R.string.account_login),
                subtitle = stringResource(R.string.account_local_description),
                onClick = onLogin,
                color = MaterialTheme.colorScheme.primary,
                trailing = { RowArrow() },
            )
            return@SettingsGroup
        }

        // Same rule as the header cloud: a failed run only matters while changes are waiting,
        // and only a refused change is red (the rest is retried on its own).
        val problem = state.problem?.takeIf { state.pendingChanges > 0 }
        val (status, isProblem) = when {
            account.sessionExpired -> stringResource(R.string.sync_session_expired) to true
            state.syncing -> stringResource(R.string.sync_in_progress) to false
            problem != null -> stringResource(problem.messageRes()) to problem.needsAttention(state.pendingChanges)
            state.offline -> stringResource(R.string.sync_offline) to false
            state.pendingChanges > 0 ->
                pluralStringResource(R.plurals.sync_pending, state.pendingChanges, state.pendingChanges) to false
            account.lastSyncAt != null -> stringResource(R.string.sync_done, syncTimePhrase(account.lastSyncAt)) to false
            else -> stringResource(R.string.sync_never) to false
        }
        SettingsRow(
            icon = when {
                isProblem -> Icons.Filled.CloudOff
                state.pendingChanges > 0 -> Icons.Filled.CloudUpload
                else -> Icons.Filled.CloudDone
            },
            title = stringResource(R.string.account_connected_as, account.username),
            subtitle = status,
            subtitleColor = if (isProblem) MaterialTheme.colorScheme.error else Color.Unspecified,
            trailing = if (state.syncing) {
                { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
        )
        if (account.sessionExpired) {
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Login,
                title = stringResource(R.string.account_login),
                onClick = onLogin,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            SettingsRow(
                icon = Icons.Filled.Sync,
                title = stringResource(R.string.sync_now),
                onClick = onSyncNow,
                enabled = !state.syncing,
            )
        }
        // Several accounts on one phone (Android only): switch, or add one.
        if (state.otherAccounts > 0) {
            SettingsRow(
                icon = Icons.Filled.SwitchAccount,
                title = stringResource(R.string.account_switch),
                onClick = { switching = true },
            )
        } else {
            SettingsRow(icon = Icons.Filled.PersonAdd, title = stringResource(R.string.account_add), onClick = onAddAccount)
        }
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.Logout,
            title = stringResource(if (loggingOut) R.string.logging_out else R.string.logout),
            onClick = onLogout,
            enabled = !loggingOut,
        )
    }
}

private fun SyncProblem.messageRes(): Int = when (this) {
    SyncProblem.SERVER_ERROR -> R.string.sync_problem_server
    SyncProblem.CHANGE_REJECTED -> R.string.sync_problem_rejected
    SyncProblem.UNEXPECTED -> R.string.sync_problem_unexpected
}

/** "today at 14:32" / "on 3 Sep at 09:10", in the phone's timezone. */
@Composable
private fun syncTimePhrase(iso: String): String {
    val day = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = formatTime(iso)
    return if (day == LocalDate.now()) {
        stringResource(R.string.sync_today_at, time)
    } else {
        stringResource(R.string.sync_on_date_at, formatDateShort(day.toString()), time)
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
    Spacer(Modifier.width(6.dp))
}

// --- Templates ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatesRows(
    templates: List<TemplateDTO>,
    onCreate: () -> Unit,
    onEdit: (TemplateDTO) -> Unit,
    onDelete: (TemplateDTO) -> Unit,
) {
    // Collapsed by default: with many templates the list would take the whole page.
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        icon = Icons.Filled.Bookmarks,
        title = stringResource(R.string.templates_title),
        subtitle = pluralStringResource(R.plurals.templates_count, templates.size, templates.size),
        onClick = { expanded = !expanded },
        trailing = {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
    if (!expanded) return
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        OutlinedButton(onClick = onCreate) {
            ButtonIcon(Icons.Filled.Add, MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.template_new), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))

        if (templates.isEmpty()) {
            CardDescription(stringResource(R.string.templates_empty))
        }
        templates.forEachIndexed { i, template ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(template.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        CardDescription(
                            if (template.entryMode == WireEntryMode.TOTAL_ONLY) {
                                stringResource(R.string.meal_manual_total)
                            } else {
                                pluralStringResource(R.plurals.ingredient_count, template.ingredients.size, template.ingredients.size)
                            },
                        )
                    }
                    IconButton(onClick = { onEdit(template) }) {
                        Icon(
                            Icons.Filled.Edit,
                            stringResource(R.string.action_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { onDelete(template) }) {
                        Icon(
                            Icons.Filled.Delete,
                            stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NutritionBadge("${formatNumber(template.resolvedCalories)} kcal", secondary = true)
                    NutritionBadge("${formatNumber(template.resolvedProtein)} g · ${stringResource(R.string.macro_protein)}")
                    NutritionBadge("${formatNumber(template.resolvedCarbs)} g · ${stringResource(R.string.macro_carbs)}")
                    NutritionBadge("${formatNumber(template.resolvedFat)} g · ${stringResource(R.string.macro_fat)}")
                }
            }
        }
    }
}

@Composable
private fun NutritionBadge(text: String, secondary: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (secondary) colors.onSecondaryContainer else colors.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .background(if (secondary) colors.secondaryContainer else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.dp, if (secondary) Color.Transparent else colors.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// --- Data (the export file) ---

@Composable
private fun DataGroup(viewModel: SettingsViewModel, message: DataMessage?, loggedIn: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching { writeText(context, uri, viewModel.backupText()) }.isSuccess
            viewModel.onExported(ok)
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readImport { readText(context, uri) }
    }

    SettingsGroup(stringResource(R.string.data_title), footer = stringResource(R.string.data_description)) {
        SettingsRow(Icons.Filled.Download, stringResource(R.string.data_export), onClick = { export.launch(backupFileName(todayKey())) })
        // With an account the data is the server's: an import would upload, so it is only offered without one.
        SettingsRow(
            Icons.Filled.Upload,
            stringResource(R.string.data_import),
            subtitle = stringResource(if (loggedIn) R.string.data_import_hint_account else R.string.data_import_hint_local),
            enabled = !loggedIn,
            onClick = { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        )
        SettingsRow(
            Icons.Filled.DeleteForever,
            stringResource(R.string.data_delete_button),
            onClick = viewModel::requestDeleteData,
            color = MaterialTheme.colorScheme.error,
        )
        message?.let {
            val isError = it !is DataMessage.Exported && it !is DataMessage.Imported && it !is DataMessage.Deleted
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dataMessageText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::dismissDataMessage) { Text(stringResource(R.string.action_ok)) }
            }
        }
    }
}

@Composable
private fun dataMessageText(message: DataMessage): String = when (message) {
    DataMessage.Exported -> stringResource(R.string.data_exported)
    DataMessage.Imported -> stringResource(R.string.data_imported)
    DataMessage.Deleted -> stringResource(R.string.data_deleted)
    DataMessage.NotBackup -> stringResource(R.string.data_import_not_backup)
    DataMessage.NewerFormat -> stringResource(R.string.data_import_newer)
    is DataMessage.Invalid -> stringResource(R.string.data_import_damaged, message.where)
    DataMessage.Failed -> stringResource(R.string.data_failed)
}

/** «412 meals, 96 weigh-ins and 5 templates, from 2 Nov 2025 to 26 Oct 2026». */
@Composable
private fun importSummary(contents: BackupContents): String {
    val counts = stringResource(
        R.string.data_import_counts,
        pluralStringResource(R.plurals.data_import_meals, contents.meals.size, contents.meals.size),
        pluralStringResource(R.plurals.data_import_weigh_ins, contents.weights.size, contents.weights.size),
        pluralStringResource(R.plurals.data_import_templates, contents.templates.size, contents.templates.size),
    )
    val first = contents.firstDay
    val last = contents.lastDay
    return if (first == null || last == null) {
        counts
    } else {
        stringResource(R.string.data_import_span, counts, formatDateWithYear(first), formatDateWithYear(last))
    }
}

private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

// --- Language ---

@Composable
private fun LanguageRow() {
    val chosen = remember { chosenAppLanguage() }
    var open by remember { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.language_system)
    val currentLabel = AppLanguages.firstOrNull { it.first == chosen }?.second ?: systemLabel
    SettingsRow(
        icon = Icons.Filled.Language,
        title = stringResource(R.string.language_title),
        subtitle = currentLabel,
        onClick = { open = true },
        trailing = { RowArrow() },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column {
                    (listOf<Pair<String?, String>>(null to systemLabel) + AppLanguages).forEach { (tag, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    open = false
                                    if (tag != chosen) setAppLanguage(tag)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = tag == chosen, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
