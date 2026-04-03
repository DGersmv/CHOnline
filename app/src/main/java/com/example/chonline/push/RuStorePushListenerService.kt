package com.example.chonline.push

import com.example.chonline.call.CallCommand
import com.example.chonline.call.CallCoordinator
import com.example.chonline.call.CallNotificationParser
import com.example.chonline.call.IncomingCallNotifier
import com.example.chonline.CHOnlineApplication
import com.example.chonline.ui.navigation.AppRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService
import ru.rustore.sdk.pushclient.messaging.exception.RuStorePushClientException

class RuStorePushListenerService : RuStoreMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        val container = (applicationContext as CHOnlineApplication).appContainer
        scope.launch {
            container.authRepository.registerPushToken(token).fold(
                onSuccess = { },
                onFailure = { },
            )
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"].orEmpty()
        if (type == "call_invite") {
            val invite = CallNotificationParser.inviteFromRuStoreData(data)
            if (invite.callId.isNotBlank()) {
                val foreground = AppRuntimeState.isForeground.value
                val hasActiveCallUi = !AppRuntimeState.activeCallId.value.isNullOrBlank()
                if (foreground && !hasActiveCallUi) {
                    CallCoordinator.submit(CallCommand.IncomingInvite(invite))
                } else {
                    IncomingCallNotifier.show(applicationContext, invite)
                }
            }
            return
        }
        val title =
            data["title"].orEmpty()
                .ifBlank { data["senderName"].orEmpty() }
                .ifBlank { data["fromName"].orEmpty() }
                .ifBlank { "Новое сообщение" }
        val body =
            data["body"].orEmpty()
                .ifBlank { data["text"].orEmpty() }
                .ifBlank { data["message"].orEmpty() }
                .ifBlank { "Откройте чат, чтобы прочитать" }
        val notificationId = message.messageId?.hashCode() ?: (System.currentTimeMillis() and 0xFFFFFF).toInt()
        val roomId =
            data["roomId"].orEmpty()
                .ifBlank { data["chatId"].orEmpty() }
                .ifBlank { data["conversationId"].orEmpty() }
                .ifBlank { null }
        val messageId =
            data["messageId"].orEmpty()
                .ifBlank { data["id"].orEmpty() }
                .ifBlank { null }
        val activeChatRoomId = AppRuntimeState.activeChatRoomId.value
        val suppressForegroundChatNotification =
            AppRuntimeState.isForeground.value &&
                !roomId.isNullOrBlank() &&
                roomId == activeChatRoomId
        if (!suppressForegroundChatNotification) {
            PushMessageNotifier.show(
                context = applicationContext,
                title = title,
                body = body,
                notificationId = notificationId,
                roomId = roomId,
                messageId = messageId,
            )
        }
    }

    override fun onDeletedMessages() = Unit

    override fun onError(errors: List<RuStorePushClientException>) = Unit
}
