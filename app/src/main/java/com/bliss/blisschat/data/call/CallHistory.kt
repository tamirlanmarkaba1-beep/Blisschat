package com.bliss.blisschat.data.call

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One line of the call log (built from the signalling documents calls/{id}, which are never deleted). */
data class CallRecord(
    val id: String,
    val chatId: String,
    val peerId: String,
    val outgoing: Boolean,
    val video: Boolean,
    val status: String,
    /** True when the callee picked up (the document has an answer). */
    val answered: Boolean,
    val ts: Long
) {
    val missed: Boolean get() = !outgoing && !answered
}

object CallHistory {
    /** Calls where I was the caller or the callee, newest first. Rules allow exactly these two queries. */
    fun observe(uid: String): Flow<List<CallRecord>> = callbackFlow {
        if (uid.isBlank()) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val db = FirebaseFirestore.getInstance()
        val out = HashMap<String, CallRecord>()
        val inc = HashMap<String, CallRecord>()
        fun push() { trySend((out.values + inc.values).sortedByDescending { it.ts }) }
        fun map(d: DocumentSnapshot, outgoing: Boolean): CallRecord? {
            val peer = d.getString(if (outgoing) "calleeId" else "callerId") ?: return null
            return CallRecord(
                d.id, d.getString("chatId").orEmpty(), peer, outgoing, d.getBoolean("video") ?: false,
                d.getString("status").orEmpty(), !d.getString("answer").isNullOrEmpty(), d.getLong("createdAt") ?: 0L
            )
        }
        val r1 = db.collection("calls").whereEqualTo("callerId", uid).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            out.clear(); snap.documents.forEach { d -> map(d, true)?.let { out[d.id] = it } }; push()
        }
        val r2 = db.collection("calls").whereEqualTo("calleeId", uid).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            inc.clear(); snap.documents.forEach { d -> map(d, false)?.let { inc[d.id] = it } }; push()
        }
        awaitClose { r1.remove(); r2.remove() }
    }
}
