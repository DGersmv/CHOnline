package com.example.chonline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.chonline.di.AppContainer
import com.example.chonline.call.CallCommand
import com.example.chonline.call.CallCoordinator
import com.example.chonline.call.CallNotificationParser
import com.example.chonline.ui.navigation.AppNavHost
import com.example.chonline.ui.theme.CHOnlineTheme
import com.example.chonline.ui.theme.CorpChatColors
import ru.rustore.sdk.pushclient.RuStorePushClient

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)
        if (BuildConfig.RUSTORE_PUSH_PROJECT_ID.isNotBlank()) {
            RuStorePushClient.init(
                application = application,
                projectId = BuildConfig.RUSTORE_PUSH_PROJECT_ID,
            )
        }
        handleCallIntent(intent)
        askNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            CHOnlineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CorpChatColors.bgDeep,
                ) {
                    AppNavHost(container = appContainer)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
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

    private fun handleCallIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val invite = CallNotificationParser.readInvite(intent) ?: return
        when (intent.getStringExtra(CallNotificationParser.EXTRA_ACTION).orEmpty()) {
            CallNotificationParser.ACTION_ACCEPT ->
                CallCoordinator.submit(CallCommand.Accept(invite))
            CallNotificationParser.ACTION_DECLINE ->
                CallCoordinator.submit(CallCommand.Decline(invite))
            else ->
                CallCoordinator.submit(CallCommand.IncomingInvite(invite))
        }
    }
}
