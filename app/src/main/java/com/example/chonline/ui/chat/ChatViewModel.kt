package com.example.chonline.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chonline.data.local.TokenStore
import com.example.chonline.data.remote.MessageDto
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.data.socket.SocketEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    val roomId: String,
    private val chatRepository: ChatRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val myUserId: String? get() = tokenStore.session.value?.userId

    init {
        viewModelScope.launch {
            chatRepository.socketEvents.collect { handleSocket(it) }
        }
        loadInitial()
    }

    private fun handleSocket(ev: SocketEvent) {
        when (ev) {
            is SocketEvent.Message -> {
                if (ev.dto.roomId != roomId) return
                appendMessages(listOf(ev.dto))
                chatRepository.updateLastSeen(roomId, ev.dto)
            }

            is SocketEvent.Missed -> {
                if (ev.roomId != roomId) return
                appendMessages(ev.messages)
                ev.messages.maxByOrNull { it.time }?.let {
                    chatRepository.updateLastSeen(roomId, it)
                }
            }

            else -> Unit
        }
    }

    private fun appendMessages(incoming: List<MessageDto>) {
        if (incoming.isEmpty()) return
        val byId = _messages.value.associateBy { it.id }.toMutableMap()
        incoming.forEach { byId[it.id] = it }
        _messages.value = byId.values.sortedBy { it.time }
    }

    fun loadInitial() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            chatRepository.loadMessages(roomId)
                .onSuccess { (list, more) ->
                    _messages.value = list.sortedBy { it.time }
                    _hasMore.value = more
                    list.maxByOrNull { it.time }?.let { chatRepository.updateLastSeen(roomId, it) }
                }
                .onFailure { _error.value = it.message }
            _loading.value = false
            chatRepository.syncRoomsSeen()
        }
    }

    fun loadOlder() {
        val oldest = _messages.value.minByOrNull { it.time } ?: return
        if (!_hasMore.value) return
        viewModelScope.launch {
            chatRepository.loadMessages(roomId, before = oldest.id)
                .onSuccess { (older, more) ->
                    _hasMore.value = more
                    val merged = (older + _messages.value).distinctBy { it.id }.sortedBy { it.time }
                    _messages.value = merged
                }
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            chatRepository.sendText(roomId, text)
                .onSuccess { msg ->
                    appendMessages(listOf(msg))
                    chatRepository.updateLastSeen(roomId, msg)
                }
                .onFailure { _error.value = it.message }
            _sending.value = false
        }
    }

    fun sendFile(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            chatRepository.sendFile(context, roomId, uri)
                .onSuccess { msg ->
                    appendMessages(listOf(msg))
                    chatRepository.updateLastSeen(roomId, msg)
                }
                .onFailure { _error.value = it.message }
            _sending.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
