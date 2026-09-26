package com.blackwatermacros.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.res.stringResource
import com.blackwatermacros.app.R
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.MealDTO
import com.blackwatermacros.app.data.TemplateDTO
import kotlin.math.roundToLong
import kotlinx.coroutines.launch
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
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenWeight: () -> Unit = {},
    viewModel: HoyViewModel = viewModel(),
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()

    var formOpen by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<MealDTO?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val loadedMeals = (state as? HoyUiState.Loaded)?.meals ?: emptyList()
    var listMeals by remember(day) { mutableStateOf(loadedMeals) }
    LaunchedEffect(state) {
        if (state is HoyUiState.Loaded) listMeals = (state as HoyUiState.Loaded).meals
    }

    val templateAddedMessage = stringResource(R.string.template_applied)
    val mealDeletedMessage = stringResource(R.string.meal_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    /** Deletes right away; the snackbar offers Undo instead of asking first. */
    fun deleteMeal(meal: MealDTO) {
        viewModel.deleteMeal(meal.id)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = mealDeletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreMeal(meal)
        }
    }

    fun applyTemplate(template: TemplateDTO) {
        viewModel.applyTemplate(template)
        scope.launch { snackbarHostState.showSnackbar(templateAddedMessage.format(template.name)) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenteredTopAppBar(
                title = stringResource(R.string.tab_meals),
                trailing = { SyncIndicator(onClick = onOpenSettings) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingMeal = null
                    formOpen = true
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.meal_add))
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
                is HoyUiState.Loaded -> {
                    val totals = Totals(loadedMeals)
                    if (loadedMeals.isEmpty()) {
                        EmptyDayContent(
                            totals = totals,
                            templates = templates,
                            onApplyTemplate = ::applyTemplate,
                            onOpenProfile = onOpenProfile,
                            onOpenWeight = onOpenWeight,
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
                            onEdit = { meal ->
                                editingMeal = meal
                                formOpen = true
                            },
                            onDelete = ::deleteMeal,
                            onApplyTemplate = ::applyTemplate,
                            onOpenProfile = onOpenProfile,
                            onOpenWeight = onOpenWeight,
                            onReorder = { newList ->
                                listMeals = newList
                                viewModel.reorder(newList)
                            },
                        )
                    }
                }
            }
        }
    }

    if (formOpen) {
        val editing = editingMeal
        MealFormSheet(
            heading = stringResource(if (editing != null) R.string.meal_edit else R.string.meal_new),
            initial = editing?.toFormValue(),
            onDismiss = {
                formOpen = false
                editingMeal = null
            },
            onSubmit = { value ->
                viewModel.saveMeal(editing?.id, value.toMealRequest(day))
                formOpen = false
                editingMeal = null
            },
        )
    }

}

@Composable
private fun MealList(
    meals: List<MealDTO>,
    totals: Totals,
    templates: List<TemplateDTO>,
    onEdit: (MealDTO) -> Unit,
    onDelete: (MealDTO) -> Unit,
    onApplyTemplate: (TemplateDTO) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWeight: () -> Unit,
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
            NutritionRecommendationsCard(totals.calories, totals.protein, onOpenProfile, onOpenWeight)
        }
        item(key = "templates") {
            if (templates.isEmpty()) {
                Spacer(Modifier.height(4.dp))
            } else {
                TemplatesRow(
                    templates = templates,
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
                    color = Color.Transparent,
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
    onApplyTemplate: (TemplateDTO) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWeight: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DailyTotalsCard(totals)
        NutritionRecommendationsCard(totals.calories, totals.protein, onOpenProfile, onOpenWeight)
        if (templates.isNotEmpty()) {
            TemplatesRow(
                templates = templates,
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
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.meals_empty_day),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.meal_add),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatesRow(
    templates: List<TemplateDTO>,
    onApply: (TemplateDTO) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = stringResource(R.string.template_apply),
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
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        template.name,
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
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.day_previous))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = caption,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (isToday) {
                Text(
                    text = stringResource(R.string.today),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.day_next))
        }
    }
}

@Composable
private fun DailyTotalsCard(totals: Totals) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.day_totals),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalCell(stringResource(R.string.total_calories), "${totals.calories.roundToLong()} kcal", Modifier.weight(1f))
                TotalCell(stringResource(R.string.macro_protein), "${formatNumber(totals.protein, 1)} g", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalCell(stringResource(R.string.macro_carbs), "${formatNumber(totals.carbs, 1)} g", Modifier.weight(1f))
                TotalCell(stringResource(R.string.macro_fat), "${formatNumber(totals.fat, 1)} g", Modifier.weight(1f))
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