package com.example.chonline.data.remote

import com.example.chonline.BuildConfig
import com.example.chonline.data.local.TokenStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

private val JsonUtf8 = "application/json; charset=utf-8".toMediaType()

fun createJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

fun createOkHttpClient(
    tokenStore: TokenStore,
    json: Json,
): OkHttpClient {
    val refreshUrl = "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/v1/auth/refresh"
    val refreshLock = Any()

    fun refreshAccessToken(): Boolean {
        val refresh = tokenStore.refreshToken() ?: return false
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
            .toRequestBody(JsonUtf8)
        val req = Request.Builder()
            .url(refreshUrl)
            .post(body)
            .build()
        val plain = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        plain.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return false
            val parsed = runCatching {
                json.decodeFromString(RefreshResponse.serializer(), text)
            }.getOrNull() ?: return false
            val newToken = parsed.token ?: return false
            tokenStore.updateAccessToken(newToken)
            return true
        }
    }

    val authRefreshInterceptor = Interceptor { chain ->
        val original = chain.request()
        val access = tokenStore.accessToken()
        val authed = if (access != null) {
            original.newBuilder().header("Authorization", "Bearer $access").build()
        } else {
            original
        }
        val response = chain.proceed(authed)
        if (response.code != 401) return@Interceptor response

        val path = authed.url.encodedPath
        if (path.contains("auth/send") || path.contains("auth/verify") || path.contains("auth/refresh")) {
            return@Interceptor response
        }

        response.close()
        if (access == null) {
            return@Interceptor chain.proceed(authed)
        }

        synchronized(refreshLock) {
            if (tokenStore.accessToken() == access) {
                if (!refreshAccessToken()) {
                    tokenStore.clear()
                }
            }
        }

        val newAccess = tokenStore.accessToken()
        val retry = authed.newBuilder()
            .apply {
                if (newAccess != null) header("Authorization", "Bearer $newAccess")
                else removeHeader("Authorization")
            }
            .build()
        chain.proceed(retry)
    }

    val logging = HttpLoggingInterceptor().apply {
        level =
            if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
            else HttpLoggingInterceptor.Level.NONE
    }

    return OkHttpClient.Builder()
        .addInterceptor(authRefreshInterceptor)
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
}

fun createRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
    val base = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/"
    return Retrofit.Builder()
        .baseUrl(base)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory(JsonUtf8))
        .build()
}
