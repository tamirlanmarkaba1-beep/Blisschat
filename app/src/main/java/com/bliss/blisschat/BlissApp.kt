package com.bliss.blisschat

import android.app.Application
import com.bliss.blisschat.data.FirebaseConfig

class BlissApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseConfig.init(this)
        container = AppContainer(this)
    }
}
