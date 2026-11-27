package com.bliss.blisschat.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Firebase is initialised from code, so the app works without a google-services.json.
 * Values come from your Firebase project "bitrate-71684".
 *
 * For Google Sign-In to work you must also:
 *  1. Firebase console > Project settings > Your apps: ensure Android app com.bliss.blisschat
 *     has SHA-1 51:CF:1C:FB:F6:4A:24:D7:CF:F1:02:50:00:CA:11:EF:34:07:79:9C added.
 *  2. Authentication > Sign-in method: enable Google and Email/Password.
 *  3. Keep WEB_CLIENT_ID set to the Web client ID from google-services.json (client_type: 3).
 */
object FirebaseConfig {
    const val API_KEY = "AIzaSyBUXc71LMSwk0Nb82bieZFZbGeMtJ5uU2s"
    const val PROJECT_ID = "bitrate-71684"
    const val STORAGE_BUCKET = "bitrate-71684.firebasestorage.app"
    const val SENDER_ID = "794345122154"
    const val APP_ID = "1:794345122154:android:ab507822911bbb33895d13"

    /** OAuth "Web client ID" used to request the Google ID token. */
    const val WEB_CLIENT_ID = "794345122154-jdusvaonf4i9p27nud577g27qq343cnq.apps.googleusercontent.com"

    /** STUN/TURN servers for calls. Add your own TURN server here for users behind strict NATs. */
    val ICE_SERVERS: List<Triple<String, String?, String?>> = listOf(
        Triple("stun:stun.l.google.com:19302", null, null),
        Triple("stun:stun1.l.google.com:19302", null, null)
    )

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setApplicationId(APP_ID)
            .setProjectId(PROJECT_ID)
            .setStorageBucket(STORAGE_BUCKET)
            .setGcmSenderId(SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
    }
}
