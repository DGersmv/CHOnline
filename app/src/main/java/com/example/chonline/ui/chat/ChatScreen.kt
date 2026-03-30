package com.example.chonline.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import com.example.chonline.BuildConfig
import com.example.chonline.data.remote.MessageDto
import com.example.chonline.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    container: AppContainer,
    onBack: () -> Unit,
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
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val imageLoader = rememberCoilWithAuth(container)

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.sendFile(context, it) }
    }

    val lastId = messages.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(roomTitle(roomId)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
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
                        myUserId = vm.myUserId,
                        baseUrl = BuildConfig.API_BASE_URL.trimEnd('/'),
                        imageLoader = imageLoader,
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
    myUserId: String?,
    baseUrl: String,
    imageLoader: ImageLoader,
) {
    val mine = msg.userId == myUserId
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            msg.from,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            when {
                msg.msgType == "file" && msg.file?.mime?.startsWith("image/") == true -> {
                    val path = msg.file.thumbUrl ?: msg.file.url
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
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                msg.msgType == "file" -> {
                    Text(
                        "Вложение: ${msg.file?.name ?: "файл"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (msg.text.isNotBlank()) Text(msg.text, style = MaterialTheme.typography.bodySmall)
                }

                else -> Text(msg.text, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                msg.time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
