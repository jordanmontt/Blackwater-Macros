package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.DataPoint
import com.blackwatermacros.app.core.MacroAverages
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.ui.chart.TrendChart
import com.blackwatermacros.app.ui.chart.WeightFatChart

/**
 * Progreso: one period selector for the whole screen; weight summary and chart,
 * calories chart with the target band, macro averages over logged days and the
 * weigh-in log (add / edit / delete). Mirrors web `progreso/page.tsx`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier,
    onOpenMetodologia: () -> Unit = {},
    viewModel: ProgressViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    var formWeight by remember { mutableStateOf<WeightDTO?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var deletingWeight by remember { mutableStateOf<WeightDTO?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val weightDeletedMessage = stringResource(R.string.weight_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    fun openForm(weight: WeightDTO?) {
        formWeight = weight
        formOpen = true
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenteredTopAppBar(
                title = stringResource(R.string.tab_progress),
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
        floatingActionButton = {
            // Labelled: a bare «+» here did not say it logs a weigh-in.
            ExtendedFloatingActionButton(
                onClick = { openForm(null) },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                icon = { Icon(Icons.Filled.MonitorWeight, contentDescription = null) },
                text = { Text(stringResource(R.string.weight_add)) },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            RangeTabs(selected = range, onSelect = viewModel::selectRange)
            when (val current = state) {
                ProgressUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ProgressUiState.Loaded -> ProgressContent(
                    progress = current.progress,
                    onAdd = { openForm(null) },
                    onEdit = { openForm(it) },
                    onDelete = { deletingWeight = it },
                )
            }
        }
    }

    if (formOpen) {
        WeightFormDialog(
            weight = formWeight,
            onDismiss = { formOpen = false },
            onSubmit = { request ->
                viewModel.saveWeight(formWeight?.id, request)
                formOpen = false
            },
        )
    }

    deletingWeight?.let { w ->
        AlertDialog(
            onDismissRequest = { deletingWeight = null },
            title = { Text(stringResource(R.string.weight_delete_title)) },
            text = { Text(stringResource(R.string.weight_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWeight(w.id)
                    deletingWeight = null
                    scope.offerUndo(snackbarHostState, weightDeletedMessage, undoLabel) { viewModel.restoreWeight(w) }
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingWeight = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun RangeTabs(selected: StatsRangeOption, onSelect: (StatsRangeOption) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        StatsRangeOption.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsRangeOption.entries.size),
            ) {
                Text(stringResource(option.labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProgressContent(
    progress: Progress,
    onAdd: () -> Unit,
    onEdit: (WeightDTO) -> Unit,
    onDelete: (WeightDTO) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (progress.summary.weights.isEmpty()) {
            DashedMessage(stringResource(R.string.weight_empty), onClick = onAdd)
        } else {
            WeightCard(progress)
        }
        CaloriesCard(progress)
        val averages = progress.averages
        if (averages == null) {
            DashedMessage(stringResource(R.string.progress_no_meals))
        } else {
            MacrosCard(progress, averages)
        }
        if (progress.hasAnyWeight) {
            WeighInList(progress.groupedWeights, onEdit, onDelete)
        }
        // Room for the FAB over the last row.
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun DashedMessage(text: String, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CardTitle(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeightCard(progress: Progress) {
    val w = progress.summary.weight
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(stringResource(R.string.progress_weight))
            Spacer(Modifier.height(4.dp))
            Text(
                w.currentWeightKg?.let { "${formatNumber(it, 1)} kg" } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(stringResource(R.string.progress_trend), w.currentTrendKg, "kg", 1, signed = false, Modifier.weight(1f))
                MiniStat(stringResource(R.string.progress_change), w.changeSinceStartKg, "kg", 1, signed = true, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(stringResource(R.string.progress_rate), w.ratePerWeekKg, stringResource(R.string.unit_kg_per_week), 2, signed = true, Modifier.weight(1f))
                MiniStat(stringResource(R.string.progress_body_fat), w.currentBodyFatPct, "%", 1, signed = false, Modifier.weight(1f))
            }
            if (progress.weightRows.size > 1) {
                Spacer(Modifier.height(12.dp))
                WeightFatChart(progress.weightRows, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Double?, unit: String, decimals: Int, signed: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value?.let { (if (signed && it > 0) "+" else "") + formatNumber(it, decimals) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (value != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CaloriesCard(progress: Progress) {
    // Logged days only: a 0 would pull the trend down for a day that was simply not logged.
    val points = progress.summary.calories.filter { it.calories > 0 }
    if (points.isEmpty()) return
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(stringResource(R.string.progress_calories))
            Spacer(Modifier.height(8.dp))
            TrendChart(
                points = points.map { DataPoint(it.date, it.calories) },
                color = ChartEmber,
                trendColor = ChartBlue,
                unit = "kcal",
                peak = null,
                band = progress.calorie?.let { it.targetMin to it.targetMax },
                bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MacrosCard(progress: Progress, averages: MacroAverages) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            CardTitle(
                stringResource(R.string.progress_macros),
                trailing = stringResource(R.string.progress_logged_days, averages.loggedDays, averages.totalDays),
            )
            Spacer(Modifier.height(8.dp))
            val calorie = progress.calorie
            val protein = progress.protein
            MacroRow(
                stringResource(R.string.total_calories),
                "${formatNumber(averages.calories)} kcal",
                calorie?.let { stringResource(R.string.progress_target, formatNumber(it.targetMin), formatNumber(it.targetMax)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MacroRow(
                stringResource(R.string.macro_protein),
                "${formatNumber(averages.protein)} g",
                protein?.let { stringResource(R.string.progress_target, formatNumber(it.range.min), formatNumber(it.range.max)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MacroRow(stringResource(R.string.macro_carbs), "${formatNumber(averages.carbs)} g", null)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MacroRow(stringResource(R.string.macro_fat), "${formatNumber(averages.fat)} g", null)

            averages.split?.let { split ->
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.progress_split),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                ) {
                    SplitSegment(split.protein, ChartClay)
                    SplitSegment(split.carbs, ChartGold)
                    SplitSegment(split.fat, ChartBlue)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.progress_split_values,
                        formatNumber(split.protein),
                        formatNumber(split.carbs),
                        formatNumber(split.fat),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.progress_per_logged_day),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            progress.expenditure?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.rec_measured_tdee, formatNumber(it.tdee), formatNumber(it.margin)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SplitSegment(percent: Double, color: Color) {
    if (percent <= 0) return
    Box(
        Modifier
            .weight(percent.toFloat())
            .fillMaxHeight()
            .background(color),
    )
}

@Composable
private fun MacroRow(label: String, value: String, target: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (target != null) {
            Spacer(Modifier.width(8.dp))
            Text(target, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeighInList(
    grouped: List<Pair<String, List<WeightDTO>>>,
    onEdit: (WeightDTO) -> Unit,
    onDelete: (WeightDTO) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.progress_entries),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (grouped.isEmpty()) {
            Text(
                stringResource(R.string.progress_no_entries_in_range),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        grouped.forEach { (dayKey, entries) ->
            Text(
                formatDateLong(dayKey),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            ) {
                entries.forEachIndexed { idx, entry ->
                    if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    WeighInRow(entry, onEdit, onDelete)
                }
            }
        }
    }
}

@Composable
private fun WeighInRow(entry: WeightDTO, onEdit: (WeightDTO) -> Unit, onDelete: (WeightDTO) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${formatNumber(entry.weightKg, 1)} kg", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            entry.bodyFatPct?.let {
                Text(
                    " · " + stringResource(R.string.body_fat_value, formatNumber(it, 1)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onEdit(entry) }) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.action_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { onDelete(entry) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        entry.note?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}
