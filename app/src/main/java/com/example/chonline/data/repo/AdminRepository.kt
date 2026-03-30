package com.example.chonline.data.repo

import com.example.chonline.data.remote.AdminClientDto
import com.example.chonline.data.remote.AdminClientPasswordRequest
import com.example.chonline.data.remote.AdminClientUsersRequest
import com.example.chonline.data.remote.AdminEmployeeRowDto
import com.example.chonline.data.remote.AdminVisibleUserDto
import com.example.chonline.data.remote.CorpChatApi
import com.example.chonline.data.remote.CreateAdminClientRequest
import com.example.chonline.data.remote.PatchAdminClientRequest
import retrofit2.HttpException

class AdminRepository(
    private val api: CorpChatApi,
) {

    suspend fun listClients(): Result<List<AdminClientDto>> = runCatching {
        api.adminClients()
    }.mapError()

    suspend fun createClient(login: String, password: String, name: String): Result<Unit> = runCatching {
        val r = api.adminCreateClient(
            CreateAdminClientRequest(
                login = login.trim().lowercase(),
                password = password,
                name = name.trim(),
            ),
        )
        if (r.ok != true) error(r.error ?: "Не удалось создать заказчика")
    }.mapError()

    suspend fun setActive(clientId: String, active: Boolean): Result<Unit> = runCatching {
        val r = api.adminPatchClient(clientId, PatchAdminClientRequest(isActive = active))
        if (r.ok != true) error(r.error ?: "Не удалось обновить")
    }.mapError()

    suspend fun setPassword(clientId: String, password: String): Result<Unit> = runCatching {
        val r = api.adminSetClientPassword(clientId, AdminClientPasswordRequest(password))
        if (r.ok != true) error(r.error ?: "Не удалось сменить пароль")
    }.mapError()

    suspend fun deleteClient(clientId: String): Result<Unit> = runCatching {
        val r = api.adminDeleteClient(clientId)
        if (r.ok != true) error(r.error ?: "Не удалось удалить")
    }.mapError()

    suspend fun listEmployees(): Result<List<AdminEmployeeRowDto>> = runCatching {
        api.adminEmployees()
    }.mapError()

    suspend fun listVisibleUsers(clientId: String): Result<List<AdminVisibleUserDto>> = runCatching {
        api.adminClientUsers(clientId)
    }.mapError()

    suspend fun replaceVisibleUsers(clientId: String, userIds: List<String>): Result<Unit> = runCatching {
        val r = api.adminPutClientUsers(clientId, AdminClientUsersRequest(userIds))
        if (r.ok != true) error(r.error ?: "Не удалось сохранить")
    }.mapError()

    private fun <T> Result<T>.mapError(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { e ->
            val msg = when (e) {
                is HttpException -> e.response()?.errorBody()?.string()?.let { parseErrorJson(it) } ?: e.message
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
