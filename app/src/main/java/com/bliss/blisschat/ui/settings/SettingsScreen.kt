package com.bliss.blisschat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.OutlinedPillButton

private fun hueColor(h: Float) = Color.hsv(h.coerceIn(0f, 359.9f), 0.78f, 0.95f)
private val Rainbow = Brush.horizontalGradient((0..12).map { hueColor(it * 30f) })

@Composable
fun SettingsScreen(container: AppContainer, onOpenAdmin: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val s by vm.settings.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        BlissBackdrop(1)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("настройки", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(16.dp))

            Card {
                SwitchRow(Icons.Filled.DarkMode, "Тёмная тема", s.dark) { vm.setDark(it) }
                Spacer(Modifier.height(4.dp))
                SwitchRow(Icons.Filled.Lock, "Запомнить меня", s.rememberMe) { vm.setRememberMe(it) }
            }
            Spacer(Modifier.height(14.dp))

            Card {
                Text("Цвет приложения", style = MaterialTheme.typography.titleMedium)
                Text("Меняет акценты и свечение фона", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(hueColor(s.hue)))
                    Spacer(Modifier.width(14.dp))
                    Slider(
                        value = s.hue, onValueChange = { vm.setHue(it) }, valueRange = 0f..360f,
                        modifier = Modifier.weight(1f),
                        track = {
                            Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(Rainbow))
                        },
                        thumb = {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White).padding(5.dp).clip(CircleShape).background(hueColor(s.hue)))
                        }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Сквозное шифрование", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Сообщения, фото и звонки шифруются на устройстве. Ключ хранится только здесь — сервер прочитать их не может.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text("Отпечаток ключа", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user?.kid?.ifBlank { "создаётся…" }.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))

            if (user?.isAdmin == true) {
                BouncyButton("Панель администратора", onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.AdminPanelSettings)
                Spacer(Modifier.height(10.dp))
            }
            OutlinedPillButton("Выйти", onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(130.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked, onChange)
    }
}
