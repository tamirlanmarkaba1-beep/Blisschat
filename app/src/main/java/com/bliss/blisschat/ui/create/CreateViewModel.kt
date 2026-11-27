package com.bliss.blisschat.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreateViewModel(private val c: AppContainer) : ViewModel() {
    private val me: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun parse(list: String) = list.split(",", " ", "\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun startDirect(username: String, onResult: (Result<String>) -> Unit) {
        val m = me.value ?: return
        viewModelScope.launch { onResult(c.repo.openDirect(m.uid, username)) }
    }

    fun createGroup(
        name: String, members: String, channel: Boolean, avatarUrl: String = "", username: String = "", description: String = "",
        onResult: (Result<String>) -> Unit
    ) {
        val m = me.value ?: return
        viewModelScope.launch {
            onResult(c.repo.createGroup(m.uid, name, parse(members), if (channel) ChatType.CHANNEL else ChatType.GROUP, avatarUrl, username, description))
        }
    }

    fun pickAvatar(uri: android.net.Uri, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch { onResult(c.repo.avatarDataUri(uri)) }
    }

    /** Looks a public group/channel up by its @name. */
    fun find(name: String, onResult: (Result<Pair<Chat?, Boolean>>) -> Unit) {
        val m = me.value ?: return
        viewModelScope.launch {
            onResult(runCatching {
                val chat = c.repo.findChatByName(name)
                chat to (chat != null && m.uid in chat.members)
            })
        }
    }

    fun join(chat: Chat, onResult: (Result<Unit>) -> Unit) {
        val m = me.value ?: return
        viewModelScope.launch { onResult(c.repo.joinPublic(chat.id, m.uid)) }
    }

    /** @return the bot token */
    fun createBot(name: String, username: String, pythonCode: String, onResult: (Result<String>) -> Unit) {
        val m = me.value ?: return
        viewModelScope.launch { onResult(c.bots.createBot(m, name, username, pythonCode)) }
    }
}
