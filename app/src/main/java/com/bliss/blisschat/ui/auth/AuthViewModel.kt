package com.bliss.blisschat.ui.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* =============================================================== e-mail + OTP */

class AuthViewModel(private val c: AppContainer) : ViewModel() {

    enum class Step { EMAIL, OTP }

    data class UiState(
        val step: Step = Step.EMAIL,
        val email: String = "",
        val code: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val debugCode: String? = null,
        val mock: Boolean = true,
        val resendIn: Int = 0
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var timer: Job? = null

    init {
        viewModelScope.launch {
            c.settings.settings.map { it.mockAuth }.distinctUntilChanged().collect { m ->
                _ui.update { it.copy(mock = m) }
            }
        }
    }

    fun setMock(value: Boolean) {
        viewModelScope.launch { c.settings.setMockAuth(value) }
    }

    fun onEmail(value: String) = _ui.update { it.copy(email = value, error = null) }

    fun submitEmail() {
        val s = _ui.value
        if (s.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            c.auth.sendOtp(s.email)
                .onSuccess { code ->
                    _ui.update { it.copy(step = Step.OTP, code = "", debugCode = code, loading = false) }
                    startResendTimer()
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun onCode(value: String) {
        _ui.update { it.copy(code = value, error = null) }
        if (value.length == 6) verify(value)
    }

    fun verify(code: String = _ui.value.code) {
        val s = _ui.value
        if (s.loading || code.length != 6) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            // On success the session flow in AppContainer switches the whole UI automatically.
            c.auth.verifyOtp(s.email, code)
                .onSuccess { _ui.update { it.copy(loading = false) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, code = "", error = e.message) } }
        }
    }

    fun resend() {
        if (_ui.value.resendIn > 0 || _ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            c.auth.sendOtp(_ui.value.email)
                .onSuccess { code ->
                    _ui.update { it.copy(loading = false, code = "", debugCode = code) }
                    startResendTimer()
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun back() {
        timer?.cancel()
        _ui.update { it.copy(step = Step.EMAIL, code = "", error = null, debugCode = null, resendIn = 0) }
    }

    private fun startResendTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (left in 30 downTo 0) {
                _ui.update { it.copy(resendIn = left) }
                delay(1000)
            }
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
                        delay(400) // debounce
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
        val clean = v.filter { (it in 'a'..'z') || (it in 'A'..'Z') || (it in '0'..'9') || it == '_' }.take(24)
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

    fun signOut() {
        viewModelScope.launch { c.auth.signOut() }
    }
}
