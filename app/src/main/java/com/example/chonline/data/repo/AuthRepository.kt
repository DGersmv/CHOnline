package com.example.chonline.data.repo

import com.example.chonline.data.local.TokenStore
import com.example.chonline.data.remote.CorpChatApi
import com.example.chonline.data.remote.LogoutRequest
import com.example.chonline.data.remote.MeResponse
import com.example.chonline.data.remote.ProfileUpdateRequest
import com.example.chonline.data.remote.SendCodeRequest
import com.example.chonline.data.remote.VerifyRequest
import retrofit2.HttpException

class AuthRepository(
    private val api: CorpChatApi,
    private val tokenStore: TokenStore,
) {

    suspend fun sendCode(email: String): Result<Unit> = runCatching {
        val r = api.sendCode(SendCodeRequest(email.trim().lowercase()))
        if (r.ok != true) {
            error(r.error ?: "Не удалось отправить код")
        }
    }.mapError()

    suspend fun verify(email: String, code: String): Result<Unit> = runCatching {
        val r = api.verify(
            VerifyRequest(
                email = email.trim().lowercase(),
                code = code.trim(),
                deviceId = tokenStore.deviceId(),
                platform = "android",
            ),
        )
        if (r.ok != true || r.token == null || r.refreshToken == null || r.user == null) {
            error(r.error ?: "Неверный код")
        }
        tokenStore.saveSession(
            access = r.token,
            refresh = r.refreshToken,
            userId = r.user.id,
            email = r.user.email,
        )
    }.mapError()

    suspend fun loadMe(): Result<MeResponse> = runCatching {
        api.me()
    }.mapError()

    suspend fun updateProfile(name: String, phone: String): Result<MeResponse> = runCatching {
        api.updateProfile(ProfileUpdateRequest(name.trim(), phone.trim()))
    }.mapError()

    suspend fun logout() {
        val rt = tokenStore.refreshToken()
        runCatching {
            if (rt != null) api.logout(LogoutRequest(rt))
        }
        tokenStore.clear()
    }

    private fun <T> Result<T>.mapError(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { e ->
            val msg = when (e) {
                is HttpException ->
                    e.response()?.errorBody()?.string()?.let { parseErrorJson(it) }
                        ?: e.message

                else -> e.message
            }
            Result.failure(Exception(msg ?: "Ошибка сети"))
        },
    )

    private fun parseErrorJson(raw: String): String? {
        return try {
            val j = org.json.JSONObject(raw)
            j.optString("error").takeIf { it.isNotBlank() }
                ?: j.optString("message").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
