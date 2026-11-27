package com.bliss.blisschat.ui.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.plugins.Plugin
import com.bliss.blisschat.ui.components.BlissHeader
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.PillTextField

@Composable
private fun Shell(title: String, onBack: () -> Unit, snackbar: SnackbarHostState, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (com.bliss.blisschat.BuildConfig.GLASS) com.bliss.blisschat.ui.components.BlissBackdrop(2)
        Column(Modifier.fillMaxSize()) {
            BlissHeader {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
            }
            content()
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

@Composable
private fun Chip(text: String, container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(shape = CircleShape, color = container) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PluginsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: PluginsViewModel = viewModel(factory = container.factory())
    val approved by vm.approved.collectAsStateWithLifecycle()
    val mine by vm.mine.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }
    var tab by remember { mutableIntStateOf(0) }
    var help by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.submit(uri) }

    Shell("Плагины", onBack, snackbar) {
        TabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Каталог") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Мои плагины") })
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tab == 0) {
                if (approved.isEmpty()) item { Text("В каталоге пока пусто. Станьте первым — отправьте свой плагин во вкладке «Мои плагины».", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(approved, key = { it.id }) { p ->
                    PluginCard(p) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("от ${p.author}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.id in installed) TextButton(onClick = { vm.uninstall(p) }) { Text("Удалить") }
                            else BouncyButton(if (busy == p.id) "Установка…" else "Установить", onClick = { vm.install(p) }, enabled = busy == null)
                        }
                    }
                }
            } else {
                item {
                    BouncyButton("Отправить плагин (.zip)", onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        loading = busy == "submit", icon = Icons.Filled.Upload, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    BouncyButton("Создать плагин кодом (JSON)", onClick = { showCodeDialog = true }, icon = Icons.Filled.Edit, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { help = true }) { Text("Как сделать плагин?") }
                }
                if (mine.isEmpty()) item { Text("Вы ещё не отправляли плагинов.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(mine, key = { it.id }) { p ->
                    PluginCard(p) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (p.status) {
                                "approved" -> Chip("в каталоге", MaterialTheme.colorScheme.primaryContainer)
                                "rejected" -> Chip("отклонён", MaterialTheme.colorScheme.errorContainer)
                                else -> Chip("на проверке")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.deleteMine(p) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                        }
                        if (p.status == "rejected" && p.note.isNotBlank()) Text("Причина: ${p.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (help) AlertDialog(
        onDismissRequest = { help = false }, title = { Text("Формат плагина") },
        text = {
            Text(
                "Плагин — это .zip (до 6 МБ) с файлом manifest.json в корне.\n\n" +
                    "Тема:\n{\"name\":\"Океан\",\"version\":\"1.0\",\"description\":\"…\",\"type\":\"theme\",\"theme\":{\"hue\":200,\"dark\":true}}\n\n" +
                    "Стикеры:\n{\"name\":\"Коты\",\"version\":\"1.0\",\"type\":\"stickers\"}\nи рядом PNG/JPG/WEBP картинки (до 60 шт).\n\n" +
                    "Код:\n{\"name\":\"Ночной режим\",\"version\":\"1.0\",\"type\":\"code\"}\nи файл main.js (до 100 КБ). Скрипт работает в изолированной песочнице без интернета: он может добавлять свои настройки и менять тему/размер текста. Подробности: Настройки → Руководство.\n\n" +
                    "Сначала плагины проверяет модератор, потом они попадают в каталог."
            )
        },
        confirmButton = { TextButton(onClick = { help = false }) { Text("Понятно") } }
    )

    if (showCodeDialog) {
        var code by remember { mutableStateOf(SAMPLE_PLUGIN) }
        AlertDialog(
            onDismissRequest = { showCodeDialog = false },
            title = { Text("Создать и протестировать плагин (Код)") },
            text = {
                Column {
                    Text("Вставьте JavaScript плагина (он сразу запустится у вас на телефоне) или JSON темы {\"type\":\"theme\", …} для предпросмотра:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    PillTextField(code, { code = it }, "код плагина", maxLines = 12, singleLine = false)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.previewCode(code) }) { Text("Запустить") }
                    BouncyButton("Закрыть", onClick = { showCodeDialog = false })
                }
            },
            dismissButton = { TextButton(onClick = { showCodeDialog = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun PluginCard(p: Plugin, footer: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Chip(p.typeLabel)
            }
            Text("версия ${p.version}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodyMedium)
            footer()
        }
    }
}

@Composable
fun ModerationScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: PluginsViewModel = viewModel(factory = container.factory())
    val pending by vm.pending.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }
    var rejecting by remember { mutableStateOf<Plugin?>(null) }

    Shell("Модерация плагинов", onBack, snackbar) {
        if (me?.let { it.moderator || it.isAdmin } != true) {
            Text("Доступ только для модераторов.", Modifier.padding(24.dp))
        } else LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Скачайте архив, откройте и проверьте manifest.json и файлы, затем одобрите или отклоните заявку.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pending.isEmpty()) item { Text("Новых заявок нет.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(pending, key = { it.id }) { p ->
                PluginCard(p) {
                    Text("от ${p.author} · ${p.size / 1024} КБ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = busy == null, onClick = { vm.export(p) { context.startActivity(it) } }) { Text(if (busy == p.id) "…" else "Скачать zip") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { rejecting = p }) { Text("Отклонить", color = MaterialTheme.colorScheme.error) }
                        BouncyButton("Одобрить", onClick = { vm.moderate(p, true, "") })
                    }
                }
            }
        }
    }

    rejecting?.let { p ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null }, title = { Text("Отклонить «${p.name}»?") },
            text = { PillTextField(note, { note = it.take(300) }, "Причина (увидит автор)") },
            confirmButton = { TextButton(onClick = { vm.moderate(p, false, note); rejecting = null }) { Text("Отклонить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Отмена") } }
        )
    }
}


private const val SAMPLE_PLUGIN = """// name: Мой первый плагин
bliss.settings.add({id: "dark", title: "Тёмная тема", type: "switch", default: true,
  description: "Включает и выключает тёмную тему"});
bliss.settings.add({id: "hue", title: "Цвет приложения", type: "slider", min: 0, max: 360, default: 174});
bliss.settings.add({id: "size", title: "Размер текста", type: "slider", min: 0.8, max: 1.4, default: 1});
bliss.settings.add({id: "hello", title: "Поздороваться", type: "button"});

bliss.on("setting", function (id, value) {
  if (id === "dark") bliss.app.setTheme({dark: value});
  if (id === "hue") bliss.app.setTheme({hue: value});
  if (id === "size") bliss.app.setTextScale(value);
  if (id === "hello") bliss.toast("Привет из плагина!");
});
"""
