package com.example.chonline.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CallCoordinator {
    private val _commands = MutableSharedFlow<CallCommand>(extraBufferCapacity = 16)
    val commands = _commands.asSharedFlow()
    private val seenIncomingCallIds = LinkedHashSet<String>()
    @Volatile
    private var pendingCommand: CallCommand? = null

    fun submit(command: CallCommand) {
        val callId = when (command) {
            is CallCommand.IncomingInvite -> command.invite.callId
            is CallCommand.Accept -> command.invite.callId
            is CallCommand.Decline -> command.invite.callId
        }
        if (callId.isNotBlank()) {
            synchronized(seenIncomingCallIds) {
                when (command) {
                    is CallCommand.IncomingInvite -> {
                        // Уже обработали этот звонок (в т.ч. ответ/отклонение из уведомления) —
                        // не перезаписывать pending и не навигировать снова на «входящий».
                        if (seenIncomingCallIds.contains(callId)) return
                        seenIncomingCallIds.add(callId)
                    }
                    is CallCommand.Accept, is CallCommand.Decline -> {
                        // Ответ раньше socket call:invite: помечаем callId, чтобы invite не сбросил Accept.
                        seenIncomingCallIds.add(callId)
                    }
                }
                while (seenIncomingCallIds.size > 256) {
                    val first = seenIncomingCallIds.firstOrNull() ?: break
                    seenIncomingCallIds.remove(first)
                }
            }
        }
        pendingCommand = command
        _commands.tryEmit(command)
    }

    fun consumePending(): CallCommand? {
        val p = pendingCommand
        pendingCommand = null
        return p
    }

}

