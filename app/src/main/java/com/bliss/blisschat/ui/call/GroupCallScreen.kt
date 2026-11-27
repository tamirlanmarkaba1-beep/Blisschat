package com.bliss.blisschat.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BlobButton
import com.bliss.blisschat.ui.components.formatDuration
import kotlinx.coroutines.delay

/** Voice call of a group: a grid of participants (a dim avatar = still connecting) and the usual buttons. */
@Composable
fun GroupCallScreen(container: AppContainer) {
    val calls = container.groupCalls
    val ui by calls.ui.collectAsStateWithLifecycle()
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.active) { while (ui.active) { now = System.currentTimeMillis(); delay(1000) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BlissBackdrop(0)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text(ui.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "групповой звонок · " + formatDuration(now - ui.since) + " · " + ui.members.size,
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(ui.members, key = { it.uid }) { m ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.androidAlpha(if (m.connected) 1f else 0.4f)) { Avatar(m.avatarUrl, m.name, 84.dp) }
                        Spacer(Modifier.height(6.dp))
                        Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        if (!m.connected) Text("соединение…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                BlobButton(if (ui.muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Микрофон", { calls.toggleMute() }, size = 62.dp,
                    container = if (ui.muted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondaryContainer,
                    content = if (ui.muted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSecondaryContainer)
                BlobButton(if (ui.speaker) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, "Динамик", { calls.toggleSpeaker() }, size = 62.dp,
                    container = if (ui.speaker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    content = if (ui.speaker) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)
                BlobButton(Icons.Filled.CallEnd, "Выйти", { calls.leave() }, size = 70.dp, container = MaterialTheme.colorScheme.error, content = MaterialTheme.colorScheme.onError)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun Modifier.androidAlpha(a: Float): Modifier = this.then(Modifier.graphicsLayer(alpha = a))
