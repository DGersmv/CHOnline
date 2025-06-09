package com.example.chonline

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("username", "") ?: ""
        val password = sharedPreferences.getString("password", "") ?: ""
        val groupId = inputData.getString("GROUP_ID") ?: return Result.failure()
        val imageUris = inputData.getStringArray("IMAGE_URIS") ?: return Result.failure()

        if (username.isEmpty() || password.isEmpty()) {
            Log.e("UploadWorker", "Ошибка авторизации: нет логина и пароля")
            return Result.failure()
        }

        val credentials = "$username:$password"
        val authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        val client = OkHttpClient()

        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        var fileAdded = false

        imageUris.forEach { uriString ->
            val uri = Uri.parse(uriString)
            val file = uriToFile(context, uri)

            if (file != null && file.exists()) {
                val requestBody = RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
                multipartBuilder.addFormDataPart("files[]", file.name, requestBody)
                fileAdded = true
            } else {
                Log.e("UploadWorker", "Файл не найден: $uriString")
            }
        }

        if (!fileAdded) {
            Log.e("UploadWorker", "Нет файлов для отправки!")
            return Result.failure()
        }

        val requestBody = multipartBuilder.build()
        val request = Request.Builder()
            .url("https://country-house.online/wp-json/my-api/v1/groups/$groupId/media")
            .addHeader("Authorization", authHeader)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Log.d("UploadWorker", "Фото успешно загружены: $responseBody")
                showToast(context, "Загрузка завершена!")
                Result.success()
            } else {
                Log.e("UploadWorker", "Ошибка загрузки: ${response.message}")
                showToast(context, "Ошибка загрузки: ${response.message}")
                Result.retry() // 🔄 Попробуем ещё раз позже
            }
        } catch (e: IOException) {
            Log.e("UploadWorker", "Ошибка сети: ${e.message}")
            showToast(context, "Ошибка сети!")
            Result.retry() // 🔄 Повторим попытку
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri).use { input ->
                FileOutputStream(file).use { output ->
                    input?.copyTo(output)
                }
            }
            file
        } catch (e: IOException) {
            Log.e("UploadWorker", "Ошибка преобразования URI в файл: ${e.message}")
            null
        }
    }

    private fun showToast(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
