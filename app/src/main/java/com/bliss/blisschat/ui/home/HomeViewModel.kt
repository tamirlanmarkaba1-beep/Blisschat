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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatFilter(val label: String) { GROUPS("группы"), UNREAD("непрочитано"), CONTACTS("контакты") }

data class HomeState(val me: UserProfile? = null, val items: List<ChatItem> = emptyList(), val loading: Boolean = true)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {

    val query = MutableStateFlow("")
    /** null = show everything */
    val filter = MutableStateFlow<ChatFilter?>(null)

    private val me: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val uid = me.map { it?.uid }.filterNotNull().distinctUntilChanged()

    private val myChats = uid.flatMapLatest { c.repo.observeMyChats(it) }.catch { emit(emptyList()) }

    /** Profiles needed for the list: the other person of each direct chat + last senders of groups. */
    private val people = myChats
        .map { chats ->
            val mine = me.value?.uid
            chats.flatMap { ch -> if (ch.type == ChatType.DIRECT) ch.members.filter { it != mine } else listOf(ch.lastSenderId) }
                .filter { it.isNotBlank() }.toSet()
        }
        .distinctUntilChanged()
        .flatMapLatest { c.repo.observeUsers(it) }
        .catch { emit(emptyMap()) }

    private val previews = HashMap<String, String>()

    private suspend fun previewOf(chat: Chat, myId: String, people: Map<String, UserProfile>): String {
        val env = chat.lastEnv
        if (env == null) {
            return when {
                chat.lastMsgId.isNotEmpty() -> "Сообщение удалено"
                chat.type == ChatType.DIRECT -> "Напишите первое сообщение"
                else -> "Группа создана"
            }
        }
        val key = "${chat.id}:${chat.lastMsgId}:${env.ct.length}"
        val text = previews.getOrPut(key) {
            runCatching { c.e2ee.decrypt(chat, myId, chat.lastSenderId, env).preview }.getOrDefault("🔒 Зашифрованное сообщение")
        }
        return if (chat.type == ChatType.GROUP) {
            val who = if (chat.lastSenderId == myId) "Вы" else people[chat.lastSenderId]?.title.orEmpty()
            if (who.isEmpty()) text else "$who: $text"
        } else text
    }

    private data class Inputs(val chats: List<Chat>, val people: Map<String, UserProfile>, val q: String, val f: ChatFilter?, val me: UserProfile?)

    val state: StateFlow<HomeState> = combine(myChats, people, query, filter, me) { ch, p, q, f, m -> Inputs(ch, p, q, f, m) }
        .mapLatest { i ->
            val myId = i.me?.uid.orEmpty()
            val items = i.chats.map { chat ->
                val preview = previewOf(chat, myId, i.people)
                if (chat.type == ChatType.DIRECT) {
                    val other = i.people[chat.members.firstOrNull { it != myId }]
                    ChatItem(chat, other?.title ?: "Личный чат", other?.avatarUrl.orEmpty(), other?.verified == true,
                        other?.support == true, preview, chat.lastAt, chat.unreadFor(myId))
                } else {
                    ChatItem(chat, chat.name, "", chat.verified, false, preview, chat.lastAt, chat.unreadFor(myId))
                }
            }
            val byFilter = when (i.f) {
                ChatFilter.GROUPS -> items.filter { it.chat.type == ChatType.GROUP }
                ChatFilter.UNREAD -> items.filter { it.unread }
                ChatFilter.CONTACTS -> items.filter { it.chat.type == ChatType.DIRECT }
                null -> items
            }
            val needle = i.q.trim().lowercase()
            val found = if (needle.isEmpty()) byFilter else byFilter.filter {
                it.title.lowercase().contains(needle) || it.preview.lowercase().contains(needle)
            }
            HomeState(i.me, found.sortedByDescending { it.time }, false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setQuery(q: String) { query.value = q }
    fun toggleFilter(f: ChatFilter) { filter.value = if (filter.value == f) null else f }

    fun startDirect(username: String, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        val me = me.value ?: return@launch
        onResult(c.repo.openDirect(me.uid, username))
    }

    fun createGroup(name: String, usernames: String, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        val me = me.value ?: return@launch
        val list = usernames.split(",", " ", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        onResult(c.repo.createGroup(me.uid, name, list))
    }
}
