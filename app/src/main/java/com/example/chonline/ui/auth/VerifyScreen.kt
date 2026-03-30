package com.example.chonline.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VerifyScreen(
    email: String,
    viewModel: AuthViewModel,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(ui.loggedIn) {
        if (ui.loggedIn) {
            onSuccess()
            viewModel.consumeLoggedIn()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Код из письма", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { v ->
                if (v.length <= 8) code = v.filter { ch -> ch.isDigit() }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Код") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.verify(email, code) },
            enabled = !ui.loading && code.length >= 4,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.loading) CircularProgressIndicator(Modifier.height(20.dp))
            else Text("Войти")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onBack,
            enabled = !ui.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Вернуться — другой email")
        }
    }
}
