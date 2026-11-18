package com.bliss.blisschat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.bliss.blisschat.data.crypto.Crypto
import com.bliss.blisschat.data.crypto.E2EE
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * All Firestore / Storage access (Repository layer of MVVM).
 *
 *   users/{uid}                    profile, badges, public key
 *   users/{uid}/keys/{kid}         public key history (so old messages stay readable)
 *   usernames/{name}               { uid, email } unique @username index (also used for username login)
 *   chats/{id}                     direct / group, last message envelope (ciphertext)
 *   chats/{id}/keys/{uid}          group key wrapped for that member
 *   chats/{id}/messages/{id}       ciphertext + key ids + reactions
 *   Storage: media/{chatId}/{uuid} encrypted files, avatars/{uid}.jpg public avatars
 */
class ChatRepository(private val context: Context, val e2ee: E2EE) {

    private val db get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance()
    private val users get() = db.collection("users")
    private val usernames get() = db.collection("usernames")
    private val chats get() = db.collection("chats")

    /* ------------------------------------------------------------------ users */

    fun observeUser(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toUserProfile())
        }
        awaitClose { reg.remove() }
    }

    fun observeUsers(ids: Set<String>): Flow<Map<String, UserProfile>> = callbackFlow {
        val parts = HashMap<Int, Map<String, UserProfile>>()
        val regs = ids.toList().chunked(10).mapIndexed { index, chunk ->
            users.whereIn(FieldPath.documentId(), chunk).addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                parts[index] = snap.documents.mapNotNull { it.toUserProfile() }.associateBy { it.uid }
                trySend(parts.values.fold(emptyMap()) { acc, m -> acc + m })
            }
        }
        if (regs.isEmpty()) trySend(emptyMap())
        awaitClose { regs.forEach { it.remove() } }
    }

    suspend fun isUsernameFree(username: String, forUid: String? = null): Boolean {
        val doc = usernames.document(username.trim().lowercase()).get().await()
        return !doc.exists() || (forUid != null && doc.getString("uid") == forUid)
    }

    suspend fun findUserByUsername(raw: String): UserProfile? {
        val name = raw.trim().removePrefix("@").lowercase()
        if (name.isEmpty()) return null
        val uid = usernames.document(name).get().await().getString("uid") ?: return null
        return users.document(uid).get().await().toUserProfile()
    }

    /** Creates the profile, reserves the @username and publishes this device's public key. */
    suspend fun createProfile(
        uid: String, email: String, displayName: String, username: String, avatar: Uri?
    ): Result<Unit> = runCatching {
        val lower = username.trim().lowercase()
        if (!isUsernameFree(lower, uid)) error("@$lower уже занят")
        val key = e2ee.ensureKeys(uid, publishToProfile = false)
        val avatarUrl = avatar?.let { runCatching { uploadAvatar(uid, it) }.getOrDefault("") }.orEmpty()
        val nameRef = usernames.document(lower)
        val userRef = users.document(uid)
        db.runTransaction { tx ->
            val snap = tx.get(nameRef)
            if (snap.exists() && snap.getString("uid") != uid) error("@$lower уже занят")
            if (!snap.exists()) tx.set(nameRef, mapOf("uid" to uid, "email" to email))
            tx.set(
                userRef,
                mapOf(
                    "displayName" to displayName.trim(), "username" to lower, "email" to email,
                    "avatarUrl" to avatarUrl, "bio" to "", "verified" to false, "support" to false,
                    "publicKey" to Crypto.b64(key.publicX509), "kid" to key.kid,
                    "lastSeen" to System.currentTimeMillis(), "createdAt" to System.currentTimeMillis()
                )
            )
            true
        }.await()
        Unit
    }.mapFailure()

    suspend fun updateProfile(uid: String, name: String, bio: String): Result<Unit> = runCatching {
        users.document(uid).update(mapOf("displayName" to name.trim(), "bio" to bio.trim())).await()
        Unit
    }.mapFailure()

    suspend fun updateAvatar(uid: String, uri: Uri): Result<Unit> = runCatching {
        users.document(uid).update("avatarUrl", uploadAvatar(uid, uri)).await()
        Unit
    }.mapFailure()

    private suspend fun uploadAvatar(uid: String, uri: Uri): String {
        val (bytes, _, _) = compressImage(uri, 512, 85)
        val ref = storage.reference.child("avatars/$uid.jpg")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString() + "&v=" + System.currentTimeMillis()
    }

    suspend fun touchPresence(uid: String) {
        runCatching { users.document(uid).update("lastSeen", System.currentTimeMillis()).await() }
    }

    suspend fun report(by: String, target: String): Result<Unit> = runCatching {
        db.collection("reports").add(mapOf("by" to by, "target" to target, "ts" to System.currentTimeMillis())).await()
        Unit
    }.mapFailure()

    /* ------------------------------------------------------------------ chats */

    fun observeMyChats(uid: String): Flow<List<Chat>> = callbackFlow {
        val reg = chats.whereArrayContains("members", uid).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toChat() }.orEmpty())
        }
        awaitClose { reg.remove() }
    }

    fun observeChat(chatId: String): Flow<Chat?> = callbackFlow {
        val reg = chats.document(chatId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toChat())
        }
        awaitClose { reg.remove() }
    }

    fun observeAllGroups(): Flow<List<Chat>> = callbackFlow {
        val reg = chats.whereEqualTo("type", ChatType.GROUP.name).addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toChat() }.orEmpty())
        }
        awaitClose { reg.remove() }
    }

    fun directId(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    suspend fun openDirect(me: String, username: String): Result<String> = runCatching {
        val other = findUserByUsername(username) ?: error("Пользователь не найден")
        require(other.uid != me) { "Нельзя написать самому себе" }
        require(other.kid.isNotEmpty()) { "У @${other.username} ещё нет ключа шифрования" }
        val id = directId(me, other.uid)
        val ref = chats.document(id)
        if (!ref.get().await().exists()) {
            val now = System.currentTimeMillis()
            ref.set(
                mapOf(
                    "type" to ChatType.DIRECT.name, "name" to "", "ownerId" to me,
                    "members" to listOf(me, other.uid), "verified" to false,
                    "lastMsgId" to "", "lastSenderId" to "", "lastAt" to now, "readers" to emptyList<String>(),
                    "createdAt" to now
                )
            ).await()
        }
        id
    }.mapFailure()

    suspend fun createGroup(me: String, name: String, memberUsernames: List<String>): Result<String> = runCatching {
        require(name.isNotBlank()) { "Введите название группы" }
        val myKey = e2ee.localKey(me) ?: error("Нет ключа шифрования на этом устройстве")
        val members = linkedMapOf<String, String>() // uid -> kid
        members[me] = myKey.kid
        memberUsernames.filter { it.isNotBlank() }.forEach { u ->
            val user = findUserByUsername(u) ?: error("Пользователь @${u.trim().removePrefix("@")} не найден")
            require(user.kid.isNotEmpty()) { "У @${user.username} ещё нет ключа шифрования" }
            members[user.uid] = user.kid
        }
        val groupKey = Crypto.randomKey()
        val ref = chats.document()
        val now = System.currentTimeMillis()
        val batch = db.batch()
        batch.set(
            ref,
            mapOf(
                "type" to ChatType.GROUP.name, "name" to name.trim(), "ownerId" to me,
                "members" to members.keys.toList(), "verified" to false,
                "lastMsgId" to "", "lastSenderId" to "", "lastAt" to now, "readers" to emptyList<String>(),
                "createdAt" to now
            )
        )
        for ((uid, kid) in members) {
            batch.set(ref.collection("keys").document(uid), e2ee.wrapGroupKey(ref.id, me, groupKey, uid, kid))
        }
        batch.commit().await()
        e2ee.rememberGroupKey(ref.id, groupKey)
        ref.id
    }.mapFailure()

    suspend fun addMember(chat: Chat, me: String, username: String): Result<Unit> = runCatching {
        val user = findUserByUsername(username) ?: error("Пользователь не найден")
        require(user.uid !in chat.members) { "@${user.username} уже в группе" }
        require(user.kid.isNotEmpty()) { "У @${user.username} ещё нет ключа шифрования" }
        val gk = e2ee.groupKey(chat.id, me)
        val batch = db.batch()
        batch.set(chats.document(chat.id).collection("keys").document(user.uid),
            e2ee.wrapGroupKey(chat.id, me, gk, user.uid, user.kid))
        batch.update(chats.document(chat.id), "members", FieldValue.arrayUnion(user.uid))
        batch.commit().await()
        Unit
    }.mapFailure()

    suspend fun leave(chatId: String, uid: String): Result<Unit> = runCatching {
        chats.document(chatId).update("members", FieldValue.arrayRemove(uid)).await()
        Unit
    }.mapFailure()

    /** Deletes a group with all of its messages and keys (owner or Super Admin). */
    suspend fun deleteChat(chatId: String): Result<Unit> = runCatching {
        for (sub in listOf("messages", "keys")) {
            val col = chats.document(chatId).collection(sub)
            while (true) {
                val page = col.limit(400).get().await()
                if (page.isEmpty) break
                val batch = db.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        chats.document(chatId).delete().await()
        Unit
    }.mapFailure()

    /* --------------------------------------------------------------- messages */

    fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val reg = chats.document(chatId).collection("messages")
            .orderBy("ts", Query.Direction.ASCENDING).limitToLast(300)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { it.toMessage(chatId) }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun send(chat: Chat, me: String, content: Content): Result<Unit> = runCatching {
        val env = e2ee.encrypt(chat, me, content)
        val now = System.currentTimeMillis()
        val msgRef = chats.document(chat.id).collection("messages").document()
        val batch = db.batch()
        batch.set(
            msgRef,
            mapOf("senderId" to me, "ct" to env.ct, "a" to env.a, "b" to env.b, "ts" to now,
                "readBy" to listOf(me), "deleted" to false)
        )
        batch.update(
            chats.document(chat.id),
            mapOf(
                "lastEnv" to mapOf("ct" to env.ct, "a" to env.a, "b" to env.b),
                "lastMsgId" to msgRef.id, "lastSenderId" to me, "lastAt" to now, "readers" to listOf(me)
            )
        )
        batch.commit().await()
        Unit
    }.mapFailure()

    /** Encrypts the picture with a fresh key, uploads only the ciphertext, then sends the key inside the message. */
    suspend fun sendImage(chat: Chat, me: String, uri: Uri): Result<Unit> = runCatching {
        val (jpeg, w, h) = compressImage(uri, 1600, 82)
        val (cipher, key) = e2ee.encryptMedia(jpeg)
        val path = "media/${chat.id}/${UUID.randomUUID()}"
        storage.reference.child(path).putBytes(cipher).await()
        send(chat, me, Content(Content.KIND_IMAGE, path = path, key = key, w = w, h = h)).getOrThrow()
    }.mapFailure()

    suspend fun downloadMedia(path: String, key: String): ByteArray {
        val cipher = storage.reference.child(path).getBytes(16L * 1024 * 1024).await()
        return e2ee.decryptMedia(cipher, key)
    }

    suspend fun markRead(chat: Chat, me: String, messages: List<Message>) {
        val unread = messages.filter { me !in it.readBy && !it.deleted }.takeLast(100)
        if (unread.isEmpty() && !chat.unreadFor(me)) return
        runCatching {
            val batch = db.batch()
            unread.forEach {
                batch.update(chats.document(chat.id).collection("messages").document(it.id),
                    "readBy", FieldValue.arrayUnion(me))
            }
            if (chat.unreadFor(me)) batch.update(chats.document(chat.id), "readers", FieldValue.arrayUnion(me))
            batch.commit().await()
        }
    }

    /** Sets, changes or (emoji == null) removes my reaction. */
    suspend fun react(chatId: String, messageId: String, me: String, emoji: String?): Result<Unit> = runCatching {
        chats.document(chatId).collection("messages").document(messageId)
            .update(FieldPath.of("reactions", me), emoji ?: FieldValue.delete()).await()
        Unit
    }.mapFailure()

    suspend fun deleteForEveryone(chat: Chat, message: Message, mediaPath: String?): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            chats.document(chat.id).collection("messages").document(message.id),
            mapOf("deleted" to true, "ct" to "", "a" to "", "b" to "", "reactions" to emptyMap<String, String>())
        )
        if (chat.lastMsgId == message.id) batch.update(chats.document(chat.id), "lastEnv", FieldValue.delete())
        batch.commit().await()
        if (!mediaPath.isNullOrEmpty()) runCatching { storage.reference.child(mediaPath).delete().await() }
        Unit
    }.mapFailure()

    /* ------------------------------------------------------------------ admin */

    private fun requireAdmin(actor: UserProfile) { check(actor.isAdmin) { "Нужны права администратора" } }

    suspend fun setVerified(actor: UserProfile, targetUid: String, value: Boolean): Result<Unit> = runCatching {
        requireAdmin(actor); users.document(targetUid).update("verified", value).await(); Unit
    }.mapFailure()

    suspend fun setSupport(actor: UserProfile, targetUid: String, value: Boolean): Result<Unit> = runCatching {
        requireAdmin(actor); users.document(targetUid).update("support", value).await(); Unit
    }.mapFailure()

    suspend fun setGroupVerified(actor: UserProfile, chatId: String, value: Boolean): Result<Unit> = runCatching {
        requireAdmin(actor); chats.document(chatId).update("verified", value).await(); Unit
    }.mapFailure()

    suspend fun adminDeleteChat(actor: UserProfile, chatId: String): Result<Unit> = runCatching {
        requireAdmin(actor); deleteChat(chatId).getOrThrow()
    }.mapFailure()

    /* ----------------------------------------------------------------- images */

    /** @return JPEG bytes, width, height (EXIF-rotated and downscaled). */
    private fun compressImage(uri: Uri, maxSide: Int, quality: Int): Triple<ByteArray, Int, Int> {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Не удалось открыть изображение")
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Triple(out.toByteArray(), bmp.width, bmp.height)
    }
}

/** Turns raw exceptions into readable messages for the UI. */
internal fun <T> Result<T>.mapFailure(): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { e ->
        val msg = when {
            e is com.google.firebase.firestore.FirebaseFirestoreException &&
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Нет доступа (проверьте правила Firestore)"
            e is com.google.firebase.FirebaseNetworkException -> "Нет подключения к интернету"
            e.message.isNullOrBlank() -> "Что-то пошло не так"
            else -> e.message!!
        }
        Result.failure(IllegalStateException(msg, e))
    }
)

val currentAuthUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid
