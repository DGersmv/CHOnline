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
        return Session(access, refresh, userId, email)
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
            .apply()
        _session.value = Session(access, refresh, userId, email)
    }

    fun updateAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS, token).apply()
        val s = _session.value ?: return
        _session.value = s.copy(accessToken = token)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .apply()
        _session.value = null
    }

    fun accessToken(): String? = _session.value?.accessToken
    fun refreshToken(): String? = _session.value?.refreshToken

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val email: String,
    )

    companion object {
        private const val PREFS_NAME = "chonline_secure_prefs"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
