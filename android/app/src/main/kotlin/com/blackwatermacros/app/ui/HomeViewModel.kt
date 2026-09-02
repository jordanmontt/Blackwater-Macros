package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.ResponseErrorMapper
import com.blackwatermacros.app.data.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val username: String, val isAdmin: Boolean) : HomeUiState
    data class Error(val message: String) : HomeUiState

    /** True once logout finished and the UI should return to login. */
    data object LoggedOut : HomeUiState
}

/**
 * Loads the current session for display and handles logout. Protected calls use
 * the token held by [SessionManager].
 */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val api = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = SessionManager::tokenProvider,
    )

    init {
        load()
    }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val session = api.session()
                _state.value = HomeUiState.Loaded(session.username, session.isAdmin)
            } catch (t: Throwable) {
                _state.value = HomeUiState.Error(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                runCatching { api.logout() }
            } finally {
                SessionManager.clear()
                _state.value = HomeUiState.LoggedOut
            }
        }
    }

    private fun ensureTrailingSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
}