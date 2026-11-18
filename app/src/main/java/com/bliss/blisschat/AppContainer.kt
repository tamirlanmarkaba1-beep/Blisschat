package com.bliss.blisschat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bliss.blisschat.data.AuthRepository
import com.bliss.blisschat.data.ChatRepository
import com.bliss.blisschat.data.SettingsStore
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.data.call.CallManager
import com.bliss.blisschat.data.crypto.E2EE
import com.bliss.blisschat.data.crypto.LocalKeys
import com.bliss.blisschat.ui.admin.AdminViewModel
import com.bliss.blisschat.ui.auth.AuthViewModel
import com.bliss.blisschat.ui.auth.ProfileSetupViewModel
import com.bliss.blisschat.ui.chat.ChatViewModel
import com.bliss.blisschat.ui.home.HomeViewModel
import com.bliss.blisschat.ui.profile.ProfileViewModel
import com.bliss.blisschat.ui.settings.SettingsViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the root of the UI should show. */
sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class NeedsProfile(val uid: String, val email: String) : Session
    data class Ready(val user: UserProfile) : Session
    data class Error(val message: String) : Session
}

/** Tiny hand-rolled dependency container (keeps the project free of DI-framework setup). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(context)
    val e2ee = E2EE(LocalKeys(context))
    val repo = ChatRepository(context.applicationContext, e2ee)
    val auth = AuthRepository()
    val calls = CallManager(context.applicationContext, e2ee)

    private val authUser: Flow<Pair<String, String>?> = callbackFlow {
        val fa = FirebaseAuth.getInstance()
        val l = FirebaseAuth.AuthStateListener { a -> trySend(a.currentUser?.let { it.uid to it.email.orEmpty() }) }
        fa.addAuthStateListener(l)
        awaitClose { fa.removeAuthStateListener(l) }
    }.distinctUntilChanged()

    /** Single source of truth: who is logged in and is their profile complete. */
    val session: StateFlow<Session> = authUser
        .flatMapLatest { u ->
            if (u == null) flowOf<Session>(Session.SignedOut)
            else repo.observeUser(u.first)
                .map<UserProfile?, Session> { p -> if (p == null) Session.NeedsProfile(u.first, u.second) else Session.Ready(p) }
                .catch { emit(Session.Error(it.message ?: "Не удалось загрузить профиль")) }
        }
        .stateIn(appScope, SharingStarted.Eagerly, Session.Loading)

    val currentUid: String? get() = (session.value as? Session.Ready)?.user?.uid

    init {
        appScope.launch {
            var keyedUid: String? = null
            session.collect { s ->
                if (s is Session.Ready) {
                    calls.listen(s.user.uid)
                    if (keyedUid != s.user.uid) {
                        keyedUid = s.user.uid
                        // First start on this device creates the key pair; the public half is published.
                        val local = e2ee.localKey(s.user.uid)
                        runCatching { e2ee.ensureKeys(s.user.uid, publishToProfile = local == null || local.kid != s.user.kid) }
                    }
                } else {
                    calls.stopListening()
                    if (s is Session.SignedOut) keyedUid = null
                }
            }
        }
    }

    fun factory(arg: String? = null): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            AuthViewModel::class.java -> AuthViewModel(this@AppContainer)
            ProfileSetupViewModel::class.java -> ProfileSetupViewModel(this@AppContainer)
            HomeViewModel::class.java -> HomeViewModel(this@AppContainer)
            ChatViewModel::class.java -> ChatViewModel(this@AppContainer, requireNotNull(arg))
            ProfileViewModel::class.java -> ProfileViewModel(this@AppContainer, requireNotNull(arg))
            SettingsViewModel::class.java -> SettingsViewModel(this@AppContainer)
            AdminViewModel::class.java -> AdminViewModel(this@AppContainer)
            else -> error("Unknown ViewModel ${modelClass.name}")
        } as T
    }
}
