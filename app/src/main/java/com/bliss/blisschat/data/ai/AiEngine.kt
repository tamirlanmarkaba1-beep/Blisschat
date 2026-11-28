package com.bliss.blisschat.data.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The AI that lives in the official group. It has no account: its messages are labelled with this id and name. */
const val OFFICIAL_AI_ID = "ai:gemini"
const val OFFICIAL_AI_NAME = "Gemini"

/**
 * Talks to Google Gemini. The model is called straight from the phone of the person who addressed the AI:
 *  - the official AI uses the key that the admin saved in Firestore (config/ai),
 *  - an AI created by a user uses that user's own API key, which never leaves his phone.
 * The last messages of the chat are given to the model, so it picks up the manner and slang of the people there.
 */
class AiEngine(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("blisschat_ai", Context.MODE_PRIVATE)
    private val db get() = FirebaseFirestore.getInstance()
    private var cached: Triple<String, String, Long>? = null

    /** API key of one of my AIs (kept only on this phone). */
    fun keyOf(aiUid: String): String = prefs.getString("key.$aiUid", "").orEmpty()
    fun setKey(aiUid: String, key: String) { prefs.edit().putString("key.$aiUid", key.trim()).apply() }
    fun forget(aiUid: String) { prefs.edit().remove("key.$aiUid").apply() }

    /** The official AI's key and model (admin writes them in the admin panel). */
    suspend fun official(): Pair<String, String> {
        val c = cached
        if (c != null && System.currentTimeMillis() - c.third < 300_000L) return c.first to c.second
        val doc = db.collection("config").document("ai").get().await()
        val key = doc.getString("key").orEmpty()
        val model = doc.getString("model").orEmpty().ifBlank { DEFAULT_MODEL }
        cached = Triple(key, model, System.currentTimeMillis())
        return key to model
    }

    suspend fun saveOfficial(key: String, model: String): Result<Unit> = runCatching {
        db.collection("config").document("ai").set(mapOf("key" to key.trim(), "model" to model.trim().ifBlank { DEFAULT_MODEL })).await()
        cached = null
    }

    suspend fun officialModel(): Pair<String, String> = runCatching { official() }.getOrDefault("" to DEFAULT_MODEL)

    /**
     * @param history last chat lines "Name: text" (oldest first)
     * @param persona extra instructions of the owner (character of a custom AI), may be empty
     */
    suspend fun reply(
        name: String, persona: String, key: String, model: String, history: List<String>, asker: String, question: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(key.isNotBlank()) { "Не задан API-ключ ИИ" }
            val system = buildString {
                append("Ты — ").append(name).append(", обычный участник группового чата друзей в мессенджере blisschat. ")
                append("Пиши как живой человек в переписке: коротко, чаще одной фразой, максимум два-три предложения. ")
                append("Никаких списков, заголовков, markdown и звёздочек, не представляйся и не говори, что ты ИИ или языковая модель. ")
                append("Ниже история чата: перенимай манеру людей оттуда — их тон, сленг, словечки, длину сообщений, смайлы. ")
                append("Если люди ругаются матом, шутят грубо или тебя задирают, отвечай в том же духе и можешь материться — к месту и не в каждом сообщении. ")
                append("Но без угроз, без травли и без оскорблений людей по национальности, расе, религии, ориентации или болезни. ")
                append("Если вопрос серьёзный или человек просит помощи, отвечай по делу и всё равно коротко. Язык — тот же, что у собеседника. ")
                if (persona.isNotBlank()) append("\nДополнительно о твоём характере: ").append(persona.trim().take(1500))
            }
            val prompt = buildString {
                if (history.isNotEmpty()) append("История чата:\n").append(history.takeLast(40).joinToString("\n")).append("\n\n")
                append(asker).append(" обращается к тебе: ").append(question.ifBlank { "привет" })
                append("\n\nНапиши только текст своего ответа одним сообщением.")
            }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("temperature", 1.1).put("maxOutputTokens", 400))
                .put("safetySettings", JSONArray().apply {
                    put(JSONObject().put("category", "HARM_CATEGORY_HARASSMENT").put("threshold", "BLOCK_NONE"))
                    put(JSONObject().put("category", "HARM_CATEGORY_HATE_SPEECH").put("threshold", "BLOCK_ONLY_HIGH"))
                    put(JSONObject().put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT").put("threshold", "BLOCK_ONLY_HIGH"))
                    put(JSONObject().put("category", "HARM_CATEGORY_DANGEROUS_CONTENT").put("threshold", "BLOCK_ONLY_HIGH"))
                })
            val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.ifBlank { DEFAULT_MODEL }}:generateContent")
                .openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 40_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", key.trim())
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault("")
                    error(when (code) {
                        400, 401, 403 -> "Gemini не принял ключ или запрос ($code). ${msg.take(120)}"
                        429 -> "Лимит запросов Gemini исчерпан, попробуйте позже"
                        else -> "Gemini ответил $code. ${msg.take(120)}"
                    })
                }
                val parts = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val out = StringBuilder()
                if (parts != null) for (i in 0 until parts.length()) out.append(parts.getJSONObject(i).optString("text"))
                val clean = out.toString().replace("**", "").replace("__", "").trim().take(600)
                if (clean.isEmpty()) error("ИИ не стал отвечать на это")
                clean
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"

        /**
         * If [text] starts with one of [triggers] (a word, case-insensitive, then a space or punctuation or the end), returns the
         * rest of the message without separators; otherwise null.
         */
        fun stripTrigger(text: String, triggers: List<String>): String? {
            val t = text.trim()
            val low = t.lowercase()
            for (tr in triggers.map { it.trim().lowercase() }.filter { it.isNotEmpty() }) {
                if (!low.startsWith(tr)) continue
                if (low.length > tr.length && low[tr.length].isLetterOrDigit() && tr.last().isLetterOrDigit()) continue
                return t.substring(tr.length).trimStart(',', ':', '.', '-', '—', '!', '?', ' ').trim()
            }
            return null
        }
    }
}
