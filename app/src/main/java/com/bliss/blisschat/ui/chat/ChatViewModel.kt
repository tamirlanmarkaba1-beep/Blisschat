package com.bliss.blisschat.ui.chat

import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.AppSettings
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.MemberStat
import com.bliss.blisschat.data.TagStep
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.data.Content
import com.bliss.blisschat.data.Message
import com.bliss.blisschat.data.UiMessage
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

const val SAVED_PACK = "Из чатов"

data class ChatUiState(
    val chat: Chat? = null,
    val chatLoaded: Boolean = false,
    val messages: List<UiMessage> = emptyList(),
    val messagesLoaded: Boolean = false,
    val users: Map<String, UserProfile> = emptyMap(),
    val me: UserProfile? = null,
    val stats: Map<String, MemberStat> = emptyMap()
) {
    val myId get() = me?.uid.orEmpty()
    val isMember get() = chat?.members?.contains(myId) == true
    val isAdmin get() = me?.isAdmin == true
    val isOwner get() = chat != null && chat.ownerId == myId
    /** Group or channel (anything that is not a direct chat). */
    val isGroup get() = chat != null && chat.type != ChatType.DIRECT
    val isChannel get() = chat?.type == ChatType.CHANNEL
    /** In a channel only the owner publishes. */
    val canPost get() = isMember && (!isChannel || isOwner)
    val other: UserProfile? get() = chat?.peerOf(myId)?.let { users[it] }
    val canDeleteGroup get() = isGroup && (isOwner || isAdmin)
    val canLeave get() = isGroup && isMember && !isOwner
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val c: AppContainer, private val chatId: String) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    val repo get() = c.repo

    /** Message the user is replying to (swipe right on a message). */
    private val _replyTo = MutableStateFlow<UiMessage?>(null)
    val replyTo: StateFlow<UiMessage?> = _replyTo.asStateFlow()
    fun setReply(m: UiMessage?) { _replyTo.value = m }

    private val me: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class ChatBox(val chat: Chat?, val loaded: Boolean)
    private data class MsgBox(val list: List<Message>, val loaded: Boolean)

    private val chatFlow = c.repo.observeChat(chatId).map { ChatBox(it, true) }.catch { emit(ChatBox(null, true)) }
    private val msgFlow = c.repo.observeMessages(chatId).map { MsgBox(it, true) }
        .catch { _events.tryEmit(it.message ?: "Не удалось загрузить сообщения"); emit(MsgBox(emptyList(), true)) }

    private val users = combine(chatFlow, msgFlow) { ch, m ->
        buildSet<String> {
            ch.chat?.let { addAll(it.members) }
            m.list.forEach { add(it.senderId) }
        }.filter { it.isNotBlank() }.toSet()
    }.distinctUntilChanged().flatMapLatest { c.repo.observeUsers(it) }.catch { emit(emptyMap()) }

    // Only the decrypted CONTENT is cached. The Message itself (reactions, readBy...) must always be the fresh one,
    // otherwise reactions would never appear after the first render.
    private val decryptCache = HashMap<String, Content>()

    /** Bumped after the group key was re-issued so that the messages are decrypted again. */
    private val keyRefresh = MutableStateFlow(0)

    private val decrypted = combine(chatFlow, msgFlow, me, c.settings.settings.map { it.hidden }.distinctUntilChanged(), keyRefresh) { ch, m, user, hidden, _ ->
        Triple(ch.chat, m.list.filter { "$chatId/${it.id}" !in hidden }, user)
    }.mapLatest { (chat, list, user) ->
        list.map { msg ->
            val key = "${msg.id}:${msg.env.ct.length}:${msg.deleted}"
            if (msg.deleted) UiMessage(msg, null, false)
            else decryptCache[key]?.let { UiMessage(msg, it, false) } ?: run {
                if (chat == null || user == null) UiMessage(msg, null, true)
                else {
                    // null = this phone has no key for it; such results are NOT cached, so a later key restore fixes them
                    val content = c.e2ee.decryptOrNull(chat, user.uid, msg.senderId, msg.env)
                    if (content != null) { decryptCache[key] = content; UiMessage(msg, content, false) } else UiMessage(msg, null, true)
                }
            }
        }
    }

    private val stats = chatFlow.map { it.chat?.type }.distinctUntilChanged().flatMapLatest { t ->
        if (t == null || t == ChatType.DIRECT) flowOf(emptyMap<String, MemberStat>()) else c.repo.observeStats(chatId)
    }.catch { emit(emptyMap()) }

    private val base = combine(chatFlow, msgFlow, decrypted, users, me) { ch, m, d, u, user ->
        ChatUiState(ch.chat, ch.loaded, d, m.loaded, u, user)
    }

    val state: StateFlow<ChatUiState> = combine(base, stats) { b, st -> b.copy(stats = st) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** True while the chat screen is on screen; used to count time spent in a group (for rank tags). */
    @Volatile private var active = false
    fun setActive(value: Boolean) { active = value }

    /** Secret chat only: some messages can't be opened on this phone (keys stayed on the old phone). */
    val keyLost: StateFlow<Boolean> = state.map { st ->
        st.chat?.secret == true && st.messages.any { it.content == null && !it.message.deleted }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Plugins: new incoming messages of the open chat are passed to plugins that listen (never from secret chats)
        viewModelScope.launch {
            var seen: Set<String>? = null
            state.collect { st ->
                if (!st.messagesLoaded || st.chat == null) return@collect
                val ids = st.messages.mapTo(HashSet()) { it.message.id }
                val before = seen
                seen = ids
                if (before == null || !active || st.chat.secret) return@collect
                st.messages.filter { it.message.id !in before && it.content != null && !it.message.deleted && it.content.kind == Content.KIND_TEXT }
                    .forEach { u ->
                        c.plugins.runtime.dispatchMessage(
                            chatId, u.content!!.text, st.users[u.message.senderId]?.title.orEmpty(),
                            u.message.senderId == st.myId, st.isGroup, u.message.ts
                        )
                    }
            }
        }
        // Plugins: bliss.chat.send(text) goes to the open chat (rate limited by the runtime)
        viewModelScope.launch {
            c.plugins.runtime.commands.collect { cmd ->
                val s = state.value
                val chat = s.chat ?: return@collect
                if (!active || chat.secret || !s.canPost || (cmd.chatId != null && cmd.chatId != chatId)) return@collect
                c.repo.send(chat, s.myId, Content(Content.KIND_TEXT, text = cmd.text))
            }
        }
        // Time in the group: +1 minute for every minute the chat is open
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val s = state.value
                if (active && s.isGroup && s.isMember && s.myId.isNotEmpty()) c.repo.touchStats(chatId, s.myId, 1, 0)
            }
        }
        // Read receipts
        viewModelScope.launch {
            state.map { s -> Triple(s.chat, s.myId, s.messages.count { s.myId !in it.message.readBy && !it.message.deleted }) }
                .distinctUntilChanged { a, b -> a.first?.lastMsgId == b.first?.lastMsgId && a.first?.readers == b.first?.readers && a.third == b.third }
                .collectLatest { (chat, myId, _) ->
                    val s = state.value
                    if (chat != null && s.isMember && myId.isNotEmpty()) c.repo.markRead(chat, myId, s.messages.map { it.message })
                }
        }
    }

    fun sendText(text: String, onFailure: (String) -> Unit) {
        val s = state.value
        val chat = s.chat ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val body = c.plugins.runtime.transformOutgoing(text.trim()).trim()
            if (body.isEmpty()) return@launch
            c.repo.send(chat, s.myId, Content(Content.KIND_TEXT, text = body, replyToId = _replyTo.value?.message?.id.orEmpty()))
                .onSuccess { _replyTo.value = null; if (chat.type != ChatType.DIRECT) c.repo.touchStats(chatId, s.myId, 0, 1) }
                .onFailure { _events.tryEmit("Не отправлено: ${it.message}"); onFailure(text) }
        }
    }

    fun sendImage(uri: Uri) {
        val s = state.value
        val chat = s.chat ?: return
        viewModelScope.launch {
            _sending.value = true
            c.repo.sendImage(chat, s.myId, uri).onFailure { _events.tryEmit("Фото не отправлено: ${it.message}") }
            _sending.value = false
        }
    }

    fun sendSticker(file: java.io.File, pack: com.bliss.blisschat.data.stickers.StickerPack? = null) {
        val s = state.value
        val chat = s.chat ?: return
        viewModelScope.launch {
            _sending.value = true
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = file.readBytes()
                    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    Triple(bytes, o.outWidth, o.outHeight)
                }
            }
            r.onSuccess { (bytes, w, h) ->
                val reply = _replyTo.value?.message?.id.orEmpty()
                c.repo.sendSticker(chat, s.myId, bytes, w, h, pack?.name.orEmpty(), pack?.stickers.orEmpty(), reply).onSuccess { _replyTo.value = null }.onFailure { _events.tryEmit("Стикер не отправлен: ${it.message}") }
            }.onFailure { _events.tryEmit("Не удалось прочитать стикер") }
            _sending.value = false
        }
    }

    /** Mutes / unmutes notifications of this chat (or of the other person in a direct chat). */
    fun toggleMute() {
        val s = state.value
        val chat = s.chat ?: return
        val key = if (chat.type == ChatType.DIRECT) "u:${chat.peerOf(s.myId).orEmpty()}" else "c:${chat.id}"
        val muted = key in settings.value.muted
        viewModelScope.launch { c.settings.setMuted(key, !muted) }
    }

    fun isMuted(chat: Chat?, myId: String, muted: Set<String>): Boolean =
        chat != null && (if (chat.type == ChatType.DIRECT) "u:${chat.peerOf(myId).orEmpty()}" in muted else "c:${chat.id}" in muted)

    fun setSelfTag(tag: String) {
        val s = state.value
        viewModelScope.launch { c.repo.setSelfTag(chatId, s.myId, tag).onFailure { _events.tryEmit(it.message ?: "Не удалось сохранить тег") } }
    }

    fun updateTags(creatorTags: Boolean, selfTags: Boolean, ladder: List<TagStep>) {
        viewModelScope.launch { c.repo.updateTagSettings(chatId, creatorTags, selfTags, ladder).onFailure { _events.tryEmit(it.message ?: "Не удалось сохранить") } }
    }

    fun react(m: UiMessage, emoji: String) {
        val s = state.value
        val mine = m.message.reactions[s.myId]
        viewModelScope.launch {
            c.repo.react(chatId, m.message.id, s.myId, if (mine == emoji) null else emoji)
                .onFailure { _events.tryEmit(it.message ?: "Не удалось поставить реакцию") }
        }
    }

    fun deleteForEveryone(m: UiMessage) {
        val s = state.value
        val chat = s.chat ?: return
        if (m.message.senderId != s.myId) return
        viewModelScope.launch {
            c.repo.deleteForEveryone(chat, m.message, m.content?.path)
                .onFailure { _events.tryEmit(it.message ?: "Не удалось удалить") }
        }
    }

    /** Pins the message under the header; pinning the already pinned message unpins it. */
    fun togglePin(m: UiMessage) {
        val s = state.value
        val chat = s.chat ?: return
        val target = if (chat.pinnedId == m.message.id) null else m.message.id
        viewModelScope.launch { c.repo.pinMessage(chat.id, target).onFailure { _events.tryEmit(it.message ?: "Не удалось закрепить") } }
    }

    fun unpin() {
        val chat = state.value.chat ?: return
        viewModelScope.launch { c.repo.pinMessage(chat.id, null) }
    }

    /** Last resort for a lost group key: issue a new key to every member (old messages stay unreadable). */
    fun resetGroupKey() {
        val s = state.value
        val chat = s.chat ?: return
        viewModelScope.launch {
            runCatching { c.e2ee.resetGroupKey(chat, s.myId) }
                .onSuccess { keyRefresh.value = keyRefresh.value + 1; _events.tryEmit("Создан новый ключ группы") }
                .onFailure { _events.tryEmit("Не удалось создать ключ: ${it.message}") }
        }
    }

    fun deleteLocally(m: UiMessage) { viewModelScope.launch { c.settings.hide(chatId, m.message.id) } }

    /** Saves the WHOLE pack when the message carries a manifest; otherwise only this one sticker (older messages). */
    fun saveStickerPack(m: UiMessage) = saveSticker(m)

    fun saveSticker(m: UiMessage) {
        val ct = m.content ?: return
        if (ct.kind != Content.KIND_STICKER) return
        viewModelScope.launch {
            _events.tryEmit("Сохраняем…")
            runCatching {
                val hasPack = ct.text.startsWith("{")
                if (hasPack) {
                    val name = org.json.JSONObject(ct.text).optString("pn").ifBlank { "Набор из чата" }
                    val all = withContext(Dispatchers.IO) { c.repo.downloadPack(ct.text) }
                    val taken = c.stickers.packs.value.map { it.name }.toSet()
                    var finalName = name; var n = 2
                    while (finalName in taken) { finalName = "$name ($n)"; n++ }
                    val pack = c.stickers.createPack(finalName)
                    all.forEach { c.stickers.addBytes(pack.id, it) }
                    _events.tryEmit("Набор «$finalName» сохранён: ${all.size} стик.")
                } else {
                    val bytes = withContext(Dispatchers.IO) { c.repo.downloadMedia(ct.path, ct.key) }
                    val packId = c.stickers.packs.value.firstOrNull { it.name == SAVED_PACK }?.id ?: c.stickers.createPack(SAVED_PACK).id
                    c.stickers.addBytes(packId, bytes)
                    _events.tryEmit("Стикер сохранён в набор «$SAVED_PACK»")
                }
            }.onFailure { _events.tryEmit("Не удалось сохранить: ${it.message}") }
        }
    }

    /* ---- voice, files, polls */

    private fun sendBlob(kind: String, bytes: ByteArray, text: String, w: Int = 0, label: String) {
        val s = state.value
        val chat = s.chat ?: return
        if (bytes.size > 600_000) { _events.tryEmit("$label слишком большое (максимум ~600 КБ)"); return }
        viewModelScope.launch {
            _sending.value = true
            c.repo.sendBlob(chat, s.myId, kind, bytes, text, w)
                .onSuccess { if (chat.type != ChatType.DIRECT) c.repo.touchStats(chatId, s.myId, 0, 1) }
                .onFailure { _events.tryEmit("$label не отправлено: ${it.message}") }
            _sending.value = false
        }
    }

    fun sendVoice(file: File, seconds: Int) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes().also { file.delete() } }.getOrNull() }
            if (bytes == null) _events.tryEmit("Не удалось прочитать запись") else sendBlob(Content.KIND_VOICE, bytes, "", seconds, "Голосовое")
        }
    }

    /** Any file or a music track (mime audio/…): text holds {"n":name,"s":size,"m":mime}. */
    fun sendFile(bytes: ByteArray, name: String, mime: String) {
        val meta = org.json.JSONObject().put("n", name).put("s", bytes.size).put("m", mime).toString()
        sendBlob(Content.KIND_FILE, bytes, meta, 0, "Файл")
    }

    fun sendPoll(question: String, options: List<String>) {
        val s = state.value
        val chat = s.chat ?: return
        val clean = options.map { it.trim() }.filter { it.isNotEmpty() }.take(8)
        if (question.isBlank() || clean.size < 2) return
        val body = org.json.JSONObject().put("q", question.trim()).put("o", org.json.JSONArray(clean)).toString()
        viewModelScope.launch {
            c.repo.send(chat, s.myId, Content(Content.KIND_POLL, text = body))
                .onFailure { _events.tryEmit("Опрос не отправлен: ${it.message}") }
        }
    }

    /** A vote is stored as the member's "reaction" value poll:<index> (reactions are writable by every member). */
    fun vote(m: UiMessage, index: Int) {
        val s = state.value
        val value = "poll:$index"
        viewModelScope.launch {
            c.repo.react(chatId, m.message.id, s.myId, if (m.message.reactions[s.myId] == value) null else value)
                .onFailure { _events.tryEmit(it.message ?: "Не удалось проголосовать") }
        }
    }

    fun call(video: Boolean) {
        val s = state.value
        val peer = s.other ?: return
        c.calls.startCall(chatId, peer, video)
    }

    fun hasCallPermissions(video: Boolean) = c.calls.hasPermissions(video)

    /** People currently in this group's voice call. */
    val groupCall: StateFlow<List<String>> = c.groupCalls.observe(chatId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun joinGroupCall() {
        val chat = state.value.chat ?: return
        val me = (c.session.value as? com.bliss.blisschat.Session.Ready)?.user ?: return
        c.groupCalls.join(chatId, chat.name, me)
    }

    fun hasMicPermission() = c.groupCalls.hasPermission()

    /* ---- groups */

    fun saveProfile(name: String, description: String, username: String, onDone: (Result<Unit>) -> Unit) {
        val s = state.value
        val chat = s.chat ?: return
        viewModelScope.launch { onDone(c.repo.updateChatProfile(chat, s.myId, name, description, username)) }
    }

    fun changeAvatar(uri: android.net.Uri) {
        viewModelScope.launch { c.repo.updateChatAvatar(chatId, uri).onFailure { _events.tryEmit(it.message ?: "Не удалось сменить аватарку") } }
    }

    fun addMember(username: String, onDone: (Result<Unit>) -> Unit) {
        val s = state.value
        val chat = s.chat ?: return
        viewModelScope.launch { onDone(c.repo.addMember(chat, s.myId, username)) }
    }

    fun leave(onDone: () -> Unit) {
        viewModelScope.launch {
            c.repo.leave(chatId, state.value.myId).onSuccess { onDone() }.onFailure { _events.tryEmit(it.message ?: "Не удалось выйти") }
        }
    }

    fun deleteGroup(onDone: () -> Unit) {
        val actor = me.value ?: return
        viewModelScope.launch {
            val r = if (actor.isAdmin) c.repo.adminDeleteChat(actor, chatId) else c.repo.deleteChat(chatId)
            r.onSuccess { onDone() }.onFailure { _events.tryEmit(it.message ?: "Не удалось удалить") }
        }
    }

    fun setGroupVerified(value: Boolean) {
        val actor = me.value?.takeIf { it.isAdmin } ?: return
        viewModelScope.launch {
            c.repo.setGroupVerified(actor, chatId, value).onFailure { _events.tryEmit(it.message ?: "Ошибка") }
        }
    }
}
