package com.example.chonline.ui.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private val autoAccept: Boolean,
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

    private val mainHandler = Handler(Looper.getMainLooper())

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
                pendingRemoteOfferSdp = b.offer.sdp
                Log.d(
                    "CallVM",
                    "consumeBufferedCallSignaling sdp.length=${b.offer.sdp.length} ice=${b.iceCandidates.size}",
                )
            }
            for (c in b.iceCandidates) {
                pendingIceCandidates.add(c)
            }
        }
        viewModelScope.launch {
            Log.d("CallVM", "init incoming=$incoming callId=$callId autoAccept=$autoAccept")
            repo.socketEvents.collect { e ->
                withContext(Dispatchers.Main) {
                    when (e) {
                        // ── Исходящий звонок: собеседник принял ──
                        is SocketEvent.CallAccept -> if (e.callId == callId) {
                            Log.d("CallVM", "CallAccept incoming=$incoming mic=$micPermissionGranted")
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
                            Log.d("CallVM", "CallEnd status=${e.status}")
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
                            Log.d(
                                "CallVM",
                                "CallOffer received sdp.length=${e.sdp.length} acceptedButWaiting=$acceptedButWaitingOffer",
                            )
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
                            Log.d(
                                "CallVM",
                                "CallAnswer sdp.length=${e.sdp.length} incoming=$incoming " +
                                    "(если раньше CallAccept — answer буферизуется в AudioCallEngine)",
                            )
                            val eng = ensureEngine()
                            eng?.onRemoteAnswer(e.sdp)
                            if (eng != null) drainPendingIce(eng)
                        }

                        // ── ICE-кандидаты ──
                        is SocketEvent.CallIce -> if (e.callId == callId) {
                            Log.d("CallVM", "CallIce received engine=${engine != null}")
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
            Log.d("CallVM", "outgoing startCall callId=$callId peerId=$peerId")
            _ui.value = _ui.value.copy(callId = callId, status = "ringing")
        } else if (autoAccept) {
            if (micPermissionGranted) accept()
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
        Log.d("CallVM", "accept() mic=$micPermissionGranted pendingSdp=${pendingRemoteOfferSdp != null}")
        Log.d("CallFlow", "accept pressed callId=$callId incoming=$incoming")
        if (!micPermissionGranted) {
            _ui.value = _ui.value.copy(status = "failed", error = "Нужен доступ к микрофону")
            return
        }
        if (callId.isBlank()) return
        viewModelScope.launch {
            val socketReady = repo.awaitSocketConnected(15_000)
            if (!socketReady) {
                Log.e("CallFlow", "accept aborted: socket not connected after timeout callId=$callId")
                _ui.value =
                    _ui.value.copy(
                        status = "failed",
                        error = "Нет соединения с сервером. Повторите звонок.",
                    )
                return@launch
            }
            Log.d("CallFlow", "socket ready before call:accept callId=$callId")
            repo.acceptCall(callId, peerId)
            Log.d("CallFlow", "emit call:accept callId=$callId toUserId=$peerId")
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
                Log.d("CallFlow", "waiting remote offer after accept callId=$callId")
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
        } else if (_ui.value.incoming && autoAccept && _ui.value.status == "incoming") {
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
        Log.d("CallVM", "applyRemoteOffer sdp.length=${sdp.length}")
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
            Log.w("CallFlow", "offer timeout after accept callId=$callId")
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
                    Log.w("CallFlow", "retry: socket down → ensureConnected (без полного сброса)")
                    repo.connectSocket()
                }
                Log.d("CallFlow", "retry emit call:accept callId=$callId")
                repo.acceptCall(callId, peerId)
            }
        }
    }

    private fun cancelAcceptRetry() {
        acceptRetryJob?.cancel()
        acceptRetryJob = null
    }

    /** Применить буферизованные ICE-кандидаты. */
    private fun drainPendingIce(eng: AudioCallEngine) {
        val n = pendingIceCandidates.size
        if (n > 0) Log.d("CallVM", "drainPendingIce count=$n")
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
                onLocalOffer = { sdp -> repo.sendCallOffer(callId, peerId, sdp) },
                onLocalAnswer = { sdp -> repo.sendCallAnswer(callId, peerId, sdp) },
                onLocalIce = { c -> repo.sendCallIce(callId, peerId, c) },
                onConnected = {
                    mainHandler.post {
                        Log.d("CallVM", "onConnected callback callId=$callId incoming=$incoming")
                        _ui.value = _ui.value.copy(status = "connected")
                    }
                },
                onConnectionFailed = {
                    mainHandler.post {
                        val s = _ui.value.status
                        if (s == "connected" || s == "ended" || s == "failed" || s == "declined") return@post
                        Log.w("CallVM", "onConnectionFailed → UI failed callId=$callId")
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