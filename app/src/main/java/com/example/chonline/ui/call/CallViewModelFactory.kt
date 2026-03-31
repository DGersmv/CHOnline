package com.example.chonline.ui.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.chonline.data.repo.ChatRepository

class CallViewModelFactory(
    private val appContext: Context,
    private val repo: ChatRepository,
    private val callId: String,
    private val roomId: String,
    private val peerId: String,
    private val peerName: String,
    private val incoming: Boolean,
    private val autoAccept: Boolean,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CallViewModel(
            appContext = appContext,
            repo = repo,
            initialCallId = callId,
            roomId = roomId,
            peerId = peerId,
            peerName = peerName,
            incoming = incoming,
            autoAccept = autoAccept,
        ) as T
    }
}

