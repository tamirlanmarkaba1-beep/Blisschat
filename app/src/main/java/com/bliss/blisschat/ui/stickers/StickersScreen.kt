package com.bliss.blisschat.ui.stickers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.stickers.StickerPack
import com.bliss.blisschat.ui.components.BlissHeader
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.bounceClick
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun StickersScreen(container: AppContainer, onBack: () -> Unit) {
    val packs by container.stickers.packs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<StickerPack?>(null) }
    var deleting by remember { mutableStateOf<StickerPack?>(null) }
    var removing by remember { mutableStateOf<File?>(null) }
    var targetPackId by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        val packId = targetPackId ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var failed = 0
            uris.forEach { uri ->
                container.stickers.addFromUri(packId, uri).onFailure { failed++ }
            }
            if (failed > 0) {
                snackbar.showSnackbar("Не удалось добавить: $failed")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (com.bliss.blisschat.BuildConfig.GLASS) com.bliss.blisschat.ui.components.BlissBackdrop(2)
        Column(modifier = Modifier.fillMaxSize()) {
            BlissHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Text(
                        text = "Стикеры",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Добавляйте картинки в набор (PNG с прозрачным фоном выглядят лучше всего). Нажмите на стикер и удерживайте, чтобы удалить.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                BouncyButton(
                    text = "Новый набор",
                    onClick = { creating = true },
                    icon = Icons.Filled.Add,
                    modifier = Modifier.fillMaxWidth()
                )

                packs.forEach { pack ->
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = pack.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { renaming = pack }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Переименовать")
                                }
                                IconButton(onClick = { deleting = pack }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Удалить набор",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.size(8.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pack.stickers.forEach { stickerFile ->
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = { removing = stickerFile }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        StickerImage(stickerFile, 68.dp)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .bounceClick {
                                            targetPackId = pack.id
                                            picker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Добавить стикеры",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }

    if (creating) {
        NameDialog(
            title = "Новый набор",
            initial = "",
            confirm = "Создать",
            onDismiss = { creating = false },
            onOk = { rawName ->
                container.stickers.createPack(rawName.trim())
                creating = false
            }
        )
    }

    renaming?.let { packToRename ->
        NameDialog(
            title = "Переименовать",
            initial = packToRename.name,
            confirm = "Сохранить",
            onDismiss = { renaming = null },
            onOk = { rawName ->
                container.stickers.rename(packToRename.id, rawName.trim())
                renaming = null
            }
        )
    }

    deleting?.let { packToDelete ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить набор «${packToDelete.name}»?") },
            text = { Text("Все стикеры набора будут удалены с этого устройства.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.stickers.deletePack(packToDelete.id)
                        deleting = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    removing?.let { stickerToRemove ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Удалить стикер?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.stickers.removeSticker(stickerToRemove)
                        removing = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onOk: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            PillTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                placeholder = "Название"
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onOk(name) }
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
