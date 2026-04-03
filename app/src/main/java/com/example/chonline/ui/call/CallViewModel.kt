package com.example.chonline.ui.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chonline.call.CallAudioRoute
import com.example.chonline.call.CallAudioRouteManager
import com.example.chonline.call.AudioCallEngine
import com.example.chonline.call.IncomingCallNotifier
import com.example.chonline.data.repo.ChatRepository
import com.example.chonline.data.socket.SocketEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CallUiState(
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val roomId: String = "",
    val incoming: Boolean = false,
    val status: String = "idle",
    val error: String? = null,
    val availableRoutes: List<CallAudioRoute> = emptyList(),
    val selectedRoute: CallAudioRoute? = null,
)

class CallViewModel(
    private val appContext: Context,
    private val repo: ChatRepository,
    private val initialCallId: String,
    private val roomId: String,
    private val peerId: String,
    private val peerName: String,
    private val incoming: Boolean,
) : ViewModel() {
    private val audioRouteManager = CallAudioRouteManager(appContext)
    private val _ui = MutableStateFlow(
        CallUiState(
            callId = initialCallId,
            roomId = roomId,
            peerId = peerId,
            peerName = peerName,
            incoming = incoming,
            status = if (incoming) "incoming" else "dialing",
            availableRoutes = audioRouteManager.availableRoutes(),
            selectedRoute = audioRouteManager.currentRoute(),
        ),
    )
    val ui = _ui.asStateFlow()

    private var engine: AudioCallEngine? = null
    private var callId: String = initialCallId
    private var micPermissionGranted = false

    /** Маршрут .../1/1 (ответ из уведомления) — подставляется без смены ViewModel, см. [syncIncomingRouteAutoAccept]. */
    private var pendingRouteAutoAccept = false

    /**
     * Адресат call:answer / ICE. В push иногда пустой fromUserId — тогда берём fromUserId из call:offer
     * (без toUserId сервер отбрасывает answer → вечное «Подключение»).
     */
    private var signalingPeerId: String = peerId

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun signalingToUserId(): String = signalingPeerId.ifBlank { peerId }

    private fun resolveSignalingPeerIfNeeded(remoteUserId: String) {
        if (remoteUserId.isBlank()) return
        if (signalingPeerId.isNotBlank()) return
        signalingPeerId = remoteUserId
        _ui.value = _ui.value.copy(peerId = signalingPeerId)
    }

    /**
     * SDP offer от звонящего (Web), приходит в call:offer ещё до того как
     * пользователь нажмёт «Ответить». Сохраняем и применяем при accept().
     */
    private var pendingRemoteOfferSdp: String? = null

    /**
     * ICE-кандидаты от звонящего, пришедшие до создания PeerConnection.
     * Применяются после onRemoteOffer / onRemoteAnswer.
     */
    private val pendingIceCandidates = mutableListOf<String>()

    /**
     * Флаг: пользователь уже нажал «Ответить», но SDP offer ещё не пришёл.
     * Когда offer придёт — сразу применим.
     */
    private var acceptedButWaitingOffer = false
    private var waitingOfferTimeoutJob: Job? = null
    private var acceptRetryJob: Job? = null

    /** Исходящий: offer создаём один раз (иначе второй call:offer рвёт звонок на web). */
    private var callerOfferStarted = false

    init {
        if (incoming && callId.isNotBlank()) {
            val b = repo.consumeBufferedCallSignaling(callId)
            if (b.offer != null) {
                resolveSignalingPeerIfNeeded(b.offer.fromUserId)
                pendingRemoteOfferSdp = b.offer.sdp
            }
            for (c in b.iceCandidates) {
                pendingIceCandidates.add(c)
            }
        }
        viewModelScope.launch {
            repo.socketEvents.collect { e ->
                withContext(Dispatchers.Main) {
                    when (e) {
                        // ── Исходящий звонок: собеседник принял ──
                        is SocketEvent.CallAccept -> if (e.callId == callId) {
                            if (!incoming) {
                                // Мы — caller: offer уже шлём из setMicPermissionGranted (ringing) или отсюда один раз
                                if (!micPermissionGranted) return@withContext
                                val currentStatus = _ui.value.status
                                if (
                                    currentStatus != "connected" &&
                                    currentStatus != "ended" &&
                                    currentStatus != "failed" &&
                                    currentStatus != "declined"
                                ) {
                                    _ui.value = _ui.value.copy(status = "connecting")
                                }
                                startCallerOfferIfNeeded()
                            }
                            // Для incoming — CallAccept приходит как эхо нашего accept, игнорируем
                        }

                        is SocketEvent.CallReject -> if (e.callId == callId) {
                            cancelWaitingOfferTimeout()
                            cancelAcceptRetry()
                            IncomingCallNotifier.cancel(appContext, callId)
                            _ui.value = _ui.value.copy(status = "rejected")
                        }

                        is SocketEvent.CallMissed -> if (e.callId == callId) {
                            cancelWaitingOfferTimeout()
                            cancelAcceptRetry()
                            IncomingCallNotifier.cancel(appContext, callId)
                            _ui.value = _ui.value.copy(status = "missed")
                        }

                        is SocketEvent.CallEnd -> if (e.callId == callId) {
                            audioRouteManager.endSession()
                            cancelWaitingOfferTimeout()
                            cancelAcceptRetry()
                            IncomingCallNotifier.cancel(appContext, callId)
                            val terminalStatus = when (e.status.lowercase()) {
                                "cancelled" -> "missed"
                                "missed" -> "missed"
                                "declined", "rejected" -> "rejected"
                                "failed" -> "failed"
                                else -> "ended"
                            }
                            _ui.value = _ui.value.copy(status = terminalStatus)
                        }

                        // ── Входящий SDP offer от звонящего ──
                        is SocketEvent.CallOffer -> if (e.callId == callId) {
                            resolveSignalingPeerIfNeeded(e.fromUserId)
                            if (acceptedButWaitingOffer) {
                                // Пользователь уже нажал «Ответить», offer пришёл — применяем
                                acceptedButWaitingOffer = false
                                cancelWaitingOfferTimeout()
                                cancelAcceptRetry()
                                applyRemoteOffer(e.sdp)
                            } else {
                                // Сохраняем SDP — применим при accept()
                                pendingRemoteOfferSdp = e.sdp
                            }
                        }

                        // ── Ответ на наш offer (исходящий звонок) ──
                        is SocketEvent.CallAnswer -> if (e.callId == callId) {
                            val eng = ensureEngine()
                            eng?.onRemoteAnswer(e.sdp)
                            if (eng != null) drainPendingIce(eng)
                        }

                        // ── ICE-кандидаты ──
                        is SocketEvent.CallIce -> if (e.callId == callId) {
                            resolveSignalingPeerIfNeeded(e.fromUserId)
                            val eng = engine
                            if (eng != null) {
                                eng.onRemoteIce(e.candidate)
                            } else {
                                // PeerConnection ещё не создан — буферизуем
                                pendingIceCandidates.add(e.candidate)
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
        if (!incoming) {
            callId = repo.startCall(toUserId = peerId, roomId = roomId, mode = "audio")
            _ui.value = _ui.value.copy(callId = callId, status = "ringing")
        }
    }

    /**
     * Один [CallViewModel] на [callId]: при смене маршрута 1/0 → 1/1 без нового экземпляра
     * (иначе второй init снова вызывает consumeBufferedCallSignaling и теряет offer/ICE).
     */
    fun syncIncomingRouteAutoAccept(wantAutoAccept: Boolean) {
        if (!incoming) return
        pendingRouteAutoAccept = wantAutoAccept
        if (wantAutoAccept && micPermissionGranted) {
            accept()
        }
    }

    /**
     * Принять входящий звонок.
     *
     * Шлём call:accept на сервер. Если SDP offer от Web уже пришёл —
     * создаём engine и вызываем onRemoteOffer (engine создаст answer).
     * Если SDP ещё не пришёл — ставим флаг и ждём CallOffer event.
     */
    fun accept() {
        if (incoming) {
            when (_ui.value.status) {
                "connecting", "connected" -> {
                    return
                }
                else -> Unit
            }
        }
        if (!micPermissionGranted) {
            _ui.value = _ui.value.copy(status = "failed", error = "Нужен доступ к микрофону")
            return
        }
        if (callId.isBlank()) return
        viewModelScope.launch {
            val socketReady = repo.awaitSocketConnected(15_000)
            if (!socketReady) {
                _ui.value =
                    _ui.value.copy(
                        status = "failed",
                        error = "Нет соединения с сервером. Повторите звонок.",
                    )
                return@launch
            }
            repo.acceptCall(callId, signalingToUserId().takeIf { it.isNotBlank() })
            _ui.value = _ui.value.copy(status = "connecting")

            val sdp = pendingRemoteOfferSdp
            if (sdp != null) {
                pendingRemoteOfferSdp = null
                cancelWaitingOfferTimeout()
                cancelAcceptRetry()
                applyRemoteOffer(sdp)
            } else {
                val buffered = repo.consumeBufferedCallSignaling(callId)
                buffered.offer?.let { off ->
                    pendingRemoteOfferSdp = null
                    for (c in buffered.iceCandidates) pendingIceCandidates.add(c)
                    cancelWaitingOfferTimeout()
                    cancelAcceptRetry()
                    applyRemoteOffer(off.sdp)
                    return@launch
                }
                for (c in buffered.iceCandidates) pendingIceCandidates.add(c)
                acceptedButWaitingOffer = true
                startAcceptRetry()
                startWaitingOfferTimeout()
            }
        }
    }

    fun decline() {
        if (callId.isBlank()) return
        repo.rejectCall(callId)
        IncomingCallNotifier.cancel(appContext, callId)
        audioRouteManager.endSession()
        _ui.value = _ui.value.copy(status = "declined")
    }

    fun end() {
        if (callId.isBlank()) return
        repo.endCall(callId)
        IncomingCallNotifier.cancel(appContext, callId)
        audioRouteManager.endSession()
        _ui.value = _ui.value.copy(status = "ended")
    }

    fun setMicPermissionGranted(granted: Boolean) {
        micPermissionGranted = granted
        if (!granted) return
        audioRouteManager.startSession()
        refreshAudioRoutes()
        // Входящий звонок: не создаём answer до explicit accept().
        // Иначе web-сторона может остаться в состоянии "подключение".
        if (!_ui.value.incoming && _ui.value.status == "ringing") {
            viewModelScope.launch(Dispatchers.Main) {
                startCallerOfferIfNeeded()
            }
        } else if (_ui.value.incoming && pendingRouteAutoAccept && _ui.value.status == "incoming") {
            accept()
        }
    }

    fun selectAudioRoute(route: CallAudioRoute) {
        if (audioRouteManager.selectRoute(route)) {
            refreshAudioRoutes()
        }
    }

    private fun refreshAudioRoutes() {
        _ui.value =
            _ui.value.copy(
                availableRoutes = audioRouteManager.availableRoutes(),
                selectedRoute = audioRouteManager.currentRoute(),
            )
    }

    /** Один SDP offer на исходящий звонок (дубль ломает callee в браузере). */
    private fun startCallerOfferIfNeeded() {
        if (incoming || callerOfferStarted || !micPermissionGranted) return
        callerOfferStarted = true
        val eng = ensureEngine()
        if (eng == null) {
            callerOfferStarted = false
            return
        }
        runCatching { eng.startAsCaller() }
            .onFailure { err ->
                callerOfferStarted = false
                _ui.value = _ui.value.copy(status = "failed", error = err.message ?: "Ошибка звонка")
            }
    }

    /**
     * Применить remote offer SDP: создать engine → PeerConnection → setRemoteDescription → createAnswer.
     */
    private fun applyRemoteOffer(sdp: String) {
        cancelWaitingOfferTimeout()
        val eng = ensureEngine() ?: return
        eng.onRemoteOffer(sdp)
        drainPendingIce(eng)
    }

    private fun startWaitingOfferTimeout() {
        cancelWaitingOfferTimeout()
        waitingOfferTimeoutJob = viewModelScope.launch(Dispatchers.Main) {
            delay(12_000)
            if (!acceptedButWaitingOffer) return@launch
            acceptedButWaitingOffer = false
            cancelAcceptRetry()
            audioRouteManager.endSession()
            runCatching { repo.endCall(callId) }
            _ui.value = _ui.value.copy(
                status = "failed",
                error = "Не получили SDP от собеседника. Попробуйте перезвонить.",
            )
        }
    }

    private fun cancelWaitingOfferTimeout() {
        waitingOfferTimeoutJob?.cancel()
        waitingOfferTimeoutJob = null
    }

    private fun startAcceptRetry() {
        cancelAcceptRetry()
        acceptRetryJob = viewModelScope.launch(Dispatchers.Main) {
            while (acceptedButWaitingOffer) {
                delay(2_000)
                if (!acceptedButWaitingOffer) break
                if (!repo.isSocketConnected()) {
                    repo.connectSocket()
                }
                repo.acceptCall(callId, signalingToUserId().takeIf { it.isNotBlank() })
            }
        }
    }

    private fun cancelAcceptRetry() {
        acceptRetryJob?.cancel()
        acceptRetryJob = null
    }

    /** Применить буферизованные ICE-кандидаты. */
    private fun drainPendingIce(eng: AudioCallEngine) {
        for (c in pendingIceCandidates) {
            eng.onRemoteIce(c)
        }
        pendingIceCandidates.clear()
    }

    private fun ensureEngine(): AudioCallEngine? {
        val existing = engine
        if (existing != null) return existing
        val created = runCatching {
            AudioCallEngine(
                context = appContext,
                onLocalOffer = { sdp -> repo.sendCallOffer(callId, signalingToUserId(), sdp) },
                onLocalAnswer = { sdp -> repo.sendCallAnswer(callId, signalingToUserId(), sdp) },
                onLocalIce = { c -> repo.sendCallIce(callId, signalingToUserId(), c) },
                onConnected = {
                    mainHandler.post {
                        _ui.value = _ui.value.copy(status = "connected")
                    }
                },
                onConnectionFailed = {
                    mainHandler.post {
                        val s = _ui.value.status
                        if (s == "connected" || s == "ended" || s == "failed" || s == "declined") return@post
                        runCatching { repo.endCall(callId) }
                        _ui.value =
                            _ui.value.copy(
                                status = "failed",
                                error = "Аудио не соединилось (сеть/NAT). Нужен свой TURN или другая сеть Wi‑Fi/4G.",
                            )
                    }
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
        val e = engine
        engine = null
        cancelWaitingOfferTimeout()
        cancelAcceptRetry()
        if (e != null) {
            mainHandler.post { e.close() }
        }
        audioRouteManager.endSession()
        super.onCleared()
    }
}