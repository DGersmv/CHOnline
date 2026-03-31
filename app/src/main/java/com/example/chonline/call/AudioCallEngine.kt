package com.example.chonline.call

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SdpObserver
import org.webrtc.audio.JavaAudioDeviceModule

class AudioCallEngine(
    context: Context,
    private val onLocalOffer: (String) -> Unit,
    private val onLocalAnswer: (String) -> Unit,
    private val onLocalIce: (String) -> Unit,
    private val onConnected: () -> Unit,
) {
    private val factory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private var pc: PeerConnection? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        val adm = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(null, false, false))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(null))
            .createPeerConnectionFactory()
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("audio0", audioSource)
    }

    fun createPeer() {
        if (pc != null) return
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()),
        )
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
                    if (state == PeerConnection.PeerConnectionState.CONNECTED) onConnected()
                }
                override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = Unit
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
        pc?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc?.setLocalDescription(SimpleSdpObserver(), desc)
                onLocalOffer(desc.description)
            }
        }, constraints)
    }

    fun onRemoteOffer(sdp: String) {
        createPeer()
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc?.setRemoteDescription(SimpleSdpObserver(), offer)
        pc?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc?.setLocalDescription(SimpleSdpObserver(), desc)
                onLocalAnswer(desc.description)
            }
        }, MediaConstraints())
    }

    fun onRemoteAnswer(sdp: String) {
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc?.setRemoteDescription(SimpleSdpObserver(), answer)
    }

    fun onRemoteIce(json: String) {
        runCatching {
            val p = JSONObject(json)
            val c = IceCandidate(
                p.optString("sdpMid"),
                p.optInt("sdpMLineIndex"),
                p.optString("candidate"),
            )
            pc?.addIceCandidate(c)
        }
    }

    fun close() {
        pc?.close()
        pc?.dispose()
        pc = null
        audioTrack.dispose()
        audioSource.dispose()
        factory.dispose()
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }
}

