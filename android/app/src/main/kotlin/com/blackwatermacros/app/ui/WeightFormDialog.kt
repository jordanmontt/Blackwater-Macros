package com.blackwatermacros.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.R
import com.blackwatermacros.app.data.WeightDTO
import com.blackwatermacros.app.data.WeightRequest
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Create/edit weight dialog mirroring the web `peso/page.tsx` weight form.
 * Validates with the server's limits so a saved entry can always be synced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightFormDialog(
    weight: WeightDTO?,
    onDismiss: () -> Unit,
    onSubmit: (WeightRequest) -> Unit,
) {
    var weightText by remember(weight?.id) { mutableStateOf(weight?.weightKg?.let(::toDecimalInput).orEmpty()) }
    var measuredAt by remember(weight?.id) {
        mutableStateOf(
            weight?.let { Instant.parse(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDateTime() }
                ?: LocalDateTime.now().withSecond(0).withNano(0),
        )
    }
    var bodyFatText by remember(weight?.id) { mutableStateOf(weight?.bodyFatPct?.let(::toDecimalInput).orEmpty()) }
    var noteText by remember(weight?.id) { mutableStateOf(weight?.note.orEmpty()) }
    var errorRes by remember(weight?.id) { mutableStateOf<Int?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(if (weight != null) R.string.weight_edit else R.string.weight_add)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(stringResource(R.string.weight_kg), Modifier.weight(1f)) {
                        CompactField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            placeholder = "",
                            decimal = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    LabeledField(stringResource(R.string.body_fat_pct), Modifier.weight(1f)) {
                        CompactField(
                            value = bodyFatText,
                            onValueChange = { bodyFatText = it },
                            placeholder = stringResource(R.string.body_fat_placeholder),
                            decimal = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(stringResource(R.string.weight_date), Modifier.weight(1f)) {
                        PickerField(DateTimeFormatter.ofPattern("d MMM", appLocale()).format(measuredAt)) { showDatePicker = true }
                    }
                    LabeledField(stringResource(R.string.weight_time), Modifier.weight(1f)) {
                        PickerField(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", appLocale()).format(measuredAt)) {
                            showTimePicker = true
                        }
                    }
                }
                LabeledField(stringResource(R.string.weight_note_optional), Modifier.fillMaxWidth()) {
                    CompactTextArea(value = noteText, onValueChange = { noteText = it }, modifier = Modifier.fillMaxWidth())
                }
                errorRes?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val result = validateWeight(weightText, bodyFatText)) {
                        is WeightValidation.Invalid -> errorRes = result.messageRes
                        is WeightValidation.Valid -> onSubmit(
                            WeightRequest(
                                measuredAt = measuredAt.atZone(ZoneId.systemDefault()).toInstant().toString(),
                                weightKg = result.weightKg,
                                bodyFatPct = result.bodyFatPct,
                                note = noteText.trim().ifEmpty { null },
                            ),
                        )
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showDatePicker) {
        // The Material date picker works in UTC midnights.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = measuredAt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            measuredAt = LocalDateTime.of(date, measuredAt.toLocalTime())
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = measuredAt.hour,
            initialMinute = measuredAt.minute,
            is24Hour = is24Hour,
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.weight_time), style = MaterialTheme.typography.labelLarge) },
            confirmButton = {
                TextButton(
                    onClick = {
                        measuredAt = measuredAt.with(LocalTime.of(timeState.hour, timeState.minute))
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            TimePicker(state = timeState)
        }
    }
}

@Composable
private fun LabeledField(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** A read-only field that opens a picker when tapped. */
@Composable
private fun PickerField(value: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        CompactField(value = value, onValueChange = {}, placeholder = "", readOnly = true, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

internal sealed interface WeightValidation {
    data class Valid(val weightKg: Double, val bodyFatPct: Double?) : WeightValidation
    data class Invalid(@StringRes val messageRes: Int) : WeightValidation
}

/** Same limits as the server (`weightInputSchema`): 20–400 kg, 3–60 % body fat. */
internal fun validateWeight(weightText: String, bodyFatText: String): WeightValidation {
    val kg = parseDecimal(weightText)
    if (kg == null || kg !in 20.0..400.0) return WeightValidation.Invalid(R.string.error_weight_range)
    val fat = if (bodyFatText.isBlank()) null else parseDecimal(bodyFatText)
    if (bodyFatText.isNotBlank() && (fat == null || fat !in 3.0..60.0)) {
        return WeightValidation.Invalid(R.string.error_body_fat_range)
    }
    return WeightValidation.Valid(kg, fat)
}
