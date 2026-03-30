package com.example.chonline.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chonline.data.repo.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    private var loginTypeJob: Job? = null

    fun onLoginInputChanged(raw: String) {
        loginTypeJob?.cancel()
        val login = raw.trim().lowercase()
        if (login.length < 3) {
            _ui.value = _ui.value.copy(loginKind = LoginKind.Unknown, loginTypeLoading = false)
            return
        }
        loginTypeJob = viewModelScope.launch {
            delay(650)
            _ui.value = _ui.value.copy(loginTypeLoading = true)
            auth.loginType(login)
                .onSuccess { t ->
                    _ui.value = _ui.value.copy(
                        loginTypeLoading = false,
                        loginKind = if (t == "client") LoginKind.Client else LoginKind.Employee,
                    )
                }
                .onFailure {
                    _ui.value = _ui.value.copy(loginTypeLoading = false, loginKind = LoginKind.Unknown)
                }
        }
    }

    fun sendCode(email: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            auth.sendCode(email)
                .onSuccess {
                    _ui.value = _ui.value.copy(loading = false, codeSentFor = email.trim().lowercase())
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun loginAsClient(login: String, password: String) {
        if (password.isBlank()) {
            _ui.value = _ui.value.copy(error = "Введите пароль")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            auth.clientLogin(login, password)
                .onSuccess {
                    _ui.value = _ui.value.copy(loading = false, loggedIn = true)
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun verify(email: String, code: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            auth.verify(email, code)
                .onSuccess {
                    _ui.value = _ui.value.copy(loading = false, loggedIn = true)
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun consumeLoggedIn() {
        _ui.value = _ui.value.copy(loggedIn = false)
    }

    fun consumeCodeSent() {
        _ui.value = _ui.value.copy(codeSentFor = null)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }
}

enum class LoginKind {
    Unknown,
    Client,
    Employee,
}

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val codeSentFor: String? = null,
    val loggedIn: Boolean = false,
    val loginKind: LoginKind = LoginKind.Unknown,
    val loginTypeLoading: Boolean = false,
)
