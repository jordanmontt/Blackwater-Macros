package com.blackwatermacros.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.todayKey
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
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenLogin: () -> Unit,
    onOpenMetodologia: () -> Unit,
    onOpenAdmin: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val logoutPrompt by viewModel.logoutPrompt.collectAsStateWithLifecycle()
    val dataMessage by viewModel.dataMessage.collectAsStateWithLifecycle()
    val deletePrompt by viewModel.deletePrompt.collectAsStateWithLifecycle()
    var deletingTemplate by remember { mutableStateOf<TemplateDTO?>(null) }
    var editingTemplate by remember { mutableStateOf<TemplateDTO?>(null) }
    var templateFormOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenteredTopAppBar(title = stringResource(R.string.tab_settings)) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            when (val loaded = state) {
                SettingsUiState.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is SettingsUiState.Loaded -> {
                    AccountCard(
                        state = account,
                        loggingOut = logoutPrompt == LogoutPrompt.Checking,
                        onLogin = onOpenLogin,
                        onSyncNow = viewModel::syncNow,
                        onLogout = viewModel::requestLogout,
                    )
                    GoalCard(goal = loaded.profile.calorieGoal, onGoalChange = viewModel::updateCalorieGoal)
                    ProfileCard(profile = loaded.profile, onProfileChange = viewModel::updateProfile)
                    RecommendationsCard(loaded)
                    TemplatesCard(
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
                    DataCard(viewModel = viewModel, message = dataMessage)
                    AppearanceCard(theme = theme, onThemeChange = viewModel::setTheme)
                    LinkCard(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.methodology),
                        subtitle = stringResource(R.string.methodology_subtitle),
                        onClick = onOpenMetodologia,
                    )
                    if (account.account?.isAdmin == true) {
                        LinkCard(
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.admin_title),
                            subtitle = stringResource(R.string.admin_subtitle),
                            onClick = onOpenAdmin,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    (logoutPrompt as? LogoutPrompt.UnsyncedChanges)?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLogout,
            title = { Text(stringResource(R.string.logout_title)) },
            text = { Text(pluralStringResource(R.plurals.logout_unsynced, prompt.count, prompt.count)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLogout) {
                    Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLogout) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    deletePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteData,
            title = { Text(stringResource(R.string.data_delete_title)) },
            text = {
                val body = stringResource(if (prompt.loggedIn) R.string.data_delete_body_account else R.string.data_delete_body_local)
                val unsynced = if (prompt.unsyncedChanges > 0) {
                    "\n\n" + pluralStringResource(R.plurals.data_delete_unsynced, prompt.unsyncedChanges, prompt.unsyncedChanges)
                } else {
                    ""
                }
                Text(body + unsynced)
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteData) {
                    Text(stringResource(R.string.data_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteData) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    deletingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.template_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTemplate(template.id)
                        deletingTemplate = null
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTemplate = null }) { Text(stringResource(R.string.action_cancel)) }
            },
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

@Composable
private fun SettingsCard(title: String?, content: @Composable () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun CardDescription(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

// --- Account ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(
    state: AccountUiState,
    loggingOut: Boolean,
    onLogin: () -> Unit,
    onSyncNow: () -> Unit,
    onLogout: () -> Unit,
) {
    val account = state.account
    SettingsCard(stringResource(R.string.account_title)) {
        if (account == null) {
            CardDescription(stringResource(R.string.account_local_description))
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onLogin) {
                ButtonIcon(Icons.AutoMirrored.Filled.Login, MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.account_login))
            }
            return@SettingsCard
        }

        Text(stringResource(R.string.account_connected_as, account.username), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        val problem = state.problem
        val (status, isProblem) = when {
            account.sessionExpired -> stringResource(R.string.sync_session_expired) to true
            state.syncing -> stringResource(R.string.sync_in_progress) to false
            problem != null -> stringResource(problem.messageRes()) to true
            state.offline -> stringResource(R.string.sync_offline) to false
            state.pendingChanges > 0 ->
                pluralStringResource(R.plurals.sync_pending, state.pendingChanges, state.pendingChanges) to false
            account.lastSyncAt != null -> stringResource(R.string.sync_done, syncTimePhrase(account.lastSyncAt)) to false
            else -> stringResource(R.string.sync_never) to false
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.syncing) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    if (isProblem) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (account.sessionExpired) {
                OutlinedButton(onClick = onLogin) { Text(stringResource(R.string.account_login)) }
            } else {
                OutlinedButton(onClick = onSyncNow, enabled = !state.syncing) {
                    ButtonIcon(Icons.Filled.Sync)
                    Text(stringResource(R.string.sync_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = onLogout, enabled = !loggingOut) {
                ButtonIcon(Icons.AutoMirrored.Filled.Logout)
                Text(
                    stringResource(if (loggingOut) R.string.logging_out else R.string.logout),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

// --- Goal & profile ---

@Composable
private fun GoalCard(goal: Goal?, onGoalChange: (Goal) -> Unit) {
    SettingsCard(stringResource(R.string.goal_title)) {
        CardDescription(stringResource(R.string.goal_description))
        Spacer(Modifier.height(12.dp))
        SegmentedChoice(
            options = listOf(
                Goal.CUT to stringResource(R.string.goal_cut),
                Goal.MAINTAIN to stringResource(R.string.goal_maintain),
                Goal.SURPLUS to stringResource(R.string.goal_surplus),
            ),
            selected = goal,
            onSelect = onGoalChange,
        )
    }
}

@Composable
private fun ProfileCard(profile: CalorieProfile, onProfileChange: (CalorieProfile) -> Unit) {
    SettingsCard(stringResource(R.string.profile_title)) {
        CardDescription(stringResource(R.string.profile_description))
        Spacer(Modifier.height(12.dp))

        FieldLabel(stringResource(R.string.profile_gender))
        Spacer(Modifier.height(6.dp))
        SegmentedChoice(
            options = listOf(
                Gender.MALE to stringResource(R.string.gender_male),
                Gender.FEMALE to stringResource(R.string.gender_female),
            ),
            selected = profile.gender,
            onSelect = { onProfileChange(profile.copy(gender = it)) },
        )
        Spacer(Modifier.height(12.dp))

        ProfileRow {
            NumberField(
                label = stringResource(R.string.profile_birth_year),
                value = profile.birthYear?.toString().orEmpty(),
                placeholder = "1990",
                error = stringResource(R.string.error_birth_year).takeUnless { profile.birthYear.okIn(1920, 2010) },
                onValue = { onProfileChange(profile.copy(birthYear = it.trim().toIntOrNull())) },
            )
            NumberField(
                label = stringResource(R.string.profile_height),
                value = profile.heightCm?.let(::toDecimalInput).orEmpty(),
                error = stringResource(R.string.error_height).takeUnless { profile.heightCm.okIn(100.0, 250.0) },
                onValue = { onProfileChange(profile.copy(heightCm = parseDecimal(it))) },
            )
        }
        ProfileRow {
            NumberField(
                label = stringResource(R.string.profile_gym_days),
                value = profile.gymDaysPerWeek?.toString().orEmpty(),
                error = stringResource(R.string.error_gym_days).takeUnless { profile.gymDaysPerWeek.okIn(0, 7) },
                onValue = { onProfileChange(profile.copy(gymDaysPerWeek = it.trim().toIntOrNull())) },
            )
            NumberField(
                label = stringResource(R.string.profile_gym_minutes),
                value = profile.gymSessionMinutes?.toString().orEmpty(),
                error = stringResource(R.string.error_gym_minutes).takeUnless { profile.gymSessionMinutes.okIn(0, 300) },
                onValue = { onProfileChange(profile.copy(gymSessionMinutes = it.trim().toIntOrNull())) },
            )
        }
        ProfileRow {
            NumberField(
                label = stringResource(R.string.profile_walking_minutes),
                value = profile.walkingMinutesPerDay?.toString().orEmpty(),
                error = stringResource(R.string.error_walking_minutes).takeUnless { profile.walkingMinutesPerDay.okIn(0, 480) },
                onValue = { onProfileChange(profile.copy(walkingMinutesPerDay = it.trim().toIntOrNull())) },
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun <T : Comparable<T>> T?.okIn(min: T, max: T): Boolean = this == null || this in min..max

@Composable
private fun ProfileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun RowScope.NumberField(
    label: String,
    value: String,
    error: String?,
    placeholder: String = "",
    onValue: (String) -> Unit,
) {
    Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        CompactField(value = value, onValueChange = onValue, placeholder = placeholder, decimal = true, modifier = Modifier.fillMaxWidth())
        if (error != null) {
            Spacer(Modifier.height(2.dp))
            Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RecommendationsCard(loaded: SettingsUiState.Loaded) {
    val calorie = loaded.calorieRec
    val protein = loaded.proteinRec
    if (calorie == null && protein == null) return
    val kcalDay = stringResource(R.string.unit_kcal_day)
    val gDay = stringResource(R.string.unit_g_day)
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (calorie != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RecommendationSection(
                        title = stringResource(R.string.rec_calories_title),
                        icon = Icons.Filled.Whatshot,
                        goal = calorie.goal,
                        rangeValue = "${formatNumber(calorie.targetMin)} – ${formatNumber(calorie.targetMax)}",
                        rangeUnit = kcalDay,
                        detail = stringResource(R.string.rec_estimated_average, "${formatNumber(calorie.target)} $kcalDay"),
                        current = 0.0,
                        rangeMin = calorie.targetMin,
                        rangeMax = calorie.targetMax,
                        showBar = false,
                    )
                    CardDescription(stringResource(R.string.rec_bmr, formatNumber(calorie.bmr)))
                    CardDescription(stringResource(R.string.rec_tdee, formatNumber(calorie.tdee)))
                }
            }
            if (protein != null) {
                RecommendationSection(
                    title = stringResource(R.string.rec_protein_title),
                    icon = Icons.Filled.FitnessCenter,
                    goal = protein.goal,
                    rangeValue = "${formatNumber(protein.bwRange.min)} – ${formatNumber(protein.bwRange.max)}",
                    rangeUnit = gDay,
                    detail = stringResource(R.string.rec_estimated_average, "${formatNumber(protein.target)} $gDay") +
                        " · ${formatNumber(protein.bwPerKg.min, 1)} – ${formatNumber(protein.bwPerKg.max, 1)} g/kg",
                    current = 0.0,
                    rangeMin = protein.bwRange.min,
                    rangeMax = protein.bwRange.max,
                    showBar = false,
                )
            }
        }
    }
}

// --- Templates ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatesCard(
    templates: List<TemplateDTO>,
    onCreate: () -> Unit,
    onEdit: (TemplateDTO) -> Unit,
    onDelete: (TemplateDTO) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.templates_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onCreate) {
                    ButtonIcon(Icons.Filled.Add, MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.template_new), style = MaterialTheme.typography.labelMedium)
                }
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

// --- Data (CSV) ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataCard(viewModel: SettingsViewModel, message: DataMessage?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun exportTo(uri: Uri?, csv: suspend () -> String) {
        uri ?: return
        scope.launch {
            val ok = runCatching { writeText(context, uri, csv()) }.isSuccess
            viewModel.onExported(ok)
        }
    }

    val exportMeals = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        exportTo(uri, viewModel::mealsCsv)
    }
    val exportWeights = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        exportTo(uri, viewModel::weightsCsv)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCsv { readText(context, uri) }
    }
    val mealsFile = stringResource(R.string.data_meals_file_name, todayKey())
    val weightsFile = stringResource(R.string.data_weights_file_name, todayKey())

    SettingsCard(stringResource(R.string.data_title)) {
        CardDescription(stringResource(R.string.data_description))
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { exportMeals.launch(mealsFile) }) {
                ButtonIcon(Icons.Filled.Download)
                Text(stringResource(R.string.data_export_meals), style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = { exportWeights.launch(weightsFile) }) {
                ButtonIcon(Icons.Filled.Download)
                Text(stringResource(R.string.data_export_weights), style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { import.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
            ) {
                ButtonIcon(Icons.Filled.Upload)
                Text(stringResource(R.string.data_import), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = viewModel::requestDeleteData) {
            ButtonIcon(Icons.Filled.DeleteForever, MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.data_delete_button), color = MaterialTheme.colorScheme.error)
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isError = it is DataMessage.Failed || it is DataMessage.UnknownFile
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
    DataMessage.Deleted -> stringResource(R.string.data_deleted)
    DataMessage.UnknownFile -> stringResource(R.string.data_unknown_file)
    DataMessage.Failed -> stringResource(R.string.data_failed)
    is DataMessage.ImportedMeals ->
        importSummary(R.plurals.data_imported_meals, message.result.added, message.result.skipped, message.invalid)
    is DataMessage.ImportedWeights ->
        importSummary(R.plurals.data_imported_weights, message.result.added, message.result.skipped, message.invalid)
}

@Composable
private fun importSummary(addedRes: Int, added: Int, skipped: Int, invalid: Int): String = listOfNotNull(
    pluralStringResource(addedRes, added, added),
    if (skipped > 0) pluralStringResource(R.plurals.data_import_skipped, skipped, skipped) else null,
    if (invalid > 0) pluralStringResource(R.plurals.data_import_invalid, invalid, invalid) else null,
).joinToString(" · ")

private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

// --- Appearance & links ---

@Composable
private fun AppearanceCard(theme: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    SettingsCard(stringResource(R.string.appearance_title)) {
        SegmentedChoice(
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark),
            ),
            selected = theme,
            onSelect = onThemeChange,
        )
    }
}

@Composable
private fun LinkCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                CardDescription(subtitle)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
