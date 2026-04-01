package com.example.chonline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.example.chonline.call.IncomingCallNotifier
import com.example.chonline.call.CallNotificationParser
import com.example.chonline.ui.navigation.NotificationNavigationCoordinator
import com.example.chonline.ui.navigation.OpenChatCommand
import com.example.chonline.ui.navigation.AppNavHost
import com.example.chonline.ui.theme.CHOnlineTheme
import com.example.chonline.ui.theme.CorpChatColors

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)
        handleLaunchIntent(intent)
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
        handleLaunchIntent(intent)
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

    private fun handleLaunchIntent(intent: android.content.Intent?) {
        if (intent == null) return
        Log.d(
            "CallFlow",
            "handleLaunchIntent action=${intent.action} hasCallId=${intent.hasExtra(CallNotificationParser.EXTRA_CALL_ID)}",
        )
        if (intent.action == com.example.chonline.push.PushMessageNotifier.ACTION_OPEN_MESSAGE) {
            val roomId = intent.getStringExtra(com.example.chonline.push.PushMessageNotifier.EXTRA_ROOM_ID).orEmpty()
            if (roomId.isNotBlank()) {
                val messageId =
                    intent.getStringExtra(com.example.chonline.push.PushMessageNotifier.EXTRA_MESSAGE_ID)
                        .orEmpty()
                        .ifBlank { null }
                NotificationNavigationCoordinator.submitOpenChat(
                    OpenChatCommand(roomId = roomId, messageId = messageId),
                )
            }
            return
        }
        val invite = CallNotificationParser.readInvite(intent) ?: return
        Log.d(
            "CallFlow",
            "launchInvite action=${intent.getStringExtra(CallNotificationParser.EXTRA_ACTION)} callId=${invite.callId} roomId=${invite.roomId}",
        )
        IncomingCallNotifier.cancel(this, invite.callId)
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
