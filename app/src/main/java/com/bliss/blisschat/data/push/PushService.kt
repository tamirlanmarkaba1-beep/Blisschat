package com.bliss.blisschat.data.push

import com.bliss.blisschat.data.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Receives pushes sent by the Cloud Functions in /functions (see README).
 * Data messages: type=call | call_end | message. Message text is never in a push (it is end-to-end encrypted).
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).update("fcmTokens", FieldValue.arrayUnion(token))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val prefs = runBlocking { SettingsStore(applicationContext).settings.first() }
        when (d["type"]) {
            "call" -> {
                val callId = d["callId"].orEmpty()
                val caller = d["callerId"].orEmpty()
                if (AppState.foreground) return // the in-app incoming-call screen is already showing
                if (callId.isEmpty() || !prefs.notifCalls || "u:$caller" in prefs.muted) return
                Notifications.showIncomingCall(
                    applicationContext, callId, d["callerName"].orEmpty().ifBlank { "blisschat" },
                    video = d["video"] == "true", fullScreen = prefs.notifFullScreenCalls
                )
            }
            "call_end" -> Notifications.cancelCall(applicationContext)
            "message" -> {
                val chatId = d["chatId"].orEmpty()
                if (AppState.foreground && chatId != "test") return // the app is open: no system notification
                if (chatId.isEmpty() || !prefs.notifMessages || "c:$chatId" in prefs.muted || "u:${d["senderId"].orEmpty()}" in prefs.muted) return
                Notifications.showMessage(applicationContext, chatId, d["senderName"].orEmpty().ifBlank { "blisschat" }, d["body"].orEmpty())
            }
        }
    }
}
