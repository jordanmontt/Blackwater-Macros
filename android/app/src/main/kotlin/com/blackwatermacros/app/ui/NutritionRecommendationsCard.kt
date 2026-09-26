package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.core.CalorieRecommendation
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.core.formatNumberEs
import kotlin.math.round

/**
 * Collapsed calorie + protein recommendation card (web
 * `NutritionRecommendationsCard`). Fetches weights + profile and renders both
 * ranges with an intake bar comparing today's intake against the goal.
 */
@Composable
fun NutritionRecommendationsCard(
    dailyCalories: Double,
    dailyProtein: Double,
    viewModel: RecommendationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state) {
        RecommendationsUiState.Loading -> AppCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        RecommendationsUiState.NoWeight -> AppCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Registra tu peso para ver recomendaciones",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is RecommendationsUiState.Ready -> {
            val ready = state as RecommendationsUiState.Ready
            val calorie = ready.calorie
            val protein = ready.protein
            if (calorie == null && protein == null) return
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (calorie != null) {
                        RecommendationSection(
                            title = "Recomendación de calorías",
                            icon = Icons.Filled.Whatshot,
                            goal = calorie.goal,
                            rangeValue = "${formatNumberEs(calorie.targetMin)} – ${formatNumberEs(calorie.targetMax)}",
                            rangeUnit = "kcal/día",
                            detail = "promedio estimado: ${formatNumberEs(calorie.target)} kcal/día",
                            current = dailyCalories,
                            rangeMin = calorie.targetMin,
                            rangeMax = calorie.targetMax,
                            missingLabel = "te faltan {min}–{max} kcal",
                            exceededLabel = "te pasaste de {min} kcal",
                        )
                    }
                    if (calorie != null && protein != null) {
                        Spacer(Modifier.height(16.dp))
                    }
                    if (protein != null) {
                        RecommendationSection(
                            title = "Recomendación de proteína",
                            icon = Icons.Filled.FitnessCenter,
                            goal = protein.goal,
                            rangeValue = "${formatNumberEs(protein.bwRange.min)} – ${formatNumberEs(protein.bwRange.max)}",
                            rangeUnit = "g/día",
                            detail = "promedio estimado: ${formatNumberEs(protein.target)} g/día",
                            current = dailyProtein,
                            rangeMin = protein.bwRange.min,
                            rangeMax = protein.bwRange.max,
                            missingLabel = "te faltan {min}–{max} g",
                            exceededLabel = "te pasaste de {min} g",
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecommendationSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    goal: Goal,
    rangeValue: String,
    rangeUnit: String,
    detail: String,
    current: Double,
    rangeMin: Double,
    rangeMax: Double,
    missingLabel: String,
    exceededLabel: String,
    showBar: Boolean = true,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text(
                "· ${goalLabel(goal)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                rangeValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                rangeUnit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showBar) {
            Spacer(Modifier.height(10.dp))
            IntakeBar(current, rangeMin, rangeMax, missingLabel, exceededLabel)
        }
    }
}

@Composable
private fun IntakeBar(
    current: Double,
    rangeMin: Double,
    rangeMax: Double,
    missingLabel: String,
    exceededLabel: String,
) {
    val status = intakeStatus(current, rangeMin, rangeMax, missingLabel, exceededLabel)
    Column {
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = status.color)
        Spacer(Modifier.height(4.dp))
        val pct = if (rangeMax > 0) (current / rangeMax * 100) else 0.0
        val barPct = pct.coerceIn(0.0, 100.0)
        val zoneLeft = if (rangeMax > 0) (rangeMin / rangeMax * 100) else 0.0
        val zoneWidth = if (rangeMax > 0) ((rangeMax - rangeMin) / rangeMax * 100) else 0.0
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
        ) {
            val w = maxWidth
            val zoneLeftF = (zoneLeft / 100.0f).toFloat()
            Box(
                Modifier
                    .offset(x = w * zoneLeftF)
                    .size(width = w * (zoneWidth / 100.0f).toFloat(), height = 6.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(barPct.toFloat() / 100f)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            )
            Tick(w, zoneLeftF)
            Tick(w, ((zoneLeft + zoneWidth).coerceAtMost(100.0) / 100.0f).toFloat())
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.Tick(totalWidth: androidx.compose.ui.unit.Dp, leftFrac: Float) {
    Box(
        Modifier
            .offset(x = totalWidth * leftFrac)
            .width(2.dp)
            .height(6.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
    )
}

private data class Status(val label: String, val color: Color)

private fun intakeStatus(
    current: Double,
    rangeMin: Double,
    rangeMax: Double,
    missingLabel: String,
    exceededLabel: String,
): Status {
    return when {
        current >= rangeMin && current <= rangeMax -> Status("en rango", Color(0xFF2F7D43))
        current < rangeMin -> {
            val missingMin = formatNumberEs(round(rangeMin - current))
            val missingMax = formatNumberEs(round(rangeMax - current))
            Status(missingLabel.replace("{min}", missingMin).replace("{max}", missingMax), Color(0xFFC8910A))
        }
        else -> {
            val exceeded = formatNumberEs(round(current - rangeMax))
            Status(exceededLabel.replace("{min}", exceeded), Color(0xFFB2561F))
        }
    }
}

private fun goalLabel(goal: Goal): String = when (goal) {
    Goal.CUT -> "Definición"
    Goal.MAINTAIN -> "Mantenimiento"
    Goal.SURPLUS -> "Volumen"
}