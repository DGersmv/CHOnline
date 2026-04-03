package com.example.chonline.push

import com.example.chonline.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.rustore.sdk.pushclient.RuStorePushClient

/**
 * Получение Ru Store token и отправка на API [com.example.chonline.data.repo.AuthRepository.registerPushToken].
 */
object PushTokenRegistrar {

    fun registerWithServer(container: AppContainer, scope: CoroutineScope) {
        RuStorePushClient.getToken()
            .addOnSuccessListener { token ->
                if (token.isBlank()) {
                    return@addOnSuccessListener
                }
                scope.launch {
                    container.authRepository.registerPushToken(token).fold(
                        onSuccess = { },
                        onFailure = { },
                    )
                }
            }
            .addOnFailureListener { }
    }
}
