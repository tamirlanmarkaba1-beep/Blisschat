package com.bliss.blisschat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bliss.blisschat.data.AuthRepository
import com.bliss.blisschat.data.BotRepository
import com.bliss.blisschat.data.bot.SystemBot
import com.bliss.blisschat.data.stickers.StickerStore
import com.bliss.blisschat.ui.create.CreateViewModel
import com.bliss.blisschat.data.ChatRepository
import com.bliss.blisschat.data.SettingsStore
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.data.call.CallManager
import com.bliss.blisschat.data.call.CallPhase
import com.bliss.blisschat.data.push.Notifications
import com.bliss.blisschat.data.update.UpdateManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import com.bliss.blisschat.data.crypto.E2EE
import com.bliss.blisschat.data.crypto.KeyBackup
import com.bliss.blisschat.data.crypto.LocalKeys
import com.bliss.blisschat.data.crypto.RestoreResult
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
import kotlinx.coroutines.tasks.await

/** What the root of the UI should show. */
sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class NeedsProfile(val uid: String, val email: String) : Session
    data class Ready(val user: UserProfile) : Session
    data class Error(val message: String) : Session
}

/** Are the encryption keys of the signed-in account available on this phone? */
enum class KeyState { OK, NEEDS_RESTORE }

/** Tiny hand-rolled dependency container (keeps the project free of DI-framework setup). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(context)
    private val localKeys = LocalKeys(context)
    val e2ee = E2EE(localKeys)
    val keyBackup = KeyBackup(localKeys)
    val repo = ChatRepository(context.applicationContext, e2ee)
    val auth = AuthRepository()
    val calls = CallManager(context.applicationContext, e2ee)
    val groupCalls = com.bliss.blisschat.data.call.GroupCallManager(context.applicationContext, calls)
    val updates = UpdateManager(context.applicationContext)
    val bots = BotRepository(context.applicationContext)
    val systemBot = SystemBot(context.applicationContext)
    val stickers = StickerStore(context.applicationContext)
    private val appContext = context.applicationContext
    val accounts = com.bliss.blisschat.data.AccountStore(context.applicationContext)
    val plugins = com.bliss.blisschat.data.plugins.PluginRepository(context.applicationContext, stickers, settings)

    /** Set by a tapped notification: chat to open / answer the call as soon as it rings in the app. */
    val pendingChat = MutableStateFlow<String?>(null)
    @Volatile var acceptWhenRinging = false

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

    /** NEEDS_RESTORE: the account has an encrypted key backup but this phone has no keys yet (asks for the backup password). */
    val keyState = MutableStateFlow(KeyState.OK)

    val currentUid: String? get() = (session.value as? Session.Ready)?.user?.uid

    init {
        calls.onLog = { chatId, status, video, secs ->
            val uid = currentUid
            if (uid != null) appScope.launch { repo.sendCallLog(chatId, uid, status, video, secs) }
        }
        groupCalls.onLog = { chatId, status, secs ->
            val uid = currentUid
            if (uid != null) appScope.launch { repo.sendCallLog(chatId, uid, status, false, secs) }
        }
        appScope.launch {
            calls.ui.collect { ui ->
                when (ui.phase) {
                    CallPhase.INCOMING -> if (acceptWhenRinging && calls.hasPermissions(ui.video)) { acceptWhenRinging = false; calls.accept() }
                    CallPhase.CONNECTING, CallPhase.ACTIVE -> { acceptWhenRinging = false; Notifications.cancelCall(appContext) }
                    CallPhase.IDLE -> { acceptWhenRinging = false; Notifications.cancelCall(appContext) }
                    CallPhase.OUTGOING -> Unit
                }
            }
        }
        appScope.launch {
            var keyedUid: String? = null
            session.collect { s ->
                if (s is Session.Ready) {
                    calls.listen(s.user.uid)
                    systemBot.onSignedIn(s.user.uid, s.user.title, FirebaseAuth.getInstance().currentUser?.metadata?.lastSignInTimestamp ?: 0L)
                    runCatching {
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> appScope.launch { repo.saveFcmToken(s.user.uid, t) } }
                    }
                    if (keyedUid != s.user.uid) {
                        keyedUid = s.user.uid
                        runCatching { setupKeys(s.user) }
                    }
                } else {
                    if (s is Session.NeedsProfile) runCatching { repo.claimLegacy(s.uid, s.email) }
                    calls.stopListening()
                    groupCalls.leave()
                    if (s is Session.SignedOut) { keyedUid = null; keyState.value = KeyState.OK }
                }
            }
        }
    }

    /**
     * Makes the account's encryption keys usable on this phone (WhatsApp-style):
     *  - keys already here            -> publish, refresh the encrypted backup;
     *  - new phone + backup in cloud  -> restore the old keys (so old messages stay readable);
     *  - nothing anywhere             -> create keys, back them up.
     * The backup is unlocked with the password typed at sign-in; if that is not possible the user is asked for it.
     */
    private suspend fun setupKeys(user: UserProfile) {
        val uid = user.uid
        val secret = auth.sessionSecret
        auth.sessionSecret = null
        // remember this account so it can be switched to from Settings
        runCatching {
            val fb = FirebaseAuth.getInstance().currentUser
            val google = fb?.providerData?.any { it.providerId == "google.com" } == true
            accounts.remember(uid, fb?.email.orEmpty(), user.title, user.avatarUrl, if (secret != null) "password" else if (google) "google" else null, secret)
        }
        val local = e2ee.localKey(uid)
        val info = runCatching { keyBackup.info(uid) }.getOrNull()
        if (local == null && info?.exists == true) {
            val res = if (secret != null) runCatching { keyBackup.restore(uid, secret) }.getOrNull() else null
            if (res != RestoreResult.RESTORED) {
                // Ordinary chats need no keys any more: don't ask anything. New keys for future secret chats are created
                // now; the old ones can be restored later in Settings -> Encryption (the cloud backup stays untouched).
                e2ee.ensureKeys(uid, publishToProfile = true)
                keyState.value = KeyState.OK
                return
            }
        }
        val existing = e2ee.localKey(uid)
        e2ee.ensureKeys(uid, publishToProfile = existing == null || existing.kid != user.kid)
        // keep the cloud copy in step with the keys on this phone (only when the backup is protected by the account password)
        if (secret != null && (info == null || !info.exists || !info.custom)) runCatching { keyBackup.save(uid, secret, custom = false) }
        keyState.value = KeyState.OK
    }

    /** "Start over" in the restore dialog: new keys on this phone, old messages cannot be read. */
    suspend fun startFreshKeys() {
        val uid = currentUid ?: return
        e2ee.ensureKeys(uid, publishToProfile = true)
        keyState.value = KeyState.OK
    }

    /** Restore dialog: unlock the backup with [secret]. */
    suspend fun restoreKeys(secret: String): RestoreResult {
        val uid = currentUid ?: return RestoreResult.NO_BACKUP
        val res = keyBackup.restore(uid, secret)
        if (res == RestoreResult.RESTORED) {
            val user = (session.value as? Session.Ready)?.user
            e2ee.ensureKeys(uid, publishToProfile = e2ee.localKey(uid)?.kid != user?.kid)
            keyState.value = KeyState.OK
        }
        return res
    }

    /**
     * Switches to another saved account. Password accounts are signed in again with the stored password;
     * a Google account needs the Google button on the login screen (we only sign out for it).
     */
    suspend fun switchAccount(acc: com.bliss.blisschat.data.SavedAccount): Result<Unit> {
        if (acc.uid == currentUid) return Result.success(Unit)
        detachPush()
        val pw = if (acc.viaPassword) accounts.password(acc.uid) else null
        if (pw == null || acc.email.isBlank()) {
            auth.signOut()
            return Result.failure(IllegalStateException("Войдите в «${acc.title}» ещё раз"))
        }
        val r = auth.login(acc.email, pw)
        if (r.isFailure) { auth.signOut(); return Result.failure(IllegalStateException("Не удалось войти в «${acc.title}»: ${r.exceptionOrNull()?.message}")) }
        return Result.success(Unit)
    }

    /** Removes THIS phone's push token from the current account, so it stops receiving the old account's messages. */
    suspend fun detachPush() {
        val uid = currentUid ?: return
        runCatching {
            val t = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("fcmTokens", com.google.firebase.firestore.FieldValue.arrayRemove(t)).await()
        }
    }

    /** Plain sign-out (login screen / error screen): detaches the push token first. */
    fun signOutPlain() { appScope.launch { detachPush(); auth.signOut() } }

    /** "Выйти": forgets this account; goes to another saved one when there is one. */
    suspend fun signOutCurrent() {
        val uid = currentUid
        detachPush()
        if (uid != null) accounts.forget(uid)
        val next = accounts.accounts.value.firstOrNull { it.viaPassword && accounts.password(it.uid) != null }
        if (next != null && switchAccount(next).isSuccess) return
        auth.signOut()
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
            CreateViewModel::class.java -> CreateViewModel(this@AppContainer)
            com.bliss.blisschat.ui.plugins.PluginsViewModel::class.java -> com.bliss.blisschat.ui.plugins.PluginsViewModel(this@AppContainer)
            else -> error("Unknown ViewModel ${modelClass.name}")
        } as T
    }
}
