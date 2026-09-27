package com.blackwatermacros.app.data.ai

import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.AiInput
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.core.MealEstimate
import com.blackwatermacros.app.core.MealEstimateResult
import com.blackwatermacros.app.core.buildMealEstimateSystemPrompt
import com.blackwatermacros.app.core.buildMealEstimateUserText
import com.blackwatermacros.app.core.parseMealEstimate

/**
 * Photos and/or a description → a meal estimate for the review form (web
 * `lib/ai/estimate.ts`). The photos go only to the provider chosen in Ajustes.
 */
class MealEstimator(private val client: AiClient, private val settings: AiSettingsStore) {

    /** [language] in English («Spanish»), see [aiLanguageName]. */
    suspend fun estimate(description: String, photos: List<AiImage>, language: String): MealEstimate {
        val text = client.complete(
            settings.current.config,
            AiInput(
                system = buildMealEstimateSystemPrompt(language),
                messages = listOf(AiMessage(AiRole.USER, buildMealEstimateUserText(description, photos.size), photos)),
                json = true,
                stream = false,
                // Thinking models spend part of this before answering.
                maxTokens = 8192,
            ),
        )
        return when (val result = parseMealEstimate(text)) {
            is MealEstimateResult.Ok -> result.estimate
            is MealEstimateResult.Error -> throw AiException(AiFailure.UNREADABLE, text.take(300))
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
