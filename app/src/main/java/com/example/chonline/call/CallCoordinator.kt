package com.example.chonline.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CallCoordinator {
    private val _commands = MutableSharedFlow<CallCommand>(extraBufferCapacity = 16)
    val commands = _commands.asSharedFlow()

    fun submit(command: CallCommand) {
        _commands.tryEmit(command)
    }
}

