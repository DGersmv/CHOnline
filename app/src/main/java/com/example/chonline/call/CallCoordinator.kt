package com.example.chonline.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CallCoordinator {
    /** extraBuffer — команды от сокета до появления подписчика. */
    private val _commands = MutableSharedFlow<CallCommand>(extraBufferCapacity = 16)
    val commands = _commands.asSharedFlow()
    private val seenIncomingCallIds = LinkedHashSet<String>()

    /**
     * Помечаем callId (дедуп с call:invite по сокету).
     * @return false — дублирующий IncomingInvite, навигацию не делать.
     */
    fun prepareForIntentNavigation(command: CallCommand): Boolean {
        val callId = when (command) {
            is CallCommand.IncomingInvite -> command.invite.callId
            is CallCommand.Accept -> command.invite.callId
            is CallCommand.Decline -> command.invite.callId
        }
        if (callId.isBlank()) return true
        synchronized(seenIncomingCallIds) {
            when (command) {
                is CallCommand.IncomingInvite -> {
                    if (seenIncomingCallIds.contains(callId)) return false
                    seenIncomingCallIds.add(callId)
                }
                is CallCommand.Accept, is CallCommand.Decline -> {
                    seenIncomingCallIds.add(callId)
                }
            }
            while (seenIncomingCallIds.size > 256) {
                val first = seenIncomingCallIds.firstOrNull() ?: break
                seenIncomingCallIds.remove(first)
            }
        }
        return true
    }

    fun submit(command: CallCommand) {
        if (!prepareForIntentNavigation(command)) return
        _commands.tryEmit(command)
    }

}

