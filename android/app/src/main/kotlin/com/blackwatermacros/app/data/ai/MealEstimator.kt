package com.blackwatermacros.app.data.ai

import android.util.Log
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.AiInput
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.core.MealEstimate
import com.blackwatermacros.app.core.MealEstimateResult
import com.blackwatermacros.app.core.buildMealEstimateSystemPrompt
import com.blackwatermacros.app.core.buildMealEstimateUserText
import com.blackwatermacros.app.core.parseMealEstimate
import com.blackwatermacros.app.data.ai.local.LocalEngine

/**
 * Photos and/or a description → a meal estimate for the review form (web
 * `lib/ai/estimate.ts`). The photos go only to the provider chosen in Ajustes.
 */
class MealEstimator(
    private val client: AiClient,
    private val settings: AiSettingsStore,
    /** The on-device model, when the user chose it for photos (D5). */
    private val local: LocalEngine? = null,
) {

    /** [language] in English («Spanish»), see [aiLanguageName]. */
    suspend fun estimate(description: String, photos: List<AiImage>, language: String): MealEstimate {
        val system = buildMealEstimateSystemPrompt(language)
        val userText = buildMealEstimateUserText(description, photos.size)
        val text = if (settings.current.photoEngine == AiEngineChoice.DEVICE && local != null) {
            if (photos.isNotEmpty() && !local.supportsImages()) throw AiException(AiFailure.NO_VISION)
            local.complete(system, userText, photos, LocalEngine.MEAL_ESTIMATE_SCHEMA)
        } else {
            client.complete(
                settings.current.config,
                AiInput(
                    system = system,
                    messages = listOf(AiMessage(AiRole.USER, userText, photos)),
                    json = true,
                    stream = false,
                    // Thinking models spend part of this before answering.
                    maxTokens = 8192,
                ),
            )
        }
        return when (val result = parseMealEstimate(text)) {
            is MealEstimateResult.Ok -> result.estimate
            is MealEstimateResult.Error -> {
                if (BuildConfig.DEBUG) Log.d("MealEstimator", "Unreadable answer: ${text.take(500)}")
                throw AiException(AiFailure.UNREADABLE, text.take(300))
            }
        }
    }
}

/** The model is told in English which language to answer in. */
fun aiLanguageName(languageCode: String): String = when (languageCode) {
    "es" -> "Spanish"
    "fr" -> "French"
    "it" -> "Italian"
    "de" -> "German"
    else -> "English"
}
