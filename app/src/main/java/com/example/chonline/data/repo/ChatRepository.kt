package com.example.chonline.data.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.chonline.R
import com.example.chonline.BuildConfig
import com.example.chonline.data.local.LastSeenStore
import com.example.chonline.data.local.TokenStore
import com.example.chonline.data.remote.CorpChatApi
import com.example.chonline.data.remote.CountingFileRequestBody
import com.example.chonline.data.remote.CreateDmRequest
import com.example.chonline.data.remote.CreateGroupRequest
import com.example.chonline.data.remote.OpenClientRequest
import com.example.chonline.data.remote.EmployeeDto
import com.example.chonline.data.remote.GroupEditResponse
import com.example.chonline.data.remote.MessageDto
import com.example.chonline.data.remote.PatchRoomRequest
import com.example.chonline.data.remote.RoomDto
import com.example.chonline.data.remote.SendMessageResponse
import com.example.chonline.data.remote.SendTextRequest
import com.example.chonline.data.remote.FileAttachmentDto
import com.example.chonline.data.remote.VoiceCallEntryDto
import com.example.chonline.data.socket.BufferedCallSignaling
import com.example.chonline.data.socket.ChatSocketController
import com.example.chonline.data.socket.SocketEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class ChatRepository(
    private val api: CorpChatApi,
    private val socket: ChatSocketController,
    private val lastSeenStore: LastSeenStore,
    private val tokenStore: TokenStore,
    private val okHttp: OkHttpClient,
    private val json: Json,
) {

    val socketEvents: Flow<SocketEvent> = socket.events

    private fun isClient(): Boolean = tokenStore.isClient()

    fun connectSocket() = socket.ensureConnected()

    fun isSocketConnected(): Boolean = socket.isConnected()

    fun disconnectSocket() = socket.disconnect()

    fun syncRoomsSeen() = socket.emitRoomsSync()

    fun startCall(toUserId: String, roomId: String, mode: String = "audio"): String {
        val callId = UUID.randomUUID().toString()
        socket.emitCallInvite(callId = callId, toUserId = toUserId, roomId = roomId, mode = mode)
        return callId
    }

    fun acceptCall(callId: String, toUserId: String? = null) = socket.emitCallAccept(callId, toUserId)

    fun rejectCall(callId: String) = socket.emitCallReject(callId)

    suspend fun rejectCallReliable(callId: String) {
        if (callId.isBlank()) return
        if (!socket.isConnected()) {
            socket.ensureConnected()
            repeat(10) {
                if (socket.isConnected()) return@repeat
                delay(150)
            }
        }
        socket.emitCallReject(callId)
    }

    suspend fun awaitSocketConnected(timeoutMs: Long = 15000): Boolean {
        if (isSocketConnected()) return true
        connectSocket()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isSocketConnected()) return true
            delay(50)
        }
        return isSocketConnected()
    }

    /**
     * Полный payload call:invite с сервера (push из Ru Store часто без roomId / fromUserId).
     * [peekCallInvite] ловит invite, пришедший до suspend-ожидания.
     */
    suspend fun awaitCallInviteForCallId(callId: String, timeoutMs: Long = 1500): SocketEvent.CallInvite? {
        if (callId.isBlank()) return null
        socket.peekCallInvite(callId)?.let { return it }
        return withTimeoutOrNull(timeoutMs) {
            socketEvents.first { ev ->
                ev is SocketEvent.CallInvite && (ev as SocketEvent.CallInvite).callId == callId
            } as SocketEvent.CallInvite
        }
    }

    fun endCall(callId: String) = socket.emitCallEnd(callId)

    fun sendCallOffer(callId: String, toUserId: String, sdp: String) =
        socket.emitCallOffer(callId, toUserId, sdp)

    fun sendCallAnswer(callId: String, toUserId: String, sdp: String) =
        socket.emitCallAnswer(callId, toUserId, sdp)

    fun sendCallIce(callId: String, toUserId: String, candidate: String) =
        socket.emitCallIce(callId, toUserId, candidate)

    /** Call:offer / ICE могут прийти до подписки CallViewModel — снимок из сокета. */
    fun consumeBufferedCallSignaling(callId: String): BufferedCallSignaling =
        socket.consumeBufferedCallSignaling(callId)

    suspend fun loadRooms(): Result<List<RoomDto>> = runCatching {
        if (isClient()) api.clientRooms().rooms else api.rooms().rooms
    }.mapError()

    suspend fun loadCallsHistory(): Result<List<VoiceCallEntryDto>> = runCatching {
        if (isClient()) emptyList()
        else api.calls().calls
    }.mapError()

    suspend fun loadEmployees(): Result<List<EmployeeDto>> = runCatching {
        if (isClient()) {
            api.clientEmployees().map { c ->
                EmployeeDto(
                    id = c.id,
                    name = c.name,
                    phone = c.phone.orEmpty(),
                    email = c.email,
                    accountEmail = null,
                    isClient = false,
                )
            }
        } else {
            val r = api.employees()
            val staff = r.employees.map { e -> e.copy(isClient = false) }
            val clients = r.clients.map { c -> c.copy(isClient = true) }
            staff + clients
        }
    }.mapError()

    /**
     * @param isClientContact только для сотрудника: [peerId] — id заказчика из `client_users`, не `users`.
     */
    suspend fun openDm(peerId: String, isClientContact: Boolean = false): Result<RoomDto> = runCatching {
        val r = when {
            isClient() -> api.clientOpenPeer(CreateDmRequest(peerId))
            isClientContact -> api.openClient(OpenClientRequest(clientId = peerId))
            else -> api.createDm(CreateDmRequest(peerId))
        }
        r.room ?: error(r.error ?: "Не удалось открыть диалог")
    }.mapError()

    suspend fun loadMessages(roomId: String, before: String? = null, limit: Int = 50): Result<Pair<List<MessageDto>, Boolean>> =
        runCatching {
            val r = if (isClient()) {
                api.clientMessages(roomId, limit, before)
            } else {
                api.messages(roomId, limit, before)
            }
            if (r.error != null) error(r.error)
            r.messages to r.hasMore
        }.mapError()

    suspend fun sendText(roomId: String, text: String): Result<MessageDto> = runCatching {
        val r = if (isClient()) {
            api.clientSendText(roomId, SendTextRequest(text.trim()))
        } else {
            api.sendText(roomId, SendTextRequest(text.trim()))
        }
        if (r.ok != true || r.message == null) error(r.error ?: "Не отправлено")
        r.message
    }.mapError()

    suspend fun editMessage(roomId: String, messageId: String, text: String): Result<MessageDto> = runCatching {
        val body = SendTextRequest(text.trim())
        val r = if (isClient()) {
            api.clientEditMessage(roomId, messageId, body)
        } else {
            api.editMessage(roomId, messageId, body)
        }
        if (r.ok != true || r.message == null) error(r.error ?: "Не сохранено")
        r.message
    }.mapError()

    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> = runCatching {
        val r = if (isClient()) {
            api.clientDeleteMessage(roomId, messageId)
        } else {
            api.deleteMessage(roomId, messageId)
        }
        if (r.ok != true) error(r.error ?: "Не удалено")
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
                val r = if (isClient()) {
                    api.clientSendFile(roomId, part, textPart, namePart)
                } else {
                    api.sendFile(roomId, part, textPart, namePart)
                }
                if (r.ok != true || r.message == null) error(r.error ?: "Файл не отправлен")
                r.message
            } finally {
                tmp.delete()
            }
        }.mapError()

    /**
     * Загрузка файла с прогрессом (OkHttp multipart, как на вебе).
     */
    suspend fun sendFileWithProgress(
        context: Context,
        roomId: String,
        uri: Uri,
        caption: String?,
        onProgress: (Float) -> Unit,
    ): Result<MessageDto> = withContext(Dispatchers.IO) {
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
                val fileBody = CountingFileRequestBody(tmp, mime.toMediaTypeOrNull()) { done, total ->
                    val p = if (total > 0) done.toFloat() / total else 0f
                    onProgress(p)
                }
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", name, fileBody)
                    .apply {
                        caption?.takeIf { it.isNotBlank() }?.let { addFormDataPart("text", it) }
                    }
                    .addFormDataPart("originalFilename", name)
                    .build()

                val enc = URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
                val path = if (isClient()) {
                    "client/rooms/$enc/messages/file"
                } else {
                    "rooms/$enc/messages/file"
                }
                val url = "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/v1/$path"
                val request = Request.Builder().url(url).post(multipartBody).build()
                okHttp.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val err = runCatching { org.json.JSONObject(bodyStr).optString("error") }.getOrNull().orEmpty()
                        error(err.ifBlank { "HTTP ${response.code}" })
                    }
                    val r = json.decodeFromString(SendMessageResponse.serializer(), bodyStr)
                    if (r.ok != true || r.message == null) error(r.error ?: "Файл не отправлен")
                    r.message
                }
            } finally {
                tmp.delete()
            }
        }.mapError()
    }

    suspend fun createGroup(
        title: String,
        memberIds: List<String>,
        clientIds: List<String>,
    ): Result<RoomDto> = runCatching {
        if (isClient()) error("Недоступно")
        val r = api.createGroup(
            CreateGroupRequest(
                title = title.trim(),
                memberIds = memberIds,
                clientIds = clientIds,
            ),
        )
        r.room ?: error(r.error ?: "Не удалось создать группу")
    }.mapError()

    suspend fun getGroupEdit(roomId: String): Result<GroupEditResponse> = runCatching {
        if (isClient()) error("Недоступно")
        api.groupEdit(roomId)
    }.mapError()

    suspend fun patchGroup(
        roomId: String,
        title: String,
        memberIds: List<String>,
        clientIds: List<String>,
    ): Result<RoomDto> = runCatching {
        if (isClient()) error("Недоступно")
        val r = api.patchRoom(
            roomId,
            PatchRoomRequest(title = title, memberIds = memberIds, clientIds = clientIds),
        )
        if (r.ok != true || r.room == null) error(r.error ?: "Не сохранено")
        r.room
    }.mapError()

    suspend fun uploadGroupAvatar(context: Context, roomId: String, uri: Uri): Result<RoomDto> =
        runCatching {
            if (isClient()) error("Недоступно")
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "image/jpeg"
            val name = cr.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } ?: "photo.jpg"
            val tmp = File(context.cacheDir, "grp_av_${System.currentTimeMillis()}_${name.replace("/", "_")}")
            cr.openInputStream(uri)!!.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            try {
                val body = tmp.asRequestBody(mime.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("photo", name, body)
                val r = api.uploadRoomAvatar(roomId, part)
                if (r.ok != true || r.room == null) error(r.error ?: "Фото не сохранено")
                r.room
            } finally {
                tmp.delete()
            }
        }.mapError()

    suspend fun deleteGroupAvatar(roomId: String): Result<RoomDto> = runCatching {
        if (isClient()) error("Недоступно")
        val r = api.deleteRoomAvatar(roomId)
        if (r.ok != true || r.room == null) error(r.error ?: "Не удалено")
        r.room
    }.mapError()

    suspend fun leaveGroup(roomId: String): Result<Unit> = runCatching {
        if (isClient()) error("Недоступно")
        val r = api.leaveRoom(roomId)
        if (r.ok != true) error(r.error ?: "Не удалось выйти из группы")
    }.mapError()

    /** Удалить комнату (группу — только создатель; личный чат — участник). */
    suspend fun deleteRoomChat(roomId: String): Result<Unit> = runCatching {
        if (isClient()) error("Недоступно")
        val r = api.deleteRoom(roomId)
        if (r.ok != true) error(r.error ?: "Не удалось удалить чат")
    }.mapError()

    fun updateLastSeen(roomId: String, message: MessageDto) {
        lastSeenStore.set(roomId, message.time)
    }

    /**
     * Кэш по [messageId]: один файл на сообщение. Повторный вызов не тянет сеть,
     * если локальный файл есть и размер совпадает с [FileAttachmentDto.size] (если сервер прислал size > 0).
     */
    suspend fun ensureChatAttachmentInCache(
        context: Context,
        baseUrl: String,
        messageId: String,
        file: FileAttachmentDto,
        isClient: Boolean,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dest = attachmentCacheFile(context, messageId, file)
            if (isAttachmentCacheValid(dest, file)) {
                return@runCatching dest
            }
            if (dest.exists()) dest.delete()
            val url = buildAttachmentUrl(baseUrl, file, isClient)
            val req = Request.Builder().url(url).build()
            val resp = okHttp.newCall(req).execute()
            if (!resp.isSuccessful) error("Не удалось скачать файл (${resp.code})")
            val body = resp.body ?: error("Пустой ответ")
            dest.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            if (!isAttachmentCacheValid(dest, file)) {
                dest.delete()
                error("Файл повреждён или размер не совпадает с сервером")
            }
            dest
        }
    }

    /**
     * Копирует уже скачанный в кэш файл в общие «Загрузки» (Android 10+) или в папку загрузок приложения.
     * @return Короткая подсказка для пользователя, куда положили файл.
     */
    suspend fun saveChatAttachmentToDownloads(
        context: Context,
        baseUrl: String,
        messageId: String,
        file: FileAttachmentDto,
        isClient: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val local = ensureChatAttachmentInCache(
                context,
                baseUrl,
                messageId,
                file,
                isClient,
            ).getOrThrow()
            val displayName = file.name.ifBlank { "file" }.replace(Regex("[\\\\/]+"), "_")
            val mime = file.mime.ifBlank { "application/octet-stream" }
            copyLocalFileToDownloads(context, local, displayName, mime).getOrThrow()
        }
    }

    private fun attachmentCacheFile(context: Context, messageId: String, file: FileAttachmentDto): File {
        val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val rawName = file.name.ifBlank { "file" }
        val safe = rawName.replace("\\", "_").replace("/", "_").take(180).ifBlank { "file" }
        val safeId = messageId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
        return File(dir, "${safeId}_$safe")
    }

    private fun isAttachmentCacheValid(cached: File, dto: FileAttachmentDto): Boolean {
        if (!cached.isFile || !cached.exists()) return false
        if (cached.length() == 0L) return false
        if (dto.size > 0 && cached.length() != dto.size) return false
        return true
    }

    private fun buildAttachmentUrl(baseUrl: String, file: FileAttachmentDto, isClient: Boolean): String {
        val pathOrUrl = if (isClient) file.clientUrl ?: file.url else file.url
        return if (pathOrUrl.startsWith("http://", true) || pathOrUrl.startsWith("https://", true)) {
            pathOrUrl
        } else {
            baseUrl.trimEnd('/') + "/" + pathOrUrl.trimStart('/')
        }
    }

    /** Подпапка в «Загрузках» = имя приложения ([R.string.app_name]), безопасное для пути. */
    private fun downloadsSubfolderName(context: Context): String {
        val raw = context.getString(R.string.app_name)
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "CHOnline" }
    }

    private fun copyLocalFileToDownloads(
        context: Context,
        sourceFile: File,
        displayName: String,
        mime: String,
    ): Result<String> = runCatching {
        val resolver = context.contentResolver
        val mimeType = mime.ifBlank { "application/octet-stream" }
        val uniqueName = "${System.currentTimeMillis()}_$displayName"
        val sub = downloadsSubfolderName(context)
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/$sub"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values) ?: error("Не удалось создать запись в Загрузках")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { it.copyTo(out) }
                } ?: error("Не удалось записать файл")
                val clear = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, clear, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            "Загрузки → $sub"
        } else {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: error("Нет доступа к памяти")
            val dir = File(base, sub).apply { mkdirs() }
            val dest = File(dir, uniqueName)
            sourceFile.copyTo(dest, overwrite = true)
            "Загрузки приложения → $sub"
        }
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
