package com.bliss.blisschat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.data.Message
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.BouncyIconButton
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.ProfileSheet
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.components.formatClock
import com.bliss.blisschat.ui.components.formatDayHeader
import com.bliss.blisschat.ui.components.isSameDay

private sealed interface Row2 {
    data class Day(val label: String, val key: String) : Row2
    data class Msg(val message: Message) : Row2
}

private fun buildRows(messages: List<Message>): List<Row2> {
    val out = ArrayList<Row2>(messages.size + 4)
    var last = 0L
    messages.forEach { m ->
        if (last == 0L || !isSameDay(last, m.timestamp)) out += Row2.Day(formatDayHeader(m.timestamp), "day-${m.timestamp / 86_400_000L}")
        out += Row2.Msg(m)
        last = m.timestamp
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(container: AppContainer, chatId: String, onBack: () -> Unit) {
    val vm: ChatViewModel = viewModel(key = "chat-$chatId", factory = container.factory(chatId))
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }

    var draft by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var profileFor by remember { mutableStateOf<UserProfile?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val chat = s.chat
    val other = s.other
    val title = when {
        chat == null -> if (s.chatLoaded) "Chat not found" else ""
        chat.type == ChatType.DIRECT -> other?.displayName?.ifBlank { other.username } ?: "Direct chat"
        else -> chat.name
    }
    val subtitle = when {
        chat == null -> ""
        chat.type == ChatType.DIRECT -> other?.let { "@${it.username}" }.orEmpty()
        chat.type == ChatType.GROUP -> "${chat.members.size} members"
        else -> "Channel - ${chat.members.size} members"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    Row(
                        Modifier.bounceClick(enabled = chat != null) {
                            if (chat?.type == ChatType.DIRECT) profileFor = other else showInfo = true
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(other?.avatarUrl.orEmpty(), title.ifBlank { "?" }, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            NameWithBadges(
                                name = title,
                                verified = other?.verified == true,
                                support = other?.support == true,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (chat != null) Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (chat.type == ChatType.DIRECT) "View profile" else "Info") },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = {
                                    menuOpen = false
                                    if (chat.type == ChatType.DIRECT) profileFor = other else showInfo = true
                                }
                            )
                            if (s.canLeave) {
                                DropdownMenuItem(
                                    text = { Text("Leave") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.leave(onBack)
                                    }
                                )
                            }
                            if (s.canDelete) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (s.isAdmin && !s.isOwner) "Delete channel (admin)" else "Delete channel",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (s.isAdmin && !s.isOwner) Icons.Filled.AdminPanelSettings else Icons.Filled.Delete,
                                            null, tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                Box(Modifier.navigationBarsPadding().imePadding()) {
                    when {
                        chat == null -> Spacer(Modifier.height(8.dp))
                        s.canPost -> InputBar(
                            draft = draft,
                            onDraft = { draft = it },
                            onSend = {
                                val text = draft.trim()
                                if (text.isNotEmpty()) {
                                    draft = ""
                                    vm.send(text) { failed -> if (draft.isBlank()) draft = failed }
                                }
                            }
                        )
                        s.canJoin -> Box(Modifier.fillMaxWidth().padding(16.dp)) {
                            BouncyButton("Join channel", onClick = vm::join, modifier = Modifier.fillMaxWidth())
                        }
                        else -> Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Only the channel owner can post here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (chat == null && s.chatLoaded) {
                Text(
                    "This chat doesn't exist or was deleted.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                MessageList(
                    s = s,
                    chat = chat,
                    onSenderClick = { profileFor = it }
                )
            }
        }
    }

    profileFor?.let { ProfileSheet(it) { profileFor = null } }
    if (showInfo && chat != null) {
        ChatInfoSheet(chat, s.users[chat.ownerId], onDismiss = { showInfo = false }, onOpenProfile = {
            showInfo = false
            profileFor = it
        })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete channel?") },
            text = { Text("\"${chat?.name.orEmpty()}\" and all of its messages will be permanently removed for everyone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteChannel(onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/* ------------------------------------------------------------------ messages */

@Composable
private fun MessageList(s: ChatUiState, chat: Chat?, onSenderClick: (UserProfile) -> Unit) {
    val rows = remember(s.messages) { buildRows(s.messages) }
    val listState = rememberLazyListState()

    // Messages present when the screen opened don't animate; only newly arriving ones do.
    var initialIds by remember { mutableStateOf<Set<String>?>(null) }
    val appeared = remember { mutableSetOf<String>() }
    LaunchedEffect(s.messagesLoaded) {
        if (s.messagesLoaded && initialIds == null) initialIds = s.messages.map { it.id }.toSet()
    }

    // Smooth auto-scroll to the newest message
    var firstScroll by remember { mutableStateOf(true) }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) {
            if (firstScroll) {
                listState.scrollToItem(rows.lastIndex)
                firstScroll = false
            } else {
                listState.animateScrollToItem(rows.lastIndex)
            }
        }
    }

    if (s.messagesLoaded && rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No messages yet. Say hi!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val isDirect = chat?.type == ChatType.DIRECT
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(rows, key = { if (it is Row2.Msg) it.message.id else (it as Row2.Day).key }) { row ->
            when (row) {
                is Row2.Day -> Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
                is Row2.Msg -> {
                    val m = row.message
                    val animate = remember(m.id) {
                        val initial = initialIds
                        initial != null && m.id !in initial && appeared.add(m.id)
                    }
                    val mine = m.senderId == s.myId
                    val bubble: @Composable () -> Unit = {
                        MessageBubble(
                            message = m,
                            mine = mine,
                            sender = s.users[m.senderId],
                            showSender = !mine && !isDirect,
                            onSenderClick = onSenderClick
                        )
                    }
                    if (animate) {
                        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(
                            visibleState = visible,
                            enter = fadeIn(tween(180)) +
                                slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it / 2 } +
                                scaleIn(
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                                    initialScale = 0.85f,
                                    transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
                                )
                        ) { bubble() }
                    } else {
                        bubble()
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    mine: Boolean,
    sender: UserProfile?,
    showSender: Boolean,
    onSenderClick: (UserProfile) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = if (mine) {
        RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 6.dp, bottomStart = 26.dp)
    } else {
        RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 26.dp, bottomStart = 6.dp)
    }
    val bg = if (mine) scheme.primaryContainer else scheme.surfaceContainerHigh
    val fg = if (mine) scheme.onPrimaryContainer else scheme.onSurface

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (showSender) {
            Box(Modifier.padding(end = 8.dp).bounceClick(enabled = sender != null) { sender?.let(onSenderClick) }) {
                Avatar(sender?.avatarUrl.orEmpty(), sender?.displayName ?: "?", size = 34.dp)
            }
        }
        Surface(shape = shape, color = bg, contentColor = fg, modifier = Modifier.widthIn(max = 300.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                if (showSender) {
                    NameWithBadges(
                        name = sender?.displayName?.ifBlank { sender.username } ?: "Unknown",
                        verified = sender?.verified == true,
                        support = sender?.support == true,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        badgeSize = 15.dp,
                        modifier = Modifier.bounceClick(enabled = sender != null) { sender?.let(onSenderClick) }
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                Row(
                    Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatClock(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.alpha(0.7f)
                    )
                    if (mine) {
                        Spacer(Modifier.width(4.dp))
                        val read = message.readBy.any { it != message.senderId }
                        Icon(
                            if (read) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (read) "Read" else "Sent",
                            tint = if (read) scheme.primary else fg.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ input */

@Composable
private fun InputBar(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        PillTextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = "Message",
            singleLine = false,
            maxLines = 5,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        BouncyIconButton(
            icon = Icons.AutoMirrored.Filled.Send,
            description = "Send",
            onClick = onSend,
            enabled = draft.isNotBlank()
        )
    }
}

/* ------------------------------------------------------------------ group / channel info */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInfoSheet(
    chat: Chat,
    owner: UserProfile?,
    onDismiss: () -> Unit,
    onOpenProfile: (UserProfile) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar("", chat.name, size = 88.dp)
            Spacer(Modifier.height(12.dp))
            Text(chat.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                (if (chat.type == ChatType.CHANNEL) (if (chat.discoverable) "Public channel" else "Private channel") else "Group") +
                    " - ${chat.members.size} members",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (chat.description.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(chat.description, Modifier.padding(16.dp).fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (owner != null) {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().bounceClick { onOpenProfile(owner) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(owner.avatarUrl, owner.displayName, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Owner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NameWithBadges(owner.displayName.ifBlank { owner.username }, owner.verified, owner.support)
                    }
                }
            }
        }
    }
}
