package com.bliss.blisschat.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import com.bliss.blisschat.data.Chat
import com.bliss.blisschat.ui.components.Avatar
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.theme.ScallopShape

private enum class Kind(val title: String, val subtitle: String, val icon: ImageVector) {
    DIRECT("Личный чат", "Написать человеку по @username", Icons.Filled.Person),
    CHANNEL("Канал", "Пишете только вы, читают подписчики", Icons.Filled.Campaign),
    GROUP("Группа", "Общий чат, теги и звания участников", Icons.Filled.Group),
    BOT("Бот", "Автоматический помощник на Python", Icons.Filled.SmartToy),
    AI("ИИ", "Свой ИИ на Gemini по вашему API-ключу", Icons.Filled.AutoAwesome),
    FIND("Найти группу или канал", "Поиск по публичному @имени", Icons.Filled.Search)
}

/** Full-screen "what do you want to create" page opened from the "+" button. */
@Composable
fun CreateScreen(container: AppContainer, onClose: () -> Unit, onOpenChat: (String) -> Unit) {
    val vm: CreateViewModel = viewModel(factory = container.factory())
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = kind?.let { Kind.valueOf(it) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BlissBackdrop(2)
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (selected == null) onClose() else kind = null }) {
                    Icon(if (selected == null) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
                Text(selected?.title ?: "Создать", style = MaterialTheme.typography.headlineSmall)
            }
            AnimatedContent(
                targetState = selected, label = "create-step",
                transitionSpec = {
                    if (targetState != null) (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith (slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(160)))
                    else (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(280))) togetherWith (slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(160)))
                }
            ) { k ->
                if (k == null) Chooser { kind = it.name }
                else Form(k.name, vm, onOpenChat, onClose)
            }
        }
    }
}

@Composable
private fun Chooser(onPick: (Kind) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Что хотите создать?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        Kind.entries.forEachIndexed { i, k ->
            Surface(
                shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp, bottomEnd = if (i % 2 == 0) 14.dp else 38.dp, bottomStart = if (i % 2 == 0) 38.dp else 14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(38.dp)).bounceClick { onPick(k) }
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(ScallopShape(8, 0.07f)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(k.icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(k.title, style = MaterialTheme.typography.titleLarge)
                        Text(k.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun Form(kindName: String, vm: CreateViewModel, onOpenChat: (String) -> Unit, onClose: () -> Unit) {
    val kind = Kind.valueOf(kindName)
    var name by rememberSaveable { mutableStateOf("") }
    var avatarUrl by rememberSaveable { mutableStateOf("") }
    var members by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var botCode by rememberSaveable { mutableStateOf("") }
    var pubName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<Chat?>(null) }
    var foundMember by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickAvatar(uri) { r -> r.onSuccess { avatarUrl = it }.onFailure { error = it.message } }
    }
    var token by remember { mutableStateOf<String?>(null) }
    var aiKey by remember { mutableStateOf("") }
    var aiDone by remember { mutableStateOf<String?>(null) }

    val chatResult: (Result<String>) -> Unit = { r -> busy = false; r.onSuccess(onOpenChat).onFailure { error = it.message } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(kind.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (kind) {
            Kind.DIRECT -> PillTextField(username, { username = it; error = null }, "username собеседника", leading = Icons.Filled.AlternateEmail)
            Kind.CHANNEL, Kind.GROUP -> {
                PillTextField(name, { name = it.take(60); error = null }, if (kind == Kind.CHANNEL) "Название канала" else "Название группы", leading = Icons.Filled.Title)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(avatarUrl, name.ifBlank { "?" }, size = 64.dp)
                    Spacer(Modifier.width(14.dp))
                    TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Filled.Image, null); Spacer(Modifier.width(8.dp)); Text(if (avatarUrl.isEmpty()) "Выбрать аватарку" else "Сменить аватарку")
                    }
                }
                PillTextField(pubName, { pubName = it.trim().removePrefix("@").lowercase().take(32); error = null }, "Публичный @username (необязательно)", leading = Icons.Filled.AlternateEmail)
                Text(
                    "Если задать @username, группу смогут найти и вступить все. Имя уникально среди групп и каналов: двух одинаковых не будет. Пусто = закрытая, только по приглашению.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PillTextField(description, { description = it.take(255); error = null }, "Описание (необязательно)", leading = Icons.Filled.Info, singleLine = false, maxLines = 4, shape = RoundedCornerShape(24.dp))
                PillTextField(members, { members = it; error = null }, if (kind == Kind.CHANNEL) "Подписчики: @anna, @ivan" else "Участники: @anna, @ivan", leading = Icons.Filled.AlternateEmail)
                Text(
                    if (kind == Kind.CHANNEL) "В канале публикуете только вы. Подписчиков можно добавить позже. Все посты шифруются ключом канала."
                    else "У группы свой ключ шифрования, его получат только участники.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Kind.FIND -> {
                PillTextField(username, { username = it.trim().removePrefix("@").lowercase(); error = null; searched = false; found = null }, "@username группы или канала", leading = Icons.Filled.AlternateEmail)
                found?.let { ch ->
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(ch.avatarUrl, ch.name, size = 72.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(ch.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "@${ch.username} · " + (if (ch.type == com.bliss.blisschat.data.ChatType.CHANNEL) "канал" else "группа") + " · ${ch.members.size} уч.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (ch.description.isNotBlank()) Text(ch.description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                if (searched && found == null) Text("Ничего не найдено. Проверьте @username: искать можно только публичные группы и каналы.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Kind.AI -> {
                PillTextField(name, { name = it.take(40); error = null }, "Имя ИИ (так к нему обращаются)", leading = Icons.Filled.AutoAwesome)
                PillTextField(username, { username = it.trim(); error = null }, "username (оканчивается на _ai)", leading = Icons.Filled.AlternateEmail)
                PillTextField(
                    aiKey, { aiKey = it.trim(); error = null }, "API-ключ Gemini", leading = Icons.Filled.Key,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                PillTextField(
                    description, { description = it.take(1500); error = null }, "Характер ИИ (необязательно): как говорит, кто он",
                    singleLine = false, maxLines = 6, shape = RoundedCornerShape(24.dp)
                )
                Text(
                    "Ключ бесплатно выдаёт Google AI Studio (aistudio.google.com/apikey). Он хранится только на этом телефоне, в базу не попадает. " +
                        "ИИ отвечает с вашего телефона, пока приложение запущено: ему можно написать в личку (найдите его по @username) " +
                        "или позвать в любом чате по имени, например «имя, привет».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Kind.BOT -> {
                PillTextField(name, { name = it.take(40); error = null }, "Имя бота", leading = Icons.Filled.SmartToy)
                PillTextField(username, { username = it.trim(); error = null }, "username (оканчивается на _bot)", leading = Icons.Filled.AlternateEmail)
                PillTextField(
                    botCode, { botCode = it.take(10_000); error = null }, "Python-код бота: напишите или вставьте",
                    singleLine = false, maxLines = 12, shape = RoundedCornerShape(24.dp)
                )
                Text(
                    "Код можно изменить позже: Настройки → «Боты и ИИ».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Бот работает на вашем сервере (Python, папка bots в проекте). После создания вы получите токен. Он показывается один раз.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.height(4.dp))
        BouncyButton(
            when (kind) {
                Kind.DIRECT -> "Начать чат"; Kind.CHANNEL -> "Создать канал"; Kind.GROUP -> "Создать группу"; Kind.BOT -> "Создать бота"; Kind.AI -> "Создать ИИ"
                Kind.FIND -> if (found == null) "Найти" else if (foundMember) "Открыть" else "Вступить"
            },
            loading = busy, modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true; error = null
                when (kind) {
                    Kind.FIND -> {
                        val f = found
                        if (f == null) vm.find(username) { r ->
                            busy = false; searched = true
                            r.onSuccess { (chat, member) -> found = chat; foundMember = member }.onFailure { error = it.message }
                        } else if (foundMember) { busy = false; onOpenChat(f.id) }
                        else vm.join(f) { r -> busy = false; r.onSuccess { onOpenChat(f.id) }.onFailure { error = it.message } }
                    }
                    Kind.DIRECT -> vm.startDirect(username, chatResult)
                    Kind.CHANNEL -> vm.createGroup(name, members, true, avatarUrl, pubName, description, chatResult)
                    Kind.GROUP -> vm.createGroup(name, members, false, avatarUrl, pubName, description, chatResult)
                    Kind.AI -> vm.createAi(name, username, aiKey, description) { r -> busy = false; r.onSuccess { aiDone = username.trim().removePrefix("@").lowercase() }.onFailure { error = it.message } }
                    Kind.BOT -> vm.createBot(name, username, botCode) { r -> busy = false; r.onSuccess { token = it }.onFailure { error = it.message } }
                }
            }
        )
        Spacer(Modifier.height(100.dp))
    }

    aiDone?.let { u ->
        AlertDialog(
            onDismissRequest = {}, shape = RoundedCornerShape(30.dp),
            icon = { Icon(Icons.Filled.AutoAwesome, null) },
            title = { Text("ИИ создан") },
            text = { Text("@$u готов. Найдите его в поиске и напишите. В группах зовите по имени: «имя, привет». Ключ можно сменить: Настройки → Мои боты и ИИ.") },
            confirmButton = { TextButton(onClick = { aiDone = null; onClose() }) { Text("Готово") } }
        )
    }

    token?.let { t ->
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = {}, shape = RoundedCornerShape(30.dp),
            icon = { Icon(Icons.Filled.SmartToy, null) },
            title = { Text("Бот создан") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Токен бота. Сохраните его сейчас: повторно показать его нельзя.", style = MaterialTheme.typography.bodyMedium)
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(t, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("bot token", t))
                    }) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Скопировать") }
                    Text("Запуск: python example_bot.py (см. README, раздел «Боты»).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { token = null; onClose() }) { Text("Готово") } }
        )
    }
}
