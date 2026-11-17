package com.bliss.blisschat.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.data.Accent
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.NameWithBadges
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.SupportBadge
import com.bliss.blisschat.ui.components.VerifiedBadge
import com.bliss.blisschat.ui.components.bounceClick
import com.bliss.blisschat.ui.theme.schemeFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onOpenAdmin: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = container.factory())
    val settings by vm.settings.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editName by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.updateAvatar(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- profile card
            me?.let { user ->
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(96.dp).bounceClick {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Avatar(user.avatarUrl, user.displayName.ifBlank { user.username }, size = 96.dp)
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.AddAPhoto, "Change photo", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        NameWithBadges(
                            user.displayName.ifBlank { user.username },
                            user.verified, user.support,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            badgeSize = 22.dp
                        )
                        Text(
                            "@${user.username}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        if (user.email.isNotBlank()) {
                            Text(
                                user.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                        if (user.verified || user.support || user.isAdmin) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (user.verified) { VerifiedBadge(20.dp); Text("Verified", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                                if (user.support) { SupportBadge(20.dp); Text("blisschat Support", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                                if (user.isAdmin) {
                                    Icon(Icons.Filled.AdminPanelSettings, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("Super Admin", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { editName = true }) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit name")
                        }
                    }
                }
            }

            // ---- admin entry (only tomatik_dev)
            if (me?.isAdmin == true) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().bounceClick(onClick = onOpenAdmin)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Admin Panel", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                "Badges and channel moderation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // ---- appearance
            SectionCard(title = "Appearance", icon = Icons.Filled.Palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (settings.dark) Icons.Filled.DarkMode else Icons.Filled.LightMode, null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dark theme", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (settings.dark) "Easy on the eyes" else "Light mode is on",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.dark,
                        onCheckedChange = { vm.setDark(it) },
                        thumbContent = {
                            Icon(
                                if (settings.dark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                null, Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text("Accent colour", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Accent.entries.forEach { accent ->
                        AccentDot(
                            accent = accent,
                            dark = settings.dark,
                            selected = settings.accent == accent,
                            onClick = { vm.setAccent(accent) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    settings.accent.label + if (settings.accent == Accent.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                        " (needs Android 12+, using turquoise)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- developer
            SectionCard(title = "Developer", icon = Icons.Filled.BugReport) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Debug login (mock OTP)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Shows the 6-digit code on screen instead of e-mailing it. Turn off once your Cloud Functions are deployed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = settings.mockAuth, onCheckedChange = { vm.setMockAuth(it) })
                }
            }

            BouncyButton(
                "Sign out",
                onClick = { confirmSignOut = true },
                tonal = true,
                icon = Icons.AutoMirrored.Filled.Logout,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "blisschat 1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
        }
    }

    if (editName) {
        var name by remember { mutableStateOf(me?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editName = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Display name") },
            text = { PillTextField(name, { name = it.take(40) }, "Your name") },
            confirmButton = {
                TextButton(onClick = { vm.updateName(name); editName = false }, enabled = name.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } }
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Sign out?") },
            text = { Text("You can sign back in any time with your email.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** A round swatch showing the accent's colour; springs when selected. */
@Composable
private fun AccentDot(accent: Accent, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val color = if (accent == Accent.DYNAMIC) MaterialTheme.colorScheme.tertiary
    else schemeFor(accent.hue, dark).primary
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "dot-scale"
    )
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        label = "dot-ring"
    )
    Box(
        Modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(BorderStroke(3.dp, ring), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, accent.label, tint = Color.Black.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
    }
}
