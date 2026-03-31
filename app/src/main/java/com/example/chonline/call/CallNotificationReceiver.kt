package com.example.chonline.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class CallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val invite = CallNotificationParser.readInvite(intent) ?: return
        val action = intent.getStringExtra(CallNotificationParser.EXTRA_ACTION).orEmpty()
        NotificationManagerCompat.from(context).cancel(CallNotificationParser.notificationId(invite.callId))
        when (action) {
            CallNotificationParser.ACTION_ACCEPT -> CallCoordinator.submit(CallCommand.Accept(invite))
            CallNotificationParser.ACTION_DECLINE -> CallCoordinator.submit(CallCommand.Decline(invite))
        }
    }
}

