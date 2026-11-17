package com.bliss.blisschat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

/**
 * All Firestore / Storage access lives here (Repository layer of MVVM).
 *
 * Collections:
 *   users/{uid}                    profile + badges
 *   usernames/{lowercaseName}      { uid }  -> unique @username index
 *   chats/{chatId}                 direct / group / channel
 *   chats/{chatId}/messages/{id}   messages
 */
class ChatRepository(private val context: Context) {

    private val db get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance()

    private val users get() = db.collection("users")
    private val usernames get() = db.collection("usernames")
    private val chats get() = db.collection("chats")

    /* ------------------------------------------------------------------ users */

    fun observeUser(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.toUserProfile())
        }
        awaitClose { reg.remove() }
    }

    /** Live map of profiles for the given ids (used for names, avatars and badges). */
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

    /** Creates the profile document and reserves the @username atomically. */
    suspend fun createProfile(
        uid: String,
        email: String,
        displayName: String,
        username: String,
        avatar: Uri?
    ): Result<Unit> = runCatching {
        val lower = username.trim().lowercase()
        if (!isUsernameFree(lower, uid)) error("@$lower is already taken")
        val avatarUrl = avatar?.let { uploadAvatar(uid, it).getOrDefault("") }.orEmpty()
        val nameRef = usernames.document(lower)
        val userRef = users.document(uid)
        db.runTransaction { tx ->
            val snap = tx.get(nameRef)
            if (snap.exists() && snap.getString("uid") != uid) {
                throw IllegalStateException("@$lower is already taken")
            }
            if (!snap.exists()) tx.set(nameRef, mapOf("uid" to uid))
            tx.set(
                userRef,
                mapOf(
                    "displayName" to displayName.trim(),
                    "username" to lower,
                    "email" to email,
                    "avatarUrl" to avatarUrl,
                    "verified" to false,
                    "support" to false,
                    "createdAt" to System.currentTimeMillis()
                )
            )
            true
        }.await()
        Unit
    }.mapFailure()

    suspend fun updateDisplayName(uid: String, name: String): Result<Unit> = runCatching {
        users.document(uid).update("displayName", name.trim()).await()
        Unit
    }.mapFailure()

    suspend fun updateAvatar(uid: String, uri: Uri): Result<Unit> = runCatching {
        val url = uploadAvatar(uid, uri).getOrThrow()
        users.document(uid).update("avatarUrl", url).await()
        Unit
    }.mapFailure()

    private suspend fun uploadAvatar(uid: String, uri: Uri): Result<String> = runCatching {
        val bytes = compressImage(uri)
        val ref = storage.reference.child("avatars/$uid.jpg")
        ref.putBytes(bytes).await()
        // cache-bust so Coil reloads after the avatar changes
        ref.downloadUrl.await().toString() + "&v=" + System.currentTimeMillis()
    }.mapFailure()

    private fun compressImage(uri: Uri, maxSide: Int = 512): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open image")
        val bmp = input.use { BitmapFactory.decodeStream(it) } ?: error("Not a valid image")
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    /* ------------------------------------------------------------------ chats */

    fun observeMyChats(uid: String): Flow<List<Chat>> = callbackFlow {
        val reg = chats.whereArrayContains("members", uid).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.documents?.mapNotNull { it.toChat() }.orEmpty())
        }
        awaitClose { reg.remove() }
    }

    fun observePublicChannels(): Flow<List<Chat>> = callbackFlow {
        val reg = chats.whereEqualTo("type", ChatType.CHANNEL.name)
            .whereEqualTo("discoverable", true)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toChat() }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    /** Every channel in the database (admin panel). */
    fun observeAllChannels(): Flow<List<Chat>> = callbackFlow {
        val reg = chats.whereEqualTo("type", ChatType.CHANNEL.name).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents?.mapNotNull { it.toChat() }.orEmpty())
        }
        awaitClose { reg.remove() }
    }

    fun observeChat(chatId: String): Flow<Chat?> = callbackFlow {
        val reg = chats.document(chatId).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.toChat())
        }
        awaitClose { reg.remove() }
    }

    suspend fun openDirect(me: String, otherUsername: String): Result<String> = runCatching {
        val other = findUserByUsername(otherUsername) ?: error("No user with that @username")
        require(other.uid != me) { "You can't message yourself" }
        val id = listOf(me, other.uid).sorted().joinToString("_")
        val ref = chats.document(id)
        if (!ref.get().await().exists()) {
            ref.set(
                mapOf(
                    "type" to ChatType.DIRECT.name,
                    "name" to "",
                    "ownerId" to me,
                    "members" to listOf(me, other.uid),
                    "discoverable" to false,
                    "lastMessage" to "",
                    "lastMessageAt" to System.currentTimeMillis(),
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
        }
        id
    }.mapFailure()

    suspend fun createGroup(me: String, name: String, memberUsernames: List<String>): Result<String> = runCatching {
        require(name.isNotBlank()) { "Group name is required" }
        val members = linkedSetOf(me)
        memberUsernames.filter { it.isNotBlank() }.forEach { u ->
            val user = findUserByUsername(u) ?: error("No user with @${u.trim().removePrefix("@")}")
            members += user.uid
        }
        val ref = chats.document()
        val now = System.currentTimeMillis()
        ref.set(
            mapOf(
                "type" to ChatType.GROUP.name,
                "name" to name.trim(),
                "ownerId" to me,
                "members" to members.toList(),
                "discoverable" to false,
                "lastMessage" to "Group created",
                "lastMessageAt" to now,
                "createdAt" to now
            )
        ).await()
        ref.id
    }.mapFailure()

    suspend fun createChannel(
        me: String,
        name: String,
        description: String,
        discoverable: Boolean
    ): Result<String> = runCatching {
        require(name.isNotBlank()) { "Channel name is required" }
        val ref = chats.document()
        val now = System.currentTimeMillis()
        ref.set(
            mapOf(
                "type" to ChatType.CHANNEL.name,
                "name" to name.trim(),
                "description" to description.trim(),
                "ownerId" to me,
                "members" to listOf(me),
                "discoverable" to discoverable,
                "lastMessage" to "Channel created",
                "lastMessageAt" to now,
                "createdAt" to now
            )
        ).await()
        ref.id
    }.mapFailure()

    suspend fun join(chatId: String, uid: String): Result<Unit> = runCatching {
        chats.document(chatId).update("members", FieldValue.arrayUnion(uid)).await()
        Unit
    }.mapFailure()

    suspend fun leave(chatId: String, uid: String): Result<Unit> = runCatching {
        chats.document(chatId).update("members", FieldValue.arrayRemove(uid)).await()
        Unit
    }.mapFailure()

    /** Force-deletes a channel together with all of its messages. */
    suspend fun deleteChat(chatId: String): Result<Unit> = runCatching {
        val msgs = chats.document(chatId).collection("messages")
        while (true) {
            val page = msgs.limit(400).get().await()
            if (page.isEmpty) break
            val batch = db.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        chats.document(chatId).delete().await()
        Unit
    }.mapFailure()

    /* --------------------------------------------------------------- messages */

    fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val reg = chats.document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(300)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toMessage(chatId) }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(chatId: String, senderId: String, text: String): Result<Unit> = runCatching {
        val body = text.trim()
        require(body.isNotEmpty()) { "Empty message" }
        val now = System.currentTimeMillis()
        val msgRef = chats.document(chatId).collection("messages").document()
        val batch = db.batch()
        batch.set(
            msgRef,
            mapOf(
                "senderId" to senderId,
                "text" to body,
                "timestamp" to now,
                "readBy" to listOf(senderId)
            )
        )
        batch.update(
            chats.document(chatId),
            mapOf("lastMessage" to body.take(120), "lastMessageAt" to now)
        )
        batch.commit().await()
        Unit
    }.mapFailure()

    suspend fun markRead(chatId: String, uid: String, messages: List<Message>) {
        val unread = messages.filter { uid !in it.readBy }.takeLast(100)
        if (unread.isEmpty()) return
        runCatching {
            val batch = db.batch()
            unread.forEach {
                batch.update(
                    chats.document(chatId).collection("messages").document(it.id),
                    "readBy", FieldValue.arrayUnion(uid)
                )
            }
            batch.commit().await()
        }
    }

    /* ------------------------------------------------------------------ admin */

    private fun requireAdmin(actor: UserProfile) {
        check(actor.isAdmin) { "Admin privileges required" }
    }

    suspend fun setVerified(actor: UserProfile, targetUid: String, value: Boolean): Result<Unit> = runCatching {
        requireAdmin(actor)
        users.document(targetUid).update("verified", value).await()
        Unit
    }.mapFailure()

    suspend fun setSupport(actor: UserProfile, targetUid: String, value: Boolean): Result<Unit> = runCatching {
        requireAdmin(actor)
        users.document(targetUid).update("support", value).await()
        Unit
    }.mapFailure()

    suspend fun adminDeleteChannel(actor: UserProfile, chatId: String): Result<Unit> = runCatching {
        requireAdmin(actor)
        deleteChat(chatId).getOrThrow()
    }.mapFailure()
}
