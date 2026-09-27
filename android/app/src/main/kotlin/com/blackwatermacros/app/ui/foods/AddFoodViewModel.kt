package com.blackwatermacros.app.ui.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.FoodLang
import com.blackwatermacros.app.core.FoodProduct
import com.blackwatermacros.app.core.GenericFood
import com.blackwatermacros.app.core.GenericFoodMatch
import com.blackwatermacros.app.core.searchGenericFoods
import com.blackwatermacros.app.data.foods.FoodChoice
import com.blackwatermacros.app.data.foods.GenericFoodsStore
import com.blackwatermacros.app.data.foods.OpenFoodFactsClient
import com.blackwatermacros.app.data.foods.RecentFoods
import com.blackwatermacros.app.ui.appLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.IOException

/** Open Food Facts part of the search. */
sealed interface OnlineResults {
    data object Idle : OnlineResults
    data object Loading : OnlineResults
    data class Done(val choices: List<FoodChoice>) : OnlineResults
    data object Failed : OnlineResults
}

data class FoodSearchState(
    val query: String = "",
    val recents: List<FoodChoice> = emptyList(),
    val generic: List<FoodChoice> = emptyList(),
    val online: OnlineResults = OnlineResults.Idle,
)

sealed interface BarcodeLookup {
    data object Idle : BarcodeLookup
    data object Loading : BarcodeLookup
    data class Found(val choice: FoodChoice) : BarcodeLookup
    data object NotFound : BarcodeLookup
    data object Offline : BarcodeLookup
}

/** Spanish first: foods follow the app language when it has names for it, else Spanish. */
fun currentFoodLang(): FoodLang =
    FoodLang.entries.firstOrNull { it.code == appLocale().language } ?: FoodLang.ES

fun GenericFoodMatch.toChoice() = FoodChoice(
    key = food.id,
    name = name,
    calories = food.per100g.calories,
    protein = food.per100g.protein,
    carbs = food.per100g.carbs,
    fat = food.per100g.fat,
    source = "generic",
)

fun FoodProduct.toChoice() = FoodChoice(
    key = "off:${code ?: name}",
    name = name,
    brand = brand,
    calories = per100g.calories,
    protein = per100g.protein,
    carbs = per100g.carbs,
    fat = per100g.fat,
    servingGrams = servingGrams,
    source = "off",
    incomplete = incomplete,
)

/**
 * «Buscar» and «Código de barras» (web `FoodSearch` + `BarcodeScanner`):
 * generic foods first (offline, instant), then Open Food Facts (online,
 * debounced, cached per query), recents when the box is empty.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddFoodViewModel(
    private val genericFoods: GenericFoodsStore = AppGraph.genericFoods,
    private val openFoodFacts: OpenFoodFactsClient = AppGraph.openFoodFacts,
    private val recentFoods: RecentFoods = AppGraph.recentFoods,
    private val lang: () -> FoodLang = ::currentFoodLang,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val recents = MutableStateFlow(recentFoods.list())
    private val onlineCache = mutableMapOf<String, List<FoodChoice>>()

    private val foods = flow { emit(runCatching { genericFoods.all() }.getOrDefault(emptyList())) }

    /** Only the latest query is searched: typing fast skips the ones in between. */
    private val genericResults = combine(query, foods) { q, all -> q to all }
        .conflate()
        .map { (q, all) -> genericMatches(all, q) }
        .flowOn(Dispatchers.Default)

    /** A new query shows «Buscando…» at once, waits for typing to pause, then asks (cached per query). */
    private val onlineResults = query
        .map { it.trim() }
        .distinctUntilChanged()
        .transformLatest { q ->
            if (q.length < ONLINE_MIN_CHARS) {
                emit(OnlineResults.Idle)
                return@transformLatest
            }
            val cached = onlineCache[q.lowercase()]
            if (cached != null) {
                emit(OnlineResults.Done(cached))
                return@transformLatest
            }
            emit(OnlineResults.Loading)
            delay(ONLINE_DEBOUNCE_MS)
            emit(searchOnline(q))
        }
        .onStart { emit(OnlineResults.Idle) }

    val state: StateFlow<FoodSearchState> =
        combine(query, recents, genericResults, onlineResults) { q, recent, generic, online ->
            FoodSearchState(q, recent, generic, if (q.trim().length < ONLINE_MIN_CHARS) OnlineResults.Idle else online)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodSearchState())

    private val _barcode = MutableStateFlow<BarcodeLookup>(BarcodeLookup.Idle)
    val barcode: StateFlow<BarcodeLookup> = _barcode.asStateFlow()

    fun setQuery(value: String) {
        query.value = value
    }

    /** Fresh search box and barcode state each time the sheet opens. */
    fun reset() {
        query.value = ""
        _barcode.value = BarcodeLookup.Idle
        recents.value = recentFoods.list()
    }

    fun remember(choice: FoodChoice) {
        recentFoods.remember(choice)
        recents.value = recentFoods.list()
    }

    fun lookUpBarcode(code: String) {
        val digits = code.filter { it.isDigit() }
        if (digits.length < 6) return
        _barcode.value = BarcodeLookup.Loading
        viewModelScope.launch {
            _barcode.value = try {
                openFoodFacts.product(digits, lang())?.let { BarcodeLookup.Found(it.toChoice()) } ?: BarcodeLookup.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BarcodeLookup.Offline
            }
        }
    }

    fun clearBarcode() {
        _barcode.value = BarcodeLookup.Idle
    }

    private fun genericMatches(all: List<GenericFood>, q: String): List<FoodChoice> =
        if (q.isBlank()) emptyList() else searchGenericFoods(all, q, lang(), GENERIC_LIMIT).map { it.toChoice() }

    private suspend fun searchOnline(q: String): OnlineResults =
        try {
            val choices = openFoodFacts.search(q, lang()).map { it.toChoice() }
            onlineCache[q.lowercase()] = choices
            OnlineResults.Done(choices)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            OnlineResults.Failed
        } catch (e: RuntimeException) {
            OnlineResults.Failed
        }

    private companion object {
        const val ONLINE_MIN_CHARS = 3
        const val ONLINE_DEBOUNCE_MS = 450L
        const val GENERIC_LIMIT = 8
    }
}
