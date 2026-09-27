package com.blackwatermacros.app.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.data.ai.AiClient
import com.blackwatermacros.app.data.ai.AiException
import com.blackwatermacros.app.data.ai.AiFailure
import com.blackwatermacros.app.data.ai.AiSettings
import com.blackwatermacros.app.data.ai.AiSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.blackwatermacros.app.data.ai.AiEngineChoice
import com.blackwatermacros.app.data.ai.local.DeviceSupport
import com.blackwatermacros.app.data.ai.local.LocalEngine
import com.blackwatermacros.app.data.ai.local.LocalModelManager
import com.blackwatermacros.app.data.ai.local.LocalModelState
import com.blackwatermacros.app.data.ai.local.LocalModelSpec

sealed interface AiTestState {
    data object Idle : AiTestState
    data object Testing : AiTestState
    data object Ok : AiTestState
    data class Failed(val failure: AiFailure, val detail: String = "") : AiTestState
}

@StringRes
fun AiFailure.messageRes(): Int = when (this) {
    AiFailure.NOT_CONFIGURED -> R.string.ai_error_not_configured
    AiFailure.INVALID_KEY -> R.string.ai_error_invalid_key
    AiFailure.QUOTA -> R.string.ai_error_quota
    AiFailure.NOT_FOUND -> R.string.ai_error_not_found
    AiFailure.OFFLINE -> R.string.ai_error_offline
    AiFailure.EMPTY -> R.string.ai_error_empty
    AiFailure.UNREADABLE -> R.string.ai_error_unreadable
    AiFailure.PROVIDER -> R.string.ai_error_provider
    AiFailure.NO_VISION -> R.string.local_model_no_vision
    AiFailure.UNAVAILABLE -> R.string.ai_error_unavailable
    AiFailure.LOCAL_MODEL -> R.string.ai_error_local
}

@StringRes
fun AiProvider.labelRes(): Int = when (this) {
    AiProvider.GEMINI -> R.string.ai_provider_gemini
    AiProvider.OPENAI -> R.string.ai_provider_openai
    AiProvider.ANTHROPIC -> R.string.ai_provider_anthropic
    AiProvider.OPENROUTER -> R.string.ai_provider_openrouter
    AiProvider.CUSTOM -> R.string.ai_provider_custom
}

/** Ajustes → IA (web `AiSettingsCard`): saved as you type, on this phone only. */
class AiSettingsViewModel(
    private val store: AiSettingsStore = AppGraph.aiSettings,
    private val client: AiClient = AppGraph.ai,
    private val models: LocalModelManager = AppGraph.localModels,
    private val local: LocalEngine = AppGraph.localEngine,
) : ViewModel() {

    val settings: StateFlow<AiSettings> = store.settings

    val modelState: StateFlow<LocalModelState> = models.state
    val selectedModel: StateFlow<LocalModelSpec> = models.selected

    fun support(model: LocalModelSpec): DeviceSupport = models.support(model)

    fun selectModel(model: LocalModelSpec) = models.select(model)

    /** D6: null until known; false = the model takes no images, photos stay in the cloud. */
    val deviceVision: StateFlow<Boolean?> = models.state
        .map { if (it == LocalModelState.Ready) local.supportsImages() else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun downloadModel(anyNetwork: Boolean) = models.download(anyNetwork)

    fun cancelDownload() = models.cancel()

    /** Frees the space; features that used the phone go back to the cloud. */
    fun deleteModel() {
        store.update { it.copy(photoEngine = AiEngineChoice.CLOUD, coachEngine = AiEngineChoice.CLOUD) }
        models.delete { local.release() }
    }

    fun setPhotoEngine(choice: AiEngineChoice) = update { it.copy(photoEngine = choice) }

    fun setCoachEngine(choice: AiEngineChoice) = update { it.copy(coachEngine = choice) }

    private val _test = MutableStateFlow<AiTestState>(AiTestState.Idle)
    val test: StateFlow<AiTestState> = _test.asStateFlow()
    private var testJob: Job? = null

    private fun update(transform: (AiSettings) -> AiSettings) {
        testJob?.cancel()
        _test.value = AiTestState.Idle
        store.update(transform)
    }

    fun setProvider(provider: AiProvider) = update { it.copy(provider = provider) }

    fun setApiKey(value: String) = update { it.copy(apiKeys = it.apiKeys + (it.provider to value.trim())) }

    fun setModel(value: String) = update { it.copy(models = it.models + (it.provider to value)) }

    fun setBaseUrl(value: String) = update { it.copy(baseUrl = value) }

    fun setCoachSeesData(value: Boolean) = update { it.copy(coachSeesData = value) }

    fun runTest() {
        testJob?.cancel()
        _test.value = AiTestState.Testing
        testJob = viewModelScope.launch {
            _test.value = try {
                client.test(store.current.config)
                AiTestState.Ok
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                AiTestState.Failed(e.failure, e.detail)
            } catch (e: Exception) {
                AiTestState.Failed(AiFailure.PROVIDER, e.message.orEmpty().take(200))
            }
        }
    }
}
