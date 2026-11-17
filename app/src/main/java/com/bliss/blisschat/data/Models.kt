package com.bliss.blisschat.data

import com.google.firebase.firestore.DocumentSnapshot

/** The one account that automatically receives Super Admin privileges (case-insensitive). */
const val ADMIN_USERNAME = "tomatik_dev"

enum class ChatType { DIRECT, GROUP, CHANNEL }

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val verified: Boolean = false,
    val support: Boolean = false,
    val createdAt: Long = 0L
) {
    val isAdmin: Boolean get() = username.equals(ADMIN_USERNAME, ignoreCase = true)
}

data class Chat(
    val id: String = "",
    val type: ChatType = ChatType.GROUP,
    val name: String = "",
    val username: String = "",
    val description: String = "",
    val discoverable: Boolean = false,
    val ownerId: String = "",
    val members: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageAt: Long = 0L,
    val createdAt: Long = 0L
)

data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val readBy: List<String> = emptyList()
)

/** Lightweight UI model for one row in the chat list. */
data class ChatItem(
    val chat: Chat,
    val title: String,
    val avatarUrl: String,
    val verified: Boolean,
    val support: Boolean,
    val subtitle: String,
    val time: Long,
    val joined: Boolean
)

/* ---------- Firestore <-> model mapping (manual, so Kotlin property names can't bite us) ---------- */

fun DocumentSnapshot.toUserProfile(): UserProfile? {
    if (!exists()) return null
    return UserProfile(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        username = getString("username").orEmpty(),
        email = getString("email").orEmpty(),
        avatarUrl = getString("avatarUrl").orEmpty(),
        verified = getBoolean("verified") ?: false,
        support = getBoolean("support") ?: false,
        createdAt = getLong("createdAt") ?: 0L
    )
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toChat(): Chat? {
    if (!exists()) return null
    return Chat(
        id = id,
        type = runCatching { ChatType.valueOf(getString("type") ?: "GROUP") }.getOrDefault(ChatType.GROUP),
        name = getString("name").orEmpty(),
        username = getString("username").orEmpty(),
        description = getString("description").orEmpty(),
        discoverable = getBoolean("discoverable") ?: false,
        ownerId = getString("ownerId").orEmpty(),
        members = (get("members") as? List<String>).orEmpty(),
        lastMessage = getString("lastMessage").orEmpty(),
        lastMessageAt = getLong("lastMessageAt") ?: 0L,
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
        text = getString("text").orEmpty(),
        timestamp = getLong("timestamp") ?: 0L,
        readBy = (get("readBy") as? List<String>).orEmpty()
    )
}
