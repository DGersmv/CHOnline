package com.example.chonline.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chonline.BuildConfig
import com.example.chonline.data.remote.CallInfoDto
import com.example.chonline.data.remote.FileAttachmentDto
import com.example.chonline.data.remote.MessageDto
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.theme.CorpChatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    container: AppContainer,
    onBack: () -> Unit,
    /** Создатель: «Изменить»; участник: «Группа» (выйти). */
    onEditGroup: (() -> Unit)? = null,
    onOpenGroupAsMember: (() -> Unit)? = null,
    onStartCall: ((peerId: String, peerName: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(
        key = roomId,
        factory = ChatViewModelFactory(roomId, container.chatRepository, container.tokenStore),
    )
    val messages by vm.messages.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val fileUploadProgress by vm.fileUploadProgress.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    val room by vm.room.collectAsStateWithLifecycle()
    val roomClosed by vm.roomClosed.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val imageLoader = rememberCoilWithAuth(container)
    val isClient = container.tokenStore.isClient()

    var editTarget by remember { mutableStateOf<MessageDto?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    var pendingFileUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var fileCaption by remember { mutableStateOf("") }
    var wasFileUploading by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingFileUris = uris
            fileCaption = ""
        }
    }

    LaunchedEffect(fileUploadProgress) {
        if (fileUploadProgress != null) wasFileUploading = true
        if (wasFileUploading && fileUploadProgress == null) {
            pendingFileUris = emptyList()
            fileCaption = ""
            wasFileUploading = false
        }
    }

    LaunchedEffect(roomId) {
        vm.refreshRoomMeta()
    }

    LaunchedEffect(roomClosed) {
        if (roomClosed) onBack()
    }

    val lastId = messages.lastOrNull()?.id
    // reverseLayout: новые сообщения у нижнего края (над полем ввода), а не «вверху пустого экрана»
    LaunchedEffect(lastId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        containerColor = CorpChatColors.bgDeep,
        topBar = {
            val barTitle = room?.title?.takeIf { it.isNotBlank() } ?: roomTitle(roomId)
            val showGroupEdit = onEditGroup != null &&
                !isClient &&
                room?.type == "group" &&
                room?.createdBy != null &&
                vm.myUserId != null &&
                room?.createdBy == vm.myUserId
            val showGroupMember = onOpenGroupAsMember != null &&
                !isClient &&
                room?.type == "group" &&
                room?.createdBy != null &&
                vm.myUserId != null &&
                room?.createdBy != vm.myUserId
            TopAppBar(
                title = { Text(barTitle) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    val canCall = !isClient && room?.type == "dm" && !room?.dmPeerUserId.isNullOrBlank()
                    if (canCall) {
                        IconButton(
                            onClick = {
                                val peer = room?.dmPeerUserId ?: return@IconButton
                                onStartCall?.invoke(peer, barTitle)
                            },
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Позвонить")
                        }
                    }
                    if (showGroupEdit) {
                        TextButton(onClick = { onEditGroup?.invoke() }) {
                            Text("Изменить")
                        }
                    }
                    if (showGroupMember) {
                        TextButton(onClick = { onOpenGroupAsMember?.invoke() }) {
                            Text("Группа")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CorpChatColors.bgPanel,
                    titleContentColor = CorpChatColors.textPrimary,
                    navigationIconContentColor = CorpChatColors.accent,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (loading && messages.isEmpty()) {
                CircularProgressIndicator(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(24.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Порядок: сначала новые (index 0 у нижнего края при reverseLayout)
                items(messages.asReversed(), key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        isMine = vm.isMyMessage(msg),
                        myUserId = vm.myUserId,
                        isClient = isClient,
                        baseUrl = BuildConfig.API_BASE_URL.trimEnd('/'),
                        imageLoader = imageLoader,
                        onEdit = {
                            if (msg.msgType == "text" || msg.msgType == "file") {
                                editTarget = msg
                                editText = msg.text
                            }
                        },
                        onDelete = { deleteTargetId = msg.id },
                    )
                }
                if (hasMore && messages.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = { vm.loadOlder() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Загрузить раньше") }
                    }
                }
            }
            err?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { pickFiles.launch(arrayOf("*/*")) },
                    enabled = !sending && fileUploadProgress == null,
                ) { Text("Файл") }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    placeholder = { Text("Сообщение") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!sending && input.isNotBlank()) {
                                vm.sendText(input)
                                input = ""
                            }
                        },
                    ),
                )
                Button(
                    onClick = {
                        vm.sendText(input)
                        input = ""
                    },
                    enabled = !sending && fileUploadProgress == null && input.isNotBlank(),
                ) {
                    if (sending) CircularProgressIndicator(Modifier.padding(4.dp))
                    else Text("Отпр.")
                }
            }
        }
    }

    editTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = {
                Text(
                    if (target.msgType == "file") "Подпись к файлу" else "Изменить сообщение",
                )
            },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.editMessage(target.id, editText, allowBlank = target.msgType == "file")
                        editTarget = null
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Отмена") }
            },
        )
    }

    if (pendingFileUris.isNotEmpty()) {
        val cr = context.contentResolver
        val firstUri = pendingFileUris.first()
        val single = pendingFileUris.size == 1
        val mime = remember(firstUri) { cr.getType(firstUri) ?: "application/octet-stream" }
        val displayName = remember(firstUri) {
            cr.query(firstUri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use "файл"
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } ?: "файл"
        }
        val isImage = single && mime.startsWith("image/")
        val uploading = fileUploadProgress != null
        AlertDialog(
            onDismissRequest = { if (!uploading) pendingFileUris = emptyList() },
            title = {
                Text(
                    if (single) "Отправить файл" else "Отправить файлов: ${pendingFileUris.size}",
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isImage) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(firstUri).crossfade(true).build(),
                            contentDescription = null,
                            imageLoader = imageLoader,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else if (single) {
                        Text(displayName, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Column(
                            Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            pendingFileUris.forEachIndexed { i, uri ->
                                key(uri) {
                                    val name = remember(uri) {
                                        cr.query(uri, null, null, null, null)?.use { c ->
                                            if (!c.moveToFirst()) return@use "файл"
                                            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                            if (idx >= 0) c.getString(idx) else null
                                        } ?: "файл"
                                    }
                                    Text(
                                        "${i + 1}. $name",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CorpChatColors.textPrimary,
                                    )
                                }
                            }
                        }
                    }
                    if (!single) {
                        Text(
                            "Подпись будет добавлена только к первому файлу.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CorpChatColors.textMuted,
                        )
                    }
                    OutlinedTextField(
                        value = fileCaption,
                        onValueChange = { fileCaption = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        enabled = !uploading,
                        placeholder = {
                            Text(if (single) "Подпись (необязательно)" else "Подпись к первому файлу (необязательно)")
                        },
                    )
                    if (uploading) {
                        val p = fileUploadProgress ?: 0f
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.sendFilesWithProgress(
                            context,
                            pendingFileUris,
                            fileCaption.trim().takeIf { it.isNotEmpty() },
                        )
                    },
                    enabled = !uploading,
                ) { Text("Отправить") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingFileUris = emptyList() },
                    enabled = !uploading,
                ) { Text("Отмена") }
            },
        )
    }

    deleteTargetId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("Удалить сообщение?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteMessage(id)
                        deleteTargetId = null
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("Отмена") }
            },
        )
    }
}

private fun formatCallLine(info: CallInfoDto?, viewerId: String?): String {
    if (info == null || viewerId.isNullOrBlank()) return "Звонок"
    if (info.callerId.isBlank() || info.calleeId.isBlank()) return "Звонок"
    val incoming = info.calleeId == viewerId
    val dir = if (incoming) "Входящий" else "Исходящий"
    val st = when (info.status) {
        "ended" -> "Состоялся"
        "declined" -> "Отклонён"
        "missed" -> "Пропущен"
        "cancelled" -> "Отменён"
        "failed" -> "Сбой"
        else -> info.status.ifBlank { "—" }
    }
    val d = info.durationSec?.takeIf { it > 0 }?.let { s ->
        val mm = s / 60
        val ss = s % 60
        " · $mm:${ss.toString().padStart(2, '0')}"
    }.orEmpty()
    return "$dir звонок · $st$d"
}

private fun roomTitle(roomId: String): String = when {
    roomId == "general" -> "Общий чат"
    roomId.startsWith("dm:") -> "Диалог"
    else -> roomId.take(24)
}

@Composable
private fun rememberCoilWithAuth(container: AppContainer): ImageLoader {
    val ctx = LocalContext.current
    return androidx.compose.runtime.remember(container.okHttpForImages) {
        ImageLoader.Builder(ctx)
            .okHttpClient(container.okHttpForImages)
            .build()
    }
}

@Composable
private fun MessageBubble(
    msg: MessageDto,
    isMine: Boolean,
    myUserId: String?,
    isClient: Boolean,
    baseUrl: String,
    imageLoader: ImageLoader,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val bubbleColor = if (isMine) CorpChatColors.bgBubbleOut else CorpChatColors.bgBubbleIn
    val shape = RoundedCornerShape(12.dp)
    val showMsgMenu = isMine && msg.msgType != "call" && (msg.msgType == "text" || msg.msgType == "file")
    var menuExpanded by remember(msg.id) { mutableStateOf(false) }
    val contentEndPadding = if (showMsgMenu) 40.dp else 12.dp
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Text(
            msg.from,
            style = MaterialTheme.typography.labelSmall,
            color = CorpChatColors.textMuted,
        )
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(bubbleColor),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = contentEndPadding, top = 8.dp, bottom = 8.dp),
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            ) {
                when {
                    msg.msgType == "call" -> {
                        Text(
                            formatCallLine(msg.callInfo, myUserId),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CorpChatColors.textPrimary,
                        )
                    }

                    msg.msgType == "file" && msg.file?.mime?.startsWith("image/") == true -> {
                        val path = imagePathFor(msg.file, isClient)
                        val full = baseUrl + path
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(full)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            imageLoader = imageLoader,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                        if (msg.text.isNotBlank()) {
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = CorpChatColors.textPrimary)
                        }
                    }

                    msg.msgType == "file" -> {
                        Text(
                            "Вложение: ${msg.file?.name ?: "файл"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CorpChatColors.textPrimary,
                        )
                        if (msg.text.isNotBlank()) {
                            Text(msg.text, style = MaterialTheme.typography.bodySmall, color = CorpChatColors.textSecondary)
                        }
                    }

                    else -> Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = CorpChatColors.textPrimary)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (msg.editedAt != null) {
                        Text(
                            "изменено",
                            style = MaterialTheme.typography.labelSmall,
                            color = CorpChatColors.textMuted,
                        )
                    }
                    Text(
                        msg.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = CorpChatColors.textMuted,
                    )
                }
            }
            if (showMsgMenu) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Действия с сообщением",
                        tint = CorpChatColors.textMuted,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (msg.msgType == "text" || msg.msgType == "file") {
                        DropdownMenuItem(
                            text = { Text("Изменить") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private fun imagePathFor(file: FileAttachmentDto, isClient: Boolean): String {
    val thumb = if (isClient) file.clientThumbUrl ?: file.thumbUrl else file.thumbUrl
    val full = if (isClient) file.clientUrl ?: file.url else file.url
    return thumb ?: full
}
