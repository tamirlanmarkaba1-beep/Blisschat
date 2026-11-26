package com.bliss.blisschat.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The latest GitHub release: [versionName] is its tag (e.g. "v1.0.3"), [changelog] its description, [apkUrl] the .apk asset. */
data class UpdateInfo(val versionCode: Long = 0L, val versionName: String, val apkUrl: String, val changelog: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    /** [progress] is 0..1, or -1 while the size is still unknown. */
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** Downloaded; the system installer is (or must be) opened. [needsPermission] = "install unknown apps" is still off. */
    data class ReadyToInstall(val info: UpdateInfo, val needsPermission: Boolean) : UpdateState
    data class Failed(val message: String) : UpdateState
}

data class UpdateUi(
    val state: UpdateState = UpdateState.Idle,
    val lastChecked: Long = 0L,
    /** The user pressed "Позже" for this launch: the dialog stays hidden, the update centre still shows it. */
    val dismissed: Boolean = false
)

/**
 * Auto-update through GitHub Releases:
 *  1. [check] asks the GitHub API for the latest release and compares its tag with the installed versionName.
 *  2. [download] fetches the APK with the system DownloadManager (Download folder of the app).
 *  3. When the file is ready, [install] opens the system installer through a FileProvider.
 */
class UpdateManager(context: Context) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow(UpdateUi())
    val ui: StateFlow<UpdateUi> = _ui.asStateFlow()

    private var downloadId = -1L
    private var progressJob: Job? = null
    private var receiver: BroadcastReceiver? = null

    val currentVersionCode: Long
        get() = PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
    val currentVersionName: String
        get() = com.bliss.blisschat.BuildConfig.VERSION_NAME

    /** @param silent true on app start: errors and "up to date" are not turned into dialogs. */
    fun check(silent: Boolean = true) {
        val st = _ui.value.state
        if (st is UpdateState.Checking || st is UpdateState.Downloading) return
        _ui.update { it.copy(state = UpdateState.Checking, dismissed = false) }
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetch() } }
            val now = System.currentTimeMillis()
            result.onSuccess { info ->
                _ui.update {
                    it.copy(lastChecked = now, state = if (isNewer(info.versionName)) UpdateState.Available(info) else UpdateState.UpToDate)
                }
            }.onFailure { e ->
                _ui.update { it.copy(lastChecked = now, state = if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Не удалось проверить обновления")) }
            }
        }
    }

    /** Tag "v1.0.3" and versionName "1.0.3" are the same version: leading v/V and spaces are ignored. */
    private fun norm(v: String) = v.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * A different tag means an update. If both look like dotted numbers and the tag is LOWER than the installed version,
     * it is not offered: Android refuses to install an older build over a newer one anyway.
     */
    private fun isNewer(tag: String): Boolean {
        val remote = norm(tag)
        val local = norm(currentVersionName)
        if (remote.isEmpty() || remote.equals(local, ignoreCase = true)) return false
        fun parts(s: String) = s.split('.', '-').map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val r = parts(remote); val l = parts(local)
        if (r.all { it != null } && l.all { it != null }) {
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrNull(i) ?: 0; val b = l.getOrNull(i) ?: 0
                if (a != b) return a > b
            }
            return false
        }
        return true
    }

    private fun fetch(): UpdateInfo {
        val conn = URL(RELEASE_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "blisschat-android")
            when (conn.responseCode) {
                200 -> {}
                404 -> error("Релизы не найдены: проверьте репозиторий (он должен быть публичным) и что есть опубликованный релиз")
                403, 429 -> error("GitHub ограничил число запросов, попробуйте позже")
                else -> error("Сервер обновлений ответил ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            var apk = ""
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.optString("name")
                // the regular app takes the plain .apk, the glass app the one with "glass" in its name
                if (n.endsWith(".apk", ignoreCase = true) && n.contains("glass", ignoreCase = true) == com.bliss.blisschat.BuildConfig.GLASS) {
                    apk = a.optString("browser_download_url"); break
                }
            }
            if (apk.isEmpty()) error(if (com.bliss.blisschat.BuildConfig.GLASS) "В последнем релизе пока нет blisschat-glass.apk" else "В последнем релизе нет .apk файла")
            return UpdateInfo(
                versionName = json.getString("tag_name"),
                apkUrl = apk,
                changelog = json.optString("body")
            )
        } finally {
            conn.disconnect()
        }
    }

    fun dismiss() { _ui.update { it.copy(dismissed = true) } }

    fun download() {
        val info = (_ui.value.state as? UpdateState.Available)?.info
            ?: (_ui.value.state as? UpdateState.Failed)?.let { lastInfo } ?: return
        lastInfo = info
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { apkFile().delete() }
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("blisschat ${info.versionName}")
            .setDescription("Загрузка обновления")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, FILE_NAME)
        downloadId = try { dm.enqueue(request) } catch (e: Exception) {
            _ui.update { it.copy(state = UpdateState.Failed(e.message ?: "Не удалось начать загрузку")) }
            return
        }
        _ui.update { it.copy(state = UpdateState.Downloading(info, -1f), dismissed = false) }
        registerReceiver(info)
        watchProgress(dm, info)
    }

    private var lastInfo: UpdateInfo? = null

    private fun registerReceiver(info: UpdateInfo) {
        unregisterReceiver()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                unregisterReceiver()
                progressJob?.cancel()
                val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val ok = dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cur ->
                    cur.moveToFirst() && cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                }
                if (ok && apkFile().exists()) install(info)
                else _ui.update { it.copy(state = UpdateState.Failed("Не удалось скачать обновление"), dismissed = false) }
            }
        }
        receiver = r
        ContextCompat.registerReceiver(app, r, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun unregisterReceiver() {
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
    }

    private fun watchProgress(dm: DownloadManager, info: UpdateInfo) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(400)
                val p = withContext(Dispatchers.IO) {
                    dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cur ->
                        if (!cur.moveToFirst()) return@use -2f
                        val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        if (total > 0) done.toFloat() / total else -1f
                    }
                }
                if (p == -2f) break
                _ui.update { if (it.state is UpdateState.Downloading) it.copy(state = UpdateState.Downloading(info, p)) else it }
            }
        }
    }

    private fun apkFile() = File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)

    /** Opens the system package installer for the downloaded APK (through our FileProvider). */
    fun install(info: UpdateInfo? = null) {
        val i = info ?: lastInfo ?: return
        lastInfo = i
        val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()
        if (!canInstall) {
            _ui.update { it.copy(state = UpdateState.ReadyToInstall(i, needsPermission = true), dismissed = false) }
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(settings) }
            return
        }
        val file = apkFile()
        if (!file.exists()) {
            _ui.update { it.copy(state = UpdateState.Failed("Файл обновления не найден, скачайте его заново"), dismissed = false) }
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        _ui.update { it.copy(state = UpdateState.ReadyToInstall(i, needsPermission = false), dismissed = false) }
        runCatching { app.startActivity(intent) }.onFailure {
            _ui.update { s -> s.copy(state = UpdateState.Failed("Не удалось открыть установщик")) }
        }
    }

    companion object {
        /** owner/repo of the GitHub repository whose Releases carry the APK. Change it here if it is different. */
        const val GITHUB_REPO = "tamirlanmarkaba1-beep/BlissAi"
        const val RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val FILE_NAME = "blisschat-update.apk"
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
