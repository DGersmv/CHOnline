package com.example.chonline.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chonline.data.repo.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            auth.loadMe()
                .onSuccess { me ->
                    _ui.value = _ui.value.copy(
                        loading = false,
                        name = me.name,
                        phone = me.phone,
                    )
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun save(name: String, phone: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            auth.updateProfile(name, phone)
                .onSuccess {
                    _ui.value = _ui.value.copy(loading = false, saved = true)
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun consumeSaved() {
        _ui.value = _ui.value.copy(saved = false)
    }
}

data class ProfileUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val name: String = "",
    val phone: String = "",
    val saved: Boolean = false,
)
