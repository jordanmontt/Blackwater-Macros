package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import com.blackwatermacros.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProteinBasis
import com.blackwatermacros.app.core.ProteinRecommendation
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
    onOpenProfile: () -> Unit = {},
    onOpenWeight: () -> Unit = {},
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
        RecommendationsUiState.NoWeight ->
            HintCard(stringResource(R.string.rec_need_weight), stringResource(R.string.rec_go_to_weight), onOpenWeight)
        RecommendationsUiState.NeedsProfile ->
            HintCard(stringResource(R.string.rec_need_profile), stringResource(R.string.rec_go_to_profile), onOpenProfile)
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
                            title = stringResource(R.string.rec_calories_title),
                            icon = Icons.Filled.Whatshot,
                            goal = calorie.goal,
                            rangeValue = "${formatNumber(calorie.targetMin)} – ${formatNumber(calorie.targetMax)}",
                            rangeUnit = stringResource(R.string.unit_kcal_day),
                            detail = stringResource(R.string.rec_estimated_average, "${formatNumber(calorie.target)} ${stringResource(R.string.unit_kcal_day)}"),
                            current = dailyCalories,
                            rangeMin = calorie.targetMin,
                            rangeMax = calorie.targetMax,
                            barUnit = "kcal",
                            // Where the target comes from (same as Perfil), shown before the bar.
                            details = listOfNotNull(
                                stringResource(R.string.rec_bmr, formatNumber(calorie.bmr)),
                                stringResource(R.string.rec_tdee, formatNumber(calorie.tdee)),
                                ready.expenditure?.let {
                                    stringResource(R.string.rec_measured_tdee, formatNumber(it.tdee), formatNumber(it.margin))
                                },
                            ),
                        )
                    }
                    if (calorie != null && protein != null) {
                        Spacer(Modifier.height(16.dp))
                    }
                    if (protein != null) {
                        RecommendationSection(
                            title = stringResource(R.string.rec_protein_title),
                            icon = Icons.Filled.FitnessCenter,
                            goal = protein.goal,
                            rangeValue = "${formatNumber(protein.range.min)} – ${formatNumber(protein.range.max)}",
                            rangeUnit = stringResource(R.string.unit_g_day),
                            detail = stringResource(R.string.rec_estimated_average, "${formatNumber(protein.target)} ${stringResource(R.string.unit_g_day)}"),
                            current = dailyProtein,
                            rangeMin = protein.range.min,
                            rangeMax = protein.range.max,
                            barUnit = "g",
                            details = listOf(proteinPerKg(protein)),
                        )
                    }
                }
            }
        }
    }
}

/** "1.4 – 2.0 g/kg", or per kg of lean mass when the cut uses body fat. */
@Composable
internal fun proteinPerKg(protein: ProteinRecommendation): String {
    val min = formatNumber(protein.perKg.min, 1)
    val max = formatNumber(protein.perKg.max, 1)
    return when (protein.basis) {
        ProteinBasis.LEAN_MASS -> stringResource(R.string.rec_per_kg_lean, min, max, formatNumber(protein.basisKg, 1))
        ProteinBasis.REFERENCE_WEIGHT -> stringResource(R.string.rec_per_kg_reference, min, max, formatNumber(protein.basisKg, 1))
        ProteinBasis.BODY_WEIGHT -> stringResource(R.string.rec_per_kg, min, max)
    }
}

/** A tappable explanation of what is missing, with a link to where to fix it. */
@Composable
private fun HintCard(message: String, action: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
    barUnit: String = "",
    showBar: Boolean = true,
    details: List<String> = emptyList(),
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text(
                "· ${stringResource(goal.labelRes())}",
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
        details.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showBar) {
            Spacer(Modifier.height(10.dp))
            IntakeBar(current, rangeMin, rangeMax, barUnit)
        }
    }
}

/**
 * Progress towards the daily target: the outlined track is what is left, the
 * solid fill is what was eaten (ember once past the target) and the two
 * markers crossing the bar are the target range. The numbers say it in words.
 */
@Composable
private fun IntakeBar(
    current: Double,
    rangeMin: Double,
    rangeMax: Double,
    unit: String,
) {
    val status = intakeStatus(current, rangeMin, rangeMax, unit)
    val colors = MaterialTheme.colorScheme
    // Leave room past the target so going over is visible on the bar.
    val scaleMax = if (rangeMax > 0) rangeMax * 1.1 else 1.0
    fun frac(v: Double) = (v / scaleMax).coerceIn(0.0, 1.0).toFloat()
    val over = current > rangeMax
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(status.label, style = MaterialTheme.typography.labelSmall, color = status.color, modifier = Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface)) {
                        append(formatNumber(round(current)))
                    }
                    append(" / ${formatNumber(rangeMin)}–${formatNumber(rangeMax)} $unit")
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .semantics {
                    contentDescription = status.label
                    progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..rangeMax.toFloat().coerceAtLeast(1f))
                },
        ) {
            val w = maxWidth
            val pill = RoundedCornerShape(50)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(pill)
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.outline.copy(alpha = 0.6f), pill),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac(current))
                        .clip(pill)
                        .background(if (over) colors.tertiary else colors.primary),
                )
            }
            listOf(rangeMin, rangeMax).forEach { mark ->
                Box(
                    Modifier
                        .offset(x = w * frac(mark) - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(colors.onSurface.copy(alpha = 0.8f), RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

private data class Status(val label: String, val color: Color)

@Composable
private fun intakeStatus(current: Double, rangeMin: Double, rangeMax: Double, unit: String): Status = when {
    current in rangeMin..rangeMax -> Status(stringResource(R.string.rec_in_range), Color(0xFF2F7D43))
    current < rangeMin -> Status(
        stringResource(R.string.rec_missing, formatNumber(round(rangeMin - current)), formatNumber(round(rangeMax - current)), unit),
        Color(0xFFC8910A),
    )
    else -> Status(stringResource(R.string.rec_exceeded, formatNumber(round(current - rangeMax)), unit), Color(0xFFB2561F))
}

internal fun Goal.labelRes(): Int = when (this) {
    Goal.CUT -> R.string.goal_cut
    Goal.MAINTAIN -> R.string.goal_maintain
    Goal.SURPLUS -> R.string.goal_surplus
}
