package com.bliss.blisschat.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlissBackdrop
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.OutlinedPillButton
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.theme.AvatarShape
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.bliss.blisschat.R

@Composable
private fun LogoBlob(size: Int = 170) {
    // The app icon itself (adaptive-icon background + foreground) instead of the chat-bubble glyph.
    Box(Modifier.size(size.dp).clip(AvatarShape), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Image(painterResource(R.drawable.ic_launcher_foreground), "blisschat", Modifier.fillMaxSize().scale(1.35f), contentScale = ContentScale.Fit)
    }
}

/* =============================================================== login / register */

@Composable
fun AuthScreen(container: AppContainer) {
    val vm: AuthViewModel = viewModel(factory = container.factory())
    val s by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val register = s.mode == AuthViewModel.Mode.REGISTER

    Box(Modifier.fillMaxSize()) {
        BlissBackdrop(0)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            LogoBlob()
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = s.mode,
                transitionSpec = {
                    val dir = if (targetState == AuthViewModel.Mode.REGISTER) 1 else -1
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it / 8 } + fadeIn(tween(200, 80))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -dir * it / 8 } + fadeOut(tween(80)))
                },
                label = "auth-mode"
            ) { mode ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (mode == AuthViewModel.Mode.LOGIN) "Добро пожаловать" else "Создать аккаунт",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        if (mode == AuthViewModel.Mode.LOGIN) "Войдите, чтобы продолжить" else "Это займёт меньше минуты",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            PillTextField(
                value = s.identifier, onValueChange = vm::onIdentifier,
                placeholder = if (register) "введите email" else "введите username или email",
                leading = if (register) Icons.Filled.Email else Icons.Filled.Person,
                keyboardType = if (register) KeyboardType.Email else KeyboardType.Text
            )
            Spacer(Modifier.height(14.dp))
            PillTextField(
                value = s.password, onValueChange = vm::onPassword, placeholder = "введите пароль",
                leading = Icons.Filled.Lock, keyboardType = KeyboardType.Password,
                visualTransformation = if (s.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = vm::toggleShow) {
                        Icon(if (s.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Показать пароль")
                    }
                }
            )
            AnimatedVisibility(register) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    PillTextField(
                        value = s.confirm, onValueChange = vm::onConfirm, placeholder = "повторите пароль",
                        leading = Icons.Filled.Lock, keyboardType = KeyboardType.Password,
                        visualTransformation = if (s.showPassword) VisualTransformation.None else PasswordVisualTransformation()
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.remember, onCheckedChange = { vm.toggleRemember() })
                Text("Запомнить меня", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).clickable { vm.toggleRemember() })
                if (!register) {
                    Text(
                        "Забыли пароль?", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { vm.forgot() }.padding(8.dp)
                    )
                }
            }
            AnimatedVisibility(s.error != null || s.info != null) {
                Text(
                    s.error ?: s.info.orEmpty(),
                    color = if (s.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp).fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
            BouncyButton(
                if (register) "Зарегистрироваться" else "Войти", onClick = vm::submit,
                enabled = s.identifier.isNotBlank() && s.password.isNotEmpty(), loading = s.loading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text("  или  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(20.dp))
            OutlinedPillButton("Войти через Google", onClick = { vm.google(context) }, loading = s.googleLoading, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.bounceClick { vm.setMode(if (register) AuthViewModel.Mode.LOGIN else AuthViewModel.Mode.REGISTER) }.padding(8.dp)
            ) {
                Text(
                    if (register) "Уже есть аккаунт? " else "Нет аккаунта? ",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (register) "Войти" else "Регистрация",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* =============================================================== profile setup */

@Composable
fun ProfileSetupScreen(container: AppContainer) {
    val vm: ProfileSetupViewModel = viewModel(factory = container.factory())
    val s by vm.ui.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.onAvatar(it) }

    Box(Modifier.fillMaxSize()) {
        BlissBackdrop(2)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            Text("Ваш профиль", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Выберите имя и уникальный @username, по нему вас смогут найти.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            Box(
                Modifier.size(120.dp).bounceClick { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.BottomEnd
            ) {
                if (s.avatar != null) Avatar(s.avatar.toString(), s.displayName, size = 120.dp)
                else Box(
                    Modifier.size(120.dp).clip(AvatarShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Person, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(Modifier.size(38.dp).clip(AvatarShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AddAPhoto, "Выбрать фото", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Text("Фото (необязательно)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(22.dp))
            PillTextField(s.displayName, vm::onName, "Имя", leading = Icons.Filled.Person)
            Spacer(Modifier.height(14.dp))
            PillTextField(
                s.username, vm::onUsername, "username", leading = Icons.Filled.AlternateEmail,
                isError = s.check == ProfileSetupViewModel.NameCheck.TAKEN || s.check == ProfileSetupViewModel.NameCheck.INVALID,
                supporting = when (s.check) {
                    ProfileSetupViewModel.NameCheck.IDLE -> "3–24 символа: латиница, цифры и _"
                    ProfileSetupViewModel.NameCheck.CHECKING -> "Проверяем…"
                    ProfileSetupViewModel.NameCheck.FREE -> "@${s.username.lowercase()} свободен"
                    ProfileSetupViewModel.NameCheck.TAKEN -> "@${s.username.lowercase()} уже занят"
                    ProfileSetupViewModel.NameCheck.INVALID -> "Только латиница, цифры и _ (3–24)"
                }
            )
            AnimatedVisibility(s.error != null) {
                Text(s.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            BouncyButton("Продолжить", onClick = vm::submit, enabled = s.canSubmit, loading = s.loading, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = vm::signOut) { Text("Войти в другой аккаунт") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* =============================================================== splash & error */

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize()) {
        BlissBackdrop(0)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LogoBlob(150)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
fun SessionErrorScreen(message: String, onSignOut: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BlissBackdrop(2)
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Error, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("Не удалось загрузить аккаунт", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Проверьте интернет и правила Firestore (см. README), затем войдите снова.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            BouncyButton("Выйти", onClick = onSignOut, tonal = true)
        }
    }
}
