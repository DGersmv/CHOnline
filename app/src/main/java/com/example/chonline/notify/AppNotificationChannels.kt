package com.example.chonline.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.chonline.call.CallNotificationParser

/**
 * Каналы должны существовать до первого [android.app.Notification], иначе на части прошивок
 * приложение не появляется в «Настройки → Уведомления» до первого показа.
 */
object AppNotificationChannels {
    const val MESSAGES_CHANNEL_ID = "messages"

    fun registerAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        ensureIncomingCalls(context)
        ensureMessages(context)
    }

    private fun ensureIncomingCalls(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CallNotificationParser.CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CallNotificationParser.CHANNEL_ID,
            "Входящие звонки",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о входящих звонках"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setBypassDnd(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 450, 350)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(ch)
    }

    private fun ensureMessages(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(MESSAGES_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            MESSAGES_CHANNEL_ID,
            "Сообщения",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомления о новых сообщениях"
        }
        nm.createNotificationChannel(channel)
    }
}
