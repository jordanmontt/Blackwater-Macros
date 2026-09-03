package com.blackwatermacros.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.core.nowDateTimeLocalValue
import com.blackwatermacros.app.core.parseLocalDateTime
import com.blackwatermacros.app.core.toDateTimeLocalValue
import com.blackwatermacros.app.data.WeightDTO

/**
 * Create/edit weight dialog mirroring the web `peso/page.tsx` weight form.
 * Shows an inline validation error ("Introduce un peso válido.") on invalid input.
 */
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
        mutableStateOf(weight?.measuredAt?.let { toLocalInput(it) } ?: nowDateTimeLocalValue())
    }
    var bodyFatText by remember(weight?.id) { mutableStateOf(weight?.bodyFatPct?.let { trimDec(it) } ?: "") }
    var noteText by remember(weight?.id) { mutableStateOf(weight?.note ?: "") }
    var localError by remember(weight?.id) { mutableStateOf<String?>(null) }

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
                        FieldLabel("Fecha y hora")
                        Spacer(Modifier.height(4.dp))
                        CompactField(
                            value = measuredAt,
                            onValueChange = { measuredAt = it },
                            placeholder = "2000-01-01T00:00",
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(2.dp))
                        TextButton(
                            onClick = { measuredAt = nowDateTimeLocalValue() },
                            enabled = !saving,
                        ) {
                            Text("Ahora", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel("Grasa corporal (%) (opcional)")
                    Spacer(Modifier.height(4.dp))
                    CompactField(
                        value = bodyFatText,
                        onValueChange = { bodyFatText = it },
                        placeholder = "Ej. 15",
                        enabled = !saving,
                        decimal = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                    val measured = parseLocalDateTime(measuredAt)
                    if (parsedWeight == null || parsedWeight <= 0 || measured == null) {
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

private fun toLocalInput(iso: String): String {
    val dt = parseLocalDateTime(iso.replace(" ", "T")) ?: return iso
    return toDateTimeLocalValue(dt)
}