package com.bliss.blisschat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.ChatItem
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatFilter(val label: String) {
    ALL("All"), DIRECT("Direct"), GROUPS("Groups"), CHANNELS("Channels"), DISCOVER("Discover")
}

data class HomeState(
    val me: UserProfile? = null,
    val items: List<ChatItem> = emptyList(),
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(ChatFilter.ALL)

    private val me: StateFlow<UserProfile?> = c.session
        .map { (it as? Session.Ready)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val uid = me.map { it?.uid }.filterNotNull().distinctUntilChanged()

    private val myChats = uid.flatMapLatest { c.repo.observeMyChats(it) }
        .catch { emit(emptyList()) }
    private val discover = c.repo.observePublicChannels().catch { emit(emptyList()) }

    /** Profiles of the people we have direct chats with (name / avatar / badges for the row). */
    private val peers = myChats
        .map { chats ->
            val mine = me.value?.uid
            chats.filter { it.type == ChatType.DIRECT }
                .flatMap { it.members }.filter { it != mine }.toSet()
        }
        .distinctUntilChanged()
        .flatMapLatest { c.repo.observeUsers(it) }
        .catch { emit(emptyMap()) }

    private data class Data(val mine: List<Chat>, val pub: List<Chat>, val peers: Map<String, UserProfile>)

    private val data = combine(myChats, discover, peers) { m, p, u -> Data(m, p, u) }

    val state: StateFlow<HomeState> = combine(data, query, filter, me) { d, q, f, user ->
        val myId = user?.uid.orEmpty()
        val joinedIds = d.mine.map { it.id }.toSet()

        fun toItem(chat: Chat, joined: Boolean): ChatItem {
            if (chat.type == ChatType.DIRECT) {
                val other = d.peers[chat.members.firstOrNull { it != myId }]
                return ChatItem(
                    chat = chat,
                    title = other?.displayName?.ifBlank { other.username } ?: "Direct chat",
                    avatarUrl = other?.avatarUrl.orEmpty(),
                    verified = other?.verified == true,
                    support = other?.support == true,
                    subtitle = chat.lastMessage.ifBlank { other?.let { "@${it.username}" }.orEmpty() },
                    time = chat.lastMessageAt,
                    joined = true
                )
            }
            return ChatItem(
                chat = chat,
                title = chat.name,
                avatarUrl = "",
                verified = false,
                support = false,
                subtitle = if (joined) chat.lastMessage
                else chat.description.ifBlank { "${chat.members.size} members" },
                time = chat.lastMessageAt,
                joined = joined
            )
        }

        val base: List<ChatItem> = when (f) {
            ChatFilter.ALL -> d.mine.map { toItem(it, true) }
            ChatFilter.DIRECT -> d.mine.filter { it.type == ChatType.DIRECT }.map { toItem(it, true) }
            ChatFilter.GROUPS -> d.mine.filter { it.type == ChatType.GROUP }.map { toItem(it, true) }
            ChatFilter.CHANNELS -> d.mine.filter { it.type == ChatType.CHANNEL }.map { toItem(it, true) }
            ChatFilter.DISCOVER -> d.pub.filter { it.id !in joinedIds }.map { toItem(it, false) }
        }
        val needle = q.trim().lowercase()
        val filtered = if (needle.isEmpty()) base else base.filter {
            it.title.lowercase().contains(needle) || it.subtitle.lowercase().contains(needle)
        }
        HomeState(me = user, items = filtered.sortedByDescending { it.time }, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: ChatFilter) { filter.value = f }

    fun startDirect(username: String, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        val me = me.value ?: return@launch
        onResult(c.repo.openDirect(me.uid, username))
    }

    fun createGroup(name: String, usernames: String, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        val me = me.value ?: return@launch
        val list = usernames.split(",", " ", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        onResult(c.repo.createGroup(me.uid, name, list))
    }

    fun createChannel(name: String, description: String, discoverable: Boolean, onResult: (Result<String>) -> Unit) =
        viewModelScope.launch {
            val me = me.value ?: return@launch
            onResult(c.repo.createChannel(me.uid, name, description, discoverable))
        }

    fun join(chatId: String, onDone: (Result<Unit>) -> Unit) = viewModelScope.launch {
        val me = me.value ?: return@launch
        onDone(c.repo.join(chatId, me.uid))
    }
}
