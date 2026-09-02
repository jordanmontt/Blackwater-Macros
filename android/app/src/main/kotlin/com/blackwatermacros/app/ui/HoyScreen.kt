package com.blackwatermacros.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.core.formatNumberEs
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.TemplateDTO
import kotlin.math.roundToLong
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Number of fixed non-meal items emitted at the top of the meal LazyColumn. */
private const val HEADER_COUNT = 3

/**
 * "Comidas/Hoy" screen matching the web `today` page: day navigator, daily
 * totals, recommendations card, apply-template row, and a reorderable meal
 * list with create/edit/delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoyScreen(
    onLogout: () -> Unit,
    viewModel: HoyViewModel = viewModel(),
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()

    var formOpen by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<MealDTO?>(null) }
    var deletingMeal by remember { mutableStateOf<MealDTO?>(null) }
    var applyingTemplateId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val loadedMeals = (state as? HoyUiState.Loaded)?.meals ?: emptyList()
    var listMeals by remember(day) { mutableStateOf(loadedMeals) }
    LaunchedEffect(state) {
        if (state is HoyUiState.Loaded) listMeals = (state as HoyUiState.Loaded).meals
    }

    fun applyTemplate(template: TemplateDTO) {
        applyingTemplateId = template.id
        viewModel.applyTemplate(template) { result ->
            applyingTemplateId = null
            if (result is ApplyTemplateResult.Applied) {
                scope.launch {
                    snackbarHostState.showSnackbar("«${template.name}» añadido a este día")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Comidas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Text(
                            "Salir",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingMeal = null
                    formOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir comida")
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            DayNavigator(
                day = day,
                isToday = day == todayKey(),
                caption = viewModel.dayCaption(),
                onPrev = viewModel::prevDay,
                onNext = viewModel::nextDay,
                onToday = viewModel::goToday,
            )

            when (state) {
                HoyUiState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is HoyUiState.Error -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (state as HoyUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is HoyUiState.Loaded -> {
                    val totals = Totals(loadedMeals)
                    if (loadedMeals.isEmpty()) {
                        EmptyDayContent(
                            totals = totals,
                            templates = templates,
                            applyingTemplateId = applyingTemplateId,
                            onApplyTemplate = ::applyTemplate,
                            onAdd = {
                                editingMeal = null
                                formOpen = true
                            },
                        )
                    } else {
                        MealList(
                            meals = listMeals,
                            totals = totals,
                            templates = templates,
                            applyingTemplateId = applyingTemplateId,
                            onEdit = { meal ->
                                editingMeal = meal
                                formOpen = true
                            },
                            onDelete = { meal -> deletingMeal = meal },
                            onApplyTemplate = ::applyTemplate,
                            onReorder = { newList -> viewModel.reorder(newList) },
                        )
                    }
                }
            }
        }
    }

    if (formOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                formOpen = false
                editingMeal = null
            },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.Hidden },
            ),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
            ) {
                AddMealScreen(
                    logDate = day,
                    meal = editingMeal,
                    onCancel = {
                        formOpen = false
                        editingMeal = null
                    },
                    onSaved = {
                        formOpen = false
                        editingMeal = null
                        viewModel.refresh()
                    },
                )
            }
        }
    }

    deletingMeal?.let { meal ->
        AlertDialog(
            onDismissRequest = { deletingMeal = null },
            title = { Text("¿Eliminar comida?") },
            text = { Text("Se borrará esta comida y sus ingredientes. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMeal(meal.id) { ok ->
                            if (ok) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Comida eliminada")
                                }
                            }
                        }
                        deletingMeal = null
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMeal = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun MealList(
    meals: List<MealDTO>,
    totals: Totals,
    templates: List<TemplateDTO>,
    applyingTemplateId: String?,
    onEdit: (MealDTO) -> Unit,
    onDelete: (MealDTO) -> Unit,
    onApplyTemplate: (TemplateDTO) -> Unit,
    onReorder: (List<MealDTO>) -> Unit,
) {
    val lazyListState: LazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorder(
            meals.toMutableList().apply {
                add(to.index - HEADER_COUNT, removeAt(from.index - HEADER_COUNT))
            },
        )
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "totals") { DailyTotalsCard(totals) }
        item(key = "recommendations") {
            NutritionRecommendationsCard(totals.calories, totals.protein)
        }
        item(key = "templates") {
            if (templates.isEmpty()) {
                Spacer(Modifier.height(4.dp))
            } else {
                TemplatesRow(
                    templates = templates,
                    applyingTemplateId = applyingTemplateId,
                    onApply = onApplyTemplate,
                )
            }
        }
        items(meals, key = { it.id }) { meal ->
            ReorderableItem(reorderableState, key = meal.id) { isDragging ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                ) {
                    MealCard(
                        meal = meal,
                        scope = this,
                        onEdit = { onEdit(meal) },
                        onDelete = { onDelete(meal) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDayContent(
    totals: Totals,
    templates: List<TemplateDTO>,
    applyingTemplateId: String?,
    onApplyTemplate: (TemplateDTO) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DailyTotalsCard(totals)
        NutritionRecommendationsCard(totals.calories, totals.protein)
        if (templates.isNotEmpty()) {
            TemplatesRow(
                templates = templates,
                applyingTemplateId = applyingTemplateId,
                onApply = onApplyTemplate,
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(12.dp),
                ),
            shape = RoundedCornerShape(12.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Text(
                text = "Todavía no has registrado ninguna comida este día.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TemplatesRow(
    templates: List<TemplateDTO>,
    applyingTemplateId: String? = null,
    onApply: (TemplateDTO) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = "Aplicar plantilla",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            templates.forEach { template ->
                OutlinedButton(
                    onClick = { onApply(template) },
                    enabled = applyingTemplateId == null,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        if (applyingTemplateId == template.id) "Añadiendo…" else template.name,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayNavigator(
    day: String,
    isToday: Boolean,
    caption: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) { Text("‹", style = MaterialTheme.typography.titleLarge) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = caption,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (!isToday) {
                Text(
                    text = "Hoy",
                    modifier = Modifier.padding(top = 2.dp).clickable(onClick = onToday),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onNext) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun DailyTotalsCard(totals: Totals) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Totales del día",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalCell("Calorías", "${totals.calories.roundToLong()} kcal", Modifier.weight(1f))
                TotalCell("Proteína", "${formatNumberEs(totals.protein, 1)} g", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalCell("Carbohidratos", "${formatNumberEs(totals.carbs, 1)} g", Modifier.weight(1f))
                TotalCell("Grasa", "${formatNumberEs(totals.fat, 1)} g", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TotalCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class Totals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)

private fun Totals(meals: List<MealDTO>): Totals =
    meals.fold(Totals()) { acc, meal ->
        Totals(
            calories = acc.calories + meal.resolvedCalories,
            protein = acc.protein + meal.resolvedProtein,
            carbs = acc.carbs + meal.resolvedCarbs,
            fat = acc.fat + meal.resolvedFat,
        )
    }