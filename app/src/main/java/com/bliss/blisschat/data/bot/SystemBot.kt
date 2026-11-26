package com.bliss.blisschat.data.bot

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BotMessage(val ts: Long, val text: String)

/**
 * The built-in @blisschat_bot. Its messages are generated on this phone (nothing is sent through the server),
 * and it greets you every time you sign in, with the exact time in Moscow time.
 */
class SystemBot(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("blisschat_system_bot", Context.MODE_PRIVATE)
    private val _messages = MutableStateFlow(load())
    val messages: StateFlow<List<BotMessage>> = _messages.asStateFlow()

    private fun load(): List<BotMessage> = runCatching {
        val arr = JSONArray(prefs.getString("messages", "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { BotMessage(it.getLong("ts"), it.getString("text")) } }
    }.getOrDefault(emptyList())

    private fun save(list: List<BotMessage>) {
        val arr = JSONArray()
        list.takeLast(60).forEach { arr.put(JSONObject().put("ts", it.ts).put("text", it.text)) }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    /** Called when the session becomes ready. [signInAt] changes only on a real sign-in, not on every app start. */
    fun onSignedIn(uid: String, name: String, signInAt: Long) {
        val key = "last_signin_$uid"
        if (signInAt <= prefs.getLong(key, 0L)) return
        prefs.edit().putLong(key, signInAt).apply()
        val now = ZonedDateTime.now(MSK)
        val text = "Добро пожаловать в blisschat, $name! 👋\n\n" +
            "Вход выполнен: ${now.format(FORMAT)} (МСК)\n\n" +
            "Ваши сообщения, фото и звонки шифруются на этом телефоне."
        val list = (_messages.value + BotMessage(now.toInstant().toEpochMilli(), text)).takeLast(60)
        _messages.value = list
        save(list)
    }

    companion object {
        val MSK: ZoneId = ZoneId.of("Europe/Moscow")
        private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm:ss", Locale("ru"))
    }
}
