package com.example.chonline.ui.rooms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chonline.data.remote.EmployeeDto
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.theme.CorpChatColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupCreateViewModel(
    private val chat: ChatRepository,
) : ViewModel() {

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _employees = MutableStateFlow<List<EmployeeDto>>(emptyList())
    val employees: StateFlow<List<EmployeeDto>> = _employees.asStateFlow()

    private val _selectedStaff = MutableStateFlow<Set<String>>(emptySet())
    val selectedStaff: StateFlow<Set<String>> = _selectedStaff.asStateFlow()

    private val _selectedClients = MutableStateFlow<Set<String>>(emptySet())
    val selectedClients: StateFlow<Set<String>> = _selectedClients.asStateFlow()

    init {
        load()
    }

    fun setTitle(t: String) {
        _title.value = t
    }

    fun toggleStaff(id: String) {
        _selectedStaff.value = _selectedStaff.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun toggleClient(id: String) {
        _selectedClients.value = _selectedClients.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            chat.loadEmployees()
                .onSuccess { _employees.value = it }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun create(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            val t = _title.value.trim()
            if (t.isEmpty() || t.length > 80) {
                _error.value = "Название: 1–80 символов"
                _saving.value = false
                return@launch
            }
            chat.createGroup(t, _selectedStaff.value.toList(), _selectedClients.value.toList())
                .onSuccess { room ->
                    _saving.value = false
                    onCreated(room.id)
                }
                .onFailure {
                    _error.value = it.message
                    _saving.value = false
                }
        }
    }

    companion object {
        fun factory(chat: ChatRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GroupCreateViewModel(chat) as T
                }
            }
    }
}

private fun displayName(em: EmployeeDto): String {
    val n = em.name.trim()
    if (n.isNotBlank()) return n
    val an = em.adminName.trim()
    if (an.isNotBlank()) return an
    val email = (em.email ?: em.accountEmail).orEmpty().trim()
    if (email.isNotBlank()) return email
    return em.id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val vm: GroupCreateViewModel = viewModel(
        factory = GroupCreateViewModel.factory(container.chatRepository),
    )
    val loading by vm.loading.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val employees by vm.employees.collectAsStateWithLifecycle()
    val selStaff by vm.selectedStaff.collectAsStateWithLifecycle()
    val selClients by vm.selectedClients.collectAsStateWithLifecycle()

    val staff = remember(employees) { employees.filter { !it.isClient } }
    val clients = remember(employees) { employees.filter { it.isClient } }

    Scaffold(
        containerColor = CorpChatColors.bgDeep,
        topBar = {
            TopAppBar(
                title = { Text("Новая группа") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !saving) { Text("Назад") }
                },
                actions = {
                    TextButton(
                        onClick = { vm.create(onCreated) },
                        enabled = !saving && !loading,
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                        } else {
                            Text("Создать")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CorpChatColors.bgPanel,
                    titleContentColor = CorpChatColors.textPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = vm::setTitle,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !saving,
            )
            Spacer(Modifier.height(12.dp))
            Text("Участники", style = MaterialTheme.typography.labelLarge)
            if (loading) {
                CircularProgressIndicator(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(24.dp),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(staff, key = { it.id }) { em ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selStaff.contains(em.id),
                                onCheckedChange = { vm.toggleStaff(em.id) },
                                enabled = !saving,
                            )
                            Text(displayName(em))
                        }
                    }
                    items(clients, key = { it.id }) { em ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selClients.contains(em.id),
                                onCheckedChange = { vm.toggleClient(em.id) },
                                enabled = !saving,
                            )
                            Text(displayName(em) + " (заказчик)")
                        }
                    }
                }
            }
            err?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
