package com.example.chonline.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val ui = viewModel.ui.collectAsStateWithLifecycle().value
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setMicPermissionGranted(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setMicPermissionGranted(granted)
        if (!granted) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(ui.status) {
        if (ui.status in setOf("ended", "declined", "rejected", "missed")) {
            onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = ui.peerName.ifBlank { "Звонок" },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = statusRu(ui.status),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        ui.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (ui.incoming && ui.status == "incoming") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewModel.decline() }, modifier = Modifier.weight(1f)) {
                    Text("Отклонить")
                }
                Button(onClick = { viewModel.accept() }, modifier = Modifier.weight(1f)) {
                    Text("Ответить")
                }
            }
        } else {
            Button(onClick = { viewModel.end() }) {
                Text("Завершить")
            }
        }
    }
}

private fun statusRu(s: String): String = when (s) {
    "incoming" -> "Входящий звонок"
    "dialing" -> "Вызов"
    "ringing" -> "Ожидание ответа"
    "connecting" -> "Подключение"
    "connected" -> "В разговоре"
    "declined" -> "Отклонено"
    "rejected" -> "Отклонено собеседником"
    "missed" -> "Пропущенный"
    "ended" -> "Завершено"
    else -> s
}

