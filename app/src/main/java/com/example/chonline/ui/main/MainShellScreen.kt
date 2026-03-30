package com.example.chonline.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chonline.data.remote.RoomDto
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.contacts.EmployeesListContent
import com.example.chonline.ui.profile.ProfileScreen
import com.example.chonline.ui.profile.ProfileViewModel
import com.example.chonline.ui.rooms.RoomsViewModel

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
) {
    val rooms by roomsViewModel.rooms.collectAsStateWithLifecycle()
    val loading by roomsViewModel.loading.collectAsStateWithLifecycle()
    val error by roomsViewModel.error.collectAsStateWithLifecycle()
    val online by roomsViewModel.online.collectAsStateWithLifecycle()
    val socketConnected by roomsViewModel.socketConnected.collectAsStateWithLifecycle()

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
        val bottomH = (maxHeight * 0.1f).coerceAtLeast(56.dp).coerceAtMost(120.dp)

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
                    MainTab.Chats -> ChatsTabBody(
                        rooms = filteredRooms,
                        loading = loading,
                        error = error,
                        onOpenChat = onOpenChat,
                    )

                    MainTab.Contacts -> EmployeesListContent(
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
                    )
                }
            }

            GlassBottomNav(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomH)
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
    loading: Boolean,
    error: String?,
    onOpenChat: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            loading && rooms.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rooms, key = { it.id }) { room ->
                    ListItem(
                        headlineContent = { Text(room.title.ifBlank { room.id }) },
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
private fun CallsTabBody() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Звонки\nскоро",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
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
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassNavItem("Контакты", selected == MainTab.Contacts) { onSelect(MainTab.Contacts) }
        GlassNavItem("Звонки", selected == MainTab.Calls) { onSelect(MainTab.Calls) }
        GlassNavItem("Чаты", selected == MainTab.Chats) { onSelect(MainTab.Chats) }
        GlassNavItem("Настройки", selected == MainTab.Settings) { onSelect(MainTab.Settings) }
    }
}

@Composable
private fun GlassNavItem(
    label: String,
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
            .widthIn(max = 96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
