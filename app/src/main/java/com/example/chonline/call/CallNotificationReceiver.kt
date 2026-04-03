package com.example.chonline.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chonline.CHOnlineApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val invite = CallNotificationParser.readInvite(intent) ?: return
        val action = intent.getStringExtra(CallNotificationParser.EXTRA_ACTION).orEmpty()
        IncomingCallNotifier.cancel(context, invite.callId)
        when (action) {
            CallNotificationParser.ACTION_ACCEPT -> CallCoordinator.submit(CallCommand.Accept(invite))
            CallNotificationParser.ACTION_DECLINE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        (context.applicationContext as CHOnlineApplication).appContainer
                            .chatRepository
                            .rejectCallReliable(invite.callId)
                    }
                    pendingResult.finish()
                }
                CallCoordinator.submit(CallCommand.Decline(invite))
            }
        }
    }
}

