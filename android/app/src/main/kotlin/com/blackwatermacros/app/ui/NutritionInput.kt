package com.blackwatermacros.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.data.WireEntryMode

/**
 * Shared nutrition-entry building blocks used by both the meal form
 * (AddMealScreen) and the template form. Mirrors the web
 * `nutrition-fields.tsx` (`NutritionEntryFields`, draft builders and parsing).
 */

internal data class IngredientDraft(
    val name: String = "",
    val quantity: String = "",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
)

internal val emptyIngredient = IngredientDraft()

internal data class EntryModeItem(val value: WireEntryMode, val label: String)

internal val modeItems = listOf(
    EntryModeItem(WireEntryMode.PER_INGREDIENT, "Por ingrediente"),
    EntryModeItem(WireEntryMode.TOTAL_ONLY, "Solo total"),
)

internal data class Patch(
    val name: String? = null,
    val quantity: String? = null,
    val calories: String? = null,
    val protein: String? = null,
    val carbs: String? = null,
    val fat: String? = null,
)

@Composable
internal fun IngredientEditor(
    item: IngredientDraft,
    enabled: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onUpdate: (Patch) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactField(
                value = item.name,
                onValueChange = { onUpdate(Patch(name = it)) },
                placeholder = "4 huevos",
                enabled = enabled,
                modifier = Modifier.weight(3f),
            )
            CompactField(
                value = item.quantity,
                onValueChange = { onUpdate(Patch(quantity = it)) },
                placeholder = "30g",
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, enabled = enabled && canRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroField(item.calories, "kcal", enabled, Modifier.weight(1f)) { onUpdate(Patch(calories = it)) }
            MacroField(item.protein, "g proteína", enabled, Modifier.weight(1f)) { onUpdate(Patch(protein = it)) }
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroField(item.carbs, "g carbohidratos", enabled, Modifier.weight(1f)) { onUpdate(Patch(carbs = it)) }
            MacroField(item.fat, "g grasa", enabled, Modifier.weight(1f)) { onUpdate(Patch(fat = it)) }
        }
    }
}

@Composable
internal fun MacroTotalGrid(
    values: List<String>,
    onValues: (Int, String) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalCell("Calorías", values[0], enabled, Modifier.weight(1f)) { onValues(0, it) }
            TotalCell("Proteína (g)", values[1], enabled, Modifier.weight(1f)) { onValues(1, it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalCell("Carbohidratos (g)", values[2], enabled, Modifier.weight(1f)) { onValues(2, it) }
            TotalCell("Grasa (g)", values[3], enabled, Modifier.weight(1f)) { onValues(3, it) }
        }
    }
}

@Composable
private fun TotalCell(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
) {
    Column(modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        CompactField(
            value = value,
            onValueChange = onValue,
            placeholder = "0",
            enabled = enabled,
            decimal = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MacroField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
) {
    CompactField(
        value = value,
        onValueChange = onValue,
        placeholder = placeholder,
        enabled = enabled,
        decimal = true,
        modifier = modifier,
    )
}

// --- Parsing (web `parseNumber` semantics) ---

private sealed interface MacroParse {
    data object Empty : MacroParse
    data class Value(val d: Double) : MacroParse
    data object Invalid : MacroParse
}

private fun parseMacro(value: String): MacroParse {
    val trimmed = value.trim().replace(",", ".")
    if (trimmed.isEmpty()) return MacroParse.Empty
    val n = trimmed.toDoubleOrNull()
    return if (n != null && n >= 0) MacroParse.Value(n) else MacroParse.Invalid
}

internal fun parseTotal(value: String): Double {
    return when (val p = parseMacro(value)) {
        is MacroParse.Value -> p.d
        else -> 0.0
    }
}

internal fun toDecimalInput(v: Double): String {
    if (v == Math.floor(v) && !v.isInfinite() && !Math.signum(v).isNaN()) {
        return v.toLong().toString()
    }
    return v.toString().trimEnd('0').trimEnd('.')
}

internal fun buildIngredients(mode: WireEntryMode, drafts: List<IngredientDraft>): Pair<List<com.blackwatermacros.app.data.WireIngredient>, Boolean> {
    if (mode != WireEntryMode.PER_INGREDIENT) return Pair(emptyList(), false)
    val list = drafts.mapNotNull { d ->
        val name = d.name.trim()
        if (name.isEmpty()) return@mapNotNull null
        com.blackwatermacros.app.data.WireIngredient(
            name = name,
            quantity = d.quantity.trim().ifEmpty { null },
            calories = (parseMacro(d.calories) as? MacroParse.Value)?.d,
            protein = (parseMacro(d.protein) as? MacroParse.Value)?.d,
            carbs = (parseMacro(d.carbs) as? MacroParse.Value)?.d,
            fat = (parseMacro(d.fat) as? MacroParse.Value)?.d,
        )
    }
    return Pair(list, list.isEmpty())
}

/**
 * Web-parity validation (mirrors `nutrition-fields.tsx` `handleSubmit`):
 * - empty title -> "El título es obligatorio."
 * - per-ingredient: blank names skipped; if all blank -> "Ingredientes";
 *   any invalid macro -> generic error.
 * - total-only: any invalid macro -> generic error.
 * Returns the Spanish error message, or null if valid.
 */
internal fun validateNutrition(
    title: String,
    mode: WireEntryMode,
    ingredients: List<IngredientDraft>,
    totalCalories: String,
    totalProtein: String,
    totalCarbs: String,
    totalFat: String,
): String? {
    if (title.trim().isEmpty()) return "El título es obligatorio."
    if (mode == WireEntryMode.PER_INGREDIENT) {
        var hasNamed = false
        for (item in ingredients) {
            val name = item.name.trim()
            if (name.isEmpty()) continue
            hasNamed = true
            val invalid = item.calories != "" && parseMacro(item.calories) is MacroParse.Invalid ||
                item.protein != "" && parseMacro(item.protein) is MacroParse.Invalid ||
                item.carbs != "" && parseMacro(item.carbs) is MacroParse.Invalid ||
                item.fat != "" && parseMacro(item.fat) is MacroParse.Invalid
            if (invalid) return "Algo salió mal. Inténtalo de nuevo."
        }
        if (!hasNamed) return "Ingredientes"
        return null
    }
    val totals = listOf(totalCalories, totalProtein, totalCarbs, totalFat)
    if (totals.any { it != "" && parseMacro(it) is MacroParse.Invalid }) {
        return "Algo salió mal. Inténtalo de nuevo."
    }
    return null
}