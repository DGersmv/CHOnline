package com.example.chonline.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chonline.data.remote.OnlineUserDto
import com.example.chonline.data.remote.RoomDto
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.data.socket.SocketEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomsViewModel(
    private val chat: ChatRepository,
) : ViewModel() {

    private val _rooms = MutableStateFlow<List<RoomDto>>(emptyList())
    val rooms: StateFlow<List<RoomDto>> = _rooms.asStateFlow()

    private val _online = MutableStateFlow<List<OnlineUserDto>>(emptyList())
    val online: StateFlow<List<OnlineUserDto>> = _online.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _socketConnected = MutableStateFlow(false)
    val socketConnected: StateFlow<Boolean> = _socketConnected.asStateFlow()

    init {
        viewModelScope.launch {
            chat.socketEvents.collect { ev ->
                when (ev) {
                    is SocketEvent.Online -> _online.value = ev.payload.users
                    SocketEvent.Connected -> _socketConnected.value = true
                    SocketEvent.Disconnected -> _socketConnected.value = false
                    else -> Unit
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            chat.loadRooms()
                .onSuccess { _rooms.value = it }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
