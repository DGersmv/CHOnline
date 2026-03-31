package com.example.chonline.data.repo

import com.example.chonline.data.local.TokenStore
import com.example.chonline.data.remote.ClientLoginRequest
import com.example.chonline.data.remote.ClientProfileDto
import com.example.chonline.data.remote.ClientProfileUpdateRequest
import com.example.chonline.data.remote.CorpChatApi
import com.example.chonline.data.remote.LogoutRequest
import com.example.chonline.data.remote.MeResponse
import com.example.chonline.data.remote.ProfileUpdateRequest
import com.example.chonline.data.remote.PushTokenSaveRequest
import com.example.chonline.data.remote.SendCodeRequest
import com.example.chonline.data.remote.VerifyRequest
import retrofit2.HttpException

class AuthRepository(
    private val api: CorpChatApi,
    private val tokenStore: TokenStore,
) {

    suspend fun loginType(login: String): Result<String> = runCatching {
        api.loginType(login.trim().lowercase()).type
    }.mapError()

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

    suspend fun clientLogin(login: String, password: String): Result<Unit> = runCatching {
        val r = api.clientLogin(
            ClientLoginRequest(
                login = login.trim().lowercase(),
                password = password,
                deviceId = tokenStore.deviceId(),
            ),
        )
        if (r.ok != true || r.token == null || r.refreshToken == null || r.client == null) {
            error(r.error ?: "Неверный логин или пароль")
        }
        val c = r.client
        tokenStore.saveClientSession(
            access = r.token,
            refresh = r.refreshToken,
            clientId = c.id,
            login = c.login,
            name = c.name,
        )
    }.mapError()

    suspend fun loadMe(): Result<MeResponse> = runCatching {
        if (tokenStore.isClient()) error("employee only")
        api.me()
    }.mapError()

    /** Имя и телефон для экрана профиля и навигации. */
    suspend fun loadProfile(): Result<ProfileFields> = runCatching {
        if (tokenStore.isClient()) {
            val c = api.clientMe()
            ProfileFields(name = c.name, phone = c.phone)
        } else {
            val m = api.me()
            ProfileFields(name = m.name, phone = m.phone)
        }
    }.mapError()

    /** Старт: нужно ли показывать обязательное заполнение профиля. */
    suspend fun loadProfileForStartup(): Result<ProfileSnapshot> = runCatching {
        if (tokenStore.isClient()) {
            val c = api.clientMe()
            ProfileSnapshot(
                name = c.name,
                phone = c.phone,
                isClient = true,
            )
        } else {
            val m = api.me()
            ProfileSnapshot(
                name = m.name,
                phone = m.phone,
                isClient = false,
            )
        }
    }.mapError()

    data class ProfileFields(val name: String, val phone: String)

    suspend fun updateProfile(name: String, phone: String): Result<Unit> = runCatching {
        if (tokenStore.isClient()) {
            api.updateClientProfile(ClientProfileUpdateRequest(name.trim(), phone.trim()))
        } else {
            api.updateProfile(ProfileUpdateRequest(name.trim(), phone.trim()))
        }
        Unit
    }.mapError()

    suspend fun logout() {
        val rt = tokenStore.refreshToken()
        runCatching {
            if (rt != null) api.logout(LogoutRequest(rt))
        }
        tokenStore.clear()
    }

    suspend fun registerPushToken(token: String): Result<Unit> = runCatching {
        val pushToken = token.trim()
        if (pushToken.isBlank()) return@runCatching
        val r = api.savePushToken(
            PushTokenSaveRequest(
                token = pushToken,
                deviceId = tokenStore.deviceId(),
                platform = "android",
            ),
        )
        if (r.ok != true) error(r.error ?: "Не удалось сохранить push-токен")
    }.mapError()

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

    data class ProfileSnapshot(
        val name: String,
        val phone: String,
        val isClient: Boolean,
    )
}
