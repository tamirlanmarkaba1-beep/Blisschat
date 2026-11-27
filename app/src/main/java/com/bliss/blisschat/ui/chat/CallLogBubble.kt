package com.bliss.blisschat.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bliss.blisschat.data.Content

/** One line of call history inside the chat: who called, how it ended and for how long. */
@Composable
fun CallLogBubble(c: Content, mine: Boolean, fg: Color) {
    val video = c.w == 1
    val secs = c.h
    val dur = if (secs > 0) " · %d:%02d".format(secs / 60, secs % 60) else ""
    val missedIn = !mine && (c.text == "missed" || c.text == "cancelled")
    val kind = if (video) "Видеозвонок" else "Звонок"
    val title = when (c.text) {
        "group" -> "Групповой звонок начат"
        "group-end" -> "Групповой звонок завершён$dur"
        "ended" -> (if (mine) "Исходящий " else "Входящий ").plus(kind.lowercase()).replaceFirstChar { it.uppercase() } + dur
        "missed", "cancelled" -> if (mine) (if (c.text == "missed") "Без ответа" else "Отменённый звонок") else "Пропущенный звонок"
        "declined" -> if (mine) "Звонок отклонён" else "Вы отклонили звонок"
        "busy" -> "Абонент был занят"
        else -> kind
    }
    val icon = when {
        c.text.startsWith("group") -> Icons.Filled.Groups
        missedIn -> Icons.Filled.CallMissed
        c.text == "declined" || c.text == "busy" -> Icons.Filled.CallMissed
        video -> Icons.Filled.Videocam
        mine -> Icons.Filled.CallMade
        else -> Icons.Filled.CallReceived
    }
    val tint = if (missedIn) MaterialTheme.colorScheme.error else fg
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}
