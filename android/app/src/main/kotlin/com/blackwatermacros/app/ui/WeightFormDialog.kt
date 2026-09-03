package com.blackwatermacros.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.core.parseLocalDateTime
import com.blackwatermacros.app.core.toDateTimeLocalValue
import com.blackwatermacros.app.data.WeightDTO
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DIALOG_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))

private fun friendlyDate(dt: LocalDateTime): String =
    DIALOG_DATE_FORMATTER.format(dt.toLocalDate())

/**
 * Create/edit weight dialog mirroring the web `peso/page.tsx` weight form.
 * Shows an inline validation error ("Introduce un peso válido.") on invalid input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightFormDialog(
    weight: WeightDTO?,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (weightKg: Double, measuredAt: String, bodyFatPct: Double?, note: String?) -> Unit,
) {
    var weightText by remember(weight?.id) { mutableStateOf(weight?.weightKg?.let { trimDec(it) } ?: "") }
    var measuredAt by remember(weight?.id) {
        mutableStateOf(weight?.measuredAt?.let { parseLocalDateTime(it.replace(" ", "T")) } ?: LocalDateTime.now())
    }
    var bodyFatText by remember(weight?.id) { mutableStateOf(weight?.bodyFatPct?.let { trimDec(it) } ?: "") }
    var noteText by remember(weight?.id) { mutableStateOf(weight?.note ?: "") }
    var localError by remember(weight?.id) { mutableStateOf<String?>(null) }
    var showDatePicker by remember(weight?.id) { mutableStateOf(false) }
    var showTimePicker by remember(weight?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (weight != null) "Editar" else "Registrar peso") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Peso (kg)")
                        Spacer(Modifier.height(4.dp))
                        CompactField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            placeholder = "",
                            enabled = !saving,
                            decimal = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Grasa corporal (%)")
                        Spacer(Modifier.height(4.dp))
                        CompactField(
                            value = bodyFatText,
                            onValueChange = { bodyFatText = it },
                            placeholder = "15% (opcional)",
                            enabled = !saving,
                            decimal = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Fecha")
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth()) {
                            CompactField(
                                value = friendlyDate(measuredAt),
                                onValueChange = {},
                                placeholder = "",
                                enabled = !saving,
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable(enabled = !saving) { showDatePicker = true },
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Hora")
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth()) {
                            CompactField(
                                value = "${measuredAt.hour}h",
                                onValueChange = {},
                                placeholder = "",
                                enabled = !saving,
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable(enabled = !saving) { showTimePicker = true },
                            )
                        }
                    }
                }
                if (showDatePicker) {
                    val dateState = rememberDatePickerState(
                        initialSelectedDateMillis =
                        measuredAt.toLocalDate()
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dateState.selectedDateMillis?.let { millis ->
                                        val date = Instant.ofEpochMilli(millis)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDate()
                                        measuredAt = LocalDateTime.of(date, measuredAt.toLocalTime())
                                    }
                                    showDatePicker = false
                                },
                            ) { Text("Aceptar") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
                        },
                    ) {
                        DatePicker(state = dateState)
                    }
                }
                if (showTimePicker) {
                    val timeState = rememberTimePickerState(
                        initialHour = measuredAt.hour,
                        initialMinute = measuredAt.minute,
                        is24Hour = true,
                    )
                    TimePickerDialog(
                        onDismissRequest = { showTimePicker = false },
                        title = {
                            Text("Hora", style = MaterialTheme.typography.labelLarge)
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    measuredAt = measuredAt.with(LocalTime.of(timeState.hour, timeState.minute))
                                    showTimePicker = false
                                },
                            ) { Text("Aceptar") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
                        },
                    ) {
                        TimePicker(state = timeState)
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel("Nota (opcional)")
                    Spacer(Modifier.height(4.dp))
                    CompactTextArea(
                        value = noteText,
                        onValueChange = { noteText = it },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val shownError = localError ?: error
                if (shownError != null) {
                    Text(
                        shownError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val parsedWeight = weightText.trim().replace(",", ".").toDoubleOrNull()
                    val measured = measuredAt
                    if (parsedWeight == null || parsedWeight <= 0) {
                        localError = "Introduce un peso válido."
                        return@TextButton
                    }
                    localError = null
                    val fat = bodyFatText.trim().replace(",", ".").toDoubleOrNull()
                    val note = noteText.trim().takeIf { it.isNotEmpty() }
                    onSubmit(parsedWeight, toDateTimeLocalValue(measured), fat, note)
                },
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        },
    )
}

private fun trimDec(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite() && !v.isNaN()) v.toLong().toString()
    else v.toString().trimEnd('0').trimEnd('.')