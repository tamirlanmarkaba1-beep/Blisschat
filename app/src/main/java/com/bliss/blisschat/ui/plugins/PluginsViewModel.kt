package com.bliss.blisschat.ui.plugins

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.data.plugins.Plugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class PluginsViewModel(private val c: AppContainer) : ViewModel() {
    val me: StateFlow<UserProfile?> = c.session.map { (it as? Session.Ready)?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val approved: StateFlow<List<Plugin>> = c.plugins.observeApproved().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mine: StateFlow<List<Plugin>> = me.flatMapLatest { u -> if (u == null) emptyFlow<List<Plugin>>() else c.plugins.observeMine(u.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pending: StateFlow<List<Plugin>> = c.plugins.observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val installed: StateFlow<Map<String, String>> = c.plugins.installed

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()
    private val _busy = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    val canModerate get() = me.value?.let { it.moderator || it.isAdmin } == true

    fun submit(uri: Uri) {
        val u = me.value ?: return
        viewModelScope.launch {
            _busy.value = "submit"
            c.plugins.submit(u, uri)
                .onSuccess { _events.tryEmit("Плагин отправлен на проверку модераторам") }
                .onFailure { _events.tryEmit(it.message ?: "Не удалось отправить") }
            _busy.value = null
        }
    }

    fun install(p: Plugin) {
        viewModelScope.launch {
            _busy.value = p.id
            c.plugins.install(p).onSuccess { _events.tryEmit("«${p.name}» установлен") }.onFailure { _events.tryEmit(it.message ?: "Не удалось установить") }
            _busy.value = null
        }
    }

    fun uninstall(p: Plugin) { c.plugins.uninstall(p); _events.tryEmit("«${p.name}» удалён") }

    fun deleteMine(p: Plugin) {
        viewModelScope.launch { c.plugins.deleteMine(p).onFailure { _events.tryEmit(it.message ?: "Не удалось удалить") } }
    }

    fun moderate(p: Plugin, approve: Boolean, note: String) {
        val u = me.value ?: return
        viewModelScope.launch {
            c.plugins.moderate(u, p.id, approve, note)
                .onSuccess { _events.tryEmit(if (approve) "«${p.name}» добавлен в каталог" else "«${p.name}» отклонён") }
                .onFailure { _events.tryEmit(it.message ?: "Ошибка") }
        }
    }

    fun export(p: Plugin, onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            _busy.value = p.id
            c.plugins.exportForReview(p).onSuccess(onIntent).onFailure { _events.tryEmit(it.message ?: "Не удалось скачать") }
            _busy.value = null
        }
    }

    fun previewCode(jsonStr: String) {
        viewModelScope.launch {
            runCatching {
                val src = jsonStr.trim()
                if (!src.startsWith("{")) {
                    // JavaScript plugin: runs right away in the sandbox, lives in Settings -> Plugins
                    val name = Regex("//\\s*name:\\s*(.+)").find(src)?.groupValues?.get(1)?.trim()?.take(40)?.ifBlank { null } ?: "Мой плагин"
                    c.plugins.runtime.install("local_" + name.hashCode().toUInt(), name, src)
                    return@runCatching "Плагин «$name» запущен. Его настройки: Настройки → Плагины"
                }
                val m = JSONObject(src)
                val type = m.optString("type")
                require(type == "theme") { "Для предпросмотра поддерживаются темы (type: theme)" }
                val t = m.optJSONObject("theme") ?: error("В theme нужен объект theme")
                val hue = t.getDouble("hue").toFloat().coerceIn(0f, 360f)
                val dark = if (t.has("dark")) t.getBoolean("dark") else false
                c.settings.setHue(hue)
                c.settings.setDark(dark)
                "Предпросмотр темы применен!"
            }.onSuccess { _events.tryEmit(it) }
             .onFailure { _events.tryEmit(it.message ?: "Ошибка предпросмотра кода") }
        }
    }
}
