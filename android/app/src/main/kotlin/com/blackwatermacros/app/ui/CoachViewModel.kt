package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.AiInput
import com.blackwatermacros.app.core.AiMessage
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.core.COACH_WEIGHT_DAYS
import com.blackwatermacros.app.core.CoachInput
import com.blackwatermacros.app.core.CoachMeal
import com.blackwatermacros.app.core.CoachWeight
import com.blackwatermacros.app.core.EXPENDITURE_WINDOW_DAYS
import com.blackwatermacros.app.core.IngredientInput
import com.blackwatermacros.app.core.addDaysToKey
import com.blackwatermacros.app.core.buildCoachContext
import com.blackwatermacros.app.core.buildCoachSystemPrompt
import com.blackwatermacros.app.core.todayKey
import com.blackwatermacros.app.data.AppRepository
import com.blackwatermacros.app.data.ai.AiClient
import com.blackwatermacros.app.data.ai.AiException
import com.blackwatermacros.app.data.ai.AiFailure
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.ai.aiLanguageName
import com.blackwatermacros.app.data.ai.AiEngineChoice
import com.blackwatermacros.app.data.ai.usableEngine
import com.blackwatermacros.app.data.ai.local.LocalEngine
import com.blackwatermacros.app.data.ai.local.LocalModelState
import com.blackwatermacros.app.data.ai.local.LocalModelSpec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val role: AiRole,
    val text: String,
    /** Set on an assistant message whose answer failed. */
    val error: AiFailure? = null,
)

data class ChatState(val messages: List<ChatMessage> = emptyList(), val streaming: Boolean = false)

/** Earlier turns sent with each question (web `COACH_HISTORY_MESSAGES`). */
const val COACH_HISTORY_MESSAGES = 20

/**
 * The earlier turns the model sees (web `historyForModel`): failed or empty
 * answers and their questions are left out; it always starts with the user.
 */
fun historyForModel(messages: List<ChatMessage>): List<AiMessage> {
    val turns = mutableListOf<AiMessage>()
    messages.forEachIndexed { index, message ->
        if (message.error != null) return@forEachIndexed
        val next = messages.getOrNull(index + 1)
        if (message.role == AiRole.USER && next != null && (next.error != null || next.text.isBlank())) return@forEachIndexed
        if (message.role == AiRole.ASSISTANT && message.text.isBlank()) return@forEachIndexed
        turns += AiMessage(message.role, message.text)
    }
    val recent = turns.takeLast(COACH_HISTORY_MESSAGES).toMutableList()
    while (recent.isNotEmpty() && recent.first().role != AiRole.USER) recent.removeAt(0)
    return recent
}

/**
 * Coach (web `lib/ai/coach-chat.ts`): memory only (D3), scoped to the activity
 * so it survives switching tabs and is gone when the app closes. Each question
 * carries a fresh summary of the user's data when the D11 toggle is on.
 */
class CoachViewModel(
    private val repository: AppRepository = AppGraph.repository,
    private val settings: AiSettingsStore = AppGraph.aiSettings,
    private val client: AiClient = AppGraph.ai,
    private val language: () -> String = { aiLanguageName(appLocale().language) },
    private val local: LocalEngine? = AppGraph.localEngine,
    localModel: StateFlow<LocalModelState> = AppGraph.localModels.state,
    /** For «Gemma 4 E2B · teléfono» under the chat. */
    val localModelSpec: StateFlow<LocalModelSpec> = AppGraph.localModels.selected,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    val aiSettings = settings.settings

    /** The engine the coach uses now (cloud or this phone), or null when it is not set up. */
    val engine: StateFlow<AiEngineChoice?> = combine(settings.settings, localModel) { current, model ->
        usableEngine(current.coachEngine, current.ready, model == LocalModelState.Ready)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, usableEngine(settings.current.coachEngine, settings.current.ready, localModel.value == LocalModelState.Ready))

    private var nextId = 1L
    private var job: Job? = null

    private fun patchLast(update: (ChatMessage) -> ChatMessage) {
        val messages = _state.value.messages
        if (messages.isEmpty()) return
        _state.value = _state.value.copy(messages = messages.dropLast(1) + update(messages.last()))
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _state.value.streaming) return
        val history = historyForModel(_state.value.messages)
        _state.value = ChatState(
            messages = _state.value.messages +
                ChatMessage(nextId++, AiRole.USER, question) +
                ChatMessage(nextId++, AiRole.ASSISTANT, ""),
            streaming = true,
        )
        job = viewModelScope.launch {
            try {
                val current = settings.current
                val context = if (current.coachSeesData) buildCoachContext(loadCoachInput()) else null
                val system = buildCoachSystemPrompt(language(), context)
                val messages = history + AiMessage(AiRole.USER, question)
                val answer = if (current.coachEngine == AiEngineChoice.DEVICE && local != null) {
                    local.stream(system, messages)
                } else {
                    client.stream(current.config, AiInput(system, messages, json = false, stream = true, maxTokens = 4096))
                }
                answer.collect { piece -> patchLast { it.copy(text = it.text + piece) } }
                if (_state.value.messages.lastOrNull()?.text.isNullOrBlank()) patchLast { it.copy(error = AiFailure.EMPTY) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                patchLast { it.copy(error = e.failure) }
            } catch (e: Exception) {
                patchLast { it.copy(error = AiFailure.PROVIDER) }
            } finally {
                _state.value = _state.value.copy(streaming = false)
            }
        }
    }

    /** Stops the answer being written; what arrived so far stays. */
    fun stop() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(streaming = false)
    }

    /** «Nueva conversación». */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = ChatState()
    }

    /** Same data as the Comidas card and Progreso: profile, targets, 4 weeks of meals, weigh-ins. */
    private suspend fun loadCoachInput(today: String = todayKey()): CoachInput {
        val profile = repository.profile().first()
        val weightDtos = repository.weights().first()
        val from = addDaysToKey(today, -EXPENDITURE_WINDOW_DAYS)
        val meals = repository.allMeals().first().filter { it.logDate in from..today }
        val ready = recommend(weightDtos, profile, meals.filter { it.logDate < today }, today) as? RecommendationsUiState.Ready
        val weightFrom = addDaysToKey(today, -COACH_WEIGHT_DAYS)
        return CoachInput(
            today = today,
            profile = profile,
            calorie = ready?.calorie,
            protein = ready?.protein,
            expenditure = ready?.expenditure,
            meals = meals.map { meal ->
                CoachMeal(
                    logDate = meal.logDate,
                    title = meal.title,
                    ingredients = meal.ingredients.map { IngredientInput(it.name, it.quantity, it.calories, it.protein, it.carbs, it.fat) },
                    resolvedCalories = meal.resolvedCalories,
                    resolvedProtein = meal.resolvedProtein,
                    resolvedCarbs = meal.resolvedCarbs,
                    resolvedFat = meal.resolvedFat,
                )
            },
            weights = weightDtos.mapNotNull { w -> localDateKey(w.measuredAt)?.let { CoachWeight(it, w.weightKg, w.bodyFatPct) } }
                .filter { it.date in weightFrom..today }
                .sortedBy { it.date },
        )
    }
}
