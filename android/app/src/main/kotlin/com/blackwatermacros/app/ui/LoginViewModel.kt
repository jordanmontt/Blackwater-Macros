package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.data.ApiClient
import com.blackwatermacros.app.data.LoginRequest
import com.blackwatermacros.app.data.ResponseErrorMapper
import com.blackwatermacros.app.data.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the login screen. */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState

    /** Logged in and `/auth/session` confirmed. */
    data class Success(val username: String, val isAdmin: Boolean) : LoginUiState

    /** Login failed; `message` is the Spanish, user-facing error. */
    data class Error(val message: String) : LoginUiState
}

/**
 * Drives the login flow against the deployed backend: `POST /api/auth/login`
 * (stores the token in [SessionManager]) then `GET /api/auth/session` (confirms
 * the user). Token persistence and offline mode arrive with the planned Room/sync
 * work.
 */
class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val api = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = SessionManager::tokenProvider,
    )

    fun login(username: String, password: String) {
        if (_state.value == LoginUiState.Loading) return
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState.Error("Introduce usuario y contraseña")
            return
        }
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val login = api.login(LoginRequest(username.trim(), password))
                SessionManager.updateToken(login.token)
                val session = api.session()
                _state.value = LoginUiState.Success(session.username, session.isAdmin)
            } catch (t: Throwable) {
                _state.value = LoginUiState.Error(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    fun retry() {
        _state.value = LoginUiState.Idle
    }

    private fun ensureTrailingSlash(base: String): String =
        if (base.endsWith("/")) base else "$base/"
}