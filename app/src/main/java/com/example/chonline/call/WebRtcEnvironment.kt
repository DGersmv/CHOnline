package com.example.chonline.call

import android.app.Application
import android.os.Looper
import com.example.chonline.BuildConfig
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.NetworkMonitor
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Один [PeerConnectionFactory] на процесс.
 * - [PeerConnectionFactory.initialize] вызывается **только здесь**, один раз за процесс (повторный вызов
 *   в том же процессе даёт fatal error в jni/jvm.cc). Не вызывайте initialize из [AudioCallEngine] и т.п.
 * - [init] вызывается из [com.example.chonline.CHOnlineApplication.onCreate] на main thread.
 * - [PeerConnectionFactory.dispose] не вызываем при завершении звонка.
 * - [NetworkMonitor.startMonitoring] на main с [Application] до [PeerConnectionFactory]: иначе при первом
 *   [PeerConnection] native запускает мониторинг с [network_thread], [NetworkMonitorAutoDetect] дергает
 *   [ConnectivityManager] и возможен SIGABRT в jni/jvm.cc (часто на новых версиях Android).
 */
internal object WebRtcEnvironment {
    private val lock = Any()
    @Volatile
    private var jniInitialized = false
    @Volatile
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null

    fun init(app: Application) {
        if (BuildConfig.DEBUG) {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "WebRtcEnvironment.init must run on the main thread"
            }
        }
        synchronized(lock) {
            if (!jniInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions(),
                )
                // Пустой deprecated NetworkMonitor.init() в WebRTC не используем — нужен startMonitoring с контекстом.
                NetworkMonitor.getInstance().startMonitoring(app, "")
                jniInitialized = true
            }
            if (factory != null) return
            val egl = EglBase.create()
            eglBase = egl
            val adm = JavaAudioDeviceModule.builder(app)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, false, false))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
                .createPeerConnectionFactory()
        }
    }

    fun requireFactory(): PeerConnectionFactory {
        synchronized(lock) {
            return factory ?: error("WebRtcEnvironment.init(Application) was not called")
        }
    }
}
