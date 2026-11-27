@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.bliss.blisschat.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.push.Notifications
import com.bliss.blisschat.data.push.PushRelay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.bliss.blisschat.data.update.UpdateState
import com.bliss.blisschat.ui.chat.ImageCache
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.OutlinedPillButton
import com.bliss.blisschat.ui.security.SecretDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun hueColor(h: Float) = Color.hsv(h.coerceIn(0f, 359.9f), 0.78f, 0.95f)
private val Rainbow = Brush.horizontalGradient((0..12).map { hueColor(it * 30f) })

/** Hues of the colour grid (the last one, -1, is the colourless "black" palette). */
private val SwatchHues = listOf(0f, 38f, 72f, 158f, 193f, 222f, 262f, 292f, -1f)

private fun swatchColor(h: Float) = if (h < 0f) Color.Black else Color.hsv(h, 1f, 1f)

/* ============================================================== appearance ("темы") */

@Composable
fun AppearancePage(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val s by vm.settings.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    var paletteOpen by remember { mutableStateOf(false) }

    SettingsPage("темы", onBack) {
        BlockLabel("предосмотр")
        SCard {
            val k = s.textScale
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewBubble(true, "имя (вы)", "привет, как тебе blisschat?", "14:08", k)
                PreviewBubble(false, "clover", "привет, blisschat просто бомбезный!", "14:09", k)
            }
        }

        BlockLabel("цвет", big = true)
        SCard {
            val custom = SwatchHues.none { it == s.hue }
            val cells = listOf<Float?>(null) + SwatchHues
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cells.chunked(5).forEach { rowCells ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowCells.forEach { h ->
                            val selected = if (h == null) custom else h == s.hue
                            val bg = when {
                                h == null -> if (custom && s.hue >= 0f) hueColor(s.hue) else scheme.outline
                                else -> swatchColor(h)
                            }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(bg)
                                    .then(if (selected && h != null) Modifier.border(3.dp, scheme.onSurface, RoundedCornerShape(22.dp)) else Modifier)
                                    .clickable { if (h == null) paletteOpen = true else vm.setHue(h) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (h == null) Icon(Icons.Filled.Palette, "Свой цвет", Modifier.size(34.dp), tint = scheme.surface)
                                else if (selected) Icon(Icons.Filled.Check, null, Modifier.size(30.dp), tint = if (h < 0f) Color.White else Color.Black)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SCard(Modifier.weight(1f)) {
                Text("выберете тему", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(scheme.surfaceContainerHighest)) {
                    ThemeSeg(Icons.Filled.LightMode, !s.dark) { vm.setDark(false) }
                    ThemeSeg(Icons.Filled.DarkMode, s.dark) { vm.setDark(true) }
                }
            }
            SCard(Modifier.weight(1f)) {
                Text("динамические цвета", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "подстраивается под обои", style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(s.dynamicColors, { vm.setDynamicColors(it) }, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("жидкое стекло", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "БЕТА", style = MaterialTheme.typography.labelSmall, color = scheme.onPrimary,
                            modifier = Modifier.clip(CircleShape).background(scheme.primary).padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        "прозрачные поверхности поверх живого цветного фона. Цвет берётся из выбранного акцента. Может чуть сильнее нагружать телефон.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(s.glassTheme, { vm.setGlassTheme(it) })
            }
        }

        if (com.bliss.blisschat.BuildConfig.GLASS) {
            Spacer(Modifier.height(10.dp))
            SCard {
                Text("качество стекла", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                val q = com.bliss.blisschat.ui.glass.GlassQuality.of(s.glassQuality)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.bliss.blisschat.ui.glass.GlassQuality.entries.forEach { item ->
                        val on = item == q
                        Text(
                            item.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                            color = if (on) scheme.onPrimary else scheme.onSurface,
                            modifier = Modifier.weight(1f).clip(CircleShape)
                                .background(if (on) scheme.primary else scheme.surfaceContainerHighest)
                                .clickable { vm.setGlassQuality(item.name) }.padding(vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(q.hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                com.bliss.blisschat.ui.glass.GlassPreview(q)
            }
        }

        BlockLabel("размер", big = true)
        SCard {
            Text("размер текста в чатах", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Slider(
                value = s.textScale, onValueChange = { vm.setTextScale(it) }, valueRange = 0.8f..1.4f,
                thumb = { Box(Modifier.width(5.dp).height(46.dp).clip(CircleShape).background(scheme.primary)) },
                track = {
                    val frac = ((s.textScale - 0.8f) / 0.6f).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(scheme.primary))
                    }
                }
            )
            Text("${(s.textScale * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    if (paletteOpen) AlertDialog(
        onDismissRequest = { paletteOpen = false }, shape = RoundedCornerShape(28.dp),
        title = { Text("Свой цвет") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(hueColor(if (s.hue < 0f) 174f else s.hue)))
                Spacer(Modifier.width(14.dp))
                Slider(
                    value = if (s.hue < 0f) 174f else s.hue, onValueChange = { vm.setHue(it) }, valueRange = 0f..360f, modifier = Modifier.weight(1f),
                    track = { Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Rainbow)) },
                    thumb = { Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White).padding(5.dp).clip(CircleShape).background(hueColor(if (s.hue < 0f) 174f else s.hue))) }
                )
            }
        },
        confirmButton = { TextButton(onClick = { paletteOpen = false }) { Text("Готово") } }
    )
}

@Composable
private fun ThemeSeg(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape).background(if (selected) scheme.inverseSurface else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, Modifier.size(26.dp), tint = if (selected) scheme.inverseOnSurface else scheme.onSurfaceVariant) }
}

@Composable
private fun PreviewBubble(mine: Boolean, name: String, text: String, time: String, k: Float) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (mine) scheme.primary else scheme.surfaceContainerHighest
    val fg = if (mine) scheme.onPrimary else scheme.onSurface
    val body = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * k, lineHeight = it.lineHeight * k) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = bg, contentColor = fg,
            shape = if (mine) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                Text(name, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.65f), modifier = Modifier.align(Alignment.Start))
                Text(text, style = body)
                Text(time, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
            }
        }
    }
}

/* ============================================================== notifications */

@Composable
fun NotificationsPage(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val s by vm.settings.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    SettingsPage("уведомления", onBack) {
        Spacer(Modifier.height(4.dp))
        SCard {
            SwitchRow(Icons.Filled.Notifications, "Сообщения", s.notifMessages) { vm.setNotifMessages(it) }
            SwitchRow(Icons.Filled.Call, "Звонки", s.notifCalls) { vm.setNotifCalls(it) }
            SwitchRow(Icons.Filled.Fullscreen, "Звонок на весь экран (на заблокированном телефоне)", s.notifFullScreenCalls) { vm.setNotifFullScreen(it) }
            if (Build.VERSION.SDK_INT >= 34 && s.notifFullScreenCalls && !Notifications.fullScreenAllowed(ctx)) {
                Text(
                    "Android блокирует полноэкранные звонки. Разрешите их для blisschat.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)
                )
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Открыть разрешение") }
            }
        }
        Spacer(Modifier.height(10.dp))
        NotificationDiagnostics()
        Spacer(Modifier.height(10.dp))
        SCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Без уведомлений", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Отключить собеседника или чат можно в меню чата (⋮ → «Выключить уведомления»).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
            if (muted.isEmpty()) {
                Text("Список пуст: уведомления приходят от всех.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            muted.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((if (m.key.startsWith("u:")) "👤 " else "💬 ") + m.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.unmute(m.key) }) { Text("Включить") }
                }
            }
        }
    }
}

/* ============================================================== privacy & data */

@Composable
fun PrivacyPage(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val s by vm.settings.collectAsStateWithLifecycle()

    SettingsPage("приватность", onBack) {
        Spacer(Modifier.height(4.dp))
        SCard {
            SwitchRow(Icons.Filled.RemoveRedEye, "Скрывать статус «в сети»", s.hideOnline) { vm.setHideOnline(it) }
            SwitchRow(Icons.Filled.Image, "Автоматически загружать фото", s.autoLoadPhotos) { vm.setAutoLoadPhotos(it) }
            SwitchRow(Icons.Filled.Lock, "Запомнить меня", s.rememberMe) { vm.setRememberMe(it) }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { ImageCache.clear() }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CleaningServices, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Очистить кэш изображений", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/* ============================================================== encryption & key backup */

@Composable
fun SecurityPage(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val info by vm.backup.collectAsStateWithLifecycle()
    var saveOpen by remember { mutableStateOf(false) }
    var restoreOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshBackup() }

    SettingsPage("шифрование", onBack) {
        Spacer(Modifier.height(4.dp))
        SCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Сквозное шифрование", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Обычные чаты, группы и каналы не требуют ключей: переписка просто следует за аккаунтом. " +
                    "Сквозное шифрование работает только в секретных чатах (профиль человека → «Секретный чат»). " +
                    "Их ключи живут на этом телефоне, поэтому для входа на новом телефоне нужна резервная копия ключей.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        SCard {
            val b = info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (b?.exists == true) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                    tint = if (b?.exists == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(12.dp))
                Text("Резервная копия ключей", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    b == null -> "Проверяем…"
                    b.exists -> "Создана " + SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(b.ts)) +
                        if (b.custom) " · защищена отдельным кодом" else " · защищена паролем аккаунта"
                    else -> "Копии нет. Если сменить телефон или переустановить приложение, старые сообщения станут недоступны."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            note?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(14.dp))
            BouncyButton(if (info?.exists == true) "Обновить копию" else "Создать копию", onClick = { saveOpen = true }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedPillButton("Восстановить из копии", onClick = { restoreOpen = true }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (saveOpen) SecretDialog(
        title = "Резервная копия", confirmLabel = "Сохранить", offerCustom = true,
        message = "Введите пароль вашего аккаунта — по нему при входе копия откроется сама. Или включите переключатель и придумайте отдельный код (нужен, если вы входите через Google).",
        onDismiss = { saveOpen = false },
        onConfirm = { secret, custom -> vm.saveBackup(secret, custom).also { if (it == null) note = "Копия сохранена ✓" } }
    )
    if (restoreOpen) SecretDialog(
        title = "Восстановить ключи", confirmLabel = "Восстановить", offerCustom = false,
        message = "Введите пароль аккаунта или код восстановления, которым защищена копия. Старые сообщения откроются после повторного входа в чат.",
        onDismiss = { restoreOpen = false },
        onConfirm = { secret, _ -> vm.restoreBackup(secret).also { if (it == null) note = "Ключи восстановлены ✓" } }
    )
}

/* ============================================================== python bots */

@Composable
fun BotsPage(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val myBots by vm.myBots.collectAsStateWithLifecycle()

    SettingsPage("боты", onBack) {
        Spacer(Modifier.height(4.dp))
        SCard {
            if (myBots.isEmpty()) {
                Text(
                    "У вас нет созданных ботов. Создать бота можно через меню создания (+) → «Создать бота». Как написать бота: Настройки → Руководство.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                myBots.forEach { bot ->
                    var codeInput by remember(bot.uid) { mutableStateOf(bot.pythonCode) }
                    var editing by remember(bot.uid) { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("@${bot.username} (${bot.displayName})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editing = !editing }) { Text(if (editing) "Закрыть" else "Код") }
                            TextButton(onClick = { vm.deleteBot(bot.uid) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                        }
                        if (editing) {
                            OutlinedTextField(
                                value = codeInput, onValueChange = { codeInput = it }, label = { Text("Python скрипт бота") },
                                modifier = Modifier.fillMaxWidth().height(180.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            BouncyButton("Сохранить скрипт", onClick = { vm.updateBotCode(bot.uid, codeInput) { } })
                        }
                    }
                }
            }
        }
    }
}

/* ============================================================== update centre */

@Composable
fun UpdatesPage(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val upd by vm.updates.ui.collectAsStateWithLifecycle()

    SettingsPage("обновления", onBack) {
        Spacer(Modifier.height(4.dp))
        SCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("blisschat ${vm.updates.currentVersionName}", style = MaterialTheme.typography.titleMedium)
                    Text("сборка ${vm.updates.currentVersionCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            when (val st = upd.state) {
                is UpdateState.Checking -> { Text("Проверка…", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is UpdateState.UpToDate -> Text("У вас последняя версия ✓", style = MaterialTheme.typography.bodyMedium)
                is UpdateState.Failed -> Text(st.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                is UpdateState.Available -> {
                    Text("Доступна версия ${st.info.versionName} (сборка ${st.info.versionCode})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (st.info.changelog.isNotBlank()) Text(st.info.changelog, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                is UpdateState.Downloading -> {
                    Text("Загрузка обновления…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    if (st.progress in 0f..1f) LinearProgressIndicator(progress = { st.progress }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is UpdateState.ReadyToInstall -> Text(
                    if (st.needsPermission) "Разрешите установку из этого источника, затем нажмите «Установить»." else "Загрузка завершена.",
                    style = MaterialTheme.typography.bodyMedium
                )
                UpdateState.Idle -> Text("Проверка выполняется при каждом запуске.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (upd.lastChecked > 0) {
                Text(
                    "Последняя проверка: " + SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(upd.lastChecked)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            when (val st = upd.state) {
                is UpdateState.Available -> BouncyButton("Скачать и установить", onClick = { vm.updates.download() }, modifier = Modifier.fillMaxWidth())
                is UpdateState.ReadyToInstall -> BouncyButton("Установить", onClick = { vm.updates.install(st.info) }, modifier = Modifier.fillMaxWidth())
                is UpdateState.Downloading, UpdateState.Checking -> Unit
                else -> OutlinedPillButton("Проверить обновления", onClick = { vm.updates.check(silent = false) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}


/** "Проверить уведомления": shows what exactly stops pushes from arriving and lets the person test the whole chain. */
@Composable
private fun NotificationDiagnostics() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<Pair<Boolean, String>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    fun run() {
        busy = true
        scope.launch {
            val out = mutableListOf<Pair<Boolean, String>>()
            val perm = Notifications.canPost(ctx)
            out += perm to (if (perm) "Разрешение на уведомления выдано" else "Android запретил уведомления для blisschat (включите в настройках приложения)")
            val enabled = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
            out += enabled to (if (enabled) "Уведомления включены в системе" else "Уведомления выключены в системе")
            val pm = ctx.getSystemService(android.os.PowerManager::class.java)
            val ignoring = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            out += ignoring to (if (ignoring) "Экономия батареи не мешает" else "Экономия батареи может блокировать пуши в фоне (особенно Xiaomi, Huawei, Samsung)")
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val hasToken = runCatching {
                val t = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid!!).get().await()
                (doc.get("fcmTokens") as? List<*>)?.contains(t) == true
            }.getOrDefault(false)
            out += hasToken to (if (hasToken) "Этот телефон зарегистрирован на сервере" else "Этот телефон не зарегистрирован: выйдите и войдите снова")
            val ping = PushRelay.ping()
            out += ping.ok to ping.text
            if (ping.ok && hasToken) {
                val t = PushRelay.serverTest()
                out += t.ok to t.text
            }
            lines = out
            busy = false
        }
    }

    SCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Проверить уведомления", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Проверит разрешения, экономию батареи, регистрацию телефона и сервер, затем пришлёт тест.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )
        lines.forEach { (ok, text) ->
            Text(
                (if (ok) "✅ " else "❌ ") + text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { run() }) { Text(if (busy) "Проверяю…" else "Проверить") }
            TextButton(onClick = {
                Notifications.showMessage(ctx, "test", "blisschat", "Локальный тест: уведомления на этом телефоне работают")
            }) { Text("Тест на телефоне") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Настройки уведомлений") }
            TextButton(onClick = {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Батарея") }
        }
    }
}
