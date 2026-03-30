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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onCodeSent: (String) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(login) {
        viewModel.onLoginInputChanged(login)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Вход в чат", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        val hint = when {
            ui.loginTypeLoading -> "Проверка…"
            ui.loginKind == LoginKind.Client -> "Заказчик: введите пароль"
            else -> "Сотрудник: введите email — пришлём код"
        }
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (ui.loginKind == LoginKind.Client) "Логин" else "Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (ui.loginKind == LoginKind.Client) KeyboardType.Text else KeyboardType.Email),
            enabled = !ui.loading,
        )
        if (ui.loginKind == LoginKind.Client) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !ui.loading,
            )
        }
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        val canSubmit = when (ui.loginKind) {
            LoginKind.Client -> login.length >= 3 && password.isNotBlank()
            LoginKind.Employee -> login.contains('@')
            LoginKind.Unknown -> login.contains('@')
        }
        Button(
            onClick = {
                when (ui.loginKind) {
                    LoginKind.Client -> viewModel.loginAsClient(login, password)
                    else -> viewModel.sendCode(login)
                }
            },
            enabled = !ui.loading && !ui.loginTypeLoading && canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.loading) CircularProgressIndicator(Modifier.height(20.dp))
            else {
                Text(
                    when (ui.loginKind) {
                        LoginKind.Client -> "Войти"
                        else -> "Получить код"
                    },
                )
            }
        }
    }

    LaunchedEffect(ui.codeSentFor) {
        val e = ui.codeSentFor ?: return@LaunchedEffect
        onCodeSent(e)
        viewModel.consumeCodeSent()
    }
}
