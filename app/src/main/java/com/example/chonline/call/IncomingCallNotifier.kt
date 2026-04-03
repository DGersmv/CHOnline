package com.example.chonline.call

import android.app.PendingIntent
import android.media.RingtoneManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.chonline.MainActivity
import com.example.chonline.R
import com.example.chonline.notify.AppNotificationChannels

object IncomingCallNotifier {
    fun show(context: Context, invite: CallInvite) {
        AppNotificationChannels.registerAll(context)

        val declineIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            putExtra(CallNotificationParser.EXTRA_ACTION, CallNotificationParser.ACTION_DECLINE)
        }
        CallNotificationParser.putInvite(declineIntent, invite)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        CallNotificationParser.putInvite(contentIntent, invite)

        val acceptActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallNotificationParser.EXTRA_ACTION, CallNotificationParser.ACTION_ACCEPT)
        }
        CallNotificationParser.putInvite(acceptActivityIntent, invite)
        val acceptPending = PendingIntent.getActivity(
            context,
            invite.callId.hashCode(),
            acceptActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val declinePending = PendingIntent.getBroadcast(
            context,
            invite.callId.hashCode() + 1,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentPending = PendingIntent.getActivity(
            context,
            invite.callId.hashCode() + 2,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = invite.fromName.ifBlank { "Входящий звонок" }
        val body = if (invite.mode == "video") "Видеозвонок" else "Аудиозвонок"
        val notif = NotificationCompat.Builder(context, CallNotificationParser.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setFullScreenIntent(contentPending, true)
            .setContentIntent(contentPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отклонить", declinePending)
            .addAction(android.R.drawable.sym_action_call, "Ответить", acceptPending)
            .build()

        NotificationManagerCompat.from(context)
            .notify(CallNotificationParser.notificationId(invite.callId), notif)
    }

    fun cancel(context: Context, callId: String) {
        if (callId.isBlank()) return
        NotificationManagerCompat.from(context).cancel(CallNotificationParser.notificationId(callId))
    }
}

