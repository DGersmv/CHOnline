package com.example.chonline.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppRuntimeState {
    private val _isForeground = MutableStateFlow(false)
    val isForeground = _isForeground.asStateFlow()

    private val _activeChatRoomId = MutableStateFlow<String?>(null)
    val activeChatRoomId = _activeChatRoomId.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    val activeCallId = _activeCallId.asStateFlow()

    fun setForeground(value: Boolean) {
        _isForeground.value = value
    }

    fun setActiveChat(roomId: String?) {
        _activeChatRoomId.value = roomId?.takeIf { it.isNotBlank() }
    }

    fun setActiveCall(callId: String?) {
        _activeCallId.value = callId?.takeIf { it.isNotBlank() }
    }
}
