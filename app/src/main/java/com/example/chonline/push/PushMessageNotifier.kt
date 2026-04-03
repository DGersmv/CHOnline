package com.example.chonline.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.chonline.MainActivity
import com.example.chonline.R
import com.example.chonline.notify.AppNotificationChannels

object PushMessageNotifier {
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
        AppNotificationChannels.registerAll(context)
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
        val notif = NotificationCompat.Builder(context, AppNotificationChannels.MESSAGES_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notif)
    }
}
