package com.example.chonline.call

import android.content.Intent
import org.json.JSONObject

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
    /** JSON как у сервера `invitePayload` — если отдельные extras обрезаны доставщиком. */
    const val EXTRA_INVITE_PAYLOAD = "invite_payload"

    fun notificationId(callId: String): Int = callId.hashCode()

    fun putInvite(intent: Intent, invite: CallInvite): Intent = intent.apply {
        putExtra(EXTRA_CALL_ID, invite.callId)
        putExtra(EXTRA_ROOM_ID, invite.roomId)
        putExtra(EXTRA_FROM_USER_ID, invite.fromUserId)
        putExtra(EXTRA_FROM_NAME, invite.fromName)
        putExtra(EXTRA_MODE, invite.mode)
        putExtra(EXTRA_TS, invite.ts)
        putExtra(
            EXTRA_INVITE_PAYLOAD,
            JSONObject().apply {
                put("callId", invite.callId)
                put("roomId", invite.roomId)
                put("fromUserId", invite.fromUserId)
                put("callerName", invite.fromName)
                put("mode", invite.mode)
                put("ts", invite.ts)
            }.toString(),
        )
    }

    fun readInvite(intent: Intent): CallInvite? {
        val parsed = parseInviteJson(intent.getStringExtra(EXTRA_INVITE_PAYLOAD).orEmpty())
        val callId =
            intent.getStringExtra(EXTRA_CALL_ID).orEmpty().ifBlank { parsed?.callId.orEmpty() }
        if (callId.isBlank()) return null
        val discrete = CallInvite(
            callId = callId,
            roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty(),
            fromUserId = intent.getStringExtra(EXTRA_FROM_USER_ID).orEmpty(),
            fromName = intent.getStringExtra(EXTRA_FROM_NAME).orEmpty(),
            mode = intent.getStringExtra(EXTRA_MODE).orEmpty().ifBlank { "audio" },
            ts = intent.getStringExtra(EXTRA_TS).orEmpty(),
        )
        if (parsed != null && parsed.callId.isNotBlank()) {
            return parsed.copy(
                callId = callId,
                roomId = parsed.roomId.ifBlank { discrete.roomId },
                fromUserId = parsed.fromUserId.ifBlank { discrete.fromUserId },
                fromName = parsed.fromName.ifBlank { discrete.fromName },
                mode = parsed.mode.ifBlank { discrete.mode },
                ts = parsed.ts.ifBlank { discrete.ts },
            )
        }
        return discrete
    }

    /** Ru Store data map: сначала `invitePayload`, иначе плоские ключи. */
    fun inviteFromRuStoreData(data: Map<String, String>): CallInvite {
        val parsed =
            parseInviteJson(
                data["invitePayload"].orEmpty().ifBlank { data["invite_payload"].orEmpty() },
            )
        val discrete =
            CallInvite(
                callId = data["callId"].orEmpty(),
                roomId = data["roomId"].orEmpty(),
                fromUserId =
                    data["fromUserId"].orEmpty().ifBlank { data["from_user_id"].orEmpty() },
                fromName =
                    data["callerName"].orEmpty().ifBlank { data["caller_name"].orEmpty() },
                mode = data["mode"].orEmpty().ifBlank { "audio" },
                ts = data["ts"].orEmpty(),
            )
        if (parsed != null && parsed.callId.isNotBlank()) {
            return parsed.copy(
                roomId = parsed.roomId.ifBlank { discrete.roomId },
                fromUserId = parsed.fromUserId.ifBlank { discrete.fromUserId },
                fromName = parsed.fromName.ifBlank { discrete.fromName },
                mode = parsed.mode.ifBlank { discrete.mode },
                ts = parsed.ts.ifBlank { discrete.ts },
            )
        }
        return discrete
    }

    private fun parseInviteJson(raw: String): CallInvite? {
        if (raw.isBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            val callId = o.optString("callId")
            if (callId.isBlank()) return@runCatching null
            CallInvite(
                callId = callId,
                roomId = o.optString("roomId"),
                fromUserId =
                    o.optString("fromUserId").ifBlank { o.optString("from_user_id") },
                fromName =
                    o.optString("callerName").ifBlank { o.optString("fromName") },
                mode = o.optString("mode").ifBlank { "audio" },
                ts = o.optString("ts"),
            )
        }.getOrNull()
    }

    /** После обработки — чтобы onResume/повторный compose не дублировали навигацию. */
    fun removeInviteExtras(intent: Intent?) {
        if (intent == null) return
        intent.removeExtra(EXTRA_CALL_ID)
        intent.removeExtra(EXTRA_ROOM_ID)
        intent.removeExtra(EXTRA_FROM_USER_ID)
        intent.removeExtra(EXTRA_FROM_NAME)
        intent.removeExtra(EXTRA_MODE)
        intent.removeExtra(EXTRA_TS)
        intent.removeExtra(EXTRA_INVITE_PAYLOAD)
        intent.removeExtra(EXTRA_ACTION)
    }
}

