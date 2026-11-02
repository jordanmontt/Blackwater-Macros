package com.blackwatermacros.app.data.ai

import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.AiInput
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.core.LABEL_READING_USER_TEXT
import com.blackwatermacros.app.core.LabelReading
import com.blackwatermacros.app.core.buildLabelReadingSystemPrompt
import com.blackwatermacros.app.core.parseLabelReading
import com.blackwatermacros.app.data.ai.local.LocalEngine

/**
 * A photo of a nutrition label → the values for «Nuevo producto» (web
 * `lib/ai/label.ts`). Like «Foto o texto», the photo goes only to the engine
 * chosen for photos in Ajustes → IA.
 */
class LabelReader(
    private val client: AiClient,
    private val settings: AiSettingsStore,
    private val local: LocalEngine? = null,
) {
    /** [language] in English («Spanish»), see [aiLanguageName]. */
    suspend fun read(photo: AiImage, language: String): LabelReading {
        val system = buildLabelReadingSystemPrompt(language)
        val text = if (settings.current.photoEngine == AiEngineChoice.DEVICE && local != null) {
            if (!local.supportsImages()) throw AiException(AiFailure.NO_VISION)
            local.complete(system, LABEL_READING_USER_TEXT, listOf(photo))
        } else {
            client.complete(
                settings.current.config,
                AiInput(
                    system = system,
                    messages = listOf(AiMessage(AiRole.USER, LABEL_READING_USER_TEXT, listOf(photo))),
                    json = true,
                    stream = false,
                    // Thinking models spend part of this before answering.
                    maxTokens = 8192,
                ),
            )
        }
        return parseLabelReading(text) ?: throw AiException(AiFailure.UNREADABLE, text.take(300))
    }
}
