package com.bliss.blisschat.data

import com.google.firebase.firestore.DocumentSnapshot
import org.json.JSONObject

/** The one account that automatically receives Super Admin privileges (case-insensitive). */
const val ADMIN_USERNAME = "tomatik_dev"

enum class ChatType { DIRECT, GROUP }

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val verified: Boolean = false,
    val support: Boolean = false,
    val publicKey: String = "",
    val kid: String = "",
    val lastSeen: Long = 0L,
    val createdAt: Long = 0L
) {
    val isAdmin: Boolean get() = username.equals(ADMIN_USERNAME, ignoreCase = true)
    val title: String get() = displayName.ifBlank { username }
    fun online(now: Long = System.currentTimeMillis()) = now - lastSeen < 75_000L
}

/** What is stored on the server for every message: only ciphertext + key ids. */
data class Envelope(val ct: String = "", val a: String = "", val b: String = "")

data class Chat(
    val id: String = "",
    val type: ChatType = ChatType.GROUP,
    val name: String = "",
    val ownerId: String = "",
    val members: List<String> = emptyList(),
    val verified: Boolean = false,
    val lastEnv: Envelope? = null,
    val lastMsgId: String = "",
    val lastSenderId: String = "",
    val lastAt: Long = 0L,
    val readers: List<String> = emptyList(),
    val createdAt: Long = 0L
) {
    fun peerOf(me: String): String? = if (type == ChatType.DIRECT) members.firstOrNull { it != me } else null
    fun unreadFor(me: String) = lastMsgId.isNotEmpty() && lastSenderId != me && me !in readers
}

data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val env: Envelope = Envelope(),
    val ts: Long = 0L,
    val readBy: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val deleted: Boolean = false
)

/** Decrypted message body. Serialised to JSON, then encrypted. */
data class Content(
    val kind: String = KIND_TEXT,
    val text: String = "",
    val path: String = "",
    val key: String = "",
    val w: Int = 0,
    val h: Int = 0
) {
    fun toJson(): String = JSONObject()
        .put("k", kind).put("t", text).put("p", path).put("e", key).put("w", w).put("h", h).toString()

    val preview: String get() = if (kind == KIND_IMAGE) "📷 Фото" else text

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_IMAGE = "image"
        fun fromJson(s: String): Content {
            val o = JSONObject(s)
            return Content(
                o.optString("k", KIND_TEXT), o.optString("t"), o.optString("p"),
                o.optString("e"), o.optInt("w"), o.optInt("h")
            )
        }
    }
}

/** A message ready for the UI (decrypted, or marked as undecryptable). */
data class UiMessage(val message: Message, val content: Content?, val failed: Boolean)

/** One row of the chat list. */
data class ChatItem(
    val chat: Chat,
    val title: String,
    val avatarUrl: String,
    val verified: Boolean,
    val support: Boolean,
    val preview: String,
    val time: Long,
    val unread: Boolean
)

/* ---------- Firestore mapping (manual, so Kotlin property names can't bite us) ---------- */

fun DocumentSnapshot.toUserProfile(): UserProfile? {
    if (!exists()) return null
    return UserProfile(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        username = getString("username").orEmpty(),
        email = getString("email").orEmpty(),
        avatarUrl = getString("avatarUrl").orEmpty(),
        bio = getString("bio").orEmpty(),
        verified = getBoolean("verified") ?: false,
        support = getBoolean("support") ?: false,
        publicKey = getString("publicKey").orEmpty(),
        kid = getString("kid").orEmpty(),
        lastSeen = getLong("lastSeen") ?: 0L,
        createdAt = getLong("createdAt") ?: 0L
    )
}

@Suppress("UNCHECKED_CAST")
private fun envelopeOf(map: Any?): Envelope? {
    val m = map as? Map<String, Any?> ?: return null
    val ct = m["ct"] as? String ?: return null
    return Envelope(ct, m["a"] as? String ?: "", m["b"] as? String ?: "")
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toChat(): Chat? {
    if (!exists()) return null
    return Chat(
        id = id,
        type = runCatching { ChatType.valueOf(getString("type") ?: "GROUP") }.getOrDefault(ChatType.GROUP),
        name = getString("name").orEmpty(),
        ownerId = getString("ownerId").orEmpty(),
        members = (get("members") as? List<String>).orEmpty(),
        verified = getBoolean("verified") ?: false,
        lastEnv = envelopeOf(get("lastEnv")),
        lastMsgId = getString("lastMsgId").orEmpty(),
        lastSenderId = getString("lastSenderId").orEmpty(),
        lastAt = getLong("lastAt") ?: 0L,
        readers = (get("readers") as? List<String>).orEmpty(),
        createdAt = getLong("createdAt") ?: 0L
    )
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toMessage(chatId: String): Message? {
    if (!exists()) return null
    return Message(
        id = id,
        chatId = chatId,
        senderId = getString("senderId").orEmpty(),
        env = Envelope(getString("ct").orEmpty(), getString("a").orEmpty(), getString("b").orEmpty()),
        ts = getLong("ts") ?: 0L,
        readBy = (get("readBy") as? List<String>).orEmpty(),
        reactions = (get("reactions") as? Map<String, String>).orEmpty(),
        deleted = getBoolean("deleted") ?: false
    )
}
