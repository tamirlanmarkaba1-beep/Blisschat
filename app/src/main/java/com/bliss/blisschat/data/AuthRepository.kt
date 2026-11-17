package com.bliss.blisschat.data

import android.util.Patterns
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Email + 6-digit OTP authentication.
 *
 *  - MOCK mode (default, no backend): a code is generated on the device and returned to the UI,
 *    which shows it in a debug banner. The user id is derived from the e-mail so the same
 *    address always maps to the same account.
 *  - REAL mode: the `sendOtp` / `verifyOtp` Cloud Functions in /functions e-mail the code and
 *    return a Firebase custom token that we sign in with.
 */
class AuthRepository(
    private val store: SettingsStore
) {
    private val mockCodes = ConcurrentHashMap<String, String>()

    fun isValidEmail(email: String) = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    /** @return the code in MOCK mode (to display), or null in REAL mode. */
    suspend fun sendOtp(rawEmail: String): Result<String?> = runCatching {
        val email = rawEmail.trim().lowercase()
        require(isValidEmail(email)) { "Please enter a valid email address" }
        if (store.settings.first().mockAuth) {
            val code = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
            mockCodes[email] = code
            code
        } else {
            FirebaseFunctions.getInstance()
                .getHttpsCallable("sendOtp")
                .call(mapOf("email" to email))
                .await()
            null
        }
    }.mapFailure()

    /** @return the stable user id of the signed-in account. */
    suspend fun verifyOtp(rawEmail: String, code: String): Result<String> = runCatching {
        val email = rawEmail.trim().lowercase()
        val uid: String
        if (store.settings.first().mockAuth) {
            require(mockCodes[email] == code) { "That code is not correct" }
            mockCodes.remove(email)
            // Best effort: lets Firestore/Storage rules that require a signed-in user pass.
            runCatching { FirebaseAuth.getInstance().signInAnonymously().await() }
            uid = uidFor(email)
        } else {
            val result = FirebaseFunctions.getInstance()
                .getHttpsCallable("verifyOtp")
                .call(mapOf("email" to email, "code" to code))
                .await()
            val data = result.data as? Map<*, *> ?: error("Unexpected server response")
            val token = data["token"] as? String ?: error("No token returned")
            val user = FirebaseAuth.getInstance().signInWithCustomToken(token).await().user
                ?: error("Sign-in failed")
            uid = user.uid
        }
        store.setSession(uid, email)
        uid
    }.mapFailure()

    suspend fun signOut() {
        runCatching { FirebaseAuth.getInstance().signOut() }
        store.clearSession()
    }

    companion object {
        /** Must match uidFor() in functions/index.js so MOCK and REAL mode share account ids. */
        fun uidFor(email: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }.take(28)
        }
    }
}

/** Turns raw exceptions into readable messages for the UI. */
internal fun <T> Result<T>.mapFailure(): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { e ->
        val msg = when {
            e is com.google.firebase.functions.FirebaseFunctionsException -> when (e.code) {
                com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND ->
                    "Login service not deployed. Turn on debug login in Settings > Developer or deploy /functions."
                else -> e.message ?: "Server error"
            }
            e.message.isNullOrBlank() -> "Something went wrong"
            else -> e.message!!
        }
        Result.failure(IllegalStateException(msg, e))
    }
)
