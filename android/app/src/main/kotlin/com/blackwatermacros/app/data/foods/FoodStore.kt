package com.blackwatermacros.app.data.foods

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.blackwatermacros.app.core.GenericFood
import com.blackwatermacros.app.core.Per100g
import com.blackwatermacros.app.core.parseGenericIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream

/** A food picked from search or a barcode, before choosing the portion (web `FoodChoice`). */
@Serializable
data class FoodChoice(
    val key: String,
    val name: String,
    val brand: String? = null,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val servingGrams: Double? = null,
    /** "generic" (bundled) or "off" (Open Food Facts). */
    val source: String,
    /** Some macros were missing in the source and count as 0. */
    val incomplete: Boolean = false,
) {
    val per100g: Per100g get() = Per100g(calories, protein, carbs, fat)
}

/** The bundled generic foods (`assets/foods/generic.json`), read once in the background. */
class GenericFoodsStore(private val open: () -> InputStream) {
    private val mutex = Mutex()
    private var cache: List<GenericFood>? = null

    suspend fun all(): List<GenericFood> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            open().use { stream -> parseGenericIndex(Json.parseToJsonElement(stream.readBytes().decodeToString())) }
        }.also { cache = it }
    }

    companion object {
        fun fromAssets(context: Context) = GenericFoodsStore { context.assets.open("foods/generic.json") }
    }
}

/** Foods picked recently on this phone (shown when the search box is empty). */
class RecentFoods(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("foods", Context.MODE_PRIVATE))

    private val serializer = ListSerializer(FoodChoice.serializer())

    fun list(): List<FoodChoice> =
        runCatching { Json.decodeFromString(serializer, prefs.getString(KEY, null) ?: "[]") }.getOrDefault(emptyList())

    fun remember(choice: FoodChoice) {
        val next = (listOf(choice) + list().filter { it.key != choice.key }).take(MAX)
        prefs.edit { putString(KEY, Json.encodeToString(serializer, next)) }
    }

    private companion object {
        const val KEY = "recent"
        const val MAX = 20
    }
}
