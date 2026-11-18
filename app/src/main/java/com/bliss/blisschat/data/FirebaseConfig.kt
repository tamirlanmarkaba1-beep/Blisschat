package com.bliss.blisschat.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Firebase is initialised from code, so the app works without a google-services.json.
 * Values come from your Firebase project "bitrate-71684".
 *
 * For Google Sign-In to work you must also:
 *  1. Firebase console > Project settings > Your apps: add an ANDROID app, package com.bliss.blisschat,
 *     with SHA-1  BD:10:C9:FB:6F:FB:95:2E:AF:E1:A6:8E:A4:34:1B:F3:A8:E6:24:2D  (the key bundled in app/blisschat.keystore).
 *  2. Authentication > Sign-in method: enable Google and Email/Password.
 *  3. Paste the "Web client ID" (Authentication > Google > Web SDK configuration) into WEB_CLIENT_ID.
 *  4. (Recommended) put that Android app's App ID (1:794345122154:android:...) into APP_ID.
 */
object FirebaseConfig {
    const val API_KEY = "AIzaSyD0U_7IJTQS_Lo6vMVp4l4_wIYlvxeG8Rg"
    const val PROJECT_ID = "bitrate-71684"
    const val STORAGE_BUCKET = "bitrate-71684.firebasestorage.app"
    const val SENDER_ID = "794345122154"
    const val APP_ID = "1:794345122154:web:05b6f2241d89652b895d13"

    /** OAuth "Web client ID" used to request the Google ID token. Leave empty to hide Google Sign-In errors. */
    const val WEB_CLIENT_ID = ""

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
