package com.example.chonline.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendCodeRequest(val email: String)

@Serializable
data class SendCodeResponse(val ok: Boolean? = null, val error: String? = null)

@Serializable
data class VerifyRequest(
    val email: String,
    val code: String,
    val deviceId: String? = null,
    val platform: String? = null,
)

@Serializable
data class VerifyResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val user: UserDto? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val token: String? = null,
)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class OkResponse(val ok: Boolean? = null, val error: String? = null)

@Serializable
data class UserDto(val id: String, val email: String)

@Serializable
data class MeResponse(
    val id: String,
    val name: String = "",
    val phone: String = "",
    val email: String? = null,
)

@Serializable
data class ProfileUpdateRequest(val name: String, val phone: String)

@Serializable
data class EmployeeDto(
    val id: String,
    val name: String = "",
    val phone: String = "",
    @SerialName("jobTitle") val jobTitle: String = "",
    val email: String? = null,
    @SerialName("account_email") val accountEmail: String? = null,
    @SerialName("adminName") val adminName: String = "",
    @SerialName("adminPhone") val adminPhone: String = "",
    // Сервер отдает 0/1, не boolean.
    @SerialName("hasAvatar") val hasAvatar: Int? = null,
    @SerialName("avatarRev") val avatarRev: String? = null,
    /** Заказчик в объединённом списке контактов (employee-only). */
    @SerialName("isClient") val isClient: Boolean = false,
    /**
     * Сотрудник: из GET /employees — есть ли общая с заказчиком комната для диалога (см. POST /rooms/open-client).
     * null — старый сервер; false — заказчик виден для групп, но диалог из контактов недоступен.
     */
    @SerialName("canOpenDm") val canOpenDm: Boolean? = null,
)

/** Сотрудник в списке для заказчика (`GET /client/employees`). */
@Serializable
data class ClientEmployeeDto(
    val id: String,
    val name: String = "",
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class EmployeesResponse(
    val employees: List<EmployeeDto> = emptyList(),
    /** Заказчики, доступные сотруднику (только employee API). */
    val clients: List<EmployeeDto> = emptyList(),
)

@Serializable
data class RoomDto(
    val id: String,
    val type: String,
    val title: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("createdBy") val createdBy: String? = null,
    @SerialName("createdByName") val createdByName: String? = null,
    @SerialName("dmPeerUserId") val dmPeerUserId: String? = null,
    @SerialName("clientLinked") val clientLinked: Boolean? = null,
    @SerialName("linkedClientIds") val linkedClientIds: List<String>? = null,
    @SerialName("hasGroupAvatar") val hasGroupAvatar: Int? = null,
    @SerialName("groupAvatarRev") val groupAvatarRev: String? = null,
    val lastPreview: String? = null,
    @SerialName("lastAt") val lastAt: String? = null,
)

@Serializable
data class RoomsResponse(val rooms: List<RoomDto> = emptyList())

@Serializable
data class MessagesResponse(
    val messages: List<MessageDto> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
)

@Serializable
data class CallInfoDto(
    val v: Int = 1,
    val callId: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val initiatorId: String = "",
    val status: String = "",
    val durationSec: Int? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val callerName: String? = null,
    val calleeName: String? = null,
)

@Serializable
data class MessageDto(
    val id: String,
    val from: String = "",
    val text: String = "",
    val time: String,
    @SerialName("editedAt") val editedAt: String? = null,
    @SerialName("userId") val userId: String? = null,
    @SerialName("roomId") val roomId: String? = null,
    @SerialName("msgType") val msgType: String = "text",
    @SerialName("fromClientId") val fromClientId: String? = null,
    val file: FileAttachmentDto? = null,
    @SerialName("callInfo") val callInfo: CallInfoDto? = null,
)

@Serializable
data class FileAttachmentDto(
    val name: String = "",
    val mime: String = "",
    val size: Long = 0,
    val url: String,
    @SerialName("clientUrl") val clientUrl: String? = null,
    @SerialName("clientThumbUrl") val clientThumbUrl: String? = null,
    @SerialName("thumbUrl") val thumbUrl: String? = null,
)

@Serializable
data class SendTextRequest(val text: String)

@Serializable
data class LoginTypeResponse(val type: String)

@Serializable
data class ClientLoginRequest(
    val login: String,
    val password: String,
    @SerialName("deviceId") val deviceId: String,
    val platform: String = "android",
)

@Serializable
data class ClientAuthResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val client: ClientProfileDto? = null,
)

@Serializable
data class ClientProfileDto(
    val id: String,
    val login: String = "",
    val name: String = "",
    val phone: String = "",
    @SerialName("hasAvatar") val hasAvatar: Boolean? = null,
)

@Serializable
data class ClientProfileUpdateRequest(
    val name: String,
    val phone: String,
)

@Serializable
data class SendMessageResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val message: MessageDto? = null,
)

@Serializable
data class CreateDmRequest(@SerialName("peerId") val peerId: String)

@Serializable
data class OpenClientRequest(val clientId: String)

@Serializable
data class CreateDmResponse(val room: RoomDto? = null, val error: String? = null)

@Serializable
data class CreateGroupRequest(
    val title: String,
    val memberIds: List<String>,
    val clientIds: List<String>,
)

@Serializable
data class GroupEditResponse(
    val room: RoomDto,
    val memberIds: List<String> = emptyList(),
    val clientIds: List<String> = emptyList(),
)

@Serializable
data class PatchRoomRequest(
    val title: String? = null,
    val memberIds: List<String>? = null,
    val clientIds: List<String>? = null,
)

@Serializable
data class RoomMutationResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val room: RoomDto? = null,
)

@Serializable
data class ErrorBody(val error: String? = null, val ok: Boolean? = null)

@Serializable
data class VoiceCallEntryDto(
    val id: String,
    val peerId: String,
    val peerName: String = "",
    val startedAt: String,
    val endedAt: String? = null,
    val status: String = "",
    val durationSec: Int? = null,
)

@Serializable
data class CallsHistoryResponse(val calls: List<VoiceCallEntryDto> = emptyList())

// --- Админка заказчиков (тот же API, что веб) ---

@Serializable
data class AdminClientDto(
    val id: String,
    val login: String = "",
    val name: String = "",
    @SerialName("is_active") val isActive: Int? = 1,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("visible_users_count") val visibleUsersCount: Int? = 0,
)

@Serializable
data class AdminEmployeeRowDto(
    val id: String,
    val name: String = "",
    val phone: String = "",
    @SerialName("jobTitle") val jobTitle: String = "",
    val email: String? = null,
    @SerialName("account_email") val accountEmail: String? = null,
    @SerialName("adminName") val adminName: String = "",
    @SerialName("adminPhone") val adminPhone: String = "",
)

@Serializable
data class AdminVisibleUserDto(
    val id: String,
    val name: String = "",
    val email: String? = null,
)

@Serializable
data class CreateAdminClientRequest(
    val login: String,
    val password: String,
    val name: String = "",
)

@Serializable
data class CreateAdminClientResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val id: String? = null,
    val login: String? = null,
    val name: String? = null,
)

@Serializable
data class PatchAdminClientRequest(
    val name: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class AdminClientPasswordRequest(val password: String)

@Serializable
data class AdminClientUsersRequest(val userIds: List<String>)
