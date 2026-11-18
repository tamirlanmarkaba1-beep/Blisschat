package com.bliss.blisschat.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(private val c: AppContainer) : ViewModel() {
    private val me: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val groups: StateFlow<List<Chat>> = c.repo.observeAllGroups().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _found = MutableStateFlow<UserProfile?>(null)
    val found: StateFlow<UserProfile?> = _found.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun clearStatus() { _status.value = null }

    fun find(username: String) {
        viewModelScope.launch {
            val u = runCatching { c.repo.findUserByUsername(username) }.getOrNull()
            _found.value = u
            _status.value = if (u == null) "Пользователь не найден" else null
        }
    }

    private fun refresh() { _found.value?.let { find(it.username) } }

    fun setVerified(v: Boolean) = act { u, a -> c.repo.setVerified(a, u.uid, v) }
    fun setSupport(v: Boolean) = act { u, a -> c.repo.setSupport(a, u.uid, v) }

    private fun act(block: suspend (UserProfile, UserProfile) -> Result<Unit>) {
        val actor = me.value ?: return
        val target = _found.value ?: return
        viewModelScope.launch {
            block(target, actor).onSuccess { refresh() }.onFailure { _status.value = it.message ?: "Ошибка" }
        }
    }

    fun setGroupVerified(chat: Chat, v: Boolean) {
        val actor = me.value ?: return
        viewModelScope.launch { c.repo.setGroupVerified(actor, chat.id, v).onFailure { _status.value = it.message } }
    }

    fun deleteGroup(chat: Chat) {
        val actor = me.value ?: return
        viewModelScope.launch {
            c.repo.adminDeleteChat(actor, chat.id)
                .onSuccess { _status.value = "Группа «${chat.name}» удалена" }
                .onFailure { _status.value = it.message }
        }
    }
}
