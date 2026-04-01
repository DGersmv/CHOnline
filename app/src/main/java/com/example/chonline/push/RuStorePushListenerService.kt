package com.example.chonline.push

import android.util.Log
import com.example.chonline.call.CallCommand
import com.example.chonline.call.CallCoordinator
import com.example.chonline.call.CallInvite
import com.example.chonline.call.IncomingCallNotifier
import com.example.chonline.di.AppContainer
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
        Log.i(TAG, "RuStore push token updated")
        val container = AppContainer(applicationContext)
        scope.launch {
            container.authRepository.registerPushToken(token)
                .onFailure { e -> Log.e(TAG, "registerPushToken failed in onNewToken: ${e.message}", e) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"].orEmpty()
        Log.i(
            TAG,
            "onMessageReceived type=$type messageId=${message.messageId} appForeground=${AppRuntimeState.isForeground.value}",
        )
        if (type == "call_invite") {
            val invite = CallInvite(
                callId = data["callId"].orEmpty(),
                roomId = data["roomId"].orEmpty(),
                fromUserId = data["fromUserId"].orEmpty(),
                fromName = data["callerName"].orEmpty(),
                mode = data["mode"].orEmpty().ifBlank { "audio" },
                ts = data["ts"].orEmpty(),
            )
            if (invite.callId.isNotBlank()) {
                val foreground = AppRuntimeState.isForeground.value
                val hasActiveCallUi = !AppRuntimeState.activeCallId.value.isNullOrBlank()
                Log.i(
                    TAG,
                    "call_invite callId=${invite.callId} foreground=$foreground hasActiveCallUi=$hasActiveCallUi",
                )
                if (foreground && !hasActiveCallUi) {
                    Log.i(TAG, "call_invite -> submit IncomingInvite to CallCoordinator")
                    CallCoordinator.submit(CallCommand.IncomingInvite(invite))
                } else if (!foreground) {
                    Log.i(TAG, "call_invite -> show incoming call notification")
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
            Log.i(TAG, "show message notification roomId=$roomId notificationId=$notificationId")
            PushMessageNotifier.show(
                context = applicationContext,
                title = title,
                body = body,
                notificationId = notificationId,
                roomId = roomId,
                messageId = messageId,
            )
        } else {
            Log.i(TAG, "suppress message notification for active roomId=$roomId")
        }
        Log.i(TAG, "RuStore push message received: id=${message.messageId}")
    }

    override fun onDeletedMessages() {
        Log.w(TAG, "RuStore push deleted messages callback")
    }

    override fun onError(errors: List<RuStorePushClientException>) {
        errors.forEach { err ->
            Log.e(TAG, "RuStore push error: ${err.message}", err)
        }
    }

    private companion object {
        const val TAG = "RuStorePush"
    }
}

