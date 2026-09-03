package com.blackwatermacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.core.formatNumberEs
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.WireEntryMode
import sh.calvin.reorderable.ReorderableCollectionItemScope

/**
 * Meal card mirroring the web `MealCard`: drag grip handle, title + summary,
 * edit/delete actions, per-ingredient nutrition, notes, and macro badges.
 */
@Composable
fun MealCard(
    meal: MealDTO,
    scope: ReorderableCollectionItemScope,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {},
                    modifier = with(scope) { Modifier.draggableHandle() },
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Reordenar comida",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = meal.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = if (meal.entryMode == WireEntryMode.TOTAL_ONLY) {
                            "Total manual"
                        } else {
                            "${meal.ingredients.size} ingredientes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (meal.entryMode == WireEntryMode.PER_INGREDIENT && meal.ingredients.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(Modifier.padding(start = 4.dp, top = 1.dp, bottom = 1.dp)) {
                    meal.ingredients.forEachIndexed { index, ingredient ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = ingredient.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = ingredient.quantity ?: "",
                                modifier = Modifier
                                    .width(64.dp)
                                    .padding(start = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = ingredient.calories?.let { "${formatNumberEs(it)} kcal" } ?: "",
                                modifier = Modifier
                                    .width(64.dp)
                                    .padding(start = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = ingredient.protein?.let { "${formatNumberEs(it)} g proteína" } ?: "",
                                modifier = Modifier
                                    .width(84.dp)
                                    .padding(start = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (ingredient.carbs != null || ingredient.fat != null) {
                                Text(
                                    text = "…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (!meal.notes.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = meal.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroBadge(
                    text = "${formatNumberEs(meal.resolvedCalories)} kcal",
                    filled = true,
                    modifier = Modifier.weight(1f),
                )
                MacroBadge(
                    text = "${formatNumberEs(meal.resolvedProtein, 1)} g · Proteína",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroBadge(
                    text = "${formatNumberEs(meal.resolvedCarbs, 1)} g · Carbohidratos",
                    modifier = Modifier.weight(1f),
                )
                MacroBadge(
                    text = "${formatNumberEs(meal.resolvedFat, 1)} g · Grasa",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MacroBadge(text: String, filled: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (filled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (filled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}