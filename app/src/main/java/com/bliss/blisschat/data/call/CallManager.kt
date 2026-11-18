package com.bliss.blisschat.data.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.bliss.blisschat.data.FirebaseConfig
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.data.crypto.E2EE
import com.bliss.blisschat.data.toUserProfile
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

enum class CallPhase { IDLE, OUTGOING, INCOMING, CONNECTING, ACTIVE }

data class CallUi(
    val phase: CallPhase = CallPhase.IDLE,
    val peer: UserProfile? = null,
    val video: Boolean = false,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val cameraOn: Boolean = true,
    val since: Long = 0L,
    val localTrack: VideoTrack? = null,
    val remoteTrack: VideoTrack? = null
)

/**
 * Real-time audio / video calls over WebRTC.
 *
 * Media travels peer-to-peer, encrypted with DTLS-SRTP. The signalling messages (SDP offer / answer, which carry
 * the DTLS fingerprints, and every ICE candidate) are additionally encrypted with the end-to-end key of the
 * chat. A rogue server therefore can neither read them nor swap the fingerprints to sit in the middle of a call.
 */
class CallManager(private val context: Context, private val e2ee: E2EE) {

    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _ui = MutableStateFlow(CallUi())
    val ui: StateFlow<CallUi> = _ui.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val egl: EglBase by lazy { EglBase.create() }
    private var factory: PeerConnectionFactory? = null

    private var me: String = ""
    private var callId = ""
    private var chatId = ""
    private var callKey: ByteArray? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: VideoCapturer? = null
    private var helper: SurfaceTextureHelper? = null
    private var remoteSet = false
    private val pending = mutableListOf<IceCandidate>()
    private var incomingReg: ListenerRegistration? = null
    private var callReg: ListenerRegistration? = null
    private var candReg: ListenerRegistration? = null
    private var timeout: Job? = null
    private var savedMode = AudioManager.MODE_NORMAL
    private var offerCt = ""
    private var kidA = ""
    private var kidB = ""

    /* ------------------------------------------------------------------ incoming */

    fun listen(uid: String) {
        if (me == uid && incomingReg != null) return
        stopListening()
        me = uid
        incomingReg = db.collection("calls").whereEqualTo("calleeId", uid).whereEqualTo("status", "ringing")
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { change ->
                    val d = change.document
                    val created = d.getLong("createdAt") ?: 0L
                    if (System.currentTimeMillis() - created > 40_000L) return@forEach
                    if (_ui.value.phase != CallPhase.IDLE) {
                        d.reference.update("status", "busy"); return@forEach
                    }
                    scope.launch {
                        val peer = runCatching {
                            db.collection("users").document(d.getString("callerId").orEmpty()).get().await().toUserProfile()
                        }.getOrNull() ?: return@launch
                        callId = d.id
                        chatId = d.getString("chatId").orEmpty()
                        offerCt = d.getString("offer").orEmpty()
                        kidA = d.getString("a").orEmpty()
                        kidB = d.getString("b").orEmpty()
                        _ui.value = CallUi(CallPhase.INCOMING, peer, d.getBoolean("video") ?: false)
                        watchCallDoc()
                        timeout = scope.launch { delay(40_000); if (_ui.value.phase == CallPhase.INCOMING) decline() }
                    }
                }
            }
    }

    fun stopListening() {
        incomingReg?.remove(); incomingReg = null
        if (_ui.value.phase != CallPhase.IDLE) hangup()
    }

    /* ------------------------------------------------------------------ actions */

    fun hasPermissions(video: Boolean): Boolean {
        val needed = buildList { add(Manifest.permission.RECORD_AUDIO); if (video) add(Manifest.permission.CAMERA) }
        return needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun startCall(chatId: String, peer: UserProfile, video: Boolean) {
        if (_ui.value.phase != CallPhase.IDLE || me.isEmpty()) return
        if (!hasPermissions(video)) { _events.tryEmit("Нет разрешения на микрофон/камеру"); return }
        scope.launch {
            try {
                this@CallManager.chatId = chatId
                callId = UUID.randomUUID().toString()
                val mine = e2ee.localKey(me) ?: error("Нет ключа шифрования")
                kidA = mine.kid
                kidB = e2ee.currentKid(peer.uid)
                callKey = e2ee.pairKey(me, kidA, peer.uid, kidB, chatId)
                _ui.value = CallUi(CallPhase.OUTGOING, peer, video, speaker = video)
                beginAudio(video)
                buildPeer()
                attachLocalMedia(video)
                val offer = createSdp { pc!!.createOffer(it, MediaConstraints()) }
                setSdp { pc!!.setLocalDescription(it, offer) }
                db.collection("calls").document(callId).set(
                    mapOf(
                        "callerId" to me, "calleeId" to peer.uid, "chatId" to chatId, "video" to video,
                        "status" to "ringing", "offer" to e2ee.seal(callKey!!, offer.description, callId),
                        "a" to kidA, "b" to kidB, "createdAt" to System.currentTimeMillis()
                    )
                ).await()
                watchCallDoc()
                watchCandidates()
                timeout = scope.launch { delay(45_000); if (_ui.value.phase == CallPhase.OUTGOING) hangup("Нет ответа") }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun accept() {
        val state = _ui.value
        val peer = state.peer ?: return
        if (state.phase != CallPhase.INCOMING) return
        if (!hasPermissions(state.video)) { _events.tryEmit("Нет разрешения на микрофон/камеру"); decline(); return }
        timeout?.cancel()
        scope.launch {
            try {
                _ui.update { it.copy(phase = CallPhase.CONNECTING, speaker = it.video) }
                callKey = e2ee.pairKey(me, kidB, peer.uid, kidA, chatId)
                beginAudio(state.video)
                buildPeer()
                val offer = e2ee.open(callKey!!, offerCt, callId)
                setSdp { pc!!.setRemoteDescription(it, SessionDescription(SessionDescription.Type.OFFER, offer)) }
                remoteSet = true
                attachLocalMedia(state.video)
                val answer = createSdp { pc!!.createAnswer(it, MediaConstraints()) }
                setSdp { pc!!.setLocalDescription(it, answer) }
                flushPending()
                db.collection("calls").document(callId)
                    .update(mapOf("answer" to e2ee.seal(callKey!!, answer.description, callId), "status" to "accepted")).await()
                watchCandidates()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun decline() {
        if (callId.isNotEmpty()) db.collection("calls").document(callId).update("status", "declined")
        cleanup()
    }

    fun hangup(message: String? = null) {
        if (callId.isNotEmpty()) db.collection("calls").document(callId).update("status", "ended")
        message?.let { _events.tryEmit(it) }
        cleanup()
    }

    fun toggleMute() {
        val m = !_ui.value.muted
        audioTrack?.setEnabled(!m)
        _ui.update { it.copy(muted = m) }
    }

    fun toggleSpeaker() {
        val s = !_ui.value.speaker
        @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = s
        _ui.update { it.copy(speaker = s) }
    }

    fun toggleCamera() {
        val on = !_ui.value.cameraOn
        videoTrack?.setEnabled(on)
        _ui.update { it.copy(cameraOn = on) }
    }

    fun switchCamera() { (capturer as? CameraVideoCapturer)?.switchCamera(null) }

    /* ------------------------------------------------------------------ signalling */

    private fun watchCallDoc() {
        callReg?.remove()
        callReg = db.collection("calls").document(callId).addSnapshotListener { snap, _ ->
            val status = snap?.getString("status") ?: return@addSnapshotListener
            when (status) {
                "accepted" -> {
                    val answer = snap.getString("answer")
                    if (_ui.value.phase == CallPhase.OUTGOING && answer != null && !remoteSet) {
                        remoteSet = true
                        timeout?.cancel()
                        _ui.update { it.copy(phase = CallPhase.CONNECTING) }
                        scope.launch {
                            try {
                                val sdp = e2ee.open(callKey!!, answer, callId)
                                setSdp { pc!!.setRemoteDescription(it, SessionDescription(SessionDescription.Type.ANSWER, sdp)) }
                                flushPending()
                            } catch (e: Exception) { fail(e) }
                        }
                    }
                }
                "ended" -> if (_ui.value.phase != CallPhase.IDLE) { _events.tryEmit("Звонок завершён"); cleanup() }
                "declined" -> if (_ui.value.phase != CallPhase.IDLE) { _events.tryEmit("Звонок отклонён"); cleanup() }
                "busy" -> if (_ui.value.phase != CallPhase.IDLE) { _events.tryEmit("Абонент занят"); cleanup() }
            }
        }
    }

    private fun watchCandidates() {
        candReg?.remove()
        candReg = db.collection("calls").document(callId).collection("cands").addSnapshotListener { snap, _ ->
            snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { ch ->
                if (ch.document.getString("from") == me) return@forEach
                val key = callKey ?: return@forEach
                runCatching {
                    val parts = e2ee.open(key, ch.document.getString("ct").orEmpty(), callId).split("|", limit = 3)
                    val cand = IceCandidate(parts[0], parts[1].toInt(), parts[2])
                    if (remoteSet) pc?.addIceCandidate(cand) else pending += cand
                }
            }
        }
    }

    private fun flushPending() { pending.forEach { pc?.addIceCandidate(it) }; pending.clear() }

    /* ------------------------------------------------------------------ WebRTC plumbing */

    private fun factory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    private fun buildPeer() {
        val servers = FirebaseConfig.ICE_SERVERS.map { (url, user, pass) ->
            PeerConnection.IceServer.builder(url).apply { if (user != null) setUsername(user); if (pass != null) setPassword(pass) }.createIceServer()
        }
        val cfg = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory().createPeerConnection(cfg, object : BaseObserver() {
            override fun onIceCandidate(c: IceCandidate?) {
                c ?: return
                val key = callKey ?: return
                val id = callId
                scope.launch {
                    runCatching {
                        db.collection("calls").document(id).collection("cands").add(
                            mapOf("from" to me, "ct" to e2ee.seal(key, "${c.sdpMid}|${c.sdpMLineIndex}|${c.sdp}", id),
                                "ts" to System.currentTimeMillis())
                        ).await()
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val t = transceiver?.receiver?.track()
                if (t is VideoTrack) scope.launch { _ui.update { it.copy(remoteTrack = t) } }
            }

            override fun onConnectionChange(s: PeerConnection.PeerConnectionState?) {
                scope.launch {
                    when (s) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            if (_ui.value.phase != CallPhase.ACTIVE) {
                                _ui.update { it.copy(phase = CallPhase.ACTIVE, since = System.currentTimeMillis()) }
                            }
                        }
                        PeerConnection.PeerConnectionState.FAILED -> hangup("Соединение потеряно")
                        else -> {}
                    }
                }
            }
        })
    }

    private fun attachLocalMedia(video: Boolean) {
        val f = factory()
        audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack("a0", audioSource)
        pc?.addTrack(audioTrack, listOf("s0"))
        if (video) {
            val enumerator = Camera2Enumerator(context)
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
                ?: error("Камера недоступна")
            val cap = enumerator.createCapturer(name, null) ?: error("Камера недоступна")
            helper = SurfaceTextureHelper.create("capture", egl.eglBaseContext)
            videoSource = f.createVideoSource(false)
            cap.initialize(helper, context, videoSource!!.capturerObserver)
            cap.startCapture(1280, 720, 30)
            capturer = cap
            videoTrack = f.createVideoTrack("v0", videoSource)
            pc?.addTrack(videoTrack, listOf("s0"))
            _ui.update { it.copy(localTrack = videoTrack) }
        }
    }

    private fun beginAudio(video: Boolean) {
        savedMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = video
        ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
    }

    private fun fail(e: Exception) {
        _events.tryEmit("Ошибка звонка: ${e.message ?: "неизвестно"}")
        hangup()
    }

    private fun cleanup() {
        timeout?.cancel()
        callReg?.remove(); callReg = null
        candReg?.remove(); candReg = null
        runCatching { (capturer)?.stopCapture() }
        capturer?.dispose(); capturer = null
        videoTrack?.dispose(); videoTrack = null
        videoSource?.dispose(); videoSource = null
        helper?.dispose(); helper = null
        audioTrack?.dispose(); audioTrack = null
        audioSource?.dispose(); audioSource = null
        pc?.close(); pc?.dispose(); pc = null
        pending.clear(); remoteSet = false; callKey = null; callId = ""; offerCt = ""
        if (_ui.value.phase != CallPhase.IDLE) {
            audioManager.mode = savedMode
            @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = false
            context.stopService(Intent(context, CallService::class.java))
        }
        _ui.value = CallUi()
    }

    private suspend fun createSdp(block: (SdpObserver) -> Unit): SessionDescription = suspendCoroutine { c ->
        block(object : SdpObserver {
            override fun onCreateSuccess(d: SessionDescription?) { c.resume(d!!) }
            override fun onCreateFailure(e: String?) { c.resumeWithException(IllegalStateException(e)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
        })
    }

    private suspend fun setSdp(block: (SdpObserver) -> Unit) = suspendCoroutine<Unit> { c ->
        block(object : SdpObserver {
            override fun onCreateSuccess(d: SessionDescription?) {}
            override fun onCreateFailure(e: String?) {}
            override fun onSetSuccess() { c.resume(Unit) }
            override fun onSetFailure(e: String?) { c.resumeWithException(IllegalStateException(e)) }
        })
    }

    private open class BaseObserver : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(p0: IceCandidate?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
    }
}
