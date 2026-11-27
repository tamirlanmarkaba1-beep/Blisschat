package com.bliss.blisschat.data.push

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tells the Cloudflare Worker in /cloudflare-worker that a message was sent / a call was started, so it can send
 * the FCM push. This replaces Firebase Cloud Functions (which need the paid Blaze plan).
 *
 * Fire-and-forget: a failure here never affects sending the message.
 */
object PushRelay {
    /** Address of the deployed Cloudflare Worker (see /cloudflare-worker). Empty = pushes off. */
    const val WORKER_URL = "https://blisschat-push.blisschat-relay.workers.dev"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun notifyMessage(chatId: String, msgId: String) =
        post("/message", JSONObject().put("chatId", chatId).put("msgId", msgId))

    fun notifyCall(callId: String) =
        post("/call", JSONObject().put("callId", callId))

    /** Result of a diagnostic request to the worker. */
    data class Check(val ok: Boolean, val text: String)

    /** Is the worker deployed and reachable at all? */
    suspend fun ping(): Check = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(WORKER_URL.trimEnd('/')).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8_000; c.readTimeout = 8_000
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                if (code == 200 && text.contains("push relay")) Check(true, "Сервер уведомлений работает")
                else Check(false, "Сервер ответил странно (код $code). Он не развёрнут или адрес в приложении неверный")
            } finally { c.disconnect() }
        }.getOrElse { Check(false, "Сервер недоступен: ${it.message}. Скорее всего он не развёрнут (Cloudflare Worker)") }
    }

    /** Asks the worker to push a test message to MY OWN phones. */
    suspend fun serverTest(): Check = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token
                ?: return@runCatching Check(false, "Вы не вошли в аккаунт")
            val c = URL(WORKER_URL.trimEnd('/') + "/test").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.connectTimeout = 10_000; c.readTimeout = 15_000; c.doOutput = true
                c.setRequestProperty("Authorization", "Bearer $token")
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write("{}".toByteArray()) }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                when {
                    code == 404 -> Check(false, "На сервере старая версия: обновите Worker (workflow «Deploy push worker»)")
                    code == 500 -> Check(false, "Ошибка сервера: скорее всего не задан секрет SERVICE_ACCOUNT_JSON")
                    code == 200 && text.contains("\"ok\":true") -> Check(true, "Сервер отправил тест, сейчас придёт уведомление")
                    code == 200 -> Check(false, "У вашего аккаунта нет токена уведомлений (выйдите и войдите снова)")
                    else -> Check(false, "Ошибка $code: ${text.take(120)}")
                }
            } finally { c.disconnect() }
        }.getOrElse { Check(false, "Не получилось: ${it.message}") }
    }

    private fun post(path: String, body: JSONObject) {
        if (WORKER_URL.isBlank()) return
        scope.launch {
            runCatching {
                val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return@runCatching
                val c = URL(WORKER_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "POST"
                    c.connectTimeout = 8_000
                    c.readTimeout = 8_000
                    c.doOutput = true
                    c.setRequestProperty("Authorization", "Bearer $token")
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                    val code = c.responseCode // send the request and wait for the answer
                    if (code !in 200..299) android.util.Log.w("PushRelay", "worker $path -> $code")
                } finally {
                    c.disconnect()
                }
            }
        }
    }
}
