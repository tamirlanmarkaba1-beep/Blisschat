package com.bliss.blisschat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bliss.blisschat.data.AuthRepository
import com.bliss.blisschat.data.ChatRepository
import com.bliss.blisschat.data.SettingsStore
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.ui.admin.AdminViewModel
import com.bliss.blisschat.ui.auth.AuthViewModel
import com.bliss.blisschat.ui.auth.ProfileSetupViewModel
import com.bliss.blisschat.ui.chat.ChatViewModel
import com.bliss.blisschat.ui.home.HomeViewModel
import com.bliss.blisschat.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.emptyFlow

/** What the root of the UI should show. */
sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class NeedsProfile(val uid: String, val email: String) : Session
    data class Ready(val user: UserProfile) : Session
    data class Error(val message: String) : Session
}

/** Tiny hand-rolled dependency container (keeps the project free of DI-framework setup). */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(context)
    val auth = AuthRepository(settings)
    val repo = ChatRepository(context.applicationContext)

    /** Single source of truth for "who is logged in and is their profile complete". */
    val session: StateFlow<Session> = settings.settings
        .map { it.sessionUid to it.sessionEmail }
        .distinctUntilChanged()
        .flatMapLatest { (uid, email) ->
            if (uid == null) {
                flowOf<Session>(Session.SignedOut)
            } else {
                repo.observeUser(uid)
                    .map<UserProfile?, Session> { profile ->
                        if (profile == null) Session.NeedsProfile(uid, email.orEmpty()) else Session.Ready(profile)
                    }
                    .catch { emit(Session.Error(it.message ?: "Could not load your profile")) }
            }
        }
        .stateIn(appScope, SharingStarted.Eagerly, Session.Loading)

    val currentUid: String? get() = (session.value as? Session.Ready)?.user?.uid
    val currentUser: UserProfile? get() = (session.value as? Session.Ready)?.user

    fun factory(chatId: String? = null): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            AuthViewModel::class.java -> AuthViewModel(this@AppContainer)
            ProfileSetupViewModel::class.java -> ProfileSetupViewModel(this@AppContainer)
            HomeViewModel::class.java -> HomeViewModel(this@AppContainer)
            ChatViewModel::class.java -> ChatViewModel(this@AppContainer, requireNotNull(chatId))
            SettingsViewModel::class.java -> SettingsViewModel(this@AppContainer)
            AdminViewModel::class.java -> AdminViewModel(this@AppContainer)
            else -> error("Unknown ViewModel ${modelClass.name}")
        } as T
    }
}
