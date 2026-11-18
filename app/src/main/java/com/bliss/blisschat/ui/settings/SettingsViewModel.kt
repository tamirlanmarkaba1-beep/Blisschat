package com.bliss.blisschat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.AppSettings
import com.bliss.blisschat.data.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val user: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setDark(v: Boolean) { viewModelScope.launch { c.settings.setDark(v) } }
    fun setHue(v: Float) { viewModelScope.launch { c.settings.setHue(v) } }
    fun setRememberMe(v: Boolean) { viewModelScope.launch { c.settings.setRememberMe(v) } }
    fun signOut() { c.auth.signOut() }
}
