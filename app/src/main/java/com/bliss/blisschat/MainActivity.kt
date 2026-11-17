package com.bliss.blisschat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bliss.blisschat.ui.AppRoot
import com.bliss.blisschat.ui.theme.BlissTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BlissApp).container

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            if (s == null) {
                // DataStore not loaded yet: show the default dark background, avoids a theme flash
                Box(Modifier.fillMaxSize().background(Color(0xFF0B1110)))
            } else {
                BlissTheme(darkTheme = s.dark, accent = s.accent) {
                    AppRoot(container)
                }
            }
        }
    }
}
