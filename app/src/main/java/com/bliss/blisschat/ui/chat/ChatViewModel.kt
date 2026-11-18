package com.bliss.blisschat.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.data.Content
import com.bliss.blisschat.data.Message
import com.bliss.blisschat.data.UiMessage
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val chat: Chat? = null,
    val chatLoaded: Boolean = false,
    val messages: List<UiMessage> = emptyList(),
    val messagesLoaded: Boolean = false,
    val users: Map<String, UserProfile> = emptyMap(),
    val me: UserProfile? = null
) {
    val myId get() = me?.uid.orEmpty()
    val isMember get() = chat?.members?.contains(myId) == true
    val isAdmin get() = me?.isAdmin == true
    val isOwner get() = chat != null && chat.ownerId == myId
    val isGroup get() = chat?.type == ChatType.GROUP
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

    private val me: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class ChatBox(val chat: Chat?, val loaded: Boolean)
    private data class MsgBox(val list: List<Message>, val loaded: Boolean)

    private val chatFlow = c.repo.observeChat(chatId).map { ChatBox(it, true) }.catch { emit(ChatBox(null, true)) }
    private val msgFlow = c.repo.observeMessages(chatId).map { MsgBox(it, true) }
        .catch { _events.tryEmit(it.message ?: "Не удалось загрузить сообщения"); emit(MsgBox(emptyList(), true)) }

    private val users = combine(chatFlow, msgFlow) { ch, m ->
        buildSet {
            ch.chat?.let { addAll(it.members) }
            m.list.forEach { add(it.senderId) }
        }.filter { it.isNotBlank() }.toSet()
    }.distinctUntilChanged().flatMapLatest { c.repo.observeUsers(it) }.catch { emit(emptyMap()) }

    private val decryptCache = HashMap<String, UiMessage>()

    private val decrypted = combine(chatFlow, msgFlow, me, c.settings.settings.map { it.hidden }.distinctUntilChanged()) { ch, m, user, hidden ->
        Triple(ch.chat, m.list.filter { "$chatId/${it.id}" !in hidden }, user)
    }.mapLatest { (chat, list, user) ->
        var keyMissing = false
        list.map { msg ->
            val key = "${msg.id}:${msg.env.ct.length}:${msg.deleted}"
            decryptCache[key] ?: run {
                if (msg.deleted) UiMessage(msg, null, false).also { decryptCache[key] = it }
                else if (chat == null || user == null || keyMissing) UiMessage(msg, null, true)
                else try {
                    UiMessage(msg, c.e2ee.decrypt(chat, user.uid, msg.senderId, msg.env), false).also { decryptCache[key] = it }
                } catch (e: Exception) {
                    if (chat.type == ChatType.GROUP) keyMissing = true
                    UiMessage(msg, null, true)
                }
            }
        }
    }

    val state: StateFlow<ChatUiState> = combine(chatFlow, msgFlow, decrypted, users, me) { ch, m, d, u, user ->
        ChatUiState(ch.chat, ch.loaded, d, m.loaded, u, user)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
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
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            c.repo.send(chat, s.myId, Content(Content.KIND_TEXT, text = body)).onFailure {
                _events.tryEmit("Не отправлено: ${it.message}"); onFailure(text)
            }
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

    fun deleteLocally(m: UiMessage) { viewModelScope.launch { c.settings.hide(chatId, m.message.id) } }

    fun call(video: Boolean) {
        val s = state.value
        val peer = s.other ?: return
        c.calls.startCall(chatId, peer, video)
    }

    fun hasCallPermissions(video: Boolean) = c.calls.hasPermissions(video)

    /* ---- groups */

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
