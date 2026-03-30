package com.example.chonline.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.chonline.data.local.LastSeenStore
import com.example.chonline.data.remote.CorpChatApi
import com.example.chonline.data.remote.CreateDmRequest
import com.example.chonline.data.remote.EmployeeDto
import com.example.chonline.data.remote.MessageDto
import com.example.chonline.data.remote.RoomDto
import com.example.chonline.data.remote.SendTextRequest
import com.example.chonline.data.socket.ChatSocketController
import com.example.chonline.data.socket.SocketEvent
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream

class ChatRepository(
    private val api: CorpChatApi,
    private val socket: ChatSocketController,
    private val lastSeenStore: LastSeenStore,
) {

    val socketEvents: Flow<SocketEvent> = socket.events

    fun connectSocket() = socket.connect()

    fun disconnectSocket() = socket.disconnect()

    fun syncRoomsSeen() = socket.emitRoomsSync()

    suspend fun loadRooms(): Result<List<RoomDto>> = runCatching {
        api.rooms().rooms
    }.mapError()

    suspend fun loadEmployees(): Result<List<EmployeeDto>> = runCatching {
        api.employees().employees
    }.mapError()

    suspend fun openDm(peerId: String): Result<RoomDto> = runCatching {
        val r = api.createDm(CreateDmRequest(peerId))
        r.room ?: error(r.error ?: "Не удалось открыть диалог")
    }.mapError()

    suspend fun loadMessages(roomId: String, before: String? = null, limit: Int = 50): Result<Pair<List<MessageDto>, Boolean>> =
        runCatching {
            val r = api.messages(roomId, limit, before)
            if (r.error != null) error(r.error)
            r.messages to r.hasMore
        }.mapError()

    suspend fun sendText(roomId: String, text: String): Result<MessageDto> = runCatching {
        val r = api.sendText(roomId, SendTextRequest(text.trim()))
        if (r.ok != true || r.message == null) error(r.error ?: "Не отправлено")
        r.message
    }.mapError()

    suspend fun sendFile(context: Context, roomId: String, uri: Uri, caption: String? = null): Result<MessageDto> =
        runCatching {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val name = cr.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } ?: "file"

            val tmp = File(context.cacheDir, "upload_${System.currentTimeMillis()}_${name.replace("/", "_")}")
            cr.openInputStream(uri)!!.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            try {
                val body = tmp.asRequestBody(mime.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", name, body)
                val textPlainUtf8 = "text/plain; charset=utf-8".toMediaTypeOrNull()
                val textPart = caption?.takeIf { it.isNotBlank() }?.toRequestBody(textPlainUtf8)
                val namePart = name.toRequestBody(textPlainUtf8)
                val r = api.sendFile(roomId, part, textPart, namePart)
                if (r.ok != true || r.message == null) error(r.error ?: "Файл не отправлен")
                r.message
            } finally {
                tmp.delete()
            }
        }.mapError()

    fun updateLastSeen(roomId: String, message: MessageDto) {
        lastSeenStore.set(roomId, message.time)
    }

    private fun <T> Result<T>.mapError(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { e ->
            val msg = when (e) {
                is HttpException -> e.response()?.errorBody()?.string()?.let { body ->
                    try {
                        org.json.JSONObject(body).optString("error").takeIf { it.isNotBlank() }
                    } catch (_: Exception) {
                        null
                    }
                } ?: e.message

                else -> e.message
            }
            Result.failure(Exception(msg ?: "Ошибка сети"))
        },
    )
}
