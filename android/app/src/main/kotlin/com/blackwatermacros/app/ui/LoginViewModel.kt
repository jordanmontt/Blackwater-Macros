package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.data.AccountController
import com.blackwatermacros.app.data.VerifiedLogin
import com.blackwatermacros.app.data.WrongAccountException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** UI state for the login screen. */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState

    /** Credentials are valid and the phone already holds data: upload or discard it? */
    data class AskLocalData(val login: VerifiedLogin) : LoginUiState

    /** The phone is now linked to the account; the first sync is scheduled. */
    data object Connected : LoginUiState

    data class Error(val error: LoginError) : LoginUiState
}

sealed interface LoginError {
    data object MissingFields : LoginError
    data object BadCredentials : LoginError
    data object Network : LoginError

    /** The phone is linked to another account whose session expired. */
    data class WrongAccount(val expected: String) : LoginError
    data class Server(val code: Int) : LoginError
}

/**
 * Connects the optional sync account (opened from Ajustes → Cuenta). Verifies
 * the credentials first; if the phone already has meals/weights/templates the
 * user decides whether to upload them to the account or discard them.
 */
class LoginViewModel(
    private val accounts: AccountController = AppGraph.accounts,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(username: String, password: String) {
        if (_state.value == LoginUiState.Loading) return
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState.Error(LoginError.MissingFields)
            return
        }
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val login = accounts.verify(username, password)
                if (login.isReLogin || login.localData.isEmpty) {
                    connect(login, uploadLocalData = login.isReLogin)
                } else {
                    _state.value = LoginUiState.AskLocalData(login)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LoginUiState.Error(e.toLoginError())
            }
        }
    }

    /** Answer to [LoginUiState.AskLocalData]. */
    fun resolveLocalData(upload: Boolean) {
        val ask = _state.value as? LoginUiState.AskLocalData ?: return
        _state.value = LoginUiState.Loading
        viewModelScope.launch { connect(ask.login, upload) }
    }

    /** Dismissing the question cancels the login; nothing was changed yet. */
    fun cancelLocalDataQuestion() {
        _state.value = LoginUiState.Idle
    }

    private suspend fun connect(login: VerifiedLogin, uploadLocalData: Boolean) {
        accounts.connect(login, uploadLocalData)
        _state.value = LoginUiState.Connected
    }
}

private fun Exception.toLoginError(): LoginError = when (this) {
    is WrongAccountException -> LoginError.WrongAccount(expected)
    is HttpException -> if (code() == 401) LoginError.BadCredentials else LoginError.Server(code())
    is IOException -> LoginError.Network
    else -> LoginError.Server(0)
}
