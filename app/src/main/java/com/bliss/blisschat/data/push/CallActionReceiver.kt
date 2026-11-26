package com.bliss.blisschat.data.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore

/** "Отклонить" button of the incoming-call notification: works without opening the app. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE) return
        val id = intent.getStringExtra(Notifications.EXTRA_CALL_ID).orEmpty()
        Notifications.cancelCall(context)
        if (id.isEmpty()) return
        val pending = goAsync()
        FirebaseFirestore.getInstance().collection("calls").document(id).update("status", "declined")
            .addOnCompleteListener { pending.finish() }
    }

    companion object { const val ACTION_DECLINE = "com.bliss.blisschat.DECLINE_CALL" }
}
