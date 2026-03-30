package com.example.chonline.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.chonline.data.local.TokenStore
import com.example.chonline.data.repo.ChatRepository

class ChatViewModelFactory(
    private val roomId: String,
    private val chatRepository: ChatRepository,
    private val tokenStore: TokenStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(roomId, chatRepository, tokenStore) as T
    }
}
