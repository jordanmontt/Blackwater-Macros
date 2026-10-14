package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.ExpenditureCheck
import com.blackwatermacros.app.core.ExpenditureNoticeDismissal
import com.blackwatermacros.app.core.ExpenditureVerdict
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.KCAL_PER_KG
import com.blackwatermacros.app.core.MissingExpenditureData
import com.blackwatermacros.app.core.approximateGap
import com.blackwatermacros.app.core.mathRound
import com.blackwatermacros.app.core.onlyWeighInsMissing
import com.blackwatermacros.app.core.showsGapNotice
import com.blackwatermacros.app.core.todayKey

/*
 * The expenditure check (core `checkExpenditure`), shown in layers so anyone can
 * follow it as far as they want: one sentence on Comidas only when the gap is
 * clear, a «Why?» sheet with the user's own numbers in every formula, and the
 * full method in Metodología. Same on the web (`components/expenditure-check.tsx`).
 */

/** What the profile's formula was fed, to write its equations with the user's numbers. */
data class FormulaInputs(
    val gender: Gender,
    val weightKg: Double,
    val heightCm: Double,
    val age: Int,
    val gymDaysPerWeek: Int,
    val gymSessionMinutes: Int,
    val walkingMinutesPerDay: Int,
)

internal fun formulaInputs(profile: CalorieProfile, weightKg: Double?, year: Int): FormulaInputs? {
    return FormulaInputs(
        gender = profile.gender ?: return null,
        weightKg = weightKg ?: return null,
        heightCm = profile.heightCm ?: return null,
        age = year - (profile.birthYear ?: return null),
        gymDaysPerWeek = profile.gymDaysPerWeek ?: return null,
        gymSessionMinutes = profile.gymSessionMinutes ?: return null,
        walkingMinutesPerDay = profile.walkingMinutesPerDay ?: return null,
    )
}

@Composable
private fun noticeText(check: ExpenditureCheck.Measured): String = stringResource(
    if (check.verdict == ExpenditureVerdict.HIGHER) R.string.check_notice_higher else R.string.check_notice_lower,
    formatNumber(approximateGap(check.gap)),
)

/**
 * Comidas, inside the calorie recommendation: the gap once it is confirmed, or
 * once, how to unlock the check when only the weigh-ins are missing. Nothing
 * otherwise: the full status lives in Perfil.
 */
@Composable
internal fun ExpenditureNotices(
    check: ExpenditureCheck?,
    inputs: FormulaInputs?,
    dismissal: ExpenditureNoticeDismissal?,
    weighInHintDismissed: Boolean,
    onDismissGap: (ExpenditureNoticeDismissal) -> Unit,
    onDismissHint: () -> Unit,
    onReviewActivity: () -> Unit,
    onOpenMethodology: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val today = todayKey()
    val measured = check as? ExpenditureCheck.Measured
    val text: String
    val onDismiss: () -> Unit
    when {
        measured != null && showsGapNotice(measured, dismissal, today) -> {
            text = noticeText(measured)
            onDismiss = { onDismissGap(ExpenditureNoticeDismissal(measured.verdict, today)) }
        }
        onlyWeighInsMissing(check) && !weighInHintDismissed -> {
            text = stringResource(R.string.check_weigh_in_hint)
            onDismiss = onDismissHint
        }
        else -> return
    }
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            if (measured != null) Icons.Filled.Info else Icons.Filled.MonitorWeight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Row(Modifier.offset(x = (-8).dp)) {
                TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.check_why), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        stringResource(R.string.check_not_now),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (open && check != null) {
        ExpenditureCheckSheet(
            check = check,
            inputs = inputs,
            onDismiss = { open = false },
            onReviewActivity = { open = false; onReviewActivity() },
            onOpenMethodology = { open = false; onOpenMethodology() },
        )
    }
}

/**
 * Perfil, under the measured expenditure: always the full status. What is
 * missing, or the verdict, with the calculation one tap away.
 */
@Composable
internal fun ExpenditureCheckStatus(check: ExpenditureCheck?, inputs: FormulaInputs?, onOpenMethodology: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (check == null) return
    when (check) {
        is ExpenditureCheck.NotEnoughData -> {
            if (check.missing.isEmpty()) {
                CardDescription(stringResource(R.string.rec_measured_pending))
                return
            }
            Column {
                CardDescription(stringResource(R.string.check_not_enough_intro))
                MissingList(check)
                TextButton(
                    onClick = onOpenMethodology,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) { Text(stringResource(R.string.check_how_it_works), style = MaterialTheme.typography.labelMedium) }
            }
        }
        is ExpenditureCheck.Measured -> {
            val gap = check.verdict != ExpenditureVerdict.MATCHES
            val text = when {
                !gap -> stringResource(R.string.check_status_matches, formatNumber(check.normalMin), formatNumber(check.normalMax))
                check.confirmed -> noticeText(check)
                else -> stringResource(R.string.check_status_unconfirmed)
            }
            Row(Modifier.fillMaxWidth()) {
                Icon(
                    if (gap) Icons.Filled.Info else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 1.dp).size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (gap && check.confirmed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(
                            stringResource(if (gap && check.confirmed) R.string.check_why else R.string.check_see_calculation),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
    if (open) {
        ExpenditureCheckSheet(
            check = check,
            inputs = inputs,
            onDismiss = { open = false },
            // Already in Perfil: closing the sheet shows the activity fields.
            onReviewActivity = { open = false },
            onOpenMethodology = { open = false; onOpenMethodology() },
        )
    }
}

@Composable
private fun MissingList(check: ExpenditureCheck.NotEnoughData) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        check.missing.forEach { missing ->
            val text = when (missing) {
                MissingExpenditureData.MEALS -> stringResource(R.string.check_missing_meals, check.data.loggedDays)
                MissingExpenditureData.WEIGH_INS -> stringResource(R.string.check_missing_weigh_ins, check.data.weighInDays)
                MissingExpenditureData.WEIGHT_SPAN -> stringResource(R.string.check_missing_weight_span, check.data.weightSpanDays)
                MissingExpenditureData.MARGIN -> stringResource(R.string.check_missing_margin, formatNumber(check.data.margin ?: 0.0))
            }
            Bullet(text)
        }
    }
}

@Composable
private fun Bullet(text: String, marker: String = "•") {
    Row {
        Text(marker, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenditureCheckSheet(
    check: ExpenditureCheck,
    inputs: FormulaInputs?,
    onDismiss: () -> Unit,
    onReviewActivity: () -> Unit,
    onOpenMethodology: () -> Unit,
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
            when (check) {
                is ExpenditureCheck.NotEnoughData -> {
                    SheetTitle(stringResource(R.string.meth_check_title))
                    Column {
                        CardDescription(stringResource(R.string.check_not_enough_intro))
                        MissingList(check)
                    }
                    SheetButtons(showActivity = false, onReviewActivity, onOpenMethodology)
                }
                is ExpenditureCheck.Measured -> CheckDetails(check, inputs, onReviewActivity, onOpenMethodology)
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SheetButtons(showActivity: Boolean, onReviewActivity: () -> Unit, onOpenMethodology: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showActivity) Button(onClick = onReviewActivity) { Text(stringResource(R.string.check_review_activity)) }
        OutlinedButton(onClick = onOpenMethodology) { Text(stringResource(R.string.check_how_it_works)) }
    }
}

/** «Why?»: every step with the user's numbers, so it can be checked by hand. */
@Composable
private fun CheckDetails(
    check: ExpenditureCheck.Measured,
    inputs: FormulaInputs?,
    onReviewActivity: () -> Unit,
    onOpenMethodology: () -> Unit,
) {
    val estimate = check.estimate
    val low = estimate.tdee - estimate.margin
    val high = estimate.tdee + estimate.margin
    val kcalDay = stringResource(R.string.unit_kcal_day)
    val gap = check.verdict != ExpenditureVerdict.MATCHES

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetTitle(
            stringResource(
                when {
                    !check.confirmed -> R.string.check_title_unconfirmed
                    check.verdict == ExpenditureVerdict.HIGHER -> R.string.check_title_higher
                    check.verdict == ExpenditureVerdict.LOWER -> R.string.check_title_lower
                    else -> R.string.check_title_matches
                },
            ),
        )
        if (gap && check.confirmed) CardDescription(noticeText(check))
    }

    Step(stringResource(R.string.check_profile_step)) {
        if (inputs != null) {
            StepText(stringResource(R.string.check_bmr_label))
            Equation(
                "10 × ${formatNumber(inputs.weightKg, 1)} + ${formatNumber(6.25, 2)} × ${formatNumber(inputs.heightCm, 1)} − 5 × ${inputs.age} " +
                    (if (inputs.gender == Gender.MALE) "+ 5" else "− 161") + " = ${formatNumber(check.bmr)} kcal",
            )
            StepText(stringResource(R.string.check_activity_label, inputs.gymDaysPerWeek, inputs.gymSessionMinutes, inputs.walkingMinutesPerDay))
            Equation(palEquation(inputs, check.activityFactor))
        }
        StepText(stringResource(R.string.check_formula_label))
        // tdee = round(BMR × PAL) with the unrounded factors: «≈» when the rounded ones differ by a few kcal.
        val exact = mathRound(check.bmr * check.activityFactor) == check.formulaTdee
        Equation("${formatNumber(check.bmr)} × ${formatNumber(check.activityFactor, 2)} ${if (exact) "=" else "≈"} ${formatNumber(check.formulaTdee)} $kcalDay")
        StepText(stringResource(R.string.check_normal_range, formatNumber(check.normalMin), formatNumber(check.normalMax)))
    }

    val rate = estimate.weightChangePerWeek
    val signedRate = (if (rate > 0) "+" else "") + formatNumber(rate, 2)
    Step(stringResource(R.string.check_data_step)) {
        Bullet(stringResource(R.string.check_eaten, formatNumber(estimate.avgIntake), estimate.loggedDays))
        Bullet(stringResource(R.string.check_trend, signedRate, estimate.weighIns))
        StepText(stringResource(R.string.check_measured_label))
        Equation(
            "${formatNumber(estimate.avgIntake)} − ($signedRate ÷ 7 × ${formatNumber(KCAL_PER_KG)}) ≈ " +
                "${formatNumber(estimate.tdee)} ± ${formatNumber(estimate.margin)} $kcalDay",
        )
        StepText(stringResource(R.string.check_margin_note))
    }

    Step(stringResource(R.string.check_comparison_step)) {
        RangeComparison(check, low, high, kcalDay)
        val conclusion = when {
            !check.confirmed -> stringResource(R.string.check_conclusion_unconfirmed, formatNumber(low), formatNumber(high), formatNumber(check.normalMin), formatNumber(check.normalMax))
            check.verdict == ExpenditureVerdict.HIGHER -> stringResource(R.string.check_conclusion_higher, formatNumber(low), formatNumber(check.normalMax))
            check.verdict == ExpenditureVerdict.LOWER -> stringResource(R.string.check_conclusion_lower, formatNumber(high), formatNumber(check.normalMin))
            else -> stringResource(R.string.check_conclusion_matches, formatNumber(low), formatNumber(high), formatNumber(check.normalMin), formatNumber(check.normalMax))
        }
        Text(conclusion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        if (gap) {
            StepText(stringResource(R.string.check_implied, formatNumber(check.impliedActivityFactor, 2), formatNumber(check.activityFactor, 2)))
        }
    }

    val reasons = when (check.verdict) {
        ExpenditureVerdict.HIGHER -> listOf(R.string.check_higher_reason_1, R.string.check_higher_reason_2, R.string.check_higher_reason_3, R.string.check_higher_reason_4)
        ExpenditureVerdict.LOWER -> listOf(R.string.check_lower_reason_1, R.string.check_lower_reason_2, R.string.check_lower_reason_3, R.string.check_lower_reason_4)
        ExpenditureVerdict.MATCHES -> emptyList()
    }
    if (reasons.isNotEmpty()) {
        Step(stringResource(R.string.check_reasons_title)) {
            reasons.forEachIndexed { i, res -> Bullet(stringResource(res), marker = "${i + 1}.") }
        }
    }

    CardDescription(stringResource(R.string.check_target_note))
    SheetButtons(showActivity = gap, onReviewActivity, onOpenMethodology)
}

/** PAL = ((1440 − gym − walking) × 1.4 + gym × 4.0 + walking × 3.5) / 1440, with the user's minutes. */
private fun palEquation(inputs: FormulaInputs, activityFactor: Double): String {
    val gym = inputs.gymDaysPerWeek * inputs.gymSessionMinutes / 7.0
    val walking = inputs.walkingMinutesPerDay.toDouble()
    return "((1440 − ${formatNumber(gym, 1)} − ${formatNumber(walking)}) × ${formatNumber(1.4, 1)} + " +
        "${formatNumber(gym, 1)} × ${formatNumber(4.0, 1)} + ${formatNumber(walking)} × ${formatNumber(3.5, 1)}) / 1440 = " +
        formatNumber(activityFactor, 2)
}

@Composable
private fun Step(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun StepText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Equation(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/**
 * The comparison drawn: the shaded band is what is normal for the profile
 * (formula ± 10 %), the bar with a dot is the measured value and its 95 % margin.
 * If the bar sits entirely outside the band, the gap is clear.
 */
@Composable
private fun RangeComparison(check: ExpenditureCheck.Measured, low: Double, high: Double, kcalDay: String) {
    val colors = MaterialTheme.colorScheme
    val from = minOf(check.normalMin, low)
    val to = maxOf(check.normalMax, high)
    val pad = (to - from) * 0.08
    fun frac(v: Double) = ((v - (from - pad)) / (to - from + 2 * pad)).toFloat().coerceIn(0f, 1f)
    val normalLegend = stringResource(R.string.check_normal_legend)
    val measuredLegend = stringResource(R.string.check_measured_legend)
    val normalText = "$normalLegend: ${formatNumber(check.normalMin)}–${formatNumber(check.normalMax)} $kcalDay"
    val measuredText = "$measuredLegend: ${formatNumber(low)}–${formatNumber(high)} $kcalDay"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics { contentDescription = "$normalText. $measuredText." },
        ) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(1.dp).background(colors.outlineVariant))
            Box(
                Modifier
                    .offset(x = w * frac(check.normalMin))
                    .width(w * (frac(check.normalMax) - frac(check.normalMin)))
                    .fillMaxHeight()
                    .background(colors.primary.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .border(1.dp, colors.primary.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
            )
            Box(
                Modifier
                    .offset(x = w * frac(check.formulaTdee) - 1.dp)
                    .padding(vertical = 4.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(colors.primary),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = w * frac(low))
                    .width(w * (frac(high) - frac(low)))
                    .height(6.dp)
                    .background(colors.onSurface.copy(alpha = 0.7f), RoundedCornerShape(50)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = w * frac(check.estimate.tdee) - 6.dp)
                    .size(12.dp)
                    .background(colors.onSurface, CircleShape)
                    .border(2.dp, colors.surface, CircleShape),
            )
        }
        LegendRow(normalText) {
            Box(
                Modifier
                    .size(width = 16.dp, height = 10.dp)
                    .background(colors.primary.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                    .border(1.dp, colors.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            )
        }
        LegendRow(measuredText) {
            Box(Modifier.size(width = 16.dp, height = 6.dp).background(colors.onSurface.copy(alpha = 0.7f), RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun LegendRow(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) { swatch() }
        Spacer(Modifier.width(8.dp))
        StepText(text)
    }
}
