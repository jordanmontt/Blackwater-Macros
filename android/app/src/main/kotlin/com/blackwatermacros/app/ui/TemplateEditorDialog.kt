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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.TemplateRequest
import com.blackwatermacros.app.data.WireEntryMode

/**
 * Create/edit dialog for meal templates. Mirrors the web `template-form.tsx`
 * + `nutrition-fields.tsx`: same fields as the meal form (title, entry mode,
 * per-ingredient or manual total, notes) sending `name === title`, per the
 * web `TemplateForm.handleSubmit`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorDialog(
    template: TemplateDTO?,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (TemplateRequest) -> Unit,
) {
    val draft = rememberTemplateDraft(template)

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden },
        ),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
        ) {
            Text(
                text = if (template != null) "Editar plantilla" else "Nueva plantilla",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            androidx.compose.runtime.key(template?.id ?: "new") {
                TemplateFormFields(
                    initialMode = draft.entryMode,
                    initialTitle = draft.title,
                    initialNotes = draft.notes,
                    initialIngredients = draft.ingredients,
                    initialTotalCalories = draft.totalCalories,
                    initialTotalProtein = draft.totalProtein,
                    initialTotalCarbs = draft.totalCarbs,
                    initialTotalFat = draft.totalFat,
                    saving = saving,
                    error = error,
                    onCancel = onDismiss,
                    onSave = onSave,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateFormFields(
    initialMode: WireEntryMode,
    initialTitle: String,
    initialNotes: String,
    initialIngredients: List<IngredientDraft>,
    initialTotalCalories: String,
    initialTotalProtein: String,
    initialTotalCarbs: String,
    initialTotalFat: String,
    saving: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onSave: (TemplateRequest) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var ingredients by rememberSaveable { mutableStateOf(initialIngredients) }
    var totalCalories by rememberSaveable { mutableStateOf(initialTotalCalories) }
    var totalProtein by rememberSaveable { mutableStateOf(initialTotalProtein) }
    var totalCarbs by rememberSaveable { mutableStateOf(initialTotalCarbs) }
    var totalFat by rememberSaveable { mutableStateOf(initialTotalFat) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val shownError = localError ?: error

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        FieldLabel("Título")
        Spacer(Modifier.height(6.dp))
        CompactField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Desayuno",
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        FieldLabel("¿Cómo quieres introducir la nutrición?")
        Spacer(Modifier.height(6.dp))
        var menuExpanded by rememberSaveable { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = { if (!saving) menuExpanded = !menuExpanded },
        ) {
            val item = modeItems.first { it.value == mode }
            CompactField(
                value = item.label,
                onValueChange = {},
                placeholder = item.label,
                enabled = !saving,
                readOnly = true,
                trailingIcon = {
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                modeItems.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.label) },
                        onClick = {
                            mode = m.value
                            menuExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (mode == WireEntryMode.PER_INGREDIENT) {
                "Introduce las calorías, proteína, carbohidratos y grasa de cada ingrediente y se sumarán solas."
            } else {
                "Introduce únicamente el total de calorías, proteína, carbohidratos y grasa de la comida."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (mode == WireEntryMode.PER_INGREDIENT) {
            FieldLabel("Ingredientes")
            Spacer(Modifier.height(6.dp))
            ingredients.forEachIndexed { index, item ->
                IngredientEditor(
                    item = item,
                    enabled = !saving,
                    canRemove = ingredients.size > 1,
                    onRemove = {
                        ingredients = ingredients.toMutableList().apply { removeAt(index) }
                    },
                    onUpdate = { patch ->
                        ingredients = ingredients.toMutableList().apply {
                            set(index, item.copy(
                                name = patch.name ?: item.name,
                                quantity = patch.quantity ?: item.quantity,
                                calories = patch.calories ?: item.calories,
                                protein = patch.protein ?: item.protein,
                                carbs = patch.carbs ?: item.carbs,
                                fat = patch.fat ?: item.fat,
                            ))
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { ingredients = ingredients + emptyIngredient },
                enabled = !saving,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir ingrediente")
            }
        } else {
            MacroTotalGrid(
                values = listOf(totalCalories, totalProtein, totalCarbs, totalFat),
                onValues =
                    { i, v ->
                        when (i) {
                            0 -> totalCalories = v
                            1 -> totalProtein = v
                            2 -> totalCarbs = v
                            3 -> totalFat = v
                        }
                    },
                enabled = !saving,
            )
        }

        Spacer(Modifier.height(16.dp))

        FieldLabel("Notas (opcional)")
        Spacer(Modifier.height(6.dp))
        CompactTextArea(
            value = notes,
            onValueChange = { notes = it },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )

        shownError?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, enabled = !saving) {
                Text("Cancelar")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val submitErr = validateNutrition(
                        title = title,
                        mode = mode,
                        ingredients = ingredients,
                        totalCalories = totalCalories,
                        totalProtein = totalProtein,
                        totalCarbs = totalCarbs,
                        totalFat = totalFat,
                    )
                    if (submitErr != null) {
                        localError = submitErr
                        return@Button
                    }
                    localError = null
                    onSave(
                        TemplateRequest(
                            name = title.trim(),
                            title = title.trim(),
                            notes = notes.trim().ifBlank { null },
                            entryMode = mode,
                            ingredients = buildIngredients(mode, ingredients).first,
                            totalCalories = if (mode == WireEntryMode.TOTAL_ONLY) parseTotal(totalCalories) else null,
                            totalProtein = if (mode == WireEntryMode.TOTAL_ONLY) parseTotal(totalProtein) else null,
                            totalCarbs = if (mode == WireEntryMode.TOTAL_ONLY) parseTotal(totalCarbs) else null,
                            totalFat = if (mode == WireEntryMode.TOTAL_ONLY) parseTotal(totalFat) else null,
                        ),
                    )
                },
                enabled = !saving,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Guardar")
                }
            }
        }
    }
}

private data class TemplateDraft(
    val entryMode: WireEntryMode,
    val title: String,
    val notes: String,
    val ingredients: List<IngredientDraft>,
    val totalCalories: String,
    val totalProtein: String,
    val totalCarbs: String,
    val totalFat: String,
)

@Composable
private fun rememberTemplateDraft(template: TemplateDTO?): TemplateDraft {
    return androidx.compose.runtime.remember(template) {
        if (template == null) {
            TemplateDraft(
                entryMode = WireEntryMode.PER_INGREDIENT,
                title = "",
                notes = "",
                ingredients = listOf(emptyIngredient),
                totalCalories = "",
                totalProtein = "",
                totalCarbs = "",
                totalFat = "",
            )
        } else {
            val ingredients = if (template.ingredients.isNotEmpty()) {
                template.ingredients.map { ingredient ->
                    IngredientDraft(
                        name = ingredient.name,
                        quantity = ingredient.quantity ?: "",
                        calories = ingredient.calories?.let { toDecimalInput(it) } ?: "",
                        protein = ingredient.protein?.let { toDecimalInput(it) } ?: "",
                        carbs = ingredient.carbs?.let { toDecimalInput(it) } ?: "",
                        fat = ingredient.fat?.let { toDecimalInput(it) } ?: "",
                    )
                }
            } else {
                listOf(emptyIngredient)
            }
            val isTotalOnly = template.entryMode == WireEntryMode.TOTAL_ONLY
            TemplateDraft(
                entryMode = template.entryMode,
                title = template.title,
                notes = template.notes ?: "",
                ingredients = ingredients,
                totalCalories = if (isTotalOnly) template.totalCalories?.let { toDecimalInput(it) } ?: "" else "",
                totalProtein = if (isTotalOnly) template.totalProtein?.let { toDecimalInput(it) } ?: "" else "",
                totalCarbs = if (isTotalOnly) template.totalCarbs?.let { toDecimalInput(it) } ?: "" else "",
                totalFat = if (isTotalOnly) template.totalFat?.let { toDecimalInput(it) } ?: "" else "",
            )
        }
    }
}