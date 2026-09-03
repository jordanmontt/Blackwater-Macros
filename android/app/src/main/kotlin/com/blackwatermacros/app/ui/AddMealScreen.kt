package com.blackwatermacros.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.WireEntryMode

/**
 * Shared create/edit meal form (the web `MealForm` + `NutritionEntryFields`),
 * rendered as the content of a modal bottom sheet hosted by HoyScreen.
 * Supports the two web entry modes: per-ingredient nutrition or a single
 * manual total. Starts with a single empty ingredient for new meals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealScreen(
    logDate: String,
    meal: MealDTO? = null,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddMealViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = meal?.id ?: "new",
    ) {
        AddMealViewModel(logDate, meal?.id)
    },
) {
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val isEdit = meal != null
    val draft = rememberDraft(meal)

    androidx.compose.runtime.key(if (isEdit) "edit-${meal?.id}" else "new-$logDate") {
        FormContent(
            initialMode = draft.entryMode,
            initialTitle = draft.title,
            initialNotes = draft.notes,
            initialIngredients = draft.ingredients,
            initialTotalCalories = draft.totalCalories,
            initialTotalProtein = draft.totalProtein,
            initialTotalCarbs = draft.totalCarbs,
            initialTotalFat = draft.totalFat,
            isEdit = isEdit,
            saving = saving,
            onCancel = onCancel,
            onSaved = onSaved,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormContent(
    initialMode: WireEntryMode,
    initialTitle: String,
    initialNotes: String,
    initialIngredients: List<IngredientDraft>,
    initialTotalCalories: String,
    initialTotalProtein: String,
    initialTotalCarbs: String,
    initialTotalFat: String,
    isEdit: Boolean,
    saving: Boolean,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddMealViewModel,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var ingredients by rememberSaveable { mutableStateOf(initialIngredients) }
    var totalCalories by rememberSaveable { mutableStateOf(initialTotalCalories) }
    var totalProtein by rememberSaveable { mutableStateOf(initialTotalProtein) }
    var totalCarbs by rememberSaveable { mutableStateOf(initialTotalCarbs) }
    var totalFat by rememberSaveable { mutableStateOf(initialTotalFat) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Text(
            text = if (isEdit) "Editar comida" else "Nueva comida",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

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

        error?.let {
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
                        error = submitErr
                        return@Button
                    }
                    error = null
                    viewModel.submit(
                        title = title,
                        notes = notes,
                        entryMode = mode,
                        ingredients = buildIngredients(mode, ingredients).first,
                        totalCalories = parseTotal(totalCalories),
                        totalProtein = parseTotal(totalProtein),
                        totalCarbs = parseTotal(totalCarbs),
                        totalFat = parseTotal(totalFat),
                    ) { result ->
                        when (result) {
                            AddMealResult.Saved -> onSaved()
                            is AddMealResult.Failed -> error = result.message
                        }
                    }
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

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Compact single-line text field mirroring the web `Input` component
 * (`h-8`, `px-2.5`, `rounded-lg`, `text-base`): no floating label, reduced
 * vertical padding so the box is web-sized rather than Material's tall default.
 */
@Composable
fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    readOnly: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val border = androidx.compose.foundation.BorderStroke(
        1.dp,
        if (enabled) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text,
        ),
        decorationBox = { innerTextField ->
            val endPadding = if (trailingIcon != null && readOnly) 4.dp else 10.dp
            Row(
                Modifier
                    .defaultMinSize(minHeight = 26.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .border(border, RoundedCornerShape(8.dp))
                    .padding(start = 10.dp, end = endPadding, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
                if (trailingIcon != null) {
                    Spacer(Modifier.width(4.dp))
                    trailingIcon()
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Compact multi-line text area mirroring the web `Textarea` (`min-h-16`,
 * `px-2.5`, `py-2`, `text-base`).
 */
@Composable
fun CompactTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val border = androidx.compose.foundation.BorderStroke(
        1.dp,
        if (enabled) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
        ),
        decorationBox = { innerTextField ->
            Box(
                Modifier
                    .defaultMinSize(minHeight = 64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .border(border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Notas",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier,
    )
}

// --- Draft construction (web `rawNutritionDraft` / `resultToNutritionDraft`) ---

private data class EmptyDraft(
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
private fun rememberDraft(meal: MealDTO?): EmptyDraft {
    return androidx.compose.runtime.remember(meal) {
        if (meal == null) {
            EmptyDraft(
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
            val ingredients = if (meal.ingredients.isNotEmpty()) {
                meal.ingredients.map { ingredient ->
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
            val isTotalOnly = meal.entryMode == WireEntryMode.TOTAL_ONLY
            EmptyDraft(
                entryMode = meal.entryMode,
                title = meal.title,
                notes = meal.notes ?: "",
                ingredients = ingredients,
                totalCalories = if (isTotalOnly) meal.totalCalories?.let { toDecimalInput(it) } ?: "" else "",
                totalProtein = if (isTotalOnly) meal.totalProtein?.let { toDecimalInput(it) } ?: "" else "",
                totalCarbs = if (isTotalOnly) meal.totalCarbs?.let { toDecimalInput(it) } ?: "" else "",
                totalFat = if (isTotalOnly) meal.totalFat?.let { toDecimalInput(it) } ?: "" else "",
            )
        }
    }
}

