package com.bliss.blisschat.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.Content
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileState(val user: UserProfile? = null, val photos: List<Content> = emptyList(), val loaded: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(private val c: AppContainer, private val userId: String) : ViewModel() {

    val repo get() = c.repo

    private val me: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val userFlow = c.repo.observeUser(userId).catch { emit(null) }

    /** Photos shared in the direct chat with this person (decrypted on this device). */
    private val photos = me.flatMapLatest { m ->
        if (m == null || m.uid == userId) flowOf(emptyList<Content>())
        else {
            val chatId = c.repo.directId(m.uid, userId)
            combine(c.repo.observeChat(chatId).catch { emit(null) }, c.repo.observeMessages(chatId).catch { emit(emptyList()) }) { chat, msgs -> chat to msgs }
                .mapLatest { (chat, msgs) ->
                    if (chat == null) emptyList<Content>()
                    else msgs.filter { !it.deleted }.mapNotNull {
                        runCatching { c.e2ee.decrypt(chat, m.uid, it.senderId, it.env) }.getOrNull()
                    }.filter { it.kind == Content.KIND_IMAGE }.reversed()
                }
        }
    }

    val state: StateFlow<ProfileState> = combine(userFlow, photos) { u, p -> ProfileState(u, p, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState())

    fun openChat(secret: Boolean = false, onResult: (Result<String>) -> Unit) {
        val m = me.value ?: return
        val u = state.value.user ?: return
        viewModelScope.launch { onResult(c.repo.openDirect(m.uid, u.username, secret)) }
    }

    fun report() {
        val m = me.value ?: return
        viewModelScope.launch {
            c.repo.report(m.uid, userId)
                .onSuccess { _messages.tryEmit("Жалоба отправлена") }
                .onFailure { _messages.tryEmit(it.message ?: "Не удалось отправить жалобу") }
        }
    }

    fun saveProfile(name: String, bio: String) {
        val m = me.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch { c.repo.updateProfile(m.uid, name, bio).onFailure { _messages.tryEmit(it.message ?: "Ошибка") } }
    }

    fun changeAvatar(uri: Uri) {
        val m = me.value ?: return
        viewModelScope.launch {
            c.repo.updateAvatar(m.uid, uri)
                .onSuccess { _messages.tryEmit("Фото обновлено") }
                .onFailure { _messages.tryEmit("Не удалось загрузить фото: ${it.message}") }
        }
    }

    val myId: String get() = me.value?.uid.orEmpty()
}
