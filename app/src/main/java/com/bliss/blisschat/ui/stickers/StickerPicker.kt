package com.bliss.blisschat.ui.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.stickers.StickerPack
import com.bliss.blisschat.ui.components.bounceClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val stickerCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** A sticker stored in the app's own folder. */
@Composable
fun StickerImage(file: File, size: Dp, modifier: Modifier = Modifier) {
    val key = file.absolutePath + file.lastModified()
    // Must reload whenever the file changes: produceState keeps its old value, which used to show the previous pack's stickers.
    val bmp by produceState<Bitmap?>(stickerCache.get(key), key) {
        value = stickerCache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }?.also { stickerCache.put(key, it) }
    }
    Box(
        modifier = modifier.height(size),
        contentAlignment = Alignment.Center
    ) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.height(size),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPicker(
    container: AppContainer,
    onPick: (File, StickerPack) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    val packs: List<StickerPack> by container.stickers.packs.collectAsStateWithLifecycle()
    var selected by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            if (packs.isEmpty() || packs.none { it.stickers.isNotEmpty() }) {
                Text(
                    text = "Стикеров пока нет. Создайте свой набор — он появится здесь.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val idx = selected.coerceIn(0, packs.lastIndex)
                val currentPack = packs[idx]

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = packs.size) { i ->
                        FilterChip(
                            selected = i == idx,
                            onClick = { selected = i },
                            label = { Text(packs[i].name) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(84.dp),
                    modifier = Modifier.height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = currentPack.stickers, key = { it.absolutePath }) { stickerFile ->
                        Box(
                            modifier = Modifier.bounceClick {
                                onPick(stickerFile, currentPack)
                                onDismiss()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            StickerImage(file = stickerFile, size = 84.dp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onManage()
                    }
                ) {
                    Text("Создать / изменить наборы")
                }
            }
        }
    }
}

/**
 * Inline sticker panel shown instead of the keyboard (below the input bar). It does NOT close when a sticker is tapped:
 * tap a pack in the top row, then tap stickers to send them one after another.
 */
@Composable
fun StickerPanel(
    container: AppContainer,
    onPick: (File, StickerPack) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val packs: List<StickerPack> by container.stickers.packs.collectAsStateWithLifecycle()
    var selected by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme

    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth().height(300.dp),
        color = scheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val usable = packs.filter { it.stickers.isNotEmpty() }
            if (usable.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Стикеров пока нет. Создайте свой набор или сохраните стикер из чата.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant
                    )
                }
            } else {
                val idx = selected.coerceIn(0, usable.lastIndex)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(count = usable.size, key = { usable[it].id }) { i ->
                        val on = i == idx
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (on) scheme.primaryContainer else scheme.surfaceContainerHighest,
                            modifier = Modifier.bounceClick { selected = i }
                        ) {
                            Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                                StickerImage(file = usable[i].stickers.first(), size = 36.dp)
                            }
                        }
                    }
                }
                Text(
                    usable[idx].name, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = usable[idx].stickers, key = { it.absolutePath }) { f ->
                        Box(Modifier.bounceClick { onPick(f, usable[idx]) }, contentAlignment = Alignment.Center) {
                            StickerImage(file = f, size = 76.dp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onManage) { Text("Создать / изменить наборы") }
            }
        }
    }
}
