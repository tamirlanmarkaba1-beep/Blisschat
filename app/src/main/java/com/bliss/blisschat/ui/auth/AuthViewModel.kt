package com.bliss.blisschat.ui.auth

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* =============================================================== login / register */

class AuthViewModel(private val c: AppContainer) : ViewModel() {

    enum class Mode { LOGIN, REGISTER }

    data class UiState(
        val mode: Mode = Mode.LOGIN,
        val identifier: String = "",
        val password: String = "",
        val confirm: String = "",
        val remember: Boolean = true,
        val showPassword: Boolean = false,
        val loading: Boolean = false,
        val googleLoading: Boolean = false,
        val error: String? = null,
        val info: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun setMode(m: Mode) = _ui.update { it.copy(mode = m, error = null, info = null, password = "", confirm = "") }
    fun onIdentifier(v: String) = _ui.update { it.copy(identifier = v, error = null) }
    fun onPassword(v: String) = _ui.update { it.copy(password = v, error = null) }
    fun onConfirm(v: String) = _ui.update { it.copy(confirm = v, error = null) }
    fun toggleRemember() = _ui.update { it.copy(remember = !it.remember) }
    fun toggleShow() = _ui.update { it.copy(showPassword = !it.showPassword) }

    fun submit() {
        val s = _ui.value
        if (s.loading) return
        if (s.mode == Mode.REGISTER && s.password != s.confirm) {
            _ui.update { it.copy(error = "Пароли не совпадают") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null) }
            c.settings.setRememberMe(s.remember)
            // On success the session flow in AppContainer switches the whole UI automatically.
            val r = if (s.mode == Mode.LOGIN) c.auth.login(s.identifier, s.password) else c.auth.register(s.identifier, s.password)
            r.onFailure { e -> _ui.update { it.copy(error = e.message) } }
            _ui.update { it.copy(loading = false) }
        }
    }

    fun google(activityContext: Context) {
        if (_ui.value.googleLoading) return
        viewModelScope.launch {
            _ui.update { it.copy(googleLoading = true, error = null, info = null) }
            c.settings.setRememberMe(_ui.value.remember)
            c.auth.google(activityContext).onFailure { e -> _ui.update { it.copy(error = e.message) } }
            _ui.update { it.copy(googleLoading = false) }
        }
    }

    fun forgot() {
        viewModelScope.launch {
            c.auth.resetPassword(_ui.value.identifier)
                .onSuccess { _ui.update { it.copy(info = "Письмо для сброса пароля отправлено", error = null) } }
                .onFailure { e -> _ui.update { it.copy(error = e.message, info = null) } }
        }
    }
}

/* =============================================================== profile setup */

class ProfileSetupViewModel(private val c: AppContainer) : ViewModel() {

    enum class NameCheck { IDLE, CHECKING, FREE, TAKEN, INVALID }

    data class UiState(
        val displayName: String = "",
        val username: String = "",
        val avatar: Uri? = null,
        val check: NameCheck = NameCheck.IDLE,
        val loading: Boolean = false,
        val error: String? = null
    ) {
        val canSubmit get() = displayName.isNotBlank() && check == NameCheck.FREE && !loading
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val usernameInput = MutableStateFlow("")
    private val usernameRegex = Regex("^[a-z0-9_]{3,24}$")

    init {
        viewModelScope.launch {
            usernameInput.collectLatest { raw ->
                val name = raw.lowercase()
                when {
                    name.isEmpty() -> _ui.update { it.copy(check = NameCheck.IDLE) }
                    !usernameRegex.matches(name) -> _ui.update { it.copy(check = NameCheck.INVALID) }
                    else -> {
                        _ui.update { it.copy(check = NameCheck.CHECKING) }
                        delay(400)
                        val uid = (c.session.value as? Session.NeedsProfile)?.uid
                        val free = runCatching { c.repo.isUsernameFree(name, uid) }.getOrDefault(false)
                        _ui.update { it.copy(check = if (free) NameCheck.FREE else NameCheck.TAKEN) }
                    }
                }
            }
        }
    }

    fun onName(v: String) = _ui.update { it.copy(displayName = v.take(40), error = null) }

    fun onUsername(v: String) {
        val clean = v.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }.take(24)
        _ui.update { it.copy(username = clean, error = null) }
        usernameInput.value = clean
    }

    fun onAvatar(uri: Uri?) = _ui.update { it.copy(avatar = uri) }

    fun submit() {
        val s = _ui.value
        val session = c.session.value as? Session.NeedsProfile ?: return
        if (!s.canSubmit) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            c.repo.createProfile(session.uid, session.email, s.displayName, s.username, s.avatar)
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message) } }
            // On success the profile snapshot arrives and the UI moves to the app automatically.
        }
    }

    fun signOut() = c.auth.signOut()
}
