package com.bliss.blisschat.data.push

/** Tiny process-wide flag so a push is not shown on top of the app while it is already on screen. */
object AppState {
    @Volatile var foreground: Boolean = false
}
