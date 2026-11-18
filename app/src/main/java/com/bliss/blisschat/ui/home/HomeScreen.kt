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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
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
import com.bliss.blisschat.ui.components.formatListTime
import com.bliss.blisschat.ui.theme.ButtonBlob

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, onOpenChat: (String) -> Unit) {
    val vm: HomeViewModel = viewModel(factory = container.factory())
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        BlissBackdrop(1)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query, onQueryChange = vm::setQuery, onSearch = {}, expanded = false, onExpandedChange = {},
                        placeholder = { Text("Поиск людей, групп, каналов") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            AnimatedVisibility(query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, "Очистить") }
                            }
                        }
                    )
                },
                expanded = false, onExpandedChange = {},
                shape = RoundedCornerShape(28.dp),
                colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
            ) {}

            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("blisschat", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                    Text("чаты", style = MaterialTheme.typography.titleLarge)
                }
                BlobButton(
                    Icons.Filled.Add, "Новый чат", onClick = { showNew = true }, size = 64.dp,
                    container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ChatFilter.entries) { f ->
                    FilterChip(
                        selected = filter == f, onClick = { vm.toggleFilter(f) }, label = { Text(f.label) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            AnimatedContent(
                targetState = state.items.isEmpty() && !state.loading,
                transitionSpec = { fadeIn(tween(220, 60)) togetherWith fadeOut(tween(90)) }, label = "home-empty"
            ) { empty ->
                if (empty) {
                    EmptyState(filter != null, query.isNotBlank())
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.items, key = { it.chat.id }) { item ->
                            ChatRow(item, Modifier.animateItem()) { onOpenChat(item.chat.id) }
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewChatSheet(vm, onDismiss = { showNew = false }, onOpenChat = { id -> showNew = false; onOpenChat(id) })
    }
}

@Composable
private fun ChatRow(item: ChatItem, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(item.avatarUrl, item.title, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.chat.type == ChatType.GROUP) {
                    Icon(Icons.Filled.Group, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
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
            else "Нажмите «+», чтобы написать кому-то по @username или создать группу. Все сообщения шифруются на вашем устройстве.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(vm: HomeViewModel, onDismiss: () -> Unit, onOpenChat: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dmUser by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("") }
    var groupMembers by rememberSaveable { mutableStateOf("") }

    val handle: (Result<String>) -> Unit = { r -> busy = false; r.onSuccess(onOpenChat).onFailure { error = it.message } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            Text("Новый чат", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                listOf("Личный", "Группа").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i; error = null }, text = { Text(t) }) }
            }
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    Text("Напишите человеку по его @username.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PillTextField(dmUser, { dmUser = it; error = null }, "username", leading = Icons.Filled.AlternateEmail)
                } else {
                    PillTextField(groupName, { groupName = it; error = null }, "Название группы", leading = Icons.Filled.Group)
                    PillTextField(groupMembers, { groupMembers = it; error = null }, "Участники: @anna, @ivan", leading = Icons.Filled.AlternateEmail)
                    Text("У группы будет свой ключ шифрования, который получат только участники.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, start = 8.dp))
            }
            Spacer(Modifier.height(20.dp))
            BouncyButton(
                if (tab == 0) "Начать чат" else "Создать группу", loading = busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true; error = null
                    if (tab == 0) vm.startDirect(dmUser, handle) else vm.createGroup(groupName, groupMembers, handle)
                }
            )
        }
    }
}
