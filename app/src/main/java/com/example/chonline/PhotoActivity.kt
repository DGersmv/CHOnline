package com.example.chonline

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.io.IOException
import android.os.Build
import android.provider.Settings



class PhotoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val groupId = intent.getStringExtra("GROUP_ID") ?: "0"

        // 🔹 Проверяем и запрашиваем доступ к файлам (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent) // 📌 Открывает настройки для разрешения доступа
            }
        }

        setContent {
            PhotoScreen(groupId)
        }
    }
}





@Composable
fun PhotoScreen(groupId: String) {
    val context = LocalContext.current
    val imageList = remember { mutableStateListOf<Uri>() }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // 🔹 Запрос разрешений на камеру
    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Камера не доступна: разрешение не предоставлено", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // ✅ Проверяем и запрашиваем разрешение перед использованием камеры
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 📸 Выбор нескольких фото из галереи
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(), // ✅ Позволяет выбрать несколько файлов
        onResult = { uris: List<Uri> -> // ✅ Обрабатываем список выбранных файлов
            if (uris.isNotEmpty()) {
                imageList.addAll(uris) // ✅ Добавляем все фото в список
                Log.d("PhotoActivity", "Добавлены фото из галереи: $uris")
            } else {
                Log.e("PhotoActivity", "Ошибка: Список uri пуст при выборе из галереи")
            }
        }
    )





    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (imageList.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(imageList) { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Фото",
                        modifier = Modifier
                            .size(120.dp)
                            .padding(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { pickImageLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Выбрать фото из галереи")
            }

            Spacer(modifier = Modifier.height(8.dp))

            val takePhotoLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicture(),
                onResult = { success: Boolean ->
                    if (success && tempPhotoUri != null) {
                        imageList.add(tempPhotoUri!!)
                        Log.d("PhotoActivity", "Добавлено фото с камеры: $tempPhotoUri")
                    } else {
                        Log.e("PhotoActivity", "Ошибка: Фото не сохранено")
                        Toast.makeText(context, "Ошибка при съёмке фото", Toast.LENGTH_SHORT).show()
                    }
                }
            )




            Spacer(modifier = Modifier.height(16.dp)) // ✅ Закрываем Column правильно

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (imageList.isNotEmpty()) {
                            val intent = Intent(context, UploadActivity::class.java)
                            intent.putStringArrayListExtra("IMAGE_URIS", ArrayList(imageList.map { it.toString() })) // ✅ Отправляем весь список
                            intent.putExtra("GROUP_ID", groupId)
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Выберите фото перед отправкой", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отправить фото в группу")
                }

            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { imageList.clear() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Удалить все фото")
            }
        }
    }
}


