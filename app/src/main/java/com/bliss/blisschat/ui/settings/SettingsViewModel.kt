package com.bliss.blisschat.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.Accent
import com.bliss.blisschat.data.AppSettings
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val me: StateFlow<UserProfile?> = c.session
        .map { (it as? Session.Ready)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setDark(value: Boolean) = viewModelScope.launch { c.settings.setDark(value) }
    fun setAccent(value: Accent) = viewModelScope.launch { c.settings.setAccent(value) }
    fun setMockAuth(value: Boolean) = viewModelScope.launch { c.settings.setMockAuth(value) }

    fun updateName(name: String) {
        val uid = me.value?.uid ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            c.repo.updateDisplayName(uid, name).onFailure { _messages.tryEmit(it.message ?: "Couldn't update name") }
        }
    }

    fun updateAvatar(uri: Uri) {
        val uid = me.value?.uid ?: return
        viewModelScope.launch {
            c.repo.updateAvatar(uid, uri)
                .onSuccess { _messages.tryEmit("Profile photo updated") }
                .onFailure { _messages.tryEmit("Couldn't upload photo: ${it.message}") }
        }
    }

    fun signOut() {
        viewModelScope.launch { c.auth.signOut() }
    }
}
