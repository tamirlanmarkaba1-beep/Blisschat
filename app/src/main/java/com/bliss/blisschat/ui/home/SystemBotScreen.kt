package com.bliss.blisschat.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissHeader
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.formatClock

/** Read-only chat with the built-in @blisschat_bot (messages are generated on this device). */
@Composable
fun SystemBotScreen(container: AppContainer, onBack: () -> Unit) {
    val messages by container.systemBot.messages.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) state.scrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BlissHeader {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                Avatar("", "blisschat bot", size = 44.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    NameWithBadges("blisschat_bot", verified = true, support = true, style = MaterialTheme.typography.titleMedium)
                    Text("системный бот", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.ts }) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 26.dp, bottomStart = 5.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(m.text, style = MaterialTheme.typography.bodyLarge)
                            Text(formatClock(m.ts), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End).padding(top = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.padding(end = 8.dp))
                Text("Системный бот: ответить ему нельзя", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
