package com.example.chonline.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chonline.BuildConfig
import com.example.chonline.data.remote.EmployeeDto
import com.example.chonline.data.remote.RoomDto
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.contacts.EmployeesListContent
import com.example.chonline.ui.profile.ProfileScreen
import com.example.chonline.ui.profile.ProfileViewModel
import com.example.chonline.ui.rooms.RoomsViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private enum class MainTab {
    Contacts,
    Calls,
    Chats,
    Settings,
}

@Composable
fun MainShellScreen(
    roomsViewModel: RoomsViewModel,
    profileViewModel: ProfileViewModel,
    container: AppContainer,
    onOpenChat: (String) -> Unit,
    onLogout: () -> Unit,
    onCreateGroup: () -> Unit = {},
    onOpenAdminClients: () -> Unit = {},
) {
    val session by container.tokenStore.session.collectAsStateWithLifecycle(initialValue = null)
    val isAdmin =
        !container.tokenStore.isClient() &&
            session?.email?.trim()?.lowercase() == BuildConfig.ADMIN_EMAIL.trim().lowercase()

    val rooms by roomsViewModel.rooms.collectAsStateWithLifecycle()
    val employees by roomsViewModel.employees.collectAsStateWithLifecycle()
    val loading by roomsViewModel.loading.collectAsStateWithLifecycle()
    val error by roomsViewModel.error.collectAsStateWithLifecycle()
    val online by roomsViewModel.online.collectAsStateWithLifecycle()
    val socketConnected by roomsViewModel.socketConnected.collectAsStateWithLifecycle()

    val onlineIds = remember(online) { online.map { it.id }.toSet() }

    var tab by remember { mutableStateOf(MainTab.Chats) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredRooms = remember(rooms, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) rooms
        else {
            rooms.filter { r ->
                r.title.lowercase().contains(q) ||
                    r.id.lowercase().contains(q) ||
                    r.lastPreview.orEmpty().lowercase().contains(q)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topH = (maxHeight * 0.1f).coerceAtLeast(56.dp).coerceAtMost(120.dp)

        Column(Modifier.fillMaxSize()) {
            TopSearchStatusBar(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                socketConnected = socketConnected,
                onlineCount = online.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topH)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (tab) {
                    MainTab.Chats -> {
                        val isClient = container.tokenStore.isClient()
                        Box(Modifier.fillMaxSize()) {
                            ChatsTabBody(
                                rooms = filteredRooms,
                                employees = employees,
                                onlineIds = onlineIds,
                                container = container,
                                loading = loading,
                                error = error,
                                onOpenChat = onOpenChat,
                            )
                            if (!isClient) {
                                FloatingActionButton(
                                    onClick = onCreateGroup,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Новая группа")
                                }
                            }
                        }
                    }

                    MainTab.Contacts -> EmployeesListContent(
                        employees = employees,
                        onlineIds = onlineIds,
                        loading = loading,
                        networkError = error,
                        container = container,
                        onOpenDm = onOpenChat,
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainTab.Calls -> CallsTabBody()

                    MainTab.Settings -> ProfileScreen(
                        viewModel = profileViewModel,
                        onDone = { },
                        exitAfterSave = false,
                        showLogout = true,
                        onLogout = onLogout,
                        isAdmin = isAdmin,
                        onOpenAdminClients = onOpenAdminClients,
                    )
                }
            }

            GlassBottomNav(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TopSearchStatusBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    socketConnected: Boolean,
    onlineCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Поиск чатов", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(min = 72.dp),
        ) {
            val (label, color) = if (socketConnected) {
                "Онлайн" to Color(0xFF2E7D32)
            } else {
                "Нет связи" to MaterialTheme.colorScheme.error
            }
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (onlineCount > 0 && socketConnected) {
                Text(
                    "$onlineCount в сети",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatsTabBody(
    rooms: List<RoomDto>,
    employees: List<EmployeeDto>,
    onlineIds: Set<String>,
    container: AppContainer,
    loading: Boolean,
    error: String?,
    onOpenChat: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val imageLoader = androidx.compose.runtime.remember(container.okHttpForImages) {
        ImageLoader.Builder(ctx)
            .okHttpClient(container.okHttpForImages)
            .build()
    }
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    val isClientApp = container.tokenStore.isClient()
    val myId = container.tokenStore.session.value?.userId
    Box(Modifier.fillMaxSize()) {
        when {
            loading && rooms.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rooms, key = { it.id }) { room ->
                    val peerOnline = remember(room, onlineIds, myId) {
                        dmPeerOnline(room, onlineIds, myId)
                    }
                    ListItem(
                        leadingContent = {
                            ChatRowAvatar(
                                room = room,
                                employees = employees,
                                baseUrl = base,
                                isClientApp = isClientApp,
                                myUserId = myId,
                                imageLoader = imageLoader,
                                showOnlineDot = peerOnline,
                            )
                        },
                        headlineContent = { Text(chatListTitleForRoom(room, employees)) },
                        supportingContent = {
                            Text(
                                room.lastPreview.orEmpty().ifBlank { "Нет сообщений" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(room.id) },
                    )
                }
            }
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ChatRowAvatar(
    room: RoomDto,
    employees: List<EmployeeDto>,
    baseUrl: String,
    isClientApp: Boolean,
    myUserId: String?,
    imageLoader: ImageLoader,
    showOnlineDot: Boolean = false,
) {
    val title = remember(room, employees) { chatListTitleForRoom(room, employees) }
    val initials = remember(title) { chatRowInitials(title) }
    val url = remember(room, employees, baseUrl, isClientApp, myUserId) {
        chatListAvatarUrl(room, employees, baseUrl, isClientApp, myUserId)
    }
    val size = 44.dp
    val onlineColor = Color(0xFF2E7D32)
    Box(modifier = Modifier.size(size)) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showOnlineDot) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(onlineColor)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

private fun dmPeerOnline(room: RoomDto, onlineIds: Set<String>, myUserId: String?): Boolean {
    if (room.type != "dm") return false
    val peer = room.dmPeerUserId ?: return false
    if (myUserId != null && peer == myUserId) return false
    return onlineIds.contains(peer)
}

private fun chatRowInitials(title: String): String {
    val parts = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/**
 * Для ЛС с [clientLinked], заголовок с сервера — имя сотрудника-собеседника, а аватар берётся с заказчика.
 * Если в комнате ровно один связанный заказчик, показываем его имя в списке чатов.
 */
private fun chatListTitleForRoom(room: RoomDto, employees: List<EmployeeDto>): String {
    val base = room.title.ifBlank { room.id }
    if (room.type != "dm") return base
    if (room.clientLinked != true) return base
    val ids = room.linkedClientIds.orEmpty()
    if (ids.size != 1) return base
    val cid = ids[0]
    val em = employees.find { it.id == cid && it.isClient } ?: return base
    val n = em.name.trim()
    if (n.isNotBlank()) return n
    val an = em.adminName.trim()
    if (an.isNotBlank()) return an
    val email = (em.email ?: em.accountEmail).orEmpty().trim()
    if (email.isNotBlank()) return email
    return base
}

private fun pickLinkedClientId(room: RoomDto, employees: List<EmployeeDto>): String? {
    val ids = room.linkedClientIds ?: return null
    for (id in ids) {
        val e = employees.find { it.id == id && it.isClient }
        if ((e?.hasAvatar ?: 0) != 0) return id
    }
    return ids.firstOrNull()
}

private fun chatListAvatarUrl(
    room: RoomDto,
    employees: List<EmployeeDto>,
    base: String,
    isClientApp: Boolean,
    myUserId: String?,
): String? {
    fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
    fun revPart(r: String?) = r?.takeIf { it.isNotBlank() }?.let { "?rev=" + enc(it) }.orEmpty()

    if (room.type == "group" && (room.hasGroupAvatar ?: 0) != 0) {
        val prefix = if (isClientApp) "$base/api/v1/client" else "$base/api/v1"
        return "$prefix/rooms/${enc(room.id)}/avatar${revPart(room.groupAvatarRev)}"
    }
    if (!isClientApp && room.clientLinked == true) {
        val cid = pickLinkedClientId(room, employees) ?: return null
        val em = employees.find { it.id == cid } ?: return null
        if ((em.hasAvatar ?: 0) == 0) return null
        return "$base/api/v1/clients/${enc(cid)}/avatar${revPart(em.avatarRev)}"
    }
    if (room.type == "dm") {
        val peer = room.dmPeerUserId ?: return null
        if (myUserId != null && peer == myUserId) return null
        val em = employees.find { it.id == peer && !it.isClient } ?: return null
        if ((em.hasAvatar ?: 0) == 0) return null
        val path = if (isClientApp) "client/users" else "users"
        return "$base/api/v1/$path/${enc(peer)}/avatar${revPart(em.avatarRev)}"
    }
    return null
}

@Composable
private fun CallsTabBody() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Аудиозвонки 1:1 между сотрудниками доступны в веб-версии мессенджера.\n\n" +
                "В приложении звонки появятся в следующих версиях.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun GlassBottomNav(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val glass = Brush.verticalGradient(
        colors = listOf(
            scheme.surface.copy(alpha = 0.42f),
            scheme.surface.copy(alpha = 0.68f),
        ),
    )
    val borderColor = scheme.outline.copy(alpha = 0.28f)

    Row(
        modifier
            .clip(shape)
            .background(glass)
            .border(1.dp, borderColor, shape)
            .height(84.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassNavItem(Icons.Filled.Person, "Контакты", selected == MainTab.Contacts) { onSelect(MainTab.Contacts) }
        GlassNavItem(Icons.Filled.Call, "Звонки", selected == MainTab.Calls) { onSelect(MainTab.Calls) }
        GlassNavItem(Icons.Filled.Chat, "Чаты", selected == MainTab.Chats) { onSelect(MainTab.Chats) }
        GlassNavItem(Icons.Filled.Settings, "Настройки", selected == MainTab.Settings) { onSelect(MainTab.Settings) }
    }
}

@Composable
private fun GlassNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }
    Box(
        Modifier
            .widthIn(max = 72.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
