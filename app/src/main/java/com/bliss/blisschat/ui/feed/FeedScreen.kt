package com.bliss.blisschat.ui.feed

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.BuildConfig
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.data.Content
import com.bliss.blisschat.data.Message
import com.bliss.blisschat.ui.chat.EncryptedImage
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BlissHeader
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.components.formatListTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

private data class Post(val chat: Chat, val message: Message, val content: Content)

/** Feed: the latest posts of public channels. Open channels can be subscribed to right from a post. */
@Composable
fun FeedScreen(container: AppContainer, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val me = container.currentUid.orEmpty()
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<Post>() }
    val joined = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        loading = true; error = null
        runCatching {
            val channels = container.repo.listPublicChats(60, channelsOnly = true)
            joined.clear(); joined.addAll(channels.filter { me in it.members }.map { it.id })
            val all = kotlinx.coroutines.coroutineScope {
                channels.map { ch -> async { runCatching { container.repo.recentPosts(ch, me, 4).map { (m, c) -> Post(ch, m, c) } }.getOrDefault(emptyList()) } }.awaitAll()
            }.flatten().sortedByDescending { it.message.ts }
            posts.clear(); posts.addAll(all.take(80))
        }.onFailure { error = it.message ?: "Не удалось загрузить ленту" }
        loading = false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (BuildConfig.GLASS) BlissBackdrop(1)
        Column(Modifier.fillMaxSize()) {
            BlissHeader {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text("лента каналов", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { tick++ }) { Icon(Icons.Filled.Refresh, "Обновить") }
                }
            }
            when {
                loading && posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                posts.isEmpty() -> Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(48.dp))
                    Text(error ?: "Пока нет постов", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Здесь появляются посты публичных каналов.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()
                ) {
                    items(posts, key = { it.chat.id + "/" + it.message.id }) { p ->
                        PostCard(p, container, subscribed = p.chat.id in joined,
                            onOpen = { onOpenChat(p.chat.id) },
                            onSubscribe = {
                                scope.launch { container.repo.joinPublic(p.chat.id, me).onSuccess { joined.add(p.chat.id) } }
                            })
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(p: Post, container: AppContainer, subscribed: Boolean, onOpen: () -> Unit, onSubscribe: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth().bounceClick(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.chat.avatarUrl, p.chat.name.ifBlank { "?" }, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    NameWithBadges(p.chat.name.ifBlank { "Канал" }, p.chat.verified, false)
                    Text("@" + p.chat.username + " · " + formatListTime(p.message.ts), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (subscribed) OutlinedButton(onClick = onOpen, shape = RoundedCornerShape(20.dp)) { Text("открыть") }
                else FilledTonalButton(onClick = onSubscribe, shape = RoundedCornerShape(20.dp)) { Text("подписаться") }
            }
            Spacer(Modifier.height(10.dp))
            val c = p.content
            if (c.kind == Content.KIND_IMAGE) EncryptedImage(c, container.repo, onClick = { onOpen() })
            val text = when (c.kind) { Content.KIND_TEXT -> c.text; Content.KIND_IMAGE -> c.caption; else -> c.preview }
            if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = if (c.kind == Content.KIND_IMAGE) 8.dp else 0.dp))
        }
    }
}
