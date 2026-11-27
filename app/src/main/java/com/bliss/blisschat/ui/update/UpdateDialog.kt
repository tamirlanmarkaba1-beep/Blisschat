package com.bliss.blisschat.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.data.update.UpdateInfo
import com.bliss.blisschat.data.update.UpdateManager
import com.bliss.blisschat.data.update.UpdateState

/** Material 3 dialog shown over the main screen when a newer version is on GitHub. */
@Composable
fun UpdateDialog(manager: UpdateManager) {
    val ui by manager.ui.collectAsStateWithLifecycle()
    if (ui.dismissed) return
    when (val st = ui.state) {
        is UpdateState.Available -> Dialog(
            st.info, manager,
            body = null,
            confirm = "Обновить" to { manager.download() },
            dismiss = "Позже" to { manager.dismiss() }
        )
        is UpdateState.Downloading -> Dialog(
            st.info, manager, body = st.progress, confirm = null, dismiss = "Скрыть" to { manager.dismiss() }
        )
        is UpdateState.ReadyToInstall -> Dialog(
            st.info, manager, body = null,
            note = if (st.needsPermission) "Разрешите установку приложений из этого источника в настройках, затем нажмите «Установить»." else "Загрузка завершена.",
            confirm = "Установить" to { manager.install() },
            dismiss = "Позже" to { manager.dismiss() }
        )
        else -> Unit
    }
}

@Composable
private fun Dialog(
    info: UpdateInfo, manager: UpdateManager, body: Float?,
    note: String? = null,
    confirm: Pair<String, () -> Unit>?, dismiss: Pair<String, () -> Unit>
) {
    AlertDialog(
        onDismissRequest = dismiss.second,
        shape = RoundedCornerShape(32.dp),
        icon = { Icon(Icons.Filled.SystemUpdate, null) },
        title = { Text("Доступно обновление") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Новая версия ${info.versionName}, у вас ${manager.currentVersionName}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (info.changelog.isNotBlank()) {
                    Text("Что нового", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        info.changelog.split("\n", ";").map { it.trim().trimStart('-', '•', '*', ' ') }.filter { it.isNotEmpty() }.forEach {
                            Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                }
                if (body != null) {
                    Spacer(Modifier.padding(2.dp))
                    if (body in 0f..1f) LinearProgressIndicator(progress = { body }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (body in 0f..1f) "Загрузка ${(body * 100).toInt()}%" else "Загрузка…", style = MaterialTheme.typography.bodySmall)
                }
                if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { if (confirm != null) TextButton(onClick = confirm.second) { Text(confirm.first) } },
        dismissButton = { TextButton(onClick = dismiss.second) { Text(dismiss.first) } }
    )
}
