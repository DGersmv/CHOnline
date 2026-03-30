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
    val email: String? = null,
    @SerialName("account_email") val accountEmail: String? = null,
)

@Serializable
data class EmployeesResponse(val employees: List<EmployeeDto> = emptyList())

@Serializable
data class RoomDto(
    val id: String,
    val type: String,
    val title: String = "",
    @SerialName("created_at") val createdAt: String? = null,
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
data class MessageDto(
    val id: String,
    val from: String = "",
    val text: String = "",
    val time: String,
    @SerialName("userId") val userId: String? = null,
    @SerialName("roomId") val roomId: String? = null,
    @SerialName("msgType") val msgType: String = "text",
    val file: FileAttachmentDto? = null,
)

@Serializable
data class FileAttachmentDto(
    val name: String = "",
    val mime: String = "",
    val size: Long = 0,
    val url: String,
    @SerialName("thumbUrl") val thumbUrl: String? = null,
)

@Serializable
data class SendTextRequest(val text: String)

@Serializable
data class SendMessageResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val message: MessageDto? = null,
)

@Serializable
data class CreateDmRequest(@SerialName("peerId") val peerId: String)

@Serializable
data class CreateDmResponse(val room: RoomDto? = null, val error: String? = null)

@Serializable
data class ErrorBody(val error: String? = null, val ok: Boolean? = null)
