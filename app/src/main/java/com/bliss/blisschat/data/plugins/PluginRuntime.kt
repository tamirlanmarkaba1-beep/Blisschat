package com.bliss.blisschat.data.plugins

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bliss.blisschat.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One setting that a code plugin added to the "Плагины" settings page. */
data class PluginSetting(
    val pluginId: String, val pluginName: String, val id: String, val title: String,
    /** switch | slider | choice | text | button */
    val type: String, val description: String, val min: Float, val max: Float, val options: List<String>, val default: String
)

/**
 * Look of the WHOLE app that plugins may change. Colours are ARGB; null / empty = keep the app's own value.
 * [colors] overrides any Material colour role by name (primary, background, surface, surfaceContainer, onSurface ...).
 * [glass] turns every surface translucent and draws a soft animated colour backdrop behind the app ("liquid glass").
 */
data class PluginStyle(
    val cornerScale: Float = 1f, val mine: Long? = null, val other: Long? = null, val chatBg: Long? = null,
    val colors: Map<String, Long> = emptyMap(),
    val glass: Boolean = false, val glassAlpha: Float = 0.5f, val glassBlobs: List<Long> = emptyList(), val glassBorder: Boolean = true,
    val font: String = "", val bubbleAlpha: Float = 1f
)

/** An item a plugin added to the message menu (place = "message") or to the chat menu (place = "chat"). */
data class PluginAction(val pluginId: String, val pluginName: String, val id: String, val title: String, val place: String)

/** A screen a plugin draws itself (see bliss.ui.addPage). */
data class PluginPage(val pluginId: String, val pluginName: String, val id: String, val title: String)

/** A request of a plugin to the open chat (bliss.chat.send). */
data class PluginCommand(val pluginId: String, val text: String, val chatId: String?)

/** A code plugin that is installed on this phone. */
data class CodePlugin(val id: String, val name: String)

/**
 * Runs JavaScript plugins.
 *
 * Every plugin lives in its own WebView that is completely cut off from the world: no network (all requests are blocked and
 * a Content-Security-Policy forbids everything but the inline script), no files, no access to the app or to chats.
 * The only way out is the small `bliss` object that this class injects:
 *
 *   bliss.settings.add({id, title, type: "switch"|"slider"|"choice"|"text"|"button", default, min, max, options, description})
 *   bliss.settings.get(id)                       -> current value (boolean / number / string)
 *   bliss.on("setting", function (id, value) {}) -> called when the user changes one of the plugin's settings
 *   bliss.app.setTheme({hue: 0..360 | -1, dark: true|false})
 *   bliss.app.setTextScale(0.8 .. 1.4)
 *   bliss.app.setStyle({cornerScale: 0..2, bubbleMine: "#RRGGBB", bubbleOther: "#RRGGBB", chatBackground: "#RRGGBB"})
 *   bliss.chat.onSend(function (text) { return newText })   -> changes every message before it is sent
 *   bliss.toast("text")
 *
 * API v2 (see the in-app guide): full style/glass engine in setStyle, bliss.storage, bliss.http (allow-listed hosts),
 * bliss.on("message"), bliss.chat.send, bliss.ui.addMessageAction / addChatAction / addPage.
 * Privacy rule enforced here: a plugin may EITHER read messages passively (on "message", onSend) OR use the network, never both.
 */
class PluginRuntime(context: Context, private val settings: SettingsStore) {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val values = app.getSharedPreferences("plugin_values", Context.MODE_PRIVATE)
    private val meta = app.getSharedPreferences("plugin_meta", Context.MODE_PRIVATE)
    private val dir get() = File(app.filesDir, "plugin_code").apply { mkdirs() }
    private val views = HashMap<String, WebView>()

    private val _defs = MutableStateFlow<List<PluginSetting>>(emptyList())
    val definitions: StateFlow<List<PluginSetting>> = _defs.asStateFlow()
    private val _plugins = MutableStateFlow<List<CodePlugin>>(emptyList())
    val plugins: StateFlow<List<CodePlugin>> = _plugins.asStateFlow()
    private val styles = HashMap<String, PluginStyle>()
    private val sendHooks = HashSet<String>()
    private val readers = LinkedHashSet<String>()                       // plugins that read messages passively
    private val hosts = HashMap<String, Set<String>>()                 // plugin -> hosts it may call
    private val sentAt = HashMap<String, ArrayDeque<Long>>()
    private val httpAt = HashMap<String, ArrayDeque<Long>>()
    private val store = app.getSharedPreferences("plugin_store", Context.MODE_PRIVATE)
    private val _actions = MutableStateFlow<List<PluginAction>>(emptyList())
    val actions: StateFlow<List<PluginAction>> = _actions.asStateFlow()
    private val _pages = MutableStateFlow<List<PluginPage>>(emptyList())
    val pages: StateFlow<List<PluginPage>> = _pages.asStateFlow()
    private val _perms = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    /** plugin id -> human readable list of what it may do ("Сеть: api.x.com", "Читает сообщения"). */
    val permissions: StateFlow<Map<String, List<String>>> = _perms.asStateFlow()
    private val _commands = MutableSharedFlow<PluginCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<PluginCommand> = _commands.asSharedFlow()
    private val _style = MutableStateFlow(PluginStyle())
    val style: StateFlow<PluginStyle> = _style.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { main.post { restore() } }

    private fun restore() {
        dir.listFiles { f -> f.extension == "js" }.orEmpty().forEach { f ->
            val id = f.nameWithoutExtension
            start(id, meta.getString("name.$id", id).orEmpty(), f.readText())
        }
    }

    /** Saves the code and starts it. A plugin with the same id is replaced. */
    fun install(id: String, name: String, code: String) {
        require(code.length <= MAX_CODE) { "Код плагина больше 100 КБ" }
        File(dir, "${safe(id)}.js").writeText(code)
        meta.edit().putString("name.${safe(id)}", name).apply()
        main.post { start(safe(id), name, code) }
    }

    fun remove(id: String) {
        val pid = safe(id)
        File(dir, "$pid.js").delete()
        meta.edit().remove("name.$pid").apply()
        val ed = values.edit()
        values.all.keys.filter { it.startsWith("$pid.") }.forEach { ed.remove(it) }
        ed.apply()
        val se = store.edit(); store.all.keys.filter { it.startsWith("$pid.") }.forEach { se.remove(it) }; se.apply()
        main.post { stop(pid) }
    }

    fun has(id: String) = File(dir, "${safe(id)}.js").exists()

    fun value(s: PluginSetting): String = values.getString("${s.pluginId}.${s.id}", null) ?: s.default

    fun setValue(s: PluginSetting, value: String) {
        values.edit().putString("${s.pluginId}.${s.id}", value).apply()
        main.post {
            views[s.pluginId]?.evaluateJavascript("window.__set(${JSONObject.quote(s.id)},${JSONObject.quote(value)})", null)
        }
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun mergeStyles() {
        var r = PluginStyle()
        for (st in styles.values) r = r.copy(
            cornerScale = if (st.cornerScale != 1f) st.cornerScale else r.cornerScale,
            mine = st.mine ?: r.mine, other = st.other ?: r.other, chatBg = st.chatBg ?: r.chatBg,
            colors = r.colors + st.colors,
            glass = r.glass || st.glass,
            glassAlpha = if (st.glass) st.glassAlpha else r.glassAlpha,
            glassBlobs = if (st.glassBlobs.isNotEmpty()) st.glassBlobs else r.glassBlobs,
            glassBorder = if (st.glass) st.glassBorder else r.glassBorder,
            font = st.font.ifEmpty { r.font },
            bubbleAlpha = if (st.bubbleAlpha != 1f) st.bubbleAlpha else r.bubbleAlpha
        )
        _style.value = r
    }

    private fun refreshPerms() {
        val m = HashMap<String, List<String>>()
        for (p in _plugins.value) {
            val l = ArrayList<String>()
            hosts[p.id]?.takeIf { it.isNotEmpty() }?.let { l += "Сеть: " + it.joinToString(", ") }
            if (p.id in readers) l += "Читает сообщения"
            if (l.isNotEmpty()) m[p.id] = l
        }
        _perms.value = m
    }

    /* ---- events and actions that the app sends INTO plugins */

    /** A new message arrived in the chat that is open. Secret chats are never passed to plugins. */
    fun dispatchMessage(chatId: String, text: String, sender: String, mine: Boolean, group: Boolean, ts: Long) {
        if (readers.isEmpty()) return
        val o = JSONObject().put("chatId", chatId).put("text", text.take(4000)).put("sender", sender).put("mine", mine).put("group", group).put("ts", ts)
        main.post { for (pid in readers) views[pid]?.evaluateJavascript("window.__ev('message',$o)", null) }
    }

    /** The person tapped a plugin item in the message menu (ctx = the message) or the chat menu. */
    fun runAction(pid: String, id: String, ctx: JSONObject) {
        main.post { views[pid]?.evaluateJavascript("window.__action(${JSONObject.quote(id)},$ctx)", null) }
    }

    /** Asks the plugin to draw one of its pages: returns a JSON array of nodes or null. */
    suspend fun renderPage(pid: String, pageId: String): String? = withTimeoutOrNull(1500) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { cont ->
                val wv = views[pid]
                if (wv == null) { cont.resume(null); return@suspendCancellableCoroutine }
                wv.evaluateJavascript("window.__page(${JSONObject.quote(pageId)})") { raw ->
                    val v = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
                    if (cont.isActive) cont.resume(v as? String)
                }
            }
        }
    }

    fun pageEvent(pid: String, pageId: String, nodeId: String, value: String) {
        main.post {
            views[pid]?.evaluateJavascript(
                "window.__pageEvent(${JSONObject.quote(pageId)},${JSONObject.quote(nodeId)},${JSONObject.quote(value.take(2000))})", null
            )
        }
    }

    /** Lets plugins with an onSend hook rewrite the text (each gets 0.8 s, then the text goes on unchanged). */
    suspend fun transformOutgoing(text: String): String {
        var result = text
        val ids = withContext(Dispatchers.Main) { sendHooks.toList() }
        for (pid in ids) {
            val out = withTimeoutOrNull(800) {
                suspendCancellableCoroutine<String?> { cont ->
                    val wv = views[pid]
                    if (wv == null) { cont.resume(null); return@suspendCancellableCoroutine }
                    wv.evaluateJavascript("window.__send(${JSONObject.quote(result)})") { raw ->
                        val v = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
                        if (cont.isActive) cont.resume(v as? String)
                    }
                }
            }
            if (!out.isNullOrEmpty()) result = out.take(4000)
        }
        return result
    }

    /** "#RRGGBB" or "#RRGGBBAA" (CSS order) -> ARGB. */
    private fun color(s: String?): Long? {
        val t = s?.trim()?.removePrefix("#") ?: return null
        if (!t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return when (t.length) {
            6 -> 0xFF000000L or t.toLong(16)
            8 -> { val v = t.toLong(16); ((v and 0xFF) shl 24) or (v shr 8) }
            else -> null
        }
    }

    private fun stop(pid: String) {
        styles.remove(pid); sendHooks.remove(pid); readers.remove(pid); hosts.remove(pid); mergeStyles()
        views.remove(pid)?.let { runCatching { it.stopLoading(); it.destroy() } }
        _defs.value = _defs.value.filter { it.pluginId != pid }
        _plugins.value = _plugins.value.filter { it.id != pid }
        _actions.value = _actions.value.filter { it.pluginId != pid }
        _pages.value = _pages.value.filter { it.pluginId != pid }
        refreshPerms()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun start(pid: String, name: String, code: String) {
        stop(pid)
        val wv = WebView(app)
        with(wv.settings) {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            blockNetworkImage = true
            domStorageEnabled = false
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        wv.webViewClient = object : WebViewClient() {
            // Everything except the page we load ourselves is answered with an empty response.
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                if (request?.url?.toString()?.startsWith(BASE) == true && request.isForMainFrame) null
                else WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), "".byteInputStream())
        }
        wv.addJavascriptInterface(Bridge(pid, name), "__native")
        views[pid] = wv
        _plugins.value = _plugins.value.filter { it.id != pid } + CodePlugin(pid, name)

        val stored = JSONObject()
        values.all.forEach { (k, v) -> if (k.startsWith("$pid.") && v is String) stored.put(k.removePrefix("$pid."), v) }
        val html = "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline'\"></head><body>" +
            "<script>" + BOOT.replace("__STORED__", stored.toString()) + "</script>" +
            "<script>try{\n" + code + "\n}catch(e){__native.call(JSON.stringify({t:'error',v:String(e)}))}</script></body></html>"
        wv.loadDataWithBaseURL(BASE, html, "text/html", "utf-8", null)
    }

    private fun handle(pid: String, pname: String, o: JSONObject) {
        when (o.optString("t")) {
            "setting" -> {
                val d = o.optJSONObject("def") ?: return
                val id = d.optString("id").take(40)
                val type = d.optString("type")
                if (id.isEmpty() || type !in TYPES) return
                if (_defs.value.count { it.pluginId == pid } >= MAX_SETTINGS) return
                val opts = d.optJSONArray("options") ?: JSONArray()
                val def = PluginSetting(
                    pid, pname, id, d.optString("title", id).take(60), type, d.optString("description").take(200),
                    d.optDouble("min", 0.0).toFloat(), d.optDouble("max", 1.0).toFloat(),
                    (0 until opts.length()).map { opts.optString(it).take(40) }.take(20),
                    d.opt("default")?.toString().orEmpty().take(200)
                )
                _defs.value = _defs.value.filter { !(it.pluginId == pid && it.id == id) } + def
            }
            "theme" -> {
                val t = o.optJSONObject("o") ?: return
                scope.launch {
                    if (t.has("hue")) settings.setHue(t.optDouble("hue").toFloat())
                    if (t.has("dark")) settings.setDark(t.optBoolean("dark"))
                }
            }
            "style" -> {
                val t = o.optJSONObject("o") ?: return
                val colors = HashMap<String, Long>()
                t.optJSONObject("colors")?.let { c -> c.keys().asSequence().take(60).forEach { k -> color(c.optString(k))?.let { v -> colors[k] = v } } }
                val g = t.optJSONObject("glass")
                val blobs = ArrayList<Long>()
                g?.optJSONArray("blobs")?.let { a -> for (i in 0 until minOf(a.length(), 6)) color(a.optString(i))?.let { blobs += it } }
                styles[pid] = PluginStyle(
                    cornerScale = t.optDouble("cornerScale", 1.0).toFloat().coerceIn(0f, 2f),
                    mine = color(t.optString("bubbleMine")), other = color(t.optString("bubbleOther")), chatBg = color(t.optString("chatBackground")),
                    colors = colors,
                    glass = g != null && g.optBoolean("enabled", true),
                    glassAlpha = (g?.optDouble("alpha", 0.5) ?: 0.5).toFloat().coerceIn(0.05f, 0.95f),
                    glassBlobs = blobs, glassBorder = g?.optBoolean("border", true) ?: true,
                    font = t.optString("font").takeIf { it in FONTS }.orEmpty(),
                    bubbleAlpha = t.optDouble("bubbleAlpha", 1.0).toFloat().coerceIn(0.2f, 1f)
                )
                mergeStyles()
            }
            "hook" -> if (o.optString("v") == "send") {
                if (!hosts[pid].isNullOrEmpty()) _messages.tryEmit("«$pname»: плагин с доступом в сеть не может читать сообщения")
                else { sendHooks.add(pid); readers.add(pid); refreshPerms() }
            }
            "listen" -> if (o.optString("v") == "message") {
                if (!hosts[pid].isNullOrEmpty()) _messages.tryEmit("«$pname»: плагин с доступом в сеть не может читать сообщения")
                else { readers.add(pid); refreshPerms() }
            }
            "hosts" -> {
                if (pid in readers) { _messages.tryEmit("«$pname»: плагин, который читает сообщения, не может ходить в сеть"); return }
                val a = o.optJSONArray("v") ?: return
                val set = LinkedHashSet<String>()
                for (i in 0 until minOf(a.length(), 5)) {
                    val h = a.optString(i).trim().lowercase()
                    if (HOST.matches(h) && !h.all { it.isDigit() || it == '.' }) set += h
                }
                hosts[pid] = set; refreshPerms()
            }
            "action" -> {
                val d = o.optJSONObject("def") ?: return
                val id = d.optString("id").take(40); val place = d.optString("place")
                if (id.isEmpty() || place !in setOf("message", "chat")) return
                if (_actions.value.count { it.pluginId == pid } >= 8) return
                _actions.value = _actions.value.filter { !(it.pluginId == pid && it.id == id) } +
                    PluginAction(pid, pname, id, d.optString("title", id).take(40), place)
            }
            "page" -> {
                val d = o.optJSONObject("def") ?: return
                val id = d.optString("id").take(40)
                if (id.isEmpty() || _pages.value.count { it.pluginId == pid } >= 5) return
                _pages.value = _pages.value.filter { !(it.pluginId == pid && it.id == id) } + PluginPage(pid, pname, id, d.optString("title", id).take(40))
            }
            "send" -> {
                val text = o.optString("text").trim().take(2000)
                if (text.isEmpty()) return
                val now = System.currentTimeMillis()
                val q = sentAt.getOrPut(pid) { ArrayDeque() }
                while (q.isNotEmpty() && now - q.first() > 60_000) q.removeFirst()
                if (q.size >= 15 || (q.isNotEmpty() && now - q.last() < 1_500)) { _messages.tryEmit("«$pname»: слишком частая отправка, сообщение пропущено"); return }
                q.addLast(now)
                _commands.tryEmit(PluginCommand(pid, text, o.optString("chatId").takeIf { it.isNotEmpty() }))
            }
            "scale" -> scope.launch { settings.setTextScale(o.optDouble("v", 1.0).toFloat()) }
            "toast" -> _messages.tryEmit("$pname: ${o.optString("v").take(200)}")
            "error" -> _messages.tryEmit("Ошибка плагина «$pname»: ${o.optString("v").take(200)}")
        }
    }

    private inner class Bridge(val pid: String, val pname: String) {
        @JavascriptInterface
        fun call(json: String) {
            if (json.length > 20_000) return
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return
            main.post { handle(pid, pname, o) }
        }

        @JavascriptInterface
        fun sget(key: String): String = store.getString("$pid.${key.take(60)}", "") ?: ""

        @JavascriptInterface
        fun sset(key: String, value: String) {
            val k = "$pid.${key.take(60)}"
            if (value.length > 50_000) return
            if (!store.contains(k) && store.all.keys.count { it.startsWith("$pid.") } >= 200) return
            if (value.isEmpty()) store.edit().remove(k).apply() else store.edit().putString(k, value).apply()
        }

        @JavascriptInterface
        fun http(id: String, json: String) {
            if (json.length > 80_000) return
            scope.launch(Dispatchers.IO) {
                val res = runCatching { doHttp(pid, JSONObject(json)) }.getOrElse {
                    JSONObject().put("ok", false).put("status", 0).put("text", "").put("error", (it.message ?: "ошибка").take(200))
                }
                withContext(Dispatchers.Main) {
                    views[pid]?.evaluateJavascript("window.__http(${JSONObject.quote(id)},${JSONObject.quote(res.toString())})", null)
                }
            }
        }
    }

    /** One HTTPS request of a plugin. Only hosts the plugin declared, no redirects, no private addresses, small answers. */
    private fun doHttp(pid: String, o: JSONObject): JSONObject {
        if (pid in readers) error("Плагин читает сообщения, сеть ему запрещена")
        val allowed = hosts[pid].orEmpty()
        val url = java.net.URL(o.getString("url"))
        if (url.protocol != "https") error("Только https")
        val host = url.host.lowercase()
        if (allowed.none { host == it || host.endsWith(".$it") }) error("Хост $host не разрешён плагином (bliss.http.allow)")
        if (java.net.InetAddress.getAllByName(host).any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress })
            error("Локальные адреса запрещены")
        val now = System.currentTimeMillis()
        synchronized(httpAt) {
            val q = httpAt.getOrPut(pid) { ArrayDeque() }
            while (q.isNotEmpty() && now - q.first() > 60_000) q.removeFirst()
            if (q.size >= 30) error("Не больше 30 запросов в минуту")
            q.addLast(now)
        }
        val method = o.optString("method", "GET").uppercase().takeIf { it in setOf("GET", "POST", "PUT", "DELETE") } ?: "GET"
        val c = url.openConnection() as java.net.HttpURLConnection
        try {
            c.requestMethod = method
            c.instanceFollowRedirects = false
            c.connectTimeout = 8_000; c.readTimeout = 10_000
            o.optJSONObject("headers")?.let { h -> h.keys().asSequence().take(10).forEach { k -> c.setRequestProperty(k.take(60), h.optString(k).take(300)) } }
            val body = o.optString("body")
            if (body.isNotEmpty() && method != "GET") { c.doOutput = true; c.outputStream.use { it.write(body.take(50_000).toByteArray()) } }
            val code = c.responseCode
            val stream = if (code in 200..399) c.inputStream else c.errorStream
            val text = stream?.use { st ->
                val buf = java.io.ByteArrayOutputStream(); val tmp = ByteArray(8192); var total = 0
                while (true) { val n = st.read(tmp); if (n < 0 || total + n > 200_000) break; buf.write(tmp, 0, n); total += n }
                buf.toString("UTF-8")
            }.orEmpty()
            return JSONObject().put("ok", code in 200..299).put("status", code).put("text", text)
        } finally { c.disconnect() }
    }

    companion object {
        const val MAX_CODE = 100 * 1024
        private const val MAX_SETTINGS = 30
        private const val BASE = "https://plugin.invalid/"
        private val TYPES = setOf("switch", "slider", "choice", "text", "button")
        private val FONTS = setOf("sans", "serif", "mono", "cursive", "default")
        private val HOST = Regex("^[a-z0-9][a-z0-9.-]{2,98}[a-z0-9]$")

        private const val BOOT = """
(function () {
  var R = __STORED__, V = {}, D = {}, H = {}, A = {}, P = {}, HT = {}, hid = 0;
  function send(o) { __native.call(JSON.stringify(o)); }
  function cv(d, raw) {
    if (raw === undefined || raw === null) raw = (d.default === undefined ? '' : String(d.default));
    if (d.type === 'switch') return raw === 'true';
    if (d.type === 'slider') { var n = parseFloat(raw); return isNaN(n) ? (d.min || 0) : n; }
    return raw;
  }
  window.bliss = {
    settings: {
      add: function (d) { D[d.id] = d; V[d.id] = cv(d, R[d.id]); send({t: 'setting', def: d}); },
      get: function (id) { return V[id]; }
    },
    app: {
      setTheme: function (o) { send({t: 'theme', o: o}); },
      setTextScale: function (x) { send({t: 'scale', v: x}); },
      setStyle: function (o) { send({t: 'style', o: o}); }
    },
    chat: {
      onSend: function (fn) { (H['send'] = H['send'] || []).push(fn); send({t: 'hook', v: 'send'}); }
    },
    toast: function (s) { send({t: 'toast', v: String(s)}); },
    on: function (ev, fn) { (H[ev] = H[ev] || []).push(fn); if (ev === 'message') send({t: 'listen', v: 'message'}); },
    storage: {
      get: function (k) { var s = __native.sget(String(k)); if (!s) return undefined; try { return JSON.parse(s); } catch (e) { return undefined; } },
      set: function (k, v) { __native.sset(String(k), v === undefined || v === null ? '' : JSON.stringify(v)); },
      remove: function (k) { __native.sset(String(k), ''); }
    },
    http: {
      allow: function (hosts) { send({t: 'hosts', v: hosts}); },
      fetch: function (url, o, cb) {
        if (typeof o === 'function') { cb = o; o = {}; }
        o = o || {};
        var p = new Promise(function (res) {
          var id = String(++hid); HT[id] = res;
          __native.http(id, JSON.stringify({url: String(url), method: o.method || 'GET', headers: o.headers || {}, body: o.body == null ? '' : String(o.body)}));
        });
        if (cb) p.then(cb);
        return p;
      }
    },
    ui: {
      addMessageAction: function (d, fn) { A[d.id] = fn; send({t: 'action', def: {id: d.id, title: d.title, place: 'message'}}); },
      addChatAction: function (d, fn) { A[d.id] = fn; send({t: 'action', def: {id: d.id, title: d.title, place: 'chat'}}); },
      addPage: function (d) { P[d.id] = d; send({t: 'page', def: {id: d.id, title: d.title}}); }
    }
  };
  window.bliss.chat.send = function (text, chatId) { send({t: 'send', text: String(text), chatId: chatId || ''}); };
  window.__ev = function (n, p) { (H[n] || []).forEach(function (f) { try { f(p); } catch (e) {} }); };
  window.__action = function (id, ctx) { var f = A[id]; if (f) { try { f(ctx); } catch (e) { send({t: 'error', v: String(e)}); } } };
  window.__page = function (id) { var d = P[id]; if (!d || !d.render) return null; try { return JSON.stringify(d.render()); } catch (e) { send({t: 'error', v: String(e)}); return null; } };
  window.__pageEvent = function (id, node, val) { var d = P[id]; if (d && d.onEvent) { try { d.onEvent(node, val); } catch (e) { send({t: 'error', v: String(e)}); } } };
  window.__http = function (id, json) {
    var f = HT[id]; delete HT[id]; if (!f) return;
    var r; try { r = JSON.parse(json); } catch (e) { r = {ok: false, status: 0, text: '', error: 'bad response'}; }
    r.json = function () { return JSON.parse(r.text); };
    f(r);
  };
  window.__send = function (t) {
    var r = t;
    (H['send'] || []).forEach(function (f) { try { var x = f(r); if (typeof x === 'string') r = x; } catch (e) {} });
    return r;
  };
  window.__set = function (id, raw) {
    var d = D[id]; if (!d) return;
    V[id] = d.type === 'button' ? true : cv(d, raw);
    (H['setting'] || []).forEach(function (f) { try { f(id, V[id]); } catch (e) {} });
  };
})();
"""
    }
}
