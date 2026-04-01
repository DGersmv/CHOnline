package com.example.chonline.push

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

object PushMessageNotifier {
    private const val CHANNEL_ID = "messages"
    const val ACTION_OPEN_MESSAGE = "open_message"
    const val EXTRA_ROOM_ID = "room_id"
    const val EXTRA_MESSAGE_ID = "message_id"

    fun show(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
        roomId: String?,
        messageId: String?,
    ) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = ACTION_OPEN_MESSAGE
            putExtra(EXTRA_ROOM_ID, roomId.orEmpty())
            putExtra(EXTRA_MESSAGE_ID, messageId.orEmpty())
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notif)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Сообщения",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомления о новых сообщениях"
        }
        nm.createNotificationChannel(channel)
    }
}
