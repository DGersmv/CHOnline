package com.example.chonline.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onDone: () -> Unit,
    exitAfterSave: Boolean = true,
    showLogout: Boolean = false,
    onLogout: () -> Unit = {},
    isAdmin: Boolean = false,
    onOpenAdminClients: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(ui.loading, ui.name, ui.phone) {
        if (!ui.loading && !profileLoaded) {
            name = ui.name
            phone = ui.phone
            profileLoaded = true
        }
    }

    LaunchedEffect(ui.saved) {
        if (ui.saved) {
            if (exitAfterSave) onDone()
            viewModel.consumeSaved()
        }
    }

    val scrollState = rememberScrollState()
    val mod = if (showLogout) {
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp)
    } else {
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    }
    val arrangement = if (showLogout) Arrangement.Top else Arrangement.Center

    Column(
        mod,
        verticalArrangement = arrangement,
    ) {
        if (showLogout) Spacer(Modifier.height(8.dp))
        Text("Профиль", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Укажите имя и телефон — так вас увидят коллеги",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Имя") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words,
            ),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Телефон") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.save(name, phone) },
            enabled = !ui.loading && name.isNotBlank() && phone.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.loading) CircularProgressIndicator(Modifier.height(20.dp))
            else Text("Сохранить")
        }
        if (showLogout) {
            if (isAdmin) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onOpenAdminClients,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Заказчики (админ)")
                }
            }
            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выйти", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(32.dp))
            PushDiagnosticsSection()
            Spacer(Modifier.height(24.dp))
        }
    }
}
