package com.bliss.blisschat.ui.calls

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.data.call.CallHistory
import com.bliss.blisschat.data.call.CallRecord
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BlissHeader
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.components.formatListTime

/** Call log: who called whom, missed / incoming / outgoing, audio or video. Tap a row to open the chat. */
@Composable
fun CallsScreen(container: AppContainer, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val me = container.currentUid.orEmpty()
    val records by remember(me) { CallHistory.observe(me) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val ids = remember(records) { records.map { it.peerId }.toSet() }
    val users by remember(ids) { container.repo.observeUsers(ids) }.collectAsStateWithLifecycle(initialValue = emptyMap())

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BlissBackdrop(1)
        Column(Modifier.fillMaxSize()) {
            BlissHeader {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text("история звонков", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
            }
            if (records.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(48.dp))
                    Icon(Icons.Filled.Call, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    Spacer(Modifier.height(16.dp))
                    Text("Звонков пока не было", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Позвоните кому-нибудь из личного чата — здесь появятся входящие, исходящие и пропущенные.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()
                ) {
                    items(records, key = { it.id }) { r -> CallRow(r, users[r.peerId]) { if (r.chatId.isNotBlank()) onOpenChat(r.chatId) } }
                }
            }
        }
    }
}

@Composable
private fun CallRow(r: CallRecord, peer: UserProfile?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (r.missed) scheme.error else scheme.onSurfaceVariant
    val label = when {
        r.missed -> "Пропущенный"
        r.outgoing && !r.answered -> "Без ответа"
        r.outgoing -> "Исходящий"
        else -> "Входящий"
    } + if (r.video) " · видео" else ""
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainer.copy(alpha = 0.88f))
            .bounceClick(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(peer?.avatarUrl.orEmpty(), peer?.title ?: "?", size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NameWithBadges(peer?.title ?: "…", peer?.verified == true, peer?.support == true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when { r.missed -> Icons.AutoMirrored.Filled.CallMissed; r.outgoing -> Icons.AutoMirrored.Filled.CallMade; else -> Icons.AutoMirrored.Filled.CallReceived },
                    null, Modifier.size(16.dp), tint = tint
                )
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatListTime(r.ts), style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            Spacer(Modifier.height(4.dp))
            Icon(if (r.video) Icons.Filled.Videocam else Icons.Filled.Call, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        }
    }
}
