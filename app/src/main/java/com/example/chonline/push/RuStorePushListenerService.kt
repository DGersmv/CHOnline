package com.example.chonline.push

import android.util.Log
import com.example.chonline.call.CallCommand
import com.example.chonline.call.CallCoordinator
import com.example.chonline.call.CallInvite
import com.example.chonline.call.IncomingCallNotifier
import com.example.chonline.di.AppContainer
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
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"].orEmpty()
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
                CallCoordinator.submit(CallCommand.IncomingInvite(invite))
                IncomingCallNotifier.show(applicationContext, invite)
            }
            return
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

