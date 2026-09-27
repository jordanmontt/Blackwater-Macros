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

sealed interface AiTestState {
    data object Idle : AiTestState
    data object Testing : AiTestState
    data object Ok : AiTestState
    data class Failed(val failure: AiFailure) : AiTestState
}

@StringRes
fun AiFailure.messageRes(): Int = when (this) {
    AiFailure.NOT_CONFIGURED -> R.string.ai_error_not_configured
    AiFailure.INVALID_KEY -> R.string.ai_error_invalid_key
    AiFailure.QUOTA -> R.string.ai_error_quota
    AiFailure.NOT_FOUND -> R.string.ai_error_not_found
    AiFailure.OFFLINE -> R.string.ai_error_offline
    AiFailure.EMPTY -> R.string.ai_error_empty
    AiFailure.PROVIDER -> R.string.ai_error_provider
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
) : ViewModel() {

    val settings: StateFlow<AiSettings> = store.settings

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
                AiTestState.Failed(e.failure)
            } catch (e: Exception) {
                AiTestState.Failed(AiFailure.PROVIDER)
            }
        }
    }
}
