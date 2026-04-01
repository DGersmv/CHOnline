package com.example.chonline.call



import android.content.Context

import android.util.Log

import java.util.concurrent.atomic.AtomicBoolean

import org.json.JSONObject

import org.webrtc.AudioSource

import org.webrtc.AudioTrack

import org.webrtc.IceCandidate

import org.webrtc.MediaConstraints

import org.webrtc.PeerConnection

import org.webrtc.PeerConnectionFactory

import org.webrtc.SessionDescription

import org.webrtc.SdpObserver



/**

 * Один звонок = один экземпляр. [PeerConnectionFactory.initialize] здесь **не** вызывается —

 * только общая фабрика из [WebRtcEnvironment] (инициализация JNI один раз в [Application]).

 */

class AudioCallEngine(

    context: Context,

    private val onLocalOffer: (String) -> Unit,

    private val onLocalAnswer: (String) -> Unit,

    private val onLocalIce: (String) -> Unit,

    private val onConnected: () -> Unit,

    /** ICE/PeerConnection ушли в FAILED — один раз, чтобы UI не висел на «Подключение». */
    private val onConnectionFailed: () -> Unit = {},

) {

    private val factory: PeerConnectionFactory

    private val audioSource: AudioSource

    private val audioTrack: AudioTrack

    private var pc: PeerConnection? = null

    /** Remote answer пришёл до [startAsCaller] / до HAVE_LOCAL_OFFER (сокет доставил call:answer раньше call:accept). */

    private var pendingRemoteAnswerSdp: String? = null

    @Volatile

    private var mediaConnectedNotified = false

    private val failureReported = AtomicBoolean(false)

    private fun reportMediaFailure(reason: String) {
        Log.w(TAG, reason)
        if (failureReported.compareAndSet(false, true)) {
            onConnectionFailed()
        }
    }

    private fun notifyMediaConnectedOnce() {

        if (mediaConnectedNotified) return

        mediaConnectedNotified = true

        Log.d(TAG, "media up → UI connected")

        onConnected()

    }



    init {

        factory = WebRtcEnvironment.requireFactory()

        audioSource = factory.createAudioSource(MediaConstraints())

        audioTrack = factory.createAudioTrack("audio0", audioSource)

    }



    fun createPeer() {

        if (pc != null) return

        val iceServers = WebRtcIceServers.peerConnectionIceServers()
        Log.d(TAG, "RTCConfiguration iceServers=${iceServers.size} (STUN+TURN)")
        val rtcConfig =
            PeerConnection.RTCConfiguration(iceServers).apply {
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            }

        pc = factory.createPeerConnection(

            rtcConfig,

            object : PeerConnection.Observer {

                override fun onIceCandidate(candidate: IceCandidate) {

                    val p = JSONObject()

                        .put("candidate", candidate.sdp)

                        .put("sdpMid", candidate.sdpMid)

                        .put("sdpMLineIndex", candidate.sdpMLineIndex)

                    Log.d(TAG, "local ICE candidate sdpMid=${candidate.sdpMid} idx=${candidate.sdpMLineIndex}")

                    onLocalIce(p.toString())

                }

                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {

                    Log.d(TAG, "PeerConnectionState=$state")

                    if (state == PeerConnection.PeerConnectionState.CONNECTED) {

                        notifyMediaConnectedOnce()

                    }

                    if (state == PeerConnection.PeerConnectionState.FAILED) {
                        reportMediaFailure("PeerConnectionState=FAILED")
                    }
                    if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
                        Log.w(TAG, "PeerConnectionState=DISCONNECTED")
                    }

                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState) {

                    Log.d(TAG, "SignalingState=$newState")

                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {

                    Log.d(TAG, "IceConnectionState=$newState")

                    when (newState) {

                        PeerConnection.IceConnectionState.CONNECTED,

                        PeerConnection.IceConnectionState.COMPLETED,

                        -> notifyMediaConnectedOnce()

                        PeerConnection.IceConnectionState.FAILED ->
                            reportMediaFailure("IceConnectionState=FAILED (NAT/TURN/блокировка сети)")

                        else -> Unit

                    }

                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {

                    Log.d(TAG, "IceConnectionReceivingChange receiving=$receiving")

                }

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {

                    Log.d(TAG, "IceGatheringState=$newState")

                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

                override fun onAddStream(stream: org.webrtc.MediaStream) = Unit

                override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit

                override fun onDataChannel(dataChannel: org.webrtc.DataChannel) = Unit

                override fun onRenegotiationNeeded() {

                    Log.d(TAG, "onRenegotiationNeeded")

                }

                override fun onAddTrack(

                    receiver: org.webrtc.RtpReceiver,

                    mediaStreams: Array<out org.webrtc.MediaStream>,

                ) = Unit

                override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {

                    Log.d(TAG, "onTrack mid=${transceiver.mid}")

                }

            },

        )

        pc?.addTrack(audioTrack)

    }



    fun startAsCaller() {

        createPeer()

        val constraints = MediaConstraints()

        pc?.createOffer(

            object : LoggingSdpObs("createOffer") {

                override fun onCreateSuccess(desc: SessionDescription) {

                    Log.d(TAG, "createOffer success type=${desc.type}")

                    pc?.setLocalDescription(

                        object : LoggingSdpObs("setLocalDescription(offer)") {

                            override fun onSetSuccess() {

                                Log.d(TAG, "local offer set, send to peer")

                                onLocalOffer(desc.description)

                                flushPendingRemoteAnswer()

                            }

                        },

                        desc,

                    )

                }

            },

            constraints,

        )

    }



    fun onRemoteOffer(sdp: String) {

        createPeer()

        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)

        // createAnswer только после успешного setRemoteDescription(offer) — иначе «вечное подключение».

        pc?.setRemoteDescription(

            object : LoggingSdpObs("setRemoteDescription(offer)") {

                override fun onSetSuccess() {

                    Log.d(TAG, "remote offer applied → createAnswer")

                    pc?.createAnswer(

                        object : LoggingSdpObs("createAnswer") {

                            override fun onCreateSuccess(desc: SessionDescription) {

                                pc?.setLocalDescription(

                                    object : LoggingSdpObs("setLocalDescription(answer)") {

                                        override fun onSetSuccess() {

                                            Log.d(TAG, "local answer set, send to peer")

                                            onLocalAnswer(desc.description)

                                        }

                                    },

                                    desc,

                                )

                            }

                        },

                        MediaConstraints(),

                    )

                }

            },

            offer,

        )

    }



    fun onRemoteAnswer(sdp: String) {

        val connection = pc

        if (connection == null) {

            Log.d(TAG, "onRemoteAnswer: pc=null → buffer sdp.length=${sdp.length}")

            pendingRemoteAnswerSdp = sdp

            return

        }

        if (connection.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {

            Log.d(

                TAG,

                "onRemoteAnswer: signaling=${connection.signalingState()} → buffer sdp.length=${sdp.length}",

            )

            pendingRemoteAnswerSdp = sdp

            return

        }

        applyRemoteAnswer(sdp)

    }



    private fun flushPendingRemoteAnswer() {

        val pending = pendingRemoteAnswerSdp ?: return

        pendingRemoteAnswerSdp = null

        if (pc?.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {

            applyRemoteAnswer(pending)

        } else {

            Log.d(TAG, "flushPendingRemoteAnswer: defer (signaling=${pc?.signalingState()})")

            pendingRemoteAnswerSdp = pending

        }

    }



    private fun applyRemoteAnswer(sdp: String) {

        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)

        Log.d(TAG, "applyRemoteAnswer sdp.length=${sdp.length}")

        pc?.setRemoteDescription(

            object : LoggingSdpObs("setRemoteDescription(answer)") {

                override fun onSetSuccess() {

                    Log.d(TAG, "remote answer applied")

                }

            },

            answer,

        )

    }



    fun onRemoteIce(json: String) {

        val conn = pc

        if (conn == null) {

            Log.w(TAG, "onRemoteIce: pc=null (candidate ignored until peer exists)")

            return

        }

        runCatching {

            val p = JSONObject(json)

            val sdpMLineIndex = if (p.has("sdpMLineIndex")) p.getInt("sdpMLineIndex") else 0

            val c = IceCandidate(

                p.optString("sdpMid"),

                sdpMLineIndex,

                p.optString("candidate"),

            )

            val ok = conn.addIceCandidate(c)

            Log.d(TAG, "addIceCandidate ok=$ok sdpMid=${c.sdpMid} idx=${c.sdpMLineIndex}")

        }.onFailure { e ->

            Log.e(TAG, "onRemoteIce failed: ${e.message} json=${json.take(200)}")

        }

    }



    fun close() {

        pendingRemoteAnswerSdp = null

        val connection = pc

        pc = null

        connection?.dispose()

        audioTrack.setEnabled(false)

        audioTrack.dispose()

        audioSource.dispose()

    }



    private abstract class LoggingSdpObs(private val step: String) : SdpObserver {

        override fun onCreateSuccess(desc: SessionDescription) = Unit

        override fun onSetSuccess() = Unit

        override fun onCreateFailure(error: String) {

            Log.e(TAG, "$step onCreateFailure: $error")

        }

        override fun onSetFailure(error: String) {

            Log.e(TAG, "$step onSetFailure: $error")

        }

    }



    companion object {

        private const val TAG = "CallVM"

    }

}


