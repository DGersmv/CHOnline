package com.example.chonline.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.chonline.AppSigningFingerprint
import com.example.chonline.BuildConfig
import com.example.chonline.push.RuStorePushErrorParser
import ru.rustore.sdk.pushclient.RuStorePushClient

/**
 * Показывает ID проекта, отпечаток подписи этой установки и даёт запросить/скопировать push-токен.
 * Без совпадения отпечатка в консоли Ru Store с [AppSigningFingerprint] getToken не сработает.
 */
@Composable
fun PushDiagnosticsSection() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var fingerprint by remember { mutableStateOf<String?>(null) }
    var allFingerprints by remember { mutableStateOf<List<String>>(emptyList()) }
    var token by remember { mutableStateOf<String?>(null) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    var pubKeyFromError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fingerprint = AppSigningFingerprint.sha256ColonHex(context)
        allFingerprints = AppSigningFingerprint.allSha256ColonHex(context)
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Уведомления (Ru Store)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        val pid = BuildConfig.RUSTORE_PUSH_PROJECT_ID
        Text(
            if (pid.isBlank()) {
                "ID проекта в сборке: не задан (local.properties → rustore.push.project.id)"
            } else {
                "ID проекта в сборке: $pid"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Отпечаток подписи текущего APK (SHA-256) — его же ждёт Ru Store в ошибке pub_key:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            fingerprint ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (allFingerprints.size > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Все отпечатки по цепочке подписи (при необходимости добавьте в консоль каждый):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            allFingerprints.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                fingerprint?.let { fp ->
                    clipboard.setText(AnnotatedString(fp))
                }
            },
            enabled = !fingerprint.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Копировать основной отпечаток")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "В Ru Store (Push → проект, com.example.chonline) должен быть зарегистрирован этот отпечаток. " +
                "Если в консоли другой (например от Play) — добавьте новый, не заменяя release.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                loading = true
                token = null
                tokenError = null
                pubKeyFromError = null
                RuStorePushClient.getToken()
                    .addOnSuccessListener { t ->
                        loading = false
                        if (t.isBlank()) {
                            tokenError = "Токен пустой"
                        } else {
                            token = t
                        }
                    }
                    .addOnFailureListener { e ->
                        loading = false
                        val full = e.message ?: e.toString()
                        tokenError = full
                        pubKeyFromError = RuStorePushErrorParser.extractPubKeySha256(full)
                    }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (loading) "Запрос…" else "Запросить push-токен")
        }
        tokenError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        pubKeyFromError?.let { pk ->
            Spacer(Modifier.height(12.dp))
            Text(
                "Ru Store отклонил запрос: этого отпечатка нет в проекте Push. Добавьте его в консоли (тот же package) и сохраните:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                pk,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(pk)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Копировать отпечаток из ошибки")
            }
        }
        token?.let { t ->
            Spacer(Modifier.height(8.dp))
            Text(
                t,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(t)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Копировать токен (для теста в консоли)")
            }
        }
    }
}
