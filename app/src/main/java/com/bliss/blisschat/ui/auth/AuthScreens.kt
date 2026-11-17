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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.ui.components.Avatar
import com.bliss.blisschat.ui.components.BouncyButton
import com.bliss.blisschat.ui.components.OtpInput
import com.bliss.blisschat.ui.components.PillTextField
import com.bliss.blisschat.ui.components.bounceClick

/* =============================================================== e-mail / OTP flow */

@Composable
fun AuthFlow(container: AppContainer) {
    val vm: AuthViewModel = viewModel(factory = container.factory())
    val state by vm.ui.collectAsStateWithLifecycle()

    // Material Motion "Shared Axis X": forward slides left, backward slides right
    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { dir * it / 6 } +
                fadeIn(tween(220, delayMillis = 90))) togetherWith
                (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -dir * it / 6 } +
                    fadeOut(tween(90)))
        },
        label = "auth-step"
    ) { step ->
        when (step) {
            AuthViewModel.Step.EMAIL -> EmailScreen(state, vm)
            AuthViewModel.Step.OTP -> OtpScreen(state, vm)
        }
    }
}

@Composable
private fun LogoMark(size: Int = 88) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.34f).dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Forum, null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size((size * 0.5f).dp)
        )
    }
}

@Composable
private fun EmailScreen(state: AuthViewModel.UiState, vm: AuthViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(24.dp))
        LogoMark()
        Spacer(Modifier.height(24.dp))
        Text("blisschat", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in with your email. We'll send you a 6-digit code.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        PillTextField(
            value = state.email,
            onValueChange = vm::onEmail,
            placeholder = "you@example.com",
            leading = Icons.Filled.Email,
            keyboardType = KeyboardType.Email,
            isError = state.error != null
        )
        AnimatedVisibility(state.error != null) {
            Text(
                state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))
        BouncyButton(
            "Send code",
            onClick = vm::submitEmail,
            enabled = state.email.isNotBlank(),
            loading = state.loading,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.BugReport, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Debug login", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.mock) "Code is shown on screen, no mail server needed"
                        else "Code is e-mailed via Cloud Functions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.mock, onCheckedChange = vm::setMock)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OtpScreen(state: AuthViewModel.UiState, vm: AuthViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        IconButton(onClick = vm::back, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
        Spacer(Modifier.height(12.dp))
        Text("Enter the code", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "We sent a 6-digit code to ${state.email.trim()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        AnimatedVisibility(state.mock && state.debugCode != null) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .fillMaxWidth()
                    .bounceClick { state.debugCode?.let(vm::onCode) }
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BugReport, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Debug mode: your code is",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            state.debugCode.orEmpty().chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Text("Tap to fill", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        OtpInput(value = state.code, onValueChange = vm::onCode, isError = state.error != null)

        AnimatedVisibility(state.error != null) {
            Text(
                state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))
        BouncyButton(
            "Verify",
            onClick = { vm.verify() },
            enabled = state.code.length == 6,
            loading = state.loading,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = vm::resend,
            enabled = state.resendIn == 0 && !state.loading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (state.resendIn > 0) "Resend code in ${state.resendIn}s" else "Resend code")
        }
    }
}

/* =============================================================== profile setup */

@Composable
fun ProfileSetupScreen(container: AppContainer) {
    val vm: ProfileSetupViewModel = viewModel(factory = container.factory())
    val state by vm.ui.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        vm.onAvatar(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Set up your profile", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pick a name and a unique @username so people can find you.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        Box(
            Modifier
                .size(112.dp)
                .bounceClick {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.BottomEnd
        ) {
            if (state.avatar != null) {
                Avatar(state.avatar.toString(), state.displayName, size = 112.dp)
            } else {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person, null,
                        Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AddAPhoto, "Choose photo", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Text(
            "Optional photo",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(24.dp))
        PillTextField(
            value = state.displayName,
            onValueChange = vm::onName,
            placeholder = "Display name",
            leading = Icons.Filled.Person
        )
        Spacer(Modifier.height(14.dp))
        PillTextField(
            value = state.username,
            onValueChange = vm::onUsername,
            placeholder = "username",
            leading = Icons.Filled.AlternateEmail,
            isError = state.check == ProfileSetupViewModel.NameCheck.TAKEN ||
                state.check == ProfileSetupViewModel.NameCheck.INVALID,
            supporting = when (state.check) {
                ProfileSetupViewModel.NameCheck.IDLE -> "3-24 characters: letters, numbers and _"
                ProfileSetupViewModel.NameCheck.CHECKING -> "Checking availability..."
                ProfileSetupViewModel.NameCheck.FREE -> "@${state.username.lowercase()} is available"
                ProfileSetupViewModel.NameCheck.TAKEN -> "@${state.username.lowercase()} is already taken"
                ProfileSetupViewModel.NameCheck.INVALID -> "Use 3-24 letters, numbers or _"
            }
        )
        AnimatedVisibility(state.error != null) {
            Text(
                state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        BouncyButton(
            "Continue",
            onClick = vm::submit,
            enabled = state.canSubmit,
            loading = state.loading,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = vm::signOut) { Text("Use a different email") }
        Spacer(Modifier.height(24.dp))
    }
}

/* =============================================================== splash & error */

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(96)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
fun SessionErrorScreen(message: String, onSignOut: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Error, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load your account", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Check your connection and your Firestore rules (see README), then sign in again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        BouncyButton("Sign out", onClick = onSignOut, tonal = true)
    }
}
