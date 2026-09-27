package com.blackwatermacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.R
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.MealRequest
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.TemplateRequest
import com.blackwatermacros.app.data.WireEntryMode
import com.blackwatermacros.app.data.WireIngredient

/**
 * What the meal form produces: a meal and a template have exactly the same
 * fields (the template's name is its title, as on the web).
 */
data class MealFormValue(
    val title: String,
    val notes: String?,
    val entryMode: WireEntryMode,
    val ingredients: List<WireIngredient>,
    val totalCalories: Double?,
    val totalProtein: Double?,
    val totalCarbs: Double?,
    val totalFat: Double?,
) {
    fun toMealRequest(logDate: String) = MealRequest(
        logDate = logDate,
        title = title,
        notes = notes,
        entryMode = entryMode,
        ingredients = ingredients,
        totalCalories = totalCalories,
        totalProtein = totalProtein,
        totalCarbs = totalCarbs,
        totalFat = totalFat,
    )

    /**
     * A new meal on [logDate] with this content (a template, or a meal copied
     * from another day). Totals only for «Solo total», as the form sends them.
     */
    fun toCopyRequest(logDate: String): MealRequest {
        val totalOnly = entryMode == WireEntryMode.TOTAL_ONLY
        return toMealRequest(logDate).copy(
            totalCalories = totalCalories.takeIf { totalOnly },
            totalProtein = totalProtein.takeIf { totalOnly },
            totalCarbs = totalCarbs.takeIf { totalOnly },
            totalFat = totalFat.takeIf { totalOnly },
        )
    }

    fun toTemplateRequest() = TemplateRequest(
        name = title,
        title = title,
        notes = notes,
        entryMode = entryMode,
        ingredients = ingredients,
        totalCalories = totalCalories,
        totalProtein = totalProtein,
        totalCarbs = totalCarbs,
        totalFat = totalFat,
    )
}

fun MealDTO.toFormValue() =
    MealFormValue(title, notes, entryMode, ingredients, totalCalories, totalProtein, totalCarbs, totalFat)

fun TemplateDTO.toFormValue() =
    MealFormValue(title, notes, entryMode, ingredients, totalCalories, totalProtein, totalCarbs, totalFat)

/**
 * Create/edit form for meals and templates (the web `MealForm` +
 * `NutritionEntryFields`), in a bottom sheet. Two entry modes: per-ingredient
 * nutrition or a single manual total. Saving is local, so it never fails.
 * The sheet closes by swiping down, tapping outside or Back — asking first
 * when there are unsaved edits. [prefilled]: a new meal filled in for the user
 * (search, barcode, AI) counts as unsaved from the start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealFormSheet(
    heading: String,
    initial: MealFormValue?,
    onDismiss: () -> Unit,
    onSubmit: (MealFormValue) -> Unit,
    prefilled: Boolean = false,
) {
    var dirty by remember { mutableStateOf(prefilled) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(dirty)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && currentDirty) {
                confirmDiscard = true
                false
            } else {
                true
            }
        },
    )
    fun requestDismiss() {
        if (dirty) confirmDiscard = true else onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::requestDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            MealFormFields(
                heading = heading,
                initial = initial,
                onCancel = ::requestDismiss,
                onSubmit = onSubmit,
                onDirtyChange = { dirty = it || prefilled },
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealFormFields(
    heading: String,
    initial: MealFormValue?,
    onCancel: () -> Unit,
    onSubmit: (MealFormValue) -> Unit,
    onDirtyChange: (Boolean) -> Unit = {},
) {
    val totalOnly = initial?.entryMode == WireEntryMode.TOTAL_ONLY
    var mode by rememberSaveable { mutableStateOf(initial?.entryMode ?: WireEntryMode.PER_INGREDIENT) }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    // Plain `remember`: these lists are not Bundle-saveable (rememberSaveable would crash on backgrounding).
    var ingredients by remember {
        mutableStateOf(initial?.ingredients?.map { it.toDraft() }?.ifEmpty { null } ?: listOf(IngredientDraft()))
    }
    var totals by remember {
        mutableStateOf(
            if (totalOnly) {
                listOf(initial!!.totalCalories, initial.totalProtein, initial.totalCarbs, initial.totalFat)
                    .map { it?.let(::toDecimalInput).orEmpty() }
            } else {
                List(4) { "" }
            },
        )
    }
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }

    val initialState = remember { FormSnapshot(mode, title, notes, ingredients, totals) }
    val dirty = FormSnapshot(mode, title, notes, ingredients, totals) != initialState
    LaunchedEffect(dirty) { onDirtyChange(dirty) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.form_title))
        Spacer(Modifier.height(6.dp))
        CompactField(
            value = title,
            onValueChange = { title = it },
            placeholder = stringResource(R.string.form_title_placeholder),
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.form_entry_mode_question))
        Spacer(Modifier.height(6.dp))
        var menuExpanded by rememberSaveable { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = !menuExpanded }) {
            val label = stringResource(mode.labelRes())
            CompactField(
                value = label,
                onValueChange = {},
                placeholder = label,
                enabled = true,
                readOnly = true,
                trailingIcon = {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                WireEntryMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            mode = option
                            menuExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (mode == WireEntryMode.PER_INGREDIENT) R.string.form_help_per_ingredient else R.string.form_help_total_only,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (mode == WireEntryMode.PER_INGREDIENT) {
            FieldLabel(stringResource(R.string.form_ingredients))
            Spacer(Modifier.height(6.dp))
            ingredients.forEachIndexed { index, item ->
                IngredientEditor(
                    item = item,
                    canRemove = ingredients.size > 1,
                    onRemove = { ingredients = ingredients.toMutableList().apply { removeAt(index) } },
                    onChange = { updated -> ingredients = ingredients.toMutableList().apply { set(index, updated) } },
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { ingredients = ingredients + IngredientDraft() }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.form_add_ingredient))
            }
        } else {
            MacroTotalGrid(values = totals, onValue = { i, v -> totals = totals.toMutableList().apply { set(i, v) } })
        }

        Spacer(Modifier.height(16.dp))

        FieldLabel(stringResource(R.string.form_notes_optional))
        Spacer(Modifier.height(6.dp))
        CompactTextArea(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth())

        errorRes?.let {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    when (val result = buildFormValue(title, notes, mode, ingredients, totals)) {
                        is FormResult.Invalid -> errorRes = result.messageRes
                        is FormResult.Valid -> onSubmit(result.value)
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

/** What the user can change, to tell whether the form has unsaved edits. */
private data class FormSnapshot(
    val mode: WireEntryMode,
    val title: String,
    val notes: String,
    val ingredients: List<IngredientDraft>,
    val totals: List<String>,
)

private fun WireEntryMode.labelRes(): Int = when (this) {
    WireEntryMode.PER_INGREDIENT -> R.string.entry_mode_per_ingredient
    WireEntryMode.TOTAL_ONLY -> R.string.entry_mode_total_only
}

private fun WireIngredient.toDraft() = IngredientDraft(
    name = name,
    quantity = quantity.orEmpty(),
    calories = calories?.let(::toDecimalInput).orEmpty(),
    protein = protein?.let(::toDecimalInput).orEmpty(),
    carbs = carbs?.let(::toDecimalInput).orEmpty(),
    fat = fat?.let(::toDecimalInput).orEmpty(),
)
