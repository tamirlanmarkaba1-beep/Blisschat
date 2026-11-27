package com.bliss.blisschat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bliss.blisschat.data.call.CallPhase
import com.bliss.blisschat.data.push.AppState
import com.bliss.blisschat.data.push.Notifications
import com.bliss.blisschat.ui.AppRoot
import com.bliss.blisschat.ui.theme.BlissTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BlissApp).container
        Notifications.createChannels(this)
        handleIntent(intent, container)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        // "Online" indicator: ping while the app is visible (unless the user hides it in Settings)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val hide = container.settings.settings.first().hideOnline
                    if (!hide) container.currentUid?.let { container.repo.touchPresence(it) }
                    delay(30_000)
                }
            }
        }

        // An incoming call may appear over the lock screen; this is switched off again as soon as the call is over,
        // so chats never stay visible on a locked phone.
        lifecycleScope.launch {
            container.calls.ui.collect { ui ->
                val ringing = ui.phase != CallPhase.IDLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(ringing)
                    setTurnScreenOn(ringing)
                }
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            if (s == null) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B1110)))
            } else {
                val plugin by container.plugins.runtime.style.collectAsStateWithLifecycle()
                // Beta "liquid glass" theme from the settings; a plugin that sets its own glass wins.
                val look = if (s.glassTheme && !plugin.glass) plugin.copy(
                    glass = true, glassAlpha = 0.42f, glassBorder = true,
                    cornerScale = if (plugin.cornerScale == 1f) 1.25f else plugin.cornerScale,
                    bubbleAlpha = if (plugin.bubbleAlpha == 1f) 0.8f else plugin.bubbleAlpha
                ) else plugin
                BlissTheme(darkTheme = s.dark, hue = s.hue, dynamic = s.dynamicColors, plugin = look) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.bliss.blisschat.ui.glass.LocalGlassQuality provides com.bliss.blisschat.ui.glass.GlassQuality.of(s.glassQuality)
                    ) { AppRoot(container) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, (application as BlissApp).container)
    }

    /** Opened from a notification: show the call over the lock screen / answer it / open the chat. */
    private fun handleIntent(intent: Intent?, container: AppContainer) {
        if (intent == null) return
        container.auth.extractIdToken(intent.data)?.let { token ->
            intent.data = null
            lifecycleScope.launch {
                container.auth.signInWithWebIdToken(token)
                    .onFailure { android.widget.Toast.makeText(this@MainActivity, it.message ?: "Ошибка входа", android.widget.Toast.LENGTH_LONG).show() }
            }
        }
        if (intent.hasExtra(Notifications.EXTRA_CALL_ID)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            container.acceptWhenRinging = intent.getBooleanExtra(Notifications.EXTRA_ACCEPT, false)
        }
        intent.getStringExtra(Notifications.EXTRA_CHAT_ID)?.takeIf { it != "test" }?.let { container.pendingChat.value = it }
    }

    override fun onStart() { super.onStart(); AppState.foreground = true }
    override fun onStop() { super.onStop(); AppState.foreground = false }
}
