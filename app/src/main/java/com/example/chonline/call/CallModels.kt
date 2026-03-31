package com.example.chonline.call

import kotlinx.serialization.Serializable

@Serializable
data class CallInvite(
    val callId: String,
    val roomId: String,
    val fromUserId: String,
    val fromName: String,
    val mode: String = "audio",
    val ts: String = "",
)

sealed interface CallCommand {
    data class IncomingInvite(val invite: CallInvite) : CallCommand
    data class Accept(val invite: CallInvite) : CallCommand
    data class Decline(val invite: CallInvite) : CallCommand
}

