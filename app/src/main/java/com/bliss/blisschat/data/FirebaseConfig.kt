package com.bliss.blisschat.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Firebase is initialised from code, so the app works without a google-services.json.
 *
 * These are the values from your Firebase web config (the "bitrate-71684" project).
 * Web API keys are not secrets - access is controlled by the security rules in /firestore.rules.
 *
 * OPTIONAL: for best results register an Android app (package com.bliss.blisschat) in the
 * Firebase console and paste its App ID (looks like 1:794345122154:android:xxxxxxxx) into APP_ID.
 */
object FirebaseConfig {
    const val API_KEY = "AIzaSyD0U_7IJTQS_Lo6vMVp4l4_wIYlvxeG8Rg"
    const val PROJECT_ID = "bitrate-71684"
    const val STORAGE_BUCKET = "bitrate-71684.firebasestorage.app"
    const val SENDER_ID = "794345122154"
    const val APP_ID = "1:794345122154:web:05b6f2241d89652b895d13"

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
