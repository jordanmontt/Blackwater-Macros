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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.core.formatDateKeyLong
import com.blackwatermacros.app.core.formatNumberEs
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.WeightRequest
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
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val loaded = state as? PesoUiState.Loaded

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Peso",
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    formWeight = null
                    formError = null
                    formOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Registrar peso")
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
                is PesoUiState.Error -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (state as PesoUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
is PesoUiState.Loaded -> PesoContent(
                    summary = (state as PesoUiState.Loaded).summary,
                    onOpenCreate = {
                        formWeight = null
                        formError = null
                        formOpen = true
                    },
                    onEdit = { w ->
                        formWeight = w
                        formError = null
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
            saving = saving,
            error = formError,
            onDismiss = { formOpen = false },
            onSubmit = { weightKg, measuredAt, bodyFatPct, note ->
                saving = true
                formError = null
                val request = WeightRequest(
                    measuredAt = measuredAt,
                    weightKg = weightKg,
                    bodyFatPct = bodyFatPct,
                    note = note,
                )
                val id = formWeight?.id
                val done: (Boolean) -> Unit = { ok ->
                    saving = false
                    if (ok) formOpen = false
                    else formError = "Algo salió mal. Inténtalo de nuevo."
                }
                if (id == null) viewModel.createWeight(request, done)
                else viewModel.updateWeight(id, request, done)
            },
        )
    }

    deletingWeight?.let { w ->
        AlertDialog(
            onDismissRequest = { deletingWeight = null },
            title = { Text("¿Eliminar registro?") },
            text = { Text("Este registro de peso se borrará permanentemente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWeight(w.id) { ok -> if (ok) deletingWeight = null }
                    },
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingWeight = null }) { Text("Cancelar") }
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
                    "Todavía no has registrado ningún peso.",
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Peso actual",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                summary.currentWeightKg?.let { "${formatNumberEs(it, 1)} kg" } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatCell("Cambio peso (7 días)", summary.changeWeight7d, "kg", Modifier.weight(1f))
                MiniStatCell("Grasa actual", summary.currentBodyFatPct, "%", Modifier.weight(1f))
                MiniStatCell("Cambio grasa (7 días)", summary.changeFat7d, "%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStatCell(label: String, value: Double?, unit: String, modifier: Modifier = Modifier) {
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
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                if (value != null) {
                    if (value > 0) append("+")
                    append(formatNumberEs(value, 1))
                    append(" ")
                    append(unit)
                } else {
                    append("—")
                }
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChartCard(summary: PesoSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Evolución del peso y grasa corporal",
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
                formatDateKeyLong(dayKey),
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                append(formatNumberEs(entry.weightKg, 1))
                append(" kg")
                entry.bodyFatPct?.let {
                    append(" · ")
                    append(formatNumberEs(it, 1))
                    append("% grasa")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        entry.note?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
        }
        IconButton(onClick = { onEdit(entry) }) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Editar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = { onDelete(entry) }) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Eliminar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}