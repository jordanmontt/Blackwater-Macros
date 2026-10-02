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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.TemplateDTO
import com.blackwatermacros.app.data.WireIngredient
import com.blackwatermacros.app.data.foods.FoodChoice
import com.blackwatermacros.app.core.foodToIngredient
import com.blackwatermacros.app.ui.foods.AddFoodViewModel
import com.blackwatermacros.app.ui.foods.BarcodeLookup
import com.blackwatermacros.app.ui.foods.BarcodeView
import com.blackwatermacros.app.ui.foods.FoodSearchView
import com.blackwatermacros.app.ui.foods.PortionView
import com.blackwatermacros.app.ui.foods.PhotoEstimateState
import com.blackwatermacros.app.ui.foods.PhotoEstimateView
import com.blackwatermacros.app.ui.foods.PhotoEstimateViewModel
import com.blackwatermacros.app.ui.foods.estimateNotice
import com.blackwatermacros.app.core.MealEstimate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow

internal enum class AddFoodView { MENU, COPY, SEARCH, BARCODE, PORTION, PHOTO }

/**
 * Whether closing «Añadir comida» now would lose something the user entered. The
 * AI description and photos count in every view (they survive the sheet's Back
 * arrow); the rest only on their own view.
 */
internal fun addFoodHasUnsavedInput(
    view: AddFoodView,
    photoText: String,
    photoCount: Int,
    estimating: Boolean,
    query: String,
    barcodeCode: String,
    copySelected: Int,
): Boolean =
    photoText.isNotBlank() || photoCount > 0 || estimating ||
        when (view) {
            AddFoodView.SEARCH -> query.isNotBlank()
            AddFoodView.BARCODE -> barcodeCode.isNotBlank()
            AddFoodView.PORTION -> true
            AddFoodView.COPY -> copySelected > 0
            AddFoodView.MENU, AddFoodView.PHOTO -> false
        }

/**
 * «Añadir comida» (web `AddFoodSheet`): every way to add food to [day].
 * Buscar and Código de barras pick a food and its portion and hand it over
 * ([onFoodPicked]) for the review form; Escribir a mano opens that form empty;
 * Copiar de otro día and the templates add meals directly (the caller offers
 * Undo). [pickOnly]: opened from the review form to add one more food, so only
 * Buscar and Código are offered. Swipe down or tap outside closes it; once
 * something was entered (an AI description, photos, a search…) it asks first
 * ([GuardedBottomSheet]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFoodSheet(
    day: String,
    templates: List<TemplateDTO>,
    mealsOn: (String) -> Flow<List<MealDTO>>,
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    /** Adds meals with this content to [day]; the message is for the Undo snackbar. */
    onAdd: (sources: List<MealFormValue>, message: String) -> Unit,
    onFoodPicked: (WireIngredient) -> Unit = {},
    /** The AI estimated a meal; the notice goes above the review form. */
    onEstimate: (estimate: MealEstimate, notice: String) -> Unit = { _, _ -> },
    onOpenAiSettings: () -> Unit = {},
    pickOnly: Boolean = false,
    foods: AddFoodViewModel = viewModel(),
    photo: PhotoEstimateViewModel = viewModel(),
) {
    var view by rememberSaveable { mutableStateOf(AddFoodView.MENU) }
    var back by rememberSaveable { mutableStateOf(AddFoodView.MENU) }
    var choice by remember { mutableStateOf<FoodChoice?>(null) }
    val searchState by foods.state.collectAsStateWithLifecycle()
    val barcode by foods.barcode.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        foods.reset()
        photo.reset()
    }
    // «Estimar “…” con IA» came from search: Back returns there.
    var fromSearch by rememberSaveable { mutableStateOf(false) }
    val photoState by photo.state.collectAsStateWithLifecycle()
    val done = photoState as? PhotoEstimateState.Done
    val notice = done?.let { estimateNotice(it.estimate) }
    LaunchedEffect(done) {
        if (done == null || notice == null) return@LaunchedEffect
        photo.consumed()
        onDismiss()
        onEstimate(done.estimate, notice)
    }
    LaunchedEffect(barcode) {
        val found = barcode as? BarcodeLookup.Found ?: return@LaunchedEffect
        choice = found.choice
        back = AddFoodView.BARCODE
        view = AddFoodView.PORTION
        foods.clearBarcode()
    }
    val copiedMessage = stringResource(R.string.add_food_copied, formatDateShort(day))
    val templateAppliedMessage = stringResource(R.string.template_applied)
    var barcodeCode by rememberSaveable { mutableStateOf("") }
    var copySelected by remember { mutableStateOf(setOf<String>()) }
    val photoText by photo.description.collectAsStateWithLifecycle()
    val photos by photo.photos.collectAsStateWithLifecycle()
    val unsaved = addFoodHasUnsavedInput(
        view = view,
        photoText = photoText,
        photoCount = photos.size,
        estimating = photoState == PhotoEstimateState.Estimating,
        query = searchState.query,
        barcodeCode = barcodeCode,
        copySelected = copySelected.size,
    )

    GuardedBottomSheet(
        hasUnsavedInput = unsaved,
        discardBody = stringResource(R.string.discard_add_body),
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (view != AddFoodView.MENU) {
                    IconButton(onClick = {
                        view = when {
                            view == AddFoodView.PORTION -> back
                            view == AddFoodView.PHOTO && fromSearch -> AddFoodView.SEARCH
                            else -> AddFoodView.MENU
                        }
                        fromSearch = false
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
                Text(
                    stringResource(
                        when (view) {
                            AddFoodView.MENU -> R.string.add_food_title
                            AddFoodView.COPY -> R.string.add_food_copy
                            AddFoodView.SEARCH -> R.string.food_search
                            AddFoodView.BARCODE -> R.string.food_barcode
                            AddFoodView.PORTION -> R.string.food_portion_title
                            AddFoodView.PHOTO -> R.string.photo_title
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))

            when (view) {
            AddFoodView.MENU -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigSource(Icons.Filled.PhotoCamera, stringResource(R.string.photo_title), stringResource(R.string.photo_hint), Modifier.weight(1f)) {
                        fromSearch = false
                        view = AddFoodView.PHOTO
                    }
                    BigSource(Icons.Filled.Search, stringResource(R.string.food_search), stringResource(R.string.food_search_hint), Modifier.weight(1f)) {
                        view = AddFoodView.SEARCH
                    }
                    BigSource(Icons.Filled.QrCodeScanner, stringResource(R.string.food_barcode), stringResource(R.string.food_barcode_hint), Modifier.weight(1f)) {
                        view = AddFoodView.BARCODE
                    }
                }
                if (!pickOnly) {
                Spacer(Modifier.height(8.dp))
                SourceRow(Icons.Filled.EditNote, stringResource(R.string.add_food_manual), stringResource(R.string.add_food_manual_hint)) {
                    onDismiss()
                    onManual()
                }
                Spacer(Modifier.height(8.dp))
                SourceRow(Icons.Filled.ContentCopy, stringResource(R.string.add_food_copy), stringResource(R.string.add_food_copy_hint)) {
                    view = AddFoodView.COPY
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
                }
            }
            AddFoodView.COPY -> CopyFromDay(
                targetDay = day,
                mealsOn = mealsOn,
                selected = copySelected,
                onSelectedChange = { copySelected = it },
            ) { meals ->
                onDismiss()
                onAdd(meals.map { it.toFormValue() }, copiedMessage)
            }
            AddFoodView.SEARCH -> FoodSearchView(
                searchState,
                foods::setQuery,
                onEstimateQuery = { query ->
                    photo.reset(autoDescription = query)
                    fromSearch = true
                    view = AddFoodView.PHOTO
                },
            ) {
                choice = it
                back = AddFoodView.SEARCH
                view = AddFoodView.PORTION
            }
            AddFoodView.PHOTO -> PhotoEstimateView(
                viewModel = photo,
                onOpenAiSettings = {
                    onDismiss()
                    onOpenAiSettings()
                },
                onManual = if (pickOnly) null else ({
                    onDismiss()
                    onManual()
                }),
            )
            AddFoodView.BARCODE -> BarcodeView(
                lookup = barcode,
                code = barcodeCode,
                onCodeChange = {
                    // Editing the code after a miss starts over.
                    if (barcode !is BarcodeLookup.Loading) foods.clearBarcode()
                    barcodeCode = it
                },
                onCode = foods::lookUpBarcode,
                onScanAgain = {
                    barcodeCode = ""
                    foods.clearBarcode()
                },
                onSearchByName = {
                    barcodeCode = ""
                    foods.clearBarcode()
                    view = AddFoodView.SEARCH
                },
                onPhoto = {
                    barcodeCode = ""
                    foods.clearBarcode()
                    fromSearch = false
                    view = AddFoodView.PHOTO
                },
            )
            AddFoodView.PORTION -> choice?.let { picked ->
                PortionView(picked) { grams ->
                    foods.remember(picked)
                    val ingredient = foodToIngredient(picked.name, picked.per100g, grams)
                    onDismiss()
                    onFoodPicked(
                        WireIngredient(ingredient.name, ingredient.quantity, ingredient.calories, ingredient.protein, ingredient.carbs, ingredient.fat),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun BigSource(icon: ImageVector, label: String, hint: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
    /** Ids of the ticked meals (kept by the sheet so it knows there is something to lose). */
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    onCopy: (List<MealDTO>) -> Unit,
) {
    var sourceDay by rememberSaveable { mutableStateOf(addDaysToKey(targetDay, -1)) }
    val meals by remember(sourceDay) { mealsOn(sourceDay) }.collectAsState(initial = null)
    // Another day: the ticks were for the meals of the previous one.
    fun goTo(newDay: String) {
        sourceDay = newDay
        onSelectedChange(emptySet())
    }

    DayNavigator(
        day = sourceDay,
        isToday = sourceDay == todayKey(),
        caption = formatDateLong(sourceDay),
        onPrev = { goTo(addDaysToKey(sourceDay, -1)) },
        onNext = { goTo(addDaysToKey(sourceDay, 1)) },
        onToday = { goTo(todayKey()) },
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
                            .clickable { onSelectedChange(if (checked) selected - meal.id else selected + meal.id) }
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onSelectedChange(if (it) selected + meal.id else selected - meal.id) })
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
