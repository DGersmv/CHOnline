package com.example.authapp

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) } // Добавляем SharedPreferences

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Поле ввода логина
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Логин") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Поле ввода пароля
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

        // Кнопка Войти
        Button(
            onClick = {
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    Toast.makeText(context, "Авторизация успешна!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Введите логин и пароль", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Войти")
        }
        Spacer(modifier = Modifier.height(8.dp))


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
