package com.bliss.blisschat.data.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
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
            val primaryModel = cleanModel(model)
            val modelsToTry = listOf(primaryModel) + FALLBACK_MODELS.filter { it != primaryModel }
            var last503: GeminiHttpException? = null
            var lastUnavailable: GeminiHttpException? = null

            for (candidateModel in modelsToTry) {
                // Initial request + three retries: 1s, 2s, 4s.
                for (attempt in 0..3) {
                    try {
                        return@runCatching requestGemini(candidateModel, key, body)
                    } catch (e: GeminiHttpException) {
                        when (e.statusCode) {
                            503 -> {
                                last503 = e
                                if (attempt < 3) delay(RETRY_DELAYS_MS[attempt])
                            }
                            404 -> {
                                lastUnavailable = e
                                break // This model is not available; try the next one.
                            }
                            else -> throw e
                        }
                    }
                }
            }
            if (last503 != null) {
                error("Gemini временно перегружен (503). Не удалось получить ответ ни от основной, ни от резервных моделей. ${last503.message.orEmpty()}")
            }
            error("Основная и резервные модели Gemini недоступны. ${lastUnavailable?.message.orEmpty()}")

        }
    }



    /**
     * Sends one request using a normalized model id. Both official and personal AIs
     * use this same path through [reply].
     */
    private fun requestGemini(model: String, key: String, body: JSONObject): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 40_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", key.trim().replace(Regex("\\s+"), ""))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val responseText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).getJSONObject("error").getString("message")
                }.getOrDefault(responseText.take(160))
                val userMessage = when (code) {
                    400, 401, 403 -> "Gemini не принял ключ или запрос ($code). ${message.take(120)}"
                    429 -> "Лимит запросов Gemini исчерпан, попробуйте позже"
                    503 -> "Gemini временно перегружен (503). ${message.take(120)}"
                    else -> "Gemini ответил $code. ${message.take(120)}"
                }
                throw GeminiHttpException(code, userMessage)
            }

            val parts = JSONObject(responseText).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val output = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    output.append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }
            val clean = output.toString().replace("**", "").replace("__", "").trim().take(600)
            if (clean.isEmpty()) error("ИИ не стал отвечать на это")
            return clean
        } finally {
            conn.disconnect()
        }
    }

    /** Removes whitespace and any repeated leading "models/" prefix. */
    private fun cleanModel(rawModel: String): String {
        var value = rawModel.trim().replace(Regex("\\s+"), "")
        while (value.startsWith("models/", ignoreCase = true)) {
            value = value.substring("models/".length)
        }
        return value.ifBlank { DEFAULT_MODEL }
    }

    private class GeminiHttpException(
        val statusCode: Int,
        message: String
    ) : Exception(message)

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"
        private val FALLBACK_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite")
        private val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L)

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
