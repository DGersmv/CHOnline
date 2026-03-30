package com.example.chonline.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onEditGroup: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(
        key = roomId,
        factory = ChatViewModelFactory(roomId, container.chatRepository, container.tokenStore),
    )
    val messages by vm.messages.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    val room by vm.room.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val imageLoader = rememberCoilWithAuth(container)
    val isClient = container.tokenStore.isClient()

    var editTarget by remember { mutableStateOf<MessageDto?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.sendFile(context, it) }
    }

    LaunchedEffect(roomId) {
        vm.refreshRoomMeta()
    }

    val lastId = messages.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
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
            TopAppBar(
                title = { Text(barTitle) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    if (showGroupEdit) {
                        TextButton(onClick = { onEditGroup?.invoke() }) {
                            Text("Изменить")
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasMore && messages.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = { vm.loadOlder() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Загрузить раньше") }
                    }
                }
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        isMine = vm.isMyMessage(msg),
                        isClient = isClient,
                        baseUrl = BuildConfig.API_BASE_URL.trimEnd('/'),
                        imageLoader = imageLoader,
                        onEdit = {
                            if (msg.msgType == "text") {
                                editTarget = msg
                                editText = msg.text
                            }
                        },
                        onDelete = { deleteTargetId = msg.id },
                    )
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
                TextButton(onClick = { pickFile.launch("*/*") }, enabled = !sending) { Text("Файл") }
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
                    enabled = !sending && input.isNotBlank(),
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
            title = { Text("Изменить сообщение") },
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
                        vm.editMessage(target.id, editText)
                        editTarget = null
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Отмена") }
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
    isClient: Boolean,
    baseUrl: String,
    imageLoader: ImageLoader,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val bubbleColor = if (isMine) CorpChatColors.bgBubbleOut else CorpChatColors.bgBubbleIn
    val shape = RoundedCornerShape(12.dp)
    val showMsgMenu = isMine && msg.msgType == "text"
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
                    DropdownMenuItem(
                        text = { Text("Изменить") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
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
