package com.bliss.blisschat

import android.app.Application
import com.bliss.blisschat.data.FirebaseConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BlissApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseConfig.init(this)
        container = AppContainer(this)
        // "Remember me" unchecked: sign out when the app is started from scratch
        runBlocking(Dispatchers.IO) {
            if (!container.settings.settings.first().rememberMe) FirebaseAuth.getInstance().signOut()
        }
    }
}
