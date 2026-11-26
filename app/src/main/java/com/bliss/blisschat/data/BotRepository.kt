package com.bliss.blisschat.data

import android.content.Context
import com.bliss.blisschat.data.crypto.Crypto
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * User-made bots. A bot is a normal account (username ends with "_bot", flag bot = true) that is driven by a Python
 * program (see /bots in the project). The password of that account is the bot's token and is shown only once.
 */
class BotRepository(private val context: Context) {

    suspend fun updateBotCode(botUid: String, pythonCode: String): Result<Unit> = runCatching {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(botUid).update("pythonCode", pythonCode.take(10_000)).await()
        Unit
    }.mapFailure()

    suspend fun deleteBot(botUid: String): Result<Unit> = runCatching {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(botUid).delete().await()
        Unit
    }.mapFailure()

    fun observeMyBots(ownerUid: String): Flow<List<UserProfile>> = callbackFlow {
        val reg = FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("ownerId", ownerUid)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents.orEmpty().mapNotNull { it.toUserProfile() })
            }
        awaitClose { reg.remove() }
    }

    /** @return the bot token "username:password" */
    suspend fun createBot(owner: UserProfile, name: String, rawUsername: String, pythonCode: String = ""): Result<String> = runCatching {
        val username = rawUsername.trim().removePrefix("@").lowercase()
        require(name.isNotBlank()) { "Введите имя бота" }
        require(Regex("^[a-z][a-z0-9_]{1,26}_bot$").matches(username)) {
            "Имя бота: латиница, цифры и _, оно должно заканчиваться на _bot"
        }
        require(username != SYSTEM_BOT_USERNAME) { "Это имя зарезервировано" }
        val main = FirebaseFirestore.getInstance()
        require(!main.collection("usernames").document(username).get().await().exists()) { "@$username уже занят" }

        // A second Firebase instance, so creating the bot account does not sign the owner out.
        val app = runCatching { FirebaseApp.getInstance("bots") }
            .getOrElse { FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "bots") }
        val auth = FirebaseAuth.getInstance(app)
        val db = FirebaseFirestore.getInstance(app)

        val password = Crypto.b64(Crypto.randomBytes(18)).replace('+', 'A').replace('/', 'B')
        val email = "$username@bots.blisschat.app"
        val created = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = created.user?.uid ?: error("Не удалось создать бота")
        try {
            val now = System.currentTimeMillis()
            val batch = db.batch()
            batch.set(db.collection("usernames").document(username), mapOf("uid" to uid, "email" to email))
            batch.set(
                db.collection("users").document(uid),
                mapOf(
                    "displayName" to name.trim().take(40), "username" to username, "email" to email, "avatarUrl" to "", "bio" to "Бот",
                    "verified" to false, "support" to false, "publicKey" to "", "kid" to "", "bot" to true, "ownerId" to owner.uid,
                    "pythonCode" to pythonCode.trim().take(10_000).ifBlank { "# Python bot script\nimport time\nprint('Bot running')" },
                    "lastSeen" to now, "createdAt" to now
                )
            )
            batch.commit().await()
        } catch (e: Exception) {
            runCatching { created.user?.delete()?.await() }
            throw e
        } finally {
            auth.signOut()
        }
        "$username:$password"
    }.mapFailure()
}
