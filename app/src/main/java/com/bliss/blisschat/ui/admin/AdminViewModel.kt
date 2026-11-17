package com.bliss.blisschat.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val query: String = "",
    val searching: Boolean = false,
    val target: UserProfile? = null,
    val error: String? = null,
    val busy: Boolean = false
)

/** Backs the Admin Panel. Every action is double-checked against the current user's admin flag. */
class AdminViewModel(private val c: AppContainer) : ViewModel() {

    val me: StateFlow<UserProfile?> = c.session
        .map { (it as? Session.Ready)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(AdminUiState())
    val ui: StateFlow<AdminUiState> = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val channels: StateFlow<List<Chat>> = c.repo.observeAllChannels()
        .map { list -> list.sortedByDescending { it.lastMessageAt } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQuery(v: String) = _ui.update { it.copy(query = v, error = null) }

    fun lookup() {
        val q = _ui.value.query
        if (q.isBlank() || _ui.value.searching) return
        viewModelScope.launch {
            _ui.update { it.copy(searching = true, error = null, target = null) }
            runCatching { c.repo.findUserByUsername(q) }
                .onSuccess { user ->
                    _ui.update {
                        it.copy(
                            searching = false,
                            target = user,
                            error = if (user == null) "No user with @${q.trim().removePrefix("@")}" else null
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(searching = false, error = e.message ?: "Lookup failed") } }
        }
    }

    fun setVerified(value: Boolean) = mutate { actor, target ->
        c.repo.setVerified(actor, target.uid, value).map { target.copy(verified = value) }
    }

    fun setSupport(value: Boolean) = mutate { actor, target ->
        c.repo.setSupport(actor, target.uid, value).map { target.copy(support = value) }
    }

    private fun mutate(block: suspend (UserProfile, UserProfile) -> Result<UserProfile>) {
        val actor = me.value?.takeIf { it.isAdmin } ?: return
        val target = _ui.value.target ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            block(actor, target)
                .onSuccess { updated ->
                    _ui.update { it.copy(busy = false, target = updated) }
                    _messages.tryEmit("Updated @${updated.username}")
                }
                .onFailure { e ->
                    _ui.update { it.copy(busy = false) }
                    _messages.tryEmit(e.message ?: "Update failed")
                }
        }
    }

    fun deleteChannel(chat: Chat) {
        val actor = me.value?.takeIf { it.isAdmin } ?: return
        viewModelScope.launch {
            c.repo.adminDeleteChannel(actor, chat.id)
                .onSuccess { _messages.tryEmit("Deleted \"${chat.name}\"") }
                .onFailure { _messages.tryEmit(it.message ?: "Couldn't delete channel") }
        }
    }
}
