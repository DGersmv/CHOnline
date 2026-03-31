package com.example.chonline.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.chonline.MainActivity
import com.example.chonline.R

object IncomingCallNotifier {
    fun show(context: Context, invite: CallInvite) {
        ensureChannel(context)

        val acceptIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            putExtra(CallNotificationParser.EXTRA_ACTION, CallNotificationParser.ACTION_ACCEPT)
        }
        CallNotificationParser.putInvite(acceptIntent, invite)
        val declineIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            putExtra(CallNotificationParser.EXTRA_ACTION, CallNotificationParser.ACTION_DECLINE)
        }
        CallNotificationParser.putInvite(declineIntent, invite)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        CallNotificationParser.putInvite(contentIntent, invite)

        val acceptPending = PendingIntent.getBroadcast(
            context,
            invite.callId.hashCode(),
            acceptIntent,
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
            .setContentIntent(contentPending)
            .addAction(0, "Отклонить", declinePending)
            .addAction(0, "Ответить", acceptPending)
            .build()

        NotificationManagerCompat.from(context)
            .notify(CallNotificationParser.notificationId(invite.callId), notif)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CallNotificationParser.CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CallNotificationParser.CHANNEL_ID,
            "Входящие звонки",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о входящих звонках"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }
}

