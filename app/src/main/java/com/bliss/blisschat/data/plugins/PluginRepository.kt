package com.bliss.blisschat.data.plugins

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bliss.blisschat.data.SettingsStore
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.data.mapFailure
import com.bliss.blisschat.data.stickers.StickerStore
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

data class Plugin(
    val id: String, val name: String, val version: String, val description: String, val type: String,
    val authorId: String, val author: String, val status: String, val note: String, val size: Int, val createdAt: Long
) {
    val typeLabel get() = when (type) { "theme" -> "Тема"; "code" -> "Код"; else -> "Стикеры" }
}

data class Manifest(val name: String, val version: String, val description: String, val type: String, val hue: Float?, val dark: Boolean?, val images: Int, val code: String? = null)

/**
 * Plugins are .zip files with a manifest.json:
 *   {"name":"Ocean","version":"1.0","description":"...","type":"theme","theme":{"hue":200,"dark":true}}
 *   {"name":"Cats","version":"1.0","type":"stickers"}  + images (png/jpg/webp) in the archive
 *   {"name":"Night mode","version":"1.0","type":"code"} + main.js: a script that runs in a sandbox (see [PluginRuntime])
 * New plugins wait ("pending") until a moderator approves them; only approved ones are in the catalogue.
 * Themes and sticker packs are plain data. Code plugins run in an isolated WebView without network or file access
 * and can only use the small `bliss` API of [PluginRuntime] (new settings, theme, text size), so installing is safe.
 */
class PluginRepository(private val context: Context, private val stickers: StickerStore, private val settings: SettingsStore) {
    companion object {
        const val MAX_ZIP = 6 * 1024 * 1024
        private const val CHUNK = 900 * 1024
        const val MAX_STICKERS = 500
        private const val MAX_UNPACKED = 24 * 1024 * 1024
        private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp")
    }

    private val db get() = FirebaseFirestore.getInstance()
    private val col get() = db.collection("plugins")
    private val files get() = db.collection("pluginFiles")
    private val prefs = context.getSharedPreferences("plugins", Context.MODE_PRIVATE)
    val runtime = PluginRuntime(context, settings)

    /** plugin id -> sticker pack id ("" when the plugin created none) */
    private val _installed = MutableStateFlow(loadInstalled())
    val installed: StateFlow<Map<String, String>> = _installed.asStateFlow()

    private fun loadInstalled(): Map<String, String> =
        prefs.getStringSet("installed", emptySet()).orEmpty().mapNotNull { e -> e.split("|", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    private fun saveInstalled(m: Map<String, String>) {
        prefs.edit().putStringSet("installed", m.map { "${it.key}|${it.value}" }.toSet()).apply()
        _installed.value = m
    }

    private fun DocumentSnapshot.toPlugin(): Plugin? {
        val n = getString("name") ?: return null
        return Plugin(
            id = id, name = n, version = getString("version").orEmpty(), description = getString("description").orEmpty(),
            type = getString("type").orEmpty(), authorId = getString("authorId").orEmpty(), author = getString("author").orEmpty(),
            status = getString("status").orEmpty(), note = getString("note").orEmpty(),
            size = (getLong("size") ?: 0L).toInt(), createdAt = getLong("createdAt") ?: 0L
        )
    }

    private fun observe(q: Query): Flow<List<Plugin>> = callbackFlow {
        val reg = q.addSnapshotListener { snap, e ->
            if (e != null) { trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents.orEmpty().mapNotNull { it.toPlugin() }.sortedByDescending { it.createdAt })
        }
        awaitClose { reg.remove() }
    }

    fun observeApproved() = observe(col.whereEqualTo("status", "approved"))
    fun observeMine(uid: String) = observe(col.whereEqualTo("authorId", uid))
    fun observePending() = observe(col.whereEqualTo("status", "pending"))

    /** Validates the archive and returns what is inside. */
    fun parse(bytes: ByteArray): Manifest {
        require(bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) { "Принимаются только файлы .zip" }
        var manifest: JSONObject? = null
        var mainJs: String? = null
        var images = 0
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val name = e.name
                require(!name.contains("..")) { "Недопустимый путь в архиве: $name" }
                if (e.isDirectory) continue
                val data = zin.readBytes()
                total += data.size
                require(total <= MAX_UNPACKED) { "Архив слишком большой после распаковки" }
                if (name == "manifest.json") manifest = JSONObject(String(data, Charsets.UTF_8))
                else if (name == "main.js") mainJs = String(data, Charsets.UTF_8)
                else if (name.substringAfterLast('.', "").lowercase() in IMAGE_EXT) images++
            }
        }
        val m = manifest ?: error("В архиве нет manifest.json (в корне)")
        val type = m.optString("type")
        require(type == "theme" || type == "stickers" || type == "code") { "type должен быть theme, stickers или code" }
        val name = m.optString("name").trim()
        require(name.isNotEmpty() && name.length <= 40) { "Укажите name (до 40 символов)" }
        var hue: Float? = null
        var dark: Boolean? = null
        if (type == "theme") {
            val t = m.optJSONObject("theme") ?: error("Для темы нужен объект theme")
            require(t.has("hue")) { "В theme нужен hue (0–360)" }
            hue = t.getDouble("hue").toFloat().coerceIn(0f, 360f)
            if (t.has("dark")) dark = t.getBoolean("dark")
        } else if (type == "code") {
            val js = mainJs ?: error("Для плагина-кода нужен файл main.js в корне архива")
            require(js.length <= PluginRuntime.MAX_CODE) { "main.js больше 100 КБ" }
            return Manifest(name, m.optString("version", "1.0").take(16), m.optString("description").take(300), type, null, null, 0, js)
        } else {
            require(images in 1..MAX_STICKERS) { "Для стикеров нужно от 1 до $MAX_STICKERS картинок" }
        }
        return Manifest(name, m.optString("version", "1.0").take(16), m.optString("description").take(300), type, hue, dark, images)
    }

    suspend fun submit(me: UserProfile, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать файл")
            require(bytes.size <= MAX_ZIP) { "Архив больше 6 МБ" }
            val m = parse(bytes)
            val id = col.document().id
            val chunks = (0 until (bytes.size + CHUNK - 1) / CHUNK).map { bytes.copyOfRange(it * CHUNK, minOf(bytes.size, (it + 1) * CHUNK)) }
            files.document(id).set(mapOf("zip" to Blob.fromBytes(chunks[0]), "n" to chunks.size, "authorId" to me.uid)).await()
            try {
                for (i in 1 until chunks.size) files.document(id).collection("parts").document(i.toString()).set(mapOf("zip" to Blob.fromBytes(chunks[i]))).await()
                col.document(id).set(
                    mapOf(
                        "name" to m.name, "version" to m.version, "description" to m.description, "type" to m.type,
                        "authorId" to me.uid, "author" to me.title, "status" to "pending", "note" to "",
                        "size" to bytes.size, "createdAt" to System.currentTimeMillis()
                    )
                ).await()
            } catch (e: Exception) {
                runCatching { deleteFiles(id) }
                throw e
            }
            Unit
        }.mapFailure()
    }

    suspend fun moderate(actor: UserProfile, id: String, approve: Boolean, note: String): Result<Unit> = runCatching {
        require(actor.moderator || actor.isAdmin) { "Нужны права модератора" }
        col.document(id).update(
            mapOf("status" to if (approve) "approved" else "rejected", "reviewedBy" to actor.uid, "note" to note.trim().take(300))
        ).await()
        Unit
    }.mapFailure()

    suspend fun downloadZip(id: String): ByteArray = withContext(Dispatchers.IO) {
        val head = files.document(id).get().await()
        val first = head.getBlob("zip")?.toBytes() ?: error("Файл плагина не найден")
        val n = (head.getLong("n") ?: 1L).toInt()
        if (n <= 1) return@withContext first
        val out = java.io.ByteArrayOutputStream(first.size * n)
        out.write(first)
        for (i in 1 until n) {
            out.write(files.document(id).collection("parts").document(i.toString()).get().await().getBlob("zip")?.toBytes() ?: error("Часть плагина $i не найдена"))
        }
        out.toByteArray()
    }

    private suspend fun deleteFiles(id: String) {
        val n = (files.document(id).get().await().getLong("n") ?: 1L).toInt()
        for (i in 1 until n) runCatching { files.document(id).collection("parts").document(i.toString()).delete().await() }
        files.document(id).delete().await()
    }

    /** Saves the archive to the app's Plugins folder and returns an intent that lets the moderator share/open it. */
    suspend fun exportForReview(p: Plugin): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = downloadZip(p.id)
            val dir = File(context.getExternalFilesDir(null), "Plugins").apply { mkdirs() }
            val safe = p.name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            val f = File(dir, "$safe-${p.version}.zip")
            f.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Скачанный плагин: ${f.name}"
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.mapFailure()
    }

    suspend fun install(p: Plugin): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = downloadZip(p.id)
            val m = parse(bytes)
            var packId = ""
            if (m.type == "code") {
                runtime.install(p.id, m.name, m.code.orEmpty())
            } else if (m.type == "theme") {
                m.hue?.let { settings.setHue(it) }
                m.dark?.let { settings.setDark(it) }
            } else {
                val pack = stickers.createPack(m.name)
                packId = pack.id
                var added = 0
                ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        if (e.isDirectory || e.name.substringAfterLast('.', "").lowercase() !in IMAGE_EXT) continue
                        if (added >= MAX_STICKERS) break
                        if (stickers.addBytes(pack.id, zin.readBytes()).isSuccess) added++
                    }
                }
                if (added == 0) { stickers.deletePack(pack.id); error("В плагине нет подходящих картинок") }
            }
            saveInstalled(_installed.value + (p.id to packId))
        }.mapFailure()
    }

    fun uninstall(p: Plugin) {
        val pack = _installed.value[p.id].orEmpty()
        runtime.remove(p.id)
        if (pack.isNotEmpty()) stickers.deletePack(pack)
        saveInstalled(_installed.value - p.id)
    }

    suspend fun deleteMine(p: Plugin): Result<Unit> = runCatching {
        col.document(p.id).delete().await()
        runCatching { deleteFiles(p.id) }
        Unit
    }.mapFailure()
}
