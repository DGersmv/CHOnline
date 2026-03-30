package com.example.chonline.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chonline.data.remote.EmployeeDto
import com.example.chonline.di.AppContainer
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    val imageLoader = rememberCoilWithAuth(container)
    val base = com.example.chonline.BuildConfig.API_BASE_URL.trimEnd('/')

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
                    val title = displayName(em)
                    val email = (em.email ?: em.accountEmail).orEmpty()
                    val phone = displayPhone(em)
                    val job = em.jobTitle.trim()
                    ListItem(
                        leadingContent = {
                            Avatar(
                                baseUrl = base,
                                employee = em,
                                imageLoader = imageLoader,
                            )
                        },
                        headlineContent = {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (email.isNotBlank()) {
                                    Text(
                                        email,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                val second = when {
                                    phone.isNotBlank() && job.isNotBlank() -> "$phone • $job"
                                    phone.isNotBlank() -> phone
                                    job.isNotBlank() -> job
                                    else -> ""
                                }
                                if (second.isNotBlank()) {
                                    Text(
                                        second,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        },
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

@Composable
private fun rememberCoilWithAuth(container: AppContainer): ImageLoader {
    val ctx = LocalContext.current
    return remember(container.okHttpForImages) {
        ImageLoader.Builder(ctx)
            .okHttpClient(container.okHttpForImages)
            .build()
    }
}

private fun displayName(em: EmployeeDto): String {
    val n = em.name.trim()
    if (n.isNotBlank()) return n
    val an = em.adminName.trim()
    if (an.isNotBlank()) return an
    val email = (em.email ?: em.accountEmail).orEmpty().trim()
    if (email.isNotBlank()) return email
    return em.id
}

private fun displayPhone(em: EmployeeDto): String {
    val p = em.phone.trim()
    if (p.isNotBlank()) return p
    val ap = em.adminPhone.trim()
    if (ap.isNotBlank()) return ap
    return ""
}

@Composable
private fun Avatar(
    baseUrl: String,
    employee: EmployeeDto,
    imageLoader: ImageLoader,
) {
    val size = 40.dp
    val has = (employee.hasAvatar ?: 0) != 0
    val avatarUrl = if (has) {
        val id = URLEncoder.encode(employee.id, StandardCharsets.UTF_8.name())
        val rev = employee.avatarRev?.takeIf { it.isNotBlank() }?.let {
            "?rev=" + URLEncoder.encode(it, StandardCharsets.UTF_8.name())
        }.orEmpty()
        "$baseUrl/api/v1/users/$id/avatar$rev"
    } else {
        null
    }

    if (avatarUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
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
                initials(displayName(employee)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val s = when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1)
        else -> (parts[0].take(1) + parts[1].take(1))
    }
    return s.uppercase()
}
