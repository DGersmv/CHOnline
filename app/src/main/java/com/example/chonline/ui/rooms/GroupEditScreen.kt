package com.example.chonline.ui.rooms

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chonline.BuildConfig
import com.example.chonline.data.remote.EmployeeDto
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.theme.CorpChatColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class GroupEditViewModel(
    private val roomId: String,
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

    private val _hasServerPhoto = MutableStateFlow(false)
    val hasServerPhoto: StateFlow<Boolean> = _hasServerPhoto.asStateFlow()

    private val _groupAvatarRev = MutableStateFlow<String?>(null)
    val groupAvatarRev: StateFlow<String?> = _groupAvatarRev.asStateFlow()

    private val _pendingRemovePhoto = MutableStateFlow(false)
    val pendingRemovePhoto: StateFlow<Boolean> = _pendingRemovePhoto.asStateFlow()

    private val _pickedUri = MutableStateFlow<Uri?>(null)
    val pickedUri: StateFlow<Uri?> = _pickedUri.asStateFlow()

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

    fun setPickedUri(uri: Uri?) {
        _pickedUri.value = uri
        if (uri != null) _pendingRemovePhoto.value = false
    }

    fun markRemovePhoto() {
        _pendingRemovePhoto.value = true
        _pickedUri.value = null
    }

    private fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            chat.loadEmployees()
                .onSuccess { _employees.value = it }
                .onFailure { _error.value = it.message }
            chat.getGroupEdit(roomId)
                .onSuccess { ge ->
                    _title.value = ge.room.title
                    _selectedStaff.value = ge.memberIds.toSet()
                    _selectedClients.value = ge.clientIds.toSet()
                    _hasServerPhoto.value = (ge.room.hasGroupAvatar ?: 0) != 0
                    _groupAvatarRev.value = ge.room.groupAvatarRev
                }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun save(context: android.content.Context, onDone: () -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            val t = _title.value.trim()
            if (t.isEmpty() || t.length > 80) {
                _error.value = "Название: 1–80 символов"
                _saving.value = false
                return@launch
            }
            val staff = _selectedStaff.value
            val clients = _selectedClients.value
            if (_pendingRemovePhoto.value) {
                chat.deleteGroupAvatar(roomId)
                    .onSuccess {
                        _hasServerPhoto.value = (it.hasGroupAvatar ?: 0) != 0
                        _groupAvatarRev.value = it.groupAvatarRev
                    }
                    .onFailure { e ->
                        _error.value = e.message
                        _saving.value = false
                        return@launch
                    }
            }
            val uri = _pickedUri.value
            if (uri != null) {
                chat.uploadGroupAvatar(context, roomId, uri)
                    .onSuccess {
                        _hasServerPhoto.value = (it.hasGroupAvatar ?: 0) != 0
                        _groupAvatarRev.value = it.groupAvatarRev
                        _pickedUri.value = null
                    }
                    .onFailure { e ->
                        _error.value = e.message
                        _saving.value = false
                        return@launch
                    }
            }
            chat.patchGroup(roomId, t, staff.toList(), clients.toList())
                .onSuccess {
                    _saving.value = false
                    onDone()
                }
                .onFailure {
                    _error.value = it.message
                    _saving.value = false
                }
        }
    }

    companion object {
        fun factory(roomId: String, chat: ChatRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GroupEditViewModel(roomId, chat) as T
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
fun GroupEditScreen(
    roomId: String,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: GroupEditViewModel = viewModel(
        key = roomId,
        factory = GroupEditViewModel.factory(roomId, container.chatRepository),
    )
    val loading by vm.loading.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val employees by vm.employees.collectAsStateWithLifecycle()
    val selStaff by vm.selectedStaff.collectAsStateWithLifecycle()
    val selClients by vm.selectedClients.collectAsStateWithLifecycle()
    val hasServerPhoto by vm.hasServerPhoto.collectAsStateWithLifecycle()
    val pendingRemove by vm.pendingRemovePhoto.collectAsStateWithLifecycle()
    val pickedUri by vm.pickedUri.collectAsStateWithLifecycle()
    val avatarRev by vm.groupAvatarRev.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    val imageLoader = remember(container.okHttpForImages) {
        ImageLoader.Builder(context)
            .okHttpClient(container.okHttpForImages)
            .build()
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        vm.setPickedUri(uri)
    }

    val staff = remember(employees) { employees.filter { !it.isClient } }
    val clients = remember(employees) { employees.filter { it.isClient } }

    val serverPhotoUrl = remember(roomId, hasServerPhoto, pendingRemove, avatarRev, base) {
        if (!hasServerPhoto || pendingRemove) {
            null
        } else {
            val enc = URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
            val rev = avatarRev?.takeIf { it.isNotBlank() }?.let {
                "?rev=" + URLEncoder.encode(it, StandardCharsets.UTF_8.name())
            }.orEmpty()
            "$base/api/v1/rooms/$enc/avatar$rev"
        }
    }

    Scaffold(
        containerColor = CorpChatColors.bgDeep,
        topBar = {
            TopAppBar(
                title = { Text("Группа") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !saving) { Text("Назад") }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save(context) { onBack() } },
                        enabled = !saving && !loading,
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                        } else {
                            Text("Сохранить")
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    pickedUri != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(pickedUri).crossfade(true).build(),
                            contentDescription = null,
                            imageLoader = imageLoader,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    serverPhotoUrl != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(serverPhotoUrl).crossfade(true).build(),
                            contentDescription = null,
                            imageLoader = imageLoader,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    else -> {
                        Text(
                            title.take(2).uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
                Column {
                    Button(onClick = { pick.launch("image/*") }, enabled = !saving) { Text("Фото") }
                    if (hasServerPhoto || pickedUri != null) {
                        TextButton(onClick = { vm.markRemovePhoto() }, enabled = !saving) {
                            Text("Убрать фото")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = vm::setTitle,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !saving,
            )
            Spacer(Modifier.height(8.dp))
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
