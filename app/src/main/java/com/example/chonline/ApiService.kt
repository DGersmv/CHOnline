package com.example.chonline.network

import android.content.Context
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import android.util.Base64

fun fetchGroups(context: Context, callback: (Result<List<Pair<String, String>>>) -> Unit) {
    val sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val username = sharedPreferences.getString("username", "") ?: ""
    val password = sharedPreferences.getString("password", "") ?: ""

    if (username.isEmpty() || password.isEmpty()) {
        callback(Result.failure(IOException("Ошибка авторизации! Войдите в систему.")))
        return
    }

    val credentials = "$username:$password"
    val authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

    val url = "https://country-house.online/wp-json/my-api/v1/groups"
    val client = OkHttpClient()
    val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", authHeader) // ✅ Добавляем авторизацию
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!response.isSuccessful) {
                    callback(Result.failure(IOException("Ошибка: ${response.code}")))
                    return
                }

                val responseBody = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(responseBody)
                val groupList = mutableListOf<Pair<String, String>>() // ✅ (ID, Name)

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val groupId = jsonObject.optString("id", "0") // ✅ Берём ID
                    val groupName = jsonObject.optString("name", "Без имени") // ✅ Берём Name
                    groupList.add(Pair(groupId, groupName))
                }

                callback(Result.success(groupList))
            }
        }
    })
}

