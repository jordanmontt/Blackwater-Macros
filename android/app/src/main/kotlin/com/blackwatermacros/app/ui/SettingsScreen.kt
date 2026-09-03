package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.formatNumberEs
import com.blackwatermacros.app.data.TemplateDTO

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onOpenMetodologia: () -> Unit,
    onOpenAdmin: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var deletingTemplate by remember { mutableStateOf<TemplateDTO?>(null) }
    var editingTemplate by remember { mutableStateOf<TemplateDTO?>(null) }
    var templateDialogOpen by remember { mutableStateOf(false) }
    var templateError by remember { mutableStateOf<String?>(null) }
    var templateSaving by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenteredTopAppBar(title = "Ajustes")
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
        when (state) {
            SettingsUiState.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is SettingsUiState.Error -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (state as SettingsUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            is SettingsUiState.Loaded -> {
                val loaded = state as SettingsUiState.Loaded
                AppearanceCard(darkTheme = darkTheme, onThemeChanged = onThemeChanged)
                GoalCard(goal = loaded.profile.calorieGoal, onGoalChange = viewModel::updateCalorieGoal)
                ProfileCard(profile = loaded.profile, onProfileChange = viewModel::updateProfile)
                RecommendationsCard(loaded)
                MethodologyLinkCard(onOpenMetodologia)
                ExportCard()
                TemplatesCard(
                    templates = templates,
                    onCreate = {
                        editingTemplate = null
                        templateError = null
                        templateDialogOpen = true
                    },
                    onEdit = { template ->
                        editingTemplate = template
                        templateError = null
                        templateDialogOpen = true
                    },
                    onDelete = { deletingTemplate = it },
                )
                if (loaded.isAdmin) {
                    AdminLinkCard(onOpenAdmin)
                }
                SessionCard(username = loaded.username, onLogout = onLogout)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    }

    deletingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = { Text("¿Eliminar plantilla?") },
            text = { Text("Se borrará esta plantilla. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTemplate(template.id) { ok -> if (ok) deletingTemplate = null }
                    },
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTemplate = null }) { Text("Cancelar") }
            },
        )
    }

    if (templateDialogOpen) {
        TemplateEditorDialog(
            template = editingTemplate,
            saving = templateSaving,
            error = templateError,
            onDismiss = { templateDialogOpen = false },
            onSave = { request ->
                templateSaving = true
                templateError = null
                val target = editingTemplate
                if (target == null) {
                    viewModel.createTemplate(request) { err ->
                        templateSaving = false
                        if (err == null) {
                            templateDialogOpen = false
                        } else {
                            templateError = err
                        }
                    }
                } else {
                    viewModel.updateTemplate(target.id, request) { err ->
                        templateSaving = false
                        if (err == null) {
                            templateDialogOpen = false
                        } else {
                            templateError = err
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String?, content: @Composable () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
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
private fun AppearanceCard(darkTheme: Boolean, onThemeChanged: (Boolean) -> Unit) {
    SettingsCard("Apariencia") {
        SegmentedRow(
            options = listOf("Claro" to !darkTheme, "Oscuro" to darkTheme),
            onSelect = { index -> onThemeChanged(index == 1) },
        )
    }
}

@Composable
private fun SegmentedRow(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, selected) ->
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

private val goalLabels = mapOf(
    Goal.CUT to "Definición",
    Goal.MAINTAIN to "Mantenimiento",
    Goal.SURPLUS to "Volumen",
)

@Composable
private fun GoalCard(goal: Goal?, onGoalChange: (Goal) -> Unit) {
    SettingsCard("Objetivo deportivo") {
        Text(
            "Define tu objetivo deportivo para calcular los rangos de proteína y calorías recomendadas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Goal.entries.forEachIndexed { index, g ->
                SegmentedButton(
                    selected = goal == g,
                    onClick = { onGoalChange(g) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = Goal.entries.size),
                ) {
                    Text(
                        goalLabels.getValue(g),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: com.blackwatermacros.app.core.CalorieProfile,
    onProfileChange: ((com.blackwatermacros.app.core.CalorieProfile) -> Unit) = {},
) {
    SettingsCard("Tu perfil") {
        Text(
            "Define tu género, edad y actividad para estimar tus necesidades calóricas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Text("Género", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                Gender.MALE to "Hombre",
                Gender.FEMALE to "Mujer",
            ).forEachIndexed { index, (gender, label) ->
                SegmentedButton(
                    selected = profile.gender == gender,
                    onClick = { onProfileChange(profile.copy(gender = gender)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        ProfileGridRow {
            NumberFieldRow(
                label = "Año de nacimiento",
                value = profile.birthYear?.toString() ?: "",
                placeholder = "1990",
                validation = "El año debe estar entre 1920 y 2010",
                modifier = Modifier.weight(1f),
                onValue = { text ->
                    onProfileChange(profile.copy(birthYear = text.trim().toIntOrNull()))
                },
            )
            NumberFieldRow(
                label = "Altura (cm)",
                value = profile.heightCm?.let { trimNum(it) } ?: "",
                placeholder = "",
                validation = "La altura debe estar entre 100 y 250 cm",
                modifier = Modifier.weight(1f),
                onValue = { text ->
                    onProfileChange(profile.copy(heightCm = text.trim().replace(",", ".").toDoubleOrNull()))
                },
            )
        }
        ProfileGridRow {
            NumberFieldRow(
                label = "Días de gimnasio por semana",
                value = profile.gymDaysPerWeek?.toString() ?: "",
                placeholder = "",
                validation = "Los días deben ser entre 0 y 7",
                modifier = Modifier.weight(1f),
                onValue = { text ->
                    onProfileChange(profile.copy(gymDaysPerWeek = text.trim().toIntOrNull()))
                },
            )
            NumberFieldRow(
                label = "Duración de sesión (min)",
                value = profile.gymSessionMinutes?.toString() ?: "",
                placeholder = "",
                validation = "La duración debe ser entre 0 y 300 min",
                modifier = Modifier.weight(1f),
                onValue = { text ->
                    onProfileChange(profile.copy(gymSessionMinutes = text.trim().toIntOrNull()))
                },
            )
        }
        ProfileGridRow {
            NumberFieldRow(
                label = "Caminata diaria (min)",
                value = profile.walkingMinutesPerDay?.toString() ?: "",
                placeholder = "",
                validation = "El tiempo debe ser entre 0 y 480 min",
                modifier = Modifier.fillMaxWidth(),
                onValue = { text ->
                    onProfileChange(profile.copy(walkingMinutesPerDay = text.trim().toIntOrNull()))
                },
            )
        }
    }
}

@Composable
private fun ProfileGridRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
    }
}

@Composable
private fun NumberFieldRow(
    label: String,
    value: String,
    placeholder: String,
    validation: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValue: (String) -> Unit,
) {
    Column(modifier.padding(bottom = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
            modifier = Modifier.heightIn(min = 0.dp),
        )
        Spacer(Modifier.height(4.dp))
        CompactField(
            value = value,
            onValueChange = onValue,
            placeholder = placeholder,
            enabled = true,
            decimal = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun trimNum(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite() && !v.isNaN()) v.toLong().toString()
    else v.toString().trimEnd('0').trimEnd('.')

@Composable
private fun RecommendationsCard(loaded: SettingsUiState.Loaded) {
    val calorie = loaded.calorieRec
    val protein = loaded.proteinRec
    if (calorie == null && protein == null) return
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (calorie != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RecommendationSection(
                        title = "Recomendación de calorías",
                        icon = Icons.Filled.Whatshot,
                        goal = calorie.goal,
                        rangeValue = "${formatNumberEs(calorie.targetMin)} – ${formatNumberEs(calorie.targetMax)}",
                        rangeUnit = "kcal/día",
                        detail = "promedio estimado: ${formatNumberEs(calorie.target)} kcal/día",
                        current = 0.0,
                        rangeMin = calorie.targetMin,
                        rangeMax = calorie.targetMax,
                        missingLabel = "",
                        exceededLabel = "",
                        showBar = false,
                    )
                    Text(
                        "Metabolismo basal (TMB): ${formatNumberEs(calorie.bmr)} kcal/día",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Gasto calórico diario estimado (TDEE): ${formatNumberEs(calorie.tdee)} kcal/día",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (protein != null) {
                RecommendationSection(
                    title = "Recomendación de proteína",
                    icon = Icons.Filled.FitnessCenter,
                    goal = protein.goal,
                    rangeValue = "${formatNumberEs(protein.bwRange.min)} – ${formatNumberEs(protein.bwRange.max)}",
                    rangeUnit = "g/día",
                    detail = "promedio estimado: ${formatNumberEs(protein.target)} g/día · ${formatNumberEs(protein.bwPerKg.min, 1)} – ${formatNumberEs(protein.bwPerKg.max, 1)} g/kg",
                    current = 0.0,
                    rangeMin = protein.bwRange.min,
                    rangeMax = protein.bwRange.max,
                    missingLabel = "",
                    exceededLabel = "",
                    showBar = false,
                )
            }
        }
    }
}

@Composable
private fun MethodologyLinkCard(onOpen: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Metodología", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Cómo se calculan las métricas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExportCard() {
    SettingsCard("Exportar datos") {
        Text(
            "Descarga todos tus datos en formato CSV.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportButton("Exportar comidas (CSV)", Modifier.weight(1f))
            ExportButton("Exportar peso (CSV)", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExportButton(label: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun TemplatesCard(
    templates: List<TemplateDTO>,
    onCreate: () -> Unit,
    onEdit: (TemplateDTO) -> Unit,
    onDelete: (TemplateDTO) -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Plantillas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.OutlinedButton(onClick = onCreate) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Nueva plantilla", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (templates.isEmpty()) {
                Text(
                    "Aún no tienes plantillas guardadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                templates.forEachIndexed { i, template ->
                    if (i > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    template.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (template.entryMode == com.blackwatermacros.app.data.WireEntryMode.TOTAL_ONLY) {
                                        "Total manual"
                                    } else {
                                        "${template.ingredients.size} ingredientes"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onEdit(template) }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Editar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.height(20.dp),
                                )
                            }
                            IconButton(onClick = { onDelete(template) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.height(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NutritionBadge(
                                text = "${formatNumberEs(template.resolvedCalories)} kcal",
                                variant = "secondary",
                            )
                            NutritionBadge(
                                text = "${formatNumberEs(template.resolvedProtein)} g · Proteína",
                            )
                            NutritionBadge(
                                text = "${formatNumberEs(template.resolvedCarbs)} g · Carbohidratos",
                            )
                            NutritionBadge(
                                text = "${formatNumberEs(template.resolvedFat)} g · Grasa",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionBadge(text: String, variant: String = "outline") {
    val isSecondary = variant == "secondary"
    val background =
        if (isSecondary) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent
    val borderColor =
        if (isSecondary) Color.Transparent
        else MaterialTheme.colorScheme.outlineVariant
    val contentColor =
        if (isSecondary) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        maxLines = 1,
        modifier = Modifier
            .background(background, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun AdminLinkCard(onOpen: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Administración", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Gestiona los usuarios de la aplicación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionCard(username: String, onLogout: () -> Unit) {
    SettingsCard("Sesión") {
        Text(
            "Sesión iniciada como $username",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedButton(onClick = onLogout) {
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.height(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text("Cerrar sesión", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}