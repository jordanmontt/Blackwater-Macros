package com.blackwatermacros.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.data.AdminCreateUserRequest
import com.blackwatermacros.app.data.AdminUpdateUserRequest
import com.blackwatermacros.app.data.AdminUserDTO
import com.blackwatermacros.app.data.ApiService
import com.blackwatermacros.app.data.ResponseErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminUiState {
    data object Loading : AdminUiState
    data class Loaded(val users: List<AdminUserDTO>, val currentUsername: String) : AdminUiState
    data class Error(val message: String) : AdminUiState
}

class AdminViewModel : ViewModel() {

    private val _state = MutableStateFlow<AdminUiState>(AdminUiState.Loading)
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    /** Admin is online-only by nature; it talks to the server directly. */
    private val api: ApiService = AppGraph.api

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AdminUiState.Loading
            try {
                val session = api.session()
                val users = api.listUsers().users
                _state.value = AdminUiState.Loaded(users = users, currentUsername = session.username)
            } catch (t: Throwable) {
                _state.value = AdminUiState.Error(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    fun create(username: String, password: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                api.createUser(AdminCreateUserRequest(username = username, password = password))
                load()
                onDone(null)
            } catch (t: Throwable) {
                onDone(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    fun update(
        user: AdminUserDTO,
        username: String,
        password: String?,
        isAdmin: Boolean?,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                api.updateUser(user.id, AdminUpdateUserRequest(
                    username = username,
                    password = password?.takeIf { it.isNotEmpty() },
                    isAdmin = isAdmin,
                ))
                load()
                onDone(null)
            } catch (t: Throwable) {
                onDone(ResponseErrorMapper.messageFrom(t))
            }
        }
    }

    fun delete(id: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                api.deleteUser(id)
                load()
                onDone(null)
            } catch (t: Throwable) {
                onDone(ResponseErrorMapper.messageFrom(t))
            }
        }
    }
}