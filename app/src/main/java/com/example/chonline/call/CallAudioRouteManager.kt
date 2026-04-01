package com.example.chonline.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

enum class CallAudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
}

class CallAudioRouteManager(
    context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var inCallSession = false

    fun startSession() {
        if (inCallSession) return
        inCallSession = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun endSession() {
        if (!inCallSession) return
        clearRouteHints()
        audioManager.mode = AudioManager.MODE_NORMAL
        inCallSession = false
    }

    fun availableRoutes(): List<CallAudioRoute> {
        val routes = mutableListOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER)
        if (isBluetoothAvailable()) {
            routes.add(CallAudioRoute.BLUETOOTH)
        }
        return routes.distinct()
    }

    fun currentRoute(): CallAudioRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val current = audioManager.communicationDevice
            if (current != null) {
                return when (current.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CallAudioRoute.SPEAKER
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    -> CallAudioRoute.BLUETOOTH
                    else -> CallAudioRoute.EARPIECE
                }
            }
        }
        if (audioManager.isSpeakerphoneOn) return CallAudioRoute.SPEAKER
        if (audioManager.isBluetoothScoOn) return CallAudioRoute.BLUETOOTH
        return CallAudioRoute.EARPIECE
    }

    fun selectRoute(route: CallAudioRoute): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            selectRouteApi31(route)
        } else {
            selectRouteLegacy(route)
        }
    }

    private fun selectRouteApi31(route: CallAudioRoute): Boolean {
        val device = when (route) {
            CallAudioRoute.SPEAKER ->
                audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
            CallAudioRoute.EARPIECE ->
                audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                    ?: audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
            CallAudioRoute.BLUETOOTH ->
                audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
        } ?: return false
        return audioManager.setCommunicationDevice(device)
    }

    private fun selectRouteLegacy(route: CallAudioRoute): Boolean {
        clearRouteHints()
        return when (route) {
            CallAudioRoute.SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
                true
            }
            CallAudioRoute.EARPIECE -> true
            CallAudioRoute.BLUETOOTH -> {
                if (!isBluetoothAvailable()) return false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                true
            }
        }
    }

    private fun clearRouteHints() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioManager.isSpeakerphoneOn = false
        audioManager.isBluetoothScoOn = false
        runCatching { audioManager.stopBluetoothSco() }
    }

    private fun isBluetoothAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        } else {
            audioManager.isBluetoothScoAvailableOffCall ||
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
        }
    }
}
