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
    data object Connected : SocketEvent
    data object Disconnected : SocketEvent
}

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
}
