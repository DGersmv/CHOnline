package com.example.chonline.di

import android.content.Context
import com.example.chonline.BuildConfig
import com.example.chonline.data.local.LastSeenStore
import com.example.chonline.data.local.TokenStore
import com.example.chonline.data.remote.CorpChatApi
import com.example.chonline.data.remote.createJson
import com.example.chonline.data.remote.createOkHttpClient
import com.example.chonline.data.remote.createRetrofit
import com.example.chonline.data.repo.AuthRepository
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.data.socket.ChatSocketController

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val tokenStore = TokenStore(appContext)
    val lastSeenStore = LastSeenStore(appContext)

    private val json = createJson()
    private val okHttp = createOkHttpClient(tokenStore, json)
    val api: CorpChatApi = createRetrofit(okHttp, json).create(CorpChatApi::class.java)

    val socketController = ChatSocketController(
        baseUrl = BuildConfig.API_BASE_URL,
        tokenStore = tokenStore,
        lastSeenStore = lastSeenStore,
    )

    val authRepository = AuthRepository(api, tokenStore)
    val chatRepository = ChatRepository(api, socketController, lastSeenStore, tokenStore, okHttp, json)

    val okHttpForImages = okHttp
}
