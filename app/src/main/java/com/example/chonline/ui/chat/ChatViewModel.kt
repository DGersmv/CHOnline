package com.example.chonline.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chonline.data.local.TokenStore
import com.example.chonline.data.remote.MessageDto
import com.example.chonline.data.remote.RoomDto
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.data.socket.SocketEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** null — нет загрузки файла; 0f..1f — прогресс multipart. */
    private val _fileUploadProgress = MutableStateFlow<Float?>(null)
    val fileUploadProgress: StateFlow<Float?> = _fileUploadProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _room = MutableStateFlow<RoomDto?>(null)
    val room: StateFlow<RoomDto?> = _room.asStateFlow()

    /** Комната удалена или пользователь вышел — закрыть экран чата */
    private val _roomClosed = MutableStateFlow(false)
    val roomClosed: StateFlow<Boolean> = _roomClosed.asStateFlow()

    val myUserId: String? get() = tokenStore.session.value?.userId

    init {
        viewModelScope.launch {
            chatRepository.socketEvents.collect { handleSocket(it) }
        }
        refreshRoomMeta()
        loadInitial()
    }

    fun refreshRoomMeta() {
        viewModelScope.launch {
            chatRepository.loadRooms()
                .onSuccess { list ->
                    _room.value = list.find { it.id == roomId }
                }
        }
    }

    fun isMyMessage(msg: MessageDto): Boolean {
        val s = tokenStore.session.value ?: return false
        return if (tokenStore.isClient()) {
            msg.fromClientId != null && msg.fromClientId == s.userId
        } else {
            msg.userId == s.userId && msg.fromClientId.isNullOrBlank()
        }
    }

    private fun handleSocket(ev: SocketEvent) {
        when (ev) {
            is SocketEvent.Message -> {
                if (ev.dto.roomId != roomId) return
                appendMessages(listOf(ev.dto))
                chatRepository.updateLastSeen(roomId, ev.dto)
            }

            is SocketEvent.MessageEdit -> {
                if (ev.dto.roomId != roomId) return
                applyEdit(ev.dto)
            }

            is SocketEvent.MessageDelete -> {
                if (ev.roomId != roomId) return
                removeById(ev.messageId)
            }

            is SocketEvent.Missed -> {
                if (ev.roomId != roomId) return
                appendMessages(ev.messages)
                ev.messages.maxByOrNull { it.time }?.let {
                    chatRepository.updateLastSeen(roomId, it)
                }
            }

            is SocketEvent.RoomPatch -> {
                if (ev.roomId != roomId) return
                _room.update { cur ->
                    val base = cur ?: return@update null
                    base.copy(
                        title = ev.title ?: base.title,
                        hasGroupAvatar = ev.hasGroupAvatar ?: base.hasGroupAvatar,
                        groupAvatarRev = ev.groupAvatarRev ?: base.groupAvatarRev,
                    )
                }
            }

            is SocketEvent.RoomDeleted -> {
                if (ev.roomId != roomId) return
                _roomClosed.value = true
            }

            else -> Unit
        }
    }

    private fun applyEdit(updated: MessageDto) {
        val list = _messages.value.map { if (it.id == updated.id) updated else it }
        _messages.value = list.sortedBy { it.time }
    }

    private fun removeById(id: String) {
        _messages.value = _messages.value.filter { it.id != id }
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

    fun editMessage(messageId: String, text: String, allowBlank: Boolean = false) {
        if (!allowBlank && text.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            chatRepository.editMessage(roomId, messageId, text)
                .onSuccess { applyEdit(it) }
                .onFailure { _error.value = it.message }
            _sending.value = false
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            chatRepository.deleteMessage(roomId, messageId)
                .onSuccess { removeById(messageId) }
                .onFailure { _error.value = it.message }
            _sending.value = false
        }
    }

    fun sendFileWithProgress(context: android.content.Context, uri: android.net.Uri, caption: String?) {
        sendFilesWithProgress(context, listOf(uri), caption)
    }

    /** Несколько файлов подряд; подпись только к первому. Общий прогресс 0…1. */
    fun sendFilesWithProgress(context: android.content.Context, uris: List<android.net.Uri>, captionForFirst: String?) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _fileUploadProgress.value = 0f
            _error.value = null
            val n = uris.size
            for ((idx, uri) in uris.withIndex()) {
                val cap = if (idx == 0) captionForFirst else null
                chatRepository.sendFileWithProgress(context, roomId, uri, cap) { p ->
                    val base = idx.toFloat() / n
                    val part = (1f / n) * p.coerceIn(0f, 1f)
                    _fileUploadProgress.value = (base + part).coerceIn(0f, 1f)
                }.fold(
                    onSuccess = { msg ->
                        appendMessages(listOf(msg))
                        chatRepository.updateLastSeen(roomId, msg)
                    },
                    onFailure = {
                        _error.value = it.message
                        _fileUploadProgress.value = null
                        return@launch
                    },
                )
            }
            _fileUploadProgress.value = null
        }
    }

    fun clearError() {
        _error.value = null
    }
}
