package com.example.chonline.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chonline.data.remote.EmployeeDto
import com.example.chonline.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun EmployeesListContent(
    container: AppContainer,
    onOpenDm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<EmployeeDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val myId = container.tokenStore.session.value?.userId

    LaunchedEffect(Unit) {
        loading = true
        container.chatRepository.loadEmployees()
            .onSuccess { list = it }
            .onFailure { error = it.message }
        loading = false
    }

    Box(modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(
                    list.filter { it.id != myId },
                    key = { it.id },
                ) { em ->
                    val title = em.name.ifBlank { em.email ?: em.accountEmail ?: em.id }
                    val sub = em.email ?: em.accountEmail.orEmpty()
                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { Text(sub) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                container.chatRepository.openDm(em.id)
                                    .onSuccess { onOpenDm(it.id) }
                                    .onFailure { error = it.message }
                            }
                        },
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
