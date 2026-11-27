package com.bliss.blisschat.data

import kotlinx.coroutines.async
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.bliss.blisschat.data.crypto.Crypto
import com.bliss.blisschat.data.crypto.E2EE
import com.bliss.blisschat.data.push.PushRelay
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.SetOptions
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
                trySend(parts.values.fold(emptyMap<String, UserProfile>()) { acc, m -> acc + m })
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

    /**
     * Attaches a v1 profile (same e-mail, no encryption key yet) to the current account:
     * the @username is transferred to [uid] and the profile is copied. Returns true when something was claimed.
     */
    suspend fun claimLegacy(uid: String, email: String): Boolean {
        if (email.isBlank()) return false
        var legacy: com.google.firebase.firestore.DocumentSnapshot? = null
        for (m in setOf(email, email.lowercase())) {
            legacy = users.whereEqualTo("email", m).get().await().documents
                .firstOrNull { it.id != uid && it.getString("kid").isNullOrEmpty() }
            if (legacy != null) break
        }
        val old = legacy ?: return false
        val name = old.getString("username").orEmpty().lowercase()
        if (name.isBlank()) return false
        val nameRef = usernames.document(name)
        val now = System.currentTimeMillis()
        db.runTransaction { tx ->
            val snap = tx.get(nameRef)
            if (!snap.exists()) tx.set(nameRef, mapOf("uid" to uid, "email" to email))
            else if (snap.getString("uid") != uid) tx.update(nameRef, mapOf("uid" to uid, "email" to email))
            tx.set(
                users.document(uid),
                mapOf(
                    "displayName" to old.getString("displayName").orEmpty().ifBlank { name },
                    "username" to name, "email" to email,
                    "avatarUrl" to old.getString("avatarUrl").orEmpty(), "bio" to old.getString("bio").orEmpty(),
                    "verified" to false, "support" to false, "publicKey" to "", "kid" to "",
                    "lastSeen" to now, "createdAt" to (old.getLong("createdAt") ?: now)
                )
            )
            true
        }.await()
        return true
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

    /** Avatars are small (256 px) and stored inline in the profile as a data URI, so no Storage bucket is needed. */
    private fun uploadAvatar(@Suppress("UNUSED_PARAMETER") uid: String, uri: Uri): String {
        var quality = 80
        var out = compressImage(uri, 256, quality).first
        while (out.size > 60_000 && quality > 40) { quality -= 10; out = compressImage(uri, 256, quality).first }
        return "data:image/jpeg;base64," + Crypto.b64(out)
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
        val reg = chats.whereIn("type", listOf(ChatType.GROUP.name, ChatType.CHANNEL.name)).addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toChat() }.orEmpty())
        }
        awaitClose { reg.remove() }
    }

    fun directId(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    /** @param secret true = separate end-to-end encrypted chat with the same person (needs keys on both phones) */
    suspend fun openDirect(me: String, username: String, secret: Boolean = false): Result<String> = runCatching {
        val other = findUserByUsername(username) ?: error("Пользователь не найден")
        require(other.uid != me) { "Нельзя написать самому себе" }
        if (secret) {
            require(!other.isBot) { "Секретный чат с ботом невозможен" }
            require(other.kid.isNotEmpty()) { "У @${other.username} ещё нет ключа шифрования: пусть откроет приложение" }
            requireNotNull(e2ee.localKey(me)) { "На этом телефоне нет ключа шифрования" }
        }
        val id = directId(me, other.uid) + if (secret) "_secret" else ""
        val ref = chats.document(id)
        if (!ref.get().await().exists()) {
            val now = System.currentTimeMillis()
            ref.set(
                mapOf(
                    "type" to ChatType.DIRECT.name, "name" to "", "ownerId" to me,
                    "members" to listOf(me, other.uid), "verified" to false,
                    "lastMsgId" to "", "lastSenderId" to "", "lastAt" to now, "readers" to emptyList<String>(),
                    "createdAt" to now, "secret" to secret
                )
            ).await()
        }
        id
    }.mapFailure()

    /* ------------------------------------------------ group / channel @names (separate namespace: chatNames/{name}) */

    private val chatNames get() = db.collection("chatNames")

    /** "" = no public name. Throws a readable error when the name is malformed. */
    private fun cleanChatName(raw: String): String {
        val n = raw.trim().removePrefix("@").lowercase()
        if (n.isEmpty()) return ""
        require(CHAT_NAME.matches(n)) { "Имя группы: 5–32 символа, латиница, цифры и _, начинается с буквы" }
        return n
    }

    suspend fun isChatNameFree(raw: String): Boolean {
        val n = cleanChatName(raw)
        return n.isEmpty() || !chatNames.document(n).get().await().exists()
    }

    /** Photo -> small data URI (same format as user avatars), ready to be stored in avatarUrl. */
    suspend fun avatarDataUri(uri: Uri): Result<String> = runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uploadAvatar("", uri) }
    }.mapFailure()

    /** Owner edits the profile of a group/channel. A changed @name is moved atomically (old one freed, new one claimed). */
    suspend fun updateChatProfile(chat: Chat, me: String, name: String, description: String, usernameRaw: String): Result<Unit> = runCatching {
        require(chat.ownerId == me) { "Менять профиль может только владелец" }
        require(name.isNotBlank()) { "Введите название" }
        val newName = cleanChatName(usernameRaw)
        val batch = db.batch()
        if (newName != chat.username) {
            if (newName.isNotEmpty()) {
                require(!chatNames.document(newName).get().await().exists()) { "@$newName уже занят другой группой или каналом" }
                batch.set(chatNames.document(newName), mapOf("chatId" to chat.id, "ownerId" to me))
            }
            if (chat.username.isNotEmpty()) batch.delete(chatNames.document(chat.username))
        }
        batch.update(chats.document(chat.id), mapOf("name" to name.trim(), "description" to description.trim().take(255), "username" to newName))
        batch.commit().await()
        Unit
    }.mapFailure()

    suspend fun updateChatAvatar(chatId: String, uri: Uri): Result<Unit> = runCatching {
        val data = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uploadAvatar("", uri) }
        chats.document(chatId).update("avatarUrl", data).await()
        Unit
    }.mapFailure()

    /** @name -> public group/channel (null when there is none). */
    suspend fun findChatByName(raw: String): Chat? {
        val n = raw.trim().removePrefix("@").lowercase()
        if (!CHAT_NAME.matches(n)) return null
        val id = chatNames.document(n).get().await().getString("chatId") ?: return null
        return chats.document(id).get().await().toChat()
    }

    /** Public channels (and groups) found through the @name directory, newest activity first. */
    suspend fun listPublicChats(limit: Long = 60, channelsOnly: Boolean = false): List<Chat> {
        val ids = chatNames.limit(limit).get().await().documents.mapNotNull { it.getString("chatId") }.distinct()
        val found = kotlinx.coroutines.coroutineScope {
            ids.map { id -> async { runCatching { chats.document(id).get().await().toChat() }.getOrNull() } }.map { it.await() }
        }.filterNotNull().filter { it.username.isNotEmpty() && !it.secret }
        return found.filter { !channelsOnly || it.type == ChatType.CHANNEL }.sortedByDescending { it.lastAt }
    }

    /** Global search over public groups/channels and people by name / @username. */
    suspend fun searchGlobal(query: String): Pair<List<UserProfile>, List<Chat>> {
        val q = query.trim().removePrefix("@").lowercase()
        if (q.length < 2) return emptyList<UserProfile>() to emptyList()
        val people = kotlinx.coroutines.coroutineScope {
            val byName = async {
                runCatching {
                    usernames.document(q).get().await().getString("uid")?.let { uid -> listOfNotNull(users.document(uid).get().await().toUserProfile()) }.orEmpty()
                }.getOrDefault(emptyList())
            }
            val byPrefix = async {
                runCatching {
                    users.orderBy("username").startAt(q).endAt(q + "\uf8ff").limit(15).get().await().documents.mapNotNull { it.toUserProfile() }
                }.getOrDefault(emptyList())
            }
            (byName.await() + byPrefix.await()).distinctBy { it.uid }
        }
        val groups = runCatching {
            val all = listPublicChats(200)
            all.filter { it.username.contains(q) || it.name.lowercase().contains(q) }
        }.getOrDefault(emptyList())
        return people to groups
    }

    /** Last posts of a public channel (plain text, readable by everybody). */
    suspend fun recentPosts(chat: Chat, me: String, limit: Long = 4): List<Pair<Message, Content>> {
        val docs = chats.document(chat.id).collection("messages").orderBy("ts", Query.Direction.DESCENDING).limit(limit).get().await().documents
        return docs.mapNotNull { d ->
            val m = d.toMessage(chat.id) ?: return@mapNotNull null
            if (m.deleted) return@mapNotNull null
            val c = e2ee.decryptOrNull(chat, me, m.senderId, m.env) ?: return@mapNotNull null
            if (c.kind == Content.KIND_CALL) null else m to c
        }
    }

    /**
     * The official @blisschat channel: created by the super admin on his first start, and every registered account is
     * subscribed to it once (afterwards the user may leave it).
     */
    suspend fun ensureOfficialChannel(user: UserProfile) {
        val prefs = context.getSharedPreferences("official", Context.MODE_PRIVATE)
        if (prefs.getBoolean("joined.${user.uid}", false)) return
        val ref = chats.document(OFFICIAL_CHANNEL)
        val snap = runCatching { ref.get().await() }.getOrNull()
        if (snap == null || !snap.exists()) {
            if (!user.isAdmin) return
            val now = System.currentTimeMillis()
            val batch = db.batch()
            batch.set(chatNames.document(OFFICIAL_CHANNEL), mapOf("chatId" to OFFICIAL_CHANNEL, "ownerId" to user.uid))
            batch.set(ref, mapOf(
                "type" to ChatType.CHANNEL.name, "name" to "blisschat", "avatarUrl" to "", "ownerId" to user.uid,
                "username" to OFFICIAL_CHANNEL, "description" to "Официальный канал blisschat: новости и обновления",
                "creatorTags" to false, "selfTags" to false, "ladder" to emptyList<Any>(),
                "members" to listOf(user.uid), "verified" to false,
                "lastMsgId" to "", "lastSenderId" to "", "lastAt" to now, "readers" to emptyList<String>(),
                "createdAt" to now, "secret" to false
            ))
            runCatching { batch.commit().await() }.onSuccess { prefs.edit().putBoolean("joined.${user.uid}", true).apply() }
            return
        }
        val members = (snap.get("members") as? List<*>).orEmpty()
        if (user.uid in members) { prefs.edit().putBoolean("joined.${user.uid}", true).apply(); return }
        joinPublic(OFFICIAL_CHANNEL, user.uid).onSuccess { prefs.edit().putBoolean("joined.${user.uid}", true).apply() }
    }

    suspend fun joinPublic(chatId: String, me: String): Result<Unit> = runCatching {
        chats.document(chatId).update("members", FieldValue.arrayUnion(me)).await()
        Unit
    }.mapFailure()

    suspend fun createGroup(
        me: String, name: String, memberUsernames: List<String>, type: ChatType = ChatType.GROUP, avatarUrl: String = "",
        username: String = "", description: String = ""
    ): Result<String> = runCatching {
        require(name.isNotBlank()) { "Введите название" }
        val pubName = cleanChatName(username)
        if (pubName.isNotEmpty()) require(!chatNames.document(pubName).get().await().exists()) { "@$pubName уже занят другой группой или каналом" }
        val members = linkedSetOf(me)
        memberUsernames.filter { it.isNotBlank() }.forEach { u ->
            val user = findUserByUsername(u) ?: error("Пользователь @${u.trim().removePrefix("@")} не найден")
            members.add(user.uid)
        }
        val ref = chats.document()
        val now = System.currentTimeMillis()
        val batch = db.batch()
        if (pubName.isNotEmpty()) batch.set(chatNames.document(pubName), mapOf("chatId" to ref.id, "ownerId" to me))
        batch.set(
            ref,
            mapOf(
                "type" to type.name, "name" to name.trim(), "avatarUrl" to avatarUrl.trim(), "ownerId" to me,
                "username" to pubName, "description" to description.trim().take(255),
                "creatorTags" to true, "selfTags" to true, "ladder" to listOf(mapOf("n" to "Новичок", "m" to 0), mapOf("n" to "Активный", "m" to 120), mapOf("n" to "Старожил", "m" to 600)),
                "members" to members.toList(), "verified" to false,
                "lastMsgId" to "", "lastSenderId" to "", "lastAt" to now, "readers" to emptyList<String>(),
                "createdAt" to now, "secret" to false
            )
        )
        batch.commit().await()
        ref.id
    }.mapFailure()

    suspend fun addMember(chat: Chat, me: String, username: String): Result<Unit> = runCatching {
        val user = findUserByUsername(username) ?: error("Пользователь не найден")
        require(user.uid !in chat.members) { "@${user.username} уже в группе" }
        val batch = db.batch()
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
        val pubName = chats.document(chatId).get().await().getString("username").orEmpty()
        for (sub in listOf("messages", "keys", "stats")) {
            val col = chats.document(chatId).collection(sub)
            while (true) {
                val page = col.limit(400).get().await()
                if (page.isEmpty) break
                val batch = db.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        runCatching {
            while (true) {
                val page = db.collection("media").whereEqualTo("chatId", chatId).limit(100).get().await()
                if (page.isEmpty) break
                val batch = db.batch(); page.documents.forEach { batch.delete(it.reference) }; batch.commit().await()
            }
        }
        chats.document(chatId).delete().await()
        if (pubName.isNotEmpty()) runCatching { chatNames.document(pubName).delete().await() }
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

    suspend fun send(chat: Chat, me: String, content: Content, notify: Boolean = true): Result<Unit> = runCatching {
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
        if (notify) PushRelay.notifyMessage(chat.id, msgRef.id)
        Unit
    }.mapFailure()

    /** Encrypts the picture with a fresh key, uploads only the ciphertext, then sends the key inside the message. */
    suspend fun sendImage(chat: Chat, me: String, uri: Uri, caption: String = "", replyToId: String = ""): Result<Unit> = runCatching {
        var (jpeg, w, h) = compressImage(uri, 1280, 80)
        var side = 1280
        while (jpeg.size > 600_000 && side > 480) { side = (side * 0.75).toInt(); val r = compressImage(uri, side, 70); jpeg = r.first; w = r.second; h = r.third }
        val (cipher, key) = e2ee.encryptMedia(jpeg)
        val path = storeMedia(chat.id, me, cipher)
        send(chat, me, Content(Content.KIND_IMAGE, path = path, key = key, w = w, h = h, replyToId = replyToId, caption = caption)).getOrThrow()
    }.mapFailure()

    /** A sticker is an encrypted PNG/WebP sent like a photo, but drawn without a bubble. */
    /** Writes a call-log line into the chat (the caller does it once per call). */
    suspend fun sendCallLog(chatId: String, me: String, status: String, video: Boolean, seconds: Int): Result<Unit> = runCatching {
        val chat = chats.document(chatId).get().await().toChat() ?: error("Чат не найден")
        send(chat, me, Content(Content.KIND_CALL, text = status, w = if (video) 1 else 0, h = seconds), notify = status == "missed" || status == "group").getOrThrow()
    }.mapFailure()

    suspend fun sendSticker(
        chat: Chat, me: String, bytes: ByteArray, w: Int, h: Int,
        packName: String = "", packFiles: List<java.io.File> = emptyList(), replyToId: String = ""
    ): Result<Unit> = runCatching {
        val (cipher, key) = e2ee.encryptMedia(bytes)
        val path = storeMedia(chat.id, me, cipher)
        // The message also carries an (encrypted) manifest of the whole pack, so the receiver can save the entire pack.
        val meta = if (packFiles.size > 1) runCatching { packManifest(chat.id, me, packName, packFiles) }.getOrDefault("") else ""
        send(chat, me, Content(Content.KIND_STICKER, text = meta, path = path, key = key, w = w, h = h, replyToId = replyToId)).getOrThrow()
    }.mapFailure()

    /**
     * Uploads every sticker of the pack once per chat (encrypted, each with its own key) plus a small encrypted manifest
     * [{p: path, k: key}]. Returns the JSON {"pn": name, "mp": manifestPath, "mk": manifestKey}; cached on disk.
     */
    private suspend fun packManifest(chatId: String, me: String, name: String, files: List<java.io.File>): String {
        val list = files.take(120)
        val sig = list.joinToString("|") { "${it.name}:${it.length()}" }.hashCode()
        val dir = java.io.File(context.filesDir, "pack_manifests").apply { mkdirs() }
        val cached = java.io.File(dir, "${chatId}_$sig.json")
        if (cached.exists()) return cached.readText()
        val sem = kotlinx.coroutines.sync.Semaphore(4)
        val entries = kotlinx.coroutines.coroutineScope {
            list.map { f ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    sem.acquire()
                    try {
                        val (c, k) = e2ee.encryptMedia(f.readBytes())
                        org.json.JSONObject().put("p", storeMedia(chatId, me, c)).put("k", k)
                    } finally { sem.release() }
                }
            }.map { it.await() }
        }
        val arr = org.json.JSONArray(); entries.forEach { arr.put(it) }
        val (mc, mk) = e2ee.encryptMedia(org.json.JSONObject().put("o", arr).toString().toByteArray())
        val mp = storeMedia(chatId, me, mc)
        val meta = org.json.JSONObject().put("pn", name).put("mp", mp).put("mk", mk).toString()
        cached.writeText(meta)
        return meta
    }

    /** Downloads a whole sticker pack described by [meta] (see packManifest): returns the PNG bytes of every sticker. */
    suspend fun downloadPack(meta: String): List<ByteArray> {
        val o = org.json.JSONObject(meta)
        val manifest = org.json.JSONObject(String(downloadMedia(o.getString("mp"), o.getString("mk"))))
        val arr = manifest.getJSONArray("o")
        return List(arr.length()) { i ->
            val e = arr.getJSONObject(i)
            downloadMedia(e.getString("p"), e.getString("k"))
        }
    }

    /** Voice / file / music: encrypted blob in media/{id}; metadata goes in Content. */
    suspend fun sendBlob(chat: Chat, me: String, kind: String, bytes: ByteArray, text: String = "", w: Int = 0, h: Int = 0, caption: String = "", replyToId: String = ""): Result<Unit> = runCatching {
        val (cipher, key) = e2ee.encryptMedia(bytes)
        val path = storeMedia(chat.id, me, cipher)
        send(chat, me, Content(kind, text = text, path = path, key = key, w = w, h = h, replyToId = replyToId, caption = caption)).getOrThrow()
    }.mapFailure()

    /**
     * Encrypted files live in Firestore (collection media/{id}, one document <= ~1 MB) so the app works on the free
     * Spark plan, where Firebase Storage is no longer available for new projects. The path "m:{id}" marks this scheme.
     */
    private suspend fun storeMedia(chatId: String, me: String, cipher: ByteArray): String {
        require(cipher.size < 900_000) { "Файл слишком большой" }
        val id = UUID.randomUUID().toString()
        db.collection("media").document(id)
            .set(mapOf("chatId" to chatId, "by" to me, "ts" to System.currentTimeMillis(), "ct" to Blob.fromBytes(cipher))).await()
        return "m:$id"
    }

    suspend fun downloadMedia(path: String, key: String): ByteArray {
        val cipher = if (path.startsWith("m:")) {
            db.collection("media").document(path.removePrefix("m:")).get().await().getBlob("ct")?.toBytes()
                ?: error("Файл не найден")
        } else {
            storage.reference.child(path).getBytes(16L * 1024 * 1024).await() // photos sent by older versions
        }
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

    /** Pins a message under the chat header (null = unpin). Only the id is stored, the text stays encrypted in the message. */
    suspend fun pinMessage(chatId: String, messageId: String?): Result<Unit> = runCatching {
        chats.document(chatId).update("pinnedId", messageId.orEmpty()).await()
        Unit
    }.mapFailure()

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
        if (!mediaPath.isNullOrEmpty()) runCatching {
            if (mediaPath.startsWith("m:")) db.collection("media").document(mediaPath.removePrefix("m:")).delete().await()
            else storage.reference.child(mediaPath).delete().await()
        }
        Unit
    }.mapFailure()

    /* ------------------------------------------------------------- group tags */

    fun observeStats(chatId: String): Flow<Map<String, MemberStat>> = callbackFlow {
        val reg = chats.document(chatId).collection("stats").addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyMap()); return@addSnapshotListener }
            trySend(snap?.documents?.associate {
                it.id to MemberStat(it.id, it.getLong("minutes") ?: 0L, it.getLong("messages") ?: 0L, it.getString("selfTag").orEmpty())
            }.orEmpty())
        }
        awaitClose { reg.remove() }
    }

    /** Adds time spent / messages written to my activity counters in this group. */
    suspend fun touchStats(chatId: String, me: String, minutes: Long, messages: Long) {
        runCatching {
            chats.document(chatId).collection("stats").document(me).set(
                mapOf("minutes" to FieldValue.increment(minutes), "messages" to FieldValue.increment(messages), "ts" to System.currentTimeMillis()),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun setSelfTag(chatId: String, me: String, tag: String): Result<Unit> = runCatching {
        chats.document(chatId).collection("stats").document(me)
            .set(mapOf("selfTag" to tag.trim().take(16)), SetOptions.merge()).await()
        Unit
    }.mapFailure()

    suspend fun updateTagSettings(chatId: String, creatorTags: Boolean, selfTags: Boolean, ladder: List<TagStep>): Result<Unit> = runCatching {
        chats.document(chatId).update(
            mapOf("creatorTags" to creatorTags, "selfTags" to selfTags, "ladder" to ladder.map { mapOf("n" to it.name.trim().take(16), "m" to it.minutes) })
        ).await()
        Unit
    }.mapFailure()

    /* ------------------------------------------------------------ push tokens */

    suspend fun saveFcmToken(uid: String, token: String) {
        runCatching { users.document(uid).update("fcmTokens", FieldValue.arrayUnion(token)).await() }
    }

    /* ------------------------------------------------------------------ admin */

    private fun requireAdmin(actor: UserProfile) { check(actor.isAdmin) { "Нужны права администратора" } }

    suspend fun setVerified(actor: UserProfile, targetUid: String, value: Boolean): Result<Unit> = runCatching {
        requireAdmin(actor); users.document(targetUid).update("verified", value).await(); Unit
    }.mapFailure()

    suspend fun setSupport(actor: UserProfile, targetUid: String, value: Boolean): Result<Unit> = runCatching {
        requireAdmin(actor); users.document(targetUid).update("support", value).await(); Unit
    }.mapFailure()

    suspend fun setModerator(actor: UserProfile, targetUid: String, value: Boolean): Result<Unit> = runCatching {
        requireAdmin(actor); users.document(targetUid).update("moderator", value).await(); Unit
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
const val OFFICIAL_CHANNEL = "blisschat"
private val CHAT_NAME = Regex("^[a-z][a-z0-9_]{4,31}$")

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
