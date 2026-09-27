package com.blackwatermacros.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.TemplateDTO
import kotlinx.coroutines.flow.Flow

/**
 * «Añadir comida» (web `AddFoodSheet`): every way to add food to [day].
 * Escribir a mano opens the review form; Copiar de otro día and the templates
 * add meals directly (the caller offers Undo). Swipe down or tap outside closes it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFoodSheet(
    day: String,
    templates: List<TemplateDTO>,
    mealsOn: (String) -> Flow<List<MealDTO>>,
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    /** Adds meals with this content to [day]; the message is for the Undo snackbar. */
    onAdd: (sources: List<MealFormValue>, message: String) -> Unit,
) {
    var copying by rememberSaveable { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.add_food_copied, formatDateShort(day))
    val templateAppliedMessage = stringResource(R.string.template_applied)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (copying) {
                    IconButton(onClick = { copying = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
                Text(
                    stringResource(if (copying) R.string.add_food_copy else R.string.add_food_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (!copying) {
                SourceRow(Icons.Filled.EditNote, stringResource(R.string.add_food_manual), stringResource(R.string.add_food_manual_hint)) {
                    onDismiss()
                    onManual()
                }
                Spacer(Modifier.height(8.dp))
                SourceRow(Icons.Filled.ContentCopy, stringResource(R.string.add_food_copy), stringResource(R.string.add_food_copy_hint)) {
                    copying = true
                }
                if (templates.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.add_food_templates),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { template ->
                            OutlinedButton(onClick = {
                                onDismiss()
                                onAdd(listOf(template.toFormValue()), templateAppliedMessage.format(template.name))
                            }, shape = RoundedCornerShape(50)) {
                                Text(template.name, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                CopyFromDay(targetDay = day, mealsOn = mealsOn) { meals ->
                    onDismiss()
                    onAdd(meals.map { it.toFormValue() }, copiedMessage)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, hint: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CopyFromDay(
    targetDay: String,
    mealsOn: (String) -> Flow<List<MealDTO>>,
    onCopy: (List<MealDTO>) -> Unit,
) {
    var sourceDay by rememberSaveable { mutableStateOf(addDaysToKey(targetDay, -1)) }
    var selected by remember(sourceDay) { mutableStateOf(setOf<String>()) }
    val meals by remember(sourceDay) { mealsOn(sourceDay) }.collectAsState(initial = null)

    DayNavigator(
        day = sourceDay,
        isToday = sourceDay == todayKey(),
        caption = formatDateLong(sourceDay),
        onPrev = { sourceDay = addDaysToKey(sourceDay, -1) },
        onNext = { sourceDay = addDaysToKey(sourceDay, 1) },
        onToday = { sourceDay = todayKey() },
    )
    Spacer(Modifier.height(8.dp))
    val list = meals
    when {
        list == null -> Unit
        list.isEmpty() -> Text(
            stringResource(R.string.add_food_copy_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
        )
        else -> {
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            ) {
                list.forEachIndexed { index, meal ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val checked = meal.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (checked) selected - meal.id else selected + meal.id }
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + meal.id else selected - meal.id })
                        Text(
                            meal.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatNumber(meal.resolvedCalories)} kcal · ${formatNumber(meal.resolvedProtein, 1)} g",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.add_food_copy_target, formatDateShort(targetDay)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onCopy(list.orEmpty().filter { it.id in selected }) },
        enabled = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.add_food_copy_button, selected.size))
    }
}
