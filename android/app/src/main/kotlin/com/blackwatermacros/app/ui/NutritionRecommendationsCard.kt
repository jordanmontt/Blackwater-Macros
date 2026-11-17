package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import com.blackwatermacros.app.core.ExpenditureEstimate
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
import com.blackwatermacros.app.core.CalorieRecommendation
import com.blackwatermacros.app.core.Goal
import com.blackwatermacros.app.core.ProteinBasis
import com.blackwatermacros.app.core.ProteinRecommendation
import com.blackwatermacros.app.core.todayKey
import kotlin.math.round

/** The day's eaten totals, as the summary shows them. */
internal data class DayTotals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)

/**
 * Comidas' summary of the day (web `DaySummaryCard`): calories and protein against
 * their ranges (a bar each, with what is missing or over in words), carbs and fat as
 * plain totals, and the expenditure check's notices. Where the ranges come from
 * (BMR, TDEE, measured expenditure, protein per kg) is one tap away in «Why these
 * numbers?» ([RecommendationDetailsSheet]), so the day's meals start on the first
 * screen. A past [day] keeps the targets it had; the notices are about the current
 * profile, so they show on today only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DaySummaryCard(
    day: String,
    totals: DayTotals,
    onOpenProfile: () -> Unit = {},
    onOpenWeight: () -> Unit = {},
    onOpenMethodology: (section: String?) -> Unit = {},
    viewModel: RecommendationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noticeDismissal by viewModel.noticeDismissal.collectAsStateWithLifecycle()
    val weighInHintDismissed by viewModel.weighInHintDismissed.collectAsStateWithLifecycle()
    var whyOpen by remember { mutableStateOf(false) }

    val ready = state as? RecommendationsUiState.Ready
    val isToday = day == todayKey()
    val dayTargets = if (isToday || ready == null) null else ready.targets?.on(day)
    val calorie = if (dayTargets != null) dayTargets.calorie else ready?.calorie
    val protein = if (dayTargets != null) dayTargets.protein else ready?.protein
    // The measurement that day's target uses (taken on that week's Monday).
    val measured = if (isToday) ready?.expenditure else ready?.targets?.measuredOn?.invoke(day)
    val differsFromToday = ready != null && dayTargets != null &&
        (calorie?.targetMin != ready.calorie?.targetMin || calorie?.targetMax != ready.calorie?.targetMax ||
            protein?.range != ready.protein?.range)
    val hasTargets = calorie != null || protein != null

    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (calorie != null) {
                IntakeBar(
                    title = stringResource(R.string.total_calories),
                    goal = calorie.goal,
                    current = totals.calories,
                    rangeMin = calorie.targetMin,
                    rangeMax = calorie.targetMax,
                    unit = "kcal",
                    overIsFine = false,
                )
            }
            if (protein != null) {
                IntakeBar(
                    title = stringResource(R.string.macro_protein),
                    goal = null,
                    current = totals.protein,
                    rangeMin = protein.range.min,
                    rangeMax = protein.range.max,
                    unit = "g",
                    overIsFine = true,
                )
            }
            if (isToday && ready != null && calorie != null) {
                ExpenditureNotices(
                    check = ready.check,
                    inputs = ready.inputs,
                    dismissal = noticeDismissal,
                    weighInHintDismissed = weighInHintDismissed,
                    onDismissGap = viewModel::dismissNotice,
                    onDismissHint = viewModel::dismissWeighInHint,
                    onReviewActivity = onOpenProfile,
                    onOpenMethodology = { onOpenMethodology(CHECK_SECTION) },
                )
            }
            if (hasTargets) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasTargets) {
                    // Stacked, so the layout does not change with the length of the numbers.
                    Column(Modifier.weight(1f)) {
                        PlainTotal(stringResource(R.string.macro_carbs), formatNumber(totals.carbs, 1), "g")
                        PlainTotal(stringResource(R.string.macro_fat), formatNumber(totals.fat, 1), "g")
                    }
                } else {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlainTotal(stringResource(R.string.total_calories), formatNumber(totals.calories), "kcal")
                        PlainTotal(stringResource(R.string.macro_protein), formatNumber(totals.protein, 1), "g")
                        PlainTotal(stringResource(R.string.macro_carbs), formatNumber(totals.carbs, 1), "g")
                        PlainTotal(stringResource(R.string.macro_fat), formatNumber(totals.fat, 1), "g")
                    }
                }
                if (hasTargets) {
                    TextButton(onClick = { whyOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(stringResource(R.string.rec_why_numbers), style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(16.dp))
                    }
                }
            }
            when (state) {
                RecommendationsUiState.NoWeight ->
                    HintLine(stringResource(R.string.rec_need_weight), stringResource(R.string.rec_go_to_weight), onOpenWeight)
                RecommendationsUiState.NeedsProfile ->
                    HintLine(stringResource(R.string.rec_need_profile), stringResource(R.string.rec_go_to_profile), onOpenProfile)
                else -> Unit
            }
            if (differsFromToday) {
                Text(
                    stringResource(R.string.past_day_targets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onOpenMethodology(PAST_DAYS_SECTION) },
                )
            }
        }
    }
    if (whyOpen && hasTargets) {
        RecommendationDetailsSheet(
            calorie = calorie,
            protein = protein,
            measured = measured,
            differsFromToday = differsFromToday,
            onDismiss = { whyOpen = false },
            onOpenMethodology = { section ->
                whyOpen = false
                onOpenMethodology(section)
            },
        )
    }
}

/** One total with no target to compare with (carbs, fat; or anything before there is a weight). */
@Composable
private fun PlainTotal(label: String, value: String, unit: String) {
    Text(
        buildAnnotatedString {
            append("$label ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(value) }
            append(" $unit")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What is missing for the recommendations, with where to fix it. */
@Composable
private fun HintLine(message: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * «Why these numbers?»: where the day's ranges come from, with the user's numbers
 * (the second layer; the methodology is the third). Labels and values sit in two
 * columns, so no language has to share another's punctuation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationDetailsSheet(
    calorie: CalorieRecommendation?,
    protein: ProteinRecommendation?,
    measured: ExpenditureEstimate?,
    differsFromToday: Boolean,
    onDismiss: () -> Unit,
    onOpenMethodology: (section: String?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.rec_why_numbers),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            val perDay = stringResource(R.string.unit_kcal_day)
            if (calorie != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RangeHeader(
                        stringResource(R.string.rec_calories_title),
                        Icons.Filled.Whatshot,
                        calorie.goal,
                        "${formatNumber(calorie.targetMin)} – ${formatNumber(calorie.targetMax)}",
                        perDay,
                        stringResource(R.string.rec_estimated_average, "${formatNumber(calorie.target)} $perDay"),
                    )
                    Column(
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        if (calorie.dataShare > 0) {
                            DetailRow(
                                stringResource(R.string.rec_target_expenditure),
                                stringResource(
                                    R.string.rec_target_expenditure_value,
                                    formatNumber(calorie.expenditure),
                                    formatNumber(round(calorie.dataShare * 100)),
                                ),
                                onClick = { onOpenMethodology(ADAPTIVE_SECTION) },
                            )
                        }
                        if (measured != null) {
                            DetailRow(
                                stringResource(R.string.rec_measured_label),
                                stringResource(R.string.rec_measured_value, formatNumber(measured.tdee), formatNumber(measured.margin)),
                            )
                        }
                        DetailRow(stringResource(R.string.rec_tdee_label), "${formatNumber(calorie.tdee)} $perDay")
                        DetailRow(stringResource(R.string.rec_bmr_label), "${formatNumber(calorie.bmr)} $perDay")
                    }
                }
            }
            if (protein != null) {
                val gDay = stringResource(R.string.unit_g_day)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RangeHeader(
                        stringResource(R.string.rec_protein_title),
                        Icons.Filled.FitnessCenter,
                        null,
                        "${formatNumber(protein.range.min)} – ${formatNumber(protein.range.max)}",
                        gDay,
                        stringResource(R.string.rec_estimated_average, "${formatNumber(protein.target)} $gDay"),
                    )
                    Text(proteinPerKg(protein), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (differsFromToday) {
                Text(stringResource(R.string.past_day_targets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onOpenMethodology(null) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.rec_full_methodology))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RangeHeader(title: String, icon: ImageVector, goal: Goal?, range: String, unit: String, average: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (goal != null) {
            Spacer(Modifier.width(6.dp))
            Text("· ${stringResource(goal.labelRes())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(range, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(average, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
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

/**
 * «Expenditure your target uses: 2990 kcal/day (85% from your data)», once the
 * measured expenditure counts (core `combineExpenditure`); tapping it opens how.
 */
@Composable
internal fun TargetExpenditureLine(calorie: CalorieRecommendation, onClick: () -> Unit) {
    if (calorie.dataShare <= 0) return
    Text(
        stringResource(
            R.string.label_value,
            stringResource(R.string.rec_target_expenditure),
            stringResource(
                R.string.rec_target_expenditure_value,
                formatNumber(calorie.expenditure),
                formatNumber(round(calorie.dataShare * 100)),
            ),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    )
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
    /** Going over the range is fine (protein): said in a neutral colour, the bar keeps its colour. */
    overIsFine: Boolean = false,
    /** Shown after the details, before the bar (the expenditure check's notices). */
    beforeBar: @Composable () -> Unit = {},
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
        beforeBar()
        if (showBar) {
            Spacer(Modifier.height(10.dp))
            IntakeBar(null, null, current, rangeMin, rangeMax, barUnit, overIsFine)
        }
    }
}

/**
 * Progress towards the daily target: with a [title] (Comidas), what was eaten as the
 * big number («2707 kcal eaten», also when over) and the range next to it; without
 * one (Perfil), the compact «eaten / min–max». Then the bar (the outlined track is what is left, the solid fill what was
 * eaten, ember once past the target unless [overIsFine], the two markers the target
 * range) and, under it, what is missing or over in words.
 */
@Composable
internal fun IntakeBar(
    title: String?,
    goal: Goal?,
    current: Double,
    rangeMin: Double,
    rangeMax: Double,
    unit: String,
    overIsFine: Boolean,
) {
    val status = intakeStatus(current, rangeMin, rangeMax, unit, overIsFine)
    val colors = MaterialTheme.colorScheme
    // Leave room past the target so going over is visible on the bar.
    val scaleMax = if (rangeMax > 0) rangeMax * 1.1 else 1.0
    fun frac(v: Double) = (v / scaleMax).coerceIn(0.0, 1.0).toFloat()
    val over = current > rangeMax && !overIsFine
    Column {
        if (title != null) {
            // What was eaten is the big number (also when over); the range sits next to it.
            Text(
                buildAnnotatedString {
                    append(title)
                    if (goal != null) append(" · ${stringResource(goal.labelRes())}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface)) {
                            append(formatNumber(round(current)))
                        }
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize, color = colors.onSurfaceVariant)) {
                            append(" ${stringResource(if (unit == "kcal") R.string.rec_kcal_eaten else R.string.rec_g_eaten)}")
                        }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.rec_of_range, formatNumber(rangeMin), formatNumber(rangeMax)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.weight(1f))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface)) {
                            append(formatNumber(round(current)))
                        }
                        append(" / ${formatNumber(rangeMin)}–${formatNumber(rangeMax)} $unit")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
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
        Spacer(Modifier.height(2.dp))
        Text(status.label, style = MaterialTheme.typography.labelMedium, color = status.color)
    }
}

private data class Status(val label: String, val color: Color)

/** [overIsFine] (protein): going over is said in a neutral colour, because eating more is not a problem. */
@Composable
private fun intakeStatus(current: Double, rangeMin: Double, rangeMax: Double, unit: String, overIsFine: Boolean): Status {
    // Text on the card needs 4.5:1: darker tones on the light theme, lighter on the dark one.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when {
        current in rangeMin..rangeMax -> Status(stringResource(R.string.rec_in_range), if (dark) Color(0xFF7FCB8F) else Color(0xFF2B6E3C))
        current < rangeMin -> Status(
            stringResource(R.string.rec_missing, formatNumber(round(rangeMin - current)), formatNumber(round(rangeMax - current)), unit),
            if (dark) Color(0xFFE8B84A) else Color(0xFF7A5200),
        )
        overIsFine -> Status(
            stringResource(R.string.rec_protein_above, formatNumber(round(current - rangeMax))),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Status(stringResource(R.string.rec_exceeded, formatNumber(round(current - rangeMax)), unit), if (dark) Color(0xFFF0A070) else Color(0xFF9C4A18))
    }
}

internal fun Goal.labelRes(): Int = when (this) {
    Goal.CUT -> R.string.goal_cut
    Goal.MAINTAIN -> R.string.goal_maintain
    Goal.SURPLUS -> R.string.goal_surplus
}
