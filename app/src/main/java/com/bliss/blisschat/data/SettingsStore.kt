package com.bliss.blisschat.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "blisschat_prefs")

const val DEFAULT_HUE = 174f // turquoise

data class AppSettings(
    val dark: Boolean = true,
    /** Accent colour as a hue on the full colour wheel (0..360). Drives the whole palette and background glow. */
    val hue: Float = DEFAULT_HUE,
    val rememberMe: Boolean = true,
    /** "chatId/messageId" pairs the user deleted only on this device. */
    val hidden: Set<String> = emptySet()
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val DARK = booleanPreferencesKey("dark")
        val HUE = floatPreferencesKey("hue")
        val REMEMBER = booleanPreferencesKey("remember_me")
        val HIDDEN = stringSetPreferencesKey("hidden_messages")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p: Preferences ->
        AppSettings(
            dark = p[Keys.DARK] ?: true,
            hue = p[Keys.HUE] ?: DEFAULT_HUE,
            rememberMe = p[Keys.REMEMBER] ?: true,
            hidden = p[Keys.HIDDEN] ?: emptySet()
        )
    }

    suspend fun setDark(value: Boolean) = context.dataStore.edit { it[Keys.DARK] = value }
    suspend fun setHue(value: Float) = context.dataStore.edit { it[Keys.HUE] = value.coerceIn(0f, 360f) }
    suspend fun setRememberMe(value: Boolean) = context.dataStore.edit { it[Keys.REMEMBER] = value }
    suspend fun hide(chatId: String, messageId: String) =
        context.dataStore.edit { it[Keys.HIDDEN] = (it[Keys.HIDDEN] ?: emptySet()) + "$chatId/$messageId" }
}
