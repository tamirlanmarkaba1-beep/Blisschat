package com.bliss.blisschat.data

import com.google.firebase.firestore.DocumentSnapshot
import org.json.JSONArray
import org.json.JSONObject

/** The one account that automatically receives Super Admin privileges (case-insensitive). */
const val ADMIN_USERNAME = "tomatik_dev"

enum class ChatType { DIRECT, GROUP, CHANNEL }

/** Username of the built-in system bot (all bot usernames end with "_bot"). */
const val SYSTEM_BOT_USERNAME = "blisschat_bot"

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
    val createdAt: Long = 0L,
    val bot: Boolean = false,
    val moderator: Boolean = false,
    val pythonCode: String = "",
    /** AI account (username ends with _ai): answers through the owner's phone using the owner's API key. */
    val ai: Boolean = false,
    val aiPrompt: String = "",
    val ownerId: String = "",
    /** Mini app (a web page) of a bot, opened inside the messenger. */
    val miniAppUrl: String = ""
) {
    val isBot: Boolean get() = bot || username.endsWith("_bot", ignoreCase = true)
    val isAi: Boolean get() = ai || username.endsWith("_ai", ignoreCase = true)
    val isAdmin: Boolean get() = username.equals(ADMIN_USERNAME, ignoreCase = true)
    val title: String get() = displayName.ifBlank { username }
    fun online(now: Long = System.currentTimeMillis()) = now - lastSeen < 75_000L
}

/** One rung of the creator's tag ladder: [name] is granted after [minutes] spent in the group. */
data class TagStep(val name: String, val minutes: Int)

/** Per-member activity in a group (chats/{id}/stats/{uid}). */
data class MemberStat(val uid: String = "", val minutes: Long = 0L, val messages: Long = 0L, val selfTag: String = "")

/** Rank earned from the ladder: the highest step whose threshold has been reached (activity = minutes + 2 per message). */
fun Chat.rankFor(stat: MemberStat?): String? {
    if (!creatorTags || stat == null) return null
    val score = stat.minutes + stat.messages * 2
    return ladder.filter { score >= it.minutes }.maxByOrNull { it.minutes }?.name
}

/** What is stored on the server for every message: JSON text (ordinary chats) or ciphertext + key ids (secret chats). */
data class Envelope(val ct: String = "", val a: String = "", val b: String = "")

data class Chat(
    val id: String = "",
    val type: ChatType = ChatType.GROUP,
    val name: String = "",
    val avatarUrl: String = "",
    val ownerId: String = "",
    val members: List<String> = emptyList(),
    val verified: Boolean = false,
    val lastEnv: Envelope? = null,
    val lastMsgId: String = "",
    val lastSenderId: String = "",
    val lastAt: Long = 0L,
    val readers: List<String> = emptyList(),
    val createdAt: Long = 0L,
    /** Group tags: ranks granted by the creator's ladder (by time spent in the group) and tags members set themselves. */
    val creatorTags: Boolean = true,
    val selfTags: Boolean = true,
    val ladder: List<TagStep> = emptyList(),
    /** Id of the pinned message (shown in the bar under the header), empty = none. */
    val pinnedId: String = "",
    /** Secret chat: one-to-one, end-to-end encrypted. Every other chat is stored as plain text on the server. */
    val secret: Boolean = false,
    /** Public @name of a group/channel (own namespace chatNames/{name}, unique among groups and channels). Empty = private. */
    val username: String = "",
    val description: String = ""
) {
    val isBroadcast: Boolean get() = type == ChatType.CHANNEL
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

data class InlineButton(val text: String, val callbackData: String = "", val url: String = "") {
    fun toJson(): JSONObject = JSONObject().put("t", text).put("c", callbackData).put("u", url)
    companion object {
        fun fromJson(o: JSONObject) = InlineButton(o.optString("t"), o.optString("c"), o.optString("u"))
    }
}

/** Decrypted message body. Serialised to JSON, then encrypted. */
data class Content(
    val kind: String = KIND_TEXT,
    val text: String = "",
    val path: String = "",
    val key: String = "",
    val w: Int = 0,
    val h: Int = 0,
    val inlineButtons: List<List<InlineButton>> = emptyList(),
    val miniAppUrl: String = "",
    val replyToId: String = "",
    /** Text written under a photo / file / voice message. */
    val caption: String = "",
    /** The message was written by an AI (id) with this display name; the real sender only forwarded it. */
    val via: String = "",
    val viaName: String = ""
) {
    fun toJson(): String {
        val o = JSONObject()
            .put("k", kind).put("t", text).put("p", path).put("e", key).put("w", w).put("h", h)
            .put("m", miniAppUrl).put("r", replyToId)
        if (caption.isNotEmpty()) o.put("c", caption)
        if (via.isNotEmpty()) o.put("v", via).put("vn", viaName)
        if (inlineButtons.isNotEmpty()) {
            val arr = JSONArray()
            for (row in inlineButtons) {
                val rowArr = JSONArray()
                for (b in row) rowArr.put(b.toJson())
                arr.put(rowArr)
            }
            o.put("btns", arr)
        }
        return o.toString()
    }

    val preview: String get() = when (kind) {
        KIND_IMAGE -> if (caption.isNotBlank()) "📷 $caption" else "📷 Фото"; KIND_STICKER -> "🙂 Стикер"; KIND_VOICE -> "🎤 Голосовое сообщение"
        KIND_POLL -> "📊 Опрос"; KIND_FILE -> if (caption.isNotBlank()) "📎 $caption" else "📎 Файл"; KIND_CALL -> "📞 Звонок"; else -> text }

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_IMAGE = "image"
        const val KIND_STICKER = "sticker"
        const val KIND_VOICE = "voice"
        const val KIND_POLL = "poll"
        const val KIND_FILE = "file"
        const val KIND_MINIAPP = "miniapp"
        /** Call log entry: text = ended | missed | declined | busy | cancelled | group; w = 1 for video; h = seconds. */
        const val KIND_CALL = "call"
        fun fromJson(s: String): Content {
            val o = JSONObject(s)
            val btnsList = mutableListOf<List<InlineButton>>()
            val arr = o.optJSONArray("btns")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val rowArr = arr.optJSONArray(i)
                    if (rowArr != null) {
                        val row = mutableListOf<InlineButton>()
                        for (j in 0 until rowArr.length()) {
                            val btnObj = rowArr.optJSONObject(j)
                            if (btnObj != null) row.add(InlineButton.fromJson(btnObj))
                        }
                        if (row.isNotEmpty()) btnsList.add(row)
                    }
                }
            }
            return Content(
                o.optString("k", KIND_TEXT), o.optString("t"), o.optString("p"),
                o.optString("e"), o.optInt("w"), o.optInt("h"),
                btnsList, o.optString("m"), o.optString("r"), o.optString("c"), o.optString("v"), o.optString("vn")
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
        createdAt = getLong("createdAt") ?: 0L,
        bot = getBoolean("bot") ?: false,
        moderator = getBoolean("moderator") ?: false,
        pythonCode = getString("pythonCode").orEmpty(),
        ai = getBoolean("ai") ?: false,
        aiPrompt = getString("aiPrompt").orEmpty(),
        ownerId = getString("ownerId").orEmpty(),
        miniAppUrl = getString("miniAppUrl").orEmpty()
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
        avatarUrl = getString("avatarUrl").orEmpty(),
        ownerId = getString("ownerId").orEmpty(),
        members = (get("members") as? List<String>).orEmpty(),
        verified = getBoolean("verified") ?: false,
        lastEnv = envelopeOf(get("lastEnv")),
        lastMsgId = getString("lastMsgId").orEmpty(),
        lastSenderId = getString("lastSenderId").orEmpty(),
        lastAt = getLong("lastAt") ?: 0L,
        readers = (get("readers") as? List<String>).orEmpty(),
        createdAt = getLong("createdAt") ?: 0L,
        pinnedId = getString("pinnedId").orEmpty(),
        secret = getBoolean("secret") ?: false,
        username = getString("username").orEmpty(),
        description = getString("description").orEmpty(),
        creatorTags = getBoolean("creatorTags") ?: true,
        selfTags = getBoolean("selfTags") ?: true,
        ladder = ((get("ladder") as? List<Map<String, Any?>>).orEmpty()).mapNotNull {
            val n = it["n"] as? String ?: return@mapNotNull null
            TagStep(n, (it["m"] as? Number)?.toInt() ?: 0)
        }
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
