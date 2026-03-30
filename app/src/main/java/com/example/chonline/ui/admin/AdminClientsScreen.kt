package com.example.chonline.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chonline.data.remote.AdminClientDto
import com.example.chonline.data.remote.AdminEmployeeRowDto
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.theme.CorpChatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminClientsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: AdminClientsViewModel = viewModel(
        factory = AdminClientsViewModel.factory(container.adminRepository),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val usersPicker by vm.usersPicker.collectAsStateWithLifecycle()

    var showNew by remember { mutableStateOf(false) }
    var newLogin by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    var pwdClient by remember { mutableStateOf<AdminClientDto?>(null) }
    var pwdValue by remember { mutableStateOf("") }

    var deleteTarget by remember { mutableStateOf<AdminClientDto?>(null) }

    Scaffold(
        containerColor = CorpChatColors.bgDeep,
        topBar = {
            TopAppBar(
                title = { Text("Заказчики", color = CorpChatColors.textPrimary) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад", color = CorpChatColors.accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CorpChatColors.bgPanel,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newLogin = ""
                    newPassword = ""
                    newName = ""
                    showNew = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Новый заказчик")
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.clients.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.clients, key = { it.id }) { c ->
                            AdminClientCard(
                                client = c,
                                onUsers = { vm.openUsersPicker(c.id, c.login) },
                                onPassword = {
                                    pwdClient = c
                                    pwdValue = ""
                                },
                                onToggle = { vm.toggleActive(c) },
                                onDelete = { deleteTarget = c },
                            )
                        }
                    }
                }
            }
            state.error?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { if (!state.loading) showNew = false },
            title = { Text("Новый заказчик") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newLogin,
                        onValueChange = { newLogin = it },
                        label = { Text("Логин (email)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Пароль (мин. 6 символов)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Имя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.createClient(newLogin, newPassword, newName) {
                            showNew = false
                        }
                    },
                    enabled = !state.loading && newLogin.isNotBlank() && newPassword.length >= 6,
                ) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showNew = false }, enabled = !state.loading) {
                    Text("Отмена")
                }
            },
        )
    }

    pwdClient?.let { c ->
        AlertDialog(
            onDismissRequest = { pwdClient = null },
            title = { Text("Пароль: ${c.login}") },
            text = {
                OutlinedTextField(
                    value = pwdValue,
                    onValueChange = { pwdValue = it },
                    label = { Text("Новый пароль") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setPassword(c.id, pwdValue) { pwdClient = null }
                    },
                    enabled = pwdValue.length >= 6,
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { pwdClient = null }) { Text("Отмена") }
            },
        )
    }

    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить заказчика?") },
            text = { Text("«${c.login}» — сессии будут сброшены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteClient(c.id)
                        deleteTarget = null
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            },
        )
    }

    usersPicker?.let { pick ->
        AlertDialog(
            onDismissRequest = { if (!pick.saving) vm.dismissUsersPicker() },
            title = { Text("Видимые сотрудники: ${pick.loginHint}") },
            text = {
                when {
                    pick.loading -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(36.dp))
                        }
                    }
                    pick.error != null -> Text(pick.error!!, color = MaterialTheme.colorScheme.error)
                    else -> {
                        Column(
                            Modifier
                                .height(360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            pick.employees.forEach { em ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = pick.selectedIds.contains(em.id),
                                        onCheckedChange = { vm.togglePickerUser(em.id) },
                                        enabled = !pick.saving,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(adminEmployeeTitle(em), style = MaterialTheme.typography.bodyMedium)
                                        val sub = adminEmployeeSub(em)
                                        if (sub.isNotBlank()) {
                                            Text(
                                                sub,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.saveUsersPicker { } },
                    enabled = !pick.loading && pick.error == null && !pick.saving,
                ) {
                    if (pick.saving) CircularProgressIndicator(Modifier.size(20.dp))
                    else Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.dismissUsersPicker() },
                    enabled = !pick.saving,
                ) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun AdminClientCard(
    client: AdminClientDto,
    onUsers: () -> Unit,
    onPassword: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val active = (client.isActive ?: 0) != 0
    val vis = client.visibleUsersCount ?: 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CorpChatColors.bgPanel),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(client.login, style = MaterialTheme.typography.titleMedium, color = CorpChatColors.textPrimary)
                Text(
                    if (active) "активен" else "выкл",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${client.name.ifBlank { "—" }} · видят $vis сотр.",
                style = MaterialTheme.typography.bodySmall,
                color = CorpChatColors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onUsers) { Text("Сотрудники") }
                TextButton(onClick = onPassword) { Text("Пароль") }
                TextButton(onClick = onToggle) { Text(if (active) "Отключить" else "Включить") }
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

private fun adminEmployeeTitle(e: AdminEmployeeRowDto): String {
    val n = e.name.trim()
    if (n.isNotBlank()) return n
    val an = e.adminName.trim()
    if (an.isNotBlank()) return an
    return (e.email ?: e.accountEmail).orEmpty().ifBlank { e.id }
}

private fun adminEmployeeSub(e: AdminEmployeeRowDto): String {
    val jt = e.jobTitle.trim()
    val em = (e.email ?: e.accountEmail).orEmpty().trim()
    return when {
        jt.isNotBlank() && em.isNotBlank() -> "$jt · $em"
        jt.isNotBlank() -> jt
        em.isNotBlank() -> em
        else -> ""
    }
}
