package com.example.chonline.data.socket

import com.example.chonline.data.local.LastSeenStore
import com.example.chonline.data.local.TokenStore
import com.example.chonline.data.remote.MessageDeletePayload
import com.example.chonline.data.remote.MessageDto
import com.example.chonline.data.remote.MissedMessagesPayload
import com.example.chonline.data.remote.OnlinePayload
import com.example.chonline.data.remote.RoomDeletedPayload
import com.example.chonline.data.remote.RoomPatchPayload
import com.example.chonline.data.remote.SystemPayload
import com.example.chonline.data.remote.createJson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

sealed interface SocketEvent {
    data class Message(val dto: MessageDto) : SocketEvent
    data class MessageEdit(val dto: MessageDto) : SocketEvent
    data class MessageDelete(val roomId: String, val messageId: String) : SocketEvent
    data class RoomPatch(
        val roomId: String,
        val title: String?,
        val hasGroupAvatar: Int?,
        val groupAvatarRev: String?,
    ) : SocketEvent
    data class RoomDeleted(val roomId: String) : SocketEvent
    data class Missed(val roomId: String, val messages: List<MessageDto>) : SocketEvent
    data class System(val roomId: String?, val text: String) : SocketEvent
    data class Online(val payload: OnlinePayload) : SocketEvent
    data class CallInvite(
        val callId: String,
        val roomId: String,
        val fromUserId: String,
        val fromName: String,
        val mode: String,
        val ts: String,
    ) : SocketEvent
    data class CallRinging(val callId: String, val toUserId: String) : SocketEvent
    data class CallAccept(val callId: String, val fromUserId: String) : SocketEvent
    data class CallReject(val callId: String, val fromUserId: String) : SocketEvent
    data class CallMissed(val callId: String) : SocketEvent
    data class CallEnd(val callId: String, val status: String) : SocketEvent
    data class CallOffer(val callId: String, val fromUserId: String, val sdp: String) : SocketEvent
    data class CallAnswer(val callId: String, val fromUserId: String, val sdp: String) : SocketEvent
    data class CallIce(val callId: String, val fromUserId: String, val candidate: String) : SocketEvent
    data object Connected : SocketEvent
    data object Disconnected : SocketEvent
}

/** Снимок offer/ICE, пришедших до подписки CallViewModel на [ChatSocketController.events] (replay=0). */
data class BufferedCallSignaling(
    val offer: SocketEvent.CallOffer?,
    val iceCandidates: List<String>,
)

class ChatSocketController(
    private val baseUrl: String,
    private val tokenStore: TokenStore,
    private val lastSeenStore: LastSeenStore,
) {
    private val json: Json = createJson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private var socket: Socket? = null

    /** call:offer / call:ice могут прийти раньше, чем CallViewModel подпишется на flow. */
    private val bufferedOfferByCallId = ConcurrentHashMap<String, SocketEvent.CallOffer>()
    private val bufferedIceByCallId = ConcurrentHashMap<String, MutableList<String>>()

    private fun bufferCallOffer(event: SocketEvent.CallOffer) {
        bufferedOfferByCallId[event.callId] = event
    }

    private fun bufferCallIce(callId: String, candidateJson: String) {
        if (callId.isBlank() || candidateJson.isBlank()) return
        bufferedIceByCallId.getOrPut(callId) { mutableListOf() }.add(candidateJson)
    }

    private fun clearCallBuffers(callId: String) {
        if (callId.isBlank()) return
        bufferedOfferByCallId.remove(callId)
        bufferedIceByCallId.remove(callId)
    }

    /** Вызывать из CallViewModel при входящем звонке до collect, иначе CallOffer теряется. */
    fun consumeBufferedCallSignaling(callId: String): BufferedCallSignaling {
        if (callId.isBlank()) return BufferedCallSignaling(null, emptyList())
        val offer = bufferedOfferByCallId.remove(callId)
        val ice = bufferedIceByCallId.remove(callId)?.toList().orEmpty()
        return BufferedCallSignaling(offer, ice)
    }

    fun connect() {
        disconnect()
        val token = tokenStore.accessToken() ?: return
        val opts = IO.Options().apply {
            path = "/socket.io/"
            auth = mapOf("token" to token)
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1_000
            reconnectionDelayMax = 10_000
        }
        val uri = URI.create(baseUrl.trimEnd('/'))
        val s = IO.socket(uri, opts)
        socket = s

        s.on(Socket.EVENT_CONNECT) {
            scope.launch { _events.emit(SocketEvent.Connected) }
            emitRoomsSync()
        }
        s.on(Socket.EVENT_DISCONNECT) {
            scope.launch { _events.emit(SocketEvent.Disconnected) }
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { }

        s.on("msg") { args ->
            if (args.isEmpty()) return@on
            val raw = (args[0] as? JSONObject)?.toString() ?: return@on
            val dto = runCatching { json.decodeFromString(MessageDto.serializer(), raw) }.getOrNull()
                ?: return@on
            scope.launch { _events.emit(SocketEvent.Message(dto)) }
        }

        s.on("msg_edit") { args ->
            if (args.isEmpty()) return@on
            val raw = (args[0] as? JSONObject)?.toString() ?: return@on
            val dto = runCatching { json.decodeFromString(MessageDto.serializer(), raw) }.getOrNull()
                ?: return@on
            scope.launch { _events.emit(SocketEvent.MessageEdit(dto)) }
        }

        s.on("msg_delete") { args ->
            if (args.isEmpty()) return@on
            val raw = (args[0] as? JSONObject)?.toString() ?: return@on
            val p = runCatching { json.decodeFromString(MessageDeletePayload.serializer(), raw) }.getOrNull()
                ?: return@on
            scope.launch { _events.emit(SocketEvent.MessageDelete(p.roomId, p.id)) }
        }

        s.on("room_patch") { args ->
            if (args.isEmpty()) return@on
            val raw = (args[0] as? JSONObject)?.toString() ?: return@on
            val p = runCatching { json.decodeFromString(RoomPatchPayload.serializer(), raw) }.getOrNull()
                ?: return@on
            scope.launch {
                _events.emit(
                    SocketEvent.RoomPatch(
                        roomId = p.roomId,
                        title = p.title,
                        hasGroupAvatar = p.hasGroupAvatar,
                        groupAvatarRev = p.groupAvatarRev,
                    ),
                )
            }
        }

        s.on("room_deleted") { args ->
            if (args.isEmpty()) return@on
            val raw = (args[0] as? JSONObject)?.toString() ?: return@on
            val p = runCatching { json.decodeFromString(RoomDeletedPayload.serializer(), raw) }.getOrNull()
                ?: return@on
            scope.launch { _events.emit(SocketEvent.RoomDeleted(p.roomId)) }
        }

        s.on("missed_messages") { args ->
            if (args.isEmpty()) return@on
            val raw = (args[0] as? JSONObject)?.toString() ?: return@on
            val payload = runCatching { json.decodeFromString(MissedMessagesPayload.serializer(), raw) }.getOrNull()
                ?: return@on
            scope.launch {
                _events.emit(SocketEvent.Missed(payload.roomId, payload.messages))
            }
        }

        s.on("system") { args ->
            if (args.isEmpty()) return@on
            val raw = (args[0] as? JSONObject)?.toString() ?: return@on
            val payload = runCatching { json.decodeFromString(SystemPayload.serializer(), raw) }.getOrNull()
                ?: return@on
            scope.launch {
                _events.emit(SocketEvent.System(payload.roomId, payload.text))
            }
        }

        s.on("online") { args ->
            if (args.isEmpty()) return@on
            val raw = (args[0] as? JSONObject)?.toString() ?: return@on
            val payload = runCatching { json.decodeFromString(OnlinePayload.serializer(), raw) }.getOrNull()
                ?: return@on
            scope.launch { _events.emit(SocketEvent.Online(payload)) }
        }

        s.on("call:invite") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch {
                _events.emit(
                    SocketEvent.CallInvite(
                        callId = p.optString("callId"),
                        roomId = p.optString("roomId"),
                        fromUserId = p.optString("fromUserId"),
                        fromName = p.optString("fromName"),
                        mode = p.optString("mode", "audio"),
                        ts = p.optString("ts"),
                    ),
                )
            }
        }
        s.on("call:ringing") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch { _events.emit(SocketEvent.CallRinging(p.optString("callId"), p.optString("toUserId"))) }
        }
        s.on("call:accept") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch { _events.emit(SocketEvent.CallAccept(p.optString("callId"), p.optString("fromUserId"))) }
        }
        s.on("call:reject") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch { _events.emit(SocketEvent.CallReject(p.optString("callId"), p.optString("fromUserId"))) }
        }
        s.on("call:missed") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch { _events.emit(SocketEvent.CallMissed(p.optString("callId"))) }
        }
        s.on("call:end") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            clearCallBuffers(p.optString("callId"))
            scope.launch {
                _events.emit(
                    SocketEvent.CallEnd(
                        callId = p.optString("callId"),
                        status = p.optString("status", "ended"),
                    ),
                )
            }
        }
        s.on("call:offer") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            val event = SocketEvent.CallOffer(
                callId = p.optString("callId"),
                fromUserId = p.optString("fromUserId"),
                sdp = p.optString("sdp"),
            )
            bufferCallOffer(event)
            scope.launch { _events.emit(event) }
        }
        s.on("call:answer") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            scope.launch {
                _events.emit(
                    SocketEvent.CallAnswer(
                        callId = p.optString("callId"),
                        fromUserId = p.optString("fromUserId"),
                        sdp = p.optString("sdp"),
                    ),
                )
            }
        }
        s.on("call:ice") { args ->
            val p = args.firstOrNull() as? JSONObject ?: return@on
            val callId = p.optString("callId")
            val candRaw = p.opt("candidate")
            // JSON null (end-of-candidates) — на Android не передаём в PeerConnection
            if (candRaw == null || candRaw === JSONObject.NULL) return@on
            val candidateStr =
                when (candRaw) {
                    is JSONObject -> candRaw.toString()
                    else -> candRaw?.toString().orEmpty()
                }
            val event = SocketEvent.CallIce(
                callId = callId,
                fromUserId = p.optString("fromUserId"),
                candidate = candidateStr,
            )
            bufferCallIce(callId, candidateStr)
            scope.launch { _events.emit(event) }
        }

        s.connect()
    }

    fun emitRoomsSync() {
        val s = socket ?: return
        val map = lastSeenStore.snapshot()
        val inner = JSONObject()
        map.forEach { (k, v) -> inner.put(k, v) }
        val body = JSONObject().put("lastSeenAt", inner)
        s.emit("rooms:sync", body)
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    fun emitCallInvite(callId: String, toUserId: String, roomId: String, mode: String = "audio") {
        val s = socket ?: return
        val body = JSONObject()
            .put("callId", callId)
            .put("toUserId", toUserId)
            .put("roomId", roomId)
            .put("mode", mode)
        s.emit("call:invite", body)
    }

    fun emitCallAccept(callId: String) {
        val s = socket ?: return
        s.emit("call:accept", JSONObject().put("callId", callId))
    }

    fun emitCallReject(callId: String) {
        val s = socket ?: return
        s.emit("call:reject", JSONObject().put("callId", callId))
    }

    fun emitCallEnd(callId: String) {
        val s = socket ?: return
        s.emit("call:end", JSONObject().put("callId", callId))
    }

    fun emitCallOffer(callId: String, toUserId: String, sdp: String) {
        val s = socket ?: return
        s.emit(
            "call:offer",
            JSONObject().put("callId", callId).put("toUserId", toUserId).put("sdp", sdp),
        )
    }

    fun emitCallAnswer(callId: String, toUserId: String, sdp: String) {
        val s = socket ?: return
        s.emit(
            "call:answer",
            JSONObject().put("callId", callId).put("toUserId", toUserId).put("sdp", sdp),
        )
    }

    fun emitCallIce(callId: String, toUserId: String, candidate: String) {
        val s = socket ?: return
        val parsed = runCatching { JSONObject(candidate) }.getOrNull()
        val body =
            JSONObject()
                .put("callId", callId)
                .put("toUserId", toUserId)
        if (parsed != null) {
            body.put("candidate", parsed)
        } else {
            body.put("candidate", candidate)
        }
        s.emit("call:ice", body)
    }
}
