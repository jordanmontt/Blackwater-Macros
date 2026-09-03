package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
    var deletingTemplate by remember { mutableStateOf<TemplateDTO?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ajustes",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
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
}

@Composable
private fun SettingsCard(title: String?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeButton("Claro", selected = !darkTheme, onClick = { onThemeChanged(false) }, Modifier.weight(1f))
            ThemeButton("Oscuro", selected = darkTheme, onClick = { onThemeChanged(true) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThemeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.entries.forEach { g ->
                ThemeButton(
                    label = goalLabels.getValue(g),
                    selected = goal == g,
                    onClick = { onGoalChange(g) },
                    Modifier.weight(1f),
                )
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeButton(
                label = "Masculino",
                selected = profile.gender == Gender.MALE,
                onClick = { onProfileChange(profile.copy(gender = Gender.MALE)) },
                Modifier.weight(1f),
            )
            ThemeButton(
                label = "Femenino",
                selected = profile.gender == Gender.FEMALE,
                onClick = { onProfileChange(profile.copy(gender = Gender.FEMALE)) },
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))

        NumberFieldRow(
            label = "Año de nacimiento",
            value = profile.birthYear?.toString() ?: "",
            placeholder = "1990",
            validation = "El año debe estar entre 1920 y 2010",
            onValue = { text ->
                onProfileChange(profile.copy(birthYear = text.trim().toIntOrNull()))
            },
        )
        NumberFieldRow(
            label = "Altura (cm)",
            value = profile.heightCm?.let { trimNum(it) } ?: "",
            placeholder = "",
            validation = "La altura debe estar entre 100 y 250 cm",
            onValue = { text ->
                onProfileChange(profile.copy(heightCm = text.trim().replace(",", ".").toDoubleOrNull()))
            },
        )
        NumberFieldRow(
            label = "Días de gimnasio por semana",
            value = profile.gymDaysPerWeek?.toString() ?: "",
            placeholder = "",
            validation = "Los días deben ser entre 0 y 7",
            onValue = { text ->
                onProfileChange(profile.copy(gymDaysPerWeek = text.trim().toIntOrNull()))
            },
        )
        NumberFieldRow(
            label = "Duración de sesión (min)",
            value = profile.gymSessionMinutes?.toString() ?: "",
            placeholder = "",
            validation = "La duración debe ser entre 0 y 300 min",
            onValue = { text ->
                onProfileChange(profile.copy(gymSessionMinutes = text.trim().toIntOrNull()))
            },
        )
        NumberFieldRow(
            label = "Caminata diaria (min)",
            value = profile.walkingMinutesPerDay?.toString() ?: "",
            placeholder = "",
            validation = "El tiempo debe ser entre 0 y 480 min",
            onValue = { text ->
                onProfileChange(profile.copy(walkingMinutesPerDay = text.trim().toIntOrNull()))
            },
        )
    }
}

@Composable
private fun NumberFieldRow(
    label: String,
    value: String,
    placeholder: String,
    validation: String,
    onValue: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        FieldLabel(label)
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
    SettingsCard("Recomendaciones") {
        if (calorie != null) {
            Text("Calorías recomendadas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatNumberEs(calorie.target)} kcal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Rango: ${formatNumberEs(calorie.targetMin)} – ${formatNumberEs(calorie.targetMax)} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "TMB: ${formatNumberEs(calorie.bmr)} · TDEE: ${formatNumberEs(calorie.tdee)} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (protein != null) {
            Text("Proteína recomendada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatNumberEs(protein.bwRange.min)} – ${formatNumberEs(protein.bwRange.max)} g/día",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatNumberEs(protein.bwPerKg.min)} – ${formatNumberEs(protein.bwPerKg.max)} g/kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MethodologyLinkCard(onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Cómo se calculan las métricas",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
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
    onDelete: (TemplateDTO) -> Unit,
) {
    SettingsCard("Plantillas") {
        if (templates.isEmpty()) {
            Text(
                "Todavía no tienes plantillas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            templates.forEachIndexed { i, template ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(template.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${formatNumberEs(template.resolvedCalories)} kcal · ${formatNumberEs(template.resolvedProtein)} g proteína",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(20.dp))
                    }
                    IconButton(onClick = { onDelete(template) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Nueva plantilla")
            }
        }
    }
}

@Composable
private fun AdminLinkCard(onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Administración", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Gestiona los usuarios de la aplicación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        androidx.compose.material3.Button(
            onClick = onLogout,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text("Cerrar sesión")
        }
    }
}