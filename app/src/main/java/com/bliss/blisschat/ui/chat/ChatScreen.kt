package com.bliss.blisschat.ui.chat

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.ChatRepository
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.data.Content
import com.bliss.blisschat.data.UiMessage
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissHeader
import com.bliss.blisschat.ui.components.BlobButton
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.VerifiedBlue
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.components.formatClock
import com.bliss.blisschat.ui.components.formatDayHeader
import com.bliss.blisschat.ui.components.isSameDay
import com.bliss.blisschat.ui.components.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥")

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
fun ChatScreen(container: AppContainer, chatId: String, onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val vm: ChatViewModel = viewModel(key = "chat-$chatId", factory = container.factory(chatId))
    val s by vm.state.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }

    var draft by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var actionFor by remember { mutableStateOf<UiMessage?>(null) }
    var viewer by remember { mutableStateOf<Bitmap?>(null) }
    var pendingVideo by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.sendImage(uri) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res[Manifest.permission.RECORD_AUDIO] == true && (!pendingVideo || res[Manifest.permission.CAMERA] == true)
        if (ok) vm.call(pendingVideo)
    }
    fun startCall(video: Boolean) {
        if (vm.hasCallPermissions(video)) vm.call(video)
        else {
            pendingVideo = video
            val perms = buildList {
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
        chat.type == ChatType.DIRECT -> if (other?.online() == true) "в сети" else other?.let { "@${it.username}" }.orEmpty()
        else -> "${chat.members.size} участн."
    }
    val openHeader = {
        if (chat?.type == ChatType.DIRECT) other?.let { onOpenProfile(it.uid) } else showInfo = true
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        BlissHeader {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                Row(Modifier.weight(1f).Row(Modifier.weight(1f).bounceClick(enabled = chat != null, onClick = { openHeader(); Unit }), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(other?.avatarUrl.orEmpty(), title.ifBlank { "?" }, size = 44.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        NameWithBadges(
                            title, verified = other?.verified == true || chat?.verified == true, support = other?.support == true,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
                        }
                    }
                }
                if (chat?.type == ChatType.DIRECT && other != null) {
                    BlobButton(Icons.Filled.Videocam, "Видеозвонок", { startCall(true) }, size = 46.dp,
                        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), content = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    BlobButton(Icons.Filled.Call, "Звонок", { startCall(false) }, size = 46.dp,
                        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), content = MaterialTheme.colorScheme.onPrimary)
                }
                if (chat != null) Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (chat.type == ChatType.DIRECT) "Профиль" else "О группе") },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = { menuOpen = false; openHeader() }
                        )
                        if (s.isAdmin && s.isGroup) {
                            DropdownMenuItem(
                                text = { Text(if (chat.verified) "Снять галочку группы" else "Подтвердить группу") },
                                leadingIcon = { Icon(Icons.Filled.Verified, null, tint = VerifiedBlue) },
                                onClick = { menuOpen = false; vm.setGroupVerified(!chat.verified) }
                            )
                        }
                        if (s.canLeave) {
                            DropdownMenuItem(
                                text = { Text("Выйти из группы") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                                onClick = { menuOpen = false; vm.leave(onBack) }
                            )
                        }
                        if (s.canDeleteGroup) {
                            DropdownMenuItem(
                                text = { Text(if (s.isAdmin && !s.isOwner) "Удалить группу (админ)" else "Удалить группу", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true }
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chat == null && s.chatLoaded) {
                Text("Этот чат не существует или был удалён.", Modifier.align(Alignment.Center).padding(32.dp), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else {
                MessageList(s, chat, vm.repo, onLongPress = { actionFor = it }, onReact = { m, e -> vm.react(m, e) }, onImage = { viewer = it },
                    onSender = { onOpenProfile(it.uid) })
            }
        }

        // Input bar
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Box(Modifier.navigationBarsPadding()) {
                when {
                    chat == null -> Spacer(Modifier.height(12.dp))
                    !s.isMember -> Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("Вы не участник этой группы", style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                        PillTextField(
                            value = draft, onValueChange = { draft = it }, placeholder = "Сообщение", singleLine = false, maxLines = 5,
                            shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f)
                        )
                        if (sending) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp) }
                        } else {
                            IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.size(48.dp)) {
                                Icon(Icons.Filled.AttachFile, "Прикрепить фото", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        BlobButton(
                            Icons.AutoMirrored.Filled.Send, "Отправить", size = 56.dp, enabled = draft.isNotBlank(),
                            onClick = {
                                val text = draft.trim()
                                if (text.isNotEmpty()) { draft = ""; vm.sendText(text) { failed -> if (draft.isBlank()) draft = failed } }
                            }
                        )
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 80.dp)) }

    actionFor?.let { m ->
        MessageActions(
            m = m, mine = m.message.senderId == s.myId, myReaction = m.message.reactions[s.myId],
            onDismiss = { actionFor = null },
            onReact = { vm.react(m, it); actionFor = null },
            onDeleteAll = { vm.deleteForEveryone(m); actionFor = null },
            onDeleteLocal = { vm.deleteLocally(m); actionFor = null }
        )
    }
    if (showInfo && chat != null) {
        GroupInfoSheet(s, chat, onDismiss = { showInfo = false }, onOpenProfile = { showInfo = false; onOpenProfile(it) }, onAdd = vm::addMember)
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
            title = { Text("Удалить группу?") },
            text = { Text("«${chat?.name.orEmpty()}» и все сообщения будут удалены у всех участников.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteGroup(onBack) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}

/* ------------------------------------------------------------------ messages */

@Composable
private fun MessageList(
    s: ChatUiState, chat: Chat?, repo: ChatRepository,
    onLongPress: (UiMessage) -> Unit, onReact: (UiMessage, String) -> Unit, onImage: (Bitmap) -> Unit, onSender: (UserProfile) -> Unit
) {
    val rows = remember(s.messages) { buildRows(s.messages) }
    val listState = rememberLazyListState()
    var banner by rememberSaveable { mutableStateOf(true) }

    var initialIds by remember { mutableStateOf<Set<String>?>(null) }
    val appeared = remember { mutableSetOf<String>() }
    LaunchedEffect(s.messagesLoaded) { if (s.messagesLoaded && initialIds == null) initialIds = s.messages.map { it.message.id }.toSet() }

    var firstScroll by remember { mutableStateOf(true) }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            if (firstScroll) { listState.scrollToItem(rows.lastIndex); firstScroll = false } else listState.animateScrollToItem(rows.lastIndex)
        }
    }

    LazyColumn(
        state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()
    ) {
        if (banner) item(key = "e2ee-banner") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Сообщения, фото и звонки защищены сквозным шифрованием. Прочитать их можете только вы и участники чата.",
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
                    val mine = u.message.senderId == s.myId
                    val bubble: @Composable () -> Unit = {
                        MessageBubble(u, mine, s.users[u.message.senderId], chat, s.myId, repo, onLongPress, onReact, onImage, onSender)
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    u: UiMessage, mine: Boolean, sender: UserProfile?, chat: Chat?, myId: String, repo: ChatRepository,
    onLongPress: (UiMessage) -> Unit, onReact: (UiMessage, String) -> Unit, onImage: (Bitmap) -> Unit, onSender: (UserProfile) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // Multi-cornered bubbles: the corner next to the sender is pointed
    val shape = if (mine) RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 5.dp, bottomStart = 26.dp)
    else RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 26.dp, bottomStart = 5.dp)
    val bg = if (mine) scheme.primary else scheme.surfaceContainerHighest
    val fg = if (mine) scheme.onPrimary else scheme.onSurface
    val source = remember { MutableInteractionSource() }
    val msg = u.message

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            Box(Modifier.padding(end = 8.dp).bounceClick(enabled = sender != null) { sender?.let(onSender) }) {
                Avatar(sender?.avatarUrl.orEmpty(), sender?.title ?: "?", size = 38.dp)
            }
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            Surface(
                shape = shape, color = bg, contentColor = fg,
                modifier = Modifier.pressScale(source, 0.97f).combinedClickable(interactionSource = source, indication = null, onClick = {}, onLongClick = { onLongPress(u) })
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    val name = (sender?.title ?: "…") + if (mine) " (вы)" else ""
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.6f))
                        if (sender?.verified == true) { Spacer(Modifier.width(3.dp)); Icon(Icons.Filled.Verified, null, Modifier.size(13.dp), tint = if (mine) fg else VerifiedBlue) }
                    }
                    when {
                        msg.deleted -> Text("Сообщение удалено", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = fg.copy(alpha = 0.6f))
                        u.failed || u.content == null -> Text("🔒 Не удалось расшифровать сообщение", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = fg.copy(alpha = 0.7f))
                        u.content.kind == Content.KIND_IMAGE -> EncryptedImage(u.content, repo, onImage)
                        else -> Text(u.content.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatClock(msg.ts), style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.7f))
                        if (mine && !msg.deleted) {
                            Spacer(Modifier.width(4.dp))
                            val read = if (chat?.type == ChatType.DIRECT) msg.readBy.any { it != myId } else msg.readBy.size > 1
                            Icon(if (read) Icons.Filled.DoneAll else Icons.Filled.Done, if (read) "Прочитано" else "Отправлено", Modifier.size(15.dp), tint = fg.copy(alpha = if (read) 1f else 0.6f))
                        }
                    }
                }
            }
            if (msg.reactions.isNotEmpty() && !msg.deleted) {
                Row(Modifier.padding(top = 3.dp, start = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    msg.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) ->
                        val mineR = msg.reactions[myId] == emoji
                        Surface(
                            shape = CircleShape, color = if (mineR) scheme.primaryContainer else scheme.surfaceContainerHigh,
                            modifier = Modifier.then(if (mineR) Modifier.border(1.dp, scheme.primary, CircleShape) else Modifier).bounceClick { onReact(u, emoji) }
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emoji, style = MaterialTheme.typography.bodyMedium)
                                if (count > 1) { Spacer(Modifier.width(3.dp)); Text("$count", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ encrypted images */

internal object ImageCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(k: String): Bitmap? = cache.get(k)
    fun put(k: String, v: Bitmap) { cache.put(k, v) }
}

@Composable
fun EncryptedImage(content: Content, repo: ChatRepository, onClick: (Bitmap) -> Unit) {
    var bmp by remember(content.path) { mutableStateOf(ImageCache.get(content.path)) }
    var failed by remember(content.path) { mutableStateOf(false) }
    LaunchedEffect(content.path) {
        if (bmp == null) {
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
    Box(
        Modifier.padding(top = 4.dp).width(230.dp).aspectRatio(ratio).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        when {
            b != null -> Image(b.asImageBitmap(), "Фото", Modifier.fillMaxSize().clickable { onClick(b) }, contentScale = ContentScale.Crop)
            failed -> Text("Не удалось загрузить фото", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
            else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

/* ------------------------------------------------------------------ long-press menu */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActions(
    m: UiMessage, mine: Boolean, myReaction: String?, onDismiss: () -> Unit,
    onReact: (String) -> Unit, onDeleteAll: () -> Unit, onDeleteLocal: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            if (!mine && !m.message.deleted) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    REACTIONS.forEach { e ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (myReaction == e) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .bounceClick { onReact(e) },
                            contentAlignment = Alignment.Center
                        ) { Text(e, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
            if (mine && !m.message.deleted) {
                SheetAction("Удалить у всех", MaterialTheme.colorScheme.error, onDeleteAll)
            }
            SheetAction("Удалить у меня", if (mine) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error, onDeleteLocal)
        }
    }
}

@Composable
private fun SheetAction(text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Delete, null, tint = color); Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/* ------------------------------------------------------------------ group info */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoSheet(
    s: ChatUiState, chat: Chat, onDismiss: () -> Unit, onOpenProfile: (String) -> Unit,
    onAdd: (String, (Result<Unit>) -> Unit) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar("", chat.name, size = 88.dp)
            Spacer(Modifier.height(10.dp))
            NameWithBadges(chat.name, chat.verified, false, style = MaterialTheme.typography.headlineSmall, badgeSize = 24.dp)
            Text("Группа · ${chat.members.size} участников", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            chat.members.forEach { uid ->
                val u = s.users[uid]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).bounceClick { onOpenProfile(uid) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u?.avatarUrl.orEmpty(), u?.title ?: "?", size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        NameWithBadges(u?.title ?: "…", u?.verified == true, u?.support == true)
                        Text("@${u?.username.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uid == chat.ownerId) Text("владелец", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (s.isOwner) {
                Spacer(Modifier.height(14.dp))
                PillTextField(username, { username = it; error = null }, "Добавить по @username", leading = Icons.Filled.PersonAdd, isError = error != null, supporting = error,
                    trailing = {
                        TextButton(enabled = username.isNotBlank() && !busy, onClick = {
                            busy = true
                            onAdd(username) { r -> busy = false; r.onSuccess { username = "" }.onFailure { error = it.message } }
                        }) { Text("Добавить") }
                    })
            }
        }
    }
}
