package com.example.chonline

import android.app.Application
import android.os.Bundle
import com.example.chonline.call.WebRtcEnvironment
import com.example.chonline.ui.navigation.AppRuntimeState
import ru.rustore.sdk.pushclient.RuStorePushClient

/**
 * WebRTC: [PeerConnectionFactory.initialize] вызывается только внутри [WebRtcEnvironment.init] (один раз за процесс).
 */
class CHOnlineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.RUSTORE_PUSH_PROJECT_ID.isNotBlank()) {
            RuStorePushClient.init(
                application = this,
                projectId = BuildConfig.RUSTORE_PUSH_PROJECT_ID,
            )
        }
        WebRtcEnvironment.init(this)
        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var startedCount = 0

                override fun onActivityStarted(activity: android.app.Activity) {
                    startedCount++
                    AppRuntimeState.setForeground(startedCount > 0)
                }

                override fun onActivityStopped(activity: android.app.Activity) {
                    startedCount = (startedCount - 1).coerceAtLeast(0)
                    AppRuntimeState.setForeground(startedCount > 0)
                }

                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: android.app.Activity) = Unit
                override fun onActivityPaused(activity: android.app.Activity) = Unit
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: android.app.Activity) = Unit
            },
        )
    }
}
