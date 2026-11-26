package com.bliss.blisschat.ui.chat

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bliss.blisschat.data.ChatRepository
import com.bliss.blisschat.data.Content
import com.bliss.blisschat.data.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ shared callbacks for bubbles */

/** Lets message bubbles talk to the screen without threading new parameters through every composable. */
class ChatActions(
    val replyPreview: (String) -> String? = { null },
    val onStickerTap: (UiMessage) -> Unit = {},
    val onVote: (UiMessage, Int) -> Unit = { _, _ -> },
    val onReply: (UiMessage) -> Unit = {},
    val toast: (String) -> Unit = {},
    val myId: String = ""
)

val LocalChatActions = compositionLocalOf { ChatActions() }

/* ------------------------------------------------------------------ swipe right to reply */

@Composable
fun SwipeToReplyBox(enabled: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val view = LocalView.current
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    val maxPull = threshold * 1.4f
    var off by remember { mutableFloatStateOf(0f) }
    var fired by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { fired = false },
                onHorizontalDrag = { change, delta ->
                    if (off > 0f || delta > 0f) change.consume()
                    off = (off + delta * 0.55f).coerceIn(0f, maxPull)
                    if (!fired && off >= threshold) { fired = true; view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                },
                onDragEnd = {
                    if (fired) onReply()
                    scope.launch { animate(off, 0f) { v, _ -> off = v } }
                },
                onDragCancel = { scope.launch { animate(off, 0f) { v, _ -> off = v } } }
            )
        }
    ) {
        val p = (off / threshold).coerceIn(0f, 1f)
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(34.dp).alpha(p)
                .clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.Reply, "Ответить", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Box(Modifier.offset { IntOffset(off.roundToInt(), 0) }) { content() }
    }
}

/** Short text shown for a replied-to message. */
fun replyPreviewOf(u: UiMessage): String = when {
    u.message.deleted -> "Сообщение удалено"
    u.content == null -> "🔒 Сообщение"
    u.content.kind == Content.KIND_POLL -> "📊 " + (parsePoll(u.content.text)?.first ?: "Опрос")
    u.content.kind == Content.KIND_FILE -> "📎 " + (fileMeta(u.content.text).name)
    else -> u.content.preview
}

/** Bar above the input field while replying. */
@Composable
fun ReplyBar(senderName: String, preview: String, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Отменить ответ") }
    }
}

/** Quote of the replied-to message inside a bubble. */
@Composable
fun QuoteBlock(replyToId: String, fg: Color) {
    val text = LocalChatActions.current.replyPreview(replyToId) ?: return
    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = 0.6f)))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/* ------------------------------------------------------------------ voice recording */

class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val running get() = rec != null
    fun elapsedSeconds(): Int = if (rec == null) 0 else ((System.currentTimeMillis() - startedAt) / 1000L).toInt()

    fun start(): Boolean {
        cancel()
        return runCatching {
            val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(24_000)
            r.setAudioEncodingBitRate(32_000) // ~4 KB/s: about two minutes fit into one encrypted message
            r.setMaxDuration(120_000)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            rec = r; file = f; startedAt = System.currentTimeMillis()
            true
        }.getOrElse { cancel(); false }
    }

    /** Returns the recorded file and its length in seconds, or null when it was too short / failed. */
    fun stop(): Pair<File, Int>? {
        val r = rec ?: return null
        val f = file
        val secs = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
        rec = null; file = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok || f == null || secs < 1) { f?.delete(); return null }
        return f to secs
    }

    fun cancel() {
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        rec = null
        file?.delete(); file = null
    }
}

/** Replaces the input row while a voice message is being recorded. */
@Composable
fun RecorderBar(recorder: VoiceRecorder, onSend: (File, Int) -> Unit, onCancel: () -> Unit) {
    var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            secs = recorder.elapsedSeconds()
            if (!recorder.running) { onCancel(); break } // the recorder died
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { recorder.cancel(); onCancel() }) { Icon(Icons.Filled.Delete, "Удалить запись", tint = MaterialTheme.colorScheme.error) }
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
        Spacer(Modifier.width(8.dp))
        Text("%d:%02d".format(secs / 60, secs % 60), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, onClick = {
            val r = recorder.stop()
            if (r != null) onSend(r.first, r.second) else onCancel()
        }) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimary) }
    }
}

/** Microphone permission + start. Returns a lambda to call when the mic button is tapped. */
@Composable
fun rememberStartRecording(recorder: VoiceRecorder, onStarted: () -> Unit, onError: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launch = { if (recorder.start()) onStarted() else onError("Не удалось начать запись") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launch() else onError("Нужно разрешение на микрофон")
    }
    return {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launch()
        else perm.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/* ------------------------------------------------------------------ audio playback (voice + music) */

@Composable
fun AudioRow(content: Content, repo: ChatRepository, fg: Color, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember(content.path) { mutableStateOf<MediaPlayer?>(null) }
    var loading by remember(content.path) { mutableStateOf(false) }
    var playing by remember(content.path) { mutableStateOf(false) }
    var progress by remember(content.path) { mutableFloatStateOf(0f) }
    var error by remember(content.path) { mutableStateOf(false) }

    DisposableEffect(content.path) { onDispose { runCatching { player?.release() }; player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player
            if (p != null && p.duration > 0) progress = p.currentPosition.toFloat() / p.duration
            delay(150)
        }
    }

    fun toggle() {
        val p = player
        if (p != null) {
            if (playing) { p.pause(); playing = false } else { p.start(); playing = true }
            return
        }
        if (loading) return
        loading = true; error = false
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { repo.downloadMedia(content.path, content.key) }
                val f = withContext(Dispatchers.IO) {
                    File(ctx.cacheDir, "play_${content.path.removePrefix("m:")}.bin").also { it.writeBytes(bytes) }
                }
                val mp = MediaPlayer()
                mp.setDataSource(f.absolutePath)
                mp.setOnCompletionListener { playing = false; progress = 0f; runCatching { it.seekTo(0) } }
                withContext(Dispatchers.IO) { mp.prepare() }
                player = mp; mp.start(); playing = true
            }.onFailure { error = true }
            loading = false
        }
    }

    Row(Modifier.width(230.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = fg.copy(alpha = 0.18f), onClick = { toggle() }, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = fg)
                    playing -> Icon(Icons.Filled.Pause, "Пауза", tint = fg)
                    else -> Icon(Icons.Filled.PlayArrow, "Воспроизвести", tint = fg)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = fg.copy(alpha = 0.7f))
                Spacer(Modifier.width(4.dp))
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = fg, trackColor = fg.copy(alpha = 0.25f)
            )
            Text(if (error) "Не удалось загрузить — нажмите ▶" else subtitle, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun VoiceBubble(content: Content, repo: ChatRepository, fg: Color) {
    val s = content.w
    AudioRow(content, repo, fg, "Голосовое сообщение", "%d:%02d".format(s / 60, s % 60), Icons.Filled.MusicNote)
}

data class FileMeta(val name: String, val size: Long, val mime: String)

fun fileMeta(text: String): FileMeta = runCatching {
    val o = JSONObject(text); FileMeta(o.optString("n", "файл"), o.optLong("s"), o.optString("m"))
}.getOrDefault(FileMeta("файл", 0, ""))

private fun sizeLabel(b: Long) = if (b >= 1_048_576) "%.1f МБ".format(b / 1_048_576.0) else "${(b / 1024).coerceAtLeast(1)} КБ"

/** Music plays in the bubble; other files are saved to Downloads on tap. */
@Composable
fun FileBubble(content: Content, repo: ChatRepository, fg: Color, onToast: (String) -> Unit) {
    val meta = fileMeta(content.text)
    if (meta.mime.startsWith("audio/")) {
        AudioRow(content, repo, fg, meta.name, sizeLabel(meta.size), Icons.Filled.MusicNote)
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(content.path) { mutableStateOf(false) }
    Row(
        Modifier.width(230.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy) {
            busy = true
            scope.launch {
                val ok = runCatching {
                    val bytes = withContext(Dispatchers.IO) { repo.downloadMedia(content.path, content.key) }
                    withContext(Dispatchers.IO) { saveToDownloads(ctx, meta.name, meta.mime, bytes) }
                }
                onToast(if (ok.isSuccess) "Сохранено в «Загрузки»" else "Не удалось сохранить файл")
                busy = false
            }
        }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = fg.copy(alpha = 0.18f), modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = fg)
                else Icon(Icons.Filled.InsertDriveFile, null, tint = fg)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(meta.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sizeLabel(meta.size) + " · нажмите, чтобы сохранить", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
        }
    }
}

private fun saveToDownloads(ctx: Context, name: String, mime: String, bytes: ByteArray) {
    if (Build.VERSION.SDK_INT >= 29) {
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/BlissChat")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("insert")
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
    } else {
        val dir = File(ctx.getExternalFilesDir(null), "Download").apply { mkdirs() }
        File(dir, name).writeBytes(bytes)
    }
}

/* ------------------------------------------------------------------ polls */

fun parsePoll(text: String): Pair<String, List<String>>? = runCatching {
    val o = JSONObject(text)
    val a = o.getJSONArray("o")
    o.getString("q") to List(a.length()) { a.getString(it) }
}.getOrNull()

@Composable
fun PollBubble(u: UiMessage, fg: Color) {
    val (q, options) = parsePoll(u.content?.text.orEmpty()) ?: return
    val actions = LocalChatActions.current
    val votes = u.message.reactions.values.filter { it.startsWith("poll:") }.groupingBy { it }.eachCount()
    val total = votes.values.sum()
    val mine = u.message.reactions[actions.myId]
    Column(Modifier.width(240.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Poll, null, Modifier.size(16.dp), tint = fg.copy(alpha = 0.7f))
            Spacer(Modifier.width(6.dp))
            Text(q, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(6.dp))
        options.forEachIndexed { i, label ->
            val n = votes["poll:$i"] ?: 0
            val chosen = mine == "poll:$i"
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { actions.onVote(u, i) }.padding(vertical = 4.dp)) {
                Row {
                    Text(if (chosen) "● $label" else "○ $label", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("$n", style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else n.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = fg, trackColor = fg.copy(alpha = 0.22f)
                )
            }
        }
        Text("Голосов: $total", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
    }
}

@Composable
fun PollDialog(onCreate: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый опрос") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(question, { question = it }, label = { Text("Вопрос") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                options.forEachIndexed { i, o ->
                    OutlinedTextField(o, { options[i] = it }, label = { Text("Вариант ${i + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (options.size < 6) TextButton(onClick = { options.add("") }) { Text("+ Вариант") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2,
                onClick = { onCreate(question, options.toList()); onDismiss() }
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/* ------------------------------------------------------------------ attach sheet + pickers */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(onPhoto: () -> Unit, onCamera: () -> Unit, onPoll: () -> Unit, onMusic: () -> Unit, onFile: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AttachItem(Icons.Filled.Image, "Фото", onPhoto, onDismiss)
            AttachItem(Icons.Filled.PhotoCamera, "Камера", onCamera, onDismiss)
            AttachItem(Icons.Filled.Poll, "Опрос", onPoll, onDismiss)
            AttachItem(Icons.Filled.MusicNote, "Музыка", onMusic, onDismiss)
            AttachItem(Icons.Filled.AttachFile, "Файл", onFile, onDismiss)
        }
    }
}

@Composable
private fun AttachItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit, dismiss: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable { dismiss(); action() }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Camera: asks for permission, writes into MediaStore and hands the saved photo's Uri to [onPhoto]. */
@Composable
fun rememberCameraLauncher(onPhoto: (Uri) -> Unit, onError: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pending; pending = null
        if (ok && u != null) onPhoto(u) else if (u != null) runCatching { ctx.contentResolver.delete(u, null, null) }
    }
    fun open() {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "blisschat_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BlissChat")
        }
        val uri = runCatching { ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) }.getOrNull()
        if (uri == null) { onError("Не удалось открыть камеру"); return }
        pending = uri
        take.launch(uri)
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) open() else onError("Нужно разрешение на камеру")
    }
    return {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open()
        else perm.launch(Manifest.permission.CAMERA)
    }
}

/** Returns (pickFile, pickMusic). Picked content is read off the main thread and passed on as bytes. */
@Composable
fun rememberFilePickers(onPicked: (ByteArray, String, String) -> Unit, onError: (String) -> Unit): Pair<() -> Unit, () -> Unit> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val handle: (Uri?) -> Unit = { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = ctx.contentResolver.getType(uri).orEmpty()
                    var name = "file"
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && i >= 0) name = c.getString(i)
                    }
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Triple(bytes, name, mime)
                }.getOrNull()
            }
            if (r == null) onError("Не удалось прочитать файл") else onPicked(r.first, r.second, r.third)
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), handle)
    val music = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), handle)
    return Pair({ file.launch(arrayOf("*/*")) }, { music.launch(arrayOf("audio/*")) })
}

/** Dialog shown after tapping somebody's sticker. [wholePack] — the message carries the entire pack. */
@Composable
fun SaveStickerDialog(wholePack: Boolean, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (wholePack) "Стикерпак" else "Стикер") },
        text = { Text(if (wholePack) "Сохранить весь набор стикеров в ваши наборы?" else "Сохранить этот стикер в ваши наборы?") },
        confirmButton = { TextButton(onClick = { onSave(); onDismiss() }) { Text(if (wholePack) "Сохранить набор" else "Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
