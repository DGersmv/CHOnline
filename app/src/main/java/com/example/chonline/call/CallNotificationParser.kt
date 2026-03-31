package com.example.chonline.call

import android.content.Intent

object CallNotificationParser {
    const val CHANNEL_ID = "incoming_calls"
    const val EXTRA_ACTION = "call_action"
    const val ACTION_ACCEPT = "accept"
    const val ACTION_DECLINE = "decline"
    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_ROOM_ID = "room_id"
    const val EXTRA_FROM_USER_ID = "from_user_id"
    const val EXTRA_FROM_NAME = "from_name"
    const val EXTRA_MODE = "mode"
    const val EXTRA_TS = "ts"

    fun notificationId(callId: String): Int = callId.hashCode()

    fun putInvite(intent: Intent, invite: CallInvite): Intent = intent.apply {
        putExtra(EXTRA_CALL_ID, invite.callId)
        putExtra(EXTRA_ROOM_ID, invite.roomId)
        putExtra(EXTRA_FROM_USER_ID, invite.fromUserId)
        putExtra(EXTRA_FROM_NAME, invite.fromName)
        putExtra(EXTRA_MODE, invite.mode)
        putExtra(EXTRA_TS, invite.ts)
    }

    fun readInvite(intent: Intent): CallInvite? {
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) return null
        return CallInvite(
            callId = callId,
            roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty(),
            fromUserId = intent.getStringExtra(EXTRA_FROM_USER_ID).orEmpty(),
            fromName = intent.getStringExtra(EXTRA_FROM_NAME).orEmpty(),
            mode = intent.getStringExtra(EXTRA_MODE).orEmpty().ifBlank { "audio" },
            ts = intent.getStringExtra(EXTRA_TS).orEmpty(),
        )
    }
}

