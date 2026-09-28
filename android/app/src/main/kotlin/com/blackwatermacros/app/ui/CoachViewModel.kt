package com.blackwatermacros.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.data.ai.AiClient
import com.blackwatermacros.app.data.ai.MAX_PHOTOS
import com.blackwatermacros.app.data.ai.PhotoCodec
import com.blackwatermacros.app.ui.foods.MealPhoto
import java.io.File
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
    /** What the user typed (may be empty when they only sent photos). */
    val text: String,
    /** Set on an assistant message whose answer failed. */
    val error: AiFailure? = null,
    /** The provider's (or engine's) own words about the failure. */
    val errorDetail: String = "",
    /** What the model is asked: [text], or a default question for photos alone. */
    val modelText: String = text,
    /** Photos sent with a question: small JPEGs in memory only (never stored). */
    val images: List<AiImage> = emptyList(),
    val previews: List<ImageBitmap> = emptyList(),
)

data class ChatState(val messages: List<ChatMessage> = emptyList(), val streaming: Boolean = false)

/** Earlier turns sent with each question (web `COACH_HISTORY_MESSAGES`). */
const val COACH_HISTORY_MESSAGES = 20

/**
 * The earlier turns the model sees (web `historyForModel`): failed or empty
 * answers and their questions are left out; it always starts with the user.
 * Photos travel again with their question, but only the newest [MAX_PHOTOS]
 * (counting the [newImages] of the question being sent).
 */
fun historyForModel(messages: List<ChatMessage>, newImages: Int = 0): List<AiMessage> {
    val turns = mutableListOf<AiMessage>()
    messages.forEachIndexed { index, message ->
        if (message.error != null) return@forEachIndexed
        val next = messages.getOrNull(index + 1)
        if (message.role == AiRole.USER && next != null && (next.error != null || next.text.isBlank())) return@forEachIndexed
        if (message.role == AiRole.ASSISTANT && message.text.isBlank()) return@forEachIndexed
        turns += AiMessage(message.role, message.modelText, message.images)
    }
    val recent = turns.takeLast(COACH_HISTORY_MESSAGES).toMutableList()
    var room = (MAX_PHOTOS - newImages).coerceAtLeast(0)
    for (i in recent.indices.reversed()) {
        val kept = recent[i].images.take(room)
        room -= kept.size
        recent[i] = recent[i].copy(images = kept)
    }
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

    private val _photos = MutableStateFlow<List<MealPhoto>>(emptyList())
    /** Photos for the next question (in memory only). */
    val photos: StateFlow<List<MealPhoto>> = _photos.asStateFlow()
    private var nextPhotoId = 1L

    /** Photos go to the cloud provider, or to the phone's model when it reads images (Gemma). */
    suspend fun canSendPhotos(): Boolean =
        settings.current.coachEngine != AiEngineChoice.DEVICE || local?.supportsImages() == true

    fun addPhotos(context: Context, uris: List<Uri>) {
        viewModelScope.launch { uris.take(MAX_PHOTOS - _photos.value.size).forEach { addPhoto(context, it, null) } }
    }

    /** A camera photo: read into memory, then the file is deleted at once. */
    fun addCameraFile(context: Context, file: File) {
        viewModelScope.launch { addPhoto(context, Uri.fromFile(file), file) }
    }

    private suspend fun addPhoto(context: Context, uri: Uri, deleteAfter: File?) {
        val scaled = PhotoCodec.read(context, uri, deleteAfter) ?: return
        if (_photos.value.size < MAX_PHOTOS) {
            _photos.value = _photos.value + MealPhoto(nextPhotoId++, scaled.image, scaled.preview.asImageBitmap())
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun setPhotosForTest(images: List<AiImage>) {
        _photos.value = images.map { MealPhoto(nextPhotoId++, it, ImageBitmap(1, 1)) }
    }

    fun removePhoto(id: Long) {
        _photos.value = _photos.value.filterNot { it.id == id }
    }

    /** [photoPrompt]: the question for photos sent without words («¿Qué me dices de esta foto?»). */
    fun send(text: String, photoPrompt: String = "") {
        val typed = text.trim()
        val photos = _photos.value
        if ((typed.isEmpty() && photos.isEmpty()) || _state.value.streaming) return
        val history = historyForModel(_state.value.messages, photos.size)
        val asked = ChatMessage(
            id = nextId++,
            role = AiRole.USER,
            text = typed,
            modelText = typed.ifEmpty { photoPrompt },
            images = photos.map { it.image },
            previews = photos.map { it.preview },
        )
        _photos.value = emptyList()
        _state.value = ChatState(
            messages = _state.value.messages + asked + ChatMessage(nextId++, AiRole.ASSISTANT, ""),
            streaming = true,
        )
        job = viewModelScope.launch {
            try {
                val current = settings.current
                val context = if (current.coachSeesData) buildCoachContext(loadCoachInput()) else null
                val system = buildCoachSystemPrompt(language(), context)
                val messages = history + AiMessage(AiRole.USER, asked.modelText, asked.images)
                val answer = if (current.coachEngine == AiEngineChoice.DEVICE && local != null) {
                    if (asked.images.isNotEmpty() && !local.supportsImages()) throw AiException(AiFailure.NO_VISION)
                    local.stream(system, messages)
                } else {
                    client.stream(current.config, AiInput(system, messages, json = false, stream = true, maxTokens = 4096))
                }
                answer.collect { piece -> patchLast { it.copy(text = it.text + piece) } }
                if (_state.value.messages.lastOrNull()?.text.isNullOrBlank()) patchLast { it.copy(error = AiFailure.EMPTY) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                patchLast { it.copy(error = e.failure, errorDetail = e.detail) }
            } catch (e: Exception) {
                patchLast { it.copy(error = AiFailure.PROVIDER, errorDetail = e.message.orEmpty().take(200)) }
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
        _photos.value = emptyList()
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
