package com.example.chonline.ui.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chonline.call.AudioCallEngine
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.data.socket.SocketEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CallUiState(
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val roomId: String = "",
    val incoming: Boolean = false,
    val status: String = "idle",
    val error: String? = null,
)

class CallViewModel(
    private val appContext: Context,
    private val repo: ChatRepository,
    private val initialCallId: String,
    private val roomId: String,
    private val peerId: String,
    private val peerName: String,
    private val incoming: Boolean,
    private val autoAccept: Boolean,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        CallUiState(
            callId = initialCallId,
            roomId = roomId,
            peerId = peerId,
            peerName = peerName,
            incoming = incoming,
            status = if (incoming) "incoming" else "dialing",
        ),
    )
    val ui = _ui.asStateFlow()

    private var engine: AudioCallEngine? = null
    private var callId: String = initialCallId
    private var micPermissionGranted = false

    init {
        viewModelScope.launch {
            repo.socketEvents.collect { e ->
                when (e) {
                    is SocketEvent.CallAccept -> if (e.callId == callId) {
                        if (!micPermissionGranted) return@collect
                        _ui.value = _ui.value.copy(status = "connecting")
                        runCatching { ensureEngine()?.startAsCaller() }
                            .onFailure { err ->
                                _ui.value = _ui.value.copy(status = "failed", error = err.message ?: "Ошибка звонка")
                            }
                    }
                    is SocketEvent.CallReject -> if (e.callId == callId) _ui.value = _ui.value.copy(status = "rejected")
                    is SocketEvent.CallMissed -> if (e.callId == callId) _ui.value = _ui.value.copy(status = "missed")
                    is SocketEvent.CallEnd -> if (e.callId == callId) _ui.value = _ui.value.copy(status = "ended")
                    is SocketEvent.CallOffer -> if (e.callId == callId) ensureEngine()?.onRemoteOffer(e.sdp)
                    is SocketEvent.CallAnswer -> if (e.callId == callId) ensureEngine()?.onRemoteAnswer(e.sdp)
                    is SocketEvent.CallIce -> if (e.callId == callId) ensureEngine()?.onRemoteIce(e.candidate)
                    else -> Unit
                }
            }
        }
        if (!incoming) {
            callId = repo.startCall(toUserId = peerId, roomId = roomId, mode = "audio")
            _ui.value = _ui.value.copy(callId = callId, status = "ringing")
        } else if (autoAccept) {
            if (micPermissionGranted) accept()
        }
    }

    fun accept() {
        if (!micPermissionGranted) {
            _ui.value = _ui.value.copy(status = "failed", error = "Нужен доступ к микрофону")
            return
        }
        if (callId.isBlank()) return
        repo.acceptCall(callId)
        _ui.value = _ui.value.copy(status = "connecting")
    }

    fun decline() {
        if (callId.isBlank()) return
        repo.rejectCall(callId)
        _ui.value = _ui.value.copy(status = "declined")
    }

    fun end() {
        if (callId.isBlank()) return
        repo.endCall(callId)
        _ui.value = _ui.value.copy(status = "ended")
    }

    fun setMicPermissionGranted(granted: Boolean) {
        micPermissionGranted = granted
        if (!granted) return
        if (!_ui.value.incoming && _ui.value.status == "ringing") {
            // Совместимость с веб-клиентом (legacy flow): отправляем offer после получения разрешения.
            runCatching { ensureEngine()?.startAsCaller() }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(status = "failed", error = err.message ?: "Ошибка звонка")
                }
        } else if (_ui.value.incoming && autoAccept && _ui.value.status == "incoming") {
            accept()
        }
    }

    private fun ensureEngine(): AudioCallEngine? {
        val existing = engine
        if (existing != null) return existing
        val created = runCatching {
            AudioCallEngine(
                context = appContext,
                onLocalOffer = { sdp -> repo.sendCallOffer(callId, peerId, sdp) },
                onLocalAnswer = { sdp -> repo.sendCallAnswer(callId, peerId, sdp) },
                onLocalIce = { c -> repo.sendCallIce(callId, peerId, c) },
                onConnected = {
                    _ui.value = _ui.value.copy(status = "connected")
                },
            )
        }.getOrElse { err ->
            _ui.value = _ui.value.copy(status = "failed", error = err.message ?: "Ошибка инициализации звонка")
            return null
        }
        engine = created
        return created
    }

    override fun onCleared() {
        engine?.close()
        super.onCleared()
    }
}

