package com.example.chonline.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chonline.data.remote.AdminClientDto
import com.example.chonline.data.remote.AdminEmployeeRowDto
import com.example.chonline.data.repo.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminClientsUiState(
    val loading: Boolean = false,
    val clients: List<AdminClientDto> = emptyList(),
    val error: String? = null,
)

data class UsersPickerState(
    val clientId: String,
    val loginHint: String,
    val employees: List<AdminEmployeeRowDto> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

class AdminClientsViewModel(
    private val repo: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminClientsUiState())
    val state: StateFlow<AdminClientsUiState> = _state.asStateFlow()

    private val _usersPicker = MutableStateFlow<UsersPickerState?>(null)
    val usersPicker: StateFlow<UsersPickerState?> = _usersPicker.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.listClients()
                .onSuccess { list ->
                    _state.value = AdminClientsUiState(loading = false, clients = list, error = null)
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun createClient(login: String, password: String, name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repo.createClient(login, password, name)
                .onSuccess {
                    refresh()
                    onDone()
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun toggleActive(client: AdminClientDto) {
        val active = (client.isActive ?: 0) != 0
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            repo.setActive(client.id, active = !active)
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun deleteClient(clientId: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            repo.deleteClient(clientId)
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun setPassword(clientId: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            repo.setPassword(clientId, password)
                .onSuccess {
                    refresh()
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun openUsersPicker(clientId: String, loginHint: String) {
        _usersPicker.value = UsersPickerState(clientId = clientId, loginHint = loginHint, loading = true)
        viewModelScope.launch {
            val emRes = repo.listEmployees()
            val visRes = repo.listVisibleUsers(clientId)
            if (emRes.isFailure) {
                _usersPicker.value = UsersPickerState(
                    clientId = clientId,
                    loginHint = loginHint,
                    loading = false,
                    error = emRes.exceptionOrNull()?.message ?: "Не удалось загрузить сотрудников",
                )
                return@launch
            }
            if (visRes.isFailure) {
                _usersPicker.value = UsersPickerState(
                    clientId = clientId,
                    loginHint = loginHint,
                    loading = false,
                    error = visRes.exceptionOrNull()?.message ?: "Ошибка",
                )
                return@launch
            }
            val all = emRes.getOrThrow()
            val sel = visRes.getOrThrow().map { it.id }.toSet()
            _usersPicker.value = UsersPickerState(
                clientId = clientId,
                loginHint = loginHint,
                employees = all.sortedWith(compareBy { adminEmployeeSortKey(it) }),
                selectedIds = sel,
                loading = false,
                error = null,
            )
        }
    }

    fun togglePickerUser(userId: String) {
        _usersPicker.update { st ->
            st?.copy(
                selectedIds = st.selectedIds.toMutableSet().apply {
                    if (contains(userId)) remove(userId) else add(userId)
                },
            )
        }
    }

    fun saveUsersPicker(onDone: () -> Unit) {
        val st = _usersPicker.value ?: return
        viewModelScope.launch {
            _usersPicker.update { it?.copy(saving = true, error = null) }
            repo.replaceVisibleUsers(st.clientId, st.selectedIds.toList())
                .onSuccess {
                    _usersPicker.value = null
                    refresh()
                    onDone()
                }
                .onFailure { e ->
                    _usersPicker.update { it?.copy(saving = false, error = e.message) }
                }
        }
    }

    fun dismissUsersPicker() {
        _usersPicker.value = null
    }

    companion object {
        fun factory(repo: AdminRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdminClientsViewModel(repo) as T
                }
            }
    }
}

private fun adminEmployeeSortKey(e: AdminEmployeeRowDto): String {
    val n = e.name.trim().ifBlank { e.adminName.trim() }
    return n.ifBlank { (e.email ?: e.accountEmail).orEmpty() }
}
