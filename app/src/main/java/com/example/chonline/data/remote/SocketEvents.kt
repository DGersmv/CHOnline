package com.example.chonline.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MissedMessagesPayload(
    val roomId: String,
    val messages: List<MessageDto> = emptyList(),
)

@Serializable
data class SystemPayload(
    val text: String = "",
    val roomId: String? = null,
)

@Serializable
data class OnlinePayload(val users: List<OnlineUserDto> = emptyList())

@Serializable
data class OnlineUserDto(
    val id: String,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
)

@Serializable
data class MessageDeletePayload(
    val id: String,
    val roomId: String,
)

@Serializable
data class RoomPatchPayload(
    val roomId: String,
    val title: String? = null,
    @SerialName("hasGroupAvatar") val hasGroupAvatar: Int? = null,
    @SerialName("groupAvatarRev") val groupAvatarRev: String? = null,
)

@Serializable
data class RoomDeletedPayload(
    val roomId: String,
)
