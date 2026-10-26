package com.blackwatermacros.app.data.ai.local

import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiRole
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The phone's model keeps the coach's conversation between questions, so a follow-up
 * reads only the new question: only while it holds exactly what the question needs.
 */
class LocalEngineTest {

    private val system = "Coach. Today: 2026-10-25"
    private val turns = listOf(ChatTurn(AiRole.USER, "¿Qué ceno?"), ChatTurn(AiRole.ASSISTANT, "Pollo con arroz."))
    private val history = listOf(AiMessage(AiRole.USER, "¿Qué ceno?"), AiMessage(AiRole.ASSISTANT, "Pollo con arroz."))

    private fun canContinue(
        chatSystem: String? = system,
        chatTurns: List<ChatTurn> = turns,
        tokenCount: Int? = 1500,
        nextSystem: String = system,
        nextHistory: List<AiMessage> = history,
        newTokens: Int = 2000,
    ) = canContinueChat(chatSystem, chatTurns, tokenCount, nextSystem, nextHistory, newTokens, maxTokens = 4096)

    @Test
    fun `a follow-up continues the kept conversation`() {
        assertThat(canContinue()).isTrue()
        // Earlier turns are compared by their words: a photo sent with them does not matter.
        val withPhoto = listOf(history[0].copy(images = listOf(AiImage("image/jpeg", "abc"))), history[1])
        assertThat(canContinue(nextHistory = withPhoto)).isTrue()
    }

    @Test
    fun `anything else starts a new conversation`() {
        // No conversation yet, or a meal logged since (the data summary changed).
        assertThat(canContinue(chatSystem = null)).isFalse()
        assertThat(canContinue(nextSystem = "Coach. Today: 2026-10-26")).isFalse()
        // Turns dropped (to fit, or a failed answer left out), or «Nueva conversación».
        assertThat(canContinue(nextHistory = history.drop(2))).isFalse()
        assertThat(canContinue(nextHistory = history + AiMessage(AiRole.USER, "¿Y mañana?"))).isFalse()
        // No room left for the question and its answer, or the size is unknown.
        assertThat(canContinue(tokenCount = 2500)).isFalse()
        assertThat(canContinue(tokenCount = null)).isFalse()
    }
}
