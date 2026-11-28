package com.bliss.blisschat.ui.chat

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Surface
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bliss.blisschat.data.MemberStat
import com.bliss.blisschat.data.TagStep
import com.bliss.blisschat.data.rankFor
import com.bliss.blisschat.ui.stickers.StickerImage
import com.bliss.blisschat.ui.stickers.StickerPicker
import com.bliss.blisschat.ui.stickers.StickerPanel
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Edit
import com.bliss.blisschat.ui.theme.LocalPluginStyle
import com.bliss.blisschat.data.plugins.PluginAction
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.AppSettings
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.ChatRepository
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.data.Content
import com.bliss.blisschat.data.UiMessage
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BlissHeader
import com.bliss.blisschat.ui.glass.glassActive
import com.bliss.blisschat.ui.glass.glassBar
import com.bliss.blisschat.ui.glass.glassWallpaper
import com.bliss.blisschat.ui.components.BlobButton
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.VerifiedBlue
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.components.formatClock
import com.bliss.blisschat.ui.components.formatDayHeader
import com.bliss.blisschat.ui.components.isSameDay
import com.bliss.blisschat.ui.components.pressScale
import com.bliss.blisschat.ui.components.SegEnd
import com.bliss.blisschat.ui.components.SegMid
import com.bliss.blisschat.ui.components.SegStart
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥")

/** Every emoji offered by the "+" button next to the quick reactions. */
private val ALL_EMOJIS = (
    "😀 😃 😄 😁 😆 😅 😂 🤣 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 😴 " +
        "😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 " +
        "😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 🙈 🙉 🙊 💋 💌 💘 💝 💖 💗 💓 💞 💕 💔 ❤ 🧡 💛 💚 💙 💜 🖤 🤍 💯 💥 💫 💦 🔥 ✨ ⭐ 🌈 " +
        "👍 👎 👌 ✌ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 👋 🤚 🖐 ✋ 👏 🙌 🤝 🙏 💪 🫶 🫡 🎉 🎊 🎁 🏆 ⚽ 🎮 🎵 🎶 🍕 🍔 🍟 🍰 ☕ 🍺 🌹 🌸 " +
        "🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐸 🐵 🐔 🐧 🦄 🐝 🦋 🐢 🐙 🐳 🌍 🌙 ☀ ⚡ ❄ 🚀 ✅ ❌ ❓ ❗ 💡 📌 🔔 🎯"
    ).split(" ").filter { it.isNotBlank() }

private sealed interface Row2 {
    data class Day(val label: String, val key: String) : Row2
    data class Msg(val m: UiMessage) : Row2
}

private fun buildRows(messages: List<UiMessage>): List<Row2> {
    val out = ArrayList<Row2>(messages.size + 4)
    var last = 0L
    messages.forEach { u ->
        val ts = u.message.ts
        if (last == 0L || !isSameDay(last, ts)) out += Row2.Day(formatDayHeader(ts), "day-${ts / 86_400_000L}")
        out += Row2.Msg(u)
        last = ts
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(container: AppContainer, chatId: String, onBack: () -> Unit, onOpenProfile: (String) -> Unit, onOpenStickers: () -> Unit = {}) {
    val vm: ChatViewModel = viewModel(key = "chat-$chatId", factory = container.factory(chatId))
    val s by vm.state.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }
    val keyLost by vm.keyLost.collectAsStateWithLifecycle()
    val aiTyping by vm.aiTyping.collectAsStateWithLifecycle()
    var scrollToId by remember { mutableStateOf<String?>(null) }

    var draft by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var actionFor by remember { mutableStateOf<MenuTarget?>(null) }
    var showStickers by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // reply / voice / attach / sticker panel
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val replyTo by vm.replyTo.collectAsStateWithLifecycle()
    val recorder = remember { VoiceRecorder(ctx.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var pollOpen by remember { mutableStateOf(false) }
    var stickerToSave by remember { mutableStateOf<UiMessage?>(null) }
    val toast: (String) -> Unit = { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show() }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    val startRec = rememberStartRecording(recorder, { recording = true }, toast)
    // whatever is typed in the input field when a photo / file is picked becomes its caption
    val takeCaption: () -> String = { val t = draft.trim(); draft = ""; t }
    val openCamera = rememberCameraLauncher({ vm.sendImage(it, takeCaption()) }, toast)
    val (pickFile, pickMusic) = rememberFilePickers({ b, n, m -> vm.sendFile(b, n, m, takeCaption()) }, toast)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    BackHandler(enabled = showStickers) { showStickers = false }
    val actions = remember(s.messages, s.myId) {
        ChatActions(
            replyPreview = { id -> s.messages.firstOrNull { it.message.id == id }?.let(::replyPreviewOf) },
            onStickerTap = { stickerToSave = it },
            onVote = { m, i -> vm.vote(m, i) },
            onReply = { vm.setReply(it) },
            toast = toast,
            myId = s.myId
        )
    }

    // "Active in the group" time counts only while the screen is visible
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> vm.setActive(true)
                Lifecycle.Event.ON_PAUSE -> vm.setActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); vm.setActive(false) }
    }
    var viewer by remember { mutableStateOf<Bitmap?>(null) }
    var pendingVideo by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.sendImage(uri, takeCaption()) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res[Manifest.permission.RECORD_AUDIO] == true && (!pendingVideo || res[Manifest.permission.CAMERA] == true)
        if (ok) vm.call(pendingVideo)
    }
    val groupCallers by vm.groupCall.collectAsStateWithLifecycle()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) vm.joinGroupCall()
    }
    fun joinGroup() {
        if (vm.hasMicPermission()) vm.joinGroupCall()
        else micPermission.launch(buildList<String> {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
    }
    fun startCall(video: Boolean) {
        if (vm.hasCallPermissions(video)) vm.call(video)
        else {
            pendingVideo = video
            val perms = buildList<String> {
                add(Manifest.permission.RECORD_AUDIO)
                if (video) add(Manifest.permission.CAMERA)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissions.launch(perms.toTypedArray())
        }
    }

    val chat = s.chat
    val other = s.other
    val title = when {
        chat == null -> if (s.chatLoaded) "Чат не найден" else ""
        chat.type == ChatType.DIRECT -> other?.title ?: "Личный чат"
        else -> chat.name
    }
    val subtitle = when {
        chat == null -> ""
        chat.type == ChatType.DIRECT -> when {
            other?.online() == true -> "в сети"
            other != null && other.lastSeen > 0 -> "в сети с " + SimpleDateFormat("HH:mm", Locale("ru")).format(Date(other.lastSeen))
            else -> other?.let { "@${it.username}" }.orEmpty()
        }
        chat.type == ChatType.CHANNEL -> "канал · ${chat.members.size} подписч."
        else -> "${chat.members.size} участн."
    }
    val openHeader: () -> Unit = {
        if (chat?.type == ChatType.DIRECT) {
            other?.let { onOpenProfile(it.uid) }
        } else {
            showInfo = true
        }
    }

    val blurRadius by animateDpAsState(if (actionFor != null) 18.dp else 0.dp, tween(220), label = "menu-blur")
    val pstyle by container.plugins.runtime.style.collectAsStateWithLifecycle()
    val pluginItems by container.plugins.runtime.actions.collectAsStateWithLifecycle()
    var miniApp by remember { mutableStateOf<Pair<String, String>?>(null) }
    CompositionLocalProvider(LocalChatActions provides actions, LocalPluginStyle provides pstyle, LocalMiniApp provides { t, u -> miniApp = t to u }) {
    miniApp?.let { (t, u) ->
        MiniAppDialog(t, u, s.myId, s.users[s.myId]?.title.orEmpty(), s.users[s.myId]?.username.orEmpty()) { miniApp = null }
    }
    val g = glassActive()
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
    if (g) {
        val cb = pstyle.chatBg
        if (cb != null) Box(Modifier.fillMaxSize().glassWallpaper().background(Color(cb))) else com.bliss.blisschat.ui.components.ChatBackground()
    }
    Column(
        Modifier.fillMaxSize().then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            .then(if (g) Modifier else Modifier.background(MaterialTheme.colorScheme.background)).imePadding()
    ) {
        val seg0 = MaterialTheme.colorScheme.surfaceContainerHighest
        val inner0 = MaterialTheme.colorScheme.surfaceContainerHigh
        val seg = if (g) Color.Transparent else seg0
        val inner = if (g) Color.Transparent else inner0
        val sh = if (g) 0.dp else 3.dp
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(onClick = onBack, shape = SegStart, color = seg, shadowElevation = sh, modifier = Modifier.size(width = 64.dp, height = 68.dp).glassBar(SegStart, seg0, 6.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад", Modifier.size(34.dp))
                }
            }
            Surface(shape = SegEnd, color = seg, shadowElevation = sh, modifier = Modifier.weight(1f).height(68.dp).glassBar(SegEnd, seg0, 6.dp)) {
                Row(Modifier.padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).bounceClick(enabled = chat != null, onClick = openHeader), verticalAlignment = Alignment.CenterVertically) {
                        Avatar((if (other != null) other.avatarUrl else chat?.avatarUrl).orEmpty(), title.ifBlank { "?" }, size = 52.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            NameWithBadges(
                                title, verified = other?.verified == true || chat?.verified == true, support = other?.support == true,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (subtitle.isNotEmpty() || chat?.secret == true) {
                                Text((if (chat?.secret == true) "🔒 секретный чат" + (if (subtitle.isNotEmpty()) " · " else "") else "") + subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                    val direct = chat?.type == ChatType.DIRECT && other != null
                    if (direct) {
                        Surface(onClick = { startCall(false) }, shape = RoundedCornerShape(24.dp, 6.dp, 6.dp, 24.dp), color = inner, modifier = Modifier.size(46.dp, 52.dp).glassBar(RoundedCornerShape(24.dp, 6.dp, 6.dp, 24.dp), inner0, 6.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Call, "Звонок", Modifier.size(24.dp)) }
                        }
                        Spacer(Modifier.width(3.dp))
                        Surface(onClick = { startCall(true) }, shape = SegMid, color = inner, modifier = Modifier.size(52.dp, 52.dp).glassBar(SegMid, inner0, 6.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Videocam, "Видеозвонок", Modifier.size(26.dp)) }
                        }
                        Spacer(Modifier.width(3.dp))
                    }
                    val miniUrl = if (direct) other?.miniAppUrl.orEmpty() else ""
                    if (miniUrl.isNotEmpty()) {
                        val openMini = LocalMiniApp.current
                        Surface(onClick = { openMini(other?.title.orEmpty(), miniUrl) }, shape = CircleShape, color = inner, modifier = Modifier.size(48.dp, 52.dp).glassBar(CircleShape, inner0, 24.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Apps, "Приложение", Modifier.size(24.dp)) }
                        }
                        Spacer(Modifier.width(3.dp))
                    }
                    if (chat != null && chat.type == ChatType.GROUP && !chat.secret) {
                        Surface(onClick = { joinGroup() }, shape = CircleShape, color = inner, modifier = Modifier.size(48.dp, 52.dp).glassBar(CircleShape, inner0, 24.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Call, "Групповой звонок", Modifier.size(24.dp)) }
                        }
                        Spacer(Modifier.width(3.dp))
                    }
                    if (chat != null) Box {
                        Surface(
                            onClick = { menuOpen = true }, color = inner,
                            modifier = Modifier.size(if (direct) 40.dp else 48.dp, 52.dp)
                                .glassBar(if (direct) RoundedCornerShape(6.dp, 24.dp, 24.dp, 6.dp) else CircleShape, inner0, 6.dp),
                            shape = if (direct) RoundedCornerShape(6.dp, 24.dp, 24.dp, 6.dp) else CircleShape
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.MoreVert, "Меню") } }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(when (chat.type) { ChatType.DIRECT -> "Профиль"; ChatType.CHANNEL -> "О канале"; else -> "О группе" }) },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = { menuOpen = false; openHeader() }
                        )
                        val muted = vm.isMuted(chat, s.myId, settings.muted)
                        DropdownMenuItem(
                            text = { Text(if (muted) "Включить уведомления" else "Выключить уведомления") },
                            leadingIcon = { Icon(if (muted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, null) },
                            onClick = { menuOpen = false; vm.toggleMute() }
                        )
                        pluginItems.filter { it.place == "chat" }.forEach { a ->
                            DropdownMenuItem(
                                text = { Text(a.title) }, leadingIcon = { Icon(Icons.Filled.Extension, null) },
                                onClick = {
                                    menuOpen = false
                                    container.plugins.runtime.runAction(a.pluginId, a.id, org.json.JSONObject().put("chatId", chatId).put("group", !direct))
                                }
                            )
                        }
                        if (s.isAdmin && s.isGroup) {
                            DropdownMenuItem(
                                text = { Text(if (chat.verified) "Снять галочку" else "Подтвердить") },
                                leadingIcon = { Icon(Icons.Filled.Verified, null, tint = VerifiedBlue) },
                                onClick = { menuOpen = false; vm.setGroupVerified(!chat.verified) }
                            )
                        }
                        if (s.canLeave) {
                            DropdownMenuItem(
                                text = { Text(if (chat.type == ChatType.CHANNEL) "Отписаться" else "Выйти из группы") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                                onClick = { menuOpen = false; vm.leave(onBack) }
                            )
                        }
                        if (s.canDeleteGroup) {
                            DropdownMenuItem(
                                text = { Text(if (s.isAdmin && !s.isOwner) "Удалить (админ)" else if (chat.type == ChatType.CHANNEL) "Удалить канал" else "Удалить группу", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true }
                            )
                        }
                    }
                    }
                }
            }
        }

        if (groupCallers.isNotEmpty() && chat?.type == ChatType.GROUP) {
            Surface(
                onClick = { joinGroup() }, shape = RoundedCornerShape(20.dp), color = if (g) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).glassBar(RoundedCornerShape(20.dp), MaterialTheme.colorScheme.primaryContainer, 20.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Call, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Идёт групповой звонок · ${groupCallers.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("Присоединиться", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // pinned message (mockup): [ pin ][ | закреплено / text ]
        val pinned = chat?.pinnedId?.takeIf { it.isNotEmpty() }
        if (pinned != null) {
            val pm = s.messages.firstOrNull { it.message.id == pinned }
            val pinText = when {
                pm == null -> "Закреплённое сообщение"
                pm.message.deleted -> "Сообщение удалено"
                pm.content == null -> "🔒 Зашифрованное сообщение"
                else -> pm.content.preview
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = SegStart, color = inner, modifier = Modifier.size(64.dp, 60.dp).glassBar(SegStart, inner0, 6.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PushPin, null, Modifier.size(28.dp)) }
                }
                Surface(
                    onClick = { scrollToId = pinned }, shape = SegEnd, color = inner,
                    modifier = Modifier.weight(1f).height(60.dp).glassBar(SegEnd, inner0, 6.dp)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                            Spacer(Modifier.width(8.dp))
                            Text("закреплено", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                            Spacer(Modifier.width(8.dp))
                            Text(pinText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val customBg = pstyle.chatBg
            if (!g) { if (customBg != null) Box(Modifier.fillMaxSize().background(Color(customBg))) else com.bliss.blisschat.ui.components.ChatBackground() }
            if (chat == null && s.chatLoaded) {
                Text("Этот чат не существует или был удалён.", Modifier.align(Alignment.Center).padding(32.dp), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else {
                MessageList(s, chat, vm.repo, settings, hiddenId = actionFor?.u?.message?.id, onLongPress = { u, r -> actionFor = MenuTarget(u, r) }, onReact = { m, e -> vm.react(m, e) }, onImage = { viewer = it },
                    onSender = { onOpenProfile(it.uid) },
                    scrollToId = scrollToId, onScrolled = { scrollToId = null })
            }
        }

        // snackbar lives INSIDE the imePadding column, so errors ("Не отправлено: ...") are never hidden by the keyboard
        SnackbarHost(snackbar, Modifier.padding(horizontal = 10.dp))

        // the group key is not readable on this phone: nothing can be sent until it is restored or re-issued
        if (keyLost && s.isMember && s.chat?.secret == true) {
            Surface(
                shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Секретные сообщения не читаются на этом телефоне", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "Ключи секретных чатов хранятся только на устройстве. Если вы входили со старого телефона, " +
                            "восстановите ключи: Настройки → Шифрование и ключи. Новые сообщения будут читаться, как только ключ вернётся.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Input bar
        Surface(
            color = if (g) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(36.dp),
            shadowElevation = if (g) 0.dp else 4.dp,
            modifier = Modifier.navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                .glassBar(RoundedCornerShape(36.dp), MaterialTheme.colorScheme.surfaceContainer, 36.dp)
        ) {
            Box {
                when {
                    chat == null -> Spacer(Modifier.height(12.dp))
                    !s.isMember -> Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (chat.username.isNotEmpty()) {
                            androidx.compose.material3.Button(onClick = { vm.join() }, shape = CircleShape) {
                                Text(if (s.isChannel) "Подписаться" else "Вступить")
                            }
                        } else {
                            Icon(Icons.Filled.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (s.isChannel) "Вы не подписаны на этот канал" else "Вы не участник этой группы", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    !s.canPost -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Публикует только владелец канала", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        val muted = vm.isMuted(chat, s.myId, settings.muted)
                        TextButton(onClick = { vm.toggleMute() }) { Text(if (muted) "Включить звук" else "Отключить звук") }
                    }
                    else -> Column {
                    val mention = if (s.isGroup) Regex("(^|\\s)@([\\p{L}\\p{N}_]*)$").find(draft) else null
                    if (mention != null) {
                        val q = mention.groupValues[2]
                        val atIdx = mention.groups[2]!!.range.first - 1
                        val found = s.users.values.filter { it.uid != s.myId && it.username.isNotBlank() && (it.username.startsWith(q, true) || it.title.contains(q, true)) }.take(5)
                        if (found.isNotEmpty()) Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                found.forEach { usr ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { draft = draft.substring(0, atIdx) + "@" + usr.username + " " }.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Avatar(usr.avatarUrl, usr.title, size = 36.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column { Text(usr.title, style = MaterialTheme.typography.titleSmall); Text("@" + usr.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                    }
                    aiTyping?.let { n ->
                        Text("$n печатает…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
                    }
                    replyTo?.let { r ->
                        ReplyBar(s.users[r.message.senderId]?.title ?: "…", replyPreviewOf(r)) { vm.setReply(null) }
                    }
                    if (recording) RecorderBar(recorder, onSend = { f, sec -> recording = false; vm.sendVoice(f, sec) }, onCancel = { recording = false })
                    else Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                        BlobButton(
                            Icons.Filled.Add, "Прикрепить", { attachOpen = true }, size = 52.dp,
                            container = MaterialTheme.colorScheme.surfaceContainerHighest, content = MaterialTheme.colorScheme.onSurface
                        )
                        PillTextField(
                            value = draft, onValueChange = { draft = it }, placeholder = "введите сообщение", singleLine = false, maxLines = 5,
                            shape = RoundedCornerShape(28.dp), containerColor = Color.Transparent,
                            modifier = Modifier.weight(1f).onFocusChanged { if (it.hasFocus) showStickers = false }
                        )
                        if (sending) {
                            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp) }
                        } else {
                            BlobButton(
                                Icons.Filled.EmojiEmotions, "Стикеры", {
                                    showStickers = !showStickers
                                    if (showStickers) { keyboard?.hide(); focus.clearFocus() }
                                }, size = 52.dp,
                                container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        if (draft.isBlank() && !sending) {
                            BlobButton(Icons.Filled.Mic, "Голосовое", size = 56.dp, onClick = { startRec() })
                        } else {
                            BlobButton(
                                Icons.AutoMirrored.Filled.Send, "Отправить", size = 56.dp, enabled = draft.isNotBlank(),
                                onClick = {
                                    val text = draft.trim()
                                    if (text.isNotEmpty()) { draft = ""; vm.sendText(text) { failed -> if (draft.isBlank()) draft = failed } }
                                }
                            )
                        }
                    }
                    if (showStickers && !recording) StickerPanel(container, onPick = { f, p -> vm.sendSticker(f, p) }, onManage = { showStickers = false; onOpenStickers() })
                    }
                }
            }
        }
    }

    actionFor?.let { t ->
        val m = t.u
        MessageMenuOverlay(
            target = t, origin = origin, mine = m.message.senderId == s.myId, myReaction = m.message.reactions[s.myId]?.takeIf { !it.startsWith("poll:") },
            bubble = { mod -> BubbleSurface(m, m.message.senderId == s.myId, s.users[m.message.senderId], chat, s.myId, vm.repo, s.stats, settings, {}, mod, users = s.users) },
            onDismiss = { actionFor = null },
            onReact = { vm.react(m, it); actionFor = null },
            onDeleteAll = { vm.deleteForEveryone(m); actionFor = null },
            onDeleteLocal = { vm.deleteLocally(m); actionFor = null },
            onSaveSticker = { vm.saveStickerPack(m); actionFor = null },
            pinned = chat?.pinnedId == m.message.id,
            onReply = if (s.isMember && s.canPost && !m.message.deleted && m.content != null) ({ vm.setReply(m); actionFor = null }) else null,
            onPin = if (s.isMember && (!s.isChannel || s.isOwner) && !m.message.deleted) ({ vm.togglePin(m); actionFor = null }) else null,
            pluginActions = pluginItems.filter { it.place == "message" },
            onPluginAction = { a ->
                val c = m.content
                container.plugins.runtime.runAction(
                    a.pluginId, a.id,
                    org.json.JSONObject().put("id", m.message.id).put("chatId", chatId).put("mine", m.message.senderId == s.myId)
                        .put("sender", s.users[m.message.senderId]?.title.orEmpty()).put("kind", c?.kind.orEmpty())
                        .put("text", if (c?.kind == Content.KIND_TEXT) c.text else "").put("ts", m.message.ts)
                )
                actionFor = null
            }
        )
    }
    } // outer box
    }
    if (attachOpen) AttachSheet(
        onPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onCamera = openCamera, onPoll = { pollOpen = true }, onMusic = pickMusic, onFile = pickFile,
        onDismiss = { attachOpen = false }
    )
    if (pollOpen) PollDialog({ q, o -> vm.sendPoll(q, o) }, { pollOpen = false })
    stickerToSave?.let { m ->
        SaveStickerDialog(wholePack = m.content?.text?.startsWith("{") == true, onSave = { vm.saveSticker(m) }, onDismiss = { stickerToSave = null })
    }
    if (showInfo && chat != null) {
        GroupInfoSheet(s, chat, onDismiss = { showInfo = false }, onOpenProfile = { showInfo = false; onOpenProfile(it) }, onAdd = vm::addMember, onSelfTag = vm::setSelfTag, onTags = vm::updateTags, onSaveProfile = vm::saveProfile, onAvatar = vm::changeAvatar)
    }
    viewer?.let { bmp ->
        Dialog(onDismissRequest = { viewer = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { viewer = null }, contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { viewer = null }, Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (chat?.type == ChatType.CHANNEL) "Удалить канал?" else "Удалить группу?") },
            text = { Text("«${chat?.name.orEmpty()}» и все сообщения будут удалены у всех участников.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteGroup(onBack) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}

/* ------------------------------------------------------------------ messages */

@Composable
private fun MessageList(
    s: ChatUiState, chat: Chat?, repo: ChatRepository, settings: AppSettings, hiddenId: String?,
    onLongPress: (UiMessage, Rect) -> Unit, onReact: (UiMessage, String) -> Unit, onImage: (Bitmap) -> Unit, onSender: (UserProfile) -> Unit,
    scrollToId: String?, onScrolled: () -> Unit
) {
    val rows = remember(s.messages) { buildRows(s.messages) }
    val groups = remember(rows) { groupFlags(rows) }
    val listState = rememberLazyListState()
    var banner by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    suspend fun goTo(id: String) {
        val i = rows.indexOfFirst { it is Row2.Msg && it.m.message.id == id }
        if (i >= 0) listState.animateScrollToItem(i + if (banner) 1 else 0)
    }
    LaunchedEffect(scrollToId) {
        if (scrollToId != null) { goTo(scrollToId); onScrolled() }
    }

    // "stick to the bottom" only while the reader is at the bottom (or wrote the last message himself)
    var stick by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { !it }.collect { stick = !listState.canScrollForward }
    }
    val showDown by remember { derivedStateOf { listState.canScrollForward } }

    // reactions / mentions that arrived while this chat is open (Telegram-like round buttons)
    var seenR by remember { mutableStateOf<Set<String>?>(null) }
    var seenM by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(s.messagesLoaded) { if (s.messagesLoaded && seenR == null) { seenR = reactKeys(s); seenM = mentionIds(s) } }
    val visibleIds by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() } }
    LaunchedEffect(visibleIds, s.messages) {
        seenR = seenR?.plus(reactKeys(s).filter { it.substringBefore('|') in visibleIds })
        seenM = seenM?.plus(mentionIds(s).filter { it in visibleIds })
    }
    val newReactions = seenR?.let { reactKeys(s) - it }.orEmpty()
    val newMentions = seenM?.let { mentionIds(s) - it }.orEmpty()

    var initialIds by remember { mutableStateOf<Set<String>?>(null) }
    val appeared = remember { mutableSetOf<String>() }
    LaunchedEffect(s.messagesLoaded) { if (s.messagesLoaded && initialIds == null) initialIds = s.messages.map { it.message.id }.toSet() }

    var firstScroll by remember { mutableStateOf(true) }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            val last = listState.layoutInfo.totalItemsCount - 1
            val lastMine = (rows.lastOrNull() as? Row2.Msg)?.m?.message?.senderId == s.myId
            if (firstScroll) { listState.scrollToItem(rows.lastIndex + if (banner) 1 else 0); firstScroll = false }
            else if (stick || lastMine) listState.animateScrollToItem(maxOf(last, rows.lastIndex))
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxSize()
    ) {
        if (banner && chat?.secret == true) item(key = "e2ee-banner") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Секретный чат: сообщения защищены сквозным шифрованием. Прочитать их можете только вы и собеседник — на этих двух телефонах.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { banner = false }, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Скрыть", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
        if (s.messagesLoaded && rows.isEmpty()) item(key = "empty") {
            Text("Сообщений пока нет. Напишите первым!", Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(rows, key = { if (it is Row2.Msg) it.m.message.id else (it as Row2.Day).key }) { row ->
            when (row) {
                is Row2.Day -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(row.label, Modifier.padding(horizontal = 18.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                is Row2.Msg -> {
                    val u = row.m
                    val animate = remember(u.message.id) { val i = initialIds; i != null && u.message.id !in i && appeared.add(u.message.id) }
                    // a message written by an AI (it was only forwarded by a person) is shown as the AI's own, on the left
                    val viaC = u.content?.takeIf { it.via.isNotEmpty() }
                    val mine = u.message.senderId == s.myId && viaC == null
                    val chatActions = LocalChatActions.current
                    val (first, last) = groups[u.message.id] ?: (true to true)
                    val bubble: @Composable () -> Unit = {
                        Box(Modifier.padding(top = if (first) 8.dp else 2.dp)) {
                            SwipeToReplyBox(enabled = !u.message.deleted && u.content != null && s.isMember, onReply = { chatActions.onReply(u) }) {
                                MessageBubble(u, mine, if (viaC != null) UserProfile(uid = viaC.via, displayName = viaC.viaName, username = viaC.viaName.lowercase() + "_ai", ai = true) else s.users[u.message.senderId], chat, s.myId, repo, s.stats, settings, hiddenId == u.message.id, onLongPress, onReact, onImage, if (viaC != null) ({ _: UserProfile -> }) else onSender, first, last, s.users)
                            }
                        }
                    }
                    if (animate) {
                        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(
                            visibleState = visible,
                            enter = fadeIn(tween(180)) + slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it / 2 } +
                                scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), initialScale = 0.85f, transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f))
                        ) { bubble() }
                    } else bubble()
                }
            }
        }
    }

    Column(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (newReactions.isNotEmpty()) FloatBadgeButton(Icons.Filled.Favorite, newReactions.map { it.substringBefore('|') }.toSet().size) {
            val id = newReactions.first().substringBefore('|')
            scope.launch { goTo(id) }
        }
        if (newMentions.isNotEmpty()) FloatBadgeButton(Icons.Filled.AlternateEmail, newMentions.size) { scope.launch { goTo(newMentions.first()) } }
        AnimatedVisibility(showDown, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            FloatBadgeButton(Icons.Filled.KeyboardArrowDown, 0) {
                scope.launch { listState.animateScrollToItem(maxOf(0, listState.layoutInfo.totalItemsCount - 1)) }
            }
        }
    }
    }
}

@Composable
private fun FloatBadgeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, onClick: () -> Unit) {
    Box {
        Surface(
            onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp, modifier = Modifier.size(48.dp)
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(26.dp)) } }
        if (count > 0) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)) {
            Text("$count", Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Text with @username parts highlighted. */
private fun mentionText(text: String, color: Color): AnnotatedString {
    val re = Regex("@[\\p{L}\\p{N}_]+")
    return buildAnnotatedString {
        var last = 0
        for (m in re.findAll(text)) {
            append(text.substring(last, m.range.first))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(m.value) }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

private fun reactKeys(s: ChatUiState): Set<String> = buildSet {
    for (m in s.messages) if (m.message.senderId == s.myId && !m.message.deleted)
        for ((uid, e) in m.message.reactions) if (uid != s.myId && !e.startsWith("poll:")) add("${m.message.id}|$uid|$e")
}

private fun mentionIds(s: ChatUiState): Set<String> {
    val name = s.users[s.myId]?.username.orEmpty()
    if (name.isEmpty()) return emptySet()
    val re = Regex("(^|[^\\p{L}\\p{N}_])@" + Regex.escape(name) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
    return s.messages.filter { it.message.senderId != s.myId && !it.message.deleted && it.content?.kind == Content.KIND_TEXT && re.containsMatchIn(it.content.text) }
        .map { it.message.id }.toSet()
}

/** For every message: is it the first / the last of a run of messages from the same sender (joined bubbles as in the mockup). */
private fun groupFlags(rows: List<Row2>): Map<String, Pair<Boolean, Boolean>> {
    val out = HashMap<String, Pair<Boolean, Boolean>>(rows.size)
    fun joins(a: Row2?, b: Row2?): Boolean =
        a is Row2.Msg && b is Row2.Msg && a.m.message.senderId == b.m.message.senderId &&
            !a.m.message.deleted && !b.m.message.deleted && b.m.message.ts - a.m.message.ts < 5 * 60_000L
    for (i in rows.indices) {
        val r = rows[i]
        if (r is Row2.Msg) out[r.m.message.id] = (!joins(rows.getOrNull(i - 1), r)) to (!joins(r, rows.getOrNull(i + 1)))
    }
    return out
}


private data class MenuTarget(val u: UiMessage, val rect: Rect)

private fun scaled(style: TextStyle, k: Float): TextStyle = if (k == 1f) style else style.copy(
    fontSize = if (style.fontSize.isSpecified) style.fontSize * k else style.fontSize,
    lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * k else style.lineHeight
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    u: UiMessage, mine: Boolean, sender: UserProfile?, chat: Chat?, myId: String, repo: ChatRepository,
    stats: Map<String, MemberStat>, settings: AppSettings, hidden: Boolean,
    onLongPress: (UiMessage, Rect) -> Unit, onReact: (UiMessage, String) -> Unit, onImage: (Bitmap) -> Unit, onSender: (UserProfile) -> Unit,
    first: Boolean = true, last: Boolean = true, users: Map<String, UserProfile> = emptyMap()
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val msg = u.message
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            // the avatar sits next to the LAST bubble of a run; the others keep the space free
            if (last) Box(Modifier.padding(end = 8.dp).bounceClick(enabled = sender != null) { sender?.let(onSender) }) {
                Avatar(sender?.avatarUrl.orEmpty(), sender?.title ?: "?", size = 38.dp)
            } else Spacer(Modifier.width(46.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            BubbleSurface(
                u, mine, sender, chat, myId, repo, stats, settings, onImage,
                Modifier.alpha(if (hidden) 0f else 1f).onGloballyPositioned { bounds = it.boundsInRoot() }
                    .pressScale(source, 0.97f)
                    .combinedClickable(interactionSource = source, indication = null, onClick = {}, onLongClick = { onLongPress(u, bounds) }),
                first = first, last = last, users = users, onReact = { onReact(u, it) }
            )
        }
    }
}

/** The bubble itself (also drawn in the long-press overlay). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BubbleSurface(
    u: UiMessage, mine: Boolean, sender: UserProfile?, chat: Chat?, myId: String, repo: ChatRepository,
    stats: Map<String, MemberStat>, settings: AppSettings, onImage: (Bitmap) -> Unit, modifier: Modifier,
    first: Boolean = true, last: Boolean = true, users: Map<String, UserProfile> = emptyMap(), onReact: ((String) -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    // Joined bubbles (mockup): inside a run of messages the corners that touch the neighbour are small
    val ps = LocalPluginStyle.current
    val big = (22f * ps.cornerScale).coerceIn(0f, 40f).dp
    val small = (6f * ps.cornerScale).coerceIn(0f, 24f).dp
    val shape = if (mine) RoundedCornerShape(topStart = big, topEnd = if (first) big else small, bottomEnd = if (last) big else small, bottomStart = big)
    else RoundedCornerShape(topStart = if (first) big else small, topEnd = big, bottomEnd = big, bottomStart = if (last) big else small)
    val msg = u.message
    val sticker = !msg.deleted && !u.failed && u.content?.kind == Content.KIND_STICKER
    val custom = if (mine) ps.mine else ps.other
    val bg = when { sticker -> Color.Transparent; custom != null -> Color(custom); mine -> scheme.primary; else -> scheme.surfaceContainerHighest }
        .let { if (sticker || ps.bubbleAlpha >= 1f) it else it.copy(alpha = it.alpha * ps.bubbleAlpha) }
    val glassEdge = if (ps.glass && ps.glassBorder && !sticker) androidx.compose.foundation.BorderStroke(
        1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.3f)))
    ) else null
    val fg = when {
        custom != null && !sticker -> if (Color(custom).luminance() > 0.5f) Color.Black else Color.White
        mine -> scheme.onPrimary
        else -> scheme.onSurface
    }
    val stat = stats[msg.senderId]
    val rank = if (chat?.type == ChatType.GROUP) chat.rankFor(stat) else null
    val selfTag = if (chat?.type == ChatType.GROUP && chat.selfTags) stat?.selfTag?.takeIf { it.isNotBlank() } else null
    val body = scaled(MaterialTheme.typography.bodyLarge, settings.textScale)
    val actions = LocalChatActions.current

    Surface(shape = shape, color = bg, contentColor = fg, border = glassEdge, modifier = modifier) {
        val media = !msg.deleted && !u.failed && u.content?.kind == Content.KIND_IMAGE
        Column(Modifier.padding(horizontal = if (sticker) 4.dp else if (media) 6.dp else 14.dp, vertical = if (sticker) 2.dp else if (media) 6.dp else 8.dp)) {
            if (!sticker && first) {
                val name = (sender?.title ?: "…") + (if (sender?.isAi == true) " · ИИ" else "") + if (mine) " (вы)" else ""
                Row(Modifier.padding(horizontal = if (media) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.6f), modifier = Modifier.weight(1f, fill = false))
                    if (sender?.verified == true) { Spacer(Modifier.width(3.dp)); Icon(Icons.Filled.Verified, null, Modifier.size(13.dp), tint = if (mine) fg else VerifiedBlue) }
                    if (rank != null) TagChip(rank, true, fg)
                    if (selfTag != null) TagChip(selfTag, false, fg)
                }
            }
            when {
                msg.deleted -> Text("Сообщение удалено", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = fg.copy(alpha = 0.6f))
                u.failed || u.content == null -> Text("🔒 Не удалось расшифровать сообщение", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = fg.copy(alpha = 0.7f))
                u.content.kind == Content.KIND_IMAGE -> Column {
                    if (u.content.replyToId.isNotEmpty()) Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp).widthIn(max = 220.dp)) { QuoteBlock(u.content.replyToId, fg) }
                    EncryptedImage(u.content, repo, onImage, autoLoad = settings.autoLoadPhotos)
                    if (u.content.caption.isNotBlank()) Text(mentionText(u.content.caption, if (mine) fg else scheme.primary), style = body, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).widthIn(max = 230.dp))
                }
                u.content.kind == Content.KIND_STICKER -> Column {
                    if (u.content.replyToId.isNotEmpty()) Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp).widthIn(max = 220.dp)) { QuoteBlock(u.content.replyToId, scheme.onSurface) }
                    EncryptedImage(u.content, repo, { actions.onStickerTap(u) }, sticker = true)
                }
                u.content.kind == Content.KIND_CALL -> CallLogBubble(u.content, mine, fg)
                u.content.kind == Content.KIND_VOICE -> Column {
                    if (u.content.replyToId.isNotEmpty()) QuoteBlock(u.content.replyToId, fg)
                    VoiceBubble(u.content, repo, fg)
                }
                u.content.kind == Content.KIND_POLL -> PollBubble(u, fg)
                u.content.kind == Content.KIND_FILE -> Column {
                    if (u.content.replyToId.isNotEmpty()) QuoteBlock(u.content.replyToId, fg)
                    FileBubble(u.content, repo, fg, actions.toast)
                    if (u.content.caption.isNotBlank()) Text(mentionText(u.content.caption, if (mine) fg else scheme.primary), style = body, modifier = Modifier.padding(top = 6.dp))
                }
                else -> Column {
                    if (u.content.replyToId.isNotEmpty()) QuoteBlock(u.content.replyToId, fg)
                    Text(mentionText(u.content.text, if (mine) fg else scheme.primary), style = body)
                }
            }
            val btnContent = u.content?.takeIf { !msg.deleted && !u.failed }
            if (btnContent != null && (btnContent.miniAppUrl.isNotEmpty() || btnContent.inlineButtons.any { r -> r.any { it.url.isNotEmpty() } })) {
                val openMini = LocalMiniApp.current
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (btnContent.miniAppUrl.isNotEmpty()) Surface(
                        shape = RoundedCornerShape(14.dp), color = fg.copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth().bounceClick { openMini(sender?.title.orEmpty(), btnContent.miniAppUrl) }
                    ) { Text("Открыть приложение", Modifier.padding(10.dp), style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    btnContent.inlineButtons.flatten().filter { it.url.startsWith("https://") }.forEach { b ->
                        Surface(
                            shape = RoundedCornerShape(14.dp), color = fg.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth().bounceClick { runCatching { uriHandler.openUri(b.url) } }
                        ) { Text(b.text, Modifier.padding(10.dp), style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }
            val shown = if (msg.deleted) emptyMap() else msg.reactions.filter { !it.value.startsWith("poll:") }.entries.groupBy({ it.value }, { it.key })
            if (shown.isNotEmpty()) {
                val rfg = if (sticker) scheme.onSurface else fg
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    shown.forEach { (emoji, who) ->
                        val chosen = myId in who
                        Surface(
                            shape = CircleShape, color = rfg.copy(alpha = if (chosen) 0.30f else 0.14f),
                            modifier = Modifier.clip(CircleShape).then(if (onReact != null) Modifier.bounceClick { onReact(emoji) } else Modifier)
                        ) {
                            Row(Modifier.padding(start = 8.dp, end = if (who.size <= 3) 4.dp else 8.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emoji, style = MaterialTheme.typography.bodyMedium)
                                if (who.size <= 3) {
                                    Spacer(Modifier.width(4.dp))
                                    // Telegram style: small avatars of the people who reacted, overlapping
                                    Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
                                        who.forEach { uid ->
                                            val usr = users[uid]
                                            Box(Modifier.size(20.dp).clip(CircleShape)) { Avatar(usr?.avatarUrl.orEmpty(), usr?.title ?: "?", size = 20.dp) }
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.width(4.dp)); Text("${who.size}", style = MaterialTheme.typography.labelMedium, color = rfg)
                                }
                            }
                        }
                    }
                }
            }
            if (last) Row(Modifier.align(Alignment.End).padding(top = 2.dp, end = if (media) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(msg.ts), style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.7f))
                if (mine && !msg.deleted) {
                    Spacer(Modifier.width(4.dp))
                    val read = if (chat?.type == ChatType.DIRECT) msg.readBy.any { it != myId } else msg.readBy.size > 1
                    Icon(if (read) Icons.Filled.DoneAll else Icons.Filled.Done, if (read) "Прочитано" else "Отправлено", Modifier.size(15.dp), tint = fg.copy(alpha = if (read) 1f else 0.6f))
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, rank: Boolean, fg: Color) {
    Spacer(Modifier.width(5.dp))
    Surface(shape = CircleShape, color = fg.copy(alpha = if (rank) 0.22f else 0.12f)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}

/* ------------------------------------------------------------------ encrypted images */

internal object ImageCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(k: String): Bitmap? = cache.get(k)
    fun put(k: String, v: Bitmap) { cache.put(k, v) }
    fun clear() { cache.evictAll() }
}

@Composable
fun EncryptedImage(content: Content, repo: ChatRepository, onClick: (Bitmap) -> Unit, sticker: Boolean = false, autoLoad: Boolean = true) {
    var bmp by remember(content.path) { mutableStateOf(ImageCache.get(content.path)) }
    var failed by remember(content.path) { mutableStateOf(false) }
    var requested by remember(content.path) { mutableStateOf(autoLoad || sticker) }
    var attempt by remember(content.path) { mutableIntStateOf(0) }
    LaunchedEffect(content.path, requested, attempt) {
        if (bmp == null && requested) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = repo.downloadMedia(content.path, content.key)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            if (loaded != null) { ImageCache.put(content.path, loaded); bmp = loaded } else failed = true
        }
    }
    val ratio = if (content.w > 0 && content.h > 0) (content.w.toFloat() / content.h).coerceIn(0.6f, 1.7f) else 1f
    val boxMod = if (sticker) Modifier.padding(top = 2.dp).width(150.dp).aspectRatio(ratio)
    else Modifier.padding(top = 4.dp).width(230.dp).aspectRatio(ratio).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.15f))
    Box(boxMod, contentAlignment = Alignment.Center) {
        val b = bmp
        when {
            b != null -> Image(b.asImageBitmap(), if (sticker) "Стикер" else "Фото", Modifier.fillMaxSize().clickable { onClick(b) },
                contentScale = if (sticker) ContentScale.Fit else ContentScale.Crop)
            failed -> Text(
                "Не удалось загрузить. Нажмите, чтобы повторить", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp).clickable { failed = false; attempt++ }
            )
            !requested -> Text("Нажмите, чтобы загрузить фото", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().clickable { requested = true }.padding(12.dp))
            else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun FloatingShapesBackdrop() {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primaryContainer, scheme.tertiaryContainer)
    val transition = rememberInfiniteTransition(label = "floating-shapes")
    val tick by transition.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "floating-tick"
    )
    val shapes = remember {
        List(9) { i ->
            FloatShape(
                x = Math.random().toFloat(), y = Math.random().toFloat(),
                r = 0.10f + Math.random().toFloat() * 0.16f,
                lobes = 5 + (Math.random() * 6).toInt(), depth = 0.04f + Math.random().toFloat() * 0.05f,
                color = i % 5,
                ax = 0.05f + Math.random().toFloat() * 0.08f, ay = 0.05f + Math.random().toFloat() * 0.10f,
                speed = (1 + (Math.random() * 2).toInt()).toFloat(), offset = Math.random().toFloat() * 6.28f
            )
        }
    }
    // Material You "cookie" shapes in the theme colours drift over the blurred chat; the whole layer is blurred too.
    Canvas(Modifier.fillMaxSize().blur(26.dp)) {
        drawRect(Color.Black.copy(alpha = 0.28f))
        shapes.forEach { sh ->
            val a = tick * sh.speed + sh.offset
            val cx = (sh.x + sh.ax * Math.sin(a.toDouble()).toFloat()) * size.width
            val cy = (sh.y + sh.ay * Math.cos(a.toDouble()).toFloat()) * size.height
            val radius = sh.r * size.width
            drawPath(
                com.bliss.blisschat.ui.theme.scallopPath(cx, cy, radius, radius, sh.lobes, sh.depth, a),
                palette[sh.color].copy(alpha = 0.6f)
            )
        }
    }
}

private class FloatShape(
    val x: Float, val y: Float, val r: Float, val lobes: Int, val depth: Float,
    val color: Int, val ax: Float, val ay: Float, val speed: Float, val offset: Float
)

/* ------------------------------------------------------------------ long-press menu */

/**
 * Telegram-style menu: the chat behind is blurred and dimmed, the pressed message is shown sharp
 * in its place and the reactions + actions appear right under it.
 */
@Composable
private fun MessageMenuOverlay(
    target: MenuTarget, origin: androidx.compose.ui.geometry.Offset, mine: Boolean, myReaction: String?,
    bubble: @Composable (Modifier) -> Unit,
    onDismiss: () -> Unit, onReact: (String) -> Unit, onDeleteAll: () -> Unit, onDeleteLocal: () -> Unit,
    onSaveSticker: (() -> Unit)? = null, pinned: Boolean = false, onPin: (() -> Unit)? = null, onReply: (() -> Unit)? = null,
    pluginActions: List<PluginAction> = emptyList(), onPluginAction: (PluginAction) -> Unit = {}
) {
    val m = target.u
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val text = m.content?.takeIf { it.kind == Content.KIND_TEXT && !m.message.deleted }?.text
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    var moreOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        FloatingShapesBackdrop()
        if (moreOpen) Dialog(onDismissRequest = { moreOpen = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(46.dp),
                    modifier = Modifier.height(340.dp).padding(12.dp)
                ) {
                    items(ALL_EMOJIS.size) { i ->
                        val e = ALL_EMOJIS[i]
                        Box(Modifier.size(46.dp).clip(CircleShape).bounceClick { moreOpen = false; onReact(e) }, contentAlignment = Alignment.Center) {
                            Text(e, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
        val hPx = with(density) { maxHeight.toPx() }
        val wPx = with(density) { maxWidth.toPx() }
        val gap = with(density) { 10.dp.toPx() }
        val menuW = with(density) { 264.dp.toPx() }
        val actionRows = (if (text != null) 1 else 0) + (if (mine && !m.message.deleted) 1 else 0) + 1
        val menuH = with(density) { (if (!m.message.deleted) 60.dp.toPx() else 0f) + actionRows * 52.dp.toPx() + 16.dp.toPx() }
        val r = target.rect.translate(-origin.x, -origin.y)
        val topLimit = with(density) { 72.dp.toPx() }
        val bottomLimit = hPx - with(density) { 24.dp.toPx() }
        var bubbleTop = r.top
        if (bubbleTop + r.height + gap + menuH > bottomLimit) bubbleTop = bottomLimit - menuH - gap - r.height
        bubbleTop = bubbleTop.coerceAtLeast(topLimit)
        val menuTop = (bubbleTop + r.height + gap).coerceAtMost(hPx - menuH - with(density) { 8.dp.toPx() })
        val menuLeft = (if (mine) r.right - menuW else r.left).coerceIn(with(density) { 8.dp.toPx() }, (wPx - menuW - with(density) { 8.dp.toPx() }).coerceAtLeast(0f))

        // the sharp copy of the pressed message
        Box(
            Modifier.offset { IntOffset(r.left.toInt(), bubbleTop.toInt()) }
                .width(with(density) { r.width.toDp() })
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        ) { bubble(Modifier) }

        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(160)) + scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.8f, transformOrigin = TransformOrigin(if (mine) 1f else 0f, 0f)),
            modifier = Modifier.offset { IntOffset(menuLeft.toInt(), menuTop.toInt()) }.width(with(density) { menuW.toDp() })
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!m.message.deleted) {
                    Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            REACTIONS.forEach { e ->
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape)
                                        .background(if (myReaction == e) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .bounceClick { onReact(e) },
                                    contentAlignment = Alignment.Center
                                ) { Text(e, fontSize = 18.sp) }
                            }
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).bounceClick { moreOpen = true },
                                contentAlignment = Alignment.Center
                            ) { Text("+", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        if (text != null) MenuAction("Копировать", Icons.Filled.ContentCopy, MaterialTheme.colorScheme.onSurface) {
                            clipboard.setText(AnnotatedString(text)); onDismiss()
                        }
                        if (onReply != null) MenuAction("Ответить", Icons.AutoMirrored.Filled.Reply, MaterialTheme.colorScheme.onSurface, onReply)
                        if (onPin != null) MenuAction(if (pinned) "Открепить" else "Закрепить", Icons.Filled.PushPin, MaterialTheme.colorScheme.onSurface, onPin)
                        if (m.content?.kind == Content.KIND_STICKER && onSaveSticker != null) {
                            MenuAction("Сохранить стикерпак", Icons.Filled.Bookmark, MaterialTheme.colorScheme.primary) {
                                onSaveSticker()
                            }
                        }
                        if (!m.message.deleted) pluginActions.forEach { a ->
                            MenuAction(a.title, Icons.Filled.Extension, MaterialTheme.colorScheme.tertiary) { onPluginAction(a) }
                        }
                        if (mine && !m.message.deleted) MenuAction("Удалить у всех", Icons.Filled.Delete, MaterialTheme.colorScheme.error, onDeleteAll)
                        MenuAction("Удалить у меня", Icons.Filled.Delete, if (mine) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error, onDeleteLocal)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/* ------------------------------------------------------------------ group info */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoSheet(
    s: ChatUiState, chat: Chat, onDismiss: () -> Unit, onOpenProfile: (String) -> Unit,
    onAdd: (String, (Result<Unit>) -> Unit) -> Unit, onSelfTag: (String) -> Unit, onTags: (Boolean, Boolean, List<TagStep>) -> Unit,
    onSaveProfile: (String, String, String, (Result<Unit>) -> Unit) -> Unit, onAvatar: (android.net.Uri) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onAvatar(uri) }
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val isGroup = chat.type == ChatType.GROUP
    if (editing) EditChatProfileDialog(chat, onSaveProfile) { editing = false }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.then(if (s.isOwner) Modifier.clip(CircleShape).bounceClick { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } else Modifier)) {
                Avatar(chat.avatarUrl, chat.name, size = 96.dp)
            }
            if (s.isOwner) TextButton(onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Сменить аватарку") }
            Spacer(Modifier.height(10.dp))
            NameWithBadges(chat.name, chat.verified, false, style = MaterialTheme.typography.headlineSmall, badgeSize = 24.dp)
            if (chat.username.isNotEmpty()) Text("@${chat.username}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                (if (chat.type == ChatType.CHANNEL) "Канал · ${chat.members.size} подписчиков" else "Группа · ${chat.members.size} участников"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (chat.description.isNotBlank()) Text(chat.description, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            if (s.isOwner) {
                TextButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(8.dp)); Text("Изменить профиль") }
            }
            Spacer(Modifier.height(16.dp))
            chat.members.forEach { uid ->
                val u = s.users[uid]
                val stat = s.stats[uid]
                val rank = if (isGroup) chat.rankFor(stat) else null
                val own = if (isGroup && chat.selfTags) stat?.selfTag?.takeIf { it.isNotBlank() } else null
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).bounceClick { onOpenProfile(uid) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u?.avatarUrl.orEmpty(), u?.title ?: "?", size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        NameWithBadges(u?.title ?: "…", u?.verified == true, u?.support == true)
                        val sub = listOfNotNull("@${u?.username.orEmpty()}", rank, own).joinToString(" · ")
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uid == chat.ownerId) Text("владелец", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (s.isOwner && chat.type != ChatType.CHANNEL) {
                Spacer(Modifier.height(14.dp))
                PillTextField(username, { username = it; error = null }, "Добавить по @username", leading = Icons.Filled.PersonAdd, isError = error != null, supporting = error,
                    trailing = {
                        TextButton(enabled = username.isNotBlank() && !busy, onClick = {
                            busy = true
                            onAdd(username) { r -> busy = false; r.onSuccess { username = "" }.onFailure { error = it.message } }
                        }) { Text("Добавить") }
                    })
            }
            if (isGroup && s.isMember) {
                Spacer(Modifier.height(20.dp))
                if (chat.selfTags) MySelfTag(s.stats[s.myId]?.selfTag.orEmpty(), onSelfTag)
                if (s.isOwner) TagSettings(chat, onTags)
            }
        }
    }
}

@Composable
private fun EditChatProfileDialog(chat: Chat, onSave: (String, String, String, (Result<Unit>) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(chat.name) }
    var about by remember { mutableStateOf(chat.description) }
    var pub by remember { mutableStateOf(chat.username) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(30.dp),
        title = { Text(if (chat.type == ChatType.CHANNEL) "Профиль канала" else "Профиль группы") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillTextField(name, { name = it.take(60); error = null }, "Название")
                PillTextField(pub, { pub = it.trim().removePrefix("@").lowercase().take(32); error = null }, "Публичный @username (пусто = закрытая)", leading = Icons.Filled.AlternateEmail)
                PillTextField(about, { about = it.take(255); error = null }, "Описание", singleLine = false, maxLines = 4, shape = RoundedCornerShape(24.dp))
                Text("@username уникален среди всех групп и каналов. Публичную группу может найти и открыть любой.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                busy = true
                onSave(name, about, pub) { r -> busy = false; r.onSuccess { onDismiss() }.onFailure { error = it.message } }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun MySelfTag(current: String, onSave: (String) -> Unit) {
    var tag by remember(current) { mutableStateOf(current) }
    Column(Modifier.fillMaxWidth()) {
        Text("Мой тег", style = MaterialTheme.typography.titleMedium)
        Text("Короткая подпись рядом с вашим именем в этой группе.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        PillTextField(tag, { tag = it.take(16) }, "например, дизайнер",
            trailing = { TextButton(enabled = tag.trim() != current, onClick = { onSave(tag.trim()) }) { Text("Сохранить") } })
    }
}

@Composable
private fun TagSettings(chat: Chat, onTags: (Boolean, Boolean, List<TagStep>) -> Unit) {
    var creator by remember(chat.creatorTags) { mutableStateOf(chat.creatorTags) }
    var selfT by remember(chat.selfTags) { mutableStateOf(chat.selfTags) }
    var ladder by remember(chat.ladder) { mutableStateOf(chat.ladder.map { it.name to it.minutes.toString() }) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("Теги группы (для создателя)", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Теги от создателя (растут со временем)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(creator, { creator = it })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Теги, которые пишут участники", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(selfT, { selfT = it })
        }
        if (creator) {
            Text(
                "Ступень выдаётся, когда активность участника достигает порога: 1 минута в группе = 1 очко, каждое сообщение = 2 очка.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ladder.forEachIndexed { i, (name, min) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillTextField(name, { v -> ladder = ladder.toMutableList().also { it[i] = v.take(16) to min } }, "Название", modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    PillTextField(min, { v -> ladder = ladder.toMutableList().also { it[i] = name to v.filter(Char::isDigit).take(6) } }, "очков",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.width(110.dp))
                    IconButton(onClick = { ladder = ladder.toMutableList().also { it.removeAt(i) } }) { Icon(Icons.Filled.Delete, "Убрать ступень") }
                }
            }
            TextButton(onClick = { ladder = ladder + ("" to "0") }) { Text("+ Добавить ступень") }
        }
        TextButton(onClick = {
            onTags(creator, selfT, ladder.mapNotNull { (n, m) -> n.trim().takeIf { it.isNotEmpty() }?.let { TagStep(it, m.toIntOrNull() ?: 0) } })
        }) { Text("Сохранить настройки тегов") }
    }
}
