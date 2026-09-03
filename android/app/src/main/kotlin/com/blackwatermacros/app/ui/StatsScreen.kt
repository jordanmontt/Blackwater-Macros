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
import com.blackwatermacros.app.core.DataPoint
import com.blackwatermacros.app.core.formatDateKeyShort
import com.blackwatermacros.app.core.formatNumberEs
import com.blackwatermacros.app.core.formatNumberEsGrouped
import com.blackwatermacros.app.data.StatsSummary
import com.blackwatermacros.app.data.WireDailyNutritionPoint
import com.blackwatermacros.app.ui.chart.TrendChart
import com.blackwatermacros.app.ui.chart.WeightFatChart

private val Chart1 = Color(0xFF4A8C5A)
private val Chart2 = Color(0xFFB5605A)
private val Chart3 = Color(0xFFC79A3C)
private val Chart4 = Color(0xFF5A7FB5)

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
        topBar = {
            CenteredTopAppBar(
                title = "Estadísticas",
                trailing = {
                    IconButton(onClick = onOpenMetodologia) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "Metodología",
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
                    is StatsUiState.Error -> Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (state as StatsUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatsRangeOption.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
            "Sin datos todavía en este periodo.",
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
    SectionTitle("Resumen de peso")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        val w = summary.weight
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatColumn("Peso actual", w.currentWeightKg, "kg", sign = false, decimals = 1, Modifier.weight(1f))
                MiniStatColumn("Tendencia actual", w.currentTrendKg, "kg", sign = false, decimals = 1, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatColumn("Grasa actual", w.currentBodyFatPct, "%", sign = false, decimals = 1, Modifier.weight(1f))
                MiniStatColumn("Cambio total", w.changeSinceStartKg, "kg", sign = true, decimals = 1, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatColumn("Ritmo semanal", w.ratePerWeekKg, "kg/semana", sign = true, decimals = 2, Modifier.weight(1f))
                MiniStatColumn("Cambio grasa", w.changeBodyFatPct, "%", sign = true, decimals = 1, Modifier.weight(1f))
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
                        append(formatNumberEs(value, decimals))
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
    SectionTitle("Evolución del peso y grasa corporal")
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
    SectionTitle("Media semanal del peso")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    Text(formatDateKeyShort(week.weekStart), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${formatNumberEsGrouped(week.avg, 1)} kg",
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
                Text("Mínimo: ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("${formatNumberEsGrouped(w.minKg ?: 0.0, 1)} kg", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("Máximo: ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("${formatNumberEsGrouped(w.maxKg ?: 0.0, 1)} kg", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
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
    SectionTitle("Resumen de macros")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                MacroCell("Media de calorías", summary.caloriesAvg, "kcal", Modifier.weight(1f))
                MacroCell("Día pico", summary.caloriesMaxDay?.let { it.calories }, "kcal", Modifier.weight(1f), summary.caloriesMaxDay?.date)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                MacroCell("Media de proteína", summary.proteinAvg, "g", Modifier.weight(1f))
                MacroCell("Día pico", summary.proteinMaxDay?.let { it.protein }, "g", Modifier.weight(1f), summary.proteinMaxDay?.date)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                MacroCell("Media de carbohidratos", summary.carbsAvg, "g", Modifier.weight(1f))
                MacroCell("Día pico", summary.carbsMaxDay?.let { it.carbs }, "g", Modifier.weight(1f), summary.carbsMaxDay?.date)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                MacroCell("Media de grasa", summary.fatAvg, "g", Modifier.weight(1f))
                MacroCell("Día pico", summary.fatMaxDay?.let { it.fat }, "g", Modifier.weight(1f), summary.fatMaxDay?.date)
            }
        }
    }
    ChartSection("Calorías diarias", summary.calories, Chart1, "kcal", summary.caloriesMaxDay, summary.caloriesAvg)
    ChartSection("Proteína diaria", summary.protein, Chart2, "g", summary.proteinMaxDay, summary.proteinAvg)
    ChartSection("Carbohidratos diarios", summary.carbs, Chart3, "g", summary.carbsMaxDay, summary.carbsAvg)
    ChartSection("Grasa diaria", summary.fat, Chart4, "g", summary.fatMaxDay, summary.fatAvg)
}

@Composable
private fun MacroCell(label: String, value: Double?, unit: String, modifier: Modifier = Modifier, maxDate: String? = null) {
    Column(modifier) {
        Text(
            if (maxDate != null) "$label · ${formatDateKeyShort(maxDate)}" else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        if (value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatNumberEs(value),
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
    points: List<WireDailyNutritionPoint>,
    color: Color,
    unit: String,
    peak: WireDailyNutritionPoint?,
    avg: Double?,
) {
    SectionTitle(title)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            val data = points.mapIndexedNotNull { i, p ->
                when (unit) {
                    "kcal" -> DataPoint(p.date, p.calories)
                    "g" -> when (title) {
                        "Proteína diaria" -> DataPoint(p.date, p.protein)
                        "Carbohidratos diarios" -> DataPoint(p.date, p.carbs)
                        else -> DataPoint(p.date, p.fat)
                    }
                    else -> DataPoint(p.date, p.calories)
                }
            }
            TrendChart(
                points = data,
                color = color,
                trendColor = Chart4,
                unit = unit,
                peak = peak?.let { DataPoint(it.date, when (unit) {
                    "kcal" -> it.calories
                    "g" -> when (title) {
                        "Proteína diaria" -> it.protein
                        "Carbohidratos diarios" -> it.carbs
                        else -> it.fat
                    }
                    else -> it.calories
                }) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}