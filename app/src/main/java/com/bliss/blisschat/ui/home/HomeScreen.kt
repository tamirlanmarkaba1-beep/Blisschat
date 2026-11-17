package com.bliss.blisschat.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.ChatItem
import com.bliss.blisschat.data.ChatType
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.components.formatListTime
import com.bliss.blisschat.ui.components.pressScale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = container.factory())
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

    var showNew by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val fabSource = remember { MutableInteractionSource() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Forum, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("blisschat", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    state.me?.let { me ->
                        Box(Modifier.padding(end = 14.dp).bounceClick(onClick = onOpenSettings)) {
                            Avatar(me.avatarUrl, me.displayName.ifBlank { me.username }, size = 38.dp)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Filled.Edit, null) },
                text = { Text("New chat") },
                shape = RoundedCornerShape(24.dp),
                interactionSource = fabSource,
                modifier = Modifier.pressScale(fabSource, 0.9f)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = vm::setQuery,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Search chats and channels") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            AnimatedVisibility(query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, "Clear") }
                            }
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp).fillMaxWidth()
            ) {}

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ChatFilter.entries) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { vm.setFilter(f) },
                        label = { Text(f.label) },
                        shape = CircleShape,
                        leadingIcon = if (filter == f) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }

            AnimatedContent(
                targetState = state.items.isEmpty() && !state.loading,
                transitionSpec = { fadeIn(tween(220, 60)) togetherWith fadeOut(tween(90)) },
                label = "home-empty"
            ) { empty ->
                if (empty) {
                    EmptyState(filter, query.isNotBlank())
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.items, key = { it.chat.id }) { item ->
                            ChatRow(
                                item = item,
                                modifier = Modifier.animateItem(),
                                onClick = { onOpenChat(item.chat.id) },
                                onJoin = {
                                    vm.join(item.chat.id) { r ->
                                        r.onSuccess { onOpenChat(item.chat.id) }
                                            .onFailure { scope.launch { snackbar.showSnackbar(it.message ?: "Couldn't join") } }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewChatSheet(
            vm = vm,
            onDismiss = { showNew = false },
            onOpenChat = { id ->
                showNew = false
                onOpenChat(id)
            }
        )
    }
}

@Composable
private fun ChatRow(item: ChatItem, modifier: Modifier, onClick: () -> Unit, onJoin: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(item.avatarUrl, item.title, size = 54.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (item.chat.type) {
                    ChatType.GROUP -> Icon(Icons.Filled.Group, null, Modifier.size(16.dp).padding(end = 0.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChatType.CHANNEL -> Icon(Icons.Filled.Campaign, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChatType.DIRECT -> {}
                }
                if (item.chat.type != ChatType.DIRECT) Spacer(Modifier.width(5.dp))
                NameWithBadges(
                    name = item.title.ifBlank { "Untitled" },
                    verified = item.verified,
                    support = item.support,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (item.joined) {
            Text(
                formatListTime(item.time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FilledTonalButton(onClick = onJoin, shape = CircleShape) { Text("Join") }
        }
    }
}

@Composable
private fun EmptyState(filter: ChatFilter, searching: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Forum, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                searching -> "Nothing found"
                filter == ChatFilter.DISCOVER -> "No public channels to join yet"
                else -> "No chats yet"
            },
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                searching -> "Try a different search."
                filter == ChatFilter.DISCOVER -> "Create a public channel and it will show up here."
                else -> "Tap \"New chat\" to message someone by their @username, or create a group or channel."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/* ------------------------------------------------------------------ new chat sheet */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(vm: HomeViewModel, onDismiss: () -> Unit, onOpenChat: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var dmUser by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("") }
    var groupMembers by rememberSaveable { mutableStateOf("") }
    var chName by rememberSaveable { mutableStateOf("") }
    var chDesc by rememberSaveable { mutableStateOf("") }
    var chPublic by rememberSaveable { mutableStateOf(true) }

    val handle: (Result<String>) -> Unit = { r ->
        busy = false
        r.onSuccess(onOpenChat).onFailure { error = it.message }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("New", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            PrimaryTabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                listOf("Direct", "Group", "Channel").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i; error = null }, text = { Text(title) })
                }
            }
            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220, 60)) togetherWith fadeOut(tween(90)) },
                label = "new-chat-tab"
            ) { t ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (t) {
                        0 -> {
                            Text(
                                "Message someone directly using their @username.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PillTextField(dmUser, { dmUser = it; error = null }, "username", leading = Icons.Filled.AlternateEmail)
                        }
                        1 -> {
                            PillTextField(groupName, { groupName = it; error = null }, "Group name", leading = Icons.Filled.Group)
                            PillTextField(
                                groupMembers, { groupMembers = it; error = null },
                                "Members: @alice, @bob (optional)", leading = Icons.Filled.Tag
                            )
                        }
                        else -> {
                            PillTextField(chName, { chName = it; error = null }, "Channel name", leading = Icons.Filled.Campaign)
                            PillTextField(
                                chDesc, { chDesc = it }, "Description (optional)",
                                singleLine = false, maxLines = 3, shape = RoundedCornerShape(28.dp)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Public channel", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (chPublic) "Anyone can find and join it" else "Hidden from Discover",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = chPublic, onCheckedChange = { chPublic = it })
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(error != null) {
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp, start = 8.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            BouncyButton(
                text = when (tab) { 0 -> "Start chat"; 1 -> "Create group"; else -> "Create channel" },
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    error = null
                    when (tab) {
                        0 -> vm.startDirect(dmUser, handle)
                        1 -> vm.createGroup(groupName, groupMembers, handle)
                        else -> vm.createChannel(chName, chDesc, chPublic, handle)
                    }
                }
            )
        }
    }
}
