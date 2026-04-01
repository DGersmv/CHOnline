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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val ui = viewModel.ui.collectAsStateWithLifecycle().value
    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.status, ui.callId) {
        elapsedSec = 0
        if (ui.status != "connected") return@LaunchedEffect
        while (true) {
            delay(1_000)
            elapsedSec++
        }
    }
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
        when (ui.status) {
            "failed" -> {
                delay(4_000)
                onClose()
            }
            in setOf("ended", "declined", "rejected", "missed") -> onClose()
            else -> Unit
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
        if (ui.status == "connected") {
            Text(
                text = formatCallDuration(elapsedSec),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
        } else {
            Text(
                text = statusRu(ui.status),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
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
        } else if (ui.status != "failed") {
            Button(onClick = { viewModel.end() }) {
                Text("Завершить")
            }
        } else {
            Button(onClick = onClose) {
                Text("Закрыть")
            }
        }
    }
}

private fun formatCallDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

private fun statusRu(s: String): String = when (s) {
    "incoming" -> "Входящий звонок"
    "dialing" -> "Вызов"
    "ringing" -> "Ожидание ответа"
    "connecting" -> "Подключение"
    "failed" -> "Не удалось соединить"
    "connected" -> "В разговоре"
    "declined" -> "Отклонено"
    "rejected" -> "Отклонено собеседником"
    "missed" -> "Пропущенный"
    "ended" -> "Завершено"
    else -> s
}

