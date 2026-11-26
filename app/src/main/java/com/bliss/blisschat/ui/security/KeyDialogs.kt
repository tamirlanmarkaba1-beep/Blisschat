package com.bliss.blisschat.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.crypto.RestoreResult
import com.bliss.blisschat.ui.components.PillTextField
import kotlinx.coroutines.launch

/**
 * Shown (and not dismissable) when this phone has no encryption keys but the account has an encrypted backup:
 * the user types the backup password (account password or recovery code) to get the old messages back.
 */
@Composable
fun RestoreKeysDialog(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmFresh by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text("Восстановить переписку") },
        text = {
            Column {
                Text(
                    "Ваши сообщения зашифрованы, а ключи на этом телефоне ещё не восстановлены. Введите пароль аккаунта " +
                        "(или код восстановления, если вы его задавали) — и вся переписка снова откроется.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                PillTextField(
                    secret, { secret = it; error = null }, "Пароль или код восстановления",
                    keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation(),
                    isError = error != null, supporting = error
                )
            }
        },
        confirmButton = {
            TextButton(enabled = secret.isNotEmpty() && !busy, onClick = {
                busy = true
                scope.launch {
                    val r = runCatching { container.restoreKeys(secret) }.getOrNull()
                    busy = false
                    when (r) {
                        RestoreResult.RESTORED -> Unit
                        RestoreResult.WRONG_SECRET -> error = "Неверный пароль"
                        else -> error = "Не удалось восстановить. Проверьте интернет и правила Firestore."
                    }
                }
            }) { Text(if (busy) "Проверяем…" else "Восстановить") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmFresh = true }) { Text("Начать заново") } }
    )

    if (confirmFresh) AlertDialog(
        onDismissRequest = { confirmFresh = false },
        shape = RoundedCornerShape(28.dp),
        title = { Text("Начать заново?") },
        text = { Text("На этом телефоне будут созданы новые ключи. Старые сообщения прочитать будет нельзя — только новые.") },
        confirmButton = {
            TextButton(onClick = { confirmFresh = false; scope.launch { runCatching { container.startFreshKeys() } } }) {
                Text("Да, начать заново", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmFresh = false }) { Text("Отмена") } }
    )
}

/**
 * A dialog with one secret field. [onConfirm] gets (secret, custom) and returns an error text, or null when everything went fine.
 * [offerCustom] adds the switch "separate recovery code".
 */
@Composable
fun SecretDialog(
    title: String, message: String, confirmLabel: String, offerCustom: Boolean,
    onDismiss: () -> Unit, onConfirm: suspend (String, Boolean) -> String?
) {
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PillTextField(
                    secret, { secret = it; error = null }, if (offerCustom && custom) "Код восстановления (от 6 символов)" else "Пароль",
                    keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation(),
                    isError = error != null, supporting = error
                )
                if (offerCustom) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Отдельный код вместо пароля аккаунта", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Switch(custom, { custom = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = secret.length >= 6 && !busy, onClick = {
                busy = true
                scope.launch {
                    val e = runCatching { onConfirm(secret, custom) }.getOrElse { it.message ?: "Ошибка" }
                    busy = false
                    if (e == null) onDismiss() else error = e
                }
            }) { Text(if (busy) "Подождите…" else confirmLabel) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } }
    )
}
