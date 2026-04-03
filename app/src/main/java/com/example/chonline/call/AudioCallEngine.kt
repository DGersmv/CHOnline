package com.example.chonline.call



import android.content.Context

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

    private fun reportMediaFailure() {
        if (failureReported.compareAndSet(false, true)) {
            onConnectionFailed()
        }
    }

    private fun notifyMediaConnectedOnce() {

        if (mediaConnectedNotified) return

        mediaConnectedNotified = true

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

                    onLocalIce(p.toString())

                }

                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {

                    if (state == PeerConnection.PeerConnectionState.CONNECTED) {

                        notifyMediaConnectedOnce()

                    }

                    if (state == PeerConnection.PeerConnectionState.FAILED) {
                        reportMediaFailure()
                    }

                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {

                    when (newState) {

                        PeerConnection.IceConnectionState.CONNECTED,

                        PeerConnection.IceConnectionState.COMPLETED,

                        -> notifyMediaConnectedOnce()

                        PeerConnection.IceConnectionState.FAILED ->
                            reportMediaFailure()

                        else -> Unit

                    }

                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

                override fun onAddStream(stream: org.webrtc.MediaStream) = Unit

                override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit

                override fun onDataChannel(dataChannel: org.webrtc.DataChannel) = Unit

                override fun onRenegotiationNeeded() = Unit

                override fun onAddTrack(

                    receiver: org.webrtc.RtpReceiver,

                    mediaStreams: Array<out org.webrtc.MediaStream>,

                ) = Unit

                override fun onTrack(transceiver: org.webrtc.RtpTransceiver) = Unit

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

                    pc?.setLocalDescription(

                        object : LoggingSdpObs("setLocalDescription(offer)") {

                            override fun onSetSuccess() {

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

                    pc?.createAnswer(

                        object : LoggingSdpObs("createAnswer") {

                            override fun onCreateSuccess(desc: SessionDescription) {

                                pc?.setLocalDescription(

                                    object : LoggingSdpObs("setLocalDescription(answer)") {

                                        override fun onSetSuccess() {

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

            pendingRemoteAnswerSdp = sdp

            return

        }

        if (connection.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {

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

            pendingRemoteAnswerSdp = pending

        }

    }



    private fun applyRemoteAnswer(sdp: String) {

        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)

        pc?.setRemoteDescription(

            object : LoggingSdpObs("setRemoteDescription(answer)") {

                override fun onSetSuccess() = Unit

            },

            answer,

        )

    }



    fun onRemoteIce(json: String) {

        val conn = pc

        if (conn == null) {

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

            conn.addIceCandidate(c)

        }.onFailure { }

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

        override fun onCreateFailure(error: String) = Unit

        override fun onSetFailure(error: String) = Unit

    }



}



