package com.example.chonline

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.chonline.ui.theme.Orange1
import com.example.chonline.ui.theme.Black10
import androidx.core.content.edit
import com.example.chonline.network.fetchGroups

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoginScreen()
        }
    }
}

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    var username by remember { mutableStateOf(loadSavedData(sharedPreferences, "username")) }
    var password by remember { mutableStateOf(loadSavedData(sharedPreferences, "password")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Логин") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    saveUserData(sharedPreferences, username, password) // Сохраняем данные
                    val intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Введите логин и пароль", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Black10,
                contentColor = Orange1
            )
        ) {
            Text("Войти")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Кнопка "Очистить данные"
        Button(
            onClick = {
                clearUserData(sharedPreferences) // Передаём просто `sharedPreferences`
                username = ""
                password = ""
                Toast.makeText(context, "Данные удалены", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Очистить данные")
        }
    }
}

/**
 * Функция для сохранения логина и пароля в SharedPreferences
 */
fun saveUserData(sharedPreferences: SharedPreferences, username: String, password: String) {
    sharedPreferences.edit {
        putString("username", username)
        putString("password", password)
    }
}

/**
 * Функция для загрузки сохранённых данных
 */
fun loadSavedData(sharedPreferences: SharedPreferences, key: String): String {
    return sharedPreferences.getString(key, "") ?: ""
}

/**
 * Функция для очистки сохранённых данных
 */
fun clearUserData(sharedPreferences: SharedPreferences) {
    sharedPreferences.edit {
        clear()
    }
}
fun loginAndFetchGroups(
    context: Context,
    sharedPreferences: SharedPreferences,
    username: String,
    password: String
) {
    if (username.isEmpty() || password.isEmpty()) {
        Toast.makeText(context, "Введите логин и пароль", Toast.LENGTH_SHORT).show()
        return
    }

    // Сохраняем логин и пароль
    saveUserData(sharedPreferences, username, password)

    // Запрашиваем группы с API
    fetchGroups(context) { result ->
        if (result.isSuccess) {
            val groups = result.getOrNull()?.joinToString("\n") ?: "Нет данных"
            Toast.makeText(context, "Группы:\n$groups", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Ошибка загрузки групп: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
