package com.bliss.blisschat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.AppSettings
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.data.crypto.BackupInfo
import com.bliss.blisschat.data.crypto.RestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class MutedItem(val key: String, val name: String)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val user: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val updates get() = c.updates

    val myBots: StateFlow<List<UserProfile>> = c.session.flatMapLatest { session ->
        val uid = (session as? Session.Ready)?.user?.uid ?: return@flatMapLatest flowOf(emptyList())
        c.bots.observeMyBots(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateBotCode(botUid: String, code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = c.bots.updateBotCode(botUid, code)
            onResult(res.isSuccess)
        }
    }

    fun deleteBot(botUid: String) {
        viewModelScope.launch { c.bots.deleteBot(botUid) }
    }

    /** People and chats whose notifications are switched off (with readable names). */
    val muted: StateFlow<List<MutedItem>> = c.settings.settings.map { it.muted }.distinctUntilChanged().mapLatest { keys ->
        keys.sorted().map { k ->
            val id = k.substring(2)
            val name = runCatching {
                if (k.startsWith("u:")) c.repo.observeUser(id).first()?.title else c.repo.observeChat(id).first()?.name?.ifBlank { "Личный чат" }
            }.getOrNull()
            MutedItem(k, name ?: id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDark(v: Boolean) { viewModelScope.launch { c.settings.setDark(v) } }
    fun setHue(v: Float) { viewModelScope.launch { c.settings.setHue(v) } }
    fun setDynamicColors(v: Boolean) { viewModelScope.launch { c.settings.setDynamicColors(v) } }
    fun setGlassTheme(v: Boolean) { viewModelScope.launch { c.settings.setGlassTheme(v) } }
    fun setGlassQuality(v: String) { viewModelScope.launch { c.settings.setGlassQuality(v) } }
    fun setRememberMe(v: Boolean) { viewModelScope.launch { c.settings.setRememberMe(v) } }
    fun setNotifMessages(v: Boolean) { viewModelScope.launch { c.settings.setNotifMessages(v) } }
    fun setNotifCalls(v: Boolean) { viewModelScope.launch { c.settings.setNotifCalls(v) } }
    fun setNotifFullScreen(v: Boolean) { viewModelScope.launch { c.settings.setNotifFullScreen(v) } }
    fun setTextScale(v: Float) { viewModelScope.launch { c.settings.setTextScale(v) } }
    fun setAutoLoadPhotos(v: Boolean) { viewModelScope.launch { c.settings.setAutoLoadPhotos(v) } }
    fun setHideOnline(v: Boolean) { viewModelScope.launch { c.settings.setHideOnline(v) } }
    fun unmute(key: String) { viewModelScope.launch { c.settings.setMuted(key, false) } }
    fun signOut() { viewModelScope.launch { c.signOutCurrent() } }

    val accounts get() = c.accounts.accounts
    private val _switchError = MutableStateFlow<String?>(null)
    val switchError: StateFlow<String?> = _switchError
    fun clearSwitchError() { _switchError.value = null }
    private val _needPassword = MutableStateFlow<com.bliss.blisschat.data.SavedAccount?>(null)
    val needPassword: StateFlow<com.bliss.blisschat.data.SavedAccount?> = _needPassword
    fun cancelPassword() { _needPassword.value = null }
    fun switchTo(acc: com.bliss.blisschat.data.SavedAccount, typed: String? = null) {
        viewModelScope.launch {
            _needPassword.value = null
            c.switchAccount(acc, typed).onFailure {
                if (it is AppContainer.NeedPassword && acc.viaPassword) _needPassword.value = acc
                else if (it is AppContainer.NeedPassword) _switchError.value = "«${acc.title}» вошёл через Google: нажмите «добавить» и войдите через Google"
                else _switchError.value = it.message
            }
        }
    }
    /** Signs out of the screen WITHOUT forgetting the account, so a second account can sign in. */
    fun addAccount() { viewModelScope.launch { c.detachPush(); c.auth.signOut() } }

    /* ---- encrypted key backup (WhatsApp-style) */

    private val _backup = MutableStateFlow<BackupInfo?>(null)
    val backup: StateFlow<BackupInfo?> = _backup.asStateFlow()
    val hasLocalKey: Boolean get() = c.currentUid?.let { c.e2ee.localKey(it) != null } == true

    fun refreshBackup() {
        val uid = c.currentUid ?: return
        viewModelScope.launch { _backup.value = runCatching { c.keyBackup.info(uid) }.getOrNull() }
    }

    /** @return an error text, or null when the backup was saved */
    suspend fun saveBackup(secret: String, custom: Boolean): String? {
        val uid = c.currentUid ?: return "Нет аккаунта"
        val err = runCatching { if (c.keyBackup.save(uid, secret, custom)) null else "На этом телефоне нет ключей" }
            .getOrElse { "Не удалось сохранить копию: ${it.message}" }
        refreshBackup()
        return err
    }

    /** @return an error text, or null when the keys were restored */
    suspend fun restoreBackup(secret: String): String? {
        val uid = c.currentUid ?: return "Нет аккаунта"
        return when (runCatching { c.keyBackup.restore(uid, secret) }.getOrNull()) {
            RestoreResult.RESTORED -> null
            RestoreResult.WRONG_SECRET -> "Неверный пароль"
            RestoreResult.NO_BACKUP -> "Резервная копия не найдена"
            null -> "Не удалось связаться с сервером"
        }
    }
}
