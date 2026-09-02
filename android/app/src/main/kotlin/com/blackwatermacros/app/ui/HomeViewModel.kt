package com.blackwatermacros.app.ui

import android.util.Log
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
    data object LoggedOut : HomeUiState
}

class HomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val api = ApiClient.create(
        baseUrl = ensureTrailingSlash(BuildConfig.API_BASE_URL),
        tokenProvider = SessionManager::tokenProvider,
    )

    init {
        Log.d(TAG, "init: token=${SessionManager.token != null}")
        load()
    }

    fun load() {
        Log.d(TAG, "load() called, current state=${_state.value}")
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                Log.d(TAG, "Calling api.session()...")
                val session = api.session()
                Log.d(TAG, "session() returned: username=${session.username}, isAdmin=${session.isAdmin}")
                _state.value = HomeUiState.Loaded(session.username, session.isAdmin)
                Log.d(TAG, "State set to Loaded")
            } catch (t: Throwable) {
                Log.e(TAG, "session() failed", t)
                _state.value = HomeUiState.Error(ResponseErrorMapper.messageFrom(t))
                Log.d(TAG, "State set to Error: ${_state.value}")
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