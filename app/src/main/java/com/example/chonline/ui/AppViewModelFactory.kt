package com.example.chonline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.chonline.di.AppContainer
import com.example.chonline.ui.auth.AuthViewModel
import com.example.chonline.ui.profile.ProfileViewModel
import com.example.chonline.ui.rooms.RoomsViewModel

@Suppress("UNCHECKED_CAST")
class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(container.authRepository) as T

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(container.authRepository) as T

            modelClass.isAssignableFrom(RoomsViewModel::class.java) ->
                RoomsViewModel(container.chatRepository) as T

            else -> throw IllegalArgumentException("Unknown VM ${modelClass.name}")
        }
    }
}
