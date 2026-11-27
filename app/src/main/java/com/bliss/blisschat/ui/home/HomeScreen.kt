package com.bliss.blisschat.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.ChatItem
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BlobButton
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.components.SegStart
import com.bliss.blisschat.ui.components.SegEnd
import com.bliss.blisschat.ui.components.SplitSearchBar
import com.bliss.blisschat.ui.components.formatListTime
import com.bliss.blisschat.ui.theme.ButtonBlob
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.Call
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.flow.MutableStateFlow

/** The round search button of the bottom bar bumps this counter; the home screen then focuses its search field. */
object SearchFocus { val tick = MutableStateFlow(0); var handled = 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, onOpenChat: (String) -> Unit, onCreate: () -> Unit, onOpenSystemBot: () -> Unit, onOpenCalls: () -> Unit = {}, onOpenFeed: () -> Unit = {}) {
    val searchFocus = remember { FocusRequester() }
    val focusTick by SearchFocus.tick.collectAsStateWithLifecycle()
    // the round search button of the bottom bar opens the search over MY chats; the bar on top searches everywhere
    var localOpen by remember { mutableStateOf(false) }
    LaunchedEffect(focusTick) {
        if (focusTick > SearchFocus.handled) { SearchFocus.handled = focusTick; localOpen = true }
    }
    LaunchedEffect(localOpen) { if (localOpen) { kotlinx.coroutines.yield(); runCatching { searchFocus.requestFocus() } } }
    var globalQ by remember { mutableStateOf("") }
    var globalPeople by remember { mutableStateOf<List<com.bliss.blisschat.data.UserProfile>>(emptyList()) }
    var globalChats by remember { mutableStateOf<List<com.bliss.blisschat.data.Chat>>(emptyList()) }
    var globalBusy by remember { mutableStateOf(false) }
    LaunchedEffect(globalQ) {
        if (globalQ.trim().length < 2) { globalPeople = emptyList(); globalChats = emptyList(); globalBusy = false; return@LaunchedEffect }
        globalBusy = true
        kotlinx.coroutines.delay(350)
        runCatching { container.repo.searchGlobal(globalQ) }.onSuccess { (p, c) -> globalPeople = p.filter { it.uid != container.currentUid }; globalChats = c }
        globalBusy = false
    }
    val vm: HomeViewModel = viewModel(factory = container.factory())
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val botMessages by vm.systemBot.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        BlissBackdrop(1)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(12.dp))
            SplitSearchBar(
                globalQ, { globalQ = it }, "поиск людей, групп, каналов", Modifier.padding(horizontal = 16.dp),
                trailing = if (globalQ.isNotEmpty()) ({ IconButton(onClick = { globalQ = "" }) { Icon(Icons.Filled.Clear, "Очистить") } }) else null
            )
            if (localOpen || query.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                SplitSearchBar(
                    query, vm::setQuery, "поиск по моим чатам", Modifier.padding(horizontal = 16.dp),
                    fieldModifier = Modifier.focusRequester(searchFocus),
                    trailing = { IconButton(onClick = { vm.setQuery(""); localOpen = false }) { Icon(Icons.Filled.Clear, "Закрыть") } }
                )
            }
            Spacer(Modifier.height(8.dp))
            if (globalQ.trim().length >= 2) {
                GlobalResults(globalPeople, globalChats, globalBusy, container.currentUid.orEmpty(),
                    onUser = { u -> vm.startDirect(u.username) { r -> r.onSuccess { id -> globalQ = ""; onOpenChat(id) } } },
                    onChat = { ch -> globalQ = ""; onOpenChat(ch.id) })
                return@Column
            }

            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("blisschat", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                    Text("чаты", style = MaterialTheme.typography.titleLarge)
                }
                BlobButton(
                    Icons.Filled.Campaign, "Лента каналов", onClick = onOpenFeed, size = 52.dp,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                BlobButton(
                    Icons.Filled.Call, "История звонков", onClick = onOpenCalls, size = 52.dp,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                BlobButton(
                    Icons.Filled.Add, "Создать", onClick = onCreate, size = 64.dp,
                    container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            var categoryMenuOpen by remember { mutableStateOf(false) }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { categoryMenuOpen = true },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.FilterList, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(filter?.label ?: "Все категории")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Все категории") }, onClick = { categoryMenuOpen = false; vm.setFilter(null) })
                    ChatFilter.entries.forEach { f ->
                        DropdownMenuItem(text = { Text(f.label) }, onClick = { categoryMenuOpen = false; vm.toggleFilter(f) })
                    }
                }
            }

            AnimatedContent(
                targetState = state.items.isEmpty() && !state.loading,
                transitionSpec = { fadeIn(tween(220, 60)) togetherWith fadeOut(tween(90)) }, label = "home-empty"
            ) { empty ->
                if (empty) {
                    Column(Modifier.fillMaxSize()) {
                        if (filter == null && query.isBlank()) Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp)) {
                            SystemBotRow(botMessages.lastOrNull()?.text?.lineSequence()?.firstOrNull().orEmpty(), botMessages.lastOrNull()?.ts ?: 0L, onOpenSystemBot)
                        }
                        EmptyState(filter != null, query.isNotBlank())
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()
                    ) {
                        if (filter == null && query.isBlank()) item(key = "system-bot") {
                            SystemBotRow(botMessages.lastOrNull()?.text?.lineSequence()?.firstOrNull().orEmpty(), botMessages.lastOrNull()?.ts ?: 0L, onOpenSystemBot)
                        }
                        items(state.items, key = { it.chat.id }) { item ->
                            ChatRow(item, Modifier.animateItem()) { onOpenChat(item.chat.id) }
                        }
                    }
                }
            }
        }
    }

}

/** Pinned row of the built-in @blisschat_bot. */
@Composable
private fun SystemBotRow(preview: String, time: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .bounceClick(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar("", "blisschat bot", size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NameWithBadges("blisschat_bot", verified = true, support = true)
            Text(preview.ifBlank { "Системные сообщения" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (time > 0) Text(formatListTime(time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ChatRow(item: ChatItem, modifier: Modifier, onClick: () -> Unit) {
    val seg = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(modifier.fillMaxWidth().bounceClick(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(SegStart).background(seg).size(width = 84.dp, height = 80.dp), contentAlignment = Alignment.Center) {
            Avatar(item.avatarUrl, item.title, size = 60.dp)
        }
        Row(
            Modifier.weight(1f).height(80.dp).clip(SegEnd).background(seg).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.chat.type != ChatType.DIRECT) {
                        Icon(if (item.chat.type == ChatType.CHANNEL) Icons.Filled.Campaign else Icons.Filled.Group, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                    }
                    if (item.chat.secret) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    NameWithBadges(item.title.ifBlank { "Без названия" }, item.verified, item.support)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    item.preview, style = MaterialTheme.typography.bodyMedium,
                    color = if (item.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatListTime(item.time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                if (item.unread) Box(Modifier.size(16.dp).clip(ButtonBlob).background(MaterialTheme.colorScheme.primary))
                else Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, searching: Boolean) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text(if (searching || filtered) "Ничего не найдено" else "Пока нет чатов", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            if (searching || filtered) "Попробуйте другой запрос или фильтр."
            else "Нажмите «+», чтобы написать кому-то, создать канал, группу или бота. Все сообщения шифруются на вашем устройстве.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GlobalResults(
    people: List<com.bliss.blisschat.data.UserProfile>, chats: List<com.bliss.blisschat.data.Chat>, busy: Boolean, myId: String,
    onUser: (com.bliss.blisschat.data.UserProfile) -> Unit, onChat: (com.bliss.blisschat.data.Chat) -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()
    ) {
        if (busy && people.isEmpty() && chats.isEmpty()) item { Text("Ищу…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!busy && people.isEmpty() && chats.isEmpty()) item { Text("Ничего не найдено", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (people.isNotEmpty()) item { Text("Люди", Modifier.padding(start = 10.dp, top = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        items(people, key = { "u" + it.uid }) { u ->
            GlobalRow(u.avatarUrl, u.title, "@" + u.username, u.verified, u.support) { onUser(u) }
        }
        if (chats.isNotEmpty()) item { Text("Группы и каналы", Modifier.padding(start = 10.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        items(chats, key = { "c" + it.id }) { ch ->
            val sub = (if (ch.type == ChatType.CHANNEL) "канал" else "группа") + " · @" + ch.username + if (myId in ch.members) " · вы участник" else ""
            GlobalRow(ch.avatarUrl, ch.name, sub, ch.verified, false) { onChat(ch) }
        }
    }
}

@Composable
private fun GlobalRow(avatar: String, title: String, subtitle: String, verified: Boolean, support: Boolean, onClick: () -> Unit) {
    val seg = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(seg).bounceClick(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(avatar, title.ifBlank { "?" }, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NameWithBadges(title.ifBlank { "Без названия" }, verified, support)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
