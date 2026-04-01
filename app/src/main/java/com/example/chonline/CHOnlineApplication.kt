package com.example.chonline

import android.app.Application
import com.example.chonline.call.WebRtcEnvironment

/**
 * WebRTC: [PeerConnectionFactory.initialize] вызывается только внутри [WebRtcEnvironment.init] (один раз за процесс).
 */
class CHOnlineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebRtcEnvironment.init(this)
    }
}
