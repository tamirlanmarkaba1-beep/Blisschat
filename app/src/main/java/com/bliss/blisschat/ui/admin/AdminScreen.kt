package com.bliss.blisschat.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.PillTextField

@Composable
fun AdminScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: AdminViewModel = viewModel(factory = container.factory())
    val groups by vm.groups.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Chat?>(null) }

    Box(Modifier.fillMaxSize()) {
        BlissBackdrop(2)
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text("администратор", style = MaterialTheme.typography.headlineSmall)
                }
            }
            item {
                Card {
                    Text("Значки пользователя", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    PillTextField(query, { query = it.trim().removePrefix("@") }, "@username", leading = Icons.Filled.Search,
                        trailing = { TextButton(onClick = { vm.find(query) }, enabled = query.length >= 3) { Text("Найти") } })
                    found?.let { u ->
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.avatarUrl, u.title, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column { NameWithBadges(u.title, u.verified, u.support); Text("@${u.username}", style = MaterialTheme.typography.bodySmall) }
                        }
                        Spacer(Modifier.height(8.dp))
                        ToggleRow("Верифицирован", u.verified) { vm.setVerified(it) }
                        ToggleRow("blisschat Support", u.support) { vm.setSupport(it) }
                        ToggleRow("Модератор плагинов", u.moderator) { vm.setModerator(it) }
                    }
                    status?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { Text("Группы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp)) }
            if (groups.isEmpty()) item { Text("Групп пока нет", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(groups, key = { it.id }) { g ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar("", g.name, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            NameWithBadges(g.name, g.verified, false)
                            Text("${g.members.size} участн.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(g.verified, { vm.setGroupVerified(g, it) })
                        IconButton(onClick = { toDelete = g }) { Icon(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    toDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, shape = RoundedCornerShape(28.dp),
            title = { Text("Удалить «${g.name}»?") }, text = { Text("Группа и все сообщения исчезнут у всех участников.") },
            confirmButton = { TextButton(onClick = { vm.deleteGroup(g); toDelete = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label); Switch(checked, onChange)
    }
}
