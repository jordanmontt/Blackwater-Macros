package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.blackwatermacros.app.core.DataPoint
import com.blackwatermacros.app.core.DailyNutritionPoint
import com.blackwatermacros.app.core.StatsSummary
import com.blackwatermacros.app.ui.chart.TrendChart
import com.blackwatermacros.app.ui.chart.WeightFatChart

private val Chart1 = ChartEmber
private val Chart2 = ChartClay
private val Chart3 = ChartGold
private val Chart4 = ChartBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenMetodologia: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenteredTopAppBar(
                title = stringResource(R.string.tab_stats),
                trailing = {
                    IconButton(onClick = onOpenMetodologia) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = stringResource(R.string.methodology),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(Modifier.fillMaxSize()) {
                RangeTabs(
                    selected = range,
                    onSelect = viewModel::selectRange,
                )
                when (state) {
                    StatsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is StatsUiState.Loaded -> StatsContent(
                        summary = (state as StatsUiState.Loaded).summary,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeTabs(
    selected: StatsRangeOption,
    onSelect: (StatsRangeOption) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        StatsRangeOption.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = StatsRangeOption.entries.size,
                ),
            ) {
                Text(
                    stringResource(option.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StatsContent(summary: StatsSummary, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (summary.weights.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            NoData()
        } else {
            WeightSummaryCard(summary)
            WeightChartCard(summary)
            if (summary.weeklyWeightAvg.isNotEmpty()) {
                WeeklyAveragesCard(summary)
            }
        }
        NutritionSection(summary)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NoData() {
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.stats_no_data),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun WeightSummaryCard(summary: StatsSummary) {
    SectionTitle(stringResource(R.string.stats_weight_summary))
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val w = summary.weight
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatColumn(stringResource(R.string.weight_current), w.currentWeightKg, "kg", sign = false, decimals = 1, Modifier.weight(1f))
                MiniStatColumn(stringResource(R.string.stats_current_trend), w.currentTrendKg, "kg", sign = false, decimals = 1, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatColumn(stringResource(R.string.body_fat_current), w.currentBodyFatPct, "%", sign = false, decimals = 1, Modifier.weight(1f))
                MiniStatColumn(stringResource(R.string.stats_total_change), w.changeSinceStartKg, "kg", sign = true, decimals = 1, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatColumn(stringResource(R.string.stats_weekly_rate), w.ratePerWeekKg, stringResource(R.string.unit_kg_per_week), sign = true, decimals = 2, Modifier.weight(1f))
                MiniStatColumn(stringResource(R.string.stats_fat_change), w.changeBodyFatPct, "%", sign = true, decimals = 1, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStatColumn(
    label: String,
    value: Double?,
    unit: String,
    sign: Boolean,
    decimals: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        if (value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    buildString {
                        if (sign && value > 0) append("+")
                        append(formatNumber(value, decimals))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                "—",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun WeightChartCard(summary: StatsSummary) {
    SectionTitle(stringResource(R.string.stats_weight_fat_chart))
    val rows = mergeWeightFatRows(summary)
    if (rows.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                WeightFatChart(rows, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun mergeWeightFatRows(summary: StatsSummary): List<WeightFatRow> {
    val weightByDate = summary.weights.associate { it.date to it.weight }
    val trendByDate = summary.weights.mapNotNull { it.trend?.let { t -> it.date to t } }.toMap()
    val fatByDate = summary.bodyFat.associate { it.date to it.bodyFatPct }
    val dates = weightByDate.keys.toSortedSet()
    return dates.map { date ->
        WeightFatRow(
            date = date,
            weight = weightByDate[date] ?: 0.0,
            weightTrend = trendByDate[date],
            bodyFatPct = fatByDate[date],
        )
    }
}

@Composable
private fun WeeklyAveragesCard(summary: StatsSummary) {
    SectionTitle(stringResource(R.string.stats_weekly_average))
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            summary.weeklyWeightAvg.forEachIndexed { i, week ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatDateShort(week.weekStart), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${formatNumberGrouped(week.avg, 1)} kg",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val w = summary.weight
                Text(stringResource(R.string.stats_minimum) + " ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("${formatNumberGrouped(w.minKg ?: 0.0, 1)} kg", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.stats_maximum) + " ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("${formatNumberGrouped(w.maxKg ?: 0.0, 1)} kg", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun NutritionSection(summary: StatsSummary) {
    val hasData = summary.calories.any { it.calories > 0 } ||
        summary.protein.any { it.protein > 0 } ||
        summary.carbs.any { it.carbs > 0 } ||
        summary.fat.any { it.fat > 0 }
    if (!hasData) {
        Spacer(Modifier.height(20.dp))
        NoData()
        return
    }
    SectionTitle(stringResource(R.string.stats_macro_summary))
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                MacroCell(stringResource(R.string.stats_avg_calories), summary.caloriesAvg, "kcal", Modifier.weight(1f))
                MacroCell(stringResource(R.string.stats_peak_day), summary.caloriesMaxDay?.let { it.calories }, "kcal", Modifier.weight(1f), summary.caloriesMaxDay?.date)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                MacroCell(stringResource(R.string.stats_avg_protein), summary.proteinAvg, "g", Modifier.weight(1f))
                MacroCell(stringResource(R.string.stats_peak_day), summary.proteinMaxDay?.let { it.protein }, "g", Modifier.weight(1f), summary.proteinMaxDay?.date)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                MacroCell(stringResource(R.string.stats_avg_carbs), summary.carbsAvg, "g", Modifier.weight(1f))
                MacroCell(stringResource(R.string.stats_peak_day), summary.carbsMaxDay?.let { it.carbs }, "g", Modifier.weight(1f), summary.carbsMaxDay?.date)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                MacroCell(stringResource(R.string.stats_avg_fat), summary.fatAvg, "g", Modifier.weight(1f))
                MacroCell(stringResource(R.string.stats_peak_day), summary.fatMaxDay?.let { it.fat }, "g", Modifier.weight(1f), summary.fatMaxDay?.date)
            }
        }
    }
    ChartSection(stringResource(R.string.stats_daily_calories), summary.calories, Chart1, "kcal", summary.caloriesMaxDay) { it.calories }
    ChartSection(stringResource(R.string.stats_daily_protein), summary.protein, Chart2, "g", summary.proteinMaxDay) { it.protein }
    ChartSection(stringResource(R.string.stats_daily_carbs), summary.carbs, Chart3, "g", summary.carbsMaxDay) { it.carbs }
    ChartSection(stringResource(R.string.stats_daily_fat), summary.fat, Chart4, "g", summary.fatMaxDay) { it.fat }
}

@Composable
private fun MacroCell(label: String, value: Double?, unit: String, modifier: Modifier = Modifier, maxDate: String? = null) {
    Column(modifier) {
        Text(
            if (maxDate != null) "$label · ${formatDateShort(maxDate)}" else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        if (value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatNumber(value),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    unit.trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChartSection(
    title: String,
    points: List<DailyNutritionPoint>,
    color: Color,
    unit: String,
    peak: DailyNutritionPoint?,
    value: (DailyNutritionPoint) -> Double,
) {
    SectionTitle(title)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            TrendChart(
                points = points.map { DataPoint(it.date, value(it)) },
                color = color,
                trendColor = Chart4,
                unit = unit,
                peak = peak?.let { DataPoint(it.date, value(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
