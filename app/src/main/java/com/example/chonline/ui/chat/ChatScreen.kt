package com.example.chonline.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var attachmentOpen by remember { mutableStateOf<AttachmentOpenUi?>(null) }
    var imagePreview by remember { mutableStateOf<ImagePreviewState?>(null) }

    var editTarget by remember { mutableStateOf<MessageDto?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    var pendingFileUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var fileCaption by remember { mutableStateOf("") }
    var fileSendAsPanorama by remember { mutableStateOf(false) }
    var wasFileUploading by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingFileUris = uris
            fileCaption = ""
            fileSendAsPanorama = false
        }
    }

    LaunchedEffect(fileUploadProgress) {
        if (fileUploadProgress != null) wasFileUploading = true
        if (wasFileUploading && fileUploadProgress == null) {
            pendingFileUris = emptyList()
            fileCaption = ""
            fileSendAsPanorama = false
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
                        onImagePreview = { url, messageId, file ->
                            imagePreview = ImagePreviewState(url, messageId, file)
                        },
                        onOpenFileAttachment = msg.file?.let { file ->
                            if (msg.msgType != "file" || file.mime.startsWith("image/")) {
                                null
                            } else {
                                {
                                    attachmentOpen = AttachmentOpenUi(
                                        messageId = msg.id,
                                        fileName = file.name.ifBlank { "файл" },
                                        file = file,
                                        phase = AttachmentOpenUi.Phase.Choose,
                                    )
                                }
                            }
                        },
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
                    val allImages = remember(pendingFileUris) {
                        pendingFileUris.all { u ->
                            cr.getType(u)?.startsWith("image/") == true
                        }
                    }
                    if (allImages) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !uploading) {
                                fileSendAsPanorama = !fileSendAsPanorama
                            },
                        ) {
                            Checkbox(
                                checked = fileSendAsPanorama,
                                onCheckedChange = { if (!uploading) fileSendAsPanorama = it },
                                enabled = !uploading,
                            )
                            Text(
                                "Сферическая панорама (360°)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CorpChatColors.textPrimary,
                            )
                        }
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
                        ) { uri ->
                            fileSendAsPanorama &&
                                context.contentResolver.getType(uri)?.startsWith("image/") == true
                        }
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

    imagePreview?.let { st ->
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        var savingImage by remember(st.messageId) { mutableStateOf(false) }
        var sphereMode by remember(st.messageId, st.file.panorama) {
            mutableStateOf(st.file.panorama)
        }
        Dialog(
            onDismissRequest = { imagePreview = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                if (sphereMode) {
                    PanoramaSphereWebView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 48.dp, bottom = 96.dp),
                        baseUrl = base,
                        messageId = st.messageId,
                        file = st.file,
                        isClient = isClient,
                        chatRepository = container.chatRepository,
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(st.url).crossfade(false).build(),
                        contentDescription = "Просмотр изображения",
                        imageLoader = imageLoader,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 40.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                IconButton(
                    onClick = { imagePreview = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White,
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Сохраняется полный файл, не превью.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (savingImage) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        TextButton(
                            onClick = {
                                savingImage = true
                                scope.launch {
                                    container.chatRepository
                                        .saveChatAttachmentToDownloads(
                                            context,
                                            base,
                                            st.messageId,
                                            st.file,
                                            isClient,
                                        )
                                        .fold(
                                            onSuccess = { hint ->
                                                savingImage = false
                                                Toast.makeText(
                                                    context,
                                                    "Сохранено: $hint",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            },
                                            onFailure = { e ->
                                                savingImage = false
                                                Toast.makeText(
                                                    context,
                                                    e.message ?: "Не удалось сохранить",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            },
                                        )
                                }
                            },
                            enabled = !savingImage,
                        ) {
                            Text("Сохранить в «Загрузки»", color = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { sphereMode = !sphereMode },
                            enabled = !savingImage,
                        ) {
                            Text(
                                if (sphereMode) "Плоско" else "360°",
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }

    attachmentOpen?.let { st ->
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        when (st.phase) {
            AttachmentOpenUi.Phase.Choose -> {
                AlertDialog(
                    onDismissRequest = { attachmentOpen = null },
                    title = { Text("Файл") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Что сделать с «${st.fileName}»?",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Повторное открытие или сохранение берёт файл из кэша, " +
                                    "если он уже скачивался и размер совпадает с сообщением — " +
                                    "без повторной загрузки из сети.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CorpChatColors.textMuted,
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        attachmentOpen = st.copy(
                                            phase = AttachmentOpenUi.Phase.Downloading,
                                            pendingAction = AttachmentOpenUi.PendingAction.Open,
                                        )
                                        container.chatRepository
                                            .ensureChatAttachmentInCache(
                                                context,
                                                base,
                                                st.messageId,
                                                st.file,
                                                isClient,
                                            )
                                            .fold(
                                                onSuccess = { javaFile ->
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        javaFile,
                                                    )
                                                    attachmentOpen = st.copy(
                                                        phase = AttachmentOpenUi.Phase.Ready,
                                                        contentUri = uri,
                                                        mime = st.file.mime.ifBlank { "application/octet-stream" },
                                                    )
                                                },
                                                onFailure = { e ->
                                                    attachmentOpen = st.copy(
                                                        phase = AttachmentOpenUi.Phase.Error,
                                                        error = e.message ?: "Не удалось скачать файл",
                                                    )
                                                },
                                            )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Открыть в другом приложении") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        attachmentOpen = st.copy(
                                            phase = AttachmentOpenUi.Phase.Downloading,
                                            pendingAction = AttachmentOpenUi.PendingAction.SaveToDownloads,
                                        )
                                        container.chatRepository
                                            .saveChatAttachmentToDownloads(
                                                context,
                                                base,
                                                st.messageId,
                                                st.file,
                                                isClient,
                                            )
                                            .fold(
                                                onSuccess = { hint ->
                                                    attachmentOpen = st.copy(
                                                        phase = AttachmentOpenUi.Phase.SavedOk,
                                                        saveHint = hint,
                                                    )
                                                },
                                                onFailure = { e ->
                                                    attachmentOpen = st.copy(
                                                        phase = AttachmentOpenUi.Phase.Error,
                                                        error = e.message ?: "Не удалось сохранить файл",
                                                    )
                                                },
                                            )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Сохранить в «Загрузки»") }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { attachmentOpen = null }) { Text("Отмена") }
                    },
                )
            }

            AttachmentOpenUi.Phase.Downloading -> {
                val subtitle = when (st.pendingAction) {
                    AttachmentOpenUi.PendingAction.SaveToDownloads -> "Сохранение в «Загрузки»…"
                    AttachmentOpenUi.PendingAction.Open,
                    null,
                    -> "Подготовка к открытию…"
                }
                Dialog(
                    onDismissRequest = { },
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false,
                    ),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CorpChatColors.bgPanel),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Загрузка файла", style = MaterialTheme.typography.titleMedium)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CorpChatColors.textMuted)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                                Text(
                                    st.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CorpChatColors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }

            AttachmentOpenUi.Phase.Ready -> {
                val uri = st.contentUri ?: return@let
                val mime = st.mime ?: "application/octet-stream"
                AlertDialog(
                    onDismissRequest = { attachmentOpen = null },
                    title = { Text("Файл готов") },
                    text = {
                        Text(
                            "Выберите приложение, чтобы открыть «${st.fileName}».",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                try {
                                    launchAttachmentViewIntent(context, uri, mime)
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Не удалось открыть файл",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                attachmentOpen = null
                            },
                        ) { Text("Открыть") }
                    },
                    dismissButton = {
                        TextButton(onClick = { attachmentOpen = null }) { Text("Закрыть") }
                    },
                )
            }

            AttachmentOpenUi.Phase.SavedOk -> {
                AlertDialog(
                    onDismissRequest = { attachmentOpen = null },
                    title = { Text("Сохранено") },
                    text = {
                        Text(
                            "Файл «${st.fileName}» сохранён: ${st.saveHint ?: ""}. " +
                                "Имя в папке может начинаться с цифр (время), чтобы не перезаписать другие файлы.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { attachmentOpen = null }) { Text("ОК") }
                    },
                )
            }

            AttachmentOpenUi.Phase.Error -> {
                AlertDialog(
                    onDismissRequest = { attachmentOpen = null },
                    title = { Text("Ошибка") },
                    text = {
                        Text(
                            st.error ?: "Ошибка",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { attachmentOpen = null }) { Text("ОК") }
                    },
                )
            }
        }
    }
}

private data class ImagePreviewState(
    val url: String,
    val messageId: String,
    val file: FileAttachmentDto,
)

private data class AttachmentOpenUi(
    val messageId: String,
    val fileName: String,
    val file: FileAttachmentDto,
    val phase: Phase,
    val pendingAction: PendingAction? = null,
    val contentUri: Uri? = null,
    val mime: String? = null,
    val error: String? = null,
    val saveHint: String? = null,
) {
    enum class Phase { Choose, Downloading, Ready, SavedOk, Error }

    enum class PendingAction { Open, SaveToDownloads }
}

private fun launchAttachmentViewIntent(context: android.content.Context, uri: Uri, mime: String) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "", uri)
    }
    val chooser = Intent.createChooser(view, "Открыть файл").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
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
    onImagePreview: ((String, String, FileAttachmentDto) -> Unit)?,
    onOpenFileAttachment: (() -> Unit)?,
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
                        val file = msg.file!!
                        val bubbleUrl = attachmentUrlForDisplay(baseUrl, imagePathFor(file, isClient))
                        val previewUrl = attachmentUrlForDisplay(baseUrl, fullImagePathFor(file, isClient))
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(bubbleUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Фото в сообщении",
                            imageLoader = imageLoader,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = onImagePreview != null) {
                                    onImagePreview?.invoke(previewUrl, msg.id, file)
                                },
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
                        if (onOpenFileAttachment != null) {
                            Text(
                                "Сохранить или открыть",
                                style = MaterialTheme.typography.labelLarge,
                                color = CorpChatColors.accent,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clickable(onClick = onOpenFileAttachment),
                            )
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

/** Полноразмерное изображение для просмотра по нажатию (не превью). */
private fun fullImagePathFor(file: FileAttachmentDto, isClient: Boolean): String {
    val full = if (isClient) file.clientUrl ?: file.url else file.url
    val thumb = if (isClient) file.clientThumbUrl ?: file.thumbUrl else file.thumbUrl
    return full.ifBlank { thumb ?: "" }
}

private fun attachmentUrlForDisplay(baseUrl: String, pathOrUrl: String): String {
    val p = pathOrUrl.trim()
    if (p.startsWith("http://", ignoreCase = true) || p.startsWith("https://", ignoreCase = true)) return p
    return baseUrl.trimEnd('/') + "/" + p.trimStart('/')
}
