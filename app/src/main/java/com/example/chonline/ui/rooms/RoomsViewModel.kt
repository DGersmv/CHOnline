package com.example.chonline.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chonline.data.remote.OnlineUserDto
import com.example.chonline.data.remote.EmployeeDto
import com.example.chonline.data.remote.RoomDto
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.data.socket.SocketEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomsViewModel(
    private val chat: ChatRepository,
) : ViewModel() {

    private val _rooms = MutableStateFlow<List<RoomDto>>(emptyList())
    val rooms: StateFlow<List<RoomDto>> = _rooms.asStateFlow()

    private val _employees = MutableStateFlow<List<EmployeeDto>>(emptyList())
    val employees: StateFlow<List<EmployeeDto>> = _employees.asStateFlow()

    private val _online = MutableStateFlow<List<OnlineUserDto>>(emptyList())
    val online: StateFlow<List<OnlineUserDto>> = _online.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _socketConnected = MutableStateFlow(false)
    val socketConnected: StateFlow<Boolean> = _socketConnected.asStateFlow()

    private var debouncedReload: Job? = null

    init {
        // При старте из push/экрана звонка событие Connected могло пройти до подписки VM.
        // Синхронизируем индикатор с фактическим состоянием сокета, чтобы не залипать в "Нет связи".
        _socketConnected.value = chat.isSocketConnected()
        viewModelScope.launch {
            chat.socketEvents.collect { ev ->
                when (ev) {
                    is SocketEvent.Online -> _online.value = ev.payload.users
                    SocketEvent.Connected -> _socketConnected.value = true
                    SocketEvent.Disconnected -> _socketConnected.value = false
                    is SocketEvent.RoomPatch -> patchRoom(ev)
                    is SocketEvent.RoomDeleted -> removeRoom(ev.roomId)
                    is SocketEvent.Message,
                    is SocketEvent.MessageEdit,
                    is SocketEvent.MessageDelete,
                    -> scheduleReloadRooms()
                    else -> Unit
                }
            }
        }
        load()
    }

    private fun patchRoom(ev: SocketEvent.RoomPatch) {
        _rooms.value = _rooms.value.map { r ->
            if (r.id != ev.roomId) r
            else {
                r.copy(
                    title = ev.title ?: r.title,
                    hasGroupAvatar = ev.hasGroupAvatar ?: r.hasGroupAvatar,
                    groupAvatarRev = ev.groupAvatarRev ?: r.groupAvatarRev,
                )
            }
        }
    }

    private fun removeRoom(roomId: String) {
        _rooms.value = _rooms.value.filter { it.id != roomId }
    }

    private fun scheduleReloadRooms() {
        debouncedReload?.cancel()
        debouncedReload = viewModelScope.launch {
            delay(600)
            chat.loadRooms()
                .onSuccess { _rooms.value = it }
                .onFailure { /* keep list */ }
        }
    }

    fun load() {
        viewModelScope.launch {
            _socketConnected.value = chat.isSocketConnected()
            _loading.value = true
            _error.value = null
            chat.loadRooms()
                .onSuccess { _rooms.value = it }
                .onFailure { _error.value = it.message }
            chat.loadEmployees()
                .onSuccess { _employees.value = it }
            _loading.value = false
        }
    }

    fun refresh() = load()

    fun clearError() {
        _error.value = null
    }
}
