package com.example.chonline.data.remote

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
