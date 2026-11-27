package com.bliss.blisschat.data.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.bliss.blisschat.data.FirebaseConfig
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.data.toUserProfile
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class GroupMember(val uid: String, val name: String, val avatarUrl: String, val connected: Boolean)

data class GroupCallUi(
    val active: Boolean = false,
    val chatId: String = "",
    val title: String = "",
    val members: List<GroupMember> = emptyList(),
    val muted: Boolean = false,
    val speaker: Boolean = true,
    val since: Long = 0L
)

/**
 * Voice calls in group chats: every participant keeps one WebRTC connection to every other one (a "mesh", fine for
 * about 6-8 people). Signalling goes through Firestore:
 *   groupCalls/{chatId}                 {active, startedBy, startedAt, participants: {uid: lastHeartbeatMs}}
 *   groupCalls/{chatId}/signals/{auto}  {from, to, type: offer | answer | cand, data, ts}
 * Of every pair the participant with the smaller uid sends the offer, so two people never offer at once.
 * Audio is encrypted peer-to-peer with DTLS-SRTP.
 */
class GroupCallManager(private val context: Context, private val calls: CallManager) {
    companion object { const val MAX_PEOPLE = 8; private const val ALIVE_MS = 45_000L }

    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _ui = MutableStateFlow(GroupCallUi())
    val ui: StateFlow<GroupCallUi> = _ui.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** (chatId, status, seconds): "group" when a call starts, "group-end" when the last person leaves. */
    var onLog: ((String, String, Int) -> Unit)? = null

    private class Peer(val pc: PeerConnection) { var remoteSet = false }

    private var factory: PeerConnectionFactory? = null
    private var me = ""
    private var chatId = ""
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private val peers = HashMap<String, Peer>()
    private val pending = HashMap<String, MutableList<IceCandidate>>()
    private val connected = HashSet<String>()
    private val profiles = HashMap<String, UserProfile>()
    private var lastAlive: List<String> = emptyList()
    private var docReg: ListenerRegistration? = null
    private var sigReg: ListenerRegistration? = null
    private var beat: Job? = null
    private var consumer: Job? = null
    private var inbox: Channel<Map<String, Any?>>? = null
    private var savedMode = AudioManager.MODE_NORMAL

    /** Who is in the call right now (for the "join" bar of the chat). */
    fun observe(chatId: String): Flow<List<String>> = callbackFlow {
        val reg = db.collection("groupCalls").document(chatId).addSnapshotListener { snap, _ -> trySend(aliveOf(snap)) }
        awaitClose { reg.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun aliveOf(snap: DocumentSnapshot?): List<String> {
        if (snap == null || snap.getBoolean("active") != true) return emptyList()
        val map = snap.get("participants") as? Map<String, Any?> ?: return emptyList()
        val now = System.currentTimeMillis()
        return map.filter { (_, v) -> now - ((v as? Number)?.toLong() ?: 0L) < ALIVE_MS }.keys.toList()
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun join(chatId: String, title: String, user: UserProfile) {
        if (_ui.value.active) return
        if (calls.ui.value.phase != CallPhase.IDLE) { _events.tryEmit("Сначала завершите текущий звонок"); return }
        if (!hasPermission()) { _events.tryEmit("Нет разрешения на микрофон"); return }
        me = user.uid
        this.chatId = chatId
        profiles[me] = user
        _ui.value = GroupCallUi(active = true, chatId = chatId, title = title, since = System.currentTimeMillis(),
            members = listOf(GroupMember(me, user.title, user.avatarUrl, true)))
        scope.launch {
            try {
                val ref = db.collection("groupCalls").document(chatId)
                val snap = ref.get().await()
                val alive = aliveOf(snap)
                if (alive.size >= MAX_PEOPLE && me !in alive) { _events.tryEmit("В звонке уже $MAX_PEOPLE человек"); leaveLocal(); return@launch }
                // leftovers from an earlier visit would confuse the handshake
                db.collection("groupCalls").document(chatId).collection("signals").whereEqualTo("to", me).get().await()
                    .documents.forEach { it.reference.delete() }
                beginAudio()
                val now = System.currentTimeMillis()
                if (alive.isEmpty()) {
                    ref.set(mapOf("chatId" to chatId, "startedBy" to me, "startedAt" to now, "active" to true, "participants" to mapOf(me to now))).await()
                    runCatching { onLog?.invoke(chatId, "group", 0) }
                } else {
                    ref.update(mapOf("active" to true, "participants.$me" to now)).await()
                }
                val ch = Channel<Map<String, Any?>>(Channel.UNLIMITED)
                inbox = ch
                consumer = scope.launch { for (s in ch) runCatching { handleSignal(s) } }
                sigReg = ref.collection("signals").whereEqualTo("to", me).addSnapshotListener { s, _ ->
                    s?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }
                        ?.sortedBy { it.document.getLong("ts") ?: 0L }
                        ?.forEach { c ->
                            val d = c.document
                            ch.trySend(mapOf("from" to d.getString("from"), "type" to d.getString("type"), "data" to d.getString("data")))
                            d.reference.delete()
                        }
                }
                docReg = ref.addSnapshotListener { s, _ -> onParticipants(aliveOf(s)) }
                beat = scope.launch {
                    while (true) {
                        delay(15_000)
                        runCatching { ref.update("participants.$me", System.currentTimeMillis()).await() }
                    }
                }
            } catch (e: Exception) {
                _events.tryEmit("Ошибка группового звонка: ${e.message ?: "неизвестно"}")
                leave()
            }
        }
    }

    fun leave() {
        if (!_ui.value.active) return
        val id = chatId
        val m = me
        val started = _ui.value.since
        leaveLocal()
        if (id.isEmpty()) return
        scope.launch {
            runCatching {
                val ref = db.collection("groupCalls").document(id)
                ref.update("participants.$m", FieldValue.delete()).await()
                if (aliveOf(ref.get().await()).isEmpty()) {
                    ref.update("active", false).await()
                    onLog?.invoke(id, "group-end", ((System.currentTimeMillis() - started) / 1000).toInt())
                }
            }
        }
    }

    private fun leaveLocal() {
        beat?.cancel(); beat = null
        docReg?.remove(); docReg = null
        sigReg?.remove(); sigReg = null
        consumer?.cancel(); consumer = null
        inbox?.close(); inbox = null
        peers.values.forEach { runCatching { it.pc.close(); it.pc.dispose() } }
        peers.clear(); pending.clear(); connected.clear(); lastAlive = emptyList()
        audioTrack?.dispose(); audioTrack = null
        audioSource?.dispose(); audioSource = null
        if (_ui.value.active) {
            audioManager.mode = savedMode
            @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = false
            context.stopService(Intent(context, CallService::class.java))
        }
        _ui.value = GroupCallUi()
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

    /* ------------------------------------------------------------------ participants */

    private fun onParticipants(alive: List<String>) {
        if (!_ui.value.active) return
        lastAlive = alive
        val others = alive.filter { it != me }
        // people who left
        peers.keys.filter { it !in others }.forEach { closePeer(it) }
        // people I must call (the smaller uid offers)
        others.filter { it !in peers && me < it }.forEach { startOffer(it) }
        scope.launch {
            others.filter { it !in profiles }.forEach { uid ->
                runCatching { db.collection("users").document(uid).get().await().toUserProfile() }.getOrNull()?.let { profiles[uid] = it }
            }
            publishMembers()
        }
        publishMembers()
    }

    private fun publishMembers() {
        val ids = (listOf(me) + lastAlive.filter { it != me })
        _ui.update { st ->
            st.copy(members = ids.map { uid ->
                val p = profiles[uid]
                GroupMember(uid, p?.title ?: "…", p?.avatarUrl.orEmpty(), uid == me || uid in connected)
            })
        }
    }

    private fun closePeer(uid: String) {
        peers.remove(uid)?.let { runCatching { it.pc.close(); it.pc.dispose() } }
        pending.remove(uid)
        connected.remove(uid)
        publishMembers()
    }

    /* ------------------------------------------------------------------ signalling */

    private fun startOffer(to: String) {
        val peer = createPeer(to) ?: return
        peers[to] = peer
        scope.launch {
            try {
                val offer = createSdp { peer.pc.createOffer(it, MediaConstraints()) }
                setSdp { peer.pc.setLocalDescription(it, offer) }
                sendSignal(to, "offer", offer.description)
            } catch (e: Exception) { closePeer(to) }
        }
    }

    private suspend fun handleSignal(s: Map<String, Any?>) {
        val from = s["from"] as? String ?: return
        val type = s["type"] as? String ?: return
        val data = s["data"] as? String ?: return
        when (type) {
            "offer" -> {
                closePeer(from)
                val peer = createPeer(from) ?: return
                peers[from] = peer
                setSdp { peer.pc.setRemoteDescription(it, SessionDescription(SessionDescription.Type.OFFER, data)) }
                peer.remoteSet = true
                flush(from, peer)
                val answer = createSdp { peer.pc.createAnswer(it, MediaConstraints()) }
                setSdp { peer.pc.setLocalDescription(it, answer) }
                sendSignal(from, "answer", answer.description)
            }
            "answer" -> {
                val peer = peers[from] ?: return
                if (peer.remoteSet) return
                setSdp { peer.pc.setRemoteDescription(it, SessionDescription(SessionDescription.Type.ANSWER, data)) }
                peer.remoteSet = true
                flush(from, peer)
            }
            "cand" -> {
                val parts = data.split("|", limit = 3)
                if (parts.size < 3) return
                val cand = IceCandidate(parts[0], parts[1].toInt(), parts[2])
                val peer = peers[from]
                if (peer != null && peer.remoteSet) peer.pc.addIceCandidate(cand) else pending.getOrPut(from) { mutableListOf() }.add(cand)
            }
        }
    }

    private fun flush(uid: String, peer: Peer) {
        pending.remove(uid)?.forEach { peer.pc.addIceCandidate(it) }
    }

    private fun sendSignal(to: String, type: String, data: String) {
        val id = chatId
        val from = me
        scope.launch {
            runCatching {
                db.collection("groupCalls").document(id).collection("signals")
                    .add(mapOf("from" to from, "to" to to, "type" to type, "data" to data, "ts" to System.currentTimeMillis())).await()
            }
        }
    }

    /* ------------------------------------------------------------------ WebRTC */

    private fun factory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    private fun createPeer(uid: String): Peer? {
        val servers = FirebaseConfig.ICE_SERVERS.map { (url, user, pass) ->
            PeerConnection.IceServer.builder(url).apply { if (user != null) setUsername(user); if (pass != null) setPassword(pass) }.createIceServer()
        }
        val cfg = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = factory().createPeerConnection(cfg, object : Observer() {
            override fun onIceCandidate(c: IceCandidate?) {
                c ?: return
                sendSignal(uid, "cand", "${c.sdpMid}|${c.sdpMLineIndex}|${c.sdp}")
            }

            override fun onConnectionChange(s: PeerConnection.PeerConnectionState?) {
                scope.launch {
                    when (s) {
                        PeerConnection.PeerConnectionState.CONNECTED -> { connected.add(uid); publishMembers() }
                        PeerConnection.PeerConnectionState.DISCONNECTED, PeerConnection.PeerConnectionState.FAILED -> {
                            // forget the dead link; if the person is still in the call it is rebuilt below
                            closePeer(uid)
                            delay(2_000)
                            if (_ui.value.active) onParticipants(lastAlive)
                        }
                        else -> {}
                    }
                }
            }
        }) ?: return null
        audioTrack?.let { pc.addTrack(it, listOf("s0")) }
        return Peer(pc)
    }

    private fun beginAudio() {
        val f = factory()
        savedMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = true
        ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
        audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack("g0", audioSource)
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

    private open class Observer : PeerConnection.Observer {
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
