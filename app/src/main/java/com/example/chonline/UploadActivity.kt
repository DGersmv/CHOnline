package com.example.chonline

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.work.*
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class UploadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUris = intent.getStringArrayListExtra("IMAGE_URIS") ?: arrayListOf()
        val groupId = intent.getStringExtra("GROUP_ID") ?: "0"

        Log.d("UploadActivity", "Получены файлы: $imageUris")
        Log.d("UploadActivity", "Группа ID: $groupId")

        if (imageUris.isEmpty()) {
            Toast.makeText(this, "Ошибка: файлы не переданы!", Toast.LENGTH_SHORT).show()
            Log.e("UploadActivity", "Ошибка: imageUris пустой!")
            finish()
            return
        }

        setContent {
            UploadScreen(imageUris, groupId) { finish() }
        }
    }
}

@Composable
fun UploadScreen(imageUris: List<String>, groupId: String, onUploadComplete: () -> Unit) {
    val context = LocalContext.current
    var uploadStatus by remember { mutableStateOf("Подготовка к загрузке...") }

    LaunchedEffect(Unit) {
        startUploadWork(context, imageUris, groupId)
        uploadStatus = "Файлы поставлены в очередь загрузки..."
        delay(2000)
        onUploadComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(uploadStatus, style = MaterialTheme.typography.titleMedium)
    }
}

fun startUploadWork(context: Context, imageUris: List<String>, groupId: String) {
    val workManager = WorkManager.getInstance(context)

    val uploadWork = OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(workDataOf(
            "IMAGE_URIS" to imageUris.toTypedArray(),
            "GROUP_ID" to groupId
        ))
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            1,
            TimeUnit.MINUTES
        )
        .build()

    workManager.enqueue(uploadWork)
}

