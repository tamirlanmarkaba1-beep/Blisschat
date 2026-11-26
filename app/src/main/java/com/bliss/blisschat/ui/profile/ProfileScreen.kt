package com.bliss.blisschat.ui.profile

import androidx.compose.material.icons.filled.Lock
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.Content
import com.bliss.blisschat.ui.chat.ImageCache
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BlobButton
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.OnlineDot
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.WavyDivider
import com.bliss.blisschat.ui.theme.scallopPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(container: AppContainer, userId: String, onBack: (() -> Unit)?, onOpenChat: (String) -> Unit) {
    val vm: ProfileViewModel = viewModel(key = "profile-$userId", factory = container.factory(userId))
    val s by vm.state.collectAsStateWithLifecycle()
    val isSelf = userId == vm.myId
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var tab by remember { mutableStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    var secretError by remember { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<Bitmap?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.changeAvatar(uri) }
    val user = s.user

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Banner with wavy blobs
            val scheme = MaterialTheme.colorScheme
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(scheme.surfaceContainerHigh)
                    drawPath(scallopPath(0f, size.height * 0.1f, size.width * 0.32f, size.width * 0.32f, 9, 0.05f), scheme.primaryContainer)
                    drawPath(scallopPath(size.width, size.height * 0.05f, size.width * 0.36f, size.width * 0.36f, 8, 0.05f), scheme.primary)
                }
                if (onBack != null) {
                    IconButton(onClick = onBack, Modifier.statusBarsPadding().padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
                Box(Modifier.align(Alignment.BottomCenter).offset(y = 75.dp)) {
                    Avatar(user?.avatarUrl.orEmpty(), user?.title ?: "?", size = 150.dp)
                }
            }
            Spacer(Modifier.height(86.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                LabeledBlob(
                    if (isSelf) Icons.Filled.Edit else Icons.Filled.Forum, if (isSelf) "изменить" else "написать",
                    onClick = { if (isSelf) edit = true else vm.openChat { r -> r.onSuccess(onOpenChat).onFailure { e -> /* shown below */ } } },
                    container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NameWithBadges(user?.title ?: "…", user?.verified == true, user?.support == true, style = MaterialTheme.typography.headlineSmall, badgeSize = 24.dp)
                    Text("@${user?.username.orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LabeledBlob(
                    if (isSelf) Icons.Filled.AddAPhoto else Icons.Filled.Report, if (isSelf) "фото" else "пожаловаться",
                    onClick = { if (isSelf) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else vm.report() },
                    container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val online = isSelf || user?.online() == true
                OnlineDot(online, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (online) "в сети" else "не в сети", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp).height(104.dp)) {
                    Text("описание", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(user?.bio?.ifBlank { if (isSelf) "Расскажите о себе — нажмите «изменить»" else "Описание не заполнено" }.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            WavyDivider(Modifier.padding(horizontal = 12.dp))

            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("фото", "видео", "сообщения").forEachIndexed { i, t ->
                            Surface(
                                onClick = { tab = i }, shape = CircleShape, modifier = Modifier.weight(1f),
                                color = if (tab == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            ) {
                                Text(t, Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    AnimatedContent(tab, transitionSpec = { fadeIn(tween(200, 60)) togetherWith fadeOut(tween(90)) }, label = "profile-tab") { t ->
                        when (t) {
                            0 -> PhotoGrid(s.photos, vm, isSelf) { viewer = it }
                            1 -> EmptyNote("Видео пока не поддерживаются")
                            else -> if (isSelf) EmptyNote("Ваши переписки — на вкладке «Чаты»")
                            else Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                BouncyButton("Открыть переписку", onClick = { vm.openChat { r -> r.onSuccess(onOpenChat) } })
                                BouncyButton(
                                    "Секретный чат", tonal = true, icon = Icons.Filled.Lock,
                                    onClick = { vm.openChat(secret = true) { r -> r.onSuccess(onOpenChat).onFailure { e -> secretError = e.message } } }
                                )
                                Text(
                                    secretError ?: "Секретный чат — отдельная переписка со сквозным шифрованием: читать её можете только вы двое.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (secretError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(130.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
    }

    viewer?.let { bmp ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { viewer = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { viewer = null }, Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Icon(Icons.Filled.Close, "Закрыть", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }

    if (edit && user != null) {
        var name by remember { mutableStateOf(user.displayName) }
        var bio by remember { mutableStateOf(user.bio) }
        AlertDialog(
            onDismissRequest = { edit = false }, shape = RoundedCornerShape(28.dp), title = { Text("Редактировать профиль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillTextField(name, { name = it.take(40) }, "Имя")
                    PillTextField(bio, { bio = it.take(200) }, "Описание", singleLine = false, maxLines = 4)
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveProfile(name, bio); edit = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun LabeledBlob(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BlobButton(icon, label, onClick, size = 62.dp, container = container, content = content)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PhotoGrid(photos: List<Content>, vm: ProfileViewModel, isSelf: Boolean, onOpen: (Bitmap) -> Unit) {
    if (photos.isEmpty()) {
        EmptyNote(if (isSelf) "Здесь появятся фото из ваших переписок" else "Общих фото пока нет")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.take(30).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c -> Thumb(c, vm, Modifier.weight(1f), onOpen) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Thumb(content: Content, vm: ProfileViewModel, modifier: Modifier, onOpen: (Bitmap) -> Unit) {
    var bmp by remember(content.path) { mutableStateOf(ImageCache.get(content.path)) }
    LaunchedEffect(content.path) {
        if (bmp == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { vm.repo.downloadMedia(content.path, content.key).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
            }
            if (loaded != null) { ImageCache.put(content.path, loaded); bmp = loaded }
        }
    }
    val b = bmp
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            .then(if (b != null) Modifier.clickableNoRipple { onOpen(b) } else Modifier)
    ) {
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(), indication = null, onClick = onClick)
)
