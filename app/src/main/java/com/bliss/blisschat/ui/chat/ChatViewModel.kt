package com.bliss.blisschat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.data.Message
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val chat: Chat? = null,
    val chatLoaded: Boolean = false,
    val messages: List<Message> = emptyList(),
    val messagesLoaded: Boolean = false,
    val users: Map<String, UserProfile> = emptyMap(),
    val me: UserProfile? = null
) {
    val myId get() = me?.uid.orEmpty()
    val isMember get() = chat?.members?.contains(myId) == true
    val isAdmin get() = me?.isAdmin == true
    val isOwner get() = chat != null && chat.ownerId == myId

    /** Direct chats: the other participant. */
    val other: UserProfile?
        get() = chat?.takeIf { it.type == ChatType.DIRECT }
            ?.members?.firstOrNull { it != myId }?.let { users[it] }

    val canPost: Boolean
        get() = chat != null && isMember &&
            (chat.type != ChatType.CHANNEL || isOwner || isAdmin)

    val canJoin: Boolean get() = chat != null && !isMember && chat.type != ChatType.DIRECT && chat.discoverable
    val canDelete: Boolean get() = chat?.type == ChatType.CHANNEL && (isOwner || isAdmin)
    val canLeave: Boolean get() = chat != null && chat.type != ChatType.DIRECT && isMember && !isOwner
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val c: AppContainer, private val chatId: String) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val me: StateFlow<UserProfile?> = c.session
        .map { (it as? Session.Ready)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class ChatBox(val chat: Chat?, val loaded: Boolean)
    private data class MsgBox(val list: List<Message>, val loaded: Boolean)

    private val chatFlow = c.repo.observeChat(chatId)
        .map { ChatBox(it, true) }
        .catch { emit(ChatBox(null, true)) }

    private val msgFlow = c.repo.observeMessages(chatId)
        .map { MsgBox(it, true) }
        .catch {
            _events.tryEmit(it.message ?: "Couldn't load messages")
            emit(MsgBox(emptyList(), true))
        }

    private val users = combine(chatFlow, msgFlow) { ch, m ->
        buildSet {
            ch.chat?.let { chat ->
                add(chat.ownerId)
                if (chat.type == ChatType.DIRECT) addAll(chat.members)
            }
            m.list.forEach { add(it.senderId) }
        }.filter { it.isNotBlank() }.toSet()
    }
        .distinctUntilChanged()
        .flatMapLatest { c.repo.observeUsers(it) }
        .catch { emit(emptyMap()) }

    val state: StateFlow<ChatUiState> = combine(chatFlow, msgFlow, users, me) { ch, m, u, user ->
        ChatUiState(
            chat = ch.chat,
            chatLoaded = ch.loaded,
            messages = m.list,
            messagesLoaded = m.loaded,
            users = u,
            me = user
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        // Read receipts: whenever new messages from others arrive and we are a member
        viewModelScope.launch {
            state.map { s -> Triple(s.myId, s.isMember, s.messages.filter { s.myId !in it.readBy }.map { it.id }) }
                .distinctUntilChanged()
                .collectLatest { (myId, member, unread) ->
                    if (member && myId.isNotEmpty() && unread.isNotEmpty()) {
                        c.repo.markRead(chatId, myId, state.value.messages)
                    }
                }
        }
    }

    fun send(text: String, onFailure: (String) -> Unit) {
        val myId = me.value?.uid ?: return
        viewModelScope.launch {
            c.repo.sendMessage(chatId, myId, text).onFailure {
                _events.tryEmit("Message not sent: ${it.message}")
                onFailure(text)
            }
        }
    }

    fun join() {
        val myId = me.value?.uid ?: return
        viewModelScope.launch {
            c.repo.join(chatId, myId).onFailure { _events.tryEmit(it.message ?: "Couldn't join") }
        }
    }

    fun leave(onDone: () -> Unit) {
        val myId = me.value?.uid ?: return
        viewModelScope.launch {
            c.repo.leave(chatId, myId)
                .onSuccess { onDone() }
                .onFailure { _events.tryEmit(it.message ?: "Couldn't leave") }
        }
    }

    /** Owner or Super Admin: force-delete the channel and all of its messages. */
    fun deleteChannel(onDone: () -> Unit) {
        val actor = me.value ?: return
        viewModelScope.launch {
            val result = if (actor.isAdmin) c.repo.adminDeleteChannel(actor, chatId) else c.repo.deleteChat(chatId)
            result.onSuccess { onDone() }.onFailure { _events.tryEmit(it.message ?: "Couldn't delete") }
        }
    }
}
