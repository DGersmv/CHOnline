package com.example.chonline

import android.app.Application
import android.os.Bundle
import com.example.chonline.call.WebRtcEnvironment
import com.example.chonline.di.AppContainer
import com.example.chonline.notify.AppNotificationChannels
import com.example.chonline.ui.navigation.AppRuntimeState

/**
 * WebRTC: PeerConnectionFactory инициализируется внутри WebRtcEnvironment.init (один раз за процесс).
 *
 * Ru Store Push: project id подставляется в манифест (meta-data ru.rustore.sdk.pushclient.project_id) из local.properties.
 * Ручной RuStorePushClient.init в Application не дублируем — см. документацию Ru Store (авто-инициализация).
 */
class CHOnlineApplication : Application() {

    /** Один экземпляр на процесс: иначе при пересоздании MainActivity теряется буфер SDP/ICE сокета. */
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        AppNotificationChannels.registerAll(this)
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
