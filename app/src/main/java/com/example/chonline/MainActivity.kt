package com.example.chonline



import android.Manifest

import android.content.pm.PackageManager

import android.os.Build

import android.os.Bundle

import androidx.activity.ComponentActivity

import androidx.activity.result.contract.ActivityResultContracts

import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.Surface

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.ui.Modifier

import androidx.core.content.ContextCompat

import androidx.lifecycle.lifecycleScope

import com.example.chonline.di.AppContainer

import com.example.chonline.push.PushTokenRegistrar

import com.example.chonline.ui.navigation.AppNavHost

import com.example.chonline.ui.theme.CHOnlineTheme

import com.example.chonline.ui.theme.CorpChatColors



class MainActivity : ComponentActivity() {



    private lateinit var appContainer: AppContainer



    /** Счётчик для Compose: обработать intent (cold start + onNewIntent), когда NavController уже на графе. */

    private val launchIntentKey = mutableIntStateOf(0)



    private val requestNotificationPermission =

        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->

            if (granted && ::appContainer.isInitialized) {

                PushTokenRegistrar.registerWithServer(appContainer, lifecycleScope)

            }

        }



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        appContainer = (application as CHOnlineApplication).appContainer

        askNotificationPermissionIfNeeded()

        enableEdgeToEdge()

        setContent {

            val intentRevision = launchIntentKey.intValue

            CHOnlineTheme {

                Surface(

                    modifier = Modifier.fillMaxSize(),

                    color = CorpChatColors.bgDeep,

                ) {

                    AppNavHost(

                        container = appContainer,

                        launchIntentKey = intentRevision,

                    )

                }

            }

        }

    }



    override fun onNewIntent(intent: android.content.Intent) {

        super.onNewIntent(intent)

        setIntent(intent)

        launchIntentKey.intValue++

    }



    private fun askNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (

            ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.POST_NOTIFICATIONS,

            ) == PackageManager.PERMISSION_GRANTED

        ) return

        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

    }

}

