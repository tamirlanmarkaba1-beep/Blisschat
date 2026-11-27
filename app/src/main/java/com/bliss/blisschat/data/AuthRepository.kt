package com.bliss.blisschat.data

import android.content.Context
import android.util.Patterns
import android.content.Intent
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/** E-mail / username + password and Google sign-in on top of Firebase Auth. */
class AuthRepository {

    companion object {
        const val AUTH_WEB_URL = "https://tamirlanmarkaba1-beep.github.io/BlissAnimate/"
    }

    private val auth get() = FirebaseAuth.getInstance()

    /**
     * The password typed at the last password sign-in. Kept in memory only and cleared right after the encryption
     * keys were set up: it unlocks (or creates) the encrypted key backup so old messages follow the account to a new phone.
     */
    @Volatile var sessionSecret: String? = null

    fun isEmail(s: String) = Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

    /** @param identifier an e-mail address or a @username */
    suspend fun login(identifier: String, password: String): Result<Unit> = runCatching {
        val id = identifier.trim().removePrefix("@")
        require(id.isNotEmpty() && password.isNotEmpty()) { "Введите логин и пароль" }
        val email = if (isEmail(id)) id else emailForUsername(id)
        sessionSecret = password
        try {
            auth.signInWithEmailAndPassword(email, password).await()
        } catch (e: Exception) {
            // Accounts made in v1 have no password: the first login with ANY password sets it.
            val retryable = e is FirebaseAuthException && e.errorCode !in setOf("ERROR_TOO_MANY_REQUESTS", "ERROR_USER_DISABLED")
            if (!retryable || !migrateLegacy(email.trim(), password)) throw e
        }
        Unit
    }.mapAuth().onFailure { sessionSecret = null }

    /** True if a v1 profile (no encryption key yet) exists for this e-mail. Needs a signed-in user. */
    private suspend fun legacyExists(email: String): Boolean {
        val users = FirebaseFirestore.getInstance().collection("users")
        for (m in setOf(email, email.lowercase())) {
            val q = users.whereEqualTo("email", m).get().await()
            if (q.documents.any { it.getString("kid").isNullOrEmpty() }) return true
        }
        return false
    }

    /**
     * Creates a Firebase account with the typed password; keeps it only when a v1 profile with this e-mail
     * exists (the profile is then attached to the new account by [ChatRepository.claimLegacy]).
     */
    private suspend fun migrateLegacy(email: String, password: String): Boolean {
        val created = try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
            throw IllegalStateException("Для старого аккаунта пароль должен быть не короче 6 символов", e)
        } catch (e: Exception) {
            return false // e-mail already has a password account -> the original "wrong password" error stands
        }
        val found = runCatching { legacyExists(email) }.getOrDefault(false)
        if (!found) {
            runCatching { created.user?.delete()?.await() }
            auth.signOut()
        } else {
            // Needed only for the reserved admin name (rules require a verified e-mail for @tomatik_dev).
            runCatching { created.user?.sendEmailVerification()?.await() }
        }
        return found
    }

    suspend fun register(email: String, password: String): Result<Unit> = runCatching {
        require(isEmail(email)) { "Введите корректный email" }
        require(password.length >= 6) { "Пароль должен быть не короче 6 символов" }
        sessionSecret = password
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        Unit
    }.mapAuth().onFailure { sessionSecret = null }

    /** Error from the last web Google sign-in (deep link); shown by the login screen, then cleared. */
    val webError = MutableStateFlow<String?>(null)

    /** Google sign-in goes through the web page: it opens in the browser and comes back via blisschat://auth?idToken=... */
    suspend fun google(activityContext: Context): Result<Unit> = runCatching {
        webError.value = null
        activityContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(AUTH_WEB_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(IllegalStateException("Не удалось открыть браузер для входа через Google", it)) }
    )

    /** Token from blisschat://auth?idToken=... (also accepts the token in the #fragment). */
    fun extractIdToken(uri: Uri?): String? {
        if (uri == null || uri.scheme != "blisschat" || uri.host != "auth") return null
        val keys = listOf("idToken", "id_token")
        for (k in keys) uri.getQueryParameter(k)?.takeIf { it.isNotBlank() }?.let { return it }
        val frag = uri.fragment ?: return null
        return Uri.parse("blisschat://auth?$frag").let { f -> keys.firstNotNullOfOrNull { f.getQueryParameter(it)?.takeIf { v -> v.isNotBlank() } } }
    }

    suspend fun signInWithWebIdToken(idToken: String): Result<Unit> = runCatching {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }.mapAuth().onFailure { e -> webError.update { e.message } }

    suspend fun resetPassword(identifier: String): Result<Unit> = runCatching {
        val id = identifier.trim().removePrefix("@")
        require(id.isNotEmpty()) { "Введите email или username" }
        val email = if (isEmail(id)) id else emailForUsername(id)
        auth.sendPasswordResetEmail(email).await()
        Unit
    }.mapAuth()

    fun signOut() { sessionSecret = null; auth.signOut() }

    /** usernames/{name} stores the login e-mail so a username can be used to sign in. */
    private suspend fun emailForUsername(username: String): String {
        val doc = FirebaseFirestore.getInstance().collection("usernames")
            .document(username.lowercase()).get().await()
        return doc.getString("email") ?: error("Пользователь не найден")
    }

    private fun <T> Result<T>.mapAuth(): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { e ->
            val msg = when {
                e is FirebaseAuthException -> when (e.errorCode) {
                    "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND",
                    "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Неверный логин или пароль"
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "Этот email уже зарегистрирован"
                    "ERROR_WEAK_PASSWORD" -> "Слишком простой пароль"
                    "ERROR_TOO_MANY_REQUESTS" -> "Слишком много попыток, попробуйте позже"
                    "ERROR_USER_DISABLED" -> "Аккаунт заблокирован"
                    else -> e.message ?: "Ошибка авторизации"
                }
                e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Неверный логин или пароль"
                e is com.google.firebase.FirebaseNetworkException -> "Нет подключения к интернету"
                e.message.isNullOrBlank() -> "Что-то пошло не так"
                else -> e.message!!
            }
            Result.failure(IllegalStateException(msg, e))
        }
    )
}
