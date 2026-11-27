package com.bliss.blisschat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.UserProfile
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.SplitSearchBar
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.theme.ButtonBlob

/** One entry of the main settings list. [route] is a navigation route. */
private data class SettingsItem(
    val group: String, val route: String, val icon: ImageVector, val title: String, val subtitle: String,
    val tone: Int, val keywords: String = ""
)

private fun settingsItems(user: UserProfile?): List<SettingsItem> = buildList {
    add(SettingsItem("основное", "settings/appearance", Icons.Filled.Palette, "Внешний вид", "Тема, цвет, размер текста", 0, "тёмная светлая динамические обои цвет шрифт оформление"))
    add(SettingsItem("основное", "settings/notifications", Icons.Filled.Notifications, "Уведомления", "Сообщения, звонки, без звука", 1, "звук push пуш звонок полный экран"))
    add(SettingsItem("основное", "settings/privacy", Icons.Filled.Lock, "Приватность и данные", "Статус «в сети», фото, кэш", 2, "онлайн кэш запомнить автозагрузка"))
    add(SettingsItem("основное", "settings/security", Icons.Filled.Shield, "Секретные чаты и ключи", "Резервная копия ключей", 1, "ключ копия восстановить пароль сообщения пропали"))
    add(SettingsItem("дополнения", "settings/plugins", Icons.Filled.Extension, "Плагины", "Настройки плагинов, каталог, свой код", 2, "тема код стиль скрипт javascript"))
    add(SettingsItem("дополнения", "stickers", Icons.Filled.SentimentSatisfied, "Стикеры", "Свои наборы стикеров", 0, "эмодзи паки"))
    add(SettingsItem("дополнения", "settings/bots", Icons.Filled.SmartToy, "Боты (Python)", "Ваши боты и их код", 1, "бот python скрипт"))
    add(SettingsItem("дополнения", "settings/guide", Icons.Filled.MenuBook, "Руководство", "Как делать ботов и плагины", 0, "бот плагин botkeep инструкция помощь api"))
    if (user?.moderator == true || user?.isAdmin == true)
        add(SettingsItem("дополнения", "moderation", Icons.Filled.Verified, "Модерация плагинов", "Заявки на проверку", 2))
    if (user?.isAdmin == true)
        add(SettingsItem("дополнения", "admin", Icons.Filled.AdminPanelSettings, "Панель администратора", "Значки, группы, модераторы", 0))
    add(SettingsItem("приложение", "settings/updates", Icons.Filled.SystemUpdate, "Центр обновлений", "Версия и проверка обновлений", 1, "апдейт версия скачать"))
}

@Composable
fun SettingsScreen(container: AppContainer, onNavigate: (String) -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val user by vm.user.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmOut by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val tones = listOf(scheme.surfaceContainerHighest, scheme.primaryContainer, scheme.tertiaryContainer)

    val all = settingsItems(user)
    val needle = query.trim().lowercase()
    val shown = if (needle.isEmpty()) all else all.filter {
        it.title.lowercase().contains(needle) || it.subtitle.lowercase().contains(needle) || it.keywords.contains(needle)
    }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        if (com.bliss.blisschat.BuildConfig.GLASS) com.bliss.blisschat.ui.components.BlissBackdrop(2)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Spacer(Modifier.height(12.dp))
            SplitSearchBar(query, { query = it }, "найти настройки тут…", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(12.dp))

            if (needle.isEmpty()) {
                val saved by vm.accounts.collectAsStateWithLifecycle()
                val switchError by vm.switchError.collectAsStateWithLifecycle()
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // the signed-in account first, then the other saved ones
                    item(key = "me") {
                        AccountTile(user?.avatarUrl.orEmpty(), user?.title.orEmpty().ifBlank { "аккаунт" }, null, selected = true) { onNavigate("profile") }
                    }
                    items(saved.filter { it.uid != user?.uid }, key = { it.uid }) { acc ->
                        AccountTile(acc.avatarUrl, acc.title.ifBlank { "аккаунт" }, null) { vm.switchTo(acc) }
                    }
                    item(key = "add") { AccountTile("", "добавить", Icons.Filled.Add) { addOpen = true } }
                    item(key = "out") { AccountTile("", "выйти", Icons.AutoMirrored.Filled.ExitToApp) { confirmOut = true } }
                }
                val needPw by vm.needPassword.collectAsStateWithLifecycle()
                needPw?.let { acc ->
                    var pw by remember(acc.uid) { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = vm::cancelPassword, shape = RoundedCornerShape(28.dp),
                        title = { Text("Пароль для «${acc.title}»") },
                        text = {
                            com.bliss.blisschat.ui.components.PillTextField(
                                pw, { pw = it }, "пароль",
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )
                        },
                        confirmButton = { TextButton(onClick = { vm.switchTo(acc, pw) }, enabled = pw.isNotEmpty()) { Text("Войти") } },
                        dismissButton = { TextButton(onClick = vm::cancelPassword) { Text("Отмена") } }
                    )
                }
                if (switchError != null) AlertDialog(
                    onDismissRequest = vm::clearSwitchError, shape = RoundedCornerShape(28.dp),
                    text = { Text(switchError.orEmpty()) },
                    confirmButton = { TextButton(onClick = vm::clearSwitchError) { Text("Понятно") } }
                )
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needle.isNotEmpty()) {
                    GroupLabel(if (shown.isEmpty()) "ничего не найдено" else "найдено")
                    shown.forEach { SectionRow(it.icon, it.title, it.subtitle, tones[it.tone % tones.size]) { onNavigate(it.route) } }
                } else {
                    shown.groupBy { it.group }.forEach { (group, list) ->
                        GroupLabel(group)
                        list.forEach { SectionRow(it.icon, it.title, it.subtitle, tones[it.tone % tones.size]) { onNavigate(it.route) } }
                    }
                }
            }
            Spacer(Modifier.height(130.dp))
        }
    }

    if (addOpen) AlertDialog(
        onDismissRequest = { addOpen = false }, shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Filled.Add, null) },
        title = { Text("Добавить аккаунт") },
        text = { Text("Откроется экран входа. Текущий аккаунт останется в списке, и между аккаунтами можно будет переключаться здесь же.") },
        confirmButton = { TextButton(onClick = { addOpen = false; vm.addAccount() }) { Text("Войти в другой") } },
        dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Отмена") } }
    )

    if (confirmOut) AlertDialog(
        onDismissRequest = { confirmOut = false }, shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
        title = { Text("Выйти из аккаунта?") },
        text = { Text("Переписка сохранится в вашем аккаунте. Чтобы увидеть её снова, войдите в тот же аккаунт.") },
        confirmButton = { TextButton(onClick = { confirmOut = false; vm.signOut() }) { Text("Выйти", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmOut = false }) { Text("Отмена") } }
    )
}

/** Tile of the account carousel: blob avatar over a short caption. */
@Composable
private fun AccountTile(avatarUrl: String, name: String, icon: ImageVector?, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(30.dp), color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(112.dp).clip(RoundedCornerShape(30.dp)).bounceClick(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Box(Modifier.size(72.dp).clip(ButtonBlob).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            } else Avatar(avatarUrl, name, size = 72.dp)
            Spacer(Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
