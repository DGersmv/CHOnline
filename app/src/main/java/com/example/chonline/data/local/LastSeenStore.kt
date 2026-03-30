package com.example.chonline.data.local

import android.content.Context

/** Локально храним время последнего сообщения по комнате для rooms:sync. */
class LastSeenStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("chonline_last_seen", Context.MODE_PRIVATE)

    fun get(roomId: String): String? = prefs.getString(key(roomId), null)

    fun set(roomId: String, isoTime: String) {
        prefs.edit().putString(key(roomId), isoTime).apply()
    }

    fun snapshot(): Map<String, String> {
        val m = mutableMapOf<String, String>()
        for (e in prefs.all) {
            val k = e.key
            if (k.startsWith(PREFIX)) {
                val roomId = k.removePrefix(PREFIX)
                val v = e.value as? String ?: continue
                m[roomId] = v
            }
        }
        return m
    }

    private fun key(roomId: String) = PREFIX + roomId

    companion object {
        private const val PREFIX = "room_"
    }
}
