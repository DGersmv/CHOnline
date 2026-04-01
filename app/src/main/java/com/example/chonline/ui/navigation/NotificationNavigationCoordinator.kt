package com.example.chonline.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class OpenChatCommand(
    val roomId: String,
    val messageId: String? = null,
)

object NotificationNavigationCoordinator {
    private val _openChat = MutableSharedFlow<OpenChatCommand>(extraBufferCapacity = 16)
    val openChat = _openChat.asSharedFlow()
    @Volatile
    private var pendingOpenChat: OpenChatCommand? = null

    fun submitOpenChat(command: OpenChatCommand) {
        if (command.roomId.isBlank()) return
        pendingOpenChat = command
        _openChat.tryEmit(command)
    }

    fun consumePendingOpenChat(): OpenChatCommand? {
        val p = pendingOpenChat
        pendingOpenChat = null
        return p
    }
}
