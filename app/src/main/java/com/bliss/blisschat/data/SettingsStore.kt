package com.bliss.blisschat.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bliss.blisschat.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "blisschat_prefs")

/** Accent colours the user can pick. [hue] drives the generated Material 3 palette. */
enum class Accent(val label: String, val hue: Float) {
    TEAL("Turquoise", 174f),
    EMERALD("Emerald", 148f),
    VIOLET("Violet", 268f),
    BLUE("Blue", 214f),
    DYNAMIC("System", 0f)
}

data class AppSettings(
    val dark: Boolean = true,
    val accent: Accent = Accent.TEAL,
    val mockAuth: Boolean = BuildConfig.MOCK_AUTH,
    val sessionUid: String? = null,
    val sessionEmail: String? = null
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val DARK = booleanPreferencesKey("dark")
        val ACCENT = stringPreferencesKey("accent")
        val MOCK = booleanPreferencesKey("mock_auth")
        val UID = stringPreferencesKey("session_uid")
        val EMAIL = stringPreferencesKey("session_email")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p: Preferences ->
        AppSettings(
            dark = p[Keys.DARK] ?: true,
            accent = runCatching { Accent.valueOf(p[Keys.ACCENT] ?: "TEAL") }.getOrDefault(Accent.TEAL),
            mockAuth = p[Keys.MOCK] ?: BuildConfig.MOCK_AUTH,
            sessionUid = p[Keys.UID],
            sessionEmail = p[Keys.EMAIL]
        )
    }

    suspend fun setDark(value: Boolean) = context.dataStore.edit { it[Keys.DARK] = value }
    suspend fun setAccent(value: Accent) = context.dataStore.edit { it[Keys.ACCENT] = value.name }
    suspend fun setMockAuth(value: Boolean) = context.dataStore.edit { it[Keys.MOCK] = value }

    suspend fun setSession(uid: String, email: String) = context.dataStore.edit {
        it[Keys.UID] = uid
        it[Keys.EMAIL] = email
    }

    suspend fun clearSession() = context.dataStore.edit {
        it.remove(Keys.UID)
        it.remove(Keys.EMAIL)
    }
}
