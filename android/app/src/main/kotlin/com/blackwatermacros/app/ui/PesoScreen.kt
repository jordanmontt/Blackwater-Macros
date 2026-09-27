package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.blackwatermacros.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.ui.chart.WeightFatChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PesoScreen(
    modifier: Modifier = Modifier,
    viewModel: PesoViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var formWeight by remember { mutableStateOf<WeightDTO?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var deletingWeight by remember { mutableStateOf<WeightDTO?>(null) }

    val loaded = state as? PesoUiState.Loaded

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenteredTopAppBar(title = stringResource(R.string.tab_weight))
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    formWeight = null
                    formOpen = true
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.weight_add))
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                PesoUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is PesoUiState.Loaded -> PesoContent(
                    summary = (state as PesoUiState.Loaded).summary,
                    onOpenCreate = {
                        formWeight = null
                        formOpen = true
                    },
                    onEdit = { w ->
                        formWeight = w
                        formOpen = true
                    },
                    onDelete = { deletingWeight = it },
                )
            }
        }
    }

    if (formOpen && loaded != null) {
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
                TextButton(
                    onClick = {
                        viewModel.deleteWeight(w.id)
                        deletingWeight = null
                    },
                ) {
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
private fun PesoContent(
    summary: PesoSummary,
    onOpenCreate: () -> Unit,
    onEdit: (WeightDTO) -> Unit,
    onDelete: (WeightDTO) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SummaryCard(summary)
        if (summary.weights.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable(onClick = onOpenCreate)
                    .padding(horizontal = 16.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.weight_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            if (summary.weights.size > 1) {
                ChartCard(summary)
            }
            HistoryList(summary.groupedWeights, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun SummaryCard(summary: PesoSummary) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.weight_current),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                summary.currentWeightKg?.let { "${formatNumber(it, 1)} kg" } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatCell(stringResource(R.string.weight_change_7d), summary.changeWeight7d, "kg", Modifier.weight(1f))
                MiniStatCell(stringResource(R.string.body_fat_current), summary.currentBodyFatPct, "%", Modifier.weight(1f), signed = false)
                MiniStatCell(stringResource(R.string.body_fat_change_7d), summary.changeFat7d, "%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStatCell(label: String, value: Double?, unit: String, modifier: Modifier = Modifier, signed: Boolean = true) {
    Column(
        modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
            modifier = Modifier.heightIn(min = 0.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    buildString {
                        if (signed && value > 0) append("+")
                        append(formatNumber(value, 1))
                    },
                    style = MaterialTheme.typography.titleMedium,
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChartCard(summary: PesoSummary) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_weight_fat_chart),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            WeightFatChart(summary.chartRows, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HistoryList(
    grouped: List<Pair<String, List<WeightDTO>>>,
    onEdit: (WeightDTO) -> Unit,
    onDelete: (WeightDTO) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        grouped.forEach { (dayKey, entries) ->
            Text(
                formatDateLong(dayKey),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            androidx.compose.foundation.shape.RoundedCornerShape(12.dp).let { shape ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, shape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                ) {
                    entries.forEachIndexed { idx, entry ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        WeightRow(entry, onEdit, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightRow(
    entry: WeightDTO,
    onEdit: (WeightDTO) -> Unit,
    onDelete: (WeightDTO) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(formatNumber(entry.weightKg, 1))
                        append(" kg")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                entry.bodyFatPct?.let {
                    Text(
                        " · " + stringResource(R.string.body_fat_value, formatNumber(it, 1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            )
        }
    }
}