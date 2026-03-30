package com.example.chonline.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private fun loadSession(): Session? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val role = prefs.getString(KEY_ROLE, ROLE_EMPLOYEE) ?: ROLE_EMPLOYEE
        val clientLogin = prefs.getString(KEY_CLIENT_LOGIN, null)
        val clientName = prefs.getString(KEY_CLIENT_NAME, null)
        return Session(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = email,
            role = role,
            clientLogin = clientLogin,
            clientName = clientName,
        )
    }

    fun deviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun saveSession(access: String, refresh: String, userId: String, email: String) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putString(KEY_ROLE, ROLE_EMPLOYEE)
            .remove(KEY_CLIENT_LOGIN)
            .remove(KEY_CLIENT_NAME)
            .apply()
        _session.value = Session(access, refresh, userId, email, ROLE_EMPLOYEE, null, null)
    }

    fun saveClientSession(access: String, refresh: String, clientId: String, login: String, name: String) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER_ID, clientId)
            .putString(KEY_EMAIL, login)
            .putString(KEY_ROLE, ROLE_CLIENT)
            .putString(KEY_CLIENT_LOGIN, login)
            .putString(KEY_CLIENT_NAME, name)
            .apply()
        _session.value = Session(access, refresh, clientId, login, ROLE_CLIENT, login, name)
    }

    fun updateAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS, token).apply()
        val s = _session.value ?: return
        _session.value = s.copy(accessToken = token)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    fun accessToken(): String? = _session.value?.accessToken
    fun refreshToken(): String? = _session.value?.refreshToken

    fun isClient(): Boolean = _session.value?.role == ROLE_CLIENT

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val email: String,
        val role: String = ROLE_EMPLOYEE,
        val clientLogin: String? = null,
        val clientName: String? = null,
    )

    companion object {
        private const val PREFS_NAME = "chonline_secure_prefs"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ROLE = "role"
        private const val KEY_CLIENT_LOGIN = "client_login"
        private const val KEY_CLIENT_NAME = "client_name"

        const val ROLE_EMPLOYEE = "employee"
        const val ROLE_CLIENT = "client"
    }
}
