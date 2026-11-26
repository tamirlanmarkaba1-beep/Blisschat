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
    val hidden: Set<String> = emptySet(),
    /** Notifications */
    val notifMessages: Boolean = true,
    val notifCalls: Boolean = true,
    val notifFullScreenCalls: Boolean = true,
    /** Chats ("c:{chatId}") and people ("u:{uid}") that never notify. */
    val muted: Set<String> = emptySet(),
    /** Look & behaviour */
    val textScale: Float = 1f,
    val autoLoadPhotos: Boolean = true,
    val hideOnline: Boolean = false,
    /** Material You colours taken from the wallpaper (Android 12+). */
    val dynamicColors: Boolean = false,
    /** BETA theme "liquid glass": translucent surfaces over a soft animated colour backdrop. */
    val glassTheme: Boolean = com.bliss.blisschat.BuildConfig.GLASS,
    /** Strength of the real glass effect in the glass app: LOW, HIGH, ULTRA, EXTREME. */
    val glassQuality: String = "HIGH"
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val DARK = booleanPreferencesKey("dark")
        val HUE = floatPreferencesKey("hue")
        val REMEMBER = booleanPreferencesKey("remember_me")
        val HIDDEN = stringSetPreferencesKey("hidden_messages")
        val N_MSG = booleanPreferencesKey("notif_messages")
        val N_CALL = booleanPreferencesKey("notif_calls")
        val N_FULL = booleanPreferencesKey("notif_fullscreen")
        val MUTED = stringSetPreferencesKey("muted")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val AUTO_PHOTOS = booleanPreferencesKey("auto_photos")
        val HIDE_ONLINE = booleanPreferencesKey("hide_online")
        val DYNAMIC = booleanPreferencesKey("dynamic_colors")
        val GLASS = booleanPreferencesKey("glass_theme")
        val GLASS_Q = androidx.datastore.preferences.core.stringPreferencesKey("glass_quality")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p: Preferences ->
        AppSettings(
            dark = p[Keys.DARK] ?: true,
            hue = p[Keys.HUE] ?: DEFAULT_HUE,
            rememberMe = p[Keys.REMEMBER] ?: true,
            hidden = p[Keys.HIDDEN] ?: emptySet(),
            notifMessages = p[Keys.N_MSG] ?: true,
            notifCalls = p[Keys.N_CALL] ?: true,
            notifFullScreenCalls = p[Keys.N_FULL] ?: true,
            muted = p[Keys.MUTED] ?: emptySet(),
            textScale = p[Keys.TEXT_SCALE] ?: 1f,
            autoLoadPhotos = p[Keys.AUTO_PHOTOS] ?: true,
            hideOnline = p[Keys.HIDE_ONLINE] ?: false,
            dynamicColors = p[Keys.DYNAMIC] ?: false,
            glassTheme = p[Keys.GLASS] ?: com.bliss.blisschat.BuildConfig.GLASS,
            glassQuality = p[Keys.GLASS_Q] ?: "HIGH"
        )
    }

    suspend fun setDark(value: Boolean) = context.dataStore.edit { it[Keys.DARK] = value }
    /** hue -1 = the colourless "black" palette */
    suspend fun setHue(value: Float) = context.dataStore.edit { it[Keys.HUE] = if (value < 0f) -1f else value.coerceIn(0f, 360f) }
    suspend fun setDynamicColors(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = v }
    suspend fun setGlassTheme(v: Boolean) = context.dataStore.edit { it[Keys.GLASS] = v }
    suspend fun setGlassQuality(v: String) = context.dataStore.edit { it[Keys.GLASS_Q] = v }
    suspend fun setRememberMe(value: Boolean) = context.dataStore.edit { it[Keys.REMEMBER] = value }
    suspend fun hide(chatId: String, messageId: String) =
        context.dataStore.edit { it[Keys.HIDDEN] = (it[Keys.HIDDEN] ?: emptySet()) + "$chatId/$messageId" }

    suspend fun setNotifMessages(v: Boolean) = context.dataStore.edit { it[Keys.N_MSG] = v }
    suspend fun setNotifCalls(v: Boolean) = context.dataStore.edit { it[Keys.N_CALL] = v }
    suspend fun setNotifFullScreen(v: Boolean) = context.dataStore.edit { it[Keys.N_FULL] = v }
    suspend fun setTextScale(v: Float) = context.dataStore.edit { it[Keys.TEXT_SCALE] = v.coerceIn(0.8f, 1.4f) }
    suspend fun setAutoLoadPhotos(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_PHOTOS] = v }
    suspend fun setHideOnline(v: Boolean) = context.dataStore.edit { it[Keys.HIDE_ONLINE] = v }
    /** @param key "c:{chatId}" or "u:{uid}" */
    suspend fun setMuted(key: String, muted: Boolean) = context.dataStore.edit {
        val cur = it[Keys.MUTED] ?: emptySet()
        it[Keys.MUTED] = if (muted) cur + key else cur - key
    }
}
