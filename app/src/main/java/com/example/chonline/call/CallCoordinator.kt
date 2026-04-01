package com.example.chonline.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CallCoordinator {
    private val _commands = MutableSharedFlow<CallCommand>(extraBufferCapacity = 16)
    val commands = _commands.asSharedFlow()
    private val seenIncomingCallIds = LinkedHashSet<String>()

    fun submit(command: CallCommand) {
        if (command is CallCommand.IncomingInvite) {
            val callId = command.invite.callId
            if (callId.isNotBlank()) {
                synchronized(seenIncomingCallIds) {
                    if (seenIncomingCallIds.contains(callId)) return
                    seenIncomingCallIds.add(callId)
                    while (seenIncomingCallIds.size > 256) {
                        val first = seenIncomingCallIds.firstOrNull() ?: break
                        seenIncomingCallIds.remove(first)
                    }
                }
            }
        }
        _commands.tryEmit(command)
    }
}

