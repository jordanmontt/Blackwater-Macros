package com.blackwatermacros.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackwatermacros.app.R
import com.blackwatermacros.app.data.WireEntryMode
import com.blackwatermacros.app.data.WireIngredient

/** Form building blocks shared by the meal/template form and the weight dialog. */

/** One ingredient row as typed (strings, validated on save). */
internal data class IngredientDraft(
    val name: String = "",
    val quantity: String = "",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
)

@Composable
internal fun IngredientEditor(
    item: IngredientDraft,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onChange: (IngredientDraft) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactField(
                value = item.name,
                onValueChange = { onChange(item.copy(name = it)) },
                placeholder = stringResource(R.string.ingredient_name_placeholder),
                modifier = Modifier.weight(3f),
            )
            CompactField(
                value = item.quantity,
                onValueChange = { onChange(item.copy(quantity = it)) },
                placeholder = stringResource(R.string.ingredient_quantity_placeholder),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(item.calories, stringResource(R.string.unit_kcal), Modifier.weight(1f)) { onChange(item.copy(calories = it)) }
            DecimalField(item.protein, stringResource(R.string.macro_g_protein), Modifier.weight(1f)) { onChange(item.copy(protein = it)) }
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(item.carbs, stringResource(R.string.macro_g_carbs), Modifier.weight(1f)) { onChange(item.copy(carbs = it)) }
            DecimalField(item.fat, stringResource(R.string.macro_g_fat), Modifier.weight(1f)) { onChange(item.copy(fat = it)) }
        }
    }
}

/** Calories / protein / carbs / fat totals for "total only" meals, in that order. */
@Composable
internal fun MacroTotalGrid(values: List<String>, onValue: (Int, String) -> Unit) {
    val labels = listOf(R.string.total_calories, R.string.total_protein_g, R.string.total_carbs_g, R.string.total_fat_g)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (rowStart in listOf(0, 2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in rowStart..rowStart + 1) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(labels[i]))
                        Spacer(Modifier.height(6.dp))
                        DecimalField(values[i], "0", Modifier.fillMaxWidth()) { onValue(i, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecimalField(value: String, placeholder: String, modifier: Modifier, onValue: (String) -> Unit) {
    CompactField(value = value, onValueChange = onValue, placeholder = placeholder, decimal = true, modifier = modifier)
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Compact single-line text field mirroring the web `Input` component: no
 * floating label and less vertical padding than Material's default.
 */
@Composable
fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    decimal: Boolean = false,
    readOnly: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    /** Overrides the text/decimal keyboard (e.g. digits only for a barcode). */
    keyboardType: KeyboardType? = null,
    /** Dots instead of the text (API keys). */
    secret: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
        // Default caret is black: invisible on the dark theme.
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType ?: if (secret) KeyboardType.Password else if (decimal) KeyboardType.Decimal else KeyboardType.Text,
            autoCorrectEnabled = if (secret || keyboardType == KeyboardType.Uri) false else null,
        ),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .fieldBackground(enabled, focused && !readOnly)
                    .padding(start = 12.dp, end = if (trailingIcon != null && readOnly) 6.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Placeholder(placeholder)
                    innerTextField()
                }
                if (trailingIcon != null) {
                    Spacer(Modifier.width(4.dp))
                    trailingIcon()
                }
            }
        },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    )
}

/** Compact multi-line text area mirroring the web `Textarea`. */
@Composable
fun CompactTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
        // Default caret is black: invisible on the dark theme.
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                Modifier
                    .defaultMinSize(minHeight = 72.dp)
                    .fieldBackground(enabled = true, focused = focused)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.isEmpty()) Placeholder(placeholder ?: stringResource(R.string.form_notes_placeholder))
                innerTextField()
            }
        },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    )
}

@Composable
private fun Placeholder(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
        // Clearly fainter than typed text, so a hint like «Desayuno» is not mistaken for a value.
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun Modifier.fieldBackground(enabled: Boolean, focused: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val border = when {
        focused -> BorderStroke(2.dp, colors.primary)
        enabled -> BorderStroke(1.dp, colors.outline)
        else -> BorderStroke(1.dp, colors.outline.copy(alpha = 0.38f))
    }
    return background(colors.surface, RoundedCornerShape(8.dp)).border(border, RoundedCornerShape(8.dp))
}

// --- Parsing & validation (web `nutrition-fields.tsx` semantics) ---

/** A stored number shown for editing, in the app language: `8,5` / `8.5`. */
internal fun toDecimalInput(value: Double): String = formatNumber(value, maxDecimals = 6)

internal sealed interface FormResult {
    data class Valid(val value: MealFormValue) : FormResult
    data class Invalid(@StringRes val messageRes: Int) : FormResult
}

/**
 * Validates the typed form. Blank macro fields count as "not entered"; a
 * negative or non-numeric one is an error. Ingredient rows without a name are
 * ignored, but a per-ingredient meal needs at least one named ingredient.
 */
internal fun buildFormValue(
    title: String,
    notes: String,
    mode: WireEntryMode,
    ingredients: List<IngredientDraft>,
    totals: List<String>,
): FormResult {
    if (title.isBlank()) return FormResult.Invalid(R.string.error_title_required)
    val named = ingredients.filter { it.name.isNotBlank() }
    val macros = if (mode == WireEntryMode.PER_INGREDIENT) {
        named.flatMap { listOf(it.calories, it.protein, it.carbs, it.fat) }
    } else {
        totals
    }
    if (macros.any { it.isNotBlank() && macro(it) == null }) return FormResult.Invalid(R.string.error_invalid_number)
    if (mode == WireEntryMode.PER_INGREDIENT && named.isEmpty()) return FormResult.Invalid(R.string.error_ingredient_required)

    val totalOnly = mode == WireEntryMode.TOTAL_ONLY
    return FormResult.Valid(
        MealFormValue(
            title = title.trim(),
            notes = notes.trim().ifEmpty { null },
            entryMode = mode,
            ingredients = if (totalOnly) {
                emptyList()
            } else {
                named.map {
                    WireIngredient(
                        name = it.name.trim(),
                        quantity = it.quantity.trim().ifEmpty { null },
                        calories = macro(it.calories),
                        protein = macro(it.protein),
                        carbs = macro(it.carbs),
                        fat = macro(it.fat),
                    )
                }
            },
            totalCalories = if (totalOnly) macro(totals[0]) ?: 0.0 else null,
            totalProtein = if (totalOnly) macro(totals[1]) ?: 0.0 else null,
            totalCarbs = if (totalOnly) macro(totals[2]) ?: 0.0 else null,
            totalFat = if (totalOnly) macro(totals[3]) ?: 0.0 else null,
        ),
    )
}

/** Non-negative number or null (blank/invalid). */
private fun macro(text: String): Double? = parseDecimal(text)?.takeIf { it >= 0 }
